package com.mki.nightsiege.game;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tor 2 (Simon's play test 08.11.: "Pfeilregen und Tesla-Angriff brauchen Animationen"): short visible
 * effects when an ability goes off. Everything is sent by the server (sendParticles, swing), so every
 * player close by sees it, with or without shaders. Only looks: no damage, no pauses, no values.
 */
public final class CastFx {

    private CastFx() {
    }

    /** The archer looses the volley: his arm swings and a fan of arrows rises from him. */
    public static void arrowVolley(ServerLevel level, ServerPlayer player, Vec3 spot, double radius) {
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < 14; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double s = 0.15 + level.getRandom().nextDouble() * 0.2;
            // count 0: the particle flies along (dx, dy, dz) - steeply up and a little apart
            level.sendParticles(ParticleTypes.CRIT, eye.x, eye.y, eye.z, 0, Math.cos(a) * s, 1.0, Math.sin(a) * s, 1.4);
        }
        level.sendParticles(ParticleTypes.POOF, eye.x, eye.y - 0.3, eye.z, 6, 0.25, 0.1, 0.25, 0.02);
        ring(level, spot, radius * 0.6, 0.25);
    }

    /** Every few ticks while the rain falls: the circle on the ground glows again. */
    public static void rainPulse(ServerLevel level, Vec3 center, double radius) {
        ring(level, center, radius, 0.15);
    }

    private static void ring(ServerLevel level, Vec3 c, double r, double lift) {
        int n = Math.max(12, (int) Math.round(r * 8));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x + Math.cos(a) * r, c.y + lift, c.z + Math.sin(a) * r,
                    1, 0, 0.05, 0, 0);
        }
    }

    /** The coil charges and fires: a flash of sparks around its top, a crackle on every monster it struck. */
    public static void teslaDischarge(ServerLevel level, Vec3 top, List<LivingEntity> struck) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 18, 0.35, 0.35, 0.35, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 6, 0.15, 0.15, 0.15, 0.06);
        for (LivingEntity e : struck) {
            double y = e.getY(0.5);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), y, e.getZ(), 12,
                    e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.3);
            level.sendParticles(ParticleTypes.END_ROD, e.getX(), y, e.getZ(), 2, 0.1, 0.2, 0.1, 0.02);
        }
    }
}
