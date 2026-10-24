package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.UUID;

/**
 * Every sound and particle the mod makes, in one place.
 *
 * <p>Announcements are played on each player directly rather than at a point in
 * the world, so they carry no matter where somebody stands. Everything tied to
 * a place in the world goes through the level, so distance and direction are
 * audible.</p>
 */
public final class Fx {

    /** Deep red for the blood moon haze. */
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0x8B0010, 1.6f);

    private Fx() {
    }

    // ------------------------------------------------------------------
    // announcements, heard by everyone wherever they are
    // ------------------------------------------------------------------

    /** Sunset warning: a war horn, answered by distant thunder. */
    public static void warning(MinecraftServer server, SiegeConfig cfg) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;   // players outside Night Siege hear nothing of it
            }
            play(player, SoundEvents.RAID_HORN.value(), 1.0f, 1.0f);
            play(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 0.6f);
        }
    }

    /** The siege begins. */
    public static void siegeStart(MinecraftServer server, SiegeConfig cfg, boolean bloodMoon) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;   // players outside Night Siege hear nothing of it
            }
            play(player, SoundEvents.WITHER_SPAWN, 0.8f, bloodMoon ? 0.6f : 0.9f);
            if (bloodMoon) {
                // A dragon's growl under the horn makes a blood moon unmistakable.
                play(player, SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 0.5f);
            }
        }
    }

    /** A fresh batch is on its way. */
    public static void waveIncoming(MinecraftServer server, SiegeConfig cfg,
                                    Collection<UUID> participants) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                play(player, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.7f, 0.8f);
            }
        }
    }

    public static void victory(MinecraftServer server, SiegeConfig cfg) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;   // players outside Night Siege hear nothing of it
            }
            play(player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
            play(player, SoundEvents.PORTAL_TRIGGER, 0.4f, 1.6f);
        }
    }

    public static void defeat(MinecraftServer server, SiegeConfig cfg) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;   // players outside Night Siege hear nothing of it
            }
            play(player, SoundEvents.WITHER_DEATH, 0.9f, 0.5f);
            play(player, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f);
        }
    }

    /** A defender went down: a hard bell, so the others notice at once. */
    public static void defenderDown(MinecraftServer server, SiegeConfig cfg,
                                    Collection<UUID> participants) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                play(player, SoundEvents.BELL_RESONATE, 1.0f, 0.5f);
            }
        }
    }

    public static void pointsEarned(MinecraftServer server, SiegeConfig cfg) {
        if (!cfg.soundsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SiegeManager.get().isRegistered(player)) {
                continue;   // players outside Night Siege hear nothing of it
            }
            play(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.4f);
            if (player.level() instanceof ServerLevel level) {
                particles(level, ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.8, 0.5, 0.02);
            }
        }
    }

    // ------------------------------------------------------------------
    // things that happen at a place in the world
    // ------------------------------------------------------------------

    /** Marks where attackers come out of the dark. */
    public static void mobSpawned(ServerLevel level, SiegeConfig cfg, Mob mob, boolean audible) {
        if (!cfg.particlesEnabled && !cfg.soundsEnabled) {
            return;
        }
        double x = mob.getX();
        double y = mob.getY() + 0.8;
        double z = mob.getZ();
        if (cfg.particlesEnabled) {
            particles(level, ParticleTypes.LARGE_SMOKE, x, y, z, 10, 0.3, 0.4, 0.3, 0.01);
            particles(level, ParticleTypes.SOUL, x, y, z, 4, 0.2, 0.3, 0.2, 0.02);
        }
        // Only a few per batch make a sound, otherwise a wave is a wall of noise.
        if (cfg.soundsEnabled && audible) {
            playAt(level, x, y, z, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, 0.6f, 0.8f);
        }
    }

    /** The champion arrives. */
    public static void championSpawned(ServerLevel level, SiegeConfig cfg, Mob champion) {
        if (cfg.soundsEnabled) {
            playAt(level, champion.getX(), champion.getY(), champion.getZ(),
                    SoundEvents.WITHER_SPAWN, 1.0f, 0.7f);
        }
        if (cfg.particlesEnabled) {
            particles(level, ParticleTypes.SOUL_FIRE_FLAME,
                    champion.getX(), champion.getY() + 1.0, champion.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
        }
    }

    /** A burning aura so the champion is easy to pick out of a crowd. */
    public static void championAura(ServerLevel level, SiegeConfig cfg, Mob champion) {
        if (!cfg.particlesEnabled) {
            return;
        }
        particles(level, ParticleTypes.FLAME,
                champion.getX(), champion.getY() + 1.2, champion.getZ(), 6, 0.45, 0.6, 0.45, 0.01);
    }

    public static void championDown(ServerLevel level, SiegeConfig cfg, Mob champion) {
        if (cfg.soundsEnabled) {
            playAt(level, champion.getX(), champion.getY(), champion.getZ(),
                    SoundEvents.WITHER_DEATH, 1.0f, 1.1f);
        }
        if (cfg.particlesEnabled) {
            particles(level, ParticleTypes.EXPLOSION,
                    champion.getX(), champion.getY() + 1.0, champion.getZ(), 8, 0.6, 0.6, 0.6, 0.0);
        }
    }

    /** Red haze drifting down around the defenders during a blood moon. */
    public static void bloodMoonHaze(ServerLevel level, SiegeConfig cfg, ServerPlayer player) {
        if (!cfg.particlesEnabled) {
            return;
        }
        particles(level, BLOOD_DUST,
                player.getX(), player.getY() + 6.0, player.getZ(), 26, 7.0, 3.0, 7.0, 0.0);
        particles(level, ParticleTypes.CRIMSON_SPORE,
                player.getX(), player.getY() + 5.0, player.getZ(), 14, 8.0, 3.0, 8.0, 0.0);
    }

    /** The core flares once when a siege is won or lost. */
    public static void coreFlare(ServerLevel level, SiegeConfig cfg, BlockPos core, boolean won) {
        if (!cfg.particlesEnabled) {
            return;
        }
        double x = core.getX() + 0.5;
        double y = core.getY() + 1.0;
        double z = core.getZ() + 0.5;
        if (won) {
            particles(level, ParticleTypes.END_ROD, x, y, z, 60, 0.3, 1.5, 0.3, 0.12);
            particles(level, ParticleTypes.HAPPY_VILLAGER, x, y, z, 30, 1.0, 1.0, 1.0, 0.05);
        } else {
            particles(level, ParticleTypes.EXPLOSION, x, y, z, 25, 2.0, 1.5, 2.0, 0.0);
            particles(level, ParticleTypes.LARGE_SMOKE, x, y, z, 80, 2.5, 2.0, 2.5, 0.06);
        }
    }

    // ------------------------------------------------------------------
    // plumbing
    // ------------------------------------------------------------------

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playSound(sound, volume, pitch);
    }

    private static void playAt(ServerLevel level, double x, double y, double z,
                               SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }
}
