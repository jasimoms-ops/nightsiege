package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The war camp's blocks (concept 27 October 2026, style A "Illager-Feldlager": rough dark
 * wood, iron, rope, rags, barrels, grey banners). A round palisade of about 31 blocks with a
 * gate of five towards the core, the workshop of 9 x 9 in the middle with the siege
 * workbench, four tents and two signal fires whose smoke rises far.
 *
 * <p>Everything follows from the middle, the gate side and a seed, so the save only keeps
 * those and the same blocks can be taken away again when the camp goes.</p>
 */
final class CampLayout {

    /** Radius of the palisade ring (blocks from the middle). */
    static final double PALISADE = 15.0;
    /** Everything closer than this counts as inside the camp. */
    static final double INSIDE = 14.4;
    /** Radius of the levelled ground. */
    static final int GROUND = 17;
    /** Height above the floor that is cleared (trees, hills). */
    static final int CLEAR = 14;

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

    /** Where the siege workbench stands (the middle of its hit box). */
    static BlockPos workbench(BlockPos center) {
        return center.above();
    }

    /** A spot just outside the gate (where the fallen are put down and the way in starts). */
    static BlockPos outsideGate(BlockPos center, int facing) {
        return world(center, facing, 0, 0, 19);
    }

    /** Spots where the guards stand at first: by the fires, the tents, the gate and the workshop. */
    static List<BlockPos> guardSpots(BlockPos center, int facing) {
        int[][] spots = {
                {-6, -10}, {6, -10}, {-4, -12}, {4, -12},
                {-6, -2}, {6, -2}, {-6, 3}, {6, 3},
                {-3, 11}, {3, 11}, {0, 8}, {-2, 6}, {2, 6},
                {-9, 0}, {9, 0}, {0, -7}, {-11, -3}, {11, -3}, {-11, 3}, {11, 3}
        };
        List<BlockPos> out = new ArrayList<>();
        for (int[] s : spots) {
            out.add(world(center, facing, s[0], 0, s[1]));
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
        return out;
    }

    private static boolean isFire(int lx, int lz) {
        return lz == -11 && (lx == -6 || lx == 6);
    }

    /** Every block of the camp above the floor, bottom up. */
    static Map<BlockPos, BlockState> blocks(BlockPos center, int facing, long seed) {
        Builder b = new Builder(center, facing);
        Random random = new Random(seed);
        palisade(b, random);
        workshop(b);
        tent(b, random, 8, -7, 1);
        tent(b, random, 8, 6, 1);
        tent(b, random, -8, -7, -1);
        tent(b, random, -8, 6, -1);
        fires(b);
        clutter(b, random);
        return b.out;
    }

    // ------------------------------------------------------------------ parts

    private static void palisade(Builder b, Random random) {
        for (int lx = -16; lx <= 16; lx++) {
            for (int lz = -16; lz <= 16; lz++) {
                double d = Math.sqrt(lx * lx + lz * lz);
                if (d < PALISADE - 0.5 || d >= PALISADE + 0.5) {
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
                    b.put(lx, h, lz, Blocks.GRAY_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
                } else {
                    b.put(lx, h, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());   // sharpened stakes
                }
            }
        }
        // The beam over the gate, with a lantern under it.
        for (int lx = -3; lx <= 3; lx++) {
            b.put(lx, 5, 15, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        b.put(0, 4, 15, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    private static void workshop(Builder b) {
        // Corner posts and the beams on top.
        for (int sx : new int[] {-4, 4}) {
            for (int sz : new int[] {-4, 4}) {
                for (int ly = 0; ly < 4; ly++) {
                    b.put(sx, ly, sz, Blocks.DARK_OAK_LOG.defaultBlockState());
                }
            }
        }
        for (int lx = -4; lx <= 4; lx++) {
            b.put(lx, 4, -4, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
            b.put(lx, 4, 4, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        for (int lz = -3; lz <= 3; lz++) {
            b.put(-4, 4, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
            b.put(4, 4, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
        }
        // A canvas over the back half (rags in it).
        for (int lx = -3; lx <= 3; lx++) {
            for (int lz = -3; lz <= 0; lz++) {
                Block cloth = (lx * 7 + lz * 3 & 7) == 0 ? Blocks.BROWN_WOOL : (lx + lz & 1) == 0 ? Blocks.GRAY_WOOL : Blocks.LIGHT_GRAY_WOOL;
                b.put(lx, 5, lz, cloth.defaultBlockState());
            }
        }
        b.put(-4, 5, -4, Blocks.BLACK_BANNER.defaultBlockState());
        b.put(4, 5, -4, Blocks.BLACK_BANNER.defaultBlockState());
        b.put(-4, 5, 4, Blocks.GRAY_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        b.put(4, 5, 4, Blocks.GRAY_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        b.put(0, 3, 4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        b.put(-2, 3, -4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        b.put(2, 3, -4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));

        // The siege workbench: a heavy block of beams with tools and a half-built throwing arm.
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                b.put(lx, 0, lz, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            }
        }
        b.put(-1, 1, -1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.put(0, 1, -1, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        b.put(1, 1, -1, Blocks.SMITHING_TABLE.defaultBlockState());
        b.put(-1, 1, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(1, 1, 1, Blocks.IRON_BLOCK.defaultBlockState());
        b.put(-1, 1, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.put(1, 1, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.put(0, 1, 0, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.put(0, 2, 0, Blocks.DARK_OAK_FENCE.defaultBlockState());
        for (int lz = -2; lz <= 2; lz++) {
            b.put(0, 3, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));   // the arm
        }
        b.put(0, 2, 2, Blocks.IRON_CHAIN.defaultBlockState());
        b.put(0, 1, 2, Blocks.COBBLESTONE.defaultBlockState());   // the counterweight on its chain
        b.put(0, 2, -2, Blocks.IRON_CHAIN.defaultBlockState());

        // Barrels, beams and hay around it.
        b.put(3, 0, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(3, 1, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(2, 0, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.put(-3, 0, -3, Blocks.HAY_BLOCK.defaultBlockState());
        for (int lz = 1; lz <= 3; lz++) {
            b.put(-3, 0, lz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
            b.put(-2, 0, lz, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
        b.put(-3, 1, 2, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
        b.put(3, 0, 2, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        b.put(3, 0, 3, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    /** A tent of rags, its opening towards the middle (side +1: east of the workshop, -1: west). */
    private static void tent(Builder b, Random random, int x0, int z0, int side) {
        Block[] cloth = {Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.BROWN_WOOL, Blocks.GRAY_WOOL};
        Block main = random.nextBoolean() ? Blocks.GRAY_WOOL : Blocks.LIGHT_GRAY_WOOL;
        for (int depth = 0; depth < 5; depth++) {
            int lx = x0 + side * depth;
            Block stripe = depth == 2 ? cloth[random.nextInt(cloth.length)] : main;
            b.put(lx, 0, z0 - 2, stripe.defaultBlockState());
            b.put(lx, 0, z0 + 2, stripe.defaultBlockState());
            b.put(lx, 1, z0 - 1, stripe.defaultBlockState());
            b.put(lx, 1, z0 + 1, stripe.defaultBlockState());
            b.put(lx, 2, z0, stripe.defaultBlockState());
        }
        int back = x0 + side * 4;
        b.put(back, 0, z0 - 1, main.defaultBlockState());
        b.put(back, 0, z0, main.defaultBlockState());
        b.put(back, 0, z0 + 1, main.defaultBlockState());
        b.put(back, 1, z0, main.defaultBlockState());
        // The ridge pole in front, a bedroll and a barrel inside.
        b.put(x0 - side, 0, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.put(x0 - side, 1, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.put(x0 - side, 2, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.put(x0 + side, 0, z0 - 1, Blocks.BROWN_CARPET.defaultBlockState());
        b.put(x0 + side * 2, 0, z0 - 1, Blocks.BROWN_CARPET.defaultBlockState());
        b.put(x0 + side * 3, 0, z0 + 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    private static void fires(Builder b) {
        for (int lx : new int[] {-6, 6}) {
            b.put(lx, 0, -11, Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(BlockStateProperties.SIGNAL_FIRE, true)
                    .setValue(BlockStateProperties.LIT, true));
            // Logs to sit on.
            b.put(lx - 2, 0, -11, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            b.put(lx + 2, 0, -11, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
    }

    private static void clutter(Builder b, Random random) {
        // Lantern posts by the gate and the tents.
        int[][] posts = {{-3, 12}, {3, 12}, {-6, 0}, {6, 0}, {0, -12}};
        for (int[] p : posts) {
            b.put(p[0], 0, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            b.put(p[0], 1, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            b.put(p[0], 2, p[1], Blocks.LANTERN.defaultBlockState());
        }
        // Barrels and hay along the palisade.
        int[][] barrels = {{-10, -9}, {-9, -10}, {10, 9}, {11, 8}, {-12, 7}, {12, -6}};
        for (int[] p : barrels) {
            b.put(p[0], 0, p[1], random.nextInt(3) == 0 ? Blocks.HAY_BLOCK.defaultBlockState()
                    : Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        }
        // A rack of beams for the machines by the gate.
        for (int lx = 5; lx <= 8; lx++) {
            b.put(lx, 0, 11, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        b.put(6, 1, 11, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.put(7, 1, 11, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.put(-7, 0, 10, Blocks.IRON_BLOCK.defaultBlockState());
        b.put(-6, 0, 10, Blocks.COBBLESTONE.defaultBlockState());
        b.put(-7, 1, 10, Blocks.COBBLESTONE_SLAB.defaultBlockState());
    }

    private static BlockState axis(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
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
