package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.EventTextures;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** During an event, zombies, skeletons, creepers and spiders wear the event's look. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyVariable(method = "getRenderType", at = @At("STORE"), ordinal = 0)
    private Identifier nightsiege$eventTexture(Identifier texture) {
        return EventTextures.swap(texture);
    }
}
