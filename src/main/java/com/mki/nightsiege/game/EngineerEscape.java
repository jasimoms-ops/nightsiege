package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The engineer's escape, the "Sprungladung" (Simon's playtest, 28 October 2026: fast melee
 * monsters ran the engineers down). The first key of the ability bar sets off a small charge at
 * his feet: he is thrown backwards, away from where he looks, and the monsters around him are
 * pushed away and crawl for a moment.
 *
 * <p>No wall climbing with it: only from the ground does it lift him (about a block and a half),
 * in the air it pushes him sideways only, and it has a pause of 15 s.</p>
 */
public final class EngineerEscape {

    /** Its id on the ability bar. */
    public static final String ID = "escape";

    /** When each engineer may set off the next charge (game time). */
    private static final Map<UUID, Long> READY = new HashMap<>();

    private EngineerEscape() {
    }

    /** Its box on the ability bar (the engineers and the heavy archer, Simon 29.10.). */
    public static com.mki.nightsiege.core.UltState.Slot slot(ServerPlayer player, long now) {
        com.mki.nightsiege.core.UltState.Slot escape = new com.mki.nightsiege.core.UltState.Slot();
        escape.id = ID;
        escape.icon = "minecraft:wind_charge";
        escape.name = "nightsiege.ability.escape";
        escape.sub = "nightsiege.ability.escape.sub";
        escape.cooldownTicks = cooldownLeft(player, now);
        escape.totalTicks = (int) pause();
        return escape;
    }

    /** Ticks between two charges. */
    static long pause() {
        return Math.round(SkillValues.get("e_escape", "pause", 300));
    }

    /** Ticks until this engineer may use it again; 0 when ready. */
    public static int cooldownLeft(ServerPlayer player, long now) {
        Long ready = player == null ? null : READY.get(player.getUUID());
        return ready == null || ready <= now ? 0 : (int) (ready - now);
    }

    /** A new night, or the end of one: every charge is ready. */
    public static void reset() {
        READY.clear();
    }

    /** The key was pressed: the charge goes off, if it may. */
    static void fire(ServerPlayer player, TrapService traps) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        SiegeManager m = SiegeManager.get();
        if (!creative && (m == null || !m.isDefending(player))) {
            bar(player, Component.translatable("nightsiege.msg.escape_fight_only"));
            return;
        }
        if (traps.isSeated(player) || player.isPassenger() || Drones.isDrone(player)) {
            bar(player, Component.translatable("nightsiege.msg.escape_seated"));
            return;
        }
        long now = level.getGameTime();
        int left = cooldownLeft(player, now);
        if (left > 0 && !creative) {
            bar(player, Component.translatable("nightsiege.msg.escape_wait", (left + 19) / 20));
            return;
        }
        // Backwards, away from where he looks (by the yaw, so it works looking up or down too).
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double push = SkillValues.get("e_escape", "push", 1.1);
        // Lift only from the ground: in the air it never adds height (no flying over walls).
        double up = player.onGround() ? SkillValues.get("e_escape", "lift", 0.45)
                : Math.min(player.getDeltaMovement().y, 0.1);
        Vec3 at = player.position();
        player.setDeltaMovement(forward.scale(-push).add(0, up, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        // ... and two hearts back (Simon 29.10., 00:08: "und man bekommt noch 2 Herzen wieder").
        player.heal((float) SkillValues.get("e_escape", "heal", 4.0));
        READY.put(player.getUUID(), now + pause());

        // The blast at his feet: the monsters around are thrown away from him and crawl.
        double radius = SkillValues.get("e_escape", "radius", 3.5);
        double knock = SkillValues.get("e_escape", "knock", 1.2);
        int slow = (int) Math.round(SkillValues.get("e_escape", "slow", 40));
        int hit = 0;
        for (Mob mob : GainGear.enemies(level, at, radius)) {
            Vec3 away = mob.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0e-4 ? forward : away.normalize();
            double give = Math.max(0.0, 1.0 - mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (give > 0.01) {
                mob.push(away.x * knock * give, 0.35 * give, away.z * knock * give);
                mob.hurtMarked = true;
            }
            if (!BossService.isBossOrPart(mob)) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 1));
                mob.getNavigation().stop();
            }
            hit++;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 24, 0.8, 0.1, 0.8, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 10, 0.5, 0.1, 0.5, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        bar(player, Component.translatable("nightsiege.msg.escape_fired", hit));
    }

    private static void bar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
