package com.mki.nightsiege.game;

import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.ExpeditionKind.Layout;
import com.mki.nightsiege.core.ExpeditionKind.Part;
import com.mki.nightsiege.core.ExpeditionKind.Weighted;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The blocks of a Night Siege dungeon, content update (Simon, 28/29 October 2026: halls 30 by 30,
 * a much bigger arena, a much bigger palette and more structures; floor plans on the design
 * canvas "Dungeon-Grundrisse 30×30").
 *
 * <p>One way only: a gatehouse on the surface, a stair down to the halls at {@link #HALL_Y}, the
 * guard halls one after the other (offset left and right, joined by corridors with a bend), each
 * closed by an iron gate that opens when its guards are dead, then the great stair down to the
 * round arena at {@link #ARENA_Y}.</p>
 *
 * <p>Every hall: galleries on both side walls, two tower niches beside the entrance, half walls,
 * four pillars, and one theme module of its building (four per building, none twice in a
 * dungeon). The arena: a ring gallery with three stairs, tower platforms, cover islands, the
 * champion's pit with the throne and four monster gates.</p>
 *
 * <p>The palette rules of the concept: variants in patches (smooth noise), base, wall and upper
 * wall, trims carry the form, floors with a border and bands, one signature accent, two light
 * colours, danger only behind bars or railings, no falling blocks overhead, and the same seed
 * gives the same picture.</p>
 *
 * <p>Layout space: x to the side, z away from the core (the gate faces the core), y up from the
 * gatehouse floor. Everything follows from origin, facing, kind, room count and seed.</p>
 */
public final class ExpeditionLayout {

    /** Walk level of the guard halls, below the gatehouse floor. */
    static final int HALL_Y = -20;
    /** Walk level of the arena. */
    static final int ARENA_Y = -24;
    /** Halls reach x -15..15 inside (31 wide, so the hall is symmetric). */
    static final int HALF = 15;
    /** Halls reach z 0..29 inside. */
    static final int LEN = 30;
    /** Air y 0..11 in a hall, the ceiling at 12. */
    static final int HALL_H = 12;
    /** A corridor between two halls: 11 long. */
    static final int CORR = 11;
    /** The first corridor starts here (after the stair). */
    static final int FIRST = 20;
    static final int PITCH = CORR + LEN + 2;
    /** The arena's floor radius (diameter 45). */
    static final int R = 22;
    /** The halls lie offset: left, right (Simon: "Knicke"). */
    static final int[] OFFSET = {0, -14, 14, -14, 14};

    /** Everything about one built dungeon, in world coordinates. */
    public static final class Plan {
        public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        /** Ground above the vaults: set only where nothing solid is (so no vault lies open to the sky). */
        public final Map<BlockPos, BlockState> fill = new LinkedHashMap<>();
        public final List<BlockPos> roomCenters = new ArrayList<>();
        public final List<List<BlockPos>> roomSpots = new ArrayList<>();
        public final List<List<BlockPos>> roomGates = new ArrayList<>();
        /** The theme module of each hall (for the tests and the effects). */
        public final List<String> roomModules = new ArrayList<>();
        /** Where a room wakes: its corridor and the room itself. */
        public final List<AABB> roomZones = new ArrayList<>();
        /** The halls themselves (without their corridor). */
        public final List<AABB> roomHalls = new ArrayList<>();
        public final List<BlockPos> arenaGate = new ArrayList<>();
        /** The gate before the first hall: it goes up when every fighter stands before it (Simon 29.10.). */
        public final List<BlockPos> frontGate = new ArrayList<>();
        /** The bars of the four monster gates (they open with the first wave). */
        public final List<BlockPos> waveGates = new ArrayList<>();
        public final List<BlockPos> waveSpots = new ArrayList<>();
        /** The champion's pit (he rises from here). */
        public BlockPos arenaCenter;
        public AABB arena;
        /** In front of the gatehouse door (where the fallen are put). */
        public BlockPos door;
        /** The signal fire on the roof (it goes out when the dungeon is won). */
        public BlockPos fire;
        /** The seal stone in the gatehouse: a right click starts the ready round. */
        public BlockPos seal;
        /** The gate at the head of the stair (closed until all are ready). */
        public final List<BlockPos> stairGate = new ArrayList<>();
        /** Where the fallen wait (the gatehouse's benches). */
        public final List<BlockPos> waiting = new ArrayList<>();
        /** The way down: from here on one is inside. */
        public final List<AABB> inside = new ArrayList<>();
        /** Everything the dungeon takes (no breaking before it is gone). */
        public AABB bounds;
        /** Floor cells one walks on (for the light check of the tests): world positions of the feet. */
        public final List<BlockPos> walkways = new ArrayList<>();
    }

    private ExpeditionLayout() {
    }

    static int gatehouseHeight(Layout layout) {
        return switch (layout) {
            case KEEP -> 8;
            case CELLAR -> 5;
            default -> 6;
        };
    }

    static int corridorStart(int i) {
        return FIRST + i * PITCH;
    }

    /** The first z inside hall i (its front wall lies one before). */
    static int hallStart(int i) {
        return corridorStart(i) + CORR + 1;
    }

    /** The arena's middle (z). */
    static int arenaCenter(int rooms) {
        return corridorStart(rooms) + 38;
    }

    /** How far the dungeon reaches away from the core. */
    static int length(int rooms) {
        return arenaCenter(rooms) + R + 6;
    }

    static Plan plan(BlockPos origin, int facing, ExpeditionKind kind, int rooms, long seed) {
        B b = new B(origin, facing, kind, seed);
        Plan plan = new Plan();
        int h = gatehouseHeight(kind.layout());
        plan.fire = gatehouse(b, h);
        stairs(b);
        List<Module> modules = modules(kind.layout(), seed, rooms);
        for (int i = 0; i < rooms; i++) {
            hall(b, plan, i, modules.get(i));
        }
        for (int i = 0; i <= rooms; i++) {
            corridor(b, plan, i, rooms);
        }
        int cz = arenaCenter(rooms);
        arena(b, plan, cz);
        b.enclose();
        b.hiddenLight();
        b.support();
        // The gates: one behind every hall (closed), the arena's (open until the fight).
        for (int i = 0; i < rooms; i++) {
            int ox = OFFSET[i];
            int z0 = hallStart(i);
            List<BlockPos> gate = new ArrayList<>();
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 4; y++) {
                    b.put(ox + x, HALL_Y + y, z0 + LEN, Blocks.IRON_BARS.defaultBlockState());
                    gate.add(b.at(ox + x, HALL_Y + y, z0 + LEN));
                }
            }
            plan.roomGates.add(gate);
            plan.roomCenters.add(b.at(ox, HALL_Y, z0 + LEN / 2));
            int c = corridorStart(i);
            int prev = i == 0 ? 0 : OFFSET[i - 1];
            int lo = Math.min(Math.min(prev, ox) - 3, ox - HALF);
            int hi = Math.max(Math.max(prev, ox) + 3, ox + HALF);
            plan.roomZones.add(b.box(lo, HALL_Y, c, hi, HALL_Y + HALL_H - 1, z0 + LEN - 1));
            plan.roomHalls.add(b.box(ox - HALF, HALL_Y, z0, ox + HALF, HALL_Y + HALL_H - 1, z0 + LEN - 1));
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 4; y++) {
                plan.arenaGate.add(b.at(x, ARENA_Y + y, cz - R - 1));
            }
        }
        // The gate in the front wall of the first hall (Simon 29.10.: "unten vorm ersten Raum sollten
        // auch Ketten sein, die erst aufgehen, wenn alle davor stehen"): bars like the other gates.
        if (rooms > 0) {
            int front = hallStart(0) - 1;
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 4; y++) {
                    b.put(OFFSET[0] + x, HALL_Y + y, front, Blocks.IRON_BARS.defaultBlockState());
                    plan.frontGate.add(b.at(OFFSET[0] + x, HALL_Y + y, front));
                }
            }
        }
        plan.arenaCenter = b.at(0, ARENA_Y - 3, cz);
        plan.arena = b.box(-R, ARENA_Y - 3, cz - R, R, ARENA_Y + 15, cz + R);
        plan.door = b.at(0, 0, -8);
        plan.seal = b.at(0, 0, -3);
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                plan.stairGate.add(b.at(x, y, -1));
            }
        }
        for (int z = 0; z <= 3; z++) {
            plan.waiting.add(b.at(-4, 0, z));
            plan.waiting.add(b.at(4, 0, z));
        }
        // Inside: below the gatehouse floor on the stair, the corridors, halls, arena and its gates.
        plan.inside.add(b.box(-2, HALL_Y - 2, 0, 2, -3, FIRST - 1));
        for (int i = 0; i <= rooms; i++) {
            int c = corridorStart(i);
            int prev = i == 0 ? 0 : OFFSET[i - 1];
            int ox = i < rooms ? OFFSET[i] : 0;
            plan.inside.add(b.box(Math.min(prev, ox) - 3, ARENA_Y - 1, c, Math.max(prev, ox) + 3, HALL_Y + 5, c + CORR - 1
                    + (i == rooms ? 4 : 0)));
        }
        for (int i = 0; i < rooms; i++) {
            plan.inside.add(plan.roomHalls.get(i));
        }
        plan.inside.add(b.box(-R - 6, ARENA_Y - 4, cz - R - 2, R + 6, ARENA_Y + 16, cz + R + 6));
        int reach = Math.max(HALF + 16, R + 7);
        plan.bounds = b.box(-reach, ARENA_Y - 6, -11, reach, h + 8, cz + R + 8);
        b.finish(plan);
        return plan;
    }

    // ------------------------------------------------------------------ the modules

    /** One theme of a hall. */
    private interface Module {
        String id();

        void build(H h);
    }

    private record Mod(String id, java.util.function.Consumer<H> body) implements Module {
        @Override
        public void build(H h) {
            body.accept(h);
        }
    }

    /** Four modules per building, in an order of the seed: no hall twice in a dungeon. */
    private static List<Module> modules(Layout layout, long seed, int rooms) {
        List<Module> all = switch (layout) {
            case CRYPT -> List.of(new Mod("sarkophage", ExpeditionLayout::sarcophagi),
                    new Mod("beinhaus", ExpeditionLayout::ossuary),
                    new Mod("kapelle", ExpeditionLayout::chapel),
                    new Mod("urnen", ExpeditionLayout::urns));
            case KEEP -> List.of(new Mod("uebungshof", ExpeditionLayout::yard),
                    new Mod("waffenkammer", ExpeditionLayout::armoury),
                    new Mod("kriegsrat", ExpeditionLayout::warRoom),
                    new Mod("proviant", ExpeditionLayout::stores));
            case CELLAR -> List.of(new Mod("zellen", ExpeditionLayout::cells),
                    new Mod("folterkammer", ExpeditionLayout::torture),
                    new Mod("brunnen", ExpeditionLayout::well),
                    new Mod("wurzeln", ExpeditionLayout::roots));
            case FORGE -> List.of(new Mod("esse", ExpeditionLayout::forge),
                    new Mod("oefen", ExpeditionLayout::ovens),
                    new Mod("giessbecken", ExpeditionLayout::castingPool),
                    new Mod("kohlelager", ExpeditionLayout::coalStore));
        };
        List<Module> order = new ArrayList<>(all);
        // The first hall always gets the building's own hall (the one Simon saw on the canvas).
        Module first = order.remove(0);
        java.util.Collections.shuffle(order, new Random(seed * 31 + 7));
        order.add(0, first);
        while (order.size() < rooms) {
            order.add(order.get(order.size() % all.size()));
        }
        return order;
    }

    // ------------------------------------------------------------------ the gatehouse and the stair

    private static BlockPos gatehouse(B b, int h) {
        Layout layout = b.kind.layout();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
                boolean border = Math.abs(x) == 4 || Math.abs(z) == 4;
                b.put(x, -1, z, edge || border ? b.first(Part.TRIM) : b.p(Part.FLOOR, x, -1, z));
                for (int y = 0; y < h; y++) {
                    if (!edge) {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());
                    } else if (z == -5 && Math.abs(x) <= 1 && y <= 2) {
                        b.put(x, y, z, Blocks.AIR.defaultBlockState());   // the door
                    } else {
                        boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
                        b.put(x, y, z, corner ? b.p(Part.PILLAR, x, y, z) : b.wall(y, x + z, x, y, z));
                    }
                }
                b.put(x, h, z, edge ? b.p(Part.WALL_TOP, x, h, z) : b.p(Part.CEILING, x, h, z));
            }
        }
        // Lintel over the door and a porch before it, banners beside it, lights inside.
        for (int x = -2; x <= 2; x++) {
            b.put(x, 3, -5, b.first(Part.FRAME));
            b.put(x, 4, -6, b.slab(SlabType.BOTTOM));
        }
        for (int y = 0; y <= 3; y++) {
            b.put(-2, y, -6, b.p(Part.PILLAR, -2, y, -6));
            b.put(2, y, -6, b.p(Part.PILLAR, 2, y, -6));
        }
        for (int x : new int[] {-5, 5}) {
            for (int z : new int[] {-5, 5}) {
                b.put(x, h, z, b.p(Part.PILLAR, x, h, z));
                b.put(x, h + 1, z, b.p(Part.PILLAR, x, h + 1, z));
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
        b.hang(-3, h - 1, -3, h);
        b.hang(3, h - 1, -3, h);
        b.hang(-3, h - 1, 3, h);
        b.hang(3, h - 1, 3, h);
        switch (layout) {
            case KEEP -> {
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        if ((Math.abs(x) == 5 || Math.abs(z) == 5) && (x + z & 1) == 0) {
                            b.put(x, h + 1, z, b.p(Part.WALL, x, h + 1, z));
                        }
                    }
                }
            }
            case CRYPT -> {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        b.put(x, h + 1, z, b.slab(SlabType.BOTTOM));
                    }
                }
                b.put(0, h + 1, 0, b.p(Part.PILLAR, 0, h + 1, 0));
                b.put(0, h + 2, 0, b.p(Part.PILLAR, 0, h + 2, 0));
            }
            case FORGE -> {
                for (int y = h + 1; y <= h + 4; y++) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = 2; z <= 4; z++) {
                            b.put(x, y, z, Math.abs(x) == 1 || z != 3 ? b.p(Part.WALL, x, y, z) : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            default -> {
            }
        }
        int fy = layout == Layout.FORGE ? h + 4 : layout == Layout.CRYPT ? h + 1 : h;
        int fz = 3;
        b.put(0, fy, fz, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(0, fy + 1, fz, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.SIGNAL_FIRE, true).setValue(BlockStateProperties.LIT, true));
        // A parapet round the stairwell, the gate at its head (closed until all are ready).
        for (int z = -1; z <= 4; z++) {
            b.put(-3, 0, z, b.rail());
            b.put(3, 0, z, b.rail());
        }
        for (int x = -2; x <= 2; x++) {
            b.put(x, 0, 4, b.rail());
        }
        // The seal stone before the stair; the waiting room of the fallen: benches and two fires.
        b.put(0, 0, -3, Blocks.LODESTONE.defaultBlockState());
        b.put(0, -1, -3, b.first(Part.FRAME));
        for (int z = 0; z <= 3; z++) {
            b.put(-4, 0, z, b.stairs(Direction.WEST));
            b.put(4, 0, z, b.stairs(Direction.EAST));
        }
        for (int x : new int[] {-4, 4}) {
            b.put(x, 0, -4, b.first(Part.FRAME));
            b.lamp(x, 1, -4);
        }
        return b.at(0, fy + 1, fz);
    }

    /** The stair from the gatehouse down to the halls: 5 wide, one step a block, a gate at its head. */
    private static void stairs(B b) {
        BlockState step = b.stairs(Direction.NORTH);
        int steps = -HALL_Y;
        for (int k = 0; k <= steps; k++) {
            int z = -1 + k;
            int y = -1 - k;
            for (int x = -2; x <= 2; x++) {
                b.put(x, y, z, step);
                b.put(x, y - 1, z, b.p(Part.FLOOR, x, y - 1, z));
                for (int up = 1; up <= 4; up++) {
                    if (y + up <= -1 || k <= 5) {
                        b.put(x, y + up, z, Blocks.AIR.defaultBlockState());
                    }
                }
                if (y + 5 <= -2) {
                    b.put(x, y + 5, z, b.p(Part.CEILING, x, y + 5, z));
                }
            }
            for (int up = -1; up <= 5; up++) {
                if (y + up <= -2) {
                    b.put(-3, y + up, z, b.wall(up + 1, z, -3, y + up, z));
                    b.put(3, y + up, z, b.wall(up + 1, z, 3, y + up, z));
                }
            }
            if (k >= 7 && k % 4 == 3) {
                b.hang(0, y + 4, z, y + 5);
            }
        }
        b.hang(0, -2, 4, -1);
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                b.put(x, y, -1, Blocks.IRON_BARS.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ corridors

    /** The corridor before hall i (i == rooms: the one to the arena, with the great stair). */
    private static void corridor(B b, Plan plan, int i, int rooms) {
        int c = corridorStart(i);
        int prev = i == 0 ? 0 : OFFSET[i - 1];
        int ox = i < rooms ? OFFSET[i] : 0;
        int y = HALL_Y;
        int bendTo = i < rooms ? c + 6 : c + 6;
        // The bend: as wide as the step from the last hall to the next.
        space(b, Math.min(prev, ox) - 3, c, Math.max(prev, ox) + 3, bendTo, y, 6, true);
        if (i < rooms) {
            space(b, ox - 3, bendTo, ox + 3, c + CORR - 1, y, 6, true);
            // Side niches with a light in the straight part.
            for (int side : new int[] {-1, 1}) {
                int nx = ox + side * 4;
                b.put(nx, y, c + 8, Blocks.AIR.defaultBlockState());
                b.put(nx, y + 1, c + 8, Blocks.AIR.defaultBlockState());
                b.put(nx, y - 1, c + 8, b.first(Part.TRIM));
                b.lamp(nx, y, c + 8);
            }
        } else {
            // The great stair down to the arena: four steps, then a landing before the gate.
            for (int k = 1; k <= 4; k++) {
                int z = c + 6 + k;
                for (int x = -3; x <= 3; x++) {
                    b.put(x, y - k - 1, z, b.stairs(Direction.NORTH));
                    b.put(x, y - k - 2, z, b.p(Part.FLOOR, x, y - k - 2, z));
                    for (int up = 0; up <= 5 + k; up++) {
                        b.put(x, y - k + up, z, Blocks.AIR.defaultBlockState());
                    }
                    b.put(x, y + 6, z, b.p(Part.CEILING, x, y + 6, z));
                }
            }
            space(b, -3, c + 11, 3, c + 14, ARENA_Y, 10, true);
            for (int x = -3; x <= 3; x++) {
                b.put(x, ARENA_Y + 10, c + 11, b.p(Part.CEILING, x, ARENA_Y + 10, c + 11));
            }
            b.hang(0, ARENA_Y + 7, c + 13, ARENA_Y + 10);
        }
        for (int z = c + 2; z <= c + CORR - 1; z += 4) {
            b.hang(z >= bendTo ? ox : (prev + ox) / 2, y + 4, z, y + 6);
        }
    }

    /** Air on a floor with a ceiling (the walls come from {@link B#enclose()}). */
    private static void space(B b, int x0, int z0, int x1, int z1, int y, int height, boolean runner) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1;
                b.put(x, y - 1, z, border ? b.first(Part.TRIM) : b.p(Part.FLOOR, x, y - 1, z));
                for (int up = 0; up < height; up++) {
                    b.put(x, y + up, z, Blocks.AIR.defaultBlockState());
                }
                b.put(x, y + height, z, (z & 3) == 0 ? b.first(Part.BEAM) : b.p(Part.CEILING, x, y + height, z));
                b.walk(x, y, z);
            }
        }
    }

    // ------------------------------------------------------------------ the hall

    /** Hall coordinates: x -15..15, y 0 = where one walks, z 0..29 from the entrance. */
    static final class H {
        final B b;
        final int ox;
        final int oy;
        final int oz;
        final List<int[]> spots = new ArrayList<>();
        final List<int[]> centerSpots = new ArrayList<>();

        H(B b, int ox, int oy, int oz) {
            this.b = b;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        void put(int x, int y, int z, BlockState s) {
            b.put(ox + x, oy + y, oz + z, s);
        }

        void put(int x, int y, int z, Part part) {
            b.put(ox + x, oy + y, oz + z, b.p(part, ox + x, oy + y, oz + z));
        }

        BlockState get(int x, int y, int z) {
            return b.get(ox + x, oy + y, oz + z);
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, Part part) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        put(x, y, z, part);
                    }
                }
            }
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        put(x, y, z, s);
                    }
                }
            }
        }

        /** Both sides: the same at x and -x. */
        void sym(int x, int y, int z, Part part) {
            put(x, y, z, part);
            put(-x, y, z, part);
        }

        void sym(int x, int y, int z, BlockState s) {
            put(x, y, z, s);
            put(-x, y, z, s);
        }

        void hang(int x, int y, int z) {
            b.hang(ox + x, oy + y, oz + z, oy + HALL_H);
        }

        void small(int x, int y, int z) {
            b.small(ox + x, oy + y, oz + z, false);
        }

        void throne(int x, int y, int z) {
            b.small(ox + x, oy + y, oz + z, true);
        }

        void lamp(int x, int y, int z) {
            b.lamp(ox + x, oy + y, oz + z);
        }

        /** The middle path from the entrance (a carpet lies on the floor, other runners are the floor). */
        void runner(int x0, int x1, int z0, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    b.runner(ox + x, oy, oz + z);
                }
            }
        }

        void prop(int x, int y, int z, int index) {
            b.prop(ox + x, oy + y, oz + z, index);
        }

        void center(int x, int z) {
            centerSpots.add(new int[] {x, 0, z});
        }
    }

    private static void hall(B b, Plan plan, int i, Module module) {
        int ox = OFFSET[i];
        int z0 = hallStart(i);
        int y0 = HALL_Y;
        H h = new H(b, ox, y0, z0);
        Layout layout = b.kind.layout();
        // The shell: floor, walls (base, wall, upper wall, trims), ceiling.
        for (int x = -HALF - 1; x <= HALF + 1; x++) {
            for (int z = -1; z <= LEN; z++) {
                boolean side = Math.abs(x) == HALF + 1;
                boolean edge = side || z == -1 || z == LEN;
                boolean border = Math.abs(x) == HALF || z == 0 || z == LEN - 1 || z % 6 == 4;
                h.put(x, -1, z, edge ? b.first(Part.BASE) : border ? b.first(Part.TRIM) : b.p(Part.FLOOR, ox + x, y0 - 1, z0 + z));
                h.put(x, HALL_H, z, Part.CEILING);
                for (int y = 0; y < HALL_H; y++) {
                    if (edge) {
                        h.put(x, y, z, b.wall(y, side ? z : x, ox + x, y0 + y, z0 + z));
                    } else {
                        h.put(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
                if (!edge) {
                    b.walk(ox + x, y0, z0 + z);
                }
            }
        }
        // Beams under the ceiling (the cellar has none: its chains and roots hang there).
        if (layout != Layout.CELLAR) {
            for (int z = 4; z < LEN; z += 6) {
                for (int x = -HALF; x <= HALF; x++) {
                    h.put(x, HALL_H - 1, z, b.first(Part.BEAM));
                }
            }
        }
        // Gates 5 wide, 5 high in the front wall (open) and the back wall (the room's gate), framed.
        for (int z : new int[] {-1, LEN}) {
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 4; y++) {
                    h.put(x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
            for (int y = 0; y <= 5; y++) {
                h.sym(3, y, z, b.first(Part.FRAME));
            }
            for (int x = -3; x <= 3; x++) {
                h.put(x, 5, z, b.first(Part.FRAME));
            }
        }
        wallDecor(h, layout);
        // Tower niches for the players, +2, left and right of the entrance, with two steps.
        for (int x = 12; x <= 14; x++) {
            for (int z = 1; z <= 3; z++) {
                h.sym(x, 0, z, b.first(Part.GALLERY));
                h.sym(x, 1, z, b.first(Part.GALLERY));
            }
        }
        h.sym(13, 0, 4, b.first(Part.GALLERY));
        h.sym(13, 1, 4, b.stairs(Direction.NORTH));
        h.sym(13, 0, 5, b.stairs(Direction.NORTH));
        for (int z = 1; z <= 3; z++) {
            h.sym(12, 2, z, b.rail());
        }
        h.sym(12, 2, 4, b.rail());
        h.sym(14, 2, 4, b.rail());
        h.sym(14, 0, 4, b.first(Part.GALLERY));   // the outer rail stands on a post (it hung in the air, 29.10.)
        h.sym(14, 1, 4, b.first(Part.GALLERY));
        // Galleries, +5, along both side walls; stairs up at the front, down along the back wall.
        for (int z = 10; z <= 27; z++) {
            for (int x = 11; x <= 15; x++) {
                h.sym(x, 4, z, b.first(Part.GALLERY));
            }
            h.sym(11, 5, z, b.rail());
            if (z % 4 == 0) {
                for (int y = 0; y <= 3; y++) {
                    h.sym(11, y, z, b.first(Part.FRAME));
                }
            }
        }
        for (int s = 0; s <= 4; s++) {
            int z = 5 + s;
            for (int x = 14; x <= 15; x++) {
                for (int y = 0; y < s; y++) {
                    h.sym(x, y, z, b.first(Part.GALLERY));
                }
                h.sym(x, s, z, b.stairs(Direction.SOUTH));
            }
        }
        for (int z = 28; z <= 29; z++) {
            for (int x = 12; x <= 15; x++) {
                h.sym(x, 4, z, b.first(Part.GALLERY));
            }
            for (int j = 0; j <= 4; j++) {
                int x = 11 - j;
                int top = 4 - j;
                for (int y = 0; y < top; y++) {
                    h.sym(x, y, z, b.first(Part.GALLERY));
                }
                if (j == 0) {
                    h.sym(x, top, z, b.first(Part.GALLERY));
                } else {
                    h.put(-x, top, z, b.stairs(Direction.WEST));
                    h.put(x, top, z, b.stairs(Direction.EAST));
                }
            }
        }
        // Four pillars 2 by 2 with a capital.
        for (int px : new int[] {7, 8}) {
            for (int pz : new int[] {9, 10, 21, 22}) {
                for (int y = 0; y < HALL_H - 1; y++) {
                    h.sym(px, y, pz, Part.PILLAR);
                }
                h.sym(px, HALL_H - 1, pz, b.first(Part.TRIM));
                h.sym(px, 0, pz, b.first(Part.TRIM));
            }
        }
        // Half walls by the entrance: the first cover for the ranged classes.
        for (int x = 4; x <= 7; x++) {
            h.sym(x, 0, 6, Part.COVER);
            h.sym(x, 1, 6, b.slab(SlabType.BOTTOM));
        }
        // Lights: lanterns on chains over the floor, under the galleries, small ones by the walls.
        for (int z : new int[] {3, 12, 19, 26}) {
            h.hang(-9, b.dim ? 5 : 7, z);
            h.hang(9, b.dim ? 5 : 7, z);
        }
        h.hang(0, b.dim ? 6 : 8, 9);
        h.hang(0, b.dim ? 6 : 8, 21);
        for (int z : new int[] {12, 17, 22, 26}) {
            h.sym(13, 3, z, b.hangUnder());
        }
        h.hang(-13, 5, 2);
        h.hang(13, 5, 2);
        // The theme module, then the guard spots: corners, galleries, the middle.
        module.build(h);
        plan.roomModules.add(module.id());
        List<int[]> want = new ArrayList<>();
        int[][] groups = {
                {-11, 0, 24}, {-9, 0, 25}, {-10, 0, 23}, {-12, 0, 25},
                {12, 5, 14}, {13, 5, 18}, {12, 5, 22}, {13, 5, 25},
                {11, 0, 24}, {9, 0, 25}, {10, 0, 23}, {12, 0, 25},
                {-12, 5, 14}, {-13, 5, 18}, {-12, 5, 22}, {-13, 5, 25},
        };
        List<int[]> middle = new ArrayList<>(h.centerSpots);
        if (middle.isEmpty()) {
            middle.add(new int[] {-3, 0, 15});
            middle.add(new int[] {3, 0, 15});
            middle.add(new int[] {0, 0, 14});
            middle.add(new int[] {0, 0, 17});
        }
        // Interleaved, so a small group still stands in three or four places.
        for (int k = 0; k < 4; k++) {
            want.add(groups[k]);
            want.add(groups[4 + k]);
            want.add(middle.get(k % middle.size()));
            want.add(groups[8 + k]);
            want.add(groups[12 + k]);
        }
        List<BlockPos> spots = new ArrayList<>();
        for (int[] w : want) {
            BlockPos at = b.freeNear(ox + w[0], y0 + w[1], z0 + w[2]);
            if (at != null) {
                spots.add(at);
            }
        }
        if (spots.isEmpty()) {
            spots.add(b.at(ox, y0, z0 + LEN / 2));
        }
        plan.roomSpots.add(spots);
    }

    /** Along the side walls every 3 blocks, by building. */
    private static void wallDecor(H h, Layout layout) {
        B b = h.b;
        for (int z = 2; z <= LEN - 2; z += 3) {
            switch (layout) {
                case CRYPT -> {
                    // Grave niches: a candle in the wall, the accent above.
                    for (int s : new int[] {-1, 1}) {
                        int x = s * (HALF + 1);
                        h.put(x, 1, z, Blocks.AIR.defaultBlockState());
                        h.put(s * (HALF + 2), 1, z, b.first(Part.BASE));
                        h.put(s * (HALF + 2), 0, z, b.first(Part.BASE));
                        h.put(s * (HALF + 2), 2, z, b.first(Part.BASE));
                        h.small(x, 1, z);
                        h.put(x, 2, z, Part.ACCENT);
                    }
                }
                case KEEP -> {
                    if (z % 6 == 2) {
                        h.sym(HALF + 1, 7, z, Part.ACCENT);
                        h.sym(HALF + 1, 8, z, Part.ACCENT);
                        h.put(-HALF, 0, z, b.banner(4));
                        h.put(HALF, 0, z, b.banner(12));
                    }
                }
                case CELLAR -> {
                    if (z % 6 == 2) {
                        for (int s : new int[] {-1, 1}) {
                            h.lamp(s * (HALF + 1), 2, z);
                        }
                    }
                }
                case FORGE -> {
                    if (z % 6 == 2) {
                        h.sym(HALF + 1, 6, z, Part.ACCENT);
                        for (int s : new int[] {-1, 1}) {
                            h.lamp(s * (HALF + 1), 2, z);
                        }
                    }
                }
            }
        }
    }

    // ---- crypt

    private static void sarcophagi(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 21);
        for (int sx : new int[] {-9, -5, 4, 8}) {
            for (int sz : new int[] {11, 17}) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        h.put(sx + dx, 0, sz + dz, b.first(Part.TRIM));
                    }
                }
                h.small(sx + (sx < 0 ? 0 : 1), 1, sz + 1);
            }
        }
        altar(h, 4, 23, 27);
        for (int y = 0; y <= 2; y++) {
            h.sym(6, y, 26, Part.ACCENT);
        }
        h.sym(6, 3, 26, b.prop(1));
        for (int z = 11; z <= 27; z += 4) {
            h.sym(14, 0, z, b.prop(0));
        }
        h.center(-2, 15);
        h.center(2, 15);
        h.center(-7, 15);
        h.center(7, 15);
    }

    /** A podium +1 from x -w..w, z z0..z1 with steps in front, the altar and its lights. */
    private static void altar(H h, int w, int z0, int z1) {
        B b = h.b;
        for (int x = -w; x <= w; x++) {
            for (int z = z0; z <= z1; z++) {
                h.put(x, 0, z, b.first(Part.GALLERY));
            }
            h.put(x, 0, z0 - 1, b.stairs(Direction.SOUTH));
        }
        for (int x = -1; x <= 1; x++) {
            h.put(x, 1, z1 - 1, b.first(Part.FRAME));
            h.throne(x, 2, z1 - 1);
        }
        h.lamp(-w, 1, z0);
        h.lamp(w, 1, z0);
        h.lamp(-w, 1, z1);
        h.lamp(w, 1, z1);
    }

    private static void ossuary(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 23);
        // Bone columns up to the ceiling, skulls on low heaps between them.
        for (int x : new int[] {4, 10}) {
            for (int z : new int[] {7, 14, 18}) {
                for (int y = 0; y < HALL_H - 1; y++) {
                    h.sym(x, y, z, Part.ACCENT);
                }
            }
        }
        for (int z = 4; z <= 22; z += 4) {
            h.sym(3, 0, z, Part.ACCENT);
            h.sym(3, 1, z, b.prop(1));
            if (z % 8 == 4) {
                h.sym(5, 0, z + 1, Part.ACCENT);
                h.sym(5, 1, z + 1, b.lightSmall());
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = 24; z <= 27; z++) {
                if (x == 0 && z == 26) {
                    h.put(x, 0, z, b.first(Part.FRAME));
                } else {
                    h.put(x, 0, z, Part.ACCENT);
                }
            }
        }
        h.throne(0, 1, 26);
        h.lamp(-3, 1, 24);
        h.lamp(3, 1, 24);
        h.center(-7, 15);
        h.center(7, 15);
        h.center(0, 12);
        h.center(0, 19);
    }

    private static void chapel(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 20);
        // Pews facing the altar.
        for (int z : new int[] {8, 11, 14, 17}) {
            for (int x = 3; x <= 9; x++) {
                if ((x == 7 || x == 8) && (z == 9 || z == 10)) {
                    continue;
                }
                h.sym(x, 0, z, b.stairs(Direction.NORTH));
            }
            h.sym(10, 0, z, b.first(Part.FRAME));
            h.sym(10, 1, z, b.lightSmall());
        }
        altar(h, 5, 22, 27);
        h.sym(7, 0, 27, b.banner(8));
        h.sym(3, 0, 27, b.banner(8));
        h.center(0, 13);
        h.center(0, 18);
        h.center(-12, 13);
        h.center(12, 13);
    }

    private static void urns(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 21);
        // Urns on pedestals in rows.
        for (int x : new int[] {4, 10}) {
            for (int z : new int[] {7, 12, 17}) {
                h.sym(x, 0, z, b.first(Part.TRIM));
                h.sym(x, 1, z, b.prop(0));
            }
        }
        // The fallen corner: rubble from the back wall, a crack in the ceiling.
        Random r = new Random(h.oz * 13L + h.ox);
        for (int x = 2; x <= 9; x++) {
            for (int z = 22; z <= 29; z++) {
                int d = Math.max(Math.abs(x - 6), Math.abs(z - 27));
                int high = 3 - d + (r.nextInt(3) == 0 ? 1 : 0);
                for (int y = 0; y < high; y++) {
                    h.put(x, y, z, y == high - 1 && r.nextBoolean() ? Part.COVER : Part.WALL);
                }
                if (high > 0 && r.nextInt(3) == 0) {
                    h.put(x, high, z, b.slab(SlabType.BOTTOM));
                }
            }
        }
        for (int x = 4; x <= 7; x++) {
            h.put(x, HALL_H, 26, Part.BASE);
        }
        h.small(-6, 0, 26);
        h.small(-3, 0, 27);
        h.throne(0, 0, 27);
        h.center(-7, 15);
        h.center(7, 15);
        h.center(0, 14);
        h.center(-4, 24);
    }

    // ---- keep

    private static void yard(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 21);
        BlockState target = B.block("target");
        for (int x : new int[] {5}) {
            for (int z : new int[] {13, 17}) {
                h.sym(x, 0, z, b.rail());
                h.sym(x, 1, z, target);
            }
        }
        for (int x = 6; x <= 7; x++) {
            h.sym(x, 0, 15, Part.COVER);
        }
        h.sym(6, 1, 15, Part.COVER);
        for (int x = -3; x <= 3; x++) {
            for (int z = 15; z <= 16; z++) {
                h.put(x, 0, z, b.slab(SlabType.TOP));
            }
        }
        h.small(-3, 1, 15);
        h.small(3, 1, 16);
        // The watch platform at the back, +3.
        for (int x = -5; x <= 5; x++) {
            for (int z = 24; z <= 27; z++) {
                for (int y = 0; y <= 2; y++) {
                    h.put(x, y, z, b.first(Part.GALLERY));
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            h.put(x, 0, 23, b.first(Part.GALLERY));
            h.put(x, 1, 23, b.stairs(Direction.SOUTH));
            h.put(x, 0, 22, b.stairs(Direction.SOUTH));
        }
        for (int x : new int[] {-5, 5}) {
            h.lamp(x, 3, 24);
            h.lamp(x, 3, 27);
        }
        for (int x = -4; x <= 4; x++) {
            if (Math.abs(x) > 3) {
                h.put(x, 3, 24, b.rail());
            }
        }
        BlockState rod = B.block("lightning_rod");
        h.sym(3, 3, 27, rod);
        int k = 0;
        for (int x : new int[] {10, 12}) {
            h.sym(x, 0, 29, b.prop(k++));
        }
        h.center(-9, 11);
        h.center(9, 19);
        h.center(0, 20);
        h.center(2, 25);
    }

    private static void armoury(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 27);
        // Weapon walls: a rack of fences with tools between, banners at the ends.
        for (int z : new int[] {12, 18}) {
            for (int x = 3; x <= 9; x++) {
                if (x == 7 || x == 8) {
                    continue;
                }
                h.sym(x, 0, z, x % 2 == 0 ? b.prop((x + z) % 4) : b.rail());
            }
            h.sym(3, 1, z, b.lightSmall());
            h.sym(10, 0, z, b.banner(8));
        }
        for (int z = 11; z <= 26; z += 5) {
            h.sym(14, 0, z, Part.COVER);
            h.sym(14, 1, z, Part.COVER);
            h.sym(13, 0, z, Part.COVER);
        }
        for (int x = -4; x <= 4; x += 2) {
            h.put(x, 0, 27, b.prop(Math.abs(x) % 4));
        }
        h.small(0, 0, 25);
        h.center(-5, 15);
        h.center(5, 15);
        h.center(0, 21);
        h.center(-5, 23);
    }

    private static void warRoom(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 10);
        // The council table with a map, chairs round it, banners at the pillars.
        for (int x = -4; x <= 4; x++) {
            for (int z = 12; z <= 18; z++) {
                h.put(x, 0, z, x == 0 && z == 15 ? B.block("cartography_table") : b.slab(SlabType.TOP));
            }
            h.put(x, 0, 11, b.stairs(Direction.NORTH));
            h.put(x, 0, 19, b.stairs(Direction.SOUTH));
        }
        for (int z = 13; z <= 17; z += 2) {
            h.put(-5, 0, z, b.stairs(Direction.WEST));
            h.put(5, 0, z, b.stairs(Direction.EAST));
        }
        h.small(-4, 1, 12);
        h.small(4, 1, 18);
        h.small(4, 1, 12);
        h.small(-4, 1, 18);
        for (int z : new int[] {8, 11, 20, 23}) {
            h.sym(9, 0, z, b.banner(z < 15 ? 8 : 0));
        }
        for (int x = -6; x <= 6; x += 3) {
            h.put(x, 0, 28, Part.COVER);
            h.put(x, 1, 28, b.banner(8));
        }
        h.center(-7, 15);
        h.center(7, 15);
        h.center(0, 22);
        h.center(0, 8);
    }

    private static void stores(H h) {
        Random r = new Random(h.oz * 7L + 3);
        h.runner(-1, 1, 0, 29);
        // Stacks of barrels, hay and crates with aisles between.
        for (int x0 : new int[] {-10, -5, 4, 9}) {
            for (int z0 : new int[] {9, 15, 21}) {
                if ((x0 == -10 || x0 == 9) && z0 == 9) {
                    continue;
                }
                for (int x = x0; x <= x0 + 1; x++) {
                    for (int z = z0; z <= z0 + 2; z++) {
                        if (Math.abs(x) >= 7 && Math.abs(x) <= 8 && (z == 9 || z == 10 || z == 21 || z == 22)) {
                            continue;
                        }
                        int high = 1 + r.nextInt(3);
                        for (int y = 0; y < high; y++) {
                            h.put(x, y, z, Part.COVER);
                        }
                    }
                }
            }
        }
        h.small(-3, 0, 12);
        h.small(3, 0, 24);
        h.center(-7, 13);
        h.center(7, 19);
        h.center(-2, 19);
        h.center(2, 13);
    }

    // ---- cellar

    private static void cells(H h) {
        B b = h.b;
        // Cells under the galleries: walls every 4, bars in front, a door in each.
        for (int z = 10; z <= 26; z += 4) {
            for (int x = 12; x <= 15; x++) {
                for (int y = 0; y <= 3; y++) {
                    h.sym(x, y, z, Part.WALL);
                }
            }
        }
        for (int z = 10; z <= 27; z++) {
            if (z % 4 == 0 || z % 4 == 1) {
                continue;
            }
            for (int y = 0; y <= 3; y++) {
                h.sym(11, y, z, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        for (int z = 12; z <= 24; z += 4) {
            h.sym(14, 0, z + 1, b.prop(z % 3));
            h.sym(13, 0, z + 2, b.lightSmall());
        }
        // The water channel down the middle with two bridges.
        for (int z = 5; z <= 26; z++) {
            for (int x = -2; x <= 2; x++) {
                boolean bridge = z >= 12 && z <= 13 || z >= 19 && z <= 20;
                h.put(x, -1, z, bridge ? b.first(Part.GALLERY) : Blocks.WATER.defaultBlockState());
                h.put(x, -2, z, Part.FLOOR);
            }
        }
        for (int z : new int[] {12, 13, 19, 20}) {
            h.sym(3, 0, z, b.slab(SlabType.BOTTOM));
        }
        // The well at the back with chains over it.
        for (int x = -4; x <= 4; x++) {
            for (int z = 23; z <= 28; z++) {
                boolean edge = Math.abs(x) == 4 || z == 23 || z == 28;
                if (edge) {
                    h.put(x, 0, z, Part.WALL);
                } else {
                    h.put(x, -1, z, Blocks.WATER.defaultBlockState());
                    h.put(x, -2, z, Part.FLOOR);
                }
            }
        }
        for (int x : new int[] {-1, 1}) {
            for (int y = 6; y < HALL_H; y++) {
                h.put(x, y, 25, b.chain());
                h.put(x, y, 26, b.chain());
            }
        }
        cage(h, 5, 15);
        cage(h, -6, 15);
        h.put(9, 0, 17, Part.COVER);
        h.put(9, 0, 16, Part.COVER);
        h.put(-9, 0, 17, Part.COVER);
        h.put(-9, 0, 16, Part.COVER);
        h.center(-5, 9);
        h.center(5, 21);
        h.center(-5, 21);
        h.center(5, 9);
    }

    /** A cage of bars 2 by 2 hanging from a chain. */
    private static void cage(H h, int x, int z) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                h.put(x + dx, 6, z + dz, Blocks.IRON_BARS.defaultBlockState());
                h.put(x + dx, 7, z + dz, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        for (int y = 8; y < HALL_H; y++) {
            h.put(x, y, z, h.b.chain());
        }
    }

    private static void torture(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 6);
        for (int x : new int[] {4, 10}) {
            for (int z : new int[] {7, 14, 18}) {
                for (int y = 5; y < HALL_H; y++) {
                    h.sym(x, y, z, b.chain());
                }
            }
        }
        cage(h, 9, 12);
        cage(h, -10, 12);
        cage(h, 9, 24);
        cage(h, -10, 24);
        for (int z : new int[] {11, 16, 21, 26}) {
            h.sym(3, 0, z, b.prop(z % 4));
            h.sym(5, 0, z + 1, b.prop((z + 1) % 4));
        }
        for (int x = -2; x <= 2; x++) {
            h.put(x, 0, 15, b.first(Part.FRAME));
            h.put(x, 0, 16, b.first(Part.FRAME));
        }
        h.small(0, 1, 15);
        h.small(-6, 0, 27);
        h.small(6, 0, 27);
        h.throne(0, 0, 27);
        h.center(-6, 15);
        h.center(6, 15);
        h.center(0, 20);
        h.center(0, 10);
    }

    private static void well(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 9);
        // A round well in the middle, pillars in a ring, chains over it.
        for (int x = -5; x <= 5; x++) {
            for (int z = 10; z <= 20; z++) {
                double r = Math.hypot(x, z - 15);
                if (r <= 3.5) {
                    h.put(x, -1, z, Blocks.WATER.defaultBlockState());
                    h.put(x, -2, z, Blocks.WATER.defaultBlockState());
                    h.put(x, -3, z, Part.FLOOR);
                } else if (r <= 4.6) {
                    h.put(x, 0, z, Part.WALL);
                    h.put(x, -2, z, Part.WALL);
                    h.put(x, -3, z, Part.WALL);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 7);
            int z = 15 + (int) Math.round(Math.sin(a) * 7);
            for (int y = 0; y < HALL_H; y++) {
                h.put(x, y, z, Part.PILLAR);
            }
            h.lamp(x, 0, z + (z > 15 ? 1 : -1));
        }
        for (int y = 4; y < HALL_H; y++) {
            h.put(0, y, 15, b.chain());
        }
        h.put(0, 3, 15, B.block("cauldron"));
        h.center(-8, 15);
        h.center(8, 15);
        h.center(0, 22);
        h.center(0, 8);
    }

    private static void roots(H h) {
        B b = h.b;
        Random r = new Random(h.oz * 11L + 5);
        h.runner(-1, 1, 0, 29);
        // Roots break through the ceiling, rubble and growth on the floor.
        for (int i = 0; i < 26; i++) {
            int x = r.nextInt(25) - 12;
            int z = 4 + r.nextInt(23);
            int len = 1 + r.nextInt(4);
            for (int y = HALL_H - 1; y >= HALL_H - len; y--) {
                h.put(x, y, z, Part.WALL_TOP);
            }
        }
        for (int x = -10; x <= -3; x++) {
            for (int z = 12; z <= 19; z++) {
                int d = Math.max(Math.abs(x + 6), Math.abs(z - 15));
                int high = 3 - d;
                for (int y = 0; y < high; y++) {
                    h.put(x, y, z, Part.WALL);
                }
                if (high == 0 && r.nextInt(3) == 0) {
                    h.put(x, 0, z, b.slab(SlabType.BOTTOM));
                }
            }
        }
        for (int i = 0; i < 18; i++) {
            int x = r.nextInt(23) - 11;
            int z = 2 + r.nextInt(26);
            if (Math.abs(x) <= 1) {
                continue;
            }
            h.put(x, -1, z, Part.ACCENT);
        }
        h.small(6, 0, 12);
        h.small(-2, 0, 22);
        h.small(8, 0, 24);
        h.center(5, 15);
        h.center(-6, 22);
        h.center(0, 8);
        h.center(6, 22);
    }

    // ---- forge

    private static void forge(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 13);
        // The casting trench across the hall: lava behind railings, two bridges.
        for (int x = -12; x <= 12; x++) {
            boolean bridge = Math.abs(x) >= 6 && Math.abs(x) <= 8;
            for (int z = 15; z <= 16; z++) {
                h.put(x, -1, z, bridge ? b.first(Part.GALLERY) : Blocks.LAVA.defaultBlockState());
                h.put(x, -2, z, Part.FLOOR);
            }
            if (!bridge) {
                h.put(x, 0, 14, b.rail());
                h.put(x, 0, 17, b.rail());
            }
        }
        for (int z = 14; z <= 17; z++) {
            h.sym(13, 0, z, b.rail());
        }
        // The forge with its glowing mouth and a chimney to the ceiling.
        for (int x = -4; x <= 4; x++) {
            for (int z = 24; z <= 28; z++) {
                for (int y = 0; y <= 3; y++) {
                    h.put(x, y, z, Part.WALL);
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            h.put(x, 1, 24, b.lampState());
            h.put(x, 2, 24, b.lampState());
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = 26; z <= 28; z++) {
                for (int y = 4; y < HALL_H; y++) {
                    h.put(x, y, z, Part.WALL_TOP);
                }
            }
        }
        BlockState oven = B.block("blast_furnace");
        for (int z = 11; z <= 26; z += 3) {
            h.put(-HALF, 0, z, oven.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            h.put(HALF, 0, z, oven.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        }
        h.sym(4, 0, 20, b.prop(0));
        h.sym(4, 0, 10, b.prop(1));
        for (int[] c : new int[][] {{6, 11}, {7, 11}, {6, 12}}) {
            h.sym(c[0], 0, c[1], Part.COVER);
        }
        h.sym(6, 1, 11, Part.COVER);
        h.center(-4, 12);
        h.center(4, 19);
        h.center(-4, 21);
        h.center(4, 9);
    }

    private static void ovens(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 29);
        String[] kinds = {"furnace", "blast_furnace", "smoker"};
        for (int z : new int[] {12, 18}) {
            for (int x = 2; x <= 10; x++) {
                if (x == 7 || x == 8) {
                    continue;
                }
                BlockState o = B.block(kinds[(x + z) % 3]);
                h.sym(x, 0, z, o.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                h.sym(x, 0, z + 1, o.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                h.sym(x, 1, z, Part.COVER);
                h.sym(x, 1, z + 1, Part.COVER);
            }
            for (int y = 2; y < HALL_H; y++) {
                h.sym(5, y, z, Part.WALL_TOP);
                h.sym(5, y, z + 1, Part.WALL_TOP);
            }
        }
        h.sym(3, 2, 13, b.lightSmall());   // on the ovens (it stood in the air over the aisle, 29.10.)
        for (int z = 24; z <= 27; z++) {
            h.sym(4, 0, z, b.prop(z % 4));
        }
        h.small(0, 0, 27);
        h.center(-6, 15);
        h.center(6, 15);
        h.center(0, 22);
        h.center(0, 9);
    }

    private static void castingPool(H h) {
        B b = h.b;
        h.runner(-1, 1, 0, 10);
        for (int x = -4; x <= 4; x++) {
            for (int z = 11; z <= 19; z++) {
                boolean edge = Math.abs(x) == 4 || z == 11 || z == 19;
                if (edge) {
                    h.put(x, 0, z, Blocks.IRON_BARS.defaultBlockState());
                    h.put(x, 1, z, Blocks.IRON_BARS.defaultBlockState());
                    h.put(x, -1, z, b.first(Part.TRIM));
                } else {
                    h.put(x, -1, z, Blocks.LAVA.defaultBlockState());
                    h.put(x, -2, z, Part.FLOOR);
                }
            }
        }
        for (int[] c : new int[][] {{5, 10}, {5, 20}}) {
            h.sym(c[0], 0, c[1], b.lightSmall());
        }
        for (int z = 22; z <= 27; z += 5) {
            for (int x = 3; x <= 6; x++) {
                h.sym(x, 0, z, b.slab(SlabType.BOTTOM));
            }
            h.sym(2, 0, z, b.prop(0));
        }
        h.center(-7, 15);
        h.center(7, 15);
        h.center(0, 23);
        h.center(0, 7);
    }

    private static void coalStore(H h) {
        Random r = new Random(h.oz * 5L + 1);
        B b = h.b;
        h.runner(-1, 1, 0, 29);
        BlockState rail = B.block("rail");
        for (int z = 0; z <= 28; z++) {
            h.put(0, 0, z, rail);
        }
        String[] ores = {"coal_block", "raw_iron_block", "raw_copper_block", "coal_block"};
        for (int[] c : new int[][] {{-9, 11}, {-4, 17}, {4, 11}, {9, 17}, {-9, 24}, {9, 24}, {4, 24}, {-4, 6}}) {
            BlockState ore = B.block(ores[r.nextInt(ores.length)]);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int high = dx == 0 && dz == 0 ? 3 : 1 + r.nextInt(2);
                    int x = c[0] + dx;
                    int z = c[1] + dz;
                    if (Math.abs(x) >= 7 && Math.abs(x) <= 8 && (z >= 9 && z <= 10 || z >= 21 && z <= 22)) {
                        continue;
                    }
                    for (int y = 0; y < high; y++) {
                        h.put(x, y, z, ore);
                    }
                }
            }
        }
        h.small(-6, 0, 14);
        h.small(6, 0, 20);
        h.center(-6, 20);
        h.center(6, 14);
        h.center(-3, 25);
        h.center(3, 3);
    }

    // ------------------------------------------------------------------ the arena

    private static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180 ? 360 - d : d;
    }

    private static int ceilAt(double r) {
        return ARENA_Y + 14 + (int) Math.round(2 * Math.cos(Math.min(r, 24.0) / 24.0 * Math.PI / 2));
    }

    private static void arena(B b, Plan plan, int cz) {
        int y = ARENA_Y;
        Layout layout = b.kind.layout();
        double inner = R + 0.5;
        double outer = R + 1.7;
        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                double r = Math.hypot(x, z);
                if (r > outer) {
                    continue;
                }
                int top = ceilAt(r);
                int wz = cz + z;
                b.put(x, y - 1, wz, r >= R - 0.5 ? b.first(Part.TRIM) : b.p(Part.FLOOR, x, y - 1, wz));
                double a = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
                boolean rib = angleDiff(a % 45, 22.5) < 3.5;
                b.put(x, top, wz, rib ? b.first(Part.BEAM) : b.p(Part.CEILING, x, top, wz));
                for (int up = y; up < top; up++) {
                    if (r <= inner) {
                        b.put(x, up, wz, Blocks.AIR.defaultBlockState());
                    } else {
                        b.put(x, up, wz, rib ? b.p(Part.PILLAR, x, up, wz) : b.wall(up - y, x + z, x, up, wz));
                    }
                }
                if (r <= inner) {
                    b.walk(x, y, wz);
                }
            }
        }
        // The ring gallery +6, railing on its inner edge, pillars under it.
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double r = Math.hypot(x, z);
                if (r >= 16.0 && r <= inner) {
                    b.put(x, y + 5, cz + z, b.first(Part.GALLERY));
                    if (r < 17.0) {
                        b.put(x, y + 6, cz + z, b.rail());
                    } else {
                        b.walk(x, y + 6, cz + z);
                    }
                }
            }
        }
        for (double a : new double[] {22.5, 67.5, 112.5, 157.5, 225, 315}) {
            double rad = Math.toRadians(a);
            int px = (int) Math.floor(Math.cos(rad) * 15.6);
            int pz = (int) Math.floor(Math.sin(rad) * 15.6);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int up = y; up < y + 5; up++) {
                        b.put(px + dx, up, cz + pz + dz, b.p(Part.PILLAR, px + dx, up, cz + pz + dz));
                    }
                }
            }
        }
        // Stairs to the gallery: south-west and south-east by the entrance, one at the back.
        for (int s : new int[] {-1, 1}) {
            for (int k = 0; k <= 5; k++) {
                int x = s * (9 + k);
                for (int z = -6; z <= -4; z++) {
                    for (int up = 0; up < k; up++) {
                        b.put(x, y + up, cz + z, b.first(Part.GALLERY));
                    }
                    b.put(x, y + k, cz + z, b.stairs(s < 0 ? Direction.WEST : Direction.EAST));
                }
            }
            for (int x = 15; x <= 17; x++) {
                for (int z = -6; z <= -4; z++) {
                    b.put(s * x, y + 5, cz + z, b.first(Part.GALLERY));
                    b.put(s * x, y + 6, cz + z, Blocks.AIR.defaultBlockState());
                }
            }
            b.put(s * 15, y + 6, cz - 7, b.rail());
            b.put(s * 15, y + 6, cz - 3, b.rail());
        }
        for (int k = 0; k <= 5; k++) {
            int z = 10 + k;
            for (int x = -1; x <= 1; x++) {
                for (int up = 0; up < k; up++) {
                    b.put(x, y + up, cz + z, b.first(Part.GALLERY));
                }
                b.put(x, y + k, cz + z, b.stairs(Direction.SOUTH));
            }
        }
        for (int z = 16; z <= 17; z++) {
            for (int x = -1; x <= 1; x++) {
                b.put(x, y + 5, cz + z, b.first(Part.GALLERY));
                b.put(x, y + 6, cz + z, Blocks.AIR.defaultBlockState());
            }
        }
        // Tower platforms +3 by the entrance, with three steps on the inner side.
        for (int s : new int[] {-1, 1}) {
            for (int ax = 7; ax <= 10; ax++) {
                for (int z = -14; z <= -11; z++) {
                    for (int up = 0; up <= 2; up++) {
                        b.put(s * ax, y + up, cz + z, b.first(Part.GALLERY));
                    }
                }
            }
            for (int k = 0; k < 3; k++) {
                int x = s * (6 - k);
                for (int z = -13; z <= -12; z++) {
                    for (int up = 0; up < 2 - k; up++) {
                        b.put(x, y + up, cz + z, b.first(Part.GALLERY));
                    }
                    b.put(x, y + 2 - k, cz + z, b.stairs(s < 0 ? Direction.WEST : Direction.EAST));
                }
            }
            b.put(s * 10, y + 3, cz - 14, b.rail());
            b.put(s * 10, y + 3, cz - 11, b.rail());
            if (layout == Layout.KEEP) {
                for (int ax = 7; ax <= 10; ax++) {
                    if ((ax & 1) == 0) {
                        b.put(s * ax, y + 3, cz - 14, b.p(Part.WALL, s * ax, y + 3, cz - 14));
                    }
                }
            }
        }
        // Cover islands: a pillar 2 by 2 with a half wall on the side of the players, a light on top.
        for (int[] c : new int[][] {{-12, 3}, {11, 3}, {-6, 12}, {5, 12}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int up = 0; up <= 3; up++) {
                        b.put(c[0] + dx, y + up, cz + c[1] + dz, b.p(Part.PILLAR, c[0] + dx, y + up, cz + c[1] + dz));
                    }
                }
            }
            b.lamp(c[0], y + 4, cz + c[1]);
            for (int dx = -1; dx <= 2; dx++) {
                b.put(c[0] + dx, y, cz + c[1] - 1, b.p(Part.COVER, c[0] + dx, y, cz + c[1] - 1));
                b.put(c[0] + dx, y + 1, cz + c[1] - 1, b.slab(SlabType.BOTTOM));
            }
        }
        // The champion's pit: -3, a ramp towards the players, the throne at its far side.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double r = Math.hypot(x, z);
                if (r <= 4.5) {
                    b.put(x, y - 4, cz + z, b.first(Part.TRIM));
                    for (int up = y - 3; up < y; up++) {
                        b.put(x, up, cz + z, Blocks.AIR.defaultBlockState());
                    }
                    b.walk(x, y - 3, cz + z);
                } else if (r <= 5.6) {
                    for (int up = y - 4; up < y; up++) {
                        b.put(x, up, cz + z, b.first(Part.TRIM));
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int k = 0; k < 3; k++) {
                int z = -4 + k;
                for (int up = y - 3; up < y - 1 - k; up++) {
                    b.put(x, up, cz + z, b.first(Part.TRIM));
                }
                b.put(x, y - 1 - k, cz + z, b.stairs(Direction.NORTH));
            }
            b.put(x, y - 1, cz - 5, b.first(Part.TRIM));
        }
        b.put(0, y - 3, cz + 3, b.stairs(Direction.SOUTH));
        for (int up = y - 3; up <= y - 1; up++) {
            b.put(0, up, cz + 4, b.first(Part.FRAME));
        }
        b.put(-1, y - 3, cz + 3, b.first(Part.FRAME));
        b.put(1, y - 3, cz + 3, b.first(Part.FRAME));
        b.small(-1, y - 2, cz + 3, true);
        b.small(1, y - 2, cz + 3, true);
        b.small(0, y, cz + 5, true);
        // The way in: the gate in the wall and the floor before it.
        for (int x = -2; x <= 2; x++) {
            for (int up = y; up <= y + 4; up++) {
                b.put(x, up, cz - R - 1, Blocks.AIR.defaultBlockState());
            }
            b.put(x, y - 1, cz - R - 1, b.first(Part.TRIM));
        }
        // Monster gates west, north-west, north-east, east: bars in the wall, a chamber behind.
        for (double a : new double[] {0, 45, 135, 180}) {
            double rad = Math.toRadians(a);
            double ca = Math.cos(rad);
            double sa = Math.sin(rad);
            for (int x = -R - 7; x <= R + 7; x++) {
                for (int z = -R - 7; z <= R + 7; z++) {
                    double t = x * ca + z * sa;
                    double u = -x * sa + z * ca;
                    if (t < 21.5 || t > 27.4 || Math.abs(u) > 2.4) {
                        continue;
                    }
                    if (Math.hypot(x, z) <= inner) {
                        continue;
                    }
                    boolean wallLine = t <= 23.9;
                    b.put(x, y - 1, cz + z, b.first(Part.TRIM));
                    for (int up = y; up <= y + 3; up++) {
                        if (wallLine) {
                            b.put(x, up, cz + z, Blocks.IRON_BARS.defaultBlockState());
                            plan.waveGates.add(b.at(x, up, cz + z));
                        } else {
                            b.put(x, up, cz + z, Blocks.AIR.defaultBlockState());
                        }
                    }
                    if (!wallLine) {
                        b.put(x, y + 4, cz + z, b.p(Part.CEILING, x, y + 4, cz + z));
                    }
                }
            }
            for (int u = -1; u <= 1; u++) {
                int x = (int) Math.round(ca * 25.5 - sa * u);
                int z = (int) Math.round(sa * 25.5 + ca * u);
                plan.waveSpots.add(b.at(x, y, cz + z));
            }
            b.hang((int) Math.round(ca * 25.5), y + 3, cz + (int) Math.round(sa * 25.5), y + 4);
        }
        // Lights: under the gallery, over the gallery, over the floor.
        for (int i = 0; i < 12; i++) {
            double rad = Math.PI * 2 * i / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(rad) * 19.5);
            int z = (int) Math.round(Math.sin(rad) * 19.5);
            b.put(x, y + 4, cz + z, b.hangUnder());
            b.hang(x, y + (b.dim ? 9 : 10), cz + z, ceilAt(19.5));
            b.small((int) Math.round(Math.cos(rad) * 21.5), y + 6, cz + (int) Math.round(Math.sin(rad) * 21.5), false);
        }
        for (int i = 0; i < 8; i++) {
            double rad = Math.PI * 2 * i / 8 + Math.PI / 8;
            int x1 = (int) Math.round(Math.cos(rad) * 9);
            int z1 = cz + (int) Math.round(Math.sin(rad) * 9);
            b.hang(x1, b.overStairs(x1, y + 7, z1) ? y + 7 : y + (b.dim ? 5 : 7), z1, ceilAt(9));
            double rad2 = Math.PI * 2 * i / 8;
            int x2 = (int) Math.round(Math.cos(rad2) * 13);
            int z2 = cz + (int) Math.round(Math.sin(rad2) * 13);
            // Over a stair a low light is in the way (Simon 29.10.: "das Ende der Treppe ist durch einen Block blockiert").
            b.hang(x2, b.overStairs(x2, y + 7, z2) ? y + 7 : y + (b.dim ? 5 : 7), z2, ceilAt(13));
        }
        arenaDecor(b, layout, cz);
    }

    /** The building's own arena: grave chamber, castle yard, cistern, casting hall. */
    private static void arenaDecor(B b, Layout layout, int cz) {
        int y = ARENA_Y;
        switch (layout) {
            case CRYPT -> {
                // Coffins between the pillars under the gallery, candles on them.
                for (double a : new double[] {90, 202.5, 337.5, 22.5 + 45 * 3}) {
                    double rad = Math.toRadians(a);
                    int x = (int) Math.round(Math.cos(rad) * 19.5);
                    int z = (int) Math.round(Math.sin(rad) * 19.5);
                    for (int dz = 0; dz < 2; dz++) {
                        b.put(x, y, cz + z + dz, b.first(Part.TRIM));
                    }
                    b.small(x, y + 1, cz + z, false);
                }
                for (int[] c : new int[][] {{-8, 6}, {8, 6}, {-8, -8}, {8, -8}}) {
                    for (int up = 0; up <= 2; up++) {
                        b.put(c[0], y + up, cz + c[1], b.p(Part.ACCENT, c[0], y + up, cz + c[1]));
                    }
                    b.put(c[0], y + 3, cz + c[1], b.prop(1));
                }
            }
            case KEEP -> {
                for (int i = 0; i < 8; i++) {
                    double rad = Math.PI * 2 * i / 8 + Math.PI / 8;
                    if (i == 5 || i == 6) {
                        continue;
                    }
                    b.put((int) Math.round(Math.cos(rad) * 20), y + 6, cz + (int) Math.round(Math.sin(rad) * 20), b.banner(i * 2 + 12));
                }
            }
            case CELLAR, FORGE -> {
                // A ring channel round the pit (water in the cistern, lava in the forge), four bridges.
                boolean lava = layout == Layout.FORGE;
                for (int x = -10; x <= 10; x++) {
                    for (int z = -10; z <= 10; z++) {
                        double r = Math.hypot(x, z);
                        boolean bridge = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                        if (r >= 6.5 && r <= 8.5) {
                            if (bridge) {
                                b.put(x, y - 1, cz + z, b.first(Part.GALLERY));
                            } else {
                                b.put(x, y - 1, cz + z, lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState());
                                b.put(x, y - 2, cz + z, b.p(Part.FLOOR, x, y - 2, cz + z));
                            }
                        } else if (lava && !bridge && (r >= 5.7 && r < 6.5 || r > 8.5 && r <= 9.3)) {
                            b.put(x, y, cz + z, b.rail());
                        }
                    }
                }
                for (int i = 0; i < 8; i++) {
                    double rad = Math.PI * 2 * i / 8 + Math.PI / 8;
                    int x = (int) Math.round(Math.cos(rad) * 12);
                    int z = (int) Math.round(Math.sin(rad) * 12);
                    for (int up = y + 8; up < ceilAt(12); up++) {
                        b.put(x, up, cz + z, b.chain());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ builder

    /** Collects blocks in layout space, turned into the world and into the kind's palette. */
    static final class B {
        final BlockPos origin;
        final int facing;
        final ExpeditionKind kind;
        final long seed;
        final Rotation rotation;
        final Map<Long, BlockState> out = new LinkedHashMap<>();
        final List<long[]> walks = new ArrayList<>();
        final Map<String, BlockState> states = new HashMap<>();
        /** Soul lanterns light less far: more of them, lower down. */
        final boolean dim;

        B(BlockPos origin, int facing, ExpeditionKind kind, long seed) {
            this.origin = origin;
            this.facing = facing;
            this.kind = kind;
            this.seed = seed;
            this.rotation = CampLayout.rotation(facing);
            this.dim = kind.block(Part.LIGHT_HANG).startsWith("soul");
        }

        static long key(int x, int y, int z) {
            return ((long) (x + 2048) << 24) | ((long) (y + 2048) << 12) | (z + 2048);
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
            out.put(key(x, y, z), state);
        }

        BlockState get(int x, int y, int z) {
            return out.get(key(x, y, z));
        }

        void walk(int x, int y, int z) {
            walks.add(new long[] {x, y, z});
        }

        BlockState state(String id) {
            return states.computeIfAbsent(id, B::block);
        }

        /** The ground block of a part. */
        BlockState first(Part part) {
            return state(kind.block(part));
        }

        /** A block of a part's mix at a spot: the ground, the others in patches (palette rule 1). */
        BlockState p(Part part, int x, int y, int z) {
            List<Weighted> mix = kind.mix(part);
            if (mix.size() <= 1) {
                return first(part);
            }
            double scale = part == Part.FLOOR ? 3.0 : 2.2;
            int total = 0;
            for (Weighted w : mix) {
                total += w.weight();
            }
            double r = (0.8 * smooth(x, y, z, scale) + 0.2 * noise(x, y, z, 3)) * total;
            r = Math.min(total - 1e-6, Math.max(0.0, (r - 0.5 * total) * 1.9 + 0.5 * total));
            for (Weighted w : mix) {
                r -= w.weight();
                if (r <= 0) {
                    return state(w.block());
                }
            }
            return state(mix.get(mix.size() - 1).block());
        }

        double noise(int x, int y, int z, int salt) {
            long v = x * 374761393L + y * 668265263L + z * 1442695041L + (seed + salt) * 2147483647L;
            v = (v ^ (v >>> 13)) * 1274126177L;
            v = v ^ (v >>> 16);
            return (v & 0xffff) / 65535.0;
        }

        double smooth(int x, int y, int z, double scale) {
            double fx = x / scale;
            double fy = y / scale;
            double fz = z / scale;
            int x0 = (int) Math.floor(fx);
            int y0 = (int) Math.floor(fy);
            int z0 = (int) Math.floor(fz);
            double tx = fx - x0;
            double ty = fy - y0;
            double tz = fz - z0;
            tx = tx * tx * (3 - 2 * tx);
            ty = ty * ty * (3 - 2 * ty);
            tz = tz * tz * (3 - 2 * tz);
            double acc = 0;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        double w = (dx == 1 ? tx : 1 - tx) * (dy == 1 ? ty : 1 - ty) * (dz == 1 ? tz : 1 - tz);
                        acc += w * noise(x0 + dx, y0 + dy, z0 + dz, 11);
                    }
                }
            }
            return acc;
        }

        /** A wall block by its height above the floor (palette rules 2 and 3); the keep gets half-timbering. */
        BlockState wall(int up, int along, int x, int y, int z) {
            if (up <= 1) {
                return p(Part.BASE, x, y, z);
            }
            if (up == 4 || up == HALL_H - 1) {
                return first(Part.TRIM);
            }
            if (up >= 6) {
                if (kind.layout() == Layout.KEEP && up <= 10) {
                    return Math.floorMod(along, 4) == 0 ? first(Part.FRAME) : p(Part.WALL_TOP, x, y, z);
                }
                return p(Part.WALL_TOP, x, y, z);
            }
            return p(Part.WALL, x, y, z);
        }

        BlockState slab(SlabType type) {
            BlockState s = first(Part.SLAB);
            return s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, type) : s;
        }

        /** Stairs going up towards the given side. */
        BlockState stairs(Direction up) {
            BlockState s = first(Part.STAIRS);
            if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, up);
            }
            if (s.hasProperty(BlockStateProperties.HALF)) {
                s = s.setValue(BlockStateProperties.HALF, Half.BOTTOM);
            }
            return s;
        }

        BlockState rail() {
            return first(Part.RAIL);
        }

        BlockState chain() {
            return state("iron_chain");
        }

        BlockState banner(int rotation16) {
            BlockState s = first(Part.BANNER);
            return s.hasProperty(BlockStateProperties.ROTATION_16) ? s.setValue(BlockStateProperties.ROTATION_16, rotation16 & 15) : s;
        }

        BlockState prop(int index) {
            List<Weighted> mix = kind.mix(Part.PROPS);
            if (mix.isEmpty()) {
                return first(Part.COVER);
            }
            return state(mix.get(Math.floorMod(index, mix.size())).block());
        }

        void prop(int x, int y, int z, int index) {
            put(x, y, z, prop(index));
        }

        BlockState lightSmall() {
            return smallState(Part.LIGHT_SMALL);
        }

        private BlockState smallState(Part part) {
            BlockState s = first(part);
            if (s.hasProperty(BlockStateProperties.CANDLES)) {
                s = s.setValue(BlockStateProperties.CANDLES, 4);
            }
            if (s.hasProperty(BlockStateProperties.DOWN) && s.is(Blocks.GLOW_LICHEN)) {
                s = s.setValue(BlockStateProperties.DOWN, true);
            }
            if (s.hasProperty(BlockStateProperties.HANGING)) {
                s = s.setValue(BlockStateProperties.HANGING, false);
            }
            return s;
        }

        /** A small light standing on the floor (the throne's colour when asked for). */
        void small(int x, int y, int z, boolean throne) {
            put(x, y, z, smallState(throne ? Part.THRONE : Part.LIGHT_SMALL));
        }

        /** A wall light: a full block goes into the wall, anything else stands in its place. */
        BlockState lampState() {
            BlockState s = first(Part.LIGHT_WALL);
            if (s.hasProperty(BlockStateProperties.FACING)) {
                s = s.setValue(BlockStateProperties.FACING, Direction.UP);
            }
            return s;
        }

        void lamp(int x, int y, int z) {
            BlockState s = lampState();
            BlockState below = get(x, y - 1, z);
            if (!s.canOcclude() && (below == null || below.isAir())) {
                put(x, y - 1, z, first(Part.BASE));   // a campfire in the hall stood in the air (29.10.)
            }
            put(x, y, z, s);
        }

        /** The hanging light under a block (no chain). */
        BlockState hangUnder() {
            BlockState s = first(Part.LIGHT_HANG);
            return s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, true) : s;
        }

        /** True when a stair lies in the column below y (down to 8 blocks): a low light there is in the way. */
        boolean overStairs(int x, int y, int z) {
            for (int dy = 1; dy <= 8; dy++) {
                BlockState s = get(x, y - dy, z);
                if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
                    return true;
                }
            }
            return false;
        }

        /** A hanging light at y on a chain up to the ceiling at ceilingY. */
        void hang(int x, int y, int z, int ceilingY) {
            for (int up = y + 1; up < ceilingY; up++) {
                BlockState there = get(x, up, z);
                if (there == null || there.isAir()) {
                    put(x, up, z, chain());
                }
            }
            put(x, y, z, hangUnder());
        }

        void runner(int x, int y, int z) {
            BlockState s = first(Part.RUNNER);
            if (s.getBlock() instanceof CarpetBlock) {
                BlockState there = get(x, y, z);
                if (there == null || there.isAir()) {
                    put(x, y, z, s);
                }
            } else {
                put(x, y - 1, z, p(Part.RUNNER, x, y - 1, z));
            }
        }

        private boolean passable(BlockState s) {
            return s == null || s.isAir() || s.getBlock() instanceof CarpetBlock;
        }

        private boolean solid(BlockState s) {
            return s != null && !s.isAir() && s.getFluidState().isEmpty() && !(s.getBlock() instanceof CarpetBlock)
                    && !s.is(Blocks.IRON_CHAIN);
        }

        /** A spot to stand on near (x, y, z): free for two blocks, solid below. Null when none. */
        BlockPos freeNear(int x, int y, int z) {
            for (int r = 0; r <= 3; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        for (int dy : new int[] {0, 1, -1}) {
                            int ax = x + dx;
                            int ay = y + dy;
                            int az = z + dz;
                            BlockState feet = get(ax, ay, az);
                            if (feet != null && passable(feet) && passable(get(ax, ay + 1, az)) && solid(get(ax, ay - 1, az))) {
                                return at(ax, ay, az);
                            }
                        }
                    }
                }
            }
            return null;
        }

        /** Walls, floors and ceilings where carved air meets nothing (corridor sides, bends). */
        void enclose() {
            List<long[]> add = new ArrayList<>();
            for (Map.Entry<Long, BlockState> e : out.entrySet()) {
                if (!e.getValue().isAir()) {
                    continue;
                }
                long k = e.getKey();
                int x = (int) (k >>> 24) - 2048;
                int y = (int) ((k >>> 12) & 0xfff) - 2048;
                int z = (int) (k & 0xfff) - 2048;
                // Walls only under the ground: above it stands the gatehouse, whose door must
                // stay open (Simon 29.10.: "der Eingang ist immer versperrt" – it was walled up).
                for (int dx = -1; dx <= 1 && y < 0; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 || dz != 0) && !out.containsKey(key(x + dx, y, z + dz))) {
                            add.add(new long[] {x + dx, y, z + dz, 0});
                        }
                    }
                }
                if (!out.containsKey(key(x, y - 1, z))) {
                    add.add(new long[] {x, y - 1, z, 1});
                }
                if (!out.containsKey(key(x, y + 1, z)) && y < -2) {
                    add.add(new long[] {x, y + 1, z, 2});
                }
            }
            for (long[] a : add) {
                int x = (int) a[0];
                int y = (int) a[1];
                int z = (int) a[2];
                if (out.containsKey(key(x, y, z))) {
                    continue;
                }
                Part part = a[3] == 1 ? Part.FLOOR : a[3] == 2 ? Part.CEILING : Part.WALL;
                put(x, y, z, p(part, x, y, z));
            }
        }

        /**
         * Hidden light (palette rule 7: at least 7 on the ways; dark comes from fog and colour):
         * invisible light blocks 2 over the floor every 5 blocks, where there is air.
         */
        void hiddenLight() {
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, dim ? 14 : 13);
            java.util.Set<Long> done = new java.util.HashSet<>();
            for (long[] w : walks) {
                int x = (int) w[0];
                int y = (int) w[1];
                int z = (int) w[2];
                if (Math.floorMod(x, 5) != 0 || Math.floorMod(z, 5) != 0) {
                    continue;
                }
                for (int up = 2; up >= 1; up--) {
                    long k = key(x, y + up, z);
                    BlockState there = out.get(k);
                    if (there != null && there.isAir() && done.add(k)) {
                        out.put(k, light);
                        break;
                    }
                }
            }
        }

        /** Palette rule 10: sand and gravel only on something solid (a cave under the floor would let them fall). */
        void support() {
            List<long[]> add = new ArrayList<>();
            for (Map.Entry<Long, BlockState> e : out.entrySet()) {
                if (!(e.getValue().getBlock() instanceof net.minecraft.world.level.block.FallingBlock)) {
                    continue;
                }
                long k = e.getKey();
                int x = (int) (k >>> 24) - 2048;
                int y = (int) ((k >>> 12) & 0xfff) - 2048;
                int z = (int) (k & 0xfff) - 2048;
                BlockState below = out.get(key(x, y - 1, z));
                if (below == null || !solid(below)) {
                    add.add(new long[] {x, y - 1, z});
                }
            }
            for (long[] a : add) {
                put((int) a[0], (int) a[1], (int) a[2], first(Part.BASE));
            }
        }

        /** Into the world: rotated, in world positions; the ground above the vaults. */
        void finish(Plan plan) {
            Map<Long, Integer> top = new HashMap<>();
            for (Map.Entry<Long, BlockState> e : out.entrySet()) {
                long k = e.getKey();
                int x = (int) (k >>> 24) - 2048;
                int y = (int) ((k >>> 12) & 0xfff) - 2048;
                int z = (int) (k & 0xfff) - 2048;
                BlockState s = e.getValue();
                plan.blocks.put(at(x, y, z), s.rotate(rotation));
                if (y <= -4) {
                    long col = key(x, 0, z);
                    top.merge(col, y, Math::max);
                }
            }
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            for (Map.Entry<Long, Integer> e : top.entrySet()) {
                long k = e.getKey();
                int x = (int) (k >>> 24) - 2048;
                int z = (int) (k & 0xfff) - 2048;
                for (int y = e.getValue() + 1; y <= Math.min(e.getValue() + 2, -3); y++) {
                    if (!out.containsKey(key(x, y, z))) {
                        plan.fill.put(at(x, y, z), dirt);
                    }
                }
            }
            for (long[] w : walks) {
                plan.walkways.add(at((int) w[0], (int) w[1], (int) w[2]));
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
