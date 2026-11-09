package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.NightKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the night the night keys (Q, E, F, C, X, R, 1-3, I by default, Tor 3.2b) belong to Night Siege
 * alone (Simon, 27 October 2026; 09.11.: other mods too): a press reaches only the ability.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingNightMixin {

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void nightsiege$nightClick(InputConstants.Key key, CallbackInfo ci) {
        if (NightKeys.click(key)) {
            ci.cancel();
        }
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void nightsiege$nightSet(InputConstants.Key key, boolean down, CallbackInfo ci) {
        if (NightKeys.set(key, down)) {
            ci.cancel();
        }
    }
}
