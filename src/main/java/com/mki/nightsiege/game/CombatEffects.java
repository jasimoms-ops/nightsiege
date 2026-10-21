package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
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
import net.minecraft.world.effect.MobEffectInstance;
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

    public CombatEffects(SkillTree tree, Function<ServerPlayer, PlayerSkills> skillsFor,
                         Predicate<Entity> isChampion, ArcherEffects archer, TrapService traps) {
        this.tree = tree;
        this.skillsFor = skillsFor;
        this.isChampion = isChampion;
        this.archer = archer;
        this.traps = traps;
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
                archer.startStorm(player, now + 60 + 40L * rank);
                play(level, player, SoundEvents.BLAZE_SHOOT, 1.0f, 1.4f);
                yield true;
            }
            case POWER_SHOT -> {
                archer.armPowerShot(player, now, rank);
                play(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.6f);
                yield true;
            }
            case BOLT_VOLLEY -> boltVolley(level, player, rank);
            case HUNTERS_MARK -> huntersMark(level, player, rank, now);
            case WHIRLWIND -> whirlwind(level, player, rank);
            case EXECUTE -> execute(level, player, rank);
            case LANCE_THRUST -> lanceThrust(level, player, rank);
            case GROUND_SLAM -> groundSlam(level, player, rank);
            case TAUNT -> taunt(level, player, rank);
            case MASS_HEAL -> massHeal(level, player, rank);
            case SHIELD_CHARGE -> shieldCharge(level, player, rank);
            case HOLY_LIGHT -> holyLight(level, player, rank);
            case BOMBARDMENT -> bombardment(level, player, rank);
            case OVERCHARGE -> {
                traps.overcharge(player, now + 160 + 40L * rank);
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
        int count = 5 + 2 * rank;   // 7 / 9 / 11 bolts
        float spread = 50f;
        for (int i = 0; i < count; i++) {
            float yaw = player.getYRot() - spread / 2 + spread * i / (count - 1);
            Arrow bolt = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
            bolt.entityTags().add(ArcherEffects.HANDLED);
            bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
            bolt.setBaseDamage(4.0 + 2.0 * rank);
            bolt.shootFromRotation(player, player.getXRot(), yaw, 0f, 3.2f, 1.0f);
            level.addFreshEntity(bolt);
        }
        play(level, player, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.7f);
        return true;
    }

    private boolean huntersMark(ServerLevel level, ServerPlayer player, int rank, long now) {
        double r = 16.0 + 8.0 * rank;
        double bonus = 0.15 + 0.05 * rank;
        for (LivingEntity victim : enemies(level, player.position(), r, 6.0)) {
            mark(victim, bonus, now + 200, true);
        }
        play(level, player, SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.4f);
        return true;
    }

    // --- warrior

    private boolean whirlwind(ServerLevel level, ServerPlayer player, int rank) {
        double r = 3.5 + 0.5 * rank;
        float damage = 4f + 3f * rank;   // 7 / 10 / 13
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, center, r, 1.5)) {
            bonusHit(level, victim, source, damage);
            pushAway(victim, center, 0.8, 0.3);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(a) * r * 0.6, center.y + 1.0,
                    center.z + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0);
        }
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        return true;
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
        double r = 4.0 + rank;
        float damage = 5f + 3f * rank;   // 8 / 11 / 14
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, center, r, 2.0)) {
            bonusHit(level, victim, source, damage);
            victim.push(0, 0.7, 0);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2, center.z, 6, r * 0.4, 0.1, r * 0.4, 0.0);
        play(level, player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.8f);
        return true;
    }

    // --- guardian

    private boolean taunt(ServerLevel level, ServerPlayer player, int rank) {
        double r = 7.0 + 3.0 * rank;   // 10 / 13 / 16
        for (LivingEntity victim : enemies(level, player.position(), r, 4.0)) {
            if (victim instanceof Mob mob) {
                mob.setTarget(player);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3,
                        victim.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60 + 20 * rank, 1));
        play(level, player, SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
        return true;
    }

    private boolean massHeal(ServerLevel level, ServerPlayer player, int rank) {
        for (ServerPlayer mate : level.players()) {
            if (mate.isAlive() && mate.distanceToSqr(player) <= 144.0) {
                mate.heal(6f + 4f * rank);
                mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 1.8, mate.getZ(),
                        6, 0.4, 0.3, 0.4, 0.0);
            }
        }
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
        return true;
    }

    private boolean shieldCharge(ServerLevel level, ServerPlayer player, int rank) {
        Vec3 flat = flatLook(player);
        if (flat == null) {
            return false;
        }
        float damage = 3f + 3f * rank;   // 6 / 9 / 12
        Vec3 eye = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, eye, 6.0, 2.0)) {
            Vec3 to = victim.position().subtract(eye);
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(flat) < 0.5) {
                continue;   // only a cone of about 60 degrees to each side
            }
            bonusHit(level, victim, source, damage);
            victim.push(flat.x * 1.4, 0.35, flat.z * 1.4);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
        }
        for (int i = 1; i <= 6; i++) {
            Vec3 p = eye.add(flat.scale(i)).add(0, 1.0, 0);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        play(level, player, SoundEvents.SHIELD_BLOCK.value(), 1.0f, 0.6f);
        play(level, player, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.8f);
        return true;
    }

    /** Paladin: the undead burn in the light, everyone else is only stung; allies are mended. */
    private boolean holyLight(ServerLevel level, ServerPlayer player, int rank) {
        double r = 8.0 + 2.0 * rank;
        Vec3 center = player.position();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : enemies(level, center, r, 3.0)) {
            if (isUndead(victim)) {
                bonusHit(level, victim, source, 10f + 5f * rank);
                victim.igniteForSeconds(5f);
            } else {
                bonusHit(level, victim, source, 3f + 2f * rank);
            }
        }
        for (ServerPlayer mate : level.players()) {
            if (mate.distanceToSqr(player) <= r * r) {
                mate.heal(4f);
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
        float damage = 8f + 4f * rank;
        int blasts = Math.min(targets.size(), 3 + 2 * rank);
        for (int i = 0; i < blasts; i++) {
            Vec3 at = targets.get(i).position();
            for (LivingEntity victim : enemies(level, at, 2.5, 1.5)) {
                bonusHit(level, victim, source, damage);
                pushAway(victim, at, 0.5, 0.4);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
        }
        return true;
    }

    private boolean toxicCloud(ServerLevel level, ServerPlayer player, int rank) {
        double r = 6.0 + rank;
        int ticks = 120 + 40 * rank;
        for (LivingEntity victim : enemies(level, player.position(), r, 3.0)) {
            victim.addEffect(new MobEffectInstance(isUndead(victim) ? MobEffects.WITHER : MobEffects.POISON, ticks, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks / 2, 1));
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
        float damage = 6f + 2f * rank;
        int jumps = 4 + 2 * rank;
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

    public void afterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
        if (dealingBonus || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
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
                    arrowHit(level, player, skills, victim, arrow, taken, now);
                }
            }
            if (source.getDirectEntity() instanceof AbstractArrow) {
                extra += taken * arrowBonus.applyAsDouble(player);
            }
            if (extra > 0.05 && victim.isAlive()) {
                bonusHit(level, victim, level.damageSources().playerAttack(player), (float) extra);
            }
        }
        // A shield catching a monster's blow.
        if (blocked && victim instanceof ServerPlayer player
                && source.getEntity() instanceof LivingEntity attacker && attacker instanceof Enemy) {
            PlayerSkills skills = skillsFor.apply(player);
            double counter = skills == null ? 0 : tree.totalBonus(SkillEffect.COUNTER, skills);
            if (counter > 0) {
                bonusHit(level, attacker, level.damageSources().playerAttack(player), (float) counter);
                pushAway(attacker, player.position(), 0.9, 0.3);
                play(level, player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.4f);
            }
        }
    }

    /** Returns the extra damage of a melee hit; side effects are applied here. */
    private double meleeHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                            LivingEntity victim, float taken, long now) {
        double bonus = 0.0;
        double rage = tree.totalBonus(SkillEffect.RAGE, skills);
        if (rage > 0) {
            bonus += rage * (1.0 - player.getHealth() / Math.max(1f, player.getMaxHealth()));
        }
        boolean crit = random.nextDouble() < tree.totalBonus(SkillEffect.CRIT_CHANCE, skills);
        if (crit) {
            bonus += 0.5;
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(),
                    12, 0.3, 0.4, 0.3, 0.2);
            play(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        }
        if (isChampion.test(victim)) {
            bonus += tree.totalBonus(SkillEffect.CHAMPION_BANE, skills);
        }
        if (isUndead(victim)) {
            bonus += tree.totalBonus(SkillEffect.SMITE, skills);
        }
        double extra = taken * bonus;

        ItemStack weapon = player.getMainHandItem();
        if (weapon.is(ItemTags.AXES)) {
            double sunder = tree.totalBonus(SkillEffect.SUNDER, skills);
            if (sunder > 0) {
                mark(victim, sunder, now + 100, false);
            }
            double heavy = tree.totalBonus(SkillEffect.HEAVY_BLOW, skills);
            if (heavy > 0) {
                extra += heavy;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
            }
        }
        if (weapon.is(ItemTags.SPEARS)) {
            double impale = tree.totalBonus(SkillEffect.IMPALE, skills);
            if (impale > 0) {
                impaleBehind(level, player, victim, (float) (taken * impale));
            }
        }

        double steal = tree.totalBonus(SkillEffect.LIFESTEAL, skills);
        if (steal > 0) {
            player.heal((float) ((taken + extra) * steal));
        }
        return extra;
    }

    private void arrowHit(ServerLevel level, ServerPlayer player, PlayerSkills skills,
                          LivingEntity victim, AbstractArrow arrow, float taken, long now) {
        double markBonus = tree.totalBonus(SkillEffect.MARK, skills);
        if (markBonus > 0) {
            mark(victim, markBonus, now + 120, true);
        }
        ItemStack weapon = arrow.getWeaponItem();
        double shrapnel = tree.totalBonus(SkillEffect.SHRAPNEL, skills);
        if (shrapnel > 0 && weapon != null && weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            Vec3 at = victim.position();
            DamageSource source = level.damageSources().playerAttack(player);
            for (LivingEntity near : enemies(level, at, 2.5, 1.5)) {
                if (near != victim) {
                    bonusHit(level, near, source, (float) (taken * shrapnel));
                }
            }
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 10, 0.6, 0.4, 0.6, 0.2);
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

    /** Deals damage the mod adds on top, through the hit cooldown, without boosting it again. */
    private void bonusHit(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
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
        if (++healCounter < HEAL_INTERVAL) {
            return;
        }
        healCounter = 0;
        long now = server.overworld().getGameTime();
        marks.values().removeIf(m -> m.until() < now);
        for (ServerPlayer medic : server.getPlayerList().getPlayers()) {
            PlayerSkills skills = skillsFor.apply(medic);
            double heal = skills == null ? 0 : tree.totalBonus(SkillEffect.HEAL_AURA, skills);
            if (heal <= 0 || !(medic.level() instanceof ServerLevel level)) {
                continue;
            }
            for (ServerPlayer mate : level.players()) {
                if (mate.isAlive() && mate.distanceToSqr(medic) <= HEAL_RADIUS * HEAL_RADIUS
                        && mate.getHealth() < mate.getMaxHealth()) {
                    mate.heal((float) heal);
                    level.sendParticles(ParticleTypes.HEART, mate.getX(), mate.getY() + 2.0, mate.getZ(),
                            1, 0.2, 0.1, 0.2, 0.0);
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
    }

    // ------------------------------------------------------------------

    private static boolean isUndead(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(e.getType()).is(EntityTypeTags.UNDEAD);
    }

    /** Monsters within a horizontal radius and a vertical band around a point. */
    private static List<LivingEntity> enemies(ServerLevel level, Vec3 center, double r, double up) {
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
    private static LivingEntity lookTarget(ServerLevel level, ServerPlayer player, double reach) {
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

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0e-4 ? null : flat.normalize();
    }

    private static void pushAway(LivingEntity victim, Vec3 from, double strength, double lift) {
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

    private static void actionBar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
