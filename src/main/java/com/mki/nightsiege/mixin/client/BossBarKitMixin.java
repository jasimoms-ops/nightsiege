package com.mki.nightsiege.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mki.nightsiege.client.NightBars;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** The bars at the top (design update, 29 October 2026): the mod's bars in iron and gold. */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarKitMixin {

    /**
     * Compact boss bars (Tor 2.3): vanilla's loop gets a list without the small-row bosses, the big boss last.
     * No cancel: vanilla's drawing and other mods' hooks in this method keep running.
     */
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<LerpingBossEvent> nightsiege$compact(Collection<LerpingBossEvent> all) {
        return NightBars.order(all);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"))
    private void nightsiege$ownBar(BossHealthOverlay self, GuiGraphicsExtractor gui, int x, int y, BossEvent event) {
        if (!NightBars.draw(gui, x, y, event)) {
            ((BossHealthOverlayInvoker) self).nightsiege$extractBar(gui, x, y, event);
        }
        NightBars.drawMinis(gui, x, y, event);
    }

    /** Tor 3.0c: the mod's bosses as a small list in the top right corner, after vanilla's bars (no cancel). */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nightsiege$corner(GuiGraphicsExtractor gui, CallbackInfo ci) {
        NightBars.drawCorner(gui);
    }
}
