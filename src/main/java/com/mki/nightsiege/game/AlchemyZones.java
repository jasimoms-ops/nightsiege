package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BrewKind;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The clouds of the alchemist's zone potions (Simon, 26 October 2026). A potion thrown with
 * the remote bursts where it lands and leaves a cloud for 8 s. Once a second it touches what
 * stands in it: the helping potions only players, the harmful ones only monsters, so a
 * healing cloud can sit right in the fight.
 */
public final class AlchemyZones {

    private static final class Zone {
        final String dimension;
        final Vec3 center;
        final BrewKind kind;
        final long until;
        long nextPulse;

        Zone(String dimension, Vec3 center, BrewKind kind, long until, long nextPulse) {
            this.dimension = dimension;
            this.center = center;
            this.kind = kind;
            this.until = until;
            this.nextPulse = nextPulse;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    private AlchemyZones() {
    }

    /** Radius of a cloud in blocks. */
    public static double radius() {
        return SkillValues.get("potion_tower", "zoneradius", 3.5);
    }

    /** A zone potion burst: its cloud starts here. */
    public static void land(ServerLevel level, KitProjectile shot, Vec3 at) {
        BrewKind kind = BrewKind.byId(shot.data());
        if (kind == null) {
            return;
        }
        long now = level.getGameTime();
        long time = Math.round(SkillValues.get("potion_tower", "zonetime", 160));
        if (shot.getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower && CrateEffects.gainLevel(thrower, "doppelbrau") > 0) {
            time = Math.round(time * 1.5);   // Langes Gebräu (crate gain, 27 October 2026)
        }
        ZONES.add(new Zone(SiegeManager.dimensionName(level), at, kind, now + time, now));
        level.playSound(null, BlockPos.containing(at), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        DustParticleOptions dust = new DustParticleOptions(kind.color(), 2.0f);
        level.sendParticles(dust, at.x, at.y + 0.4, at.z, 50, 1.2, 0.4, 1.2, 0.0);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 20, 0.6, 0.2, 0.6, 0.1);
    }

    /** Every tick: the clouds glow, and once a second they work. */
    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) {
            return;
        }
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            ServerLevel level = SiegeManager.levelByName(server, zone.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now > zone.until) {
                it.remove();
                continue;
            }
            double r = radius();
            if (now % 4 == 0) {
                DustParticleOptions dust = new DustParticleOptions(zone.kind.color(), 1.6f);
                int ring = 18;
                double turn = (now % 40) / 40.0 * Math.PI * 2 / ring;
                for (int i = 0; i < ring; i++) {
                    double a = Math.PI * 2 * i / ring + turn;
                    level.sendParticles(dust, zone.center.x + Math.cos(a) * r, zone.center.y + 0.15,
                            zone.center.z + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
                }
                level.sendParticles(dust, zone.center.x, zone.center.y + 0.5, zone.center.z, 8, r * 0.45, 0.35, r * 0.45, 0.0);
            }
            if (now >= zone.nextPulse) {
                zone.nextPulse = now + 20;
                pulse(level, zone, r);
            }
        }
    }

    private static boolean inside(LivingEntity e, Zone zone, double r) {
        double dx = e.getX() - zone.center.x;
        double dz = e.getZ() - zone.center.z;
        return dx * dx + dz * dz <= r * r && Math.abs(e.getY() - zone.center.y) <= 2.5;
    }

    private static void pulse(ServerLevel level, Zone zone, double r) {
        AABB box = new AABB(zone.center, zone.center).inflate(r, 2.5, r);
        if (zone.kind.forPlayers()) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box,
                    p -> p.isAlive() && !p.isSpectator() && !Drones.isDrone(p) && inside(p, zone, r))) {
                switch (zone.kind) {
                    case HEAL -> {
                        p.heal((float) SkillValues.get("potion_tower", "zoneheal", 2.0));
                        level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.0, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                    }
                    case REGEN -> p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, false, true));
                    case STRENGTH -> p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 50, 0, false, true));
                    case SPEED -> p.addEffect(new MobEffectInstance(MobEffects.SPEED, 50, 1, false, true));
                    default -> {
                    }
                }
            }
            return;
        }
        for (LivingEntity e : TrapService.enemies(level, box)) {
            if (!inside(e, zone, r)) {
                continue;
            }
            switch (zone.kind) {
                case POISON -> e.addEffect(new MobEffectInstance(e.isInvertedHealAndHarm() ? MobEffects.WITHER : MobEffects.POISON,
                        80, 1, false, true));
                case SLOW -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, true));
                case WEAK -> e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, false, true));
                default -> {
                }
            }
        }
    }

    /** A new night, or the end of one: no cloud stays. */
    public static void reset() {
        ZONES.clear();
    }

    /** Clouds standing right now (for the test). */
    public static int count() {
        return ZONES.size();
    }
}
