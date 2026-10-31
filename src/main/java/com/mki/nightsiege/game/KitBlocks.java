package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Blocks set down from kit items (Simon 31.10.: survival safety 2, "Blöcke aus dem Kit verschwinden
 * am Morgen ohne Drops, wie die Barrikaden"): the kit's cobblestone, torches and ladders stand as
 * night blocks ({@link TempBlocks}): broken without drops and gone when the night ends. In a
 * dungeon or camp fight they go when their builder's kit goes.
 */
public final class KitBlocks {

    private record Spot(String dimension, BlockPos pos) {
    }

    /** Kit blocks each player set down while wearing the kit (not saved: a restart clears all night blocks). */
    private static final Map<UUID, List<Spot>> BY_PLAYER = new HashMap<>();
    private static int counter;

    private KitBlocks() {
    }

    /** A player set a block down from a kit item (the block item mixin). */
    public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || ModContent.trapOf(state) != null || ModContent.isCore(state)) {
            return;   // traps and towers stand for good, as ever (TrapService)
        }
        TempBlocks.remember(level, pos, state);
        BY_PLAYER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(new Spot(SiegeManager.dimensionName(level), pos.immutable()));
    }

    /**
     * A player set a block of his own down where a night block had stood (a light, a ladder blown
     * away): from now on it is an ordinary block, mined with its drop.
     */
    public static void placedOwn(ServerLevel level, BlockPos pos) {
        if (TempBlocks.isTemp(level, pos)) {
            TempBlocks.forget(level, pos);
        }
    }

    /** Once a second: the kit blocks of players whose kit went (a dungeon or camp fight is over) go too. */
    static void tick(MinecraftServer server, Predicate<UUID> wearsKit) {
        if (BY_PLAYER.isEmpty() || ++counter % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, List<Spot>>> it = BY_PLAYER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Spot>> entry = it.next();
            if (wearsKit.test(entry.getKey())) {
                continue;
            }
            for (Spot spot : entry.getValue()) {
                ServerLevel level = SiegeManager.levelByName(server, spot.dimension());
                if (level != null && level.isLoaded(spot.pos()) && TempBlocks.isTemp(level, spot.pos())) {
                    TempBlocks.remove(level, spot.pos());   // without a drop; one in an unloaded chunk goes at the next night's end
                }
            }
            it.remove();
        }
    }

    /** For the tests: how many kit blocks this player has standing on the list. */
    public static int listed(UUID player) {
        List<Spot> spots = BY_PLAYER.get(player);
        return spots == null ? 0 : spots.size();
    }
}
