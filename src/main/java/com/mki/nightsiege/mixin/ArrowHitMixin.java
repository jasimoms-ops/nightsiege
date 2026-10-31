package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.GainGear;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Light, climbing and storm arrows do their thing where they strike a block; the arrows of a
 * manned turret count double on a head hit (Simon, 26 October 2026). The towers' arrows strike only
 * monsters (survival safety 2, Simon 31.10.).
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

    /**
     * An arrow of a tower (manned or not) or a drone flies through everything that is no monster
     * (survival safety 2, Simon 31.10.): players, pets, villagers, animals, the summoner's companions.
     */
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void nightsiege$towerArrowsSpare(net.minecraft.world.entity.Entity target, CallbackInfoReturnable<Boolean> cir) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!self.level().isClientSide() && com.mki.nightsiege.game.TrapService.sparedByTowerArrow(self, target)) {
            cir.setReturnValue(false);
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
