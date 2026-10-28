package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.KitInventory;
import com.mki.nightsiege.core.KitView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The kit inventory (Simon, 28 October 2026): with the kit worn, the inventory (E) gets the dark iron
 * and gold look; where the 2x2 crafting grid was, the subclass crest, the class name, the kit's values
 * and the abilities. The recipe book button is hidden.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenKitMixin {

    @Inject(method = "init", at = @At("HEAD"))
    private void nightsiege$kitView(CallbackInfo ci) {
        KitView.hideCrafting = KitHud.worn();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nightsiege$hideRecipeButton(CallbackInfo ci) {
        if (!KitView.hideCrafting) {
            return;
        }
        for (GuiEventListener child : ((Screen) (Object) this).children()) {
            if (child instanceof ImageButton button) {
                button.visible = false;
                button.active = false;
            }
        }
    }

    @Redirect(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void nightsiege$kitBackground(GuiGraphicsExtractor gui, RenderPipeline pipeline, Identifier texture, int x, int y,
                                          float u, float v, int w, int h, int texW, int texH) {
        if (KitView.hideCrafting) {
            KitInventory.drawBackground(gui, x, y);
        } else {
            gui.blit(pipeline, texture, x, y, u, v, w, h, texW, texH);
        }
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void nightsiege$noCraftingTitle(GuiGraphicsExtractor gui, int mouseX, int mouseY, CallbackInfo ci) {
        if (KitView.hideCrafting) {
            ci.cancel();
        }
    }
}
