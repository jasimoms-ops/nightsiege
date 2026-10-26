package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.HealthViewClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Medic and warden (Simon, 26 October 2026): under each defender's name the life left,
 * "♥ 14/20", in the line Minecraft keeps for a score under the name.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void nightsiege$healthLine(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null || !(entity instanceof Player player)) {
            return;
        }
        Component line = HealthViewClient.line(player);
        if (line != null) {
            state.scoreText = line;
        }
    }
}
