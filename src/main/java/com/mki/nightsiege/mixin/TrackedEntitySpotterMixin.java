package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SpotterChunks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The bird's-eye view (27 October 2026): monsters are sent to a player by their distance to
 * the camera while the view is open, so the ones under the camera show even far from the body.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntitySpotterMixin {

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 nightsiege$spotterEye(ServerPlayer player) {
        return SpotterChunks.eyeFor(player);
    }
}
