package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Random;

/** The reward of a special night held: a chest of loot next to the core. */
final class SpecialNight {

    static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath("nightsiege", "chests/special_night"));

    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    private SpecialNight() {
    }

    static void lootChest(ServerLevel level, BlockPos core, Random random) {
        BlockPos spot = null;
        for (int r = 1; r <= 3 && spot == null; r++) {
            for (int[] d : AROUND) {
                for (int dy = 0; dy <= 1 && spot == null; dy++) {
                    BlockPos at = core.offset(d[0] * r, dy, d[1] * r);
                    if (free(level, at)) {
                        spot = at;
                    }
                }
                if (spot != null) {
                    break;
                }
            }
        }
        if (spot == null) {
            spot = core.above();
        }
        level.setBlockAndUpdate(spot, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT, random.nextLong());
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5,
                60, 0.5, 0.8, 0.5, 0.3);
        level.playSound(null, spot, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static boolean free(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        BlockPos below = at.below();
        return state.canBeReplaced() && state.getFluidState().isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
