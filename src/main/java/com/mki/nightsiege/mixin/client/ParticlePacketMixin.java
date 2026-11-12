package com.mki.nightsiege.mixin.client;

import com.mki.nightsiege.client.AbilityFxClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Z4 (Simon 12.11.): an ability's own particles (sent while its key's code runs) come bigger, longer and
 * more on clients with full effects; {@link AbilityFxClient#boost} draws them then, else Minecraft does.
 * After the thread check, so it runs on the client thread only.
 */
@Mixin(ClientPacketListener.class)
public abstract class ParticlePacketMixin {

    @Inject(method = "handleParticleEvent", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
    private void nightsiege$bigAbilityParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (AbilityFxClient.boost(packet)) {
            ci.cancel();
        }
    }
}
