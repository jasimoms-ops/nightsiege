package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.core.KitView;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The kit inventory (28 October 2026): the recipe book stays closed (its setting is kept for later). */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookKitMixin {

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void nightsiege$closedWithKit(CallbackInfoReturnable<Boolean> cir) {
        if (KitView.hideCrafting) {
            cir.setReturnValue(false);
        }
    }
}
