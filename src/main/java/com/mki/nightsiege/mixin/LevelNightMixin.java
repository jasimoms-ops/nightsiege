package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change of the siege world during a night passes here, so whatever the night breaks
 * – monsters, blasts, fire, water – is written down and comes back after it (Simon, 27 October 2026).
 */
@Mixin(Level.class)
public abstract class LevelNightMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void nightsiege$writeDown(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!NightDamage.changing((Level) (Object) this, pos, state, flags)) {
            cir.setReturnValue(false);   // a container that the night would break
        }
    }
}
