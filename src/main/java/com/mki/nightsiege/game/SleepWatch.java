package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Notices when a player lies down in a bed.
 *
 * <p>This is the only place that touches Minecraft's sleeping API. If a future
 * game version renames those two calls, this file is the only one to adjust.</p>
 */
public final class SleepWatch {

    private final Set<UUID> sleeping = new HashSet<>();

    /** Calls the handler once for every player who just lay down. */
    public void poll(MinecraftServer server, BiConsumer<ServerPlayer, BlockPos> onSleepStart) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean isSleepingNow = player.isSleeping();
            if (!isSleepingNow) {
                sleeping.remove(id);
                continue;
            }
            if (sleeping.add(id)) {
                Optional<BlockPos> bed = player.getSleepingPos();
                if (bed.isPresent()) {
                    onSleepStart.accept(player, bed.get());
                }
            }
        }
    }

    public void forget(UUID playerId) {
        sleeping.remove(playerId);
    }
}
