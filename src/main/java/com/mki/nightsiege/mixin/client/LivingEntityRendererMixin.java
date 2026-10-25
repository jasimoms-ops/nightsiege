package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.BossLookState;
import com.mki.nightsiege.client.BossLooks;
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
        if (state instanceof BossLookState boss) {
            boss.nightsiege$setBoss(BossLooks.of(entity.getId()));
        }
    }

    /** A boss wears its own look: while it is drawn, the textures come from its folder. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void nightsiege$bossLookOn(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                                       net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        EventTextures.renderingBoss(state instanceof BossLookState boss ? boss.nightsiege$boss() : null);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void nightsiege$bossLookOff(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                        net.minecraft.client.renderer.SubmitNodeCollector collector,
                                        net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        EventTextures.renderingBoss(null);
    }
}
