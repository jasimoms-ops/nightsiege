package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Everything that acts in a fight rather than on an attribute: the ultimates of
 * all sixteen paths, fired with the ultimate key, and the passive combat skills.
 *
 * <p>Every ultimate has its own cooldown of 25, 20 or 15 seconds by rank. Firing
 * it too early only shows how long is left.</p>
 */
public final class CombatEffects {

    /** Heal auras pulse this often. */
    private static final int HEAL_INTERVAL = 60;
    private static final double HEAL_RADIUS = 8.0;

    private final SkillTree tree;
    private final Function<ServerPlayer, PlayerSkills> skillsFor;
    private final Predicate<Entity> isChampion;
    private final ArcherEffects archer;
    private final TrapService traps;
    private final GuardianEffects guardian;
    private final Random random = new Random();

    /** Multiplier on the ultimate cooldown, set by the vigilance perk. */
    private java.util.function.ToDoubleFunction<ServerPlayer> cooldownScale = player -> 1.0;
    /** Extra share of arrow damage, set by the flame perk. */
    private java.util.function.ToDoubleFunction<ServerPlayer> arrowBonus = player -> 0.0;
    private final Map<UUID, Long> ultReadyAt = new HashMap<>();
    /** Length of the cooldown last started, for the shading in the hotbar slot. */
    private final Map<UUID, Integer> ultTotal = new HashMap<>();
    /** Guardians whose angel has already saved someone this night. */
    private final Set<UUID> angelUsed = new HashSet<>();
    /** Monsters taking extra damage: mark from a hunter, cracks from an axe. */
    private final Map<UUID, Mark> marks = new HashMap<>();
    /** Archer: armour torn open by steel bolts; counts on top of a mark (balance update, 26 October 2026). */
    private final Map<UUID, Mark> boltCracks = new HashMap<>();
    /** Set while the mod deals its own bonus damage, so that hit is not boosted again. */
    private boolean dealingBonus;
    private int healCounter;

    private record Mark(double bonus, long until) {
    }

    /** Warrior: hit streaks (stacks, last hit), the strong hit after a kill, the divine shield. */
    private final Map<UUID, long[]> combos = new HashMap<>();
    private final Map<UUID, Long> momentumUntil = new HashMap<>();
    private final Map<UUID, Long> divineReadyAt = new HashMap<>();
    /** Things an ultimate does a moment later (second slam, further whirlwind turns). */
    private final List<Delayed> delayed = new ArrayList<>();
    private int tickCounter;
    private long currentTime;
    private static final net.minecraft.resources.Identifier WARRIOR_SPEED = ModContent.id("warrior_speed");

    private record Delayed(long due, Runnable action) {
    }

    /** Archer: tags on the bolts of the volley masteries, the tripwire's pause, crossbow speed. */
    private static final String TAG_BARRAGE = "nightsiege_barrage";
    private static final String TAG_NET = "nightsiege_net";
    /** On an arrow whose skills have already fired once: a bolt with vanilla Piercing triggers them only on its first monster. */
    private static final String TAG_STRUCK = "nightsiege_struck";
    private final Map<UUID, Long> tripReadyAt = new HashMap<>();
    private static final net.minecraft.resources.Identifier XBOW_SPEED = ModContent.id("xbow_speed");

    public CombatEffects(SkillTree tree, Function<ServerPlayer, PlayerSkills> skillsFor,
                         Predicate<Entity> isChampion, ArcherEffects archer, TrapService traps) {
        this.tree = tree;
        this.skillsFor = skillsFor;
        this.isChampion = isChampion;
        this.archer = archer;
        this.traps = traps;
        this.guardian = new GuardianEffects(tree, skillsFor, this);
    }

    public GuardianEffects guardian() {
        return guardian;
    }

    /** Runs an action a few ticks later (the bounces of a thrown shield, the debris of a charge). */
    void schedule(int ticks, Runnable action) {
        delayed.add(new Delayed(currentTime + Math.max(1, ticks), action));
    }

    /** Rebirth: the guardian's angel may save someone again this night. */
    void readyAngel(UUID player) {
        angelUsed.remove(player);
    }

    /** A new night: every guardian angel is ready again. */
    public void newSiege() {
        angelUsed.clear();
        guardian.newNight();   // full shield energy
    }

    // ------------------------------------------------------------------
    // ultimates
    // ------------------------------------------------------------------

