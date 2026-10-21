package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.ShaderSupport;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the shown event to every shader program as two uniforms:
 * {@code uniform int ns_event} and {@code uniform float ns_strength}.
 * The Night Siege shader pack reads them; other packs simply ignore them.
 * Only applies when Iris is installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisUniformsMixin {

    @org.spongepowered.asm.mixin.Unique
    private static boolean nightsiege$logged;

    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), require = 0, remap = false)
    private static void nightsiege$eventUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                                 PackDirectives directives, CallbackInfo ci) {
        if (!nightsiege$logged) {
            nightsiege$logged = true;
            System.out.println("[Night Siege] event uniforms for shader packs registered");
        }
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "ns_event", ShaderSupport::eventId);
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ns_strength", ShaderSupport::strength);
    }
}
