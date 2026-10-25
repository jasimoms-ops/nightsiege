package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.EventTextures;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The clothes layers of strays, boggeds and drowned take the event's colours too. */
@Mixin(RenderLayer.class)
public abstract class RenderLayerTextureMixin {

    @ModifyVariable(method = "renderColoredCutoutModel", at = @At("HEAD"), argsOnly = true)
    private static Identifier nightsiege$eventOverlay(Identifier texture) {
        return EventTextures.swap(texture);
    }
}
