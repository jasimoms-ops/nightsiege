package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SpotterScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The demolition expert's bird's-eye view (Simon, 26 October 2026): while it is open the
 * camera hangs above the base and looks straight down, north at the top of the screen.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    private float fov;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    /**
     * Right after Minecraft placed the camera at the player: the culling frustum is built next
     * from this place and rotation (27 October 2026: built from the player's own view, only the
     * wedge in front of the body was drawn and the rest of the ground stayed empty).
     */
    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void nightsiege$birdsEyeBeforeCulling(DeltaTracker tracker, CallbackInfo ci) {
        double[] at = SpotterScreen.cameraAt(((Camera) (Object) this).getCameraEntityPartialTicks(tracker));
        if (at != null) {
            this.detached = true;
            this.setRotation(180f, 90f);
            this.setPosition(at[0], at[1], at[2]);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void nightsiege$birdsEye(DeltaTracker tracker, CallbackInfo ci) {
        double[] at = SpotterScreen.cameraAt(((Camera) (Object) this).getCameraEntityPartialTicks(tracker));
        if (at == null) {
            return;
        }
        this.detached = true;
        this.setRotation(180f, 90f);
        this.setPosition(at[0], at[1], at[2]);
        // A narrow lens from higher up: almost a map, walls do not lean (27 October 2026).
        float lens = SpotterScreen.viewFov();
        if (lens > 0) {
            this.fov = lens;
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void nightsiege$birdsEyeLens(CallbackInfoReturnable<Float> cir) {
        float lens = SpotterScreen.viewFov();
        if (lens > 0) {
            cir.setReturnValue(lens);
        }
    }
}
