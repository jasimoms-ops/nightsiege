package com.mki.nightsiege.game;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block id of a block state (for the protected-block checks). The blast of a lost siege that lived here
 * is gone (failureBlast, Tor 12 J).
 */
public final class Demolition {

    private Demolition() {
    }

    static String blockId(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.toString();
    }
}
