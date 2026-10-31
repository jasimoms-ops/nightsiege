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
     * At most this many chunks per world (31.10., survival safety): about 0.3 MB in the save. Over it,
     * the chunk farthest from the core gives way (camps and dungeons rise near the core).
     */
    static final int MAX_PER_DIMENSION = 12_000;

    /**
     * A player set a block down here (the block item mixin). Only blocks that look like nature (dirt,
     * stone, logs, sand …) are written down: anything else (planks, torches, glass …) the site checks
     * see by itself, and a torch in a cave does not shut a whole chunk.
     *
     * <p>31.10. (survival safety): the save is no longer written for every new chunk on the spot (in a
     * survival world that is every few seconds), it is marked and written within 30 s; and only chunks
     * a camp or a dungeon could reach are kept: in the core's world, within the farthest site distance
     * of the config (plus the size of a dungeon) around the core. Without any core yet, the overworld
     * is kept, so buildings from before the core is set down count too.
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
        String dimension = SiegeManager.dimensionName(level);
        if (!inReach(m, level, dimension, pos.getX(), pos.getZ())) {
            return;
        }
        if (m.save().playerBuilt == null) {
            m.save().playerBuilt = new java.util.HashMap<>();
        }
        Set<Long> chunks = m.save().playerBuilt.computeIfAbsent(dimension, k -> new HashSet<>());
        if (chunks.add(key(pos.getX() >> 4, pos.getZ() >> 4))) {
            if (chunks.size() > MAX_PER_DIMENSION) {
                dropFarthest(m, dimension, chunks);
            }
            m.persistSoon();   // a new chunk: written with the next save, at the latest in 30 s
        }
    }

    /** Whether a camp or a dungeon could ever stand on this spot (see mark). */
    private static boolean inReach(SiegeManager m, ServerLevel level, String dimension, int x, int z) {
        java.util.List<CoreRecord> cores = m.save().cores;
        boolean anyCore = false;
        double reach = reach(m);
        for (CoreRecord core : cores == null ? java.util.List.<CoreRecord>of() : cores) {
            if (core == null) {
                continue;
            }
            anyCore = true;
            if (dimension.equals(core.dimension)) {
                double dx = x - core.x;
                double dz = z - core.z;
                if (dx * dx + dz * dz <= reach * reach) {
                    return true;
                }
            }
        }
        return !anyCore && level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
    }

    /** The farthest a camp or a dungeon reaches from the core, in blocks. */
    private static double reach(SiegeManager m) {
        com.mki.nightsiege.core.SiegeConfig cfg = m.config();
        if (cfg == null) {
            return 3300;
        }
        return Math.max(cfg.campMaxDistance + 64, cfg.expeditionMaxDistance + 256);
    }

    /** Over the cap: the chunk farthest from the cores of this world goes. */
    private static void dropFarthest(SiegeManager m, String dimension, Set<Long> chunks) {
        java.util.List<int[]> centers = new java.util.ArrayList<>();
        for (CoreRecord core : m.save().cores == null ? java.util.List.<CoreRecord>of() : m.save().cores) {
            if (core != null && dimension.equals(core.dimension)) {
                centers.add(new int[] {core.x >> 4, core.z >> 4});
            }
        }
        if (centers.isEmpty()) {
            centers.add(new int[] {0, 0});
        }
        long worst = 0L;
        double worstDistance = -1;
        for (long k : chunks) {
            int cx = (int) k;
            int cz = (int) (k >> 32);
            double near = Double.MAX_VALUE;
            for (int[] c : centers) {
                double dx = cx - c[0];
                double dz = cz - c[1];
                near = Math.min(near, dx * dx + dz * dz);
            }
            if (near > worstDistance) {
                worstDistance = near;
                worst = k;
            }
        }
        chunks.remove(worst);
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
