package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * Where players have built (update 30.10., Simon: "die dungeons oder heerlager dürfen auf keinen fall
 * in irgendwelchen von spieler platzierten blöcken spawnen"). Every chunk a player sets a block down
 * in with a block that looks like nature is written down for good; the war camp and the dungeons
 * keep out of those chunks. Everything else shows by its blocks: the site checks turn down anything
 * that is not nature, every block of the space checked.
 */
public final class PlayerBuilt {

    private PlayerBuilt() {
    }

    /**
     * A player set a block down here (the block item mixin). Only blocks that look like nature (dirt,
     * stone, logs, sand …) are written down: anything else (planks, torches, glass …) the site checks
     * see by itself, and a torch in a cave does not shut a whole chunk.
     */
    public static void mark(ServerLevel level, BlockPos pos) {
        SiegeManager m = SiegeManager.get();
        if (m == null || m.save() == null || level == null || pos == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState placed = level.getBlockState(pos);
        if (placed.isAir() || !SiegeCamp.natural(placed)) {
            return;
        }
        if (m.save().playerBuilt == null) {
            m.save().playerBuilt = new java.util.HashMap<>();
        }
        Set<Long> chunks = m.save().playerBuilt.computeIfAbsent(SiegeManager.dimensionName(level), k -> new HashSet<>());
        if (chunks.add(key(pos.getX() >> 4, pos.getZ() >> 4))) {
            m.persist();   // a new chunk: rare, so it is written at once
        }
    }

    /** A chunk's key, as {@code ChunkPos.asLong}: x in the low half, z in the high half. */
    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    /** True when players have set blocks down in this chunk. */
    public static boolean marked(ServerLevel level, int chunkX, int chunkZ) {
        SiegeManager m = SiegeManager.get();
        if (m == null || m.save() == null || m.save().playerBuilt == null || level == null) {
            return false;
        }
        Set<Long> chunks = m.save().playerBuilt.get(SiegeManager.dimensionName(level));
        return chunks != null && chunks.contains(key(chunkX, chunkZ));
    }

    /** True when any chunk touching this square of blocks holds a player's building. */
    public static boolean anyIn(ServerLevel level, int x0, int z0, int x1, int z1) {
        for (int cx = Math.min(x0, x1) >> 4; cx <= Math.max(x0, x1) >> 4; cx++) {
            for (int cz = Math.min(z0, z1) >> 4; cz <= Math.max(z0, z1) >> 4; cz++) {
                if (marked(level, cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }
}
