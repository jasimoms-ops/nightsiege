package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.NightBars;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The bars at the top (design update, 29 October 2026): the mod's bars in iron and gold. */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarKitMixin {

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"))
    private void nightsiege$ownBar(BossHealthOverlay self, GuiGraphicsExtractor gui, int x, int y, BossEvent event) {
        if (!NightBars.draw(gui, x, y, event)) {
            ((BossHealthOverlayInvoker) self).nightsiege$extractBar(gui, x, y, event);
        }
    }
}
