package com.mki.nightsiege.game;

import com.mki.nightsiege.core.BlockGuard;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The punishment for a lost siege: the anchor bed and everything around it is
 * torn apart. Built from plain block removal instead of a vanilla explosion so
 * the protected blocks really do survive.
 */
public final class Demolition {

    private Demolition() {
    }

    public static int blast(ServerLevel world, BlockPos center, SiegeConfig cfg) {
        int radius = cfg.failureBlastRadius;
        if (radius <= 0) {
            return 0;
        }
        int radiusSq = radius * radius;
        int destroyed = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    // Outside the world getBlockState returns air, which is skipped below.
                    BlockPos pos = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // The core survives on purpose: a lost siege resets it
                    // instead of destroying it.
                    if (ModContent.isCore(state)) {
                        continue;
                    }
                    if (cfg.failureBlastProtectsContainers && isProtected(state, cfg)) {
                        continue;
                    }
                    // Bedrock always survives, whatever the settings say.
                    if (isBedrock(state)) {
                        continue;
                    }
                    if (world.destroyBlock(pos, false)) {
                        destroyed++;
                    }
                }
            }
        }
        return destroyed;
    }

    private static boolean isProtected(BlockState state, SiegeConfig cfg) {
        return BlockGuard.isProtected(blockId(state), cfg);
    }

    private static boolean isBedrock(BlockState state) {
        return "minecraft:bedrock".equals(blockId(state));
    }

    static String blockId(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.toString();
    }
}
