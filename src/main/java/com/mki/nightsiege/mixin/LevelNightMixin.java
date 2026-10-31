package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mki.nightsiege.game.NightDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change of the siege world during a night passes here, so whatever the night's attackers
 * break – themselves, their blasts, their fire – is written down and comes back after it (Simon, 27
 * October 2026).
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

    /**
     * Survival safety (Simon 31.10.): a block an attacker breaks, or one that falls off with the wall an
     * attacker broke (a torch, a sign), comes back after the night – so it drops nothing now.
     */
    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
    private boolean nightsiege$noDoubleDrops(BlockPos pos, boolean drop, Entity breaker, int recursionLeft,
                                             Operation<Boolean> original) {
        boolean keep = drop && NightDamage.keepsDrops((Level) (Object) this, pos);
        return original.call(pos, drop && !keep, breaker, recursionLeft);
    }
}
