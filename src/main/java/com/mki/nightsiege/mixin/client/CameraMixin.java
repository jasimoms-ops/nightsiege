package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SpotterScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The demolition expert's bird's-eye view (Simon, 26 October 2026): while it is open the
 * camera hangs above the base and looks straight down, north at the top of the screen.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void nightsiege$birdsEye(DeltaTracker tracker, CallbackInfo ci) {
        double[] at = SpotterScreen.cameraAt(((Camera) (Object) this).getCameraEntityPartialTicks(tracker));
        if (at == null) {
            return;
        }
        this.detached = true;
        this.setRotation(180f, 90f);
        this.setPosition(at[0], at[1], at[2]);
    }
}
