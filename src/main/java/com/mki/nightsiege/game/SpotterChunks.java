package com.mki.nightsiege.game;

import com.mki.nightsiege.mixin.ChunkMapInvoker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bird's-eye view (Simon, 27 October 2026: on the left and at the top the world stayed
 * empty): the server sends a player the chunks around their own body, so a camera far from
 * the body looked at chunks the client never got. While the view is open the client reports
 * the camera's chunk, and the chunks sent are centred there (ChunkMapSpotterMixin).
 */
public final class SpotterChunks {

    /** How far the camera may be from the body for this (blocks). */
    private static final double MAX_FROM_BODY = 200;
    /** Without a new report for this long the view counts as closed (ticks). */
    private static final long TIMEOUT = 80;

    private record Center(ChunkPos pos, long at) {
    }

    private static final Map<UUID, Center> CENTERS = new ConcurrentHashMap<>();

    private SpotterChunks() {
    }

    /** The middle of the chunks the player gets: the camera of an open view, else the body. */
    public static ChunkPos centerFor(ServerPlayer player) {
        Center c = CENTERS.get(player.getUUID());
        return c == null ? player.chunkPosition() : c.pos();
    }

    /** The client reports the camera (block x/z); null or too far = the view closed. */
    public static void report(ServerPlayer player, Double x, Double z) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Center old = CENTERS.get(player.getUUID());
        if (x == null || z == null || Math.hypot(x - player.getX(), z - player.getZ()) > MAX_FROM_BODY) {
            if (CENTERS.remove(player.getUUID()) != null) {
                refresh(level, player);
            }
            return;
        }
        ChunkPos pos = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        // The body's own chunk stays among the sent ones: the middle moves at most to two
        // chunks short of the edge towards the camera.
        int view = Math.min(Math.max(2, player.requestedViewDistance()),
                Math.max(2, level.getServer().getPlayerList().getViewDistance()));
        ChunkPos body = player.chunkPosition();
        int dx = pos.x() - body.x();
        int dz = pos.z() - body.z();
        double d = Math.hypot(dx, dz);
        double max = Math.max(0, view - 2);
        if (d > max) {
            double k = max / d;
            pos = new ChunkPos(body.x() + (int) Math.round(dx * k), body.z() + (int) Math.round(dz * k));
        }
        CENTERS.put(player.getUUID(), new Center(pos, level.getGameTime()));
        if (old == null || !old.pos().equals(pos)) {
            refresh(level, player);
        }
    }

    /** Every second: views that stopped reporting (closed, player gone) give the body back its chunks. */
    public static void tick(MinecraftServer server) {
        if (CENTERS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Center>> it = CENTERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Center> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.level() instanceof ServerLevel level && level.getGameTime() - e.getValue().at() > TIMEOUT) {
                it.remove();
                refresh(level, player);
            }
        }
    }

    private static void refresh(ServerLevel level, ServerPlayer player) {
        try {
            ((ChunkMapInvoker) level.getChunkSource().chunkMap).nightsiege$updateChunkTracking(player);
        } catch (RuntimeException e) {
            // never let the view break the server
        }
    }
}
