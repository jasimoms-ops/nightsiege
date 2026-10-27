package com.mki.nightsiege.mixin;

import com.mki.nightsiege.game.SpotterChunks;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The demolition expert's bird's-eye view (Simon, 27 October 2026: parts of the world stayed
 * empty): while it is open, the chunks the client gets are the ones around the camera, not
 * around the player's body.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapSpotterMixin {

    @Redirect(method = "updateChunkTracking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos nightsiege$spotterCenter(ServerPlayer player) {
        return SpotterChunks.centerFor(player);
    }
}
