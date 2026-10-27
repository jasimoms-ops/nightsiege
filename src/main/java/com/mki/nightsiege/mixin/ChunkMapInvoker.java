package com.mki.nightsiege.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Re-centres the chunks a player is sent (SpotterChunks). */
@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {

    @Invoker("updateChunkTracking")
    void nightsiege$updateChunkTracking(ServerPlayer player);
}
