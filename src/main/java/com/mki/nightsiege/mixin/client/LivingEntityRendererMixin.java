package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.ClassLookState;
import com.mki.nightsiege.client.ClassLooks;
import com.mki.nightsiege.client.EventTextures;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During an event, zombies, skeletons, creepers and spiders wear the event's look.
 * Players with a subclass (and armour stands named after one) wear its outfit.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyVariable(method = "getRenderType", at = @At("STORE"), ordinal = 0)
    private Identifier nightsiege$eventTexture(Identifier texture) {
        return EventTextures.swap(texture);
    }

    /** Monsters of the other mods keep their textures but take the event's tint. */
    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void nightsiege$eventTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!EventTextures.fromEventMod(state.entityType)) {
            return;
        }
        int tint = EventTextures.modTint();
        if (tint != -1) {
            cir.setReturnValue(net.minecraft.util.ARGB.multiply(cir.getReturnValue(), tint));
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void nightsiege$classLook(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state instanceof ClassLookState look) {
            look.nightsiege$setLook(ClassLooks.lookOf(entity));
        }
    }
}
