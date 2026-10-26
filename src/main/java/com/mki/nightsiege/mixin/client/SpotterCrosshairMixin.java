package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SpotterScreen;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the bird's-eye view the red X at the mouse is the crosshair; the white one in the middle goes. */
@Mixin(Hud.class)
public abstract class SpotterCrosshairMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noCrosshairFromAbove(CallbackInfo ci) {
        if (SpotterScreen.isOpen()) {
            ci.cancel();
        }
    }
}
