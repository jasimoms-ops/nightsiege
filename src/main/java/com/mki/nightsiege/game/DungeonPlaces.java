package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Finding places inside a generated structure: where its core stands and where
 * its guards wait. Everything is read from the blocks as they stand in the world.
 *
 * <p>The core wants a real room: solid floor under a free 3×3 patch with three
 * blocks of headroom, a roof over it, no door, ladder or trapdoor within two
 * blocks. Of all such places the lowest one wins (the deepest room), bigger rooms
 * first on the same floor. Only structures without any roofed room (open ruins)
 * fall back to the lowest open floor.</p>
 */
final class DungeonPlaces {

    /** Smallest patch of floor around the core (cells of 7×7) that counts as a room. */
    private static final int MIN_ROOM = 12;

    private DungeonPlaces() {
    }

    /** The boxes of the structure's pieces (or its whole box when it has none). */
    static List<BoundingBox> boxes(StructureStart start) {
        List<BoundingBox> out = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            out.add(piece.getBoundingBox());
        }
        if (out.isEmpty()) {
            out.add(start.getBoundingBox());
        }
        return out;
    }

    /** Whether every chunk the structure covers is loaded, so its blocks can be read. */
    static boolean loaded(ServerLevel level, List<BoundingBox> boxes) {
        for (BoundingBox box : boxes) {
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The core's place, or null when the structure has no floor at all. */
    static BlockPos coreSpot(ServerLevel level, List<BoundingBox> boxes) {
        BlockPos best = find(level, boxes, true);
        return best != null ? best : find(level, boxes, false);
    }

    private static BlockPos find(ServerLevel level, List<BoundingBox> boxes, boolean roofed) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BoundingBox box : boxes) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!standable(level, p, 3) || !coreFits(level, p, roofed)) {
                            continue;
                        }
                        int room = roomSize(level, p);
                        if (room < MIN_ROOM) {
                            continue;
                        }
                        // Deepest first; on the same floor the bigger room, then the middle of it.
                        int score = -y * 100 + Math.min(room, 49);
                        if (score > bestScore) {
                            bestScore = score;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Free spots for the guards, spread over the structure and away from {@code avoid}. */
    static List<BlockPos> guardSpots(ServerLevel level, List<BoundingBox> boxes, int count, BlockPos avoid, Random random) {
        List<BlockPos> out = new ArrayList<>();
        long volume = 0;
        for (BoundingBox box : boxes) {
            volume += (long) box.getXSpan() * box.getZSpan();
        }
        for (int attempt = 0; attempt < count * 60 && out.size() < count && volume > 0; attempt++) {
            // A box picked by its size, then a column in it, then a floor in the column.
            long pick = (long) (random.nextDouble() * volume);
            BoundingBox box = boxes.get(0);
            for (BoundingBox b : boxes) {
                long size = (long) b.getXSpan() * b.getZSpan();
                if (pick < size) {
                    box = b;
                    break;
                }
                pick -= size;
            }
            int x = box.minX() + random.nextInt(Math.max(1, box.getXSpan()));
            int z = box.minZ() + random.nextInt(Math.max(1, box.getZSpan()));
            List<BlockPos> floors = new ArrayList<>();
            for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (standable(level, p, 2)) {
                    floors.add(p);
                }
            }
            if (floors.isEmpty()) {
                continue;
            }
            BlockPos p = floors.get(random.nextInt(floors.size()));
            if (avoid != null && p.distSqr(avoid) < 36) {
                continue;
            }
            boolean crowded = false;
            for (BlockPos other : out) {
                if (other.distSqr(p) < 9) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                out.add(p);
            }
        }
        return out;
    }

    /** Solid floor below and {@code height} free blocks from here up. */
    static boolean standable(ServerLevel level, BlockPos p, int height) {
        BlockPos below = p.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || doorish(floor.getBlock())) {
            return false;
        }
        for (int i = 0; i < height; i++) {
            if (!open(level, p.above(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean coreFits(ServerLevel level, BlockPos p, boolean roofed) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !standable(level, p.offset(dx, 0, dz), 2)) {
                    return false;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (doorish(level.getBlockState(p.offset(dx, dy, dz)).getBlock())) {
                        return false;
                    }
                }
            }
        }
        if (!roofed) {
            return true;
        }
        if (level.canSeeSky(p.above(2))) {
            return false;
        }
        for (int dy = 3; dy <= 10; dy++) {
            if (!open(level, p.above(dy))) {
                return true;   // a ceiling not far above: a room, not a courtyard
            }
        }
        return false;
    }

    /** Floor cells around (7×7) on the same level: how much of a room this is. */
    private static int roomSize(ServerLevel level, BlockPos p) {
        int count = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (standable(level, p.offset(dx, 0, dz), 2)) {
                    count++;
                }
            }
        }
        return count;
    }

    static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().getType() != Fluids.LAVA
                && state.getFluidState().getType() != Fluids.FLOWING_LAVA && !doorish(state.getBlock());
    }

    private static boolean doorish(Block block) {
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof LadderBlock;
    }
}
