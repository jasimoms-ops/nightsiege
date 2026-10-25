package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CrateRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The world effect of a crate gain: when the band of the crate window stops, the player
 * stands in a ring of light in the colour of the rarity. From rare on it spirals up, from
 * epic on a firework bursts above them (only particles, it hurts nobody), and a legendary
 * gain bursts three times and is told to everyone online.
 */
public final class CrateFx {

    /** Ticks the band of the crate window turns (CrateScreen.SPIN, 5.2 s). */
    public static final int SPIN_TICKS = 104;

    private record Due(UUID player, CrateRarity rarity, String gain, int at, int burst) {
    }

    private static final List<Due> DUE = new ArrayList<>();

    private CrateFx() {
    }

    /** The crate of this player was opened: the effect comes when the band stands. */
    public static void schedule(ServerPlayer player, CrateRarity rarity, String gain, int delayTicks) {
        MinecraftServer server = player.level().getServer();
        if (server == null || rarity == null) {
            return;
        }
        DUE.add(new Due(player.getUUID(), rarity, gain, server.getTickCount() + Math.max(0, delayTicks), -1));
    }

    /** Every server tick. */
    public static void tick(MinecraftServer server) {
        if (DUE.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Due> ready = new ArrayList<>();
        Iterator<Due> it = DUE.iterator();
        while (it.hasNext()) {
            Due due = it.next();
            if (now >= due.at()) {
                it.remove();
                ready.add(due);
            }
        }
        for (Due due : ready) {
            ServerPlayer player = server.getPlayerList().getPlayer(due.player());
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            if (due.burst() >= 0) {
                burst(level, player, due.rarity(), due.burst());
            } else {
                reveal(server, level, player, due.rarity(), due.gain(), now);
            }
        }
    }

    private static void reveal(MinecraftServer server, ServerLevel level, ServerPlayer player, CrateRarity rarity, String gain, int now) {
        int tier = rarity.ordinal();
        DustParticleOptions dust = new DustParticleOptions(rarity.color(), 1.3f + 0.15f * tier);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        // A ring of light at the feet.
        int ring = 18 + 8 * tier;
        for (int i = 0; i < ring; i++) {
            double a = i * Math.PI * 2 / ring;
            level.sendParticles(dust, x + Math.cos(a) * 1.3, y + 0.15, z + Math.sin(a) * 1.3, 1, 0, 0.04, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 4 + 4 * tier, 0.5, 0.6, 0.5, 0.03);
        if (tier >= CrateRarity.RARE.ordinal()) {
            // A spiral up the player.
            for (int i = 0; i < 48; i++) {
                double a = i * 0.45;
                level.sendParticles(dust, x + Math.cos(a) * 0.9, y + i * 0.05, z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        if (tier <= CrateRarity.UNCOMMON.ordinal()) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f + 0.2f * tier);
        } else if (tier == CrateRarity.RARE.ordinal()) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
        } else {
            // The firework: a streak goes up, then it bursts (once, legendary three times).
            for (int i = 0; i < 14; i++) {
                level.sendParticles(ParticleTypes.FIREWORK, x, y + 1.5 + i * 0.4, z, 1, 0.02, 0.05, 0.02, 0.01);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 1f);
            int bursts = rarity == CrateRarity.LEGENDARY ? 3 : 1;
            for (int b = 0; b < bursts; b++) {
                DUE.add(new Due(player.getUUID(), rarity, gain, now + 12 + 9 * b, b));
            }
        }
        if (rarity == CrateRarity.LEGENDARY) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 60, 0.6, 0.8, 0.6, 0.5);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("nightsiege.crate.legendary_all",
                    player.getDisplayName(), Component.translatable("nightsiege.gain." + gain))
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** One burst of the firework above the player. */
    private static void burst(ServerLevel level, ServerPlayer player, CrateRarity rarity, int index) {
        int color = rarity == CrateRarity.LEGENDARY && index % 2 == 1 ? 0xFFF6D8 : rarity.color();
        DustParticleOptions dust = new DustParticleOptions(color, 2.0f);
        double x = player.getX() + (index == 0 ? 0 : (index == 1 ? 2.5 : -2.5));
        double y = player.getY() + 7.5 + index * 0.8;
        double z = player.getZ() + (index == 2 ? 1.5 : 0);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 70, 0.1, 0.1, 0.1, 0.28);
        level.sendParticles(dust, x, y, z, 60, 1.6, 1.6, 1.6, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, player.getX(), y, player.getZ(), index == 0 ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST
                : SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 1f);
        level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.4f, 1f);
    }

    /** Effects still to come (for the tests). */
    public static int pending() {
        return DUE.size();
    }
}
