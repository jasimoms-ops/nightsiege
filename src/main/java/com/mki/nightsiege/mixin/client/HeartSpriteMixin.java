package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Z3 (Simon 12.11.: "nur Herzen im anderen Design"): with the kit, vanilla's hearts keep every state (half, rows,
 * absorption, poison, wither, frost, regeneration wave, blinking, shaking) and only take the mod's sprites.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud$HeartType")
public abstract class HeartSpriteMixin {

    @Inject(method = "getSprite", at = @At("HEAD"), cancellable = true)
    private void nightsiege$kitHeart(boolean hardcore, boolean half, boolean blinking, CallbackInfoReturnable<Identifier> cir) {
        Identifier own = KitHud.heartSprite(((Enum<?>) (Object) this).name(), half, blinking);
        if (own != null) {
            cir.setReturnValue(own);
        }
    }
}
