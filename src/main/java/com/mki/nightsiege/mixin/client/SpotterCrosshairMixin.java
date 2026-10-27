package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SpotterScreen;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the bird's-eye view the red X at the mouse is the crosshair; the white one in the middle
 * goes, and so does the rest of the HUD (hotbar, hearts, boss bar; Simon, 27 October 2026) –
 * the view's own panels show what matters.
 */
@Mixin(Hud.class)
public abstract class SpotterCrosshairMixin {

    @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true)
    private void nightsiege$hiddenFromAbove(CallbackInfoReturnable<Boolean> cir) {
        if (SpotterScreen.isOpen()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noHudFromAbove(CallbackInfo ci) {
        if (SpotterScreen.isOpen()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noCrosshairFromAbove(CallbackInfo ci) {
        if (SpotterScreen.isOpen()) {
            ci.cancel();
        }
    }
}
