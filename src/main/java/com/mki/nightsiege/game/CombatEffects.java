package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
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
                archer.startStorm(player, now + 60 + 40L * rank + (SkillTree.has(skills, "a_tempest") ? 40 : 0));
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
                traps.overcharge(player, now + 160 + 40L * rank + (SkillTree.has(skills, "e_sustained") ? 80 : 0));
                play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
                yield true;
            }
            case TOXIC_CLOUD -> toxicCloud(level, player, rank);
            case THUNDERSTORM -> thunderstorm(level, player, rank);
            default -> false;
        };
        if (fired) {
            // In creative the ultimate is for trying out: only a short pause.
            int cooldown = player.getAbilities().instabuild ? 40
                    : (int) Math.round(cooldownFor(rank) * cooldownScale.applyAsDouble(player));
            ultReadyAt.put(player.getUUID(), now + cooldown);
            ultTotal.put(player.getUUID(), cooldown);
            actionBar(player, Component.translatable("nightsiege.msg.ult_fired",
                    Component.translatable("nightsiege.skill." + node.id())));
        }
    }

    // --- archer

    private boolean boltVolley(ServerLevel level, ServerPlayer player, int rank) {
        PlayerSkills skills = skillsFor.apply(player);
        int count = 5 + 2 * rank;   // 7 / 9 / 11 bolts
        float spread = 50f;
        for (int i = 0; i < count; i++) {
            float yaw = player.getYRot() - spread / 2 + spread * i / (count - 1);
            Arrow bolt = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
            bolt.entityTags().add(ArcherEffects.HANDLED);
            bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
            bolt.setBaseDamage(4.0 + 2.0 * rank);
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
        List<LivingEntity> near = enemies(level, player.position(), 10.0 + 2.0 * rank, 6.0);
        if (near.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        double trap = tree.totalBonus(SkillEffect.DEATHTRAP, skillsFor.apply(player));
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : near) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 + 20 * rank, 9));
            bonusHit(level, victim, source, 4f + 2f * rank);
            if (trap > 0) {
                mark(victim, trap, now + 60 + 20L * rank, true);
            }
            level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY(0.8), victim.getZ(),
                    12, 0.4, 0.4, 0.4, 0.0);
        }
        play(level, player, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.5f);
        play(level, player, SoundEvents.WOOL_PLACE, 1.0f, 0.7f);
        return true;
    }

    private boolean huntersMark(ServerLevel level, ServerPlayer player, int rank, long now) {
        boolean pack = tree.totalBonus(SkillEffect.PACK, skillsFor.apply(player)) > 0;
        double r = (16.0 + 8.0 * rank) * (pack ? 1.5 : 1.0);
        double bonus = 0.3 + 0.1 * rank;
        if (pack) {
            // The pack hunts together: everyone close by runs faster.
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(player) <= 400.0) {
                    mate.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
                }
            }
        }
        for (LivingEntity victim : enemies(level, player.position(), r, 6.0)) {
            mark(victim, bonus, now + 300, true);
            if (pack) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
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
            delayed.add(new Delayed(now + 10, () -> whirlPulse(level, player, rank, skills)));
            delayed.add(new Delayed(now + 20, () -> whirlPulse(level, player, rank, skills)));
        }
        return true;
    }

    private void whirlPulse(ServerLevel level, ServerPlayer player, int rank, PlayerSkills skills) {
        if (!player.isAlive()) {
            return;
        }
        double r = 3.5 + 0.5 * rank;
        float damage = 4f + 3f * rank;   // 7 / 10 / 13
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        int hits = 0;
        for (LivingEntity victim : enemies(level, center, r, 1.5)) {
            bonusHit(level, victim, source, damage);
            pushAway(victim, center, 0.8, 0.3);
            hits++;
        }
        if (hits > 0 && SkillTree.has(skills, "w_bloodstorm")) {
            player.heal(Math.min(8, hits));
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(a) * r * 0.6, center.y + 1.0,
                    center.z + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0);
        }
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
    }

    private boolean execute(ServerLevel level, ServerPlayer player, int rank) {
        LivingEntity target = lookTarget(level, player, 5.0);
        if (target == null) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        double threshold = 0.2 + 0.05 * rank;   // 25 % / 30 % / 35 % health
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
            for (LivingEntity next : enemies(level, at, 5.0, 2.0)) {
                if (jumps >= 2 || !next.isAlive() || next.getHealth() > next.getMaxHealth() * threshold) {
                    continue;
                }
                TrapService.spark(level, at.add(0, 1, 0), next.position().add(0, 1, 0));
                bonusHit(level, next, level.damageSources().playerAttack(player), next.getHealth() + 1000f);
                jumps++;
            }
        }
        if (SkillTree.has(skills, "w_bloody_execute")) {
            player.heal(6f);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
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
            delayed.add(new Delayed(level.getGameTime() + 20, () -> slam(level, player, rank, 0.6f, false)));
        }
        return true;
    }

    private void slam(ServerLevel level, ServerPlayer player, int rank, float scale, boolean crater) {
        if (!player.isAlive()) {
            return;
        }
        double r = 4.0 + rank;
        float damage = (5f + 3f * rank) * scale;   // 8 / 11 / 14
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, center, r, 2.0)) {
            bonusHit(level, victim, source, damage);
            if (crater) {
                // The ground caves in: everything slides towards the warrior and sticks.
                pushAway(victim, center, -0.9, 0.2);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
            } else {
                victim.push(0, 0.7, 0);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2, center.z, 6, r * 0.4, 0.1, r * 0.4, 0.0);
        play(level, player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, scale < 1f ? 1.1f : 0.8f);
    }

    // --- guardian

    /** Paladin: the undead burn in the light, everyone else is only stung; allies are mended. */
    private boolean holyLight(ServerLevel level, ServerPlayer player, int rank) {
        double r = 8.0 + 2.0 * rank;
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        boolean sunfire = SkillTree.has(skills, "w_sunfire");
        boolean redemption = SkillTree.has(skills, "w_redemption");
        for (LivingEntity victim : enemies(level, center, r, 3.0)) {
            if (isUndead(victim)) {
                bonusHit(level, victim, source, (10f + 5f * rank) * (sunfire ? 1.5f : 1f));
                victim.igniteForSeconds(sunfire ? 10f : 5f);
            } else {
                bonusHit(level, victim, source, 3f + 2f * rank);
                if (sunfire) {
                    victim.igniteForSeconds(3f);
                }
            }
        }
        for (ServerPlayer mate : level.players()) {
            if (mate.distanceToSqr(player) <= r * r) {
                mate.heal(redemption ? 4f + 4f * rank : 4f);
                if (redemption) {
                    for (MobEffectInstance effect : new ArrayList<>(mate.getActiveEffects())) {
                        if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            mate.removeEffect(effect.getEffect());
                        }
                    }
                    mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
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
        List<LivingEntity> targets = new ArrayList<>(enemies(level, player.position(), 20.0, 8.0));
        if (targets.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        float damage = (8f + 4f * rank) * (float) engineerBlast(skills);
        boolean firebombs = SkillTree.has(skills, "e_firebombs");
        int carpet = (int) Math.round(tree.totalBonus(SkillEffect.CARPET_BOMB, skills));
        int blasts = Math.min(targets.size() + carpet, 3 + 2 * rank + carpet);
        for (int i = 0; i < blasts; i++) {
            LivingEntity aim = targets.get(i % targets.size());
            Vec3 at = i < targets.size() ? aim.position()
                    : aim.position().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            for (LivingEntity victim : enemies(level, at, 2.5, 1.5)) {
                bonusHit(level, victim, source, damage);
                pushAway(victim, at, 0.5, 0.4);
                if (firebombs) {
                    victim.igniteForSeconds(5f);
                    victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, 100, 0));
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
        double r = 6.0 + rank + (plague ? 3.0 : 0.0);
        int ticks = (int) Math.round((120 + 40 * rank) * (SkillTree.has(skills, "e_plague_doctor") ? 2.0 : 1.0));
        long until = level.getGameTime() + ticks;
        Set<LivingEntity> sick = new HashSet<>(enemies(level, player.position(), r, 3.0));
        if (plague) {
            // The plague jumps on to whatever stands next to the sick.
            for (LivingEntity victim : new ArrayList<>(sick)) {
                sick.addAll(enemies(level, victim.position(), 3.0, 2.0));
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
        List<LivingEntity> near = enemies(level, player.position(), 16.0, 6.0);
        if (near.isEmpty()) {
            actionBar(player, Component.translatable("nightsiege.msg.ult_no_target"));
            return false;
        }
        DamageSource source = level.damageSources().playerAttack(player);
        PlayerSkills skills = skillsFor.apply(player);
        boolean ball = SkillTree.has(skills, "e_ball_lightning");
        float damage = 6f + 2f * rank + (ball ? 2f : 0f);
        int jumps = 4 + 2 * rank + (ball ? 4 : 0);
        if (SkillTree.has(skills, "e_superconductor")) {
            traps.fillRemote(player);
            traps.overcharge(player, level.getGameTime() + 80);
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
            current = enemies(level, at, 6.0, 3.0).stream()
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
        long now = level.getGameTime();
        if (victim instanceof Enemy && taken > 0 && source.getEntity() instanceof ServerPlayer player) {
            PlayerSkills skills = skillsFor.apply(player);
            double extra = 0.0;
            Mark mark = marks.get(victim.getUUID());
            if (mark != null && now <= mark.until()) {
                extra += taken * mark.bonus();
            }
            if (skills != null) {
                if (source.getDirectEntity() == player) {
                    extra += meleeHit(level, player, skills, victim, taken, now);
                } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                    extra += arrowHit(level, player, skills, victim, arrow, taken, now);
                }
            }
            if (source.getDirectEntity() instanceof AbstractArrow shot
                    && (shot.entityTags().contains(TrapService.TAG_CHARGED) || shot.entityTags().contains(TrapService.TAG_BLAST))) {
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
                bonusHit(level, victim, level.damageSources().playerAttack(player), (float) extra);
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
        double bonus = 0.0;
        double rage = tree.totalBonus(SkillEffect.RAGE, skills);
        if (rage > 0) {
            bonus += rage * (1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth()));
        }
        boolean crit = random.nextDouble() < tree.totalBonus(SkillEffect.CRIT_CHANCE, skills);
        if (crit) {
            bonus += 0.5 + tree.totalBonus(SkillEffect.CRIT_POWER, skills);
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
            if (now - streak[1] > 40) {
                streak[0] = 0;
            }
            bonus += combo * streak[0];
            streak[0] = Math.min(5, streak[0] + 1);
            streak[1] = now;
        }
        double headhunter = tree.totalBonus(SkillEffect.HEADHUNTER, skills);
        if (headhunter > 0 && victim.getHealth() + taken > victim.getMaxHealth() * 0.7f) {
            bonus += headhunter;
        }
        double price = tree.totalBonus(SkillEffect.BLOOD_PRICE, skills);
        if (price > 0) {
            bonus += price;
            if (player.getHealth() > 6f) {
                player.setHealth(player.getHealth() - 0.5f);
            }
        }
        Long momentum = momentumUntil.remove(player.getUUID());
        if (momentum != null && now <= momentum) {
            bonus += tree.totalBonus(SkillEffect.MOMENTUM, skills);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.6), victim.getZ(), 1, 0, 0, 0, 0);
        }
        double shock = tree.totalBonus(SkillEffect.EARTHSHOCK, skills);
        boolean falling = shock > 0 && player.fallDistance > 1.5;
        if (falling) {
            bonus += shock;
        }
        if (SkillTree.has(skills, "w_glass_cannon")) {
            bonus += 0.3;
        }
        double extra = taken * bonus;

        double sunder = tree.totalBonus(SkillEffect.SUNDER, skills);
        if (sunder > 0) {
            mark(victim, sunder, now + 100, false);
        }
        double heavy = tree.totalBonus(SkillEffect.HEAVY_BLOW, skills);
        if (heavy > 0) {
            extra += heavy;
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
        }
        if (crit) {
            double headsman = tree.totalBonus(SkillEffect.HEADSMAN, skills);
            if (headsman > 0) {
                extra += headsman;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2));
            }
            if (undead && SkillTree.has(skills, "w_holy_strike")) {
                victim.igniteForSeconds(4f);
                player.heal(1f);
            }
            if (SkillTree.has(skills, "w_bladefrenzy")) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0));
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
            float splash = (float) ((taken + extra) * Math.max(cleave, falling ? 0.5 : 0.0));
            int healed = 0;
            for (LivingEntity near : enemies(level, victim.position(), 2.5, 1.5)) {
                if (near == victim) {
                    continue;
                }
                bonusHit(level, near, source, splash);
                if (crusader && cleave > 0) {
                    if (isUndead(near)) {
                        near.igniteForSeconds(3f);
                    }
                    if (healed < 6) {
                        player.heal(0.5f);
                        healed++;
                    }
                }
            }
            if (falling) {
                level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY(), victim.getZ(), 2, 0.6, 0.1, 0.6, 0.0);
                play(level, player, SoundEvents.MACE_SMASH_GROUND, 0.8f, 1.0f);
            }
        }

        extra += guardian.meleeExtra(level, player, skills, victim, taken, now);
        double steal = tree.totalBonus(SkillEffect.LIFESTEAL, skills);
        if (steal > 0) {
            player.heal((float) ((taken + extra) * steal));
        }
        return extra;
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
            momentumUntil.put(player.getUUID(), level.getGameTime() + 60);
        }
        if (skills != null && tree.totalBonus(SkillEffect.RELOAD_RUSH, skills) > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
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
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0));
            play(level, player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.6f);
            return false;
        }
        double parry = tree.totalBonus(SkillEffect.PARRY, skills);
        boolean melee = source.getDirectEntity() == attacker;
        if (parry > 0 && melee && holdsWayWeapon(player, skills) && random.nextDouble() < parry) {
            bonusHit(level, attacker, level.damageSources().playerAttack(player), 4f);
            pushAway(attacker, player.position(), 0.7, 0.2);
            double heal = tree.totalBonus(SkillEffect.PARRY_HEAL, skills);
            if (heal > 0) {
                player.heal((float) heal);
            }
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY(0.6), player.getZ(), 8, 0.4, 0.3, 0.4, 0.1);
            play(level, player, SoundEvents.SHIELD_BLOCK.value(), 0.9f, 1.5f);
            return false;
        }
        if (amount >= 4f && SkillTree.has(skills, "w_divine_shield")
                && now >= divineReadyAt.getOrDefault(player.getUUID(), 0L)) {
            divineReadyAt.put(player.getUUID(), now + 600);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 20, 0.5, 0.6, 0.5, 0.05);
            play(level, player, SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.8f);
            actionBar(player, Component.translatable("nightsiege.msg.divine_shield"));
            return false;
        }
        return true;
    }

    /** Returns the extra damage of an arrow or bolt hit; side effects are applied here. */
    private double arrowHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                            LivingEntity victim, AbstractArrow arrow, float taken, long now) {
        ItemStack weapon = arrow.getWeaponItem();
        boolean crossbow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
        boolean bow = weapon != null && weapon.getItem() instanceof net.minecraft.world.item.BowItem;
        DamageSource source = level.damageSources().playerAttack(player);
        double extra = 0.0;

        // Bolts of the ultimates carry their own effect.
        if (arrow.entityTags().contains(TAG_BARRAGE)) {
            burst(level, player, victim.position(), 3.0, 6f);
        }
        if (arrow.entityTags().contains(TAG_NET)) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
        }
        if (arrow.entityTags().contains(ArcherEffects.SNIPER_TAG)) {
            pierceBehind(level, player, victim, arrow, taken * 0.8f, 6);
        }

        if (skills.clazz() == SkillClass.ARCHER) {
            String way = skills.pathTaken(tree);
            if ((SkillTree.WAY_BOW.equals(way) && !bow) || (SkillTree.WAY_CROSSBOW.equals(way) && !crossbow)) {
                return 0.0;   // the skills of a way only work with its weapon
            }
        }

        Mark before = marks.get(victim.getUUID());
        boolean wasMarked = before != null && now <= before.until();
        double markBonus = tree.totalBonus(SkillEffect.MARK, skills);
        if (SkillTree.has(skills, "a_watchful") && crossbow) {
            markBonus = Math.max(markBonus, 0.15);
        }
        if (markBonus > 0) {
            mark(victim, markBonus, now + 120, true);
        }
        double deathMark = tree.totalBonus(SkillEffect.DEATH_MARK, skills);
        if (wasMarked && deathMark > 0) {
            extra += taken * deathMark;
        }
        double longshot = tree.totalBonus(SkillEffect.LONGSHOT, skills);
        if (longshot > 0 && player.distanceToSqr(victim) > 225.0) {
            extra += taken * longshot;
        }
        double crit = tree.totalBonus(SkillEffect.ARROW_CRIT, skills);
        if (crit > 0 && random.nextDouble() < crit) {
            extra += taken * 0.5;
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
        }
        double brace = tree.totalBonus(SkillEffect.BRACE, skills);
        if (brace > 0 && crossbow && player.isShiftKeyDown()) {
            extra += taken * brace;
        }
        if (tree.totalBonus(SkillEffect.RETREAT, skills) > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
        }
        if (tree.totalBonus(SkillEffect.ARROW_KNOCKBACK, skills) > 0) {
            pushAway(victim, player.position(), 1.0, 0.25);
        }
        double sunder = tree.totalBonus(SkillEffect.BOLT_SUNDER, skills);
        if (sunder > 0 && crossbow) {
            mark(victim, sunder, now + 100, false);
        }
        double poison = tree.totalBonus(SkillEffect.POISON_ARROWS, skills);
        if (poison > 0) {
            boolean already = victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER);
            victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, 80, 1));
            if (already && tree.totalBonus(SkillEffect.VENOM_BURST, skills) > 0) {
                for (LivingEntity near : enemies(level, victim.position(), 3.0, 1.5)) {
                    near.addEffect(new MobEffectInstance(isUndead(near) ? MobEffects.WITHER : MobEffects.POISON, 80, 1));
                }
                level.sendParticles(TrapService.POISON_DUST, victim.getX(), victim.getY(0.5), victim.getZ(),
                        40, 1.2, 0.5, 1.2, 0.0);
            }
        }

        // Crossbow: nets, shackles, bursts.
        if (crossbow) {
            double net = tree.totalBonus(SkillEffect.NET_BOLT, skills);
            boolean netted = false;
            if (net > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.round(20 * net), 1));
                netted = true;
            }
            if (tree.totalBonus(SkillEffect.NET_FIELD, skills) > 0) {
                for (LivingEntity near : enemies(level, victim.position(), 3.0, 1.5)) {
                    near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
                level.sendParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY(0.3), victim.getZ(),
                        20, 1.2, 0.2, 1.2, 0.0);
                netted = true;
            }
            if (tree.totalBonus(SkillEffect.SHACKLE, skills) > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9));
                netted = true;
            }
            double hunterNet = tree.totalBonus(SkillEffect.HUNTER_NET, skills);
            if (netted && hunterNet > 0) {
                mark(victim, hunterNet, now + 120, true);
            }
            double explosive = tree.totalBonus(SkillEffect.EXPLOSIVE, skills);
            boolean cluster = tree.totalBonus(SkillEffect.CLUSTER, skills) > 0;
            if (explosive > 0 || cluster) {
                burst(level, player, victim.position(), cluster ? 4.0 : 2.5,
                        (float) (Math.max(explosive, 3.0) + (cluster ? 4.0 : 0.0)));
            }
        }

        double shrapnel = tree.totalBonus(SkillEffect.SHRAPNEL, skills);
        if (shrapnel > 0) {
            Vec3 at = victim.position();
            for (LivingEntity near : enemies(level, at, 2.5, 1.5)) {
                if (near != victim) {
                    bonusHit(level, near, source, (float) (taken * shrapnel));
                }
            }
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 10, 0.6, 0.4, 0.6, 0.2);
        }
        int pierce = (int) Math.round(tree.totalBonus(SkillEffect.PIERCE, skills));
        if (pierce > 0) {
            pierceBehind(level, player, victim, arrow, taken * 0.7f, pierce);
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
        List<LivingEntity> line = new ArrayList<>(enemies(level, from, 8.5, 2.0).stream()
                .filter(e -> e != victim)
                .filter(e -> {
                    Vec3 to = e.position().subtract(from);
                    double along = to.x * d.x + to.z * d.z;
                    double side = Math.abs(to.x * d.z - to.z * d.x);
                    return along > 0 && along <= 8.0 && side <= 1.0;
                })
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                .toList());
        DamageSource source = level.damageSources().playerAttack(player);
        for (int i = 0; i < Math.min(count, line.size()); i++) {
            LivingEntity behind = line.get(i);
            bonusHit(level, behind, source, amount);
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
            bonusHit(level, behind, level.damageSources().playerAttack(player), amount);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, behind.getX(), behind.getY(0.6), behind.getZ(),
                    6, 0.2, 0.3, 0.2, 0.1);
        }
    }

    private void mark(LivingEntity victim, double bonus, long until, boolean glow) {
        Mark old = marks.get(victim.getUUID());
        if (old == null || old.bonus() <= bonus || old.until() < until) {
            marks.put(victim.getUUID(), new Mark(Math.max(bonus, old == null ? 0 : old.bonus()),
                    Math.max(until, old == null ? 0 : old.until())));
        }
        if (glow) {
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.max(20, until - victim.level().getGameTime()), 0));
        }
    }

    /** Blasts of an engineer: stronger for the pyromaniac, weaker for the plague doctor. */
    private double engineerBlast(PlayerSkills skills) {
        double scale = 1.0;
        if (SkillTree.has(skills, "e_pyro")) {
            scale *= 1.0 + tree.totalBonus(SkillEffect.PYROMANIAC, skills);
        }
        if (SkillTree.has(skills, "e_plague_doctor")) {
            scale *= 0.7;
        }
        return scale;
    }

    /** The engineer's remote marks a monster: it glows and takes more damage. */
    public void remoteMark(LivingEntity victim, double bonus, long until, boolean glow) {
        mark(victim, bonus, until, glow);
    }

    /** Deals damage the mod adds on top, through the hit cooldown, without boosting it again. */
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
            int rank = skills == null ? 0 : (int) Math.round(tree.totalBonus(SkillEffect.GUARDIAN_ANGEL, skills));
            if (rank <= 0) {
                continue;
            }
            double reach = 8.0 + 8.0 * rank;   // 16 / 24 / 32 blocks
            if (guardian != victim && guardian.distanceToSqr(victim) > reach * reach) {
                continue;
            }
            angelUsed.add(guardian.getUUID());
            victim.setHealth(4f + 2f * rank);
            victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
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
        for (ServerPlayer medic : server.getPlayerList().getPlayers()) {
            PlayerSkills skills = skillsFor.apply(medic);
            double heal = skills == null ? 0 : tree.totalBonus(SkillEffect.HEAL_AURA, skills);
            boolean martyr = SkillTree.has(skills, "w_martyr");
            if ((heal <= 0 && !martyr) || !(medic.level() instanceof ServerLevel level)) {
                continue;
            }
            double radius = HEAL_RADIUS + guardian.auraRange(skills);
            for (ServerPlayer mate : level.players()) {
                if (!mate.isAlive() || mate.distanceToSqr(medic) > radius * radius) {
                    continue;
                }
                float amount = (float) heal + (martyr && mate != medic ? 1.5f : 0f);
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
        List<LivingEntity> close = enemies(level, player.position(), 3.0, 1.5);
        if (close.isEmpty()) {
            return;
        }
        double master = tree.totalBonus(SkillEffect.TRAPMASTER, skills);
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : close) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, master > 0 ? 9 : 1));
            pushAway(victim, player.position(), 0.6, 0.1);
            if (master > 0) {
                bonusHit(level, victim, source, (float) master);
            }
        }
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.2, player.getZ(), 16, 1.5, 0.1, 1.5, 0.05);
        play(level, player, SoundEvents.TRIPWIRE_CLICK_ON, 1.0f, 1.0f);
        tripReadyAt.put(player.getUUID(), now + 100);
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
            bonus += SkillTree.WAY_MIGHT.equals(way) ? 0.3 : SkillTree.WAY_BLADE.equals(way) ? 0.2 : 0.0;
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
        if (counter % 40 != 0) {
            return;
        }
        if (tree.totalBonus(SkillEffect.WARCRY, skills) > 0) {
            for (LivingEntity victim : enemies(level, player.position(), 5.0, 2.0)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            }
        }
        if (tree.totalBonus(SkillEffect.VOW, skills) > 0) {
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(player) <= 36.0) {
                    mate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, true));
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
