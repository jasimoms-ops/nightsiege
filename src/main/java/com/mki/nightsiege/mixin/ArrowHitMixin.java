package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.GainGear;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Light, climbing and storm arrows do their thing where they strike a block; the arrows of a
 * manned turret count double on a head hit (Simon, 26 October 2026).
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowHitMixin {

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void nightsiege$gainArrows(BlockHitResult hit, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!self.level().isClientSide()) {
            GainGear.onArrowHitBlock(self, hit);
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void nightsiege$headshot(EntityHitResult hit, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!self.level().isClientSide()) {
            com.mki.nightsiege.game.TrapService.onMannedArrowHit(self, hit);
        }
    }
}
