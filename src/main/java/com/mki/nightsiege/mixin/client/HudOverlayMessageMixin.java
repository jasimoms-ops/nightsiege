package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kit HUD v2 (Simon, 29 October 2026): the action bar's text goes into the kit bar's middle arch. */
@Mixin(Hud.class)
public abstract class HudOverlayMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void nightsiege$toKitBar(Component message, boolean animate, CallbackInfo ci) {
        KitHud.overlayMessage(message);
    }
}
