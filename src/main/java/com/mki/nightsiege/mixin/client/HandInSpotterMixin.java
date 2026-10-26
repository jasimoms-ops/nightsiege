package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SpotterScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hand and no held item in the bird's-eye view (the camera hangs above the body) or in a drone. */
@Mixin(GameRenderer.class)
public abstract class HandInSpotterMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noHandFromAbove(CallbackInfo ci) {
        if (SpotterScreen.cameraAt(0f) != null || com.mki.nightsiege.client.DroneClient.localDrone()) {
            ci.cancel();
        }
    }
}
