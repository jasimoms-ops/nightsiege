package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.AbilityFxClient;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Z4: the ability effects' particles glow in full light, by night and under shaders (CI 550877110). */
@Mixin(Particle.class)
public abstract class ParticleGlowMixin {

    @Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
    private void nightsiege$abilityGlow(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (AbilityFxClient.glows((Particle) (Object) this)) {
            cir.setReturnValue(0xF000F0);
        }
    }
}
