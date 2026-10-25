package com.mki.nightsiege.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block a block display shows (a soul lantern, a cracking egg). */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {

    @Invoker("setBlockState")
    void nightsiege$setBlockState(BlockState state);
}
