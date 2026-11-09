package com.mki.nightsiege.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mki.nightsiege.client.StrikeRing;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Tor 4.10: Minecraft's attack indicator at the crosshair is off while the warrior's ring stands in for it.
 * Only the value read right after {@code Options.attackIndicator()} changes; it chains with other mods.
 */
@Mixin(Hud.class)
public abstract class CrosshairRingMixin {

    @ModifyExpressionValue(method = "extractCrosshair",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;attackIndicator()Lnet/minecraft/client/OptionInstance;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object nightsiege$ringInstead(Object original) {
        return original instanceof AttackIndicatorStatus && StrikeRing.shown() ? AttackIndicatorStatus.OFF : original;
    }
}
