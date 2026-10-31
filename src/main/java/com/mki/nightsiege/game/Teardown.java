package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Survival safety (Simon 31.10.): what the teardown of a dungeon or of the war camp leaves standing.
 * Both put back, spot by spot, what stood there before they were built; a player's things in such a
 * place must not vanish with it.
 */
final class Teardown {

    private Teardown() {
    }

    /**
     * May this spot go back to what stood there before the dungeon or the camp? Not when a player's
     * thing stands there now: a container with something in it, a nether portal or its frame (one the
     * game set up on its own can land there), or a block that is neither the old one nor one the
     * dungeon or camp set there itself. Empty spots, plants, fluids and fire go back as before.
     *
     * @param ours true for what the dungeon or the camp itself set at this spot (null: not known; then
     *             only containers with contents and portals stay)
     */
    static boolean mayReset(ServerLevel level, BlockPos pos, BlockState old, Predicate<BlockState> ours) {
        BlockState now = level.getBlockState(pos);
        if (now.is(Blocks.NETHER_PORTAL) || (now.is(Blocks.OBSIDIAN) || now.is(Blocks.CRYING_OBSIDIAN)) && nextToPortal(level, pos)) {
            return false;
        }
        if (now.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container && !container.isEmpty()) {
            return false;   // a chest with something in it stays where it is
        }
        if (now.isAir() || now.canBeReplaced() || !now.getFluidState().isEmpty() || now.getBlock() instanceof BaseFireBlock) {
            return true;
        }
        if (old != null && now.is(old.getBlock())) {
            return true;
        }
        return ours == null || ours.test(now);
    }

    private static boolean nextToPortal(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(Blocks.NETHER_PORTAL)) {
                return true;
            }
        }
        return false;
    }
}
