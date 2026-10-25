package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.EventTextures;
import net.minecraft.client.renderer.entity.layers.PhantomEyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/** Phantom eyes glow gold in the eclipse. */
@Mixin(PhantomEyesLayer.class)
public abstract class PhantomEyesLayerMixin {

    private static final Identifier NIGHTSIEGE$EYES = Identifier.withDefaultNamespace("textures/entity/phantom/phantom_eyes.png");
    private static final Map<Identifier, RenderType> NIGHTSIEGE$TYPES = new HashMap<>();

    @Inject(method = "renderType", at = @At("RETURN"), cancellable = true)
    private void nightsiege$eventEyes(CallbackInfoReturnable<RenderType> cir) {
        Identifier swapped = EventTextures.swap(NIGHTSIEGE$EYES);
        if (swapped != NIGHTSIEGE$EYES) {
            cir.setReturnValue(NIGHTSIEGE$TYPES.computeIfAbsent(swapped, RenderTypes::eyes));
        }
    }
}
