package com.mki.nightsiege.game;

import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.ExpeditionKind.Layout;
import com.mki.nightsiege.core.ExpeditionKind.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks of a Night Siege dungeon (dungeon update, 27 October 2026: "Bau sie so, dass es
 * einen klaren Weg gibt"). One way only: a gatehouse on the surface, a stair down, then guard
 * rooms one after the other – each closed by an iron gate that opens when its guards are dead –
 * and the round arena for the wave fight at the end. Everything is lit, so nothing but the
 * dungeon's own monsters spawns inside.
 *
 * <p>Layout space: x to the side, z away from the core (the gate faces the core), y up from the
 * gatehouse floor. The underground walks at {@link #DEPTH}. Everything follows from origin,
 * facing, kind, room count and seed, so the save keeps only those.</p>
 */
public final class ExpeditionLayout {

    /** Walking height underground, below the gatehouse floor. */
    static final int DEPTH = -15;
    static final int ROOM_HALF = 5;
    static final int ROOM_LEN = 11;
    static final int ROOM_H = 6;
    static final int CORRIDOR = 5;
    static final int ARENA_R = 10;
    static final int ARENA_H = 9;
    /** First room's start (z). */
    static final int FIRST_ROOM = 20;

    /** Everything about one built dungeon, in world coordinates. */
    public static final class Plan {
        public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        public final List<BlockPos> roomCenters = new ArrayList<>();
        public final List<List<BlockPos>> roomSpots = new ArrayList<>();
        public final List<List<BlockPos>> roomGates = new ArrayList<>();
        /** Where a room wakes: its corridor and the room itself. */
        public final List<AABB> roomZones = new ArrayList<>();
        public final List<BlockPos> arenaGate = new ArrayList<>();
        public final List<BlockPos> waveSpots = new ArrayList<>();
        public BlockPos arenaCenter;
        public BlockPos chest;
        public AABB arena;
        /** In front of the gatehouse door (where the fallen are put). */
        public BlockPos door;
        /** The signal fire on the roof (it goes out when the dungeon is won). */
        public BlockPos fire;
        /** The way down: from here on one is inside. */
        public final List<AABB> inside = new ArrayList<>();
        /** Everything the dungeon takes (no breaking before it is won). */
        public AABB bounds;
    }

    private ExpeditionLayout() {
    }

    static int gatehouseHeight(Layout layout) {
        return switch (layout) {
            case KEEP -> 8;
            case CELLAR -> 4;
            default -> 6;
        };
    }

    static int arenaStart(int rooms) {
        return FIRST_ROOM + rooms * (ROOM_LEN + CORRIDOR);
    }

    /** How far the dungeon reaches away from the core (z of the arena's far wall). */
    static int length(int rooms) {
        return arenaStart(rooms) + 2 * ARENA_R + 2;
    }

    static Plan plan(BlockPos origin, int facing, ExpeditionKind kind, int rooms, long seed) {
        B b = new B(origin, facing, kind, seed);
        Plan plan = new Plan();
        int h = gatehouseHeight(kind.layout());
        plan.fire = gatehouse(b, h);
        stairs(b);
        int d = DEPTH;
        for (int i = 0; i < rooms; i++) {
            int r0 = FIRST_ROOM + i * (ROOM_LEN + CORRIDOR);
            room(b, r0);
        }
        int a0 = arenaStart(rooms);
        int cz = a0 + ARENA_R;
        arena(b, cz);
        // Tunnels after the halls: their air opens the walls where they meet.
        tunnel(b, 15, FIRST_ROOM - 1);
        for (int i = 0; i < rooms; i++) {
            int r0 = FIRST_ROOM + i * (ROOM_LEN + CORRIDOR);
            tunnel(b, r0 + ROOM_LEN, r0 + ROOM_LEN + CORRIDOR - 1);
        }
        for (int i = 0; i < rooms; i++) {
            int r0 = FIRST_ROOM + i * (ROOM_LEN + CORRIDOR);
            roomDecor(b, r0);
        }
        arenaDecor(b, cz);
        // Gates: one behind every room (closed), the arena's (open until the fight).
        for (int i = 0; i < rooms; i++) {
            int r0 = FIRST_ROOM + i * (ROOM_LEN + CORRIDOR);
            List<BlockPos> gate = new ArrayList<>();
            for (int x = -1; x <= 1; x++) {
                for (int y = d; y <= d + 3; y++) {
                    b.put(x, y, r0 + ROOM_LEN + 1, Blocks.IRON_BARS.defaultBlockState());
                    gate.add(b.at(x, y, r0 + ROOM_LEN + 1));
                }
            }
            plan.roomGates.add(gate);
            plan.roomCenters.add(b.at(0, d, r0 + ROOM_LEN / 2));
            List<BlockPos> spots = new ArrayList<>();
            // Free floor in every building: the keep's table, the crypt's coffins and the cellar's cells stay clear.
            int[][] rel = kind.layout() == Layout.CELLAR
                    ? new int[][] {{-2, 1}, {2, 1}, {-2, 4}, {2, 4}, {-2, 7}, {2, 7}, {0, 2}, {0, 8}, {-1, 5}, {1, 5}}
                    : new int[][] {{-3, 2}, {3, 2}, {-3, 8}, {3, 8}, {-2, 5}, {2, 5}, {-4, 1}, {4, 9}, {0, 1}, {0, 9}};
            for (int[] r : rel) {
                spots.add(b.at(r[0], d, r0 + r[1]));
            }
            plan.roomSpots.add(spots);
            // A room wakes when someone walks its corridor or stands in it.
            plan.roomZones.add(b.box(-ROOM_HALF, d, r0 - CORRIDOR, ROOM_HALF, d + ROOM_H - 1, r0 + ROOM_LEN - 1));
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = d; y <= d + 3; y++) {
                plan.arenaGate.add(b.at(x, y, a0 - 2));
            }
        }
        plan.arenaCenter = b.at(0, d, cz);
        plan.chest = b.at(0, d, cz + 4);
        plan.arena = b.box(-ARENA_R, d, cz - ARENA_R, ARENA_R, d + ARENA_H - 1, cz + ARENA_R);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0 + Math.PI / 8;   // between the four great pillars
            plan.waveSpots.add(b.at((int) Math.round(Math.cos(a) * 8), d, cz + (int) Math.round(Math.sin(a) * 8)));
        }
        plan.door = b.at(0, 0, -8);
        plan.inside.add(b.box(-1, -16, 0, 1, -3, 14));
        plan.inside.add(b.box(-1, d, 15, 1, d + 3, a0));
        for (int i = 0; i < rooms; i++) {
            int r0 = FIRST_ROOM + i * (ROOM_LEN + CORRIDOR);
            plan.inside.add(b.box(-ROOM_HALF, d, r0, ROOM_HALF, d + ROOM_H - 1, r0 + ROOM_LEN - 1));
        }
        plan.inside.add(plan.arena);
        plan.bounds = b.box(-8, d - 2, -9, 8, h + 8, cz + ARENA_R + 3).inflate(ARENA_R - 8 + 1, 0, 0);
        plan.blocks.putAll(b.out);
        return plan;
    }

    // ------------------------------------------------------------------ parts

    private static BlockPos gatehouse(B b, int h) {
        Layout layout = b.kind.layout();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                b.put(x, -1, z, b.part(Math.abs(x) + Math.abs(z) <= 2 ? Part.FLOOR_ALT : Part.FLOOR));
                boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
                for (int y = 0; y < h; y++) {
                    if (!edge) {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());
                    } else if (z == -5 && Math.abs(x) <= 1 && y <= 2) {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());   // the door
                    } else {
                        boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
                        b.put(x, y, z, b.part(corner ? Part.PILLAR : (y + x + z & 3) == 0 ? Part.WALL_ALT : Part.WALL));
                    }
                }
                b.put(x, h, z, b.part(edge ? Part.WALL : Part.CEILING));
            }
        }
        // Lintel over the door and a porch before it, banners beside it, lights inside.
        for (int x = -2; x <= 2; x++) {
            b.put(x, 3, -5, b.part(Part.PILLAR));
            b.put(x, 4, -6, b.slab(SlabType.BOTTOM));
        }
        for (int y = 0; y <= 3; y++) {
            b.put(-2, y, -6, b.part(Part.PILLAR));
            b.put(2, y, -6, b.part(Part.PILLAR));
        }
        // Corner towers a little higher, window slits in the sides and the back.
        for (int x : new int[] {-5, 5}) {
            for (int z : new int[] {-5, 5}) {
                b.put(x, h, z, b.part(Part.PILLAR));
                b.put(x, h + 1, z, b.part(Part.PILLAR));
                b.put(x, h + 2, z, b.slab(SlabType.BOTTOM));
            }
        }
        for (int t : new int[] {-2, 2}) {
            for (int y = 1; y <= Math.min(3, h - 2); y++) {
                b.put(-5, y, t, Blocks.IRON_BARS.defaultBlockState());
                b.put(5, y, t, Blocks.IRON_BARS.defaultBlockState());
                b.put(t, y, 5, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        b.put(-3, 0, -7, b.banner(8));   // facing whoever comes from the core
        b.put(3, 0, -7, b.banner(8));
        b.light(-3, h - 1, -3);
        b.light(3, h - 1, -3);
        b.light(-3, h - 1, 3);
        b.light(3, h - 1, 3);
        switch (layout) {
            case KEEP -> {
                // Battlements.
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        if ((Math.abs(x) == 5 || Math.abs(z) == 5) && (x + z & 1) == 0) {
                            b.put(x, h + 1, z, b.part(Part.WALL));
                        }
                    }
                }
            }
            case CRYPT -> {
                // A stepped roof with a spire.
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        b.put(x, h + 1, z, b.part(Part.SLAB));
                    }
                }
                b.put(0, h + 1, 0, b.part(Part.PILLAR));
                b.put(0, h + 2, 0, b.part(Part.PILLAR));
            }
            case FORGE -> {
                // A chimney at the back.
                for (int y = h + 1; y <= h + 4; y++) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = 2; z <= 4; z++) {
                            b.put(x, y, z, Math.abs(x) == 1 || z != 3 ? b.part(Part.WALL) : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            default -> {
            }
        }
        // The signal fire: its smoke shows the dungeon from afar.
        int fy = layout == Layout.FORGE ? h + 4 : layout == Layout.CRYPT ? h + 1 : h;
        int fz = 3;
        b.put(0, fy, fz, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(0, fy + 1, fz, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.SIGNAL_FIRE, true).setValue(BlockStateProperties.LIT, true));
        // A parapet round the stairwell.
        for (int z = -1; z <= 4; z++) {
            b.put(-2, 0, z, b.part(Part.SLAB));
            b.put(2, 0, z, b.part(Part.SLAB));
        }
        return b.at(0, fy + 1, fz);
    }

    private static void stairs(B b) {
        BlockState step = b.stairs(Direction.NORTH);
        for (int k = 0; k <= 15; k++) {
            int z = -1 + k;
            int y = -1 - k;
            for (int x = -1; x <= 1; x++) {
                b.put(x, y, z, step);
                b.put(x, y - 1, z, b.part(Part.WALL));
                for (int up = 1; up <= 4; up++) {
                    if (y + up <= -1 || k <= 5) {
                        b.put(x, y + up, z, Blocks.AIR.defaultBlockState());
                    }
                }
                if (y + 5 <= -2) {
                    b.put(x, y + 5, z, b.part(Part.CEILING));
                }
            }
            for (int up = 0; up <= 4; up++) {
                if (y + up <= -2) {
                    b.put(-2, y + up, z, b.part(Part.WALL));
                    b.put(2, y + up, z, b.part(Part.WALL));
                }
            }
            if (k >= 9 && k % 4 == 1) {
                b.light(0, y + 4, z);
            }
        }
        // The first light hangs under the gatehouse floor, where the stairs dive below it.
        b.light(0, -2, 4);
    }

    /** A corridor 3 wide and 4 high, open at both ends. */
    private static void tunnel(B b, int z0, int z1) {
        int d = DEPTH;
        for (int z = z0; z <= z1; z++) {
            for (int x = -2; x <= 2; x++) {
                b.put(x, d - 1, z, b.part(Math.abs(x) <= 1 && (z & 1) == 0 ? Part.FLOOR_ALT : Part.FLOOR));
                b.put(x, d + 4, z, b.part(Part.CEILING));
                for (int y = d; y <= d + 3; y++) {
                    b.put(x, y, z, Math.abs(x) == 2 ? b.part((y + z & 3) == 0 ? Part.WALL_ALT : Part.WALL)
                            : Blocks.AIR.defaultBlockState());
                }
            }
            if ((z & 3) == 0) {
                b.light(0, d + 3, z);
            }
        }
    }

    /** A guard room: 11 by 11, 6 high, with its walls, floor and ceiling. */
    private static void room(B b, int r0) {
        int d = DEPTH;
        for (int x = -ROOM_HALF - 1; x <= ROOM_HALF + 1; x++) {
            for (int z = r0 - 1; z <= r0 + ROOM_LEN; z++) {
                boolean edge = Math.abs(x) == ROOM_HALF + 1 || z == r0 - 1 || z == r0 + ROOM_LEN;
                b.put(x, d - 1, z, b.part((x + z & 1) == 0 && !edge ? Part.FLOOR_ALT : Part.FLOOR));
                b.put(x, d + ROOM_H, z, b.part(Part.CEILING));
                for (int y = d; y < d + ROOM_H; y++) {
                    if (edge) {
                        boolean pillar = Math.abs(x) == ROOM_HALF + 1 && (z - r0) % 5 == 0;
                        b.put(x, y, z, b.part(pillar ? Part.PILLAR : (y * 3 + z + x & 7) == 0 ? Part.WALL_ALT : Part.WALL));
                    } else {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        b.light(-3, d + ROOM_H - 1, r0 + 2);
        b.light(3, d + ROOM_H - 1, r0 + 2);
        b.light(-3, d + ROOM_H - 1, r0 + 8);
        b.light(3, d + ROOM_H - 1, r0 + 8);
        b.light(0, d + ROOM_H - 1, r0 + 5);
    }

    private static void roomDecor(B b, int r0) {
        int d = DEPTH;
        switch (b.kind.layout()) {
            case CRYPT -> {
                // Stone coffins along the walls, webs in the corners.
                for (int side : new int[] {-4, 4}) {
                    for (int z : new int[] {r0 + 2, r0 + 7}) {
                        b.put(side, d, z, b.part(Part.PILLAR));
                        b.put(side, d, z + 1, b.part(Part.PILLAR));
                        b.put(side, d + 1, z, b.part(Part.SLAB));
                        b.put(side, d + 1, z + 1, b.part(Part.SLAB));
                    }
                }
                for (int x : new int[] {-ROOM_HALF, ROOM_HALF}) {
                    for (int z : new int[] {r0, r0 + ROOM_LEN - 1}) {
                        b.put(x, d + ROOM_H - 1, z, b.part(Part.ACCENT));
                    }
                }
            }
            case KEEP -> {
                // A long table, barrels and the band's banners.
                for (int z = r0 + 3; z <= r0 + 7; z++) {
                    b.put(0, d, z, b.slab(SlabType.TOP));
                }
                for (int x : new int[] {-ROOM_HALF, ROOM_HALF}) {
                    b.put(x, d, r0 + 1, b.part(Part.DECOR));
                    b.put(x, d + 1, r0 + 1, b.part(Part.DECOR));
                    b.put(x, d, r0 + 9, b.part(Part.DECOR));
                    b.put(x, d, r0 + 5, b.banner(x < 0 ? 12 : 4));
                }
                b.put(-2, d + ROOM_H - 1, r0 + 5, b.part(Part.ACCENT));
                b.put(2, d + ROOM_H - 1, r0 + 5, b.part(Part.ACCENT));
            }
            case CELLAR -> {
                // Cells behind bars along both walls, chains from the ceiling.
                for (int side : new int[] {-3, 3}) {
                    for (int z = r0; z <= r0 + ROOM_LEN - 1; z++) {
                        if (z == r0 + 5) {
                            continue;
                        }
                        for (int y = d; y <= d + 2; y++) {
                            b.put(side, y, z, (z - r0) % 3 == 0 ? b.part(Part.PILLAR) : Blocks.IRON_BARS.defaultBlockState());
                        }
                    }
                    int far = side < 0 ? -ROOM_HALF : ROOM_HALF;
                    b.put(far, d, r0 + 2, b.part(Part.DECOR));
                    b.put(far, d, r0 + 8, b.part(Part.ACCENT));
                }
                for (int y = d + 3; y < d + ROOM_H; y++) {
                    b.put(0, y, r0 + 3, Blocks.IRON_CHAIN.defaultBlockState());
                    b.put(0, y, r0 + 7, Blocks.IRON_CHAIN.defaultBlockState());
                }
            }
            case FORGE -> {
                // Anvils, glowing seams and lava behind bars in the walls.
                b.put(-3, d, r0 + 3, b.part(Part.DECOR));
                b.put(3, d, r0 + 7, b.part(Part.DECOR));
                for (int side : new int[] {-1, 1}) {
                    int wall = side * (ROOM_HALF + 1);
                    for (int z : new int[] {r0 + 2, r0 + 8}) {
                        lavaNiche(b, wall, d + 1, z, side);
                    }
                    for (int z = r0; z < r0 + ROOM_LEN; z += 2) {
                        b.put(wall, d + 3, z, b.part(Part.ACCENT));
                    }
                }
            }
        }
    }

    /** Lava in the wall behind iron bars: light and heat, out of reach. */
    private static void lavaNiche(B b, int wallX, int y, int z, int side) {
        b.put(wallX + side, y, z, b.part(Part.WALL));
        b.put(wallX + side, y + 1, z, b.part(Part.WALL));
        b.put(wallX, y, z, Blocks.LAVA.defaultBlockState());
        b.put(wallX - side, y, z, Blocks.IRON_BARS.defaultBlockState());
    }

    /** The round arena at the end. */
    private static void arena(B b, int cz) {
        int d = DEPTH;
        double inner = (ARENA_R + 0.5) * (ARENA_R + 0.5);
        double outer = (ARENA_R + 1.5) * (ARENA_R + 1.5);
        for (int x = -ARENA_R - 2; x <= ARENA_R + 2; x++) {
            for (int z = cz - ARENA_R - 2; z <= cz + ARENA_R + 2; z++) {
                double r2 = x * x + (z - cz) * (double) (z - cz);
                if (r2 > outer) {
                    continue;
                }
                double r = Math.sqrt(r2);
                Part floor = r < 1.6 ? Part.PILLAR : r >= 5 && r < 6 ? Part.FLOOR_ALT : Part.FLOOR;
                b.put(x, d - 1, z, b.part(floor));
                b.put(x, d + ARENA_H, z, b.part(Part.CEILING));
                for (int y = d; y < d + ARENA_H; y++) {
                    if (r2 <= inner) {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());
                    } else {
                        boolean rib = Math.abs(x) <= 0 || Math.abs(z - cz) <= 0 || Math.abs(Math.abs(x) - Math.abs(z - cz)) <= 0;
                        b.put(x, y, z, b.part(rib ? Part.PILLAR : (y + x * 5 + z & 7) == 0 ? Part.WALL_ALT : Part.WALL));
                    }
                }
            }
        }
        // Four great pillars and the lights round the hall.
        for (int sx : new int[] {-6, 6}) {
            for (int sz : new int[] {-6, 6}) {
                for (int y = d; y < d + ARENA_H; y++) {
                    b.put(sx, y, cz + sz, b.part(Part.PILLAR));
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0 + Math.PI / 8;
            b.light((int) Math.round(Math.cos(a) * 8), d + ARENA_H - 1, cz + (int) Math.round(Math.sin(a) * 8));
        }
        b.light(-3, d + ARENA_H - 1, cz);
        b.light(3, d + ARENA_H - 1, cz);
    }

    private static void arenaDecor(B b, int cz) {
        int d = DEPTH;
        switch (b.kind.layout()) {
            case CRYPT -> {
                // A low dais in the middle, coffins on the walls.
                for (int x = -2; x <= 2; x++) {
                    for (int z = cz - 2; z <= cz + 2; z++) {
                        if (x * x + (z - cz) * (z - cz) <= 5) {
                            b.put(x, d - 1, z, b.part(Part.PILLAR));
                        }
                    }
                }
                for (int x : new int[] {-8, 8}) {
                    b.put(x, d, cz, b.part(Part.PILLAR));
                    b.put(x, d + 1, cz, b.part(Part.SLAB));
                }
                b.put(-6, d + ARENA_H - 1, cz + 6, b.part(Part.ACCENT));
                b.put(6, d + ARENA_H - 1, cz - 6, b.part(Part.ACCENT));
            }
            case KEEP -> {
                // The warlord's chair at the far end, banners beside it.
                b.put(0, d, cz + 9, b.stairs(Direction.SOUTH));
                b.put(0, d, cz + 10, b.part(Part.PILLAR));
                b.put(0, d + 1, cz + 10, b.part(Part.PILLAR));
                b.put(-2, d, cz + 9, b.banner(8));
                b.put(2, d, cz + 9, b.banner(8));
                for (int x : new int[] {-9, 9}) {
                    b.put(x, d, cz - 2, b.part(Part.DECOR));
                    b.put(x, d, cz + 2, b.part(Part.DECOR));
                }
            }
            case CELLAR -> {
                // Chains hang between the pillars.
                for (int sx : new int[] {-6, 6}) {
                    for (int y = d + 5; y < d + ARENA_H; y++) {
                        b.put(sx / 2, y, cz, Blocks.IRON_CHAIN.defaultBlockState());
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2.0 * i / 6.0;
                    b.put((int) Math.round(Math.cos(a) * 9), d, cz + (int) Math.round(Math.sin(a) * 9), b.part(Part.DECOR));
                }
            }
            case FORGE -> {
                // Lava in the walls on both sides.
                for (int z : new int[] {cz - 3, cz + 3}) {
                    lavaNiche(b, -(ARENA_R + 1), d + 1, z, -1);
                    lavaNiche(b, ARENA_R + 1, d + 1, z, 1);
                }
                b.put(-8, d, cz + 4, b.part(Part.DECOR));
                b.put(8, d, cz - 4, b.part(Part.DECOR));
            }
        }
    }

    // ------------------------------------------------------------------ builder

    /** Collects blocks in layout space, turned into the world and into the kind's palette. */
    private static final class B {
        final BlockPos origin;
        final int facing;
        final ExpeditionKind kind;
        final Rotation rotation;
        final Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        final Map<Part, BlockState> states = new java.util.EnumMap<>(Part.class);

        B(BlockPos origin, int facing, ExpeditionKind kind, long seed) {
            this.origin = origin;
            this.facing = facing;
            this.kind = kind;
            this.rotation = CampLayout.rotation(facing);
            for (Part p : Part.values()) {
                states.put(p, block(kind.block(p)));
            }
        }

        BlockPos at(int x, int y, int z) {
            return CampLayout.world(origin, facing, x, y, z);
        }

        AABB box(int x0, int y0, int z0, int x1, int y1, int z1) {
            BlockPos a = at(x0, y0, z0);
            BlockPos c = at(x1, y1, z1);
            return new AABB(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()),
                    Math.max(a.getX(), c.getX()) + 1, Math.max(a.getY(), c.getY()) + 1, Math.max(a.getZ(), c.getZ()) + 1);
        }

        void put(int x, int y, int z, BlockState state) {
            out.put(at(x, y, z), state.rotate(rotation));
        }

        BlockState part(Part part) {
            return states.get(part);
        }

        BlockState slab(SlabType type) {
            BlockState s = states.get(Part.SLAB);
            return s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, type) : s;
        }

        BlockState stairs(Direction up) {
            BlockState s = states.get(Part.STAIRS);
            if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, up);
            }
            if (s.hasProperty(BlockStateProperties.HALF)) {
                s = s.setValue(BlockStateProperties.HALF, Half.BOTTOM);
            }
            return s;
        }

        BlockState banner(int rotation16) {
            BlockState s = states.get(Part.BANNER);
            return s.hasProperty(BlockStateProperties.ROTATION_16) ? s.setValue(BlockStateProperties.ROTATION_16, rotation16) : s;
        }

        /** A light at the top of a space: hanging if it can hang, else set into the ceiling above. */
        void light(int x, int topY, int z) {
            BlockState s = states.get(Part.LIGHT);
            if (s.hasProperty(BlockStateProperties.HANGING)) {
                put(x, topY, z, s.setValue(BlockStateProperties.HANGING, true));
            } else {
                put(x, topY + 1, z, s);
            }
        }

        static BlockState block(String id) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
            BlockState state = (block == null || block == Blocks.AIR ? Blocks.STONE : block).defaultBlockState();
            if (state.hasProperty(BlockStateProperties.LIT)) {
                state = state.setValue(BlockStateProperties.LIT, true);
            }
            return state;
        }
    }
}
