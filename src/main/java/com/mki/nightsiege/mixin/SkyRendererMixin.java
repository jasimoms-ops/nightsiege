package com.mki.nightsiege.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mki.nightsiege.client.BloodMoonSky;
import com.mki.nightsiege.client.BloodMoonSkyMarker;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the blood moon in place of the normal moon and turns the stars red.
 *
 * <p>The blood moon is one extra quad in the celestial atlas, built the same way
 * vanilla builds the sun. To draw it, the moon buffer is swapped for that quad
 * for a single call, so vanilla's own render pass, pipeline and blending are
 * used unchanged. While fading, both moons are drawn with shared alpha.</p>
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin implements BloodMoonSkyMarker {

    @Shadow
    @Final
    @Mutable
    private GpuBuffer moonBuffer;

    @Shadow
    @Final
    private TextureAtlas celestialsAtlas;

    @Shadow
    private static GpuBuffer buildCelestialQuad(String name, TextureAtlasSprite sprite) {
        throw new AssertionError();
    }

    @Unique
    private GpuBuffer nightsiege$bloodMoon;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nightsiege$buildBloodMoon(TextureManager textures, AtlasManager atlases, RenderTarget target,
                                           CallbackInfo ci) {
        nightsiege$bloodMoon = buildCelestialQuad("Night Siege blood moon",
                celestialsAtlas.getSprite(BloodMoonSky.MOON_SPRITE));
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void nightsiege$closeBloodMoon(CallbackInfo ci) {
        if (nightsiege$bloodMoon != null) {
            nightsiege$bloodMoon.close();
            nightsiege$bloodMoon = null;
        }
    }

    @WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void nightsiege$renderBloodMoon(SkyRenderer self, MoonPhase phase, float alpha, PoseStack pose,
                                           Operation<Void> original) {
        BloodMoonSky.moonHookSeen = true;
        float level = BloodMoonSky.intensity();
        GpuBuffer blood = nightsiege$bloodMoon;
        if (level <= 0f || blood == null) {
            original.call(self, phase, alpha, pose);
            return;
        }
        if (level < 1f) {
            original.call(self, phase, alpha * (1f - level), pose);
        }
        GpuBuffer vanilla = moonBuffer;
        moonBuffer = blood;
        pose.pushPose();
        // The quad lies flat and is pushed up along Y afterwards, so scaling X
        // and Z here makes it larger without moving it.
        float scale = BloodMoonSky.moonScale(level);
        pose.scale(scale, 1f, scale);
        try {
            original.call(self, BloodMoonSky.firstPhase(), alpha * level, pose);
        } finally {
            pose.popPose();
            moonBuffer = vanilla;
        }
    }

    @WrapOperation(method = "renderStars", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice nightsiege$redStars(DynamicUniforms uniforms, Matrix4f pose, Vector4f color,
                                              Operation<GpuBufferSlice> original) {
        BloodMoonSky.tintStars(color);
        return original.call(uniforms, pose, color);
    }
}
