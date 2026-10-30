package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.GainClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With the arrow or potion key held, the mouse wheel goes through the choices and not through the
 * hotbar (Simon 30.10.: "pfeile und die tränke auf eine taste … dann kann man scrollen um zu ändern").
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nightsiege$scrollChoice(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (GainClient.onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