    /** The ultimate key was pressed. */
    public void triggerUltimate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        SkillNode node = skills == null ? null : tree.ultimateOf(skills);
        if (node == null) {
            actionBar(player, Component.translatable(skills == null
                    ? "nightsiege.msg.ult_inactive" : "nightsiege.msg.ult_none"));
            return;
        }
        int rank = Math.max(1, skills.levelOf(node.id()));
        long now = level.getGameTime();
        long readyAt = ultReadyAt.getOrDefault(player.getUUID(), 0L);
        if (now < readyAt) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_wait", (readyAt - now + 19) / 20));
            return;
        }
        boolean fired = switch (node.effect()) {
            case ARROW_STORM -> {
                archer.startStorm(player, now + (long) SkillValues.get("arrow_storm", "dur" + rank, 60 + 40 * rank)
                        + (SkillTree.has(skills, "a_tempest") ? (long) SkillValues.get("a_tempest", "extra", 40) : 0));
                play(level, player, SoundEvents.BLAZE_SHOOT, 1.0f, 1.4f);
                yield true;
            }
            case POWER_SHOT -> {
                archer.armPowerShot(player, now, rank);
                play(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.6f);
                yield true;
            }
            case BOLT_VOLLEY -> boltVolley(level, player, rank);
            case NET_RAIN -> netRain(level, player, rank, now);
            case HUNTERS_MARK -> huntersMark(level, player, rank, now);
            case WHIRLWIND -> whirlwind(level, player, rank);
            case EXECUTE -> execute(level, player, rank);
            case LANCE_THRUST -> lanceThrust(level, player, rank);
            case GROUND_SLAM -> groundSlam(level, player, rank);
            case TAUNT -> guardian.taunt(level, player, rank);
            case MASS_HEAL -> guardian.massHeal(level, player, rank);
            case SHIELD_CHARGE -> guardian.shieldCharge(level, player, rank);
            case SANCTUARY -> guardian.sanctuary(level, player, rank);
            case HOLY_LIGHT -> holyLight(level, player, rank);
            case BOMBARDMENT -> bombardment(level, player, rank);
            case OVERCHARGE -> {
                // Arrow turrets and tesla coils fire faster (the tesla too since the balance update, 26 October 2026).
                traps.overcharge(player, now + Math.round(SkillValues.get("overcharge", "time" + rank, 160 + 40 * rank))
                        + (SkillTree.has(skills, "e_sustained") ? Math.round(SkillValues.get("e_sustained", "time", 80)) : 0));
                play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
                yield true;
            }
            case TOXIC_CLOUD -> toxicCloud(level, player, rank);
            case THUNDERSTORM -> thunderstorm(level, player, rank);
            case WILD_HUNT -> Companions.get() != null && Companions.get().wildHunt(level, player, rank);
            case TITAN -> Companions.get() != null && Companions.get().titan(level, player, rank);
            case ARMY_OF_THE_DEAD -> Companions.get() != null && Companions.get().armyOfTheDead(level, player, rank);
            case SOUL_STORM -> Companions.get() != null && Companions.get().soulStorm(level, player, rank);
            default -> false;
        };
        if (fired) {
            // In creative the ultimate is for trying out: only a short pause.
            int cooldown = player.getAbilities().instabuild ? 40
                    : (int) Math.round((node.clazz() == SkillClass.WARRIOR || node.clazz() == SkillClass.SUMMONER
                    ? SkillValues.get(node.id(), "cd" + rank, cooldownFor(rank)) : cooldownFor(rank))
                    * cooldownScale.applyAsDouble(player));
            ultReadyAt.put(player.getUUID(), now + cooldown);
            ultTotal.put(player.getUUID(), cooldown);
            actionBar(player, Component.translatable("nightsiege.msg.ult_fired",
                    Component.translatable("nightsiege.skill." + node.id())));
        }
    }

    // --- archer

    private boolean boltVolley(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int count = Math.max(2, (int) SkillValues.get("bolt_volley", "count" + rank, 5 + 2 * rank));   // 7 / 9 / 11 bolts
        float spread = (float) SkillValues.get("bolt_volley", "spread", 50.0);
        for (int i = 0; i < count; i++) {
            float yaw = player.getYRot() - spread / 2 + spread * i / (count - 1);
            Arrow bolt = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
            bolt.entityTags().add(ArcherEffects.HANDLED);
            bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
            bolt.setBaseDamage(SkillValues.get("bolt_volley", "dmg" + rank, 4.0 + 2.0 * rank));
            if (SkillTree.has(skills, "a_barrage")) {
                bolt.entityTags().add(TAG_BARRAGE);
            }
            if (SkillTree.has(skills, "a_widenet")) {
                bolt.entityTags().add(TAG_NET);
            }
            bolt.shootFromRotation(player, player.getXRot(), yaw, 0f, 3.2f, 1.0f);
            level.addFreshEntity(bolt);
        }
        play(level, player, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.7f);
        return true;
    }

    /** Trapper: nets rain on every monster around; they stick, take damage and, with the death trap, more of it. */
    private boolean netRain(ServerLevel level, ServerPlayer player, int rank, long now) {
        List<LivingEntity> near = enemies(level, player.position(),
                SkillValues.get("a_netrain", "radius" + rank, 10.0 + 2.0 * rank), 6.0);
        if (near.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        double trap = tree.totalBonus(SkillEffect.DEATHTRAP, skillsFor.apply(player));
        DamageSource source = level.damageSources().playerAttack(player);
        int hold = (int) SkillValues.get("a_netrain", "dur" + rank, 60 + 20 * rank);
        float hurt = (float) SkillValues.get("a_netrain", "dmg" + rank, 4.0 + 2.0 * rank);
        for (LivingEntity victim : near) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, hold, 9));
            bonusHit(level, victim, source, hurt);
            if (trap > 0) {
                mark(victim, trap, now + hold, true);
            }
            level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY(0.8), victim.getZ(),
                    12, 0.4, 0.4, 0.4, 0.0);
        }
        play(level, player, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.5f);
        play(level, player, SoundEvents.WOOL_PLACE, 1.0f, 0.7f);
        return true;
    }

    private boolean huntersMark(ServerLevel level, ServerPlayer player, int rank, long now) {
        PlayerSkills skills = skillsFor.apply(player);
        boolean pack = tree.totalBonus(SkillEffect.PACK, skills) > 0;
        // Both ways have their own copy of the hunt and the pack; the numbers come from the one learned.
        String hunt = SkillTree.has(skills, "x_hunt") ? "x_hunt" : "b_hunt";
        String packNode = SkillTree.has(skills, "x_pack") ? "x_pack" : "b_pack";
        double r = SkillValues.get(hunt, "radius" + rank, 16.0 + 8.0 * rank)
                * (pack ? SkillValues.get(packNode, "radius", 1.5) : 1.0);
        double bonus = SkillValues.get(hunt, "bonus" + rank, 0.3 + 0.1 * rank);
        int packTicks = (int) SkillValues.get(packNode, "dur", 200);
        if (pack) {
            // The pack hunts together: everyone close by runs faster.
            double range = SkillValues.get(packNode, "range", 20.0);
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(player) <= range * range) {
                    mate.addEffect(new MobEffectInstance(MobEffects.SPEED, packTicks, 0));
                }
            }
        }
        long until = now + (long) SkillValues.get(hunt, "dur", 300);
        for (LivingEntity victim : enemies(level, player.position(), r, 6.0)) {
            mark(victim, bonus, until, true);
            if (pack) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, packTicks, 0));
            }
        }
        play(level, player, SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.4f);
        return true;
    }

    // --- warrior

    private boolean whirlwind(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        whirlPulse(level, player, rank, skills);
        if (SkillTree.has(skills, "w_bladestorm")) {
            long now = level.getGameTime();
            int pulses = (int) SkillValues.get("w_bladestorm", "pulses", 3);
            long gap = (long) SkillValues.get("w_bladestorm", "gap", 10);
            for (int i = 1; i < pulses; i++) {
                delayed.add(new Delayed(now + gap * i, () -> whirlPulse(level, player, rank, skills)));
            }
        }
        return true;
    }

    private void whirlPulse(ServerLevel level, ServerPlayer player, int rank, PlayerSkills skills) {
        if (!player.isAlive()) {
            return;
        }
        double r = SkillValues.get("whirlwind", "radius" + rank, 3.5 + 0.5 * rank);
        float damage = (float) SkillValues.get("whirlwind", "dmg" + rank, 4f + 3f * rank);   // 7 / 10 / 13
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        int hits = 0;
        for (LivingEntity victim : enemies(level, center, r, 1.5)) {
            bonusHit(level, victim, source, damage);
            pushAway(victim, center, 0.8, 0.3);
            hits++;
        }
        if (hits > 0 && SkillTree.has(skills, "w_bloodstorm")) {
            player.heal((float) Math.min(SkillValues.get("w_bloodstorm", "max", 8),
                    hits * SkillValues.get("w_bloodstorm", "heal", 1)));
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(a) * r * 0.6, center.y + 1.0,
                    center.z + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0);
        }
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
    }

    private boolean execute(ServerLevel level, ServerPlayer player, int rank) {
        LivingEntity target = lookTarget(level, player, SkillValues.get("execute", "reach", 5));
        if (target == null) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        double threshold = SkillValues.get("execute", "limit" + rank, 0.2 + 0.05 * rank);   // 25 % / 30 % / 35 % health
        if (target.getHealth() > target.getMaxHealth() * threshold) {
            actionBar(player, Component.translatable("nightsiege.msg.execute_too_healthy",
                    Math.round(threshold * 100)));
            return false;
        }
        bonusHit(level, target, level.damageSources().playerAttack(player), target.getHealth() + 1000f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(),
                30, 0.4, 0.5, 0.4, 0.3);
        play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        play(level, player, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
        PlayerSkills skills = skillsFor.apply(player);
        if (SkillTree.has(skills, "w_chain_execute")) {
            // The blade jumps on to up to two more weakened monsters nearby.
            Vec3 at = target.position();
            int jumps = 0;
            int maxJumps = (int) SkillValues.get("w_chain_execute", "jumps", 2);
            for (LivingEntity next : enemies(level, at, SkillValues.get("w_chain_execute", "radius", 5), 2.0)) {
                if (jumps >= maxJumps || !next.isAlive() || next.getHealth() > next.getMaxHealth() * threshold) {
                    continue;
                }
                TrapService.spark(level, at.add(0, 1, 0), next.position().add(0, 1, 0));
                bonusHit(level, next, level.damageSources().playerAttack(player), next.getHealth() + 1000f);
                jumps++;
            }
        }
        if (SkillTree.has(skills, "w_bloody_execute")) {
            player.heal((float) SkillValues.get("w_bloody_execute", "heal", 6));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) SkillValues.get("w_bloody_execute", "regen", 100), 0));
        }
        return true;
    }

    /** Spear: everything in a line of eight blocks is run through. */
    private boolean lanceThrust(ServerLevel level, ServerPlayer player, int rank) {
        Vec3 dir = flatLook(player);
        if (dir == null) {
            return false;
        }
        Vec3 origin = player.position();
        float damage = 5f + 3f * rank;   // 8 / 11 / 14
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, origin, 8.5, 2.0)) {
            Vec3 to = victim.position().subtract(origin);
            double along = to.x * dir.x + to.z * dir.z;
            double side = Math.abs(to.x * dir.z - to.z * dir.x);
            if (along > 0 && along <= 8.0 && side <= 1.3) {
                bonusHit(level, victim, source, damage);
                victim.push(dir.x * 0.6, 0.15, dir.z * 0.6);
            }
        }
        for (int i = 1; i <= 8; i++) {
            Vec3 p = origin.add(dir.scale(i)).add(0, 1.1, 0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.05);
        }
        play(level, player, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
        return true;
    }

    /** Axe: the ground shakes, everything around is thrown up and staggered. */
    private boolean groundSlam(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        slam(level, player, rank, 1.0f, SkillTree.has(skills, "w_crater"));
        if (SkillTree.has(skills, "w_aftershock")) {
            float scale = (float) SkillValues.get("w_aftershock", "scale", 0.6);
            delayed.add(new Delayed(level.getGameTime() + (long) SkillValues.get("w_aftershock", "delay", 20),
                    () -> slam(level, player, rank, scale, false)));
        }
        return true;
    }

    private void slam(ServerLevel level, ServerPlayer player, int rank, float scale, boolean crater) {
        if (!player.isAlive()) {
            return;
        }
        double r = SkillValues.get("ground_slam", "radius" + rank, 4.0 + rank);
        float damage = (float) SkillValues.get("ground_slam", "dmg" + rank, 5f + 3f * rank) * scale;   // 8 / 11 / 14
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, center, r, 2.0)) {
            bonusHit(level, victim, source, damage);
            if (crater) {
                // The ground caves in: everything slides towards the warrior and sticks.
                pushAway(victim, center, -0.9, 0.2);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get("w_crater", "slow", 60), 3));
            } else {
                victim.push(0, 0.7, 0);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get("ground_slam", "slow", 60), 2));
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2, center.z, 6, r * 0.4, 0.1, r * 0.4, 0.0);
        play(level, player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, scale < 1f ? 1.1f : 0.8f);
    }

    // --- guardian

    /** Paladin: the undead burn in the light, everyone else is only stung; allies are mended. */
    private boolean holyLight(ServerLevel level, ServerPlayer player, int rank) {
        double r = SkillValues.get("holy_light", "radius" + rank, 8.0 + 2.0 * rank);
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        boolean sunfire = SkillTree.has(skills, "w_sunfire");
        boolean redemption = SkillTree.has(skills, "w_redemption");
        float sunFactor = sunfire ? (float) SkillValues.get("w_sunfire", "factor", 1.5) : 1f;
        for (LivingEntity victim : enemies(level, center, r, 3.0)) {
            if (isUndead(victim)) {
                bonusHit(level, victim, source, (float) SkillValues.get("holy_light", "undead" + rank, 10f + 5f * rank) * sunFactor);
                victim.igniteForSeconds((float) ((sunfire ? SkillValues.get("w_sunfire", "burn", 200)
                        : SkillValues.get("holy_light", "burn", 100)) / 20.0));
            } else {
                bonusHit(level, victim, source, (float) SkillValues.get("holy_light", "dmg" + rank, 3f + 2f * rank));
                if (sunfire) {
                    victim.igniteForSeconds((float) (SkillValues.get("w_sunfire", "burnother", 60) / 20.0));
                }
            }
        }
        for (ServerPlayer mate : GuardianEffects.mates(level)) {
            if (mate.distanceToSqr(player) <= r * r) {
                mate.heal((float) (redemption ? SkillValues.get("w_redemption", "heal" + rank, 4f + 4f * rank)
                        : SkillValues.get("holy_light", "heal", 4)));
                if (redemption) {
                    for (MobEffectInstance effect : new ArrayList<>(mate.getActiveEffects())) {
                        if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            mate.removeEffect(effect.getEffect());
                        }
                    }
                    mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) SkillValues.get("w_redemption", "regen", 100), 1));
                }
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 80, r * 0.4, 1.0, r * 0.4, 0.05);
        play(level, player, SoundEvents.BELL_BLOCK, 1.0f, 1.5f);
        return true;
    }

    // --- engineer

    /** Demolition: blasts land on the nearest monsters. Blocks and players are spared. */
    private boolean bombardment(ServerLevel level, ServerPlayer player, int rank) {
        List<LivingEntity> targets = new ArrayList<>(enemies(level, player.position(),
                SkillValues.get("bombardment", "reach", 20.0), 8.0));
        if (targets.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        float damage = (float) (SkillValues.get("bombardment", "dmg" + rank, 8 + 4 * rank) * engineerBlast(skills));
        boolean firebombs = SkillTree.has(skills, "e_firebombs");
        int burn = (int) Math.round(SkillValues.get("e_firebombs", "burn", 100));
        double radius = SkillValues.get("bombardment", "radius", 2.5);
        int carpet = (int) Math.round(tree.totalBonus(SkillEffect.CARPET_BOMB, skills));
        int blasts = Math.min(targets.size() + carpet,
                (int) Math.round(SkillValues.get("bombardment", "blasts" + rank, 3 + 2 * rank)) + carpet);
        for (int i = 0; i < blasts; i++) {
            LivingEntity aim = targets.get(i % targets.size());
            Vec3 at = i < targets.size() ? aim.position()
                    : aim.position().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            for (LivingEntity victim : enemies(level, at, radius, 1.5)) {
                bonusHit(level, victim, source, damage);
                pushAway(victim, at, 0.5, 0.4);
                if (firebombs) {
                    victim.igniteForSeconds(burn / 20f);
                    victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, burn, 0));
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
        }
        return true;
    }

    private boolean toxicCloud(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        boolean plague = SkillTree.has(skills, "e_plague");
        double acid = tree.totalBonus(SkillEffect.ACID_FOG, skills);
        double r = SkillValues.get("toxic_cloud", "radius" + rank, 6 + rank) + (plague ? SkillValues.get("e_plague", "radius", 3.0) : 0.0);
        int ticks = (int) Math.round(SkillValues.get("toxic_cloud", "time" + rank, 120 + 40 * rank)
                * (SkillTree.has(skills, "e_plague_doctor") ? SkillValues.get("e_plague_doctor", "poison", 2.0) : 1.0));
        long until = level.getGameTime() + ticks;
        Set<LivingEntity> sick = new HashSet<>(enemies(level, player.position(), r, 3.0));
        if (plague) {
            // The plague jumps on to whatever stands next to the sick.
            double spread = SkillValues.get("e_plague", "spread", 3.0);
            for (LivingEntity victim : new ArrayList<>(sick)) {
                sick.addAll(enemies(level, victim.position(), spread, 2.0));
            }
        }
        for (LivingEntity victim : sick) {
            victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, ticks, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks / 2, 1));
            if (acid > 0) {
                mark(victim, acid, until, false);
            }
        }
        Vec3 c = player.position();
        level.sendParticles(TrapService.POISON_DUST, c.x, c.y + 1.0, c.z, 150, r * 0.5, 1.0, r * 0.5, 0.0);
        play(level, player, SoundEvents.WITCH_THROW, 1.0f, 0.6f);
        play(level, player, SoundEvents.BREWING_STAND_BREW, 1.0f, 0.5f);
        return true;
    }

    private boolean thunderstorm(ServerLevel level, ServerPlayer player, int rank) {
        List<LivingEntity> near = enemies(level, player.position(), SkillValues.get("thunderstorm", "range", 16.0), 6.0);
        if (near.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        boolean ball = SkillTree.has(skills, "e_ball_lightning");
        float damage = (float) (SkillValues.get("thunderstorm", "dmg" + rank, 6 + 2 * rank)
                + (ball ? SkillValues.get("e_ball_lightning", "dmg", 2.0) : 0.0));
        int jumps = (int) Math.round(SkillValues.get("thunderstorm", "jumps" + rank, 4 + 2 * rank)
                + (ball ? SkillValues.get("e_ball_lightning", "jumps", 4.0) : 0.0));
        double jump = SkillValues.get("thunderstorm", "jump", 6.0);
        if (SkillTree.has(skills, "e_superconductor")) {
            traps.fillRemote(player);
            traps.overcharge(player, level.getGameTime() + Math.round(SkillValues.get("e_superconductor", "time", 80)));
        }
        List<LivingEntity> hit = new ArrayList<>();
        Vec3 from = player.position().add(0, 1.5, 0);
        LivingEntity current = near.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        while (current != null && hit.size() < jumps) {
            hit.add(current);
            Vec3 to = current.position().add(0, current.getBbHeight() * 0.5, 0);
            TrapService.spark(level, from, to);
            bonusHit(level, current, source, damage);
            from = to;
            Vec3 at = to;
            current = enemies(level, at, jump, 3.0).stream()
                    .filter(e -> !hit.contains(e))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(at)))
                    .orElse(null);
        }
        play(level, player, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------
    // passive combat skills
    // ------------------------------------------------------------------

    public void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
        if (dealingBonus || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        guardian.afterDamage(victim, source, base, taken, blocked);
        if (victim instanceof ServerPlayer hurt && !blocked) {
            SiegeManager tuning = SiegeManager.get();
            if (tuning != null) {
                ArmorBreak.afterHit(hurt, source, taken, tuning.config());   // zombies and creepers crack the armour
            }
        }
        long now = level.getGameTime();
        if (victim instanceof Enemy && taken > 0 && source.getEntity() instanceof ServerPlayer player) {
            PlayerSkills skills = skillsFor.apply(player);
            double extra = 0.0;
            Mark mark = marks.get(victim.getUUID());
            if (mark != null && now <= mark.until()) {
                extra += taken * mark.bonus();
            }
            Mark crack = boltCracks.get(victim.getUUID());
            if (crack != null && now <= crack.until()) {
                extra += taken * crack.bonus();
            }
            if (skills != null) {
                if (source.getDirectEntity() == player) {
                    extra += meleeHit(level, player, skills, victim, taken, now);
                } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                    extra += arrowHit(level, player, skills, victim, arrow, taken, now);
                }
            }
            if (source.getDirectEntity() instanceof AbstractArrow shot
                    && (shot.entityTags().contains(TrapService.TAG_CHARGED) || shot.entityTags().contains(TrapService.TAG_BLAST))
                    && handledHits.add(turretKey(shot, victim))) {
                dealingBonus = true;
                try {
                    traps.onTurretArrowHit(level, victim, shot, player, taken);
                } finally {
                    dealingBonus = false;
                }
            }
            if (source.getDirectEntity() instanceof AbstractArrow) {
                extra += taken * arrowBonus.applyAsDouble(player);
            }
            if (extra > 0.05 && victim.isAlive()) {
                followUpHit(level, victim, level.damageSources().playerAttack(player), (float) extra);
            }
        }
    }

    /** Returns the extra damage of a melee hit; side effects are applied here. */
    private double meleeHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                            LivingEntity victim, float taken, long now) {
        // A warrior's skills only work with the weapon of their way.
        if (skills.clazz() == SkillClass.WARRIOR && !holdsWayWeapon(player, skills)) {
            return 0.0;
        }
        // The axe fighter's cleave heals at half the rate of his hit life steal (Simon 29.10.): what it took is counted here.
        boolean axeSteal = SkillTree.SUB_AXE.equals(tree.subclassOf(skills));
        float cleaved = 0f;
        // Spaltaxt (crate gain, 27 October 2026): an axe hit cleaves into the monster right behind.
        if (CrateEffects.gainLevel(player, "spaltaxt") > 0
                && BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().endsWith("_axe")) {
            Vec3 dir = victim.position().subtract(player.position());
            dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() > 1.0e-4) {
                Vec3 behind = victim.position().add(dir.normalize().scale(1.4));
                for (LivingEntity near : enemies(level, behind, 1.3, 1.5)) {
                    if (near != victim) {
                        float before = near.getHealth();
                        bonusHit(level, near, level.damageSources().playerAttack(player), taken * 0.5f);
                        cleaved += axeSteal ? Math.max(0f, before - near.getHealth()) : 0f;
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, near.getX(), near.getY(0.6), near.getZ(), 1, 0, 0, 0, 0);
                        break;
                    }
                }
            }
        }
        double bonus = 0.0;
        double rage = tree.totalBonus(SkillEffect.RAGE, skills);
        if (rage > 0) {
            bonus += rage * (1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth()));
        }
        double critChance = tree.totalBonus(SkillEffect.CRIT_CHANCE, skills);
        double critBase = SkillValues.get(SkillTree.has(skills, "m_cross_keen") ? "m_cross_keen" : "keen_edge", "crit", 0.5);
        if (SkillTree.has(skills, "w_precision")) {
            // Präzision shares a choice with Scharfe Klinge, so it brings its own, rarer critical hits.
            critChance += SkillValues.get("w_precision", "chance", 0.10);
            critBase = SkillValues.get("w_precision", "crit", 0.5);
        }
        if (critChance <= 0 && SkillTree.has(skills, "w_headsman")) {
            critChance = SkillValues.get("w_headsman", "chance", 0.10);
        }
        if (critChance <= 0 && SkillTree.has(skills, "w_holy_strike")) {
            critChance = SkillValues.get("w_holy_strike", "chance", 0.10);
        }
        boolean crit = random.nextDouble() < critChance;
        if (crit) {
            bonus += critBase + tree.totalBonus(SkillEffect.CRIT_POWER, skills);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(),
                    12, 0.3, 0.4, 0.3, 0.2);
            play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        }
        if (isChampion.test(victim)) {
            bonus += tree.totalBonus(SkillEffect.CHAMPION_BANE, skills);
        }
        boolean undead = isUndead(victim);
        if (undead) {
            bonus += tree.totalBonus(SkillEffect.SMITE, skills);
        }
        double combo = tree.totalBonus(SkillEffect.COMBO, skills);
        if (combo > 0) {
            long[] streak = combos.computeIfAbsent(player.getUUID(), u -> new long[2]);
            if (now - streak[1] > (long) SkillValues.get("w_combo", "window", 40)) {
                streak[0] = 0;
            }
            bonus += combo * streak[0];
            streak[0] = Math.min((long) SkillValues.get("w_combo", "stacks", 5), streak[0] + 1);
            streak[1] = now;
        }
        if (Combo.active(player)) {
            // Under the combo the attack speed is fixed: Klingentanz and Raserei turn into damage.
            bonus += skills.levelOf("w_bladedance") * SkillValues.get("w_bladedance", "combo", 0.08);
            if (SkillTree.has(skills, "w_frenzy")) {
                bonus += SkillValues.get("w_frenzy", "combo", 0.4)
                        * (1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth()));
            }
        }
        double headhunter = tree.totalBonus(SkillEffect.HEADHUNTER, skills);
        if (headhunter > 0 && victim.getHealth() + taken > victim.getMaxHealth() * SkillValues.get("w_headhunter", "above", 0.7)) {
            bonus += headhunter;
        }
        double price = tree.totalBonus(SkillEffect.BLOOD_PRICE, skills);
        if (price > 0) {
            bonus += price;
            float floor = (float) SkillValues.get("w_bloodprice", "floor", 6.0);
            if (player.getHealth() > floor) {
                // One life point (half a heart) per hit, but never below the floor.
                player.setHealth(Math.max(floor, player.getHealth() - (float) SkillValues.get("w_bloodprice", "cost", 1.0)));
            }
        }
        Long momentum = momentumUntil.remove(player.getUUID());
        if (momentum != null && now <= momentum) {
            bonus += tree.totalBonus(SkillEffect.MOMENTUM, skills);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.6), victim.getZ(), 1, 0, 0, 0, 0);
        }
        double shock = tree.totalBonus(SkillEffect.EARTHSHOCK, skills);
        boolean falling = shock > 0 && player.fallDistance > SkillValues.get("w_earthshock", "fall", 1.5);
        if (falling) {
            bonus += shock;
        }
        if (SkillTree.has(skills, "w_glass_cannon")) {
            bonus += SkillValues.get("w_glass_cannon", "dmg", 0.3);
        }
        double extra = taken * bonus;

        double sunder = tree.totalBonus(SkillEffect.SUNDER, skills);
        if (sunder > 0) {
            mark(victim, sunder, now + (long) SkillValues.get("sunder", "time", 100), false);
        }
        double heavy = tree.totalBonus(SkillEffect.HEAVY_BLOW, skills);
        if (heavy > 0) {
            extra += heavy;
            String slowFrom = SkillTree.has(skills, "e_cross_heavy") ? "e_cross_heavy" : "heavy_blow";
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get(slowFrom, "slow", 20), 1));
        }
        if (crit) {
            double headsman = tree.totalBonus(SkillEffect.HEADSMAN, skills);
            if (headsman > 0) {
                extra += headsman;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get("w_headsman", "slow", 20), 2));
            }
            if (undead && SkillTree.has(skills, "w_holy_strike")) {
                victim.igniteForSeconds((float) (SkillValues.get("w_holy_strike", "burn", 80) / 20.0));
                player.heal((float) SkillValues.get("w_holy_strike", "heal", 1));
            }
            if (SkillTree.has(skills, "w_bladefrenzy")) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, (int) SkillValues.get("w_bladefrenzy", "time", 60), 0));
            }
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.is(ItemTags.SPEARS)) {
            double impale = tree.totalBonus(SkillEffect.IMPALE, skills);
            if (impale > 0) {
                impaleBehind(level, player, victim, (float) (taken * impale));
            }
        }

        double cleave = tree.totalBonus(SkillEffect.CLEAVE, skills);
        if (cleave > 0 || falling) {
            boolean crusader = SkillTree.has(skills, "w_crusader");
            DamageSource source = level.damageSources().playerAttack(player);
            double cleaveShare = cleave;
            if (cleave > 0 && SkillTree.SUB_AXE.equals(tree.subclassOf(skills))) {
                cleaveShare *= axeCleaveScale(nightLevel());   // Simon 26.10.
            }
            float splash = (float) ((taken + extra) * Math.max(cleaveShare, falling ? SkillValues.get("w_earthshock", "splash", 0.5) : 0.0));
            double splashRadius = cleave > 0 ? SkillValues.get("w_cleave", "radius", 2.5) : 0.0;
            if (falling) {
                splashRadius = Math.max(splashRadius, SkillValues.get("w_earthshock", "radius", 2.5));
            }
            float crusaderHeal = (float) SkillValues.get("w_crusader", "heal", 0.5);
            float crusaderMax = (float) SkillValues.get("w_crusader", "max", 3);
            float healed = 0f;
            for (LivingEntity near : enemies(level, victim.position(), splashRadius, 1.5)) {
                if (near == victim) {
                    continue;
                }
                float before = near.getHealth();
                followUpHit(level, near, source, splash);
                cleaved += axeSteal ? Math.max(0f, before - near.getHealth()) : 0f;
                if (crusader && cleave > 0) {
                    if (isUndead(near)) {
                        near.igniteForSeconds((float) (SkillValues.get("w_crusader", "burn", 60) / 20.0));
                    }
                    if (healed + crusaderHeal <= crusaderMax + 1.0e-4f) {
                        player.heal(crusaderHeal);
                        healed += crusaderHeal;
                    }
                }
            }
            if (falling) {
                level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY(), victim.getZ(), 2, 0.6, 0.1, 0.6, 0.0);
                play(level, player, SoundEvents.MACE_SMASH_GROUND, 0.8f, 1.0f);
            }
        }

        // Simon 26.10.: the berserker's bloodlust splinters onto the monsters around the target,
        // 15 % of the hit plus up to 15 % more the less life he has.
        if (skills.clazz() == SkillClass.WARRIOR && SkillTree.SUB_BERSERKER.equals(tree.subclassOf(skills))) {
            double lost = 1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth());
            double share = SkillValues.get("bloodlust", "splash", 0.15)
                    + SkillValues.get("bloodlust", "splashlow", 0.15) * Math.max(0.0, Math.min(1.0, lost));
            float shard = (float) ((taken + extra) * share);
            if (shard > 0.05f) {
                DamageSource source = level.damageSources().playerAttack(player);
                for (LivingEntity near : enemies(level, victim.position(), SkillValues.get("bloodlust", "radius", 2.5), 1.5)) {
                    if (near != victim) {
                        followUpHit(level, near, source, shard);
                    }
                }
            }
        }

        extra += guardian.meleeExtra(level, player, skills, victim, taken, now);
        // Life steal on hits only for the berserker below half his life (Simon, 25 October 2026);
        // the other warriors and the shield fighter heal from kills (see onKill).
        // The share is the skill value bloodlust.steal (no longer the config's berserkerLifesteal).
        if (skills.clazz() == SkillClass.WARRIOR && SkillTree.SUB_BERSERKER.equals(tree.subclassOf(skills))
                && player.getHealth() < player.getMaxHealth() * SkillValues.get("bloodlust", "below", 0.5)) {
            // Blutklinge (Simon 26.10.): +5 % life steal in the bloodlust (no longer tied to Parade).
            double steal = SkillValues.get("bloodlust", "steal", 0.12)
                    + (SkillTree.has(skills, "w_bloodblade") ? SkillValues.get("w_bloodblade", "steal", 0.05) : 0.0);
            if (steal > 0) {
                player.heal((float) ((taken + extra) * steal));
            }
        }
        // Axe fighter, blademaster and paladin: a share of every hit heals (Simon 29.10., config hitLifesteal),
        // more on the low levels and at least half a heart (balance update 2, 29.10.).
        if (skills.clazz() == SkillClass.WARRIOR && taken + extra > 0) {
            stealOnHit(player, tree.subclassOf(skills), taken + extra, cleaved, now);
        }
        return extra;
    }

    /** Players' last hit that healed the minimum (game time), for hitLifestealMinTicks. */
    private final Map<UUID, Long> minHealAt = new HashMap<>();

    /**
     * The hit life steal of the axe fighter, the blademaster and the paladin (Simon 29.10.: solo they
     * healed only 9–20 % of what they took): the share of the hit times the low-level factor, at least
     * hitLifestealMinHeal (at most once per hitLifestealMinTicks), and the cleave at half the rate.
     */
    private void stealOnHit(ServerPlayer player, String subclass, double dealt, double cleaved, long now) {
        SiegeManager manager = SiegeManager.get();
        if (manager == null || manager.config().hitLifesteal(subclass) <= 0 || dealt <= 0) {
            return;
        }
        com.mki.nightsiege.core.SiegeConfig cfg = manager.config();
        int level = nightLevel();
        Long last = minHealAt.get(player.getUUID());
        boolean minReady = last == null || now < last || now - last >= cfg.hitLifestealMinTicks;
        if (minReady && dealt * cfg.hitLifesteal(subclass) * cfg.hitLifestealLevelFactor(level) < cfg.hitLifestealMinHeal) {
            minHealAt.put(player.getUUID(), now);   // the floor is used: the next one in hitLifestealMinTicks
        }
        float heal = (float) hitHeal(cfg, subclass, dealt, cleaved, level, minReady);
        if (heal > 0f) {
            player.heal(heal);
        }
    }

    /**
     * What one melee hit of this subclass heals (also for the tests): {@code dealt} × hitLifesteal × the
     * factor of the level, at least hitLifestealMinHeal when {@code minReady}, plus {@code cleaved} (the
     * axe fighter's cleave) at hitLifestealCleave of that rate.
     */
    public static double hitHeal(com.mki.nightsiege.core.SiegeConfig cfg, String subclass, double dealt, double cleaved,
                                 int level, boolean minReady) {
        double share = cfg.hitLifesteal(subclass) * cfg.hitLifestealLevelFactor(level);
        if (share <= 0 || dealt <= 0) {
            return 0.0;
        }
        double heal = dealt * share;
        if (minReady && heal < cfg.hitLifestealMinHeal) {
            heal = cfg.hitLifestealMinHeal;
        }
        return heal + Math.max(0.0, cleaved) * share * cfg.hitLifestealCleave;
    }

    /**
     * Every warrior and the shield fighter heal a little from their melee hits, so they hold
     * out without a healer (config meleeLifesteal). Not the berserker: he has his own steal.
     * Not the blademaster either: he heals only from kills in a combo (see {@link #onKill}).
     */
    private double baseLifesteal(PlayerSkills skills) {
        SiegeManager m = SiegeManager.get();
        if (m == null || skills == null) {
            return 0.0;
        }
        String sub = tree.subclassOf(skills);
        boolean melee = skills.clazz() == SkillClass.WARRIOR && !SkillTree.SUB_BERSERKER.equals(sub)
                && !SkillTree.SUB_BLADEMASTER.equals(sub)
                || skills.clazz() == SkillClass.GUARDIAN && SkillTree.SUB_SHIELDFIGHTER.equals(sub);
        if (!melee || m.config().hitLifesteal(sub) > 0) {
            return 0.0;   // life steal on hits instead (axe fighter, paladin; Simon 29.10.)
        }
        // Simon 26.10.: the axe fighter heals half as much from kills.
        return m.config().meleeLifesteal * (SkillTree.SUB_AXE.equals(sub) ? SkillValues.get("heavy_blow", "lifesteal", 0.5) : 1.0);
    }

    /**
     * Simon 26.10.: the axe fighter's cleave does 20 % less up to world level 10, then grows back
     * in a line to its full strength at level 20.
     */
    static double axeCleaveScale(int level) {
        double cut = SkillValues.get("heavy_blow", "groupcut", 0.2);
        double from = SkillValues.get("heavy_blow", "groupfull1", 10);
        double to = SkillValues.get("heavy_blow", "groupfull2", 20);
        if (level <= from) {
            return 1.0 - cut;
        }
        if (level >= to || to <= from) {
            return 1.0;
        }
        return 1.0 - cut * (to - level) / (to - from);
    }

    /** The world level of the running night (the coming one outside a night). */
    private static int nightLevel() {
        SiegeManager m = SiegeManager.get();
        return m == null ? 1 : m.currentLevel();
    }

    /** True when the player holds the weapon of their warrior way (or is no warrior). */
    private boolean holdsWayWeapon(ServerPlayer player, PlayerSkills skills) {
        if (skills == null || skills.clazz() != SkillClass.WARRIOR) {
            return true;
        }
        ItemStack weapon = player.getMainHandItem();
        String way = skills.pathTaken(tree);
        if (SkillTree.WAY_BLADE.equals(way)) {
            return weapon.is(ItemTags.SWORDS);
        }
        if (SkillTree.WAY_MIGHT.equals(way)) {
            return weapon.is(ItemTags.AXES) || weapon.getItem() instanceof MaceItem;
        }
        return true;
    }

    /** A kill readies the strong next hit of the momentum talent. */
    public void onKill(ServerPlayer player, Entity killed) {
        if (!(killed instanceof Enemy) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills != null && tree.totalBonus(SkillEffect.MOMENTUM, skills) > 0) {
            momentumUntil.put(player.getUUID(), level.getGameTime() + (long) SkillValues.get("w_momentum", "window", 60));
        }
        if (skills != null && tree.totalBonus(SkillEffect.RELOAD_RUSH, skills) > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) SkillValues.get("a_reloadrush", "dur", 60), 1));
        }
        // Blutklinge without Parade: every kill heals a little instead of every parry.
        // Warriors (not berserker and blademaster) and the shield fighter heal from kills only:
        // a share of the slain monster's life (config meleeLifesteal, Simon 25 October 2026).
        if (skills != null && killed instanceof LivingEntity slain) {
            double share = baseLifesteal(skills);
            if (share > 0) {
                player.heal((float) (slain.getMaxHealth() * share));
            }
        }
        // The blademaster's life steal: only a kill in the middle of a combo heals (Simon, 24 Oct 2026).
        SiegeManager sm = SiegeManager.get();
        boolean onHits = sm != null && skills != null && sm.config().hitLifesteal(tree.subclassOf(skills)) > 0;
        if (skills != null && !onHits && SkillTree.SUB_BLADEMASTER.equals(tree.subclassOf(skills)) && killed instanceof LivingEntity dead) {
            long[] streak = combos.get(player.getUUID());
            long now = level.getGameTime();
            // The share is the skill value w_combo.killheal (no longer the config's comboKillLifesteal).
            if (streak != null && streak[0] >= (long) SkillValues.get("w_combo", "killstreak", 2)
                    && now - streak[1] <= (long) SkillValues.get("w_combo", "window", 40)) {
                float heal = (float) (dead.getMaxHealth() * SkillValues.get("w_combo", "killheal", 0.15));
                if (heal > 0) {
                    player.heal(heal);
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1.1), player.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                }
            }
        }
    }

    /**
     * Returns false when a warrior avoids a monster's damage completely: a dodge,
     * a parry (with a strike back) or the divine shield.
     */
    public boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (dealingBonus || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || !(source.getEntity() instanceof LivingEntity attacker) || !(attacker instanceof Enemy) || amount <= 0) {
            return true;
        }
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != SkillClass.WARRIOR) {
            return true;
        }
        long now = level.getGameTime();
        double dodge = tree.totalBonus(SkillEffect.DODGE, skills);
        if (dodge > 0 && random.nextDouble() < dodge) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) SkillValues.get("w_evasion", "speed", 30), 0));
            play(level, player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.6f);
            return false;
        }
        double parry = tree.totalBonus(SkillEffect.PARRY, skills);
        boolean melee = source.getDirectEntity() == attacker;
        if (parry > 0 && melee && holdsWayWeapon(player, skills) && random.nextDouble() < parry) {
            bonusHit(level, attacker, level.damageSources().playerAttack(player), (float) SkillValues.get("w_parry", "counter", 4));
            pushAway(attacker, player.position(), 0.7, 0.2);
            double heal = tree.totalBonus(SkillEffect.PARRY_HEAL, skills);
            if (heal > 0) {
                player.heal((float) heal);
            }
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY(0.6), player.getZ(), 8, 0.4, 0.3, 0.4, 0.1);
            play(level, player, SoundEvents.SHIELD_BLOCK.value(), 0.9f, 1.5f);
            return false;
        }
        if (amount >= SkillValues.get("w_divine_shield", "min", 4) && SkillTree.has(skills, "w_divine_shield")
                && now >= divineReadyAt.getOrDefault(player.getUUID(), 0L)) {
            divineReadyAt.put(player.getUUID(), now + (long) SkillValues.get("w_divine_shield", "every", 600));
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 20, 0.5, 0.6, 0.5, 0.05);
            play(level, player, SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.8f);
            actionBar(player, Component.translatable("nightsiege.msg.divine_shield"));
            return false;
        }
        return true;
    }

    /** (arrow id, victim id) pairs whose hit was handled, so a killing hit is not handled twice. */
    private final java.util.Set<Long> handledHits = new java.util.HashSet<>();

    /**
     * A bolt or arrow that kills still does what it does on a hit (Simon, 27 October 2026: explosive
     * bolts did not blow when they killed and flew on). Fabric's AFTER_DAMAGE leaves out killing
     * hits, so this comes from the death of the monster.
     */
    public void afterKillingHit(LivingEntity victim, DamageSource source) {
        if (!dealingBonus && victim instanceof Enemy && source.getEntity() instanceof ServerPlayer striker
                && source.getDirectEntity() == striker) {
            killingBlowSteal(striker, victim);
        }
        if (dealingBonus || !(victim.level() instanceof ServerLevel level) || !(victim instanceof Enemy)
                || !(source.getEntity() instanceof ServerPlayer player) || !(source.getDirectEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        PlayerSkills skills = skillsFor.apply(player);
        // What the hit did, about: the arrow's damage at its speed, at most the monster's life.
        float taken = (float) Math.min(victim.getMaxHealth(),
                Math.max(1.0, ((com.mki.nightsiege.mixin.ArrowAccessor) arrow).nightsiege$baseDamage()
                        * arrow.getDeltaMovement().length()));
        if (skills != null) {
            arrowHit(level, player, skills, victim, arrow, taken, level.getGameTime());
        }
        // Charged and blasting turret bolts too.
        if ((arrow.entityTags().contains(TrapService.TAG_CHARGED) || arrow.entityTags().contains(TrapService.TAG_BLAST))
                && handledHits.add(turretKey(arrow, victim))) {
            dealingBonus = true;
            try {
                traps.onTurretArrowHit(level, victim, arrow, player, taken);
            } finally {
                dealingBonus = false;
            }
        }
    }

    /**
     * A killing melee blow heals like any other hit (balance update 2, 29.10.: at the low levels most
     * blows kill, and AFTER_DAMAGE leaves them out). What it did: about the weapon's damage, at most
     * the monster's life.
     */
    private void killingBlowSteal(ServerPlayer player, LivingEntity victim) {
        PlayerSkills skills = skillsFor.apply(player);
        if (skills == null || skills.clazz() != SkillClass.WARRIOR || !holdsWayWeapon(player, skills)) {
            return;
        }
        double dealt = Math.min(victim.getMaxHealth(), player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        stealOnHit(player, tree.subclassOf(skills), dealt, 0.0, player.level().getGameTime());
    }

    private static long turretKey(AbstractArrow arrow, LivingEntity victim) {
        return (((long) arrow.getId() << 32) ^ (victim.getId() & 0xffffffffL)) ^ 0x4000000000000000L;
    }

    /** Returns the extra damage of an arrow or bolt hit; side effects are applied here. */
    private double arrowHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                            LivingEntity victim, AbstractArrow arrow, float taken, long now) {
        long key = ((long) arrow.getId() << 32) ^ (victim.getId() & 0xffffffffL);
        if (handledHits.size() > 2048) {
            handledHits.clear();
        }
        if (!handledHits.add(key)) {
            return 0.0;
        }
        ItemStack weapon = arrow.getWeaponItem();
        boolean crossbow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
        boolean bow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.BowItem;
        DamageSource source = level.damageSources().playerAttack(player);
        double extra = 0.0;

        // A bolt with vanilla Piercing hits several monsters: its skills fire only on the first one
        // (balance update, 26 October 2026: nets, blasts and pierce hits used to repeat on every monster).
        if (skills.clazz() == SkillClass.ARCHER) {
            if (arrow.entityTags().contains(TAG_STRUCK)) {
                return 0.0;
            }
            arrow.entityTags().add(TAG_STRUCK);
        }

        // Bolts of the ultimates carry their own effect.
        if (arrow.entityTags().contains(TAG_BARRAGE)) {
            burst(level, player, victim.position(), SkillValues.get("a_barrage", "radius", 3.0),
                    (float) SkillValues.get("a_barrage", "dmg", 6.0));
        }
        if (arrow.entityTags().contains(TAG_NET)) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get("a_widenet", "dur", 80), 2));
        }
        boolean sniperShot = arrow.entityTags().contains(ArcherEffects.SNIPER_TAG);
        if (sniperShot) {
            pierceBehind(level, player, victim, arrow, taken * (float) SkillValues.get("a_sniper", "share", 0.8),
                    (int) SkillValues.get("a_sniper", "count", 6));
        }

        if (skills.clazz() == SkillClass.ARCHER) {
            String way = skills.pathTaken(tree);
            if ((SkillTree.WAY_BOW.equals(way) && !bow) || (SkillTree.WAY_CROSSBOW.equals(way) && !crossbow)) {
                return 0.0;   // the skills of a way only work with its weapon
            }
        }

        Mark before = marks.get(victim.getUUID());
        boolean wasMarked = before != null && now <= before.until();
        boolean wasSlowed = victim.hasEffect(MobEffects.SLOWNESS);
        double markBonus = tree.totalBonus(SkillEffect.MARK, skills);
        boolean watchful = SkillTree.has(skills, "a_watchful") && crossbow;
        if (watchful) {
            // Watchful eye: every bolt marks, and the marks of your bolts are this much stronger.
            markBonus += tree.totalBonus(SkillEffect.WATCHFUL, skills);
        }
        if (markBonus > 0) {
            String markNode = SkillTree.has(skills, "x_mark") ? "x_mark" : SkillTree.has(skills, "b_mark") ? "b_mark" : "a_watchful";   // all 6 s
            mark(victim, markBonus, now + (long) SkillValues.get(markNode, "dur", 120), true);
        }
        double deathMark = tree.totalBonus(SkillEffect.DEATH_MARK, skills);
        if (wasMarked && deathMark > 0) {
            extra += taken * deathMark;
        }
        double hunterNet = tree.totalBonus(SkillEffect.HUNTER_NET, skills);
        if (hunterNet > 0 && crossbow && (wasMarked || wasSlowed)) {
            extra += taken * hunterNet;   // hunter's net: caught prey takes more from your bolts
        }
        double longshot = tree.totalBonus(SkillEffect.LONGSHOT, skills);
        double far = SkillValues.get("a_longshot", "range", 15.0);
        if (longshot > 0 && player.distanceToSqr(victim) > far * far) {
            extra += taken * longshot;
        }
        double crit = tree.totalBonus(SkillEffect.ARROW_CRIT, skills);
        if (crit > 0 && random.nextDouble() < crit) {
            extra += taken * SkillValues.get("a_eagle", "crit", 0.5);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
        }
        double brace = tree.totalBonus(SkillEffect.BRACE, skills);
        if (brace > 0 && crossbow && arrow.entityTags().contains(ArcherEffects.BRACED_TAG)) {
            extra += taken * brace;
        }
        // Fire arrows: burning monsters take more from your arrows, so the node still counts
        // once the kit bow has Flame (balance update, 26 October 2026).
        if (tree.totalBonus(SkillEffect.FIRE_ARROWS, skills) > 0 && victim.isOnFire()) {
            extra += taken * SkillValues.get("a_fire", "bonus", 0.15);
        }
        // Fire volley: a burning arrow also sets the monsters around its target alight, so the node
        // counts for every bow build, not only for the ones with extra arrows (balance update, 26 October 2026).
        if (tree.totalBonus(SkillEffect.FIRE_VOLLEY, skills) > 0 && arrow.isOnFire()) {
            float burn = (float) (SkillValues.get("a_firevolley", "burn", 80) / 20.0);
            for (LivingEntity near : enemies(level, victim.position(), SkillValues.get("a_firevolley", "radius", 2.0), 1.5)) {
                if (near != victim) {
                    near.igniteForSeconds(burn);
                }
            }
        }
        if (tree.totalBonus(SkillEffect.RETREAT, skills) > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) SkillValues.get("a_retreat", "dur", 40), 0, true, false, true));
        }
        if (tree.totalBonus(SkillEffect.ARROW_KNOCKBACK, skills) > 0) {
            pushAway(victim, player.position(), SkillValues.get("a_knockarrow", "push", 1.0),
                    SkillValues.get("a_knockarrow", "lift", 0.25));
        }
        double sunder = tree.totalBonus(SkillEffect.BOLT_SUNDER, skills);
        if (sunder > 0 && crossbow) {
            // Steel bolts tear the armour open: their own bonus, on top of any mark.
            long until = now + (long) SkillValues.get("a_steelbolts", "dur", 100);
            Mark old = boltCracks.get(victim.getUUID());
            if (old == null || now > old.until() || sunder >= old.bonus()) {
                boltCracks.put(victim.getUUID(), new Mark(sunder, until));
            }
        }
        double poison = tree.totalBonus(SkillEffect.POISON_ARROWS, skills);
        if (poison > 0) {
            boolean already = victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER);
            int venom = (int) SkillValues.get("a_poison", "dur", 80);
            victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, venom, 1));
            if (already && tree.totalBonus(SkillEffect.VENOM_BURST, skills) > 0) {
                int cloud = (int) SkillValues.get("a_venomburst", "dur", 80);
                for (LivingEntity near : enemies(level, victim.position(), SkillValues.get("a_venomburst", "radius", 3.0), 1.5)) {
                    near.addEffect(new MobEffectInstance(isUndead(near) ? MobEffects.WITHER : MobEffects.POISON, cloud, 1));
                }
                level.sendParticles(TrapService.POISON_DUST, victim.getX(), victim.getY(0.5), victim.getZ(),
                        40, 1.2, 0.5, 1.2, 0.0);
            }
        }

        // Crossbow: nets, shackles, bursts.
        if (crossbow) {
            double net = tree.totalBonus(SkillEffect.NET_BOLT, skills);
            if (net > 0 && CrateEffects.gainLevel(player, "teernetz") > 0 && victim instanceof net.minecraft.world.entity.Mob caught) {
                Besiegers.disrupt(caught);   // Teernetz: the ladder or the fuse is dropped, it just fights
            }
            if (net > 0) {
                int slow = (int) Math.round(20 * net);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 1));
                // More crowd control (Simon, 27 October 2026): the net catches the monsters next to the target too.
                double splash = SkillValues.get("a_netbolt", "splash", 2.0);
                if (splash > 0) {
                    for (LivingEntity near : enemies(level, victim.position(), splash, 1.5)) {
                        if (near != victim) {
                            near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow / 2, 1));
                        }
                    }
                    level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY(0.4), victim.getZ(),
                            8, splash * 0.4, 0.2, splash * 0.4, 0.0);
                }
            }
            if (tree.totalBonus(SkillEffect.NET_FIELD, skills) > 0) {
                int field = (int) SkillValues.get("a_snarefield", "dur", 60);
                for (LivingEntity near : enemies(level, victim.position(), SkillValues.get("a_snarefield", "radius", 3.0), 1.5)) {
                    near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, field, (int) SkillValues.get("a_snarefield", "level", 3)));
                }
                level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY(0.3), victim.getZ(),
                        20, 1.2, 0.2, 1.2, 0.0);
            }
            if (tree.totalBonus(SkillEffect.SHACKLE, skills) > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) SkillValues.get("a_shackle", "dur", 40), 9));
            }
            double explosive = tree.totalBonus(SkillEffect.EXPLOSIVE, skills);
            boolean cluster = tree.totalBonus(SkillEffect.CLUSTER, skills) > 0;
            if (explosive > 0 || cluster) {
                double blast = cluster ? Math.max(explosive, SkillValues.get("a_clusterbolt", "base", 3.0))
                        + SkillValues.get("a_clusterbolt", "bonus", 4.0) : explosive;
                burst(level, player, victim.position(),
                        cluster ? SkillValues.get("a_clusterbolt", "radius", 4.0) : SkillValues.get("a_explosive", "radius", 2.5),
                        (float) blast);
            }
        }

        double shrapnel = tree.totalBonus(SkillEffect.SHRAPNEL, skills);
        if (shrapnel > 0) {
            Vec3 at = victim.position();
            double splash = SkillValues.get(crossbow ? "shrapnel" : "b_cross_shrapnel", "radius", 2.5);
            for (LivingEntity near : enemies(level, at, splash, 1.5)) {
                if (near != victim) {
                    followUpHit(level, near, source, (float) (taken * shrapnel));
                }
            }
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 10, 0.6, 0.4, 0.6, 0.2);
        }
        int pierce = (int) Math.round(tree.totalBonus(SkillEffect.PIERCE, skills));
        if (pierce > 0 && !sniperShot) {   // the sniper's shot already goes through the line on its own
            // Each way's pierce node carries the share it deals behind the target.
            String pierceNode = crossbow ? "a_punch" : SkillTree.has(skills, "piercing") ? "piercing" : "a_pierce";
            pierceBehind(level, player, victim, arrow, taken * (float) SkillValues.get(pierceNode, "share", 0.7), pierce);
        }
        return extra;
    }

    /** A small blast that hurts monsters only: no blocks, no players. */
    private void burst(ServerLevel level, ServerPlayer player, Vec3 at, double radius, float damage) {
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity near : enemies(level, at, radius, 1.5)) {
            bonusHit(level, near, source, damage);
            pushAway(near, at, 0.4, 0.3);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    /** The arrow goes on: up to {@code count} monsters behind the target in its line of flight. */
    private void pierceBehind(ServerLevel level, ServerPlayer player, LivingEntity victim, AbstractArrow arrow,
                              float amount, int count) {
        Vec3 motion = arrow.getDeltaMovement();
        Vec3 dir = new Vec3(motion.x, 0, motion.z);
        if (dir.lengthSqr() < 1.0e-4 || amount <= 0) {
            dir = flatLook(player);
        } else {
            dir = dir.normalize();
        }
        if (dir == null) {
            return;
        }
        Vec3 from = victim.position();
        Vec3 d = dir;
        double reach = SkillValues.get("a_pierce", "range", 8.0);
        List<LivingEntity> line = new ArrayList<>(enemies(level, from, reach + 0.5, 2.0).stream()
                .filter(e -> e != victim)
                .filter(e -> {
                    Vec3 to = e.position().subtract(from);
                    double along = to.x * d.x + to.z * d.z;
                    double side = Math.abs(to.x * d.z - to.z * d.x);
                    return along > 0 && along <= reach && side <= 1.0;
                })
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                .toList());
        DamageSource source = level.damageSources().playerAttack(player);
        for (int i = 0; i < Math.min(count, line.size()); i++) {
            LivingEntity behind = line.get(i);
            followUpHit(level, behind, source, amount);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, behind.getX(), behind.getY(0.6), behind.getZ(),
                    6, 0.2, 0.3, 0.2, 0.1);
        }
    }

    /** The first monster up to three blocks behind the target, in the line of the thrust. */
    private void impaleBehind(ServerLevel level, ServerPlayer player, LivingEntity victim, float amount) {
        Vec3 dir = flatLook(player);
        if (dir == null || amount <= 0) {
            return;
        }
        Vec3 from = victim.position();
        LivingEntity behind = enemies(level, from, 3.5, 1.5).stream()
                .filter(e -> e != victim)
                .filter(e -> {
                    Vec3 to = e.position().subtract(from);
                    double along = to.x * dir.x + to.z * dir.z;
                    double side = Math.abs(to.x * dir.z - to.z * dir.x);
                    return along > 0 && along <= 3.0 && side <= 1.0;
                })
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                .orElse(null);
        if (behind != null) {
            followUpHit(level, behind, level.damageSources().playerAttack(player), amount);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, behind.getX(), behind.getY(0.6), behind.getZ(),
                    6, 0.2, 0.3, 0.2, 0.1);
        }
    }

    /**
     * One mark per monster, a pair of bonus and end. A new mark replaces the old one only when it is
     * at least as strong or the old one has run out; it never lends its length to a stronger mark
     * (balance update, 26 October 2026: a weak, long mark kept the hunt's strong one alive).
     */
    private void mark(LivingEntity victim, double bonus, long until, boolean glow) {
        Mark old = marks.get(victim.getUUID());
        long now = victim.level().getGameTime();
        boolean active = old != null && now <= old.until();
        if (!active || bonus > old.bonus() + 1.0e-9) {
            marks.put(victim.getUUID(), new Mark(bonus, until));
        } else if (Math.abs(bonus - old.bonus()) <= 1.0e-9 && until > old.until()) {
            marks.put(victim.getUUID(), new Mark(bonus, until));   // the same mark again: it lasts longer
        }
        if (glow) {
            Mark kept = marks.get(victim.getUUID());   // glows as long as the mark that counts
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.max(20, kept.until() - now), 0));
        }
    }

    /** Blasts of an engineer: stronger for the pyromaniac, weaker for the plague doctor. */
    private double engineerBlast(PlayerSkills skills) {
        double scale = 1.0;
        if (SkillTree.has(skills, "e_pyro")) {
            scale *= 1.0 + tree.totalBonus(SkillEffect.PYROMANIAC, skills);
        }
        if (SkillTree.has(skills, "e_plague_doctor")) {
            scale *= 1.0 - SkillValues.get("e_plague_doctor", "blast", 0.3);
        }
        return scale;
    }

    /** The engineer's remote marks a monster: it glows and takes more damage. */
    public void remoteMark(LivingEntity victim, double bonus, long until, boolean glow) {
        mark(victim, bonus, until, glow);
    }

    /** Deals damage the mod adds on top, through the hit cooldown, without boosting it again. */
    /**
     * A follow-up hit whose amount comes from a hit that already went through the class and
     * subclass factor (pierce, cleave, shrapnel, marks, crits): it must not get that factor
     * a second time (balance update, 26 October 2026: heavy shooters hit 122 % behind the target).
     */
    void followUpHit(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        followUp = true;
        try {
            bonusHit(level, victim, source, amount);
        } finally {
            followUp = false;
        }
    }

    /** Set during {@link #followUpHit}: the damage factor of the attacker's class is already in. */
    private static boolean followUp;

    /** True while a follow-up hit is dealt; read by the damage scaling. */
    static boolean inFollowUp() {
        return followUp;
    }

    void bonusHit(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        dealingBonus = true;
        try {
            victim.invulnerableTime = 0;
            victim.hurtServer(level, source, amount);
        } finally {
            dealingBonus = false;
        }
    }

    // ------------------------------------------------------------------
    // guardian angel and healing
    // ------------------------------------------------------------------

    /** Returns false when a guardian angel catches this player. */
    public boolean allowDeath(LivingEntity dying) {
        if (!(dying instanceof ServerPlayer victim) || !(victim.level() instanceof ServerLevel level)) {
            return true;
        }
        for (ServerPlayer guardian : level.players()) {
            if (angelUsed.contains(guardian.getUUID())) {
                continue;
            }
            PlayerSkills skills = skillsFor.apply(guardian);
            if (skills == null || tree.totalBonus(SkillEffect.GUARDIAN_ANGEL, skills) <= 0) {
                continue;
            }
            // Reach and health come from the skill values (before: the amount was read as a rank).
            double reach = com.mki.nightsiege.core.SkillValues.get("guardian_angel", "reach", 24.0);
            if (guardian != victim && guardian.distanceToSqr(victim) > reach * reach) {
                continue;
            }
            angelUsed.add(guardian.getUUID());
            victim.setHealth((float) com.mki.nightsiege.core.SkillValues.get("guardian_angel", "hp", 8.0));
            victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    (int) Math.round(com.mki.nightsiege.core.SkillValues.get("guardian_angel", "regen", 100.0)), 1));
            victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                    (int) Math.round(com.mki.nightsiege.core.SkillValues.get("guardian_angel", "res", 60.0)), 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY(1.0), victim.getZ(),
                    40, 0.4, 0.6, 0.4, 0.4);
            play(level, victim, SoundEvents.TOTEM_USE, 0.8f, 1.2f);
            victim.sendSystemMessage(Component.translatable("nightsiege.msg.angel_saved", guardian.getDisplayName()));
            if (guardian != victim) {
                guardian.sendSystemMessage(Component.translatable("nightsiege.msg.angel_used", victim.getDisplayName()));
            }
            return false;
        }
        return true;
    }

    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        currentTime = now;
        if (!delayed.isEmpty()) {
            List<Delayed> due = new ArrayList<>();
            delayed.removeIf(d -> {
                if (d.due() <= now) {
                    due.add(d);
                    return true;
                }
                return false;
            });
            for (Delayed d : due) {
                d.action().run();
            }
        }
        tickCounter++;
        guardian.tick(server);
        if (tickCounter % 10 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                warriorTick(player, tickCounter);
                archerTick(player, now);
            }
        }
        if (++healCounter < HEAL_INTERVAL) {
            return;
        }
        healCounter = 0;
        marks.values().removeIf(m -> m.until() < now);
        boltCracks.values().removeIf(m -> m.until() < now);
        for (ServerPlayer medic : server.getPlayerList().getPlayers()) {
            PlayerSkills skills = skillsFor.apply(medic);
            if (GuardianEffects.isGuardian(skills)) {
                continue;   // the guardian's field dressing pulses in GuardianEffects (its own numbers)
            }
            double heal = skills == null ? 0 : tree.totalBonus(SkillEffect.HEAL_AURA, skills);
            boolean martyr = SkillTree.has(skills, "w_martyr");
            if ((heal <= 0 && !martyr) || !(medic.level() instanceof ServerLevel level)) {
                continue;
            }
            double radius = HEAL_RADIUS + guardian.auraRange(skills);
            for (ServerPlayer mate : GuardianEffects.mates(level)) {
                if (!mate.isAlive() || mate.distanceToSqr(medic) > radius * radius) {
                    continue;
                }
                float amount = (float) heal + (martyr && mate != medic ? (float) SkillValues.get("w_martyr", "heal", 1.5) : 0f);
                if (amount > 0 && guardian.heal(medic, mate, amount) > 0) {
                    level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 2.0, mate.getZ(),
                            1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    /** Every half second: crossbow speed and the tripwire. */
    private void archerTick(ServerPlayer player, long now) {
        AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
        PlayerSkills skills = skillsFor.apply(player);
        boolean archer = skills != null && skills.clazz() == SkillClass.ARCHER && player.isAlive();
        double speed = 0.0;
        if (archer && player.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            speed = tree.totalBonus(SkillEffect.XBOW_SPEED, skills);
        }
        if (move != null) {
            if (speed > 0.0001) {
                move.addOrUpdateTransientModifier(new AttributeModifier(XBOW_SPEED, speed,
                        AttributeModifier.Operation.ADD_VALUE));
            } else if (move.getModifier(XBOW_SPEED) != null) {
                move.removeModifier(XBOW_SPEED);
            }
        }
        if (!archer || !(player.level() instanceof ServerLevel level)
                || tree.totalBonus(SkillEffect.TRIPWIRE, skills) <= 0
                || now < tripReadyAt.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        List<LivingEntity> close = enemies(level, player.position(), SkillValues.get("a_tripwire", "radius", 3.0), 1.5);
        if (close.isEmpty()) {
            return;
        }
        double master = tree.totalBonus(SkillEffect.TRAPMASTER, skills);
        DamageSource source = level.damageSources().playerAttack(player);
        int trip = (int) SkillValues.get("a_tripwire", "dur", 40);
        for (LivingEntity victim : close) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, trip, master > 0 ? 9 : 1));
            pushAway(victim, player.position(), 0.6, 0.1);
            if (master > 0) {
                bonusHit(level, victim, source, (float) master);
            }
        }
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.2, player.getZ(), 16, 1.5, 0.1, 1.5, 0.05);
        play(level, player, SoundEvents.TRIPWIRE_CLICK_ON, 1.0f, 1.0f);
        tripReadyAt.put(player.getUUID(), now + (long) SkillValues.get("a_tripwire", "cd", 100));
    }

    /** Every half second: the warrior's attack speed, war cry, vow and the unstoppable keystone. */
    private void warriorTick(ServerPlayer player, int counter) {
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        PlayerSkills skills = skillsFor.apply(player);
        boolean warrior = skills != null && skills.clazz() == SkillClass.WARRIOR && player.isAlive();
        double bonus = 0.0;
        if (warrior && holdsWayWeapon(player, skills)) {
            String way = skills.pathTaken(tree);
            // Axes and swords feel slow in Minecraft: the way itself already helps a little.
            bonus += SkillTree.WAY_MIGHT.equals(way) ? SkillValues.get("w_heavyswing", "way", 0.3)
                    : SkillTree.WAY_BLADE.equals(way) ? SkillValues.get("w_bladedance", "way", 0.2) : 0.0;
            bonus += tree.totalBonus(SkillEffect.WEAPON_SPEED, skills);
            double frenzy = tree.totalBonus(SkillEffect.FRENZY, skills);
            bonus += frenzy * (1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth()));
        }
        if (speed != null) {
            if (bonus > 0.001) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(WARRIOR_SPEED, bonus,
                        AttributeModifier.Operation.ADD_VALUE));
            } else if (speed.getModifier(WARRIOR_SPEED) != null) {
                speed.removeModifier(WARRIOR_SPEED);
            }
        }
        if (!warrior || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (SkillTree.has(skills, "w_unstoppable")) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        if (tree.totalBonus(SkillEffect.WARCRY, skills) > 0
                && counter % Math.max(10, (int) SkillValues.get("w_warcry", "every", 40)) == 0) {
            int time = (int) SkillValues.get("w_warcry", "time", 60);
            for (LivingEntity victim : enemies(level, player.position(), SkillValues.get("w_warcry", "radius", 5), 2.0)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, time, 0));
            }
        }
        if (tree.totalBonus(SkillEffect.VOW, skills) > 0
                && counter % Math.max(10, (int) SkillValues.get("w_vow", "every", 40)) == 0) {
            double r = SkillValues.get("w_vow", "radius", 6);
            int time = (int) SkillValues.get("w_vow", "time", 60);
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(player) <= r * r) {
                    mate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, time, 0, true, false, true));
                }
            }
        }
    }

    /** Ticks until this player's ultimate is ready again. */
    public int cooldownLeft(UUID player, long now) {
        long readyAt = ultReadyAt.getOrDefault(player, 0L);
        return (int) Math.max(0L, readyAt - now);
    }

    /** Length of the cooldown currently running, or the given default. */
    public int cooldownTotal(UUID player, int fallback) {
        return ultTotal.getOrDefault(player, fallback);
    }

    /** Full cooldown at this rank. */
    public static int cooldownFor(int rank) {
        return 600 - 100 * Math.max(1, rank);
    }

    public void setArrowBonus(java.util.function.ToDoubleFunction<ServerPlayer> bonus) {
        this.arrowBonus = bonus;
    }

    public void setCooldownScale(java.util.function.ToDoubleFunction<ServerPlayer> scale) {
        this.cooldownScale = scale;
    }

    public void forget(UUID player) {
        ultReadyAt.remove(player);
        combos.remove(player);
        momentumUntil.remove(player);
        divineReadyAt.remove(player);
        tripReadyAt.remove(player);
        guardian.forget(player);
    }

    // ------------------------------------------------------------------

    static boolean isUndead(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(e.getType()).is(EntityTypeTags.UNDEAD);
    }

    /** Monsters within a horizontal radius and a vertical band around a point. */
    static List<LivingEntity> enemies(ServerLevel level, Vec3 center, double r, double up) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : TrapService.enemies(level, new AABB(center, center).inflate(r, up, r))) {
            double dx = e.getX() - center.x;
            double dz = e.getZ() - center.z;
            if (dx * dx + dz * dz <= r * r) {
                out.add(e);
            }
        }
        return out;
    }

    /** The monster the player is looking at, within reach. */
    static LivingEntity lookTarget(ServerLevel level, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.93;
        for (LivingEntity e : TrapService.enemies(level, new AABB(eye, eye).inflate(reach))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 1.0e-4) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0e-4 ? null : flat.normalize();
    }

    static void pushAway(LivingEntity victim, Vec3 from, double strength, double lift) {
        Vec3 away = victim.position().subtract(from);
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() > 1.0e-4) {
            flat = flat.normalize().scale(strength);
            victim.push(flat.x, lift, flat.z);
        }
    }

    private static void play(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
