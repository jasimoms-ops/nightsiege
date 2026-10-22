package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the defenders have built around their core.
 *
 * <p>From the core the scan walks the ways a monster could walk: on solid floor
 * with two blocks of room, one block up, up to three down, swimming through
 * water. Fences and walls stop it; doors and gates never do, even shut, since
 * every door is a way in. If it never gets further
 * than the scan radius, the base is closed: its walls are where the walk ended,
 * and monsters spawn outside them. Otherwise the base is open; its edge is taken
 * from the built blocks around the core (planks, bricks, glass, doors and the
 * like) and the place where the way out leaves the base is the gap.</p>
 */
public final class BaseScan {

    /** Slices of 5 degrees around the core. */
    public static final int SLICES = 72;
    private static final int MAX_NODES = 250_000;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] STEPS = {0, 1, -1, -2, -3};

    public final BlockPos core;
    public final boolean closed;
    /** Distance from the core to the outer edge of the base, per slice. */
    private final double[] edge;
    /** Columns inside the walls (closed bases), packed x/z. */
    private final Set<Long> inside;
    /** Open bases: where the way out leaves the base, or null. */
    public final BlockPos gap;
    public final int minX;
    public final int maxX;
    public final int minZ;
    public final int maxZ;

    private BaseScan(BlockPos core, boolean closed, double[] edge, Set<Long> inside, BlockPos gap) {
        this.core = core;
        this.closed = closed;
        this.edge = edge;
        this.inside = inside;
        this.gap = gap;
        int x1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int z1 = Integer.MAX_VALUE;
        int z2 = Integer.MIN_VALUE;
        if (closed && !inside.isEmpty()) {
            for (long packed : inside) {
                int x = (int) (packed >> 32);
                int z = (int) packed;
                x1 = Math.min(x1, x);
                x2 = Math.max(x2, x);
                z1 = Math.min(z1, z);
                z2 = Math.max(z2, z);
            }
        } else {
            for (int s = 0; s < SLICES; s++) {
                double a = (s + 0.5) * 2 * Math.PI / SLICES;
                int x = core.getX() + (int) Math.round(Math.cos(a) * edge[s]);
                int z = core.getZ() + (int) Math.round(Math.sin(a) * edge[s]);
                x1 = Math.min(x1, x);
                x2 = Math.max(x2, x);
                z1 = Math.min(z1, z);
                z2 = Math.max(z2, z);
            }
        }
        this.minX = x1;
        this.maxX = x2;
        this.minZ = z1;
        this.maxZ = z2;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    private static int slice(double dx, double dz) {
        double a = Math.atan2(dz, dx);
        if (a < 0) {
            a += 2 * Math.PI;
        }
        return Math.min(SLICES - 1, (int) (a / (2 * Math.PI) * SLICES));
    }

    /** Distance from the core to the edge of the base in the direction of a point. */
    public double edgeTowards(double x, double z) {
        return edge[slice(x - (core.getX() + 0.5), z - (core.getZ() + 0.5))];
    }

    /** Whether a column belongs to the base (inside its walls, or within its built edge). */
    public boolean contains(int x, int z) {
        if (closed) {
            return inside.contains(pack(x, z));
        }
        double dx = x + 0.5 - (core.getX() + 0.5);
        double dz = z + 0.5 - (core.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= edgeTowards(x + 0.5, z + 0.5);
    }

    /** Whether a column lies within the edge of the base plus a margin (the walls themselves). */
    public boolean near(int x, int z, double margin) {
        if (closed && inside.contains(pack(x, z))) {
            return true;
        }
        double dx = x + 0.5 - (core.getX() + 0.5);
        double dz = z + 0.5 - (core.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= edgeTowards(x + 0.5, z + 0.5) + margin;
    }

    /** The farthest the edge reaches from the core in any direction. */
    public double reach() {
        double most = 0;
        for (double e : edge) {
            most = Math.max(most, e);
        }
        return most;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------

    public static BaseScan scan(ServerLevel level, BlockPos core, int radius) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0) && walkable(level, p) && !parent.containsKey(p.asLong())) {
                        parent.put(p.asLong(), Long.MIN_VALUE);
                        queue.add(p);
                    }
                }
            }
        }
        int minY = core.getY() - 24;
        int maxY = core.getY() + 32;
        BlockPos escape = null;
        int r2 = radius * radius;
        search:
        while (!queue.isEmpty()) {
            if (parent.size() > MAX_NODES) {
                escape = queue.peek();
                break;
            }
            BlockPos p = queue.poll();
            for (int[] d : DIRS) {
                for (int dy : STEPS) {
                    BlockPos q = p.offset(d[0], dy, d[1]);
                    if (q.getY() < minY || q.getY() > maxY) {
                        continue;
                    }
                    int hx = q.getX() - core.getX();
                    int hz = q.getZ() - core.getZ();
                    if (!level.hasChunk(q.getX() >> 4, q.getZ() >> 4) || hx * hx + hz * hz > r2) {
                        if (walkable(level, q) || !level.hasChunk(q.getX() >> 4, q.getZ() >> 4)) {
                            escape = p;
                            break search;
                        }
                        continue;
                    }
                    if (!canMove(level, p, q, dy)) {
                        continue;
                    }
                    if (!parent.containsKey(q.asLong())) {
                        parent.put(q.asLong(), p.asLong());
                        queue.add(q);
                    }
                    break;   // one way per direction is enough
                }
            }
        }

        if (escape == null) {
            // Closed: the walk ended at the walls.
            Set<Long> inside = new HashSet<>();
            double[] edge = new double[SLICES];
            for (long packedPos : parent.keySet()) {
                BlockPos p = BlockPos.of(packedPos);
                inside.add(pack(p.getX(), p.getZ()));
                double dx = p.getX() - core.getX();
                double dz = p.getZ() - core.getZ();
                int s = slice(dx, dz);
                edge[s] = Math.max(edge[s], Math.sqrt(dx * dx + dz * dz) + 1.5);
            }
            fill(edge, 4.0);
            return new BaseScan(core, true, edge, inside, null);
        }

        // Open: the edge follows what was built, the gap is where the way out leaves it.
        double[] edge = builtEdge(level, core, radius);
        BlockPos gap = null;
        java.util.List<BlockPos> path = new java.util.ArrayList<>();
        long at = escape.asLong();
        while (at != Long.MIN_VALUE && path.size() < 10_000) {
            path.add(BlockPos.of(at));
            Long up = parent.get(at);
            if (up == null) {
                break;
            }
            at = up;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            BlockPos p = path.get(i);
            double dx = p.getX() + 0.5 - (core.getX() + 0.5);
            double dz = p.getZ() + 0.5 - (core.getZ() + 0.5);
            if (Math.sqrt(dx * dx + dz * dz) >= edge[slice(dx, dz)] - 1.0) {
                gap = p;
                break;
            }
        }
        return new BaseScan(core, false, edge, new HashSet<>(), gap);
    }

    /** Slices the walk never reached take the larger of their neighbours. */
    private static void fill(double[] edge, double least) {
        for (int pass = 0; pass < SLICES; pass++) {
            boolean changed = false;
            for (int s = 0; s < SLICES; s++) {
                if (edge[s] <= 0) {
                    double n = Math.max(edge[(s + 1) % SLICES], edge[(s + SLICES - 1) % SLICES]);
                    if (n > 0) {
                        edge[s] = n;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        for (int s = 0; s < SLICES; s++) {
            edge[s] = Math.max(least, edge[s]);
        }
    }

    /**
     * Per slice, the farthest built block on the way out from the core; the search
     * in a slice stops after sixteen blocks with nothing built, so a village far
     * away does not count as part of the base.
     */
    private static double[] builtEdge(ServerLevel level, BlockPos core, int radius) {
        double[] edge = new double[SLICES];
        for (int s = 0; s < SLICES; s++) {
            double a = (s + 0.5) * 2 * Math.PI / SLICES;
            double found = 0;
            int empty = 0;
            for (int r = 2; r <= radius && empty < 16; r++) {
                int x = core.getX() + (int) Math.round(Math.cos(a) * r);
                int z = core.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    break;
                }
                boolean built = false;
                for (int y = core.getY() - 6; y <= core.getY() + 20 && !built; y++) {
                    built = isBuilt(level.getBlockState(new BlockPos(x, y, z)).getBlock());
                }
                if (built) {
                    found = r + 1;
                    empty = 0;
                } else {
                    empty++;
                }
            }
            edge[s] = found;
        }
        fill(edge, 8.0);
        return edge;
    }

    private static final String[] BUILT = {"planks", "brick", "stairs", "slab", "_wall", "fence", "glass", "door",
            "trapdoor", "torch", "lantern", "chest", "barrel", "furnace", "crafting", "wool", "carpet", "concrete",
            "smooth_", "polished_", "chiseled_", "cut_", "_bed", "bookshelf", "ladder", "scaffolding", "iron_bars",
            "anvil", "table", "stripped_", "pane", "banner", "sign", "lamp", "rail", "hopper", "cobblestone",
            "terracotta", "tiles", "pillar", "siege_core", "trap", "turret", "tesla"};

    /** Blocks that do not grow on their own: whatever is made of them was built. */
    static boolean isBuilt(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (path.equals("air") || path.endsWith("_ore") || path.contains("leaves") || path.contains("log")
                && !path.startsWith("stripped_")) {
            return false;
        }
        for (String part : BUILT) {
            if (path.contains(part)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ walking

    private static boolean walkable(ServerLevel level, BlockPos p) {
        FluidState fluid = level.getFluidState(p);
        if (fluid.is(Fluids.WATER) && open(level, p.above())) {
            return true;   // monsters swim
        }
        BlockPos below = p.below();
        BlockState floor = level.getBlockState(below);
        return !floor.getCollisionShape(level, below).isEmpty() && low(level, p) && open(level, p.above());
    }

    private static boolean canMove(ServerLevel level, BlockPos from, BlockPos to, int dy) {
        if (!walkable(level, to)) {
            return false;
        }
        if (dy > 0) {
            return open(level, from.above(2));   // room to jump up a block
        }
        // Walking off an edge: the way down has to be free.
        for (int y = to.getY() + 2; y <= from.getY() + 1; y++) {
            if (!open(level, new BlockPos(to.getX(), y, to.getZ()))) {
                return false;
            }
        }
        return true;
    }

    /** Nothing in the way at all. A door or gate counts as a way through. */
    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (isDoor(state)) {
            return true;
        }
        return state.getCollisionShape(level, p).isEmpty() && !state.getFluidState().is(Fluids.LAVA);
    }

    static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /** Free, or only something low to walk on (a carpet, a bottom slab, a little snow). */
    private static boolean low(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.getFluidState().is(Fluids.LAVA)) {
            return false;
        }
        if (isDoor(state)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, p);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5;
    }
}
