package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mki.nightsiege.client.BloodMoonSky;
import com.mki.nightsiege.client.BloodMoonSkyMarker;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The camera reads sky colour, fog colour, fog distances and star brightness
 * through this probe. On a blood moon night they are tinted red here, so the
 * sky, the horizon and the haze over the land all agree with each other.
 *
 * <p>Only listed under "client" in the mixin config. Visual values only; nothing
 * the server decides with, such as the moon phase, is touched.</p>
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin implements BloodMoonSkyMarker {

    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private Object nightsiege$bloodMoon(Object original, EnvironmentAttribute<?> attribute, float partialTick) {
        return BloodMoonSky.adjust(attribute, original);
    }
}
