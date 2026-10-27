package com.mki.nightsiege.game;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the monsters break in a night comes back after it (Simon, 27 October 2026: "only
 * the automatic rebuild, but everything that was destroyed in the night"). The base itself is
 * kept by BaseDefense's snapshot; this log adds what the diggers, the sappers and explosions
 * break anywhere else in the siege world. Containers are left out (their contents fall out).
 */
public final class NightDamage {

    private static final int MAX = 200_000;
    private static ServerLevel world;
    private static final Long2ObjectLinkedOpenHashMap<BlockState> LOST = new Long2ObjectLinkedOpenHashMap<>();

    private NightDamage() {
    }

    static void begin(ServerLevel level) {
        world = level;
        LOST.clear();
    }

    static void end() {
        world = null;
        LOST.clear();
    }

    /** A block is about to be broken by a monster or an explosion of the night. */
    public static void record(Level level, BlockPos pos, BlockState old) {
        if (world == null || level != world || old == null || old.isAir() || old.getBlock() instanceof LiquidBlock
                || LOST.size() >= MAX || TempBlocks.isTemp(level, pos) || ModContent.trapOf(old) != null
                || ModContent.isCore(old)) {
            return;
        }
        if (old.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container) {
            return;
        }
        LOST.putIfAbsent(pos.asLong(), old);
    }

    /** True while a night's damage is being written down (for the explosion mixin). */
    public static boolean recording(Level level) {
        return world != null && level == world;
    }

    /** Puts every logged block back where nothing else stands now; returns how many. */
    static int restore(ServerLevel level) {
        if (level != world || LOST.isEmpty()) {
            return 0;
        }
        List<Long> keys = new ArrayList<>(LOST.keySet());
        keys.sort((a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));   // from the bottom up
        int put = 0;
        for (long key : keys) {
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            if (!now.isAir() && !now.canBeReplaced()) {
                continue;
            }
            if (level.setBlock(pos, LOST.get(key), Block.UPDATE_CLIENTS)) {
                put++;
            }
        }
        LOST.clear();
        return put;
    }
}
