package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeping the distance (balancing update, 28 October 2026): in every playtest the ranged classes
 * were run down by fast melee monsters (the skirmisher above all) and died before they could shoot.
 * A monster that hits an archer or an engineer in melee is thrown back and slowed for a moment
 * (config rangedPushbackSeconds), at most once every three seconds per monster. Bosses and the
 * camp's workbench are not moved.
 */
final class RangedGuard {

    private static final long PER_MOB_TICKS = 60L;
    private static final double PUSH = 1.1;

    /** Monster → game time it may be pushed again. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private RangedGuard() {
    }

    static void afterDamage(SiegeManager m, LivingEntity entity, DamageSource source, float taken) {
        if (!(entity instanceof ServerPlayer player) || taken <= 0f || m.config().rangedPushbackSeconds <= 0.0
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        if (!(source.getEntity() instanceof Mob mob) || source.getDirectEntity() != mob || !(mob instanceof Enemy)
                || BossService.isBoss(mob) || Companions.isCompanion(mob) || !mob.isAlive()) {
            return;
        }
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        SkillClass clazz = skills == null ? null : skills.clazz();
        if (clazz != SkillClass.ARCHER && clazz != SkillClass.ENGINEER) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long next = NEXT.get(mob.getUUID());
        if (next != null && now < next) {
            return;
        }
        NEXT.put(mob.getUUID(), now + PER_MOB_TICKS);
        if (NEXT.size() > 512) {
            NEXT.values().removeIf(t -> t < now);
        }
        double dx = player.getX() - mob.getX();
        double dz = player.getZ() - mob.getZ();
        if (dx * dx + dz * dz < 1.0e-4) {
            dx = player.getLookAngle().x;
            dz = player.getLookAngle().z;
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        // Away from the player, a little up: like a strong knockback.
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5).add(-dx / len * PUSH, 0.3, -dz / len * PUSH));
        mob.hurtMarked = true;
        int ticks = (int) Math.round(m.config().rangedPushbackSeconds * 20.0);
        mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 2, false, true));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                1, 0, 0, 0, 0);
        level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    /** Forgets everything (a new night). */
    static void clear() {
        NEXT.clear();
    }
}
