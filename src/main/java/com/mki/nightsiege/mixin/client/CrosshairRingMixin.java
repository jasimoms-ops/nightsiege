package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.StrikeRing;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Tor 4.10: Minecraft's attack indicator at the crosshair is off while the warrior's ring stands in for it. */
@Mixin(Hud.class)
public abstract class CrosshairRingMixin {

    @Redirect(method = "extractCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object nightsiege$ringInstead(OptionInstance<?> option) {
        return StrikeRing.shown() ? AttackIndicatorStatus.OFF : option.get();
    }
}
