package com.mki.nightsiege.game;

import com.mki.nightsiege.mixin.ChunkMapInvoker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

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
    /** At most one camera report this often (ticks; the client sends one per chunk crossed or second). */
    private static final long MIN_GAP = 4;
    /** The radius of the ticket that keeps the ground under the camera loaded (chunks). */
    private static final int TICKET_RADIUS = 4;

    /** The view's middle, the camera, the last report and the chunk whose ticket this view holds. */
    private record Center(ChunkPos pos, double x, double z, long at, ChunkPos ticket) {
    }

    private static final Map<UUID, Center> CENTERS = new ConcurrentHashMap<>();
    /**
     * Players whose view the server opened (TrapService.openSpotter, 31.10. survival safety): only
     * they may report a camera; the view's close (or the timeout) ends it.
     */
    private static final java.util.Set<UUID> ALLOWED = ConcurrentHashMap.newKeySet();
    /** When each player's last camera report was taken (game time). */
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    /** The server opened this player's bird's-eye view: its camera may report from now on. */
    public static void allow(ServerPlayer player) {
        if (player != null) {
            ALLOWED.add(player.getUUID());
        }
    }

    /** Whether this player's camera may report (the view was opened by the server and not closed). */
    public static boolean allowed(ServerPlayer player) {
        return player != null && ALLOWED.contains(player.getUUID());
    }

    private SpotterChunks() {
    }

    /** True while this player's bird's-eye view is open (it reports its camera). */
    public static boolean isOpen(ServerPlayer player) {
        return player != null && CENTERS.containsKey(player.getUUID());
    }

    /** The middle of the chunks the player gets: the camera of an open view, else the body. */
    public static ChunkPos centerFor(ServerPlayer player) {
        Center c = CENTERS.get(player.getUUID());
        return c == null ? player.chunkPosition() : c.pos();
    }

    /** Where monsters are measured from for this player: the camera of an open view, else the body. */
    public static Vec3 eyeFor(ServerPlayer player) {
        Center c = CENTERS.isEmpty() ? null : CENTERS.get(player.getUUID());
        return c == null ? player.position() : new Vec3(c.x(), player.getY(), c.z());
    }

    /** The client reports the camera (block x/z); null or too far = the view closed. */
    public static void report(ServerPlayer player, Double x, Double z) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Center old = CENTERS.get(player.getUUID());
        if (x == null || z == null || Math.hypot(x - player.getX(), z - player.getZ()) > MAX_FROM_BODY) {
            if (x == null || z == null) {
                ALLOWED.remove(player.getUUID());   // the view closed: a new one must be opened by the server
            }
            Center gone = CENTERS.remove(player.getUUID());
            if (gone != null) {
                release(level, gone);
                refresh(level, player);
            }
            return;
        }
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (old != null && last != null && now - last < MIN_GAP && now >= last) {
            return;   // too often: the next report (at the latest in a second) moves it
        }
        LAST.put(player.getUUID(), now);
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
        // The ground under the camera stays loaded while the view is open (the ticket runs out
        // by itself two seconds after the last report). One ticket per view: the one before is
        // given back at once when the camera moves on to another chunk (31.10.).
        ChunkPos cam = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        try {
            level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, cam, TICKET_RADIUS);
        } catch (RuntimeException ignored) {
            // loading is a help, not a must
        }
        if (old != null && old.ticket() != null && !old.ticket().equals(cam)) {
            release(level, old);
        }
        CENTERS.put(player.getUUID(), new Center(pos, x, z, now, cam));
        if (old == null || !old.pos().equals(pos)) {
            refresh(level, player);
        } else {
            refreshEntities(level, player);
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
                ALLOWED.remove(e.getKey());
                LAST.remove(e.getKey());
                continue;   // its ticket runs out by itself (two seconds)
            }
            if (player.level() instanceof ServerLevel level && level.getGameTime() - e.getValue().at() > TIMEOUT) {
                it.remove();   // still allowed: a lag spike must not lock the view (only its close does)
                release(level, e.getValue());
                refresh(level, player);
            }
        }
    }

    /** Gives a view's ticket back (it would also run out by itself after two seconds). */
    private static void release(ServerLevel level, Center c) {
        if (c == null || c.ticket() == null) {
            return;
        }
        try {
            level.getChunkSource().removeTicketWithRadius(TicketType.ENDER_PEARL, c.ticket(), TICKET_RADIUS);
        } catch (RuntimeException ignored) {
            // as above
        }
    }

    private static void refresh(ServerLevel level, ServerPlayer player) {
        try {
            ((ChunkMapInvoker) level.getChunkSource().chunkMap).nightsiege$updateChunkTracking(player);
        } catch (RuntimeException e) {
            // never let the view break the server
        }
        refreshEntities(level, player);
    }

    /** Which monsters the player is sent, measured again (ChunkMap.move goes through all of them). */
    private static void refreshEntities(ServerLevel level, ServerPlayer player) {
        try {
            level.getChunkSource().chunkMap.move(player);
        } catch (RuntimeException e) {
            // as above
        }
    }
}
