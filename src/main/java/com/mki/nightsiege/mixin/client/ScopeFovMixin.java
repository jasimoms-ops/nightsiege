package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.game.Gadgets;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The scope (archer crate gain): drawing a kit bow with it zooms the view in. */
@Mixin(AbstractClientPlayer.class)
public abstract class ScopeFovMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void nightsiege$scope(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (self.isUsingItem() && Gadgets.flag(self.getUseItem(), Gadgets.SCOPE)) {
            float pull = Math.min(1f, self.getTicksUsingItem() / 20f);
            cir.setReturnValue(cir.getReturnValue() * (1f - 0.45f * pull));
        }
    }
}
