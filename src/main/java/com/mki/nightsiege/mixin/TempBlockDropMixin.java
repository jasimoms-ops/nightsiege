package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.TempBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A night block drops nothing, whatever breaks it (survival safety 2, Simon 31.10.): a kit torch or
 * ladder that falls off with the wall a monster dug away, a climbing arrow's ladder, a barricade.
 * Mined by a player it goes without a drop anyway (SiegeManager's break hook).
 */
@Mixin(Block.class)
public abstract class TempBlockDropMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void nightsiege$noDrop(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (level != null && !level.isClientSide() && TempBlocks.isTemp(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void nightsiege$noDropAt(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity be, CallbackInfo ci) {
        if (level instanceof Level real && !real.isClientSide() && TempBlocks.isTemp(real, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void nightsiege$noDropBy(BlockState state, Level level, BlockPos pos, BlockEntity be, Entity breaker,
                                            ItemStack tool, CallbackInfo ci) {
        if (level != null && !level.isClientSide() && TempBlocks.isTemp(level, pos)) {
            ci.cancel();
        }
    }
}
