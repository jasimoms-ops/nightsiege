package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * What the crate gains do in a night, beyond the items of the kit: the night
 * totem, feather fall, the lightning blade, the executioner, battle rush, the
 * eagle feather, the glowing shield, blast proof and cover.
 *
 * <p>Everything here only works for defenders of a running night, with the gains
 * of the class they play.</p>
 */
public final class CrateEffects {

    private static final Random RANDOM = new Random();
    /** Defenders whose night totem was used up tonight. */
    private static final Set<UUID> TOTEM_USED = new HashSet<>();
    /** Eagle-feather arrows and the tick they vanish (they fly straight, so they do not fall). */
    private static final Map<UUID, Long> STRAIGHT_ARROWS = new HashMap<>();

    private CrateEffects() {
    }

    /** A new night: the totems are fresh. */
    public static void reset() {
        TOTEM_USED.clear();
        STRAIGHT_ARROWS.clear();
    }

    private static boolean has(ServerPlayer player, String gain) {
        SiegeManager m = SiegeManager.get();
        if (m == null || player == null || !m.isDefending(player)) {
            return false;
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        return m.kits().gainLevel(own, own.clazz(), gain) > 0;
    }

    /** The working level of a crate gain for a defender of the running night (0 otherwise). */
    public static int gainLevel(ServerPlayer player, String gain) {
        return level(player, gain);
    }

    private static int level(ServerPlayer player, String gain) {
        SiegeManager m = SiegeManager.get();
        if (m == null || player == null || !m.isDefending(player)) {
            return 0;
        }
        PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
        return m.kits().gainLevel(own, own.clazz(), gain);
    }

    /** Feather fall: no fall damage in the night. Returns false to cancel the damage. */
    public static boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (!SecondUlts.allowDamage(victim, source)) {
            return false;   // the mirror shield turns projectiles
        }
        return !(victim instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && has(player, "federleicht"));
    }

    /** Damage factors: executioner, blast proof and cover. */
    public static float factor(LivingEntity victim, DamageSource source) {
        float factor = 1f;
        if (source.getEntity() instanceof ServerPlayer attacker && victim instanceof Enemy
                && victim.getHealth() < victim.getMaxHealth() * 0.25f && has(attacker, "hinrichter")) {
            factor *= 1.5f;
        }
        if (victim instanceof ServerPlayer player) {
            if (source.is(DamageTypeTags.IS_EXPLOSION) && has(player, "bombenfest")) {
                factor *= 0.25f;
            }
            Entity from = source.getEntity();
            if (from instanceof Enemy && behindCover(player, from)) {
                factor *= 0.5f;
            }
        }
        return factor * GainGear.damageFactor(victim, source);
    }

    /** True when a blocking guardian with the cover gain stands between the attacker and the player. */
    private static boolean behindCover(ServerPlayer player, Entity attacker) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        for (ServerPlayer guard : level.players()) {
            if (guard == player || !guard.isBlocking() || guard.distanceToSqr(player) > 16.0 || !has(guard, "deckung")) {
                continue;
            }
            Vec3 toAttacker = attacker.position().subtract(guard.position()).normalize();
            Vec3 toPlayer = player.position().subtract(guard.position()).normalize();
            if (toAttacker.dot(toPlayer) < -0.5) {
                return true;
            }
        }
        return false;
    }

    /** After damage: the night totem saves a defender once, the lightning blade strikes. */
    public static void afterDamage(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer player && player.isAlive()
                && player.getHealth() < player.getMaxHealth() * 0.2f && !TOTEM_USED.contains(player.getUUID())
                && has(player, "totem")) {
            TOTEM_USED.add(player.getUUID());
            player.setHealth(player.getMaxHealth());
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(),
                        40, 0.4, 0.6, 0.4, 0.4);
                level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            player.sendSystemMessage(Component.translatable("nightsiege.gain.totem.used"));
        }
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && victim instanceof Enemy && victim.isAlive() && RANDOM.nextDouble() < 0.1
                && has(attacker, "blitzklinge") && victim.level() instanceof ServerLevel level) {
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(victim.getX(), victim.getY(), victim.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            victim.hurtServer(level, level.damageSources().lightningBolt(), 6.0f);
        }
    }

    /** Battle rush: a kill gives three seconds of speed. */
    public static void onKill(Entity killer, LivingEntity killed) {
        if (killer instanceof ServerPlayer player && killed instanceof Enemy && has(player, "kampfrausch")) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, true));
        }
    }

    /** Eagle feather: the archer's arrows fly twice as fast and straight. */
    public static void onArrow(Entity entity) {
        if (!(entity instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer player)
                || arrow.entityTags().contains("nightsiege_turret") || !has(player, "adlerfeder")) {
            return;
        }
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.6));
        STRAIGHT_ARROWS.put(arrow.getUUID(), arrow.level().getGameTime() + 100L);
    }

    /** Once a second in the night: glowing shield, the guardian's firm grip, straight arrows that flew too far. */
    public static void tick(ServerLevel world, Iterable<ServerPlayer> defenders) {
        for (ServerPlayer player : defenders) {
            if (has(player, "leuchtschild")) {
                AABB box = player.getBoundingBox().inflate(6.0);
                for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                }
            }
            if (has(player, "fester_griff")) {
                ItemStack shield = player.getOffhandItem();
                if (!shield.isEmpty() && player.getCooldowns().isOnCooldown(shield)) {
                    player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(shield));
                }
            }
        }
        long now = world.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = STRAIGHT_ARROWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity arrow = world.getEntity(entry.getKey());
            if (arrow == null) {
                it.remove();
            } else if (now >= entry.getValue()) {
                arrow.setNoGravity(false);
                it.remove();
            }
        }
    }

    /** The amulet: ten percent more points from the waves. */
    public static double pointFactor(ServerPlayer player) {
        return level(player, "amulett") > 0 ? 1.1 : 1.0;
    }
}
