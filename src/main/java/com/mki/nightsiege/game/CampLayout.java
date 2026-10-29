package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The war camp's blocks (concept 27 October 2026, style A "Illager-Feldlager": rough dark
 * wood, iron, rope, rags, barrels, grey banners).
 *
 * <p>Content update (29 October 2026, the board "Heerlager mit Startbereich"): about 41 blocks
 * across. A fenced forecourt before the gate with the war drum and the waiting place of the
 * fallen; the outer palisade with a walkway; in the outer ring four tents in three colours (two
 * guards each), two watchtowers with crossbowmen at the back, cooking fires, weapon racks,
 * provisions and barricades before the inner ring; the inner ring opens when the tent guards
 * have fallen, the siege workbench stands on a podium in the middle.</p>
 *
 * <p>Everything follows from the middle, the gate side and a seed, so the save only keeps
 * those and the same blocks can be taken away again when the camp goes.</p>
 */
final class CampLayout {

    /** Radius of the outer palisade ring (blocks from the middle). */
    static final double PALISADE = 18.0;
    /** Everything closer than this counts as inside the camp. */
    static final double INSIDE = 17.4;
    /** Radius of the inner palisade ring round the workshop. */
    static final double INNER = 9.0;
    /** Radius of the levelled ground. */
    static final int GROUND = 20;
    /** Height above the floor that is cleared (trees, hills). */
    static final int CLEAR = 14;
    /** The forecourt before the gate (layout space): x from -FORE_HALF to FORE_HALF, z from FORE_NEAR to FORE_FAR. */
    static final int FORE_HALF = 6;
    static final int FORE_NEAR = 18;
    static final int FORE_FAR = 28;
    /** The gate rows: the outer palisade's and the inner ring's (layout z). */
    static final int GATE_Z = 18;
    static final int INNER_GATE_Z = 9;

    /** The tents: front x, middle z, side (+1 east: the tent reaches outwards to +x). */
    private static final int[][] TENTS = {{11, -3, 1}, {11, 5, 1}, {-11, -3, -1}, {-11, 5, -1}};
    /** The watchtowers' middles (layout x, z). */
    private static final int[][] TOWERS = {{-10, -10}, {10, -10}};
    /** Top of a watchtower's platform, where its crossbowmen stand. */
    static final int TOWER_FLOOR = 6;

    private CampLayout() {
    }

