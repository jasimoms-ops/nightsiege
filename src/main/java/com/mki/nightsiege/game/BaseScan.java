package com.mki.nightsiege.game;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What the defenders have built around their core.
 *
 * <p>Since the raid plan (Simon, 27 October 2026: "a castle and a few houses around it") the
 * scan looks at the base from outside: a monster's walk from the edge of the search circle
 * inwards, on solid floor with two blocks of room, one block up, up to three down, swimming
 * through water, where doors and fence gates are shut. Whatever the core can be reached from
 * but that walk cannot is the base: its walls hold, its doors and gates are its gates. An open
 * gateway without a door (an archway) is closed in thought, as a gate that never shuts. The
 * houses around a castle are outside it: there the monsters gather.</p>
 *
 * <p>Only when no wall closes the core in (or the gaps are too wide) is the base open; its edge
 * is then taken from the built blocks around the core and the gap is where the way out leaves
 * it, as before.</p>
 *
 * <p>The walk outside also measures, for every spot of open ground in front of the walls, how far
 * a monster walks from there to the wall: the raid spawns, gathers and shoots from those spots
 * (see {@link RaidPlan}).</p>
 */
public final class BaseScan {

    /** Slices of 5 degrees around the core. */
    public static final int SLICES = 72;
    private static final int MAX_NODES = 250_000;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] STEPS = {0, 1, -1, -2, -3};
    /** Open gateways closed in thought: how many narrow places are tried. */
    private static final int MAX_ARCH_TRIES = 10;
    private static final int MAX_ARCH_ROUNDS = 4;
    /** A yard shut in by a gateway closed in thought is at most this big (walkable spots). */
    private static final int YARD_LIMIT = 30_000;
    /** The disc that closes a gateway in thought. */
    private static final int ARCH_RADIUS = 3;
    /** A passage at most this wide counts as a gateway. */
    private static final int ARCH_WIDTH = 5;
    /** Walking distances from the wall: where the raid shoots, gathers and comes from. */
    public static final int FIRE_MIN = 4;
    public static final int FIRE_MAX = 9;
    public static final int RALLY_MIN = 7;
    public static final int RALLY_MAX = 12;
    public static final int SPAWN_MIN = 16;
    public static final int SPAWN_MAX = 32;
    private static final int SPOTS_PER_SLICE = 48;
    /** How far from the inside the foot of a wall can be (thick walls, towers). */
    private static final int WALL_BAND = 4;

    public final BlockPos core;
    public final boolean closed;
    /** Distance from the core to the outer edge of the base, per slice. */
    private final double[] edge;
    /** Columns inside the walls (closed bases), packed x/z. */
    private final LongOpenHashSet inside;
    /** Open bases: where the way out leaves the base, or null. */
    public final BlockPos gap;
    public final int minX;
    public final int maxX;
    public final int minZ;
    public final int maxZ;
    /** The gates of a closed base: doors and fence gates in the wall, and open gateways. */
    public final List<Gate> gates;
    /** Open ground outside the walls by slice: shooting line, gathering place, spawn ground. */
    private final List<BlockPos>[] fire;
    private final List<BlockPos>[] rally;
    private final List<BlockPos>[] spawn;

    /**
     * A way into a closed base. {@code pos} is the door (its lower half) or fence gate, or the
     * middle of an open gateway ({@code arch}); {@code front} is the ground in front of it outside.
     */
    public record Gate(BlockPos pos, BlockPos front, boolean arch) {
    }

    @SuppressWarnings("unchecked")
    private BaseScan(BlockPos core, boolean closed, double[] edge, LongOpenHashSet inside, BlockPos gap, List<Gate> gates,
                     List<BlockPos>[] fire, List<BlockPos>[] rally, List<BlockPos>[] spawn) {
        this.core = core;
        this.closed = closed;
        this.edge = edge;
        this.inside = inside;
        this.gap = gap;
        this.gates = gates == null ? List.of() : List.copyOf(gates);
        this.fire = fire != null ? fire : emptySlices();
        this.rally = rally != null ? rally : emptySlices();
        this.spawn = spawn != null ? spawn : emptySlices();
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

    @SuppressWarnings("unchecked")
    private static List<BlockPos>[] emptySlices() {
        List<BlockPos>[] out = new List[SLICES];
        for (int s = 0; s < SLICES; s++) {
            out[s] = new ArrayList<>();
        }
        return out;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    static int slice(double dx, double dz) {
        double a = Math.atan2(dz, dx);
        if (a < 0) {
            a += 2 * Math.PI;
        }
        return Math.min(SLICES - 1, (int) (a / (2 * Math.PI) * SLICES));
    }

    /** The slice an angle (radians, atan2 of z and x) falls into. */
    public static int sliceOfAngle(double angle) {
        return slice(Math.cos(angle), Math.sin(angle));
    }

    /** The middle of a slice as an angle. */
    public static double angleOfSlice(int s) {
        return (Math.floorMod(s, SLICES) + 0.5) * 2 * Math.PI / SLICES;
    }

    /** The angle of a spot seen from the core. */
    public double angleOf(double x, double z) {
        return Math.atan2(z - (core.getZ() + 0.5), x - (core.getX() + 0.5));
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

    /** Spots of each kind in a slice (for the raid and the gametest). */
    public List<BlockPos> spawnSpots(int slice) {
        return spawn[Math.floorMod(slice, SLICES)];
    }

    public List<BlockPos> rallySpots(int slice) {
        return rally[Math.floorMod(slice, SLICES)];
    }

    public List<BlockPos> fireSpots(int slice) {
        return fire[Math.floorMod(slice, SLICES)];
    }

    /** All spawn spots around the base. */
    public int spawnSpotCount() {
        int n = 0;
        for (List<BlockPos> l : spawn) {
            n += l.size();
        }
        return n;
    }

    /**
     * A spot of one kind near an angle: from the slice of the angle, then from its neighbours
     * further and further out (up to {@code spread} slices to either side). Null when none.
     */
    public BlockPos spotNear(List<BlockPos>[] kind, double angle, int spread, Random random) {
        int s0 = sliceOfAngle(angle);
        for (int d = 0; d <= spread; d++) {
            int first = random.nextBoolean() ? 1 : -1;
            for (int sign : new int[] {first, -first}) {
                List<BlockPos> list = kind[Math.floorMod(s0 + sign * d, SLICES)];
                if (!list.isEmpty()) {
                    return list.get(random.nextInt(list.size()));
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return null;
    }

    public BlockPos spawnNear(double angle, int spread, Random random) {
        return spotNear(spawn, angle, spread, random);
    }

    public BlockPos rallyNear(double angle, int spread, Random random) {
        return spotNear(rally, angle, spread, random);
    }

    public BlockPos fireNear(double angle, int spread, Random random) {
        return spotNear(fire, angle, spread, random);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ the scan

    public static BaseScan scan(ServerLevel level, BlockPos core, int radius) {
        Walker w = new Walker(level, core);
        int minY = core.getY() - 24;
        int maxY = core.getY() + 32;
        LongOpenHashSet coreZone = new LongOpenHashSet();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0) && w.walkable(p.asLong(), false)) {
                        coreZone.add(p.asLong());
                    }
                }
            }
        }

        // From outside in, doors shut. A ring too far out to be loaded shrinks.
        int ring = radius;
        LongArrayFIFOQueue seeds = w.ringSeeds(ring, minY, maxY);
        while (seeds.size() < 16 && ring > 24) {
            ring -= 12;
            seeds = w.ringSeeds(ring, minY, maxY);
        }
        LongOpenHashSet virtual = new LongOpenHashSet();
        List<BlockPos> arches = new ArrayList<>();
        Flood out = w.flood(seeds, true, coreZone, virtual, null, ring, minY, maxY, false);

        // Gateways without a door (Simon's castle may have an archway): where the walk from outside
        // comes in, the narrow places on its way (outermost first) are closed in thought, one at a
        // time. If that shuts in the core (or the yard in front of a door of the base, when the core
        // stands in a house of its own), the narrow place was a gateway: it stays closed.
        LongOpenHashSet triedFronts = new LongOpenHashSet();
        int tests = 0;
        for (int round = 0; round < MAX_ARCH_ROUNDS && !out.overflow && !coreZone.isEmpty() && tests < MAX_ARCH_TRIES; round++) {
            long start;
            if (out.hit != Long.MIN_VALUE) {
                start = out.hit;
            } else {
                LongArrayFIFOQueue inner = new LongArrayFIFOQueue();
                for (long p : coreZone) {
                    inner.enqueue(p);
                }
                Flood in = w.flood(inner, false, null, null, out.seen, radius, core.getY() - 8, maxY, false);
                start = Long.MIN_VALUE;
                for (Gate g : w.gates(in.seen, out.seen, List.of())) {
                    long f = g.front().asLong();
                    if (triedFronts.add(f)) {
                        start = f;
                        break;
                    }
                }
                if (start == Long.MIN_VALUE) {
                    break;   // every door of the base opens onto open land
                }
            }
            BlockPos accepted = null;
            for (BlockPos c : w.narrowPlaces(out.pathFrom(start), out.seen, core)) {
                if (tests++ >= MAX_ARCH_TRIES) {
                    break;
                }
                LongOpenHashSet more = new LongOpenHashSet(virtual);
                disc(more, c);
                if (w.shutIn(start, more, ring, minY, maxY)) {
                    accepted = c;
                    virtual = more;
                    break;
                }
            }
            if (accepted == null) {
                if (out.hit != Long.MIN_VALUE) {
                    break;   // the core lies open: an open base
                }
                continue;   // the next door of the base
            }
            arches.add(accepted);
            out = w.flood(w.ringSeeds(ring, minY, maxY), true, coreZone, virtual, null, ring, minY, maxY, false);
        }

        if (!coreZone.isEmpty() && out.hit == Long.MIN_VALUE && !out.overflow && out.seen.size() > 64) {
            // Closed: the core's own walk (through its doors) that never steps onto the ground outside.
            LongArrayFIFOQueue inner = new LongArrayFIFOQueue();
            for (long p : coreZone) {
                inner.enqueue(p);
            }
            // Not far down: a mine under the castle is no part of the ground it stands on.
            Flood in = w.flood(inner, false, null, null, out.seen, radius, core.getY() - 8, maxY, false);
            if (!in.overflow && !in.seen.isEmpty()) {
                LongOpenHashSet insideCols = new LongOpenHashSet();
                double[] edge = new double[SLICES];
                for (long packedPos : in.seen) {
                    int x = BlockPos.getX(packedPos);
                    int z = BlockPos.getZ(packedPos);
                    insideCols.add(pack(x, z));
                    double dx = x - core.getX();
                    double dz = z - core.getZ();
                    int s = slice(dx, dz);
                    edge[s] = Math.max(edge[s], Math.sqrt(dx * dx + dz * dz) + 1.5);
                }
                for (BlockPos a : arches) {
                    disc(insideCols, a);
                }
                fill(edge, 4.0);
                List<Gate> gates = w.gates(in.seen, out.seen, arches);
                @SuppressWarnings("unchecked")
                List<BlockPos>[][] spots = w.field(out.seen, insideCols, gates, core);
                return new BaseScan(core, true, edge, insideCols, null, gates, spots[0], spots[1], spots[2]);
            }
        }

        // Open: the edge follows what was built, the gap is where the way out leaves it.
        double[] edge = builtEdge(level, core, radius);
        BlockPos gap = null;
        if (out.hit != Long.MIN_VALUE) {
            List<Long> path = out.pathFrom(out.hit);
            for (long at : path) {
                BlockPos p = BlockPos.of(at);
                double dx = p.getX() + 0.5 - (core.getX() + 0.5);
                double dz = p.getZ() + 0.5 - (core.getZ() + 0.5);
                if (Math.sqrt(dx * dx + dz * dz) >= edge[slice(dx, dz)] - 1.0) {
                    gap = p;
                    break;
                }
            }
        }
        List<BlockPos>[] spawnOpen = emptySlices();
        Random pick = new Random(core.asLong());
        for (long at : out.seen) {
            BlockPos p = BlockPos.of(at);
            double dx = p.getX() + 0.5 - (core.getX() + 0.5);
            double dz = p.getZ() + 0.5 - (core.getZ() + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            int s = slice(dx, dz);
            if (d >= edge[s] + 8 && d <= edge[s] + 24 && w.openGround(p)) {
                addCapped(spawnOpen[s], p, pick);
            }
        }
        return new BaseScan(core, false, edge, new LongOpenHashSet(), gap, List.of(), null, null, spawnOpen);
    }

    /** The columns of a gateway closed in thought. */
    private static void disc(LongOpenHashSet columns, BlockPos c) {
        for (int dx = -ARCH_RADIUS; dx <= ARCH_RADIUS; dx++) {
            for (int dz = -ARCH_RADIUS; dz <= ARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz <= ARCH_RADIUS * ARCH_RADIUS) {
                    columns.add(pack(c.getX() + dx, c.getZ() + dz));
                }
            }
        }
    }

    private static void addCapped(List<BlockPos> list, BlockPos p, Random random) {
        if (list.size() < SPOTS_PER_SLICE) {
            list.add(p);
        } else {
            int i = random.nextInt(list.size() * 3);
            if (i < list.size()) {
                list.set(i, p);   // a rough sample over the whole slice
            }
        }
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

    static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * Whether a monster can walk from {@code from} into a column of the base, around a spot
     * (a hole in the wall): a short walk with the doors shut, at most {@code budget} steps
     * within {@code reach} blocks of the spot. Ladders count as a way up.
     */
    public boolean walkInto(ServerLevel level, BlockPos from, BlockPos around, int reach, int budget) {
        Walker w = new Walker(level, core);
        if (from == null || !w.walkable(from.asLong(), true)) {
            return false;   // the spot in front is built over (a hole walled up)
        }
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet seen = new LongOpenHashSet();
        queue.enqueue(from.asLong());
        seen.add(from.asLong());
        int r2 = reach * reach;
        while (!queue.isEmpty() && seen.size() < budget) {
            long at = queue.dequeueLong();
            int x = BlockPos.getX(at);
            int y = BlockPos.getY(at);
            int z = BlockPos.getZ(at);
            if (contains(x, z)) {
                return true;
            }
            for (int[] d : DIRS) {
                for (int dy : STEPS) {
                    long q = BlockPos.asLong(x + d[0], y + dy, z + d[1]);
                    int hx = x + d[0] - around.getX();
                    int hz = z + d[1] - around.getZ();
                    if (hx * hx + hz * hz > r2 || !level.hasChunk((x + d[0]) >> 4, (z + d[1]) >> 4)) {
                        continue;
                    }
                    if (!w.canMove(at, q, dy, true, null)) {
                        continue;
                    }
                    if (seen.add(q)) {
                        queue.enqueue(q);
                    }
                    break;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ walking

    /** Result of one walk: where it got, how (parents) and whether it touched the core. */
    private static final class Flood {
        final LongOpenHashSet seen = new LongOpenHashSet();
        final Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        long hit = Long.MIN_VALUE;
        boolean overflow;

        Flood() {
            parent.defaultReturnValue(Long.MIN_VALUE);
        }

        /** The way from a spot back to where the walk began (for an outside walk: core to ring). */
        List<Long> pathFrom(long at) {
            List<Long> path = new ArrayList<>();
            long p = at;
            while (p != Long.MIN_VALUE && path.size() < 10_000) {
                path.add(p);
                p = parent.get(p);
            }
            return path;
        }
    }

    /**
     * The blocks around the base as a monster's feet know them, each looked at once and kept:
     * the scan walks the same ground several times (outside, inside, gateways).
     */
    private static final class Walker {
        private static final byte KNOWN = 1;
        private static final byte FREE = 2;      // nothing to bump into (and no lava)
        private static final byte DOOR = 4;      // a door or fence gate
        private static final byte LOW = 8;       // free or only something low to walk on
        private static final byte FLOOR = 16;    // something to stand on
        private static final byte WATER = 32;
        private final ServerLevel level;
        private final BlockPos core;
        private final Long2ByteOpenHashMap info = new Long2ByteOpenHashMap();

        Walker(ServerLevel level, BlockPos core) {
            this.level = level;
            this.core = core;
        }

        private byte info(long key) {
            byte b = info.get(key);
            if (b != 0) {
                return b;
            }
            BlockPos p = BlockPos.of(key);
            BlockState state = level.getBlockState(p);
            boolean lava = state.getFluidState().is(Fluids.LAVA);
            VoxelShape shape = state.getCollisionShape(level, p);
            b = KNOWN;
            if (shape.isEmpty() && !lava) {
                b |= FREE;
            }
            if (isDoor(state)) {
                b |= DOOR;
            }
            if (!lava && (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5)) {
                b |= LOW;
            }
            if (!shape.isEmpty()) {
                b |= FLOOR;
            }
            if (state.getFluidState().is(Fluids.WATER)) {
                b |= WATER;
            }
            info.put(key, b);
            return b;
        }

        /** Nothing in the way; a door or gate only while the doors are open. */
        boolean open(long key, boolean doorsShut) {
            byte b = info(key);
            if ((b & DOOR) != 0) {
                return !doorsShut;
            }
            return (b & FREE) != 0;
        }

        private boolean low(long key, boolean doorsShut) {
            byte b = info(key);
            if ((b & DOOR) != 0) {
                return !doorsShut;
            }
            return (b & LOW) != 0;
        }

        boolean walkable(long key, boolean doorsShut) {
            long above = BlockPos.offset(key, 0, 1, 0);
            if ((info(key) & WATER) != 0 && open(above, doorsShut)) {
                return true;   // monsters swim
            }
            long below = BlockPos.offset(key, 0, -1, 0);
            return (info(below) & FLOOR) != 0 && low(key, doorsShut) && open(above, doorsShut);
        }

        boolean canMove(long from, long to, int dy, boolean doorsShut, LongOpenHashSet virtual) {
            if (virtual != null && !virtual.isEmpty() && virtual.contains(pack(BlockPos.getX(to), BlockPos.getZ(to)))) {
                return false;   // a gateway closed in thought
            }
            if (!walkable(to, doorsShut)) {
                return false;
            }
            if (dy > 0) {
                return open(BlockPos.offset(from, 0, 2, 0), doorsShut);   // room to jump up a block
            }
            // Walking off an edge: the way down has to be free.
            int x = BlockPos.getX(to);
            int z = BlockPos.getZ(to);
            for (int y = BlockPos.getY(to) + 2; y <= BlockPos.getY(from) + 1; y++) {
                if (!open(BlockPos.asLong(x, y, z), doorsShut)) {
                    return false;
                }
            }
            return true;
        }

        /** Open ground under the sky, no water: where a monster can stand outside. */
        boolean openGround(BlockPos p) {
            if ((info(p.asLong()) & WATER) != 0 || (info(p.below().asLong()) & WATER) != 0) {
                return false;
            }
            return p.getY() <= core.getY() + 40 && level.canSeeSky(p);
        }

        /** Where the walk from outside starts: every spot to stand on along the search circle. */
        LongArrayFIFOQueue ringSeeds(int radius, int minY, int maxY) {
            LongArrayFIFOQueue seeds = new LongArrayFIFOQueue();
            LongOpenHashSet columns = new LongOpenHashSet();
            double inner = (radius - 1.5) * (radius - 1.5);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > radius * radius || d2 < inner) {
                        continue;
                    }
                    int x = core.getX() + dx;
                    int z = core.getZ() + dz;
                    if (!level.hasChunk(x >> 4, z >> 4) || !columns.add(pack(x, z))) {
                        continue;
                    }
                    for (int y = maxY; y >= minY; y--) {
                        long p = BlockPos.asLong(x, y, z);
                        if (walkable(p, true)) {
                            seeds.enqueue(p);
                        }
                    }
                }
            }
            return seeds;
        }

        /**
         * A walk. {@code target}: spots that end the walk's question (it notes the first one in
         * {@code hit}); {@code stopAtTarget} ends the walk there. {@code refuse}: spots it never
         * steps on (the inside walk does not leave the base).
         */
        Flood flood(LongArrayFIFOQueue start, boolean doorsShut, LongOpenHashSet target, LongOpenHashSet virtual,
                    LongOpenHashSet refuse, int radius, int minY, int maxY, boolean stopAtTarget) {
            Flood f = new Flood();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            while (!start.isEmpty()) {
                long p = start.dequeueLong();
                if ((refuse == null || !refuse.contains(p)) && f.seen.add(p)) {
                    queue.enqueue(p);
                    if (target != null && target.contains(p) && f.hit == Long.MIN_VALUE) {
                        f.hit = p;
                    }
                }
            }
            int r2 = radius * radius;
            while (!queue.isEmpty()) {
                if (f.seen.size() > MAX_NODES) {
                    f.overflow = true;
                    break;
                }
                if (stopAtTarget && f.hit != Long.MIN_VALUE) {
                    break;
                }
                long p = queue.dequeueLong();
                int x = BlockPos.getX(p);
                int y = BlockPos.getY(p);
                int z = BlockPos.getZ(p);
                for (int[] d : DIRS) {
                    int qx = x + d[0];
                    int qz = z + d[1];
                    int hx = qx - core.getX();
                    int hz = qz - core.getZ();
                    if (hx * hx + hz * hz > r2 || !level.hasChunk(qx >> 4, qz >> 4)) {
                        continue;
                    }
                    for (int dy : STEPS) {
                        int qy = y + dy;
                        if (qy < minY || qy > maxY) {
                            continue;
                        }
                        long q = BlockPos.asLong(qx, qy, qz);
                        if (!canMove(p, q, dy, doorsShut, virtual)) {
                            continue;
                        }
                        if ((refuse == null || !refuse.contains(q)) && f.seen.add(q)) {
                            f.parent.put(q, p);
                            queue.enqueue(q);
                            if (target != null && f.hit == Long.MIN_VALUE && target.contains(q)) {
                                f.hit = q;
                            }
                        }
                        break;   // one way per direction is enough
                    }
                }
            }
            return f;
        }

        /**
         * Whether a walk from a spot, doors shut, stays shut in (never gets near the search circle,
         * and stays smaller than a yard) with these gateways closed.
         */
        boolean shutIn(long start, LongOpenHashSet virtual, int ring, int minY, int maxY) {
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongOpenHashSet seen = new LongOpenHashSet();
            queue.enqueue(start);
            seen.add(start);
            int far = (ring - 3) * (ring - 3);
            while (!queue.isEmpty()) {
                if (seen.size() > YARD_LIMIT) {
                    return false;
                }
                long p = queue.dequeueLong();
                int x = BlockPos.getX(p);
                int y = BlockPos.getY(p);
                int z = BlockPos.getZ(p);
                int hx0 = x - core.getX();
                int hz0 = z - core.getZ();
                if (hx0 * hx0 + hz0 * hz0 >= far) {
                    return false;   // out on open land
                }
                for (int[] d : DIRS) {
                    int qx = x + d[0];
                    int qz = z + d[1];
                    if (!level.hasChunk(qx >> 4, qz >> 4)) {
                        return false;
                    }
                    for (int dy : STEPS) {
                        int qy = y + dy;
                        if (qy < minY || qy > maxY) {
                            continue;
                        }
                        long q = BlockPos.asLong(qx, qy, qz);
                        if (!canMove(p, q, dy, true, virtual)) {
                            continue;
                        }
                        if (seen.add(q)) {
                            queue.enqueue(q);
                        }
                        break;
                    }
                }
            }
            return true;
        }

        /**
         * Narrow places on the way from the core out (a gateway, an alley): the middle of each
         * run of narrow spots, the outermost first, within 48 blocks of the core.
         */
        List<BlockPos> narrowPlaces(List<Long> path, LongOpenHashSet ground, BlockPos core) {
            List<BlockPos> out = new ArrayList<>();
            int runStart = -1;
            for (int i = 0; i <= path.size(); i++) {
                boolean narrow = false;
                if (i < path.size() - 1) {
                    BlockPos p = BlockPos.of(path.get(i));
                    BlockPos n = BlockPos.of(path.get(i + 1));
                    double dist = Math.sqrt(p.distSqr(new BlockPos(core.getX(), p.getY(), core.getZ())));
                    if (dist >= 3 && dist <= 48) {
                        int ax = n.getX() - p.getX();
                        int az = n.getZ() - p.getZ();
                        int w = 1 + freeSide(ground, p, az, ax) + freeSide(ground, p, -az, -ax);
                        narrow = w <= ARCH_WIDTH;
                    }
                }
                if (narrow && runStart < 0) {
                    runStart = i;
                } else if (!narrow && runStart >= 0) {
                    out.add(BlockPos.of(path.get((runStart + i - 1) / 2)));
                    runStart = -1;
                }
            }
            // The path runs from the core out: the outermost gateway is the castle's own.
            Collections.reverse(out);
            return out;
        }

        /** How far the ground stays walkable to one side (up to 4 blocks). */
        private static int freeSide(LongOpenHashSet ground, BlockPos p, int sx, int sz) {
            int n = 0;
            for (int k = 1; k <= 4; k++) {
                boolean any = false;
                for (int dy = -1; dy <= 1 && !any; dy++) {
                    any = ground.contains(BlockPos.asLong(p.getX() + sx * k, p.getY() + dy, p.getZ() + sz * k));
                }
                if (!any) {
                    break;
                }
                n++;
            }
            return n;
        }

        /** Doors and gates between the inside and the ground outside, and the gateways closed in thought. */
        List<Gate> gates(LongOpenHashSet in, LongOpenHashSet out, List<BlockPos> arches) {
            List<Gate> gates = new ArrayList<>();
            LongOpenHashSet doors = new LongOpenHashSet();
            for (long p : in) {
                if ((info(p) & DOOR) == 0) {
                    continue;
                }
                int x = BlockPos.getX(p);
                int y = BlockPos.getY(p);
                int z = BlockPos.getZ(p);
                for (int[] d : DIRS) {
                    long front = Long.MIN_VALUE;
                    for (int dy : STEPS) {
                        long q = BlockPos.asLong(x + d[0], y + dy, z + d[1]);
                        if (out.contains(q)) {
                            front = q;
                            break;
                        }
                    }
                    if (front != Long.MIN_VALUE) {
                        // A double door or a wide gate is one gate.
                        boolean known = false;
                        for (long o : doors) {
                            if (Math.abs(BlockPos.getX(o) - x) + Math.abs(BlockPos.getZ(o) - z) <= 2 && Math.abs(BlockPos.getY(o) - y) <= 1) {
                                known = true;
                                break;
                            }
                        }
                        if (!known) {
                            doors.add(p);
                            gates.add(new Gate(BlockPos.of(p), BlockPos.of(front), false));
                        }
                        break;
                    }
                }
            }
            for (BlockPos a : arches) {
                BlockPos front = null;
                double best = Double.MAX_VALUE;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            long q = BlockPos.asLong(a.getX() + dx, a.getY() + dy, a.getZ() + dz);
                            int d2 = dx * dx + dz * dz;
                            if (d2 > 9 && d2 < best && out.contains(q)) {
                                best = d2;
                                front = BlockPos.of(q);
                            }
                        }
                    }
                }
                gates.add(new Gate(a, front != null ? front : a, true));
            }
            return gates;
        }

        /**
         * The ground outside by walking distance to the wall: shooting line, gathering place and
         * spawn ground, per slice ({@code [0]} fire, {@code [1]} rally, {@code [2]} spawn).
         */
        @SuppressWarnings("unchecked")
        List<BlockPos>[][] field(LongOpenHashSet out, LongOpenHashSet insideCols, List<Gate> gates, BlockPos core) {
            List<BlockPos>[] fire = emptySlices();
            List<BlockPos>[] rally = emptySlices();
            List<BlockPos>[] spawn = emptySlices();
            Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
            dist.defaultReturnValue(-1);
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            // The foot of the wall: ground outside within a few blocks of the inside (walls are thick).
            LongOpenHashSet band = new LongOpenHashSet();
            for (long c : insideCols) {
                int x = (int) (c >> 32);
                int z = (int) c;
                boolean rim = false;
                for (int[] d : DIRS) {
                    if (!insideCols.contains(pack(x + d[0], z + d[1]))) {
                        rim = true;
                        break;
                    }
                }
                if (!rim) {
                    continue;
                }
                for (int dx = -WALL_BAND; dx <= WALL_BAND; dx++) {
                    for (int dz = -WALL_BAND; dz <= WALL_BAND; dz++) {
                        band.add(pack(x + dx, z + dz));
                    }
                }
            }
            for (long p : out) {
                if (band.contains(pack(BlockPos.getX(p), BlockPos.getZ(p)))) {
                    dist.put(p, 0);
                    queue.enqueue(p);
                }
            }
            for (Gate g : gates) {
                long f = g.front().asLong();
                if (out.contains(f) && dist.get(f) < 0) {
                    dist.put(f, 0);
                    queue.enqueue(f);
                }
            }
            Random pick = new Random(core.asLong());
            while (!queue.isEmpty()) {
                long p = queue.dequeueLong();
                int d0 = dist.get(p);
                boolean isFire = d0 >= FIRE_MIN && d0 <= FIRE_MAX;
                boolean isRally = d0 >= RALLY_MIN && d0 <= RALLY_MAX;
                boolean isSpawn = d0 >= SPAWN_MIN && d0 <= SPAWN_MAX;
                if (isFire || isRally || isSpawn) {
                    BlockPos at = BlockPos.of(p);
                    if (openGround(at)) {
                        int s = slice(at.getX() + 0.5 - (core.getX() + 0.5), at.getZ() + 0.5 - (core.getZ() + 0.5));
                        if (isFire) {
                            addCapped(fire[s], at, pick);
                        }
                        if (isRally) {
                            addCapped(rally[s], at, pick);
                        }
                        if (isSpawn) {
                            addCapped(spawn[s], at, pick);
                        }
                    }
                }
                if (d0 >= SPAWN_MAX) {
                    continue;
                }
                int x = BlockPos.getX(p);
                int y = BlockPos.getY(p);
                int z = BlockPos.getZ(p);
                for (int[] d : DIRS) {
                    for (int dy : STEPS) {
                        long q = BlockPos.asLong(x + d[0], y + dy, z + d[1]);
                        if (out.contains(q)) {
                            if (dist.get(q) < 0) {
                                dist.put(q, d0 + 1);
                                queue.enqueue(q);
                            }
                            break;
                        }
                    }
                }
            }
            return new List[][] {fire, rally, spawn};
        }
    }
}
