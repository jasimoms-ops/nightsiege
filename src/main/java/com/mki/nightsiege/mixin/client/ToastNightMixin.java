package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tor 3.2c (Simon 09.11.): at night no advancement or recipe toasts over the boss list. */
@Mixin(ToastManager.class)
public abstract class ToastNightMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void nightsiege$quietAtNight(Toast toast, CallbackInfo ci) {
        if (KitHud.blocksToast(toast)) {
            ci.cancel();
        }
    }
}
