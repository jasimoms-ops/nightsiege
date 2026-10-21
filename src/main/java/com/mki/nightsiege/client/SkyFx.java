package com.mki.nightsiege.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Extra things in the sky: the eclipsed sun, the blood moon and the aurora.
 * Drawn the same way Minecraft draws its own sun, from sprites of the
 * celestial atlas, so resource packs and shaders treat them alike.
 */
public final class SkyFx {

    public static final Identifier ECLIPSE_SUN = Identifier.fromNamespaceAndPath("nightsiege", "eclipse_sun");
    public static final Identifier BLOOD_MOON = Identifier.fromNamespaceAndPath("nightsiege", "blood_moon");
    public static final Identifier AURORA = Identifier.fromNamespaceAndPath("nightsiege", "aurora");

    /** Curtain pieces of the aurora. */
    public static final int AURORA_QUADS = 48;

    private SkyFx() {
    }

    /** One flat quad, like the vanilla sun: x and z from -1 to 1 at y = 0. */
    public static GpuBuffer quad(String name, TextureAtlasSprite sprite) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV0());
            builder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV0());
            builder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV1());
            builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV1());
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> name, 32, mesh.vertexBuffer());
            }
        }
    }

    /**
     * The aurora: a wavy curtain hanging over the northern half of the sky,
     * built from upright quads on a ring around the viewer.
     */
    public static GpuBuffer aurora(TextureAtlasSprite sprite) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(AURORA_QUADS * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            float radius = 92f;
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            for (int i = 0; i < AURORA_QUADS; i++) {
                double a0 = Math.toRadians(-80 + 160.0 * i / AURORA_QUADS);
                double a1 = Math.toRadians(-80 + 160.0 * (i + 1) / AURORA_QUADS);
                float r0 = radius + (float) (Math.sin(i * 0.55) * 9 + Math.sin(i * 1.7) * 3);
                float r1 = radius + (float) (Math.sin((i + 1) * 0.55) * 9 + Math.sin((i + 1) * 1.7) * 3);
                float x0 = (float) Math.sin(a0) * r0;
                float z0 = (float) -Math.cos(a0) * r0;
                float x1 = (float) Math.sin(a1) * r1;
                float z1 = (float) -Math.cos(a1) * r1;
                float low0 = 22f + (float) Math.sin(i * 0.4) * 6f;
                float low1 = 22f + (float) Math.sin((i + 1) * 0.4) * 6f;
                float high0 = low0 + 46f + (float) Math.cos(i * 0.9) * 8f;
                float high1 = low1 + 46f + (float) Math.cos((i + 1) * 0.9) * 8f;
                // Every four pieces the texture repeats across.
                float ua = u0 + (u1 - u0) * ((i % 4) / 4f);
                float ub = u0 + (u1 - u0) * ((i % 4 + 1) / 4f);
                builder.addVertex(x0, low0, z0).setUv(ua, v1);
                builder.addVertex(x1, low1, z1).setUv(ub, v1);
                builder.addVertex(x1, high1, z1).setUv(ub, v0);
                builder.addVertex(x0, high0, z0).setUv(ua, v0);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Night Siege aurora", 32, mesh.vertexBuffer());
            }
        }
    }

    /**
     * Draws quads of a buffer with the celestial pipeline, which adds light onto
     * the sky: black stays invisible, colour glows.
     */
    public static void draw(String name, GpuBuffer buffer, int quads, PoseStack poseStack, float height, float scale,
                            Vector4f color, TextureAtlas atlas, RenderTarget target,
                            RenderSystem.AutoStorageIndexBuffer indices) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, height, 0.0F);
        modelViewStack.scale(scale, 1.0F, scale);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack), color);
        GpuBuffer indexBuffer = indices.getBuffer(quads * 6);
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> name, target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, buffer.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(quads * 6, 1, 0, 0, 0);
        } finally {
            modelViewStack.popMatrix();
        }
    }
}
