package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.WheelOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the wheel of Fähigkeit 3 is open (Simon, 27 October 2026: usable while walking), the
 * mouse moves the wheel's pointer instead of turning the camera.
 */
@Mixin(MouseHandler.class)
public abstract class MouseWheelPointerMixin {

    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void nightsiege$wheelPointer(double time, CallbackInfo ci) {
        if (WheelOverlay.isOpen()) {
            WheelOverlay.movePointer(this.accumulatedDX, this.accumulatedDY);
            ci.cancel();
        }
    }
}
