package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.SkyFx;
import com.mki.nightsiege.game.EventAtmosphere;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eclipsed sun, blood moon and aurora on top of the normal sky. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Shadow @Final private TextureAtlas celestialsAtlas;
    @Shadow @Final private RenderTarget renderTarget;
    @Shadow @Final private RenderSystem.AutoStorageIndexBuffer quadIndices;

    @Unique private GpuBuffer nightsiege$eclipse;
    @Unique private GpuBuffer nightsiege$bloodMoon;
    @Unique private GpuBuffer nightsiege$aurora;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nightsiege$build(TextureManager textureManager, AtlasManager atlasManager, RenderTarget target,
                                  CallbackInfo ci) {
        try {
            nightsiege$eclipse = SkyFx.quad("Night Siege eclipse", celestialsAtlas.getSprite(SkyFx.ECLIPSE_SUN));
            nightsiege$bloodMoon = SkyFx.quad("Night Siege blood moon", celestialsAtlas.getSprite(SkyFx.BLOOD_MOON));
            nightsiege$aurora = SkyFx.aurora(celestialsAtlas.getSprite(SkyFx.AURORA));
        } catch (RuntimeException e) {
            // Without the extra buffers the sky simply stays vanilla.
            nightsiege$eclipse = null;
            nightsiege$bloodMoon = null;
            nightsiege$aurora = null;
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nightsiege$close(CallbackInfo ci) {
        if (nightsiege$eclipse != null) {
            nightsiege$eclipse.close();
        }
        if (nightsiege$bloodMoon != null) {
            nightsiege$bloodMoon.close();
        }
        if (nightsiege$aurora != null) {
            nightsiege$aurora.close();
        }
    }

    /** The normal sun fades out while the eclipse comes. */
    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1)
    private Vector4f nightsiege$dimSun(Vector4f color) {
        float s = EventAtmosphere.clientStrength(EventAtmosphere.ECLIPSE);
        return s <= 0f ? color : new Vector4f(color.x, color.y, color.z, color.w * (1f - s));
    }

    @Inject(method = "renderSun", at = @At("TAIL"))
    private void nightsiege$eclipseSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        float s = EventAtmosphere.clientStrength(EventAtmosphere.ECLIPSE);
        if (s > 0.01f && nightsiege$eclipse != null) {
            SkyFx.draw("Night Siege eclipse", nightsiege$eclipse, 1, poseStack, 100.0F, 34.0F,
                    new Vector4f(1f, 1f, 1f, s), celestialsAtlas, renderTarget, quadIndices);
        }
    }

    /** The normal moon fades out under a blood moon. */
    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1)
    private Vector4f nightsiege$dimMoon(Vector4f color) {
        float s = EventAtmosphere.clientStrength(EventAtmosphere.BLOOD_MOON);
        return s <= 0f ? color : new Vector4f(color.x, color.y, color.z, color.w * (1f - s));
    }

    @Inject(method = "renderMoon", at = @At("TAIL"))
    private void nightsiege$bloodMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        float s = EventAtmosphere.clientStrength(EventAtmosphere.BLOOD_MOON);
        if (s > 0.01f && nightsiege$bloodMoon != null) {
            SkyFx.draw("Night Siege blood moon", nightsiege$bloodMoon, 1, poseStack, 100.0F, 24.0F,
                    new Vector4f(1f, 1f, 1f, s), celestialsAtlas, renderTarget, quadIndices);
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void nightsiege$aurora(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                   MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        float s = EventAtmosphere.clientStrength(EventAtmosphere.AURORA);
        if (s <= 0.01f || nightsiege$aurora == null) {
            return;
        }
        // Two layers breathing against each other make the curtain shimmer.
        double t = (System.currentTimeMillis() % 600000L) / 1000.0;
        float a = (float) (0.55 + 0.25 * Math.sin(t * 0.7)) * s * rainBrightness;
        float b = (float) (0.45 + 0.25 * Math.sin(t * 0.43 + 2.0)) * s * rainBrightness;
        SkyFx.draw("Night Siege aurora", nightsiege$aurora, SkyFx.AURORA_QUADS, poseStack, 0.0F, 1.0F,
                new Vector4f(1f, 1f, 1f, a), celestialsAtlas, renderTarget, quadIndices);
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(14.0F));
        poseStack.scale(1.06F, 1.12F, 1.06F);
        SkyFx.draw("Night Siege aurora", nightsiege$aurora, SkyFx.AURORA_QUADS, poseStack, 0.0F, 1.0F,
                new Vector4f(0.8f, 0.9f, 1f, b), celestialsAtlas, renderTarget, quadIndices);
        poseStack.popPose();
    }
}