    /** Rotation that turns the layout (gate towards +Z) to the gate side 0 south, 1 west, 2 north, 3 east. */
    static Rotation rotation(int facing) {
        return switch (facing & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The gate side for a direction (towards the core). */
    static int facingTowards(double dx, double dz) {
        Direction d = Direction.getApproximateNearest(dx, 0, dz);
        return switch (d) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** A point of the layout (x to the side, z towards the gate) in the world. */
    static BlockPos world(BlockPos center, int facing, int lx, int ly, int lz) {
        return switch (facing & 3) {
            case 1 -> center.offset(-lz, ly, lx);
            case 2 -> center.offset(-lx, ly, -lz);
            case 3 -> center.offset(lz, ly, -lx);
            default -> center.offset(lx, ly, lz);
        };
    }

    /** Where the siege workbench stands (the middle of its hit box): on the podium. */
    static BlockPos workbench(BlockPos center) {
        return center.above(2);
    }

    /** A spot just outside the gate, in the forecourt (where the way in starts). */
    static BlockPos outsideGate(BlockPos center, int facing) {
        return world(center, facing, 0, 0, 22);
    }

    /** The war drum in the forecourt: a right click starts the ready round. */
    static BlockPos drum(BlockPos center, int facing) {
        return world(center, facing, 3, 0, 20);
    }

    /** Where the fallen wait: by the fire in the forecourt. */
    static BlockPos waiting(BlockPos center, int facing) {
        return world(center, facing, -3, 0, 26);
    }

    /** Just outside the forecourt's way in (who is put out of it stands here). */
    static BlockPos beforeForecourt(BlockPos center, int facing) {
        return world(center, facing, 0, 0, FORE_FAR + 3);
    }

    /** The five-wide gate in the palisade, up to under its beam (iron bars close it in a fight). */
    static List<BlockPos> gate(BlockPos center, int facing) {
        List<BlockPos> out = new ArrayList<>();
        for (int lx = -2; lx <= 2; lx++) {
            for (int ly = 0; ly <= 4; ly++) {
                out.add(world(center, facing, lx, ly, GATE_Z));
            }
        }
        return out;
    }

    /** The forecourt's way in (the second portcullis: iron bars close it in a fight). */
    static List<BlockPos> forecourtGate(BlockPos center, int facing) {
        List<BlockPos> out = new ArrayList<>();
        for (int lx = -1; lx <= 2; lx++) {
            for (int ly = 0; ly <= 2; ly++) {
                out.add(world(center, facing, lx, ly, FORE_FAR));
            }
        }
        return out;
    }

    /** The inner ring's gate: iron bars that go when the tent guards have fallen. */
    static List<BlockPos> innerGate(BlockPos center, int facing) {
        List<BlockPos> out = new ArrayList<>();
        for (int lx = -1; lx <= 1; lx++) {
            for (int ly = 0; ly <= 2; ly++) {
                out.add(world(center, facing, lx, ly, INNER_GATE_Z));
            }
        }
        return out;
    }

    /** Where the ready round's fighters stand when the fight begins: just inside the gate. */
    static BlockPos insideGate(BlockPos center, int facing, int index) {
        int lx = (index % 5) - 2;
        int lz = 15 - (index / 5);
        return world(center, facing, lx, 0, lz);
    }

    /** A world point back in layout space: {x to the side, z towards the gate}. */
    static int[] local(BlockPos center, int facing, BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        return switch (facing & 3) {
            case 1 -> new int[] {dz, -dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {-dz, dx};
            default -> new int[] {dx, dz};
        };
    }

    /** True for a point of the forecourt (layout space, the fence included). */
    static boolean inForecourt(int lx, int lz) {
        return Math.abs(lx) <= FORE_HALF && lz >= FORE_NEAR && lz <= FORE_FAR;
    }

    /** True for a point inside the forecourt's fence (layout space). */
    static boolean insideForecourt(int lx, int lz) {
        return Math.abs(lx) < FORE_HALF && lz > FORE_NEAR && lz < FORE_FAR;
    }

    /** Spots in front of each tent where its guards stand (two per tent, in tent order). */
    static List<BlockPos> tentSpots(BlockPos center, int facing) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] t : TENTS) {
            out.add(world(center, facing, t[0] - t[2], 0, t[1] - 1));
            out.add(world(center, facing, t[0] - t[2], 0, t[1] + 1));
        }
        return out;
    }

    /** Where the crossbowmen stand: two on each watchtower's platform. */
    static List<BlockPos> towerSpots(BlockPos center, int facing) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            for (int[] t : TOWERS) {
                out.add(world(center, facing, t[0] + (i == 0 ? -1 : 1), TOWER_FLOOR, t[1] - 1));
            }
        }
        return out;
    }

    /** The ground: one block under the floor height, coarse earth with paths, planks in the workshop. */
    static Map<BlockPos, BlockState> floor(BlockPos center, int facing, long seed) {
        Random random = new Random(seed ^ 0x5DEECE66DL);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (int lx = -GROUND; lx <= GROUND; lx++) {
            for (int lz = -GROUND; lz <= GROUND; lz++) {
                double d = Math.sqrt(lx * lx + lz * lz);
                if (d > GROUND + 0.5) {
                    continue;
                }
                BlockState state;
                if (d >= PALISADE + 0.5) {
                    state = Blocks.GRASS_BLOCK.defaultBlockState();
                } else if (Math.abs(lx) <= 4 && Math.abs(lz) <= 4) {
                    state = Blocks.SPRUCE_PLANKS.defaultBlockState();
                } else if (Math.abs(lx) <= 1 && lz >= 5) {
                    state = Blocks.DIRT_PATH.defaultBlockState();
                } else if (isFire(lx, lz)) {
                    state = Blocks.HAY_BLOCK.defaultBlockState();
                } else if (d < INNER - 0.5) {
                    int r = random.nextInt(100);
                    state = r < 45 ? Blocks.GRAVEL.defaultBlockState()
                            : r < 80 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
                } else {
                    int r = random.nextInt(100);
                    state = r < 52 ? Blocks.COARSE_DIRT.defaultBlockState()
                            : r < 72 ? Blocks.DIRT.defaultBlockState()
                            : r < 84 ? Blocks.PODZOL.defaultBlockState()
                            : r < 94 ? Blocks.GRAVEL.defaultBlockState()
                            : Blocks.ROOTED_DIRT.defaultBlockState();
                }
                out.put(world(center, facing, lx, -1, lz), state);
            }
        }
        // The forecourt: trodden earth, a path to the gate, planks under the drum.
        for (int lx = -FORE_HALF; lx <= FORE_HALF; lx++) {
            for (int lz = FORE_NEAR; lz <= FORE_FAR; lz++) {
                if (Math.sqrt(lx * lx + lz * lz) < PALISADE + 0.5) {
                    continue;   // the palisade and its gate
                }
                BlockState state;
                if (lx == 0 || lx == 1) {
                    state = Blocks.DIRT_PATH.defaultBlockState();
                } else if (lx >= 2 && lx <= 4 && lz >= 19 && lz <= 21) {
                    state = Blocks.SPRUCE_PLANKS.defaultBlockState();
                } else {
                    int r = random.nextInt(100);
                    state = r < 60 ? Blocks.COARSE_DIRT.defaultBlockState()
                            : r < 82 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                }
                out.put(world(center, facing, lx, -1, lz), state);
            }
        }
        return out;
    }

    private static boolean isFire(int lx, int lz) {
        return lz == -14 && (lx == -5 || lx == 5);
    }

    /** Every block of the camp above the floor, bottom up. */
    static Map<BlockPos, BlockState> blocks(BlockPos center, int facing, long seed) {
        Builder b = new Builder(center, facing);
        Random random = new Random(seed);
        palisade(b, random);
        walkway(b);
        innerRing(b);
        workshop(b);
        int[] colours = tentColours(random);
        for (int i = 0; i < TENTS.length; i++) {
            tent(b, random, TENTS[i][0], TENTS[i][1], TENTS[i][2], colours[i]);
        }
        for (int[] t : TOWERS) {
            tower(b, t[0], t[1]);
        }
        fires(b);
        cooking(b);
        barricades(b);
        racks(b);
        clutter(b, random);
        forecourt(b);
        return b.out;
    }

    /** Three colours on four tents: each once, one twice. */
    private static int[] tentColours(Random random) {
        int[] c = {0, 1, 2, random.nextInt(3)};
        for (int i = c.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return c;
    }

    /**
     * The forecourt before the gate: a low fence with a way in towards the core (a portcullis
     * closes it in a fight), the war drum on its stand by the gate, and benches round a fire
     * where the fallen wait.
     */
    private static void forecourt(Builder b) {
        for (int lz = FORE_NEAR; lz <= FORE_FAR; lz++) {
            b.put(-FORE_HALF, 0, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.put(FORE_HALF, 0, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        for (int lx = -FORE_HALF + 1; lx <= FORE_HALF - 1; lx++) {
            if (lx < -1 || lx > 2) {
                b.put(lx, 0, FORE_FAR, Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
        }
        // Posts with lanterns at the way in, a beam over it for the portcullis.
        for (int lx : new int[] {-2, 3}) {
            for (int ly = 0; ly <= 2; ly++) {
                b.put(lx, ly, FORE_FAR, Blocks.DARK_OAK_LOG.defaultBlockState());
            }
            b.put(lx, 4, FORE_FAR, Blocks.LANTERN.defaultBlockState());
        }
        for (int lx = -2; lx <= 3; lx++) {
            b.put(lx, 3, FORE_FAR, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        // The war drum: a barrel standing up between two posts with lanterns.
        b.put(3, 0, 20, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        for (int lx : new int[] {2, 4}) {
            b.put(lx, 0, 20, Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.put(lx, 1, 20, Blocks.LANTERN.defaultBlockState());
        }
        // The waiting place: a fire and two benches.
        b.put(-3, 0, 24, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true)
                .setValue(BlockStateProperties.SIGNAL_FIRE, false));
        for (int lz = 23; lz <= 25; lz++) {
            b.put(-5, 0, lz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            b.put(-1, 0, lz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }
        b.put(-4, 0, 27, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(-5, 0, 27, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(5, 0, 27, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(5, 1, 27, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(4, 0, 27, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    // ------------------------------------------------------------------ parts

    private static boolean ring(int lx, int lz, double radius) {
        double d = Math.sqrt(lx * lx + lz * lz);
        return d >= radius - 0.5 && d < radius + 0.5;
    }

    private static boolean inTower(int lx, int lz) {
        for (int[] t : TOWERS) {
            if (Math.abs(lx - t[0]) <= 2 && Math.abs(lz - t[1]) <= 3) {
                return true;
            }
        }
        return false;
    }

    private static void palisade(Builder b, Random random) {
        int r = (int) PALISADE + 1;
        for (int lx = -r; lx <= r; lx++) {
            for (int lz = -r; lz <= r; lz++) {
                if (!ring(lx, lz, PALISADE)) {
                    continue;
                }
                if (lz > 0 && Math.abs(lx) <= 2) {
                    continue;   // the gate
                }
                boolean post = lz > 0 && Math.abs(lx) == 3;
                int h = post ? 6 : 4 + ((lx * 31 + lz * 17) & 1);
                Block log = random.nextInt(10) < 7 ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG;
                for (int ly = 0; ly < h; ly++) {
                    b.put(lx, ly, lz, log.defaultBlockState());
                }
                if (post) {
                    b.put(lx, h, lz, colored("gray_banner").defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
                } else {
                    b.put(lx, h, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());   // sharpened stakes
                }
            }
        }
        // The beam over the gate, with a lantern under it.
        for (int lx = -3; lx <= 3; lx++) {
            b.put(lx, 5, GATE_Z, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        b.put(0, 4, GATE_Z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    /**
     * The walkway inside the outer palisade: planks on top at the height of the stakes, ladders
     * up at the back and on both sides (archers and engineers stand up there).
     */
    private static void walkway(Builder b) {
        int[][] ladders = {{0, -17, 0, 1}, {-17, 0, 1, 0}, {17, 0, -1, 0}};   // column x, z and the way to the ladder
        int r = (int) PALISADE;
        for (int lx = -r; lx <= r; lx++) {
            for (int lz = -r; lz <= r; lz++) {
                if (!ring(lx, lz, PALISADE - 1.0) || lz > 0 && Math.abs(lx) <= 4 || inTower(lx, lz)) {
                    continue;
                }
                boolean column = false;
                for (int[] l : ladders) {
                    column |= l[0] == lx && l[1] == lz;
                }
                if (column) {
                    continue;
                }
                b.put(lx, 2, lz, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
                if (((lx * 7 + lz * 13) % 6 + 6) % 6 == 0) {
                    b.put(lx, 0, lz, Blocks.SPRUCE_FENCE.defaultBlockState());   // a prop under the planks
                    b.put(lx, 1, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
        for (int[] l : ladders) {
            for (int ly = 0; ly <= 2; ly++) {
                b.put(l[0], ly, l[1], Blocks.DARK_OAK_LOG.defaultBlockState());
                b.put(l[0] + l[2], ly, l[1] + l[3], Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, Direction.getApproximateNearest(l[2], 0, l[3])));
            }
        }
    }

    /** The inner ring: a lower palisade round the workshop, its gate shut by iron bars at first. */
    private static void innerRing(Builder b) {
        int r = (int) INNER + 1;
        for (int lx = -r; lx <= r; lx++) {
            for (int lz = -r; lz <= r; lz++) {
                if (!ring(lx, lz, INNER)) {
                    continue;
                }
                if (lz > 0 && Math.abs(lx) <= 1) {
                    continue;   // the gate
                }
                boolean post = lz > 0 && Math.abs(lx) == 2;
                int h = post ? 4 : 3;
                Block log = ((lx * 13 + lz * 7) & 3) == 0 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG;
                for (int ly = 0; ly < h; ly++) {
                    b.put(lx, ly, lz, log.defaultBlockState());
                }
                if (post) {
                    b.put(lx, h, lz, colored("black_banner").defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
                } else {
                    b.put(lx, h, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
            }
        }
        for (int lx = -1; lx <= 1; lx++) {
            b.put(lx, 3, INNER_GATE_Z, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
            for (int ly = 0; ly <= 2; ly++) {
                b.put(lx, ly, INNER_GATE_Z, Blocks.IRON_BARS.defaultBlockState());
            }
        }
    }

    private static void workshop(Builder b) {
        // The podium: stone, steps on all four sides.
        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                boolean edge = Math.abs(lx) == 2 || Math.abs(lz) == 2;
                b.put(lx, 0, lz, edge ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        for (int i = -1; i <= 1; i++) {
            b.put(i, 0, 3, stairs(Direction.NORTH));
            b.put(i, 0, -3, stairs(Direction.SOUTH));
            b.put(3, 0, i, stairs(Direction.WEST));
            b.put(-3, 0, i, stairs(Direction.EAST));
        }
        // Corner posts and the beams on top.
        for (int sx : new int[] {-4, 4}) {
            for (int sz : new int[] {-4, 4}) {
                for (int ly = 0; ly < 5; ly++) {
                    b.put(sx, ly, sz, Blocks.DARK_OAK_LOG.defaultBlockState());
                }
            }
        }
        for (int lx = -4; lx <= 4; lx++) {
            b.put(lx, 5, -4, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
            b.put(lx, 5, 4, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        for (int lz = -3; lz <= 3; lz++) {
            b.put(-4, 5, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
            b.put(4, 5, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
        }
        // A canvas over the back half (rags in it).
        for (int lx = -3; lx <= 3; lx++) {
            for (int lz = -3; lz <= 0; lz++) {
                Block cloth = (lx * 7 + lz * 3 & 7) == 0 ? colored("brown_wool") : (lz & 1) == 0 ? colored("gray_wool") : colored("light_gray_wool");
                b.put(lx, 6, lz, cloth.defaultBlockState());
            }
        }
        b.put(-4, 6, -4, colored("black_banner").defaultBlockState());
        b.put(4, 6, -4, colored("black_banner").defaultBlockState());
        b.put(-4, 6, 4, colored("gray_banner").defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        b.put(4, 6, 4, colored("gray_banner").defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        b.put(0, 4, 4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        b.put(-2, 4, -4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        b.put(2, 4, -4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));

        // The siege workbench on the podium: a heavy block of beams with tools and a half-built throwing arm.
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                b.put(lx, 1, lz, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            }
        }
        b.put(-1, 2, -1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.put(0, 2, -1, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        b.put(1, 2, -1, Blocks.SMITHING_TABLE.defaultBlockState());
        b.put(-1, 2, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(1, 2, 1, Blocks.IRON_BLOCK.defaultBlockState());
        b.put(-1, 2, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.put(1, 2, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.put(0, 2, 0, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.put(0, 3, 0, Blocks.DARK_OAK_FENCE.defaultBlockState());
        for (int lz = -2; lz <= 2; lz++) {
            b.put(0, 4, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));   // the arm
        }
        b.put(0, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
        b.put(0, 2, 2, Blocks.COBBLESTONE.defaultBlockState());   // the counterweight on its chain
        b.put(0, 3, -2, Blocks.IRON_CHAIN.defaultBlockState());

        // Barrels and hay at the workshop's corners.
        b.put(3, 0, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(3, 1, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(-3, 0, -3, Blocks.HAY_BLOCK.defaultBlockState());
        b.put(3, 0, 3, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        b.put(-3, 0, 3, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    /**
     * A tent of rags, its opening towards the middle (side +1: east of the workshop, -1: west).
     * Colour 0 grey, 1 brown and white, 2 red and black.
     */
    private static void tent(Builder b, Random random, int x0, int z0, int side, int colour) {
        Block[][] palettes = {
                {colored("gray_wool"), colored("light_gray_wool")},
                {colored("brown_wool"), colored("white_wool")},
                {colored("red_wool"), colored("black_wool")}};
        Block main = palettes[colour][0];
        Block stripe = palettes[colour][1];
        for (int depth = 0; depth < 5; depth++) {
            int lx = x0 + side * depth;
            Block cloth = depth == 1 || depth == 3 ? stripe : main;
            b.put(lx, 0, z0 - 2, cloth.defaultBlockState());
            b.put(lx, 0, z0 + 2, cloth.defaultBlockState());
            b.put(lx, 1, z0 - 1, cloth.defaultBlockState());
            b.put(lx, 1, z0 + 1, cloth.defaultBlockState());
            b.put(lx, 2, z0, cloth.defaultBlockState());
        }
        int back = x0 + side * 4;
        b.put(back, 0, z0 - 1, main.defaultBlockState());
        b.put(back, 0, z0, main.defaultBlockState());
        b.put(back, 0, z0 + 1, main.defaultBlockState());
        b.put(back, 1, z0, main.defaultBlockState());
        // The ridge pole in front with a banner, a bedroll and a barrel inside.
        b.put(x0 - side, 0, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.put(x0 - side, 1, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.put(x0 - side, 2, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        String banner = colour == 2 ? "red_banner" : colour == 1 ? "brown_banner" : "gray_banner";
        b.put(x0 - side, 3, z0, colored(banner).defaultBlockState().setValue(BlockStateProperties.ROTATION_16, side > 0 ? 12 : 4));
        b.put(x0 + side, 0, z0 - 1, colored(colour == 2 ? "red_carpet" : "brown_carpet").defaultBlockState());
        b.put(x0 + side * 2, 0, z0 - 1, colored(colour == 2 ? "red_carpet" : "brown_carpet").defaultBlockState());
        b.put(x0 + side * 3, 0, z0 + 1, random.nextBoolean() ? Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)
                : Blocks.HAY_BLOCK.defaultBlockState());
    }

    /**
     * A watchtower: four posts, a platform at {@link #TOWER_FLOOR} with a low parapet, a roof, a
     * ladder up on the side towards the camp's gate.
     */
    private static void tower(Builder b, int cx, int cz) {
        for (int dx : new int[] {-2, 2}) {
            for (int dz : new int[] {-2, 2}) {
                for (int ly = 0; ly < 5; ly++) {
                    b.put(cx + dx, ly, cz + dz, Blocks.DARK_OAK_LOG.defaultBlockState());
                }
                for (int ly = 6; ly <= 8; ly++) {
                    b.put(cx + dx, ly, cz + dz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
            }
        }
        for (int ly = 0; ly < 5; ly++) {
            b.put(cx, ly, cz + 2, Blocks.DARK_OAK_LOG.defaultBlockState());   // the ladder's beam
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                b.put(cx + dx, 5, cz + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (edge && !corner && !(dx == 0 && dz == 2)) {
                    b.put(cx + dx, 6, cz + dz, Blocks.SPRUCE_SLAB.defaultBlockState());   // a low parapet: the crossbows shoot over it
                }
                b.put(cx + dx, 9, cz + dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
        }
        b.put(cx, 10, cz, colored("gray_banner").defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        b.put(cx, 8, cz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        for (int ly = 0; ly <= 5; ly++) {
            b.put(cx, ly, cz + 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
    }

    /** The two signal fires behind the workshop: their smoke shows the camp from afar. */
    private static void fires(Builder b) {
        for (int lx : new int[] {-5, 5}) {
            b.put(lx, 0, -14, Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(BlockStateProperties.SIGNAL_FIRE, true)
                    .setValue(BlockStateProperties.LIT, true));
            // Logs to sit on.
            b.put(lx - 2, 0, -14, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            b.put(lx + 2, 0, -14, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
    }

    /** Two cooking fires between the tents, a pot over each. */
    private static void cooking(Builder b) {
        for (int lx : new int[] {-13, 13}) {
            b.put(lx, 0, 1, Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(BlockStateProperties.SIGNAL_FIRE, false)
                    .setValue(BlockStateProperties.LIT, true));
            b.put(lx, 1, 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
            b.put(lx - 2, 0, 1, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            b.put(lx + 2, 0, 1, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
    }

    /** Barricades left and right of the way to the inner ring: cover for the way in. */
    private static void barricades(Builder b) {
        for (int side : new int[] {-1, 1}) {
            for (int i = 3; i <= 6; i++) {
                b.put(side * i, 0, 13, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
                b.put(side * i, 1, 13, Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
            b.put(side * 7, 0, 12, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
    }

    /** A weapon rack and a smith's corner by the inner ring's gate. */
    private static void racks(Builder b) {
        for (int lx = -7; lx <= -4; lx++) {
            boolean post = lx == -7 || lx == -4;
            b.put(lx, 0, 11, post ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
            b.put(lx, 1, 11, post ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
            b.put(lx, 2, 11, Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        b.put(5, 0, 11, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        b.put(6, 0, 11, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        b.put(7, 0, 11, Blocks.SMITHING_TABLE.defaultBlockState());
    }

    private static void clutter(Builder b, Random random) {
        // Lantern posts by the gates and between the tents.
        int[][] posts = {{-3, 16}, {3, 16}, {-3, 10}, {3, 10}, {-9, 1}, {9, 1}, {0, -12}};
        for (int[] p : posts) {
            b.put(p[0], 0, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            b.put(p[0], 1, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            b.put(p[0], 2, p[1], Blocks.LANTERN.defaultBlockState());
        }
        // Provisions: barrels, hay and sacks along the palisade and behind the workshop.
        int[][] stacks = {{-12, 10}, {-11, 11}, {12, 10}, {11, 11}, {-2, -11}, {2, -11}, {-15, -6}, {15, -6},
                {-8, 14}, {8, 14}};
        for (int[] p : stacks) {
            int kind = random.nextInt(4);
            BlockState state = kind == 0 ? Blocks.HAY_BLOCK.defaultBlockState()
                    : kind == 1 ? colored("brown_wool").defaultBlockState()
                    : Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            b.put(p[0], 0, p[1], state);
            if (random.nextBoolean()) {
                b.put(p[0], 1, p[1], Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            }
        }
        // A rack of beams for the machines by the gate.
        for (int lx = 5; lx <= 8; lx++) {
            b.put(lx, 0, 15, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        b.put(6, 1, 15, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.put(7, 1, 15, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.put(-7, 0, 15, Blocks.IRON_BLOCK.defaultBlockState());
        b.put(-6, 0, 15, Blocks.COBBLESTONE.defaultBlockState());
        b.put(-7, 1, 15, Blocks.COBBLESTONE_SLAB.defaultBlockState());
    }

    /** A dyed block by its id (the dyed blocks are no single fields any more). */
    private static Block colored(String id) {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(id));
        return block == null ? Blocks.STONE : block;
    }

    private static BlockState axis(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    /** Collects blocks in layout space and puts them, turned, into the world. */
    private static final class Builder {
        final BlockPos center;
        final int facing;
        final Rotation rotation;
        final Map<BlockPos, BlockState> out = new LinkedHashMap<>();

        Builder(BlockPos center, int facing) {
            this.center = center;
            this.facing = facing;
            this.rotation = rotation(facing);
        }

        void put(int lx, int ly, int lz, BlockState state) {
            out.put(world(center, facing, lx, ly, lz), state.rotate(rotation));
        }
    }
}
