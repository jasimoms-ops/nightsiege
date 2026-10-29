package com.mki.nightsiege.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A ready round at a start block (content update, Simon 28/29 October 2026): the war drum before
 * the war camp, built so that the dungeons' seal stone can use it as well. Whoever is signed up,
 * has a class and stands within {@link #RADIUS} blocks of the block when it is struck is in it;
 * each one chooses a class in the window ("Bereit"); when all are ready the fight begins. Whoever
 * walks off is out; the starter walking off (or striking again) calls it off; after
 * {@link #FORCE_SECONDS} the starter may go without those not ready.
 */
final class StartRound {

    static final double RADIUS = 20.0;
    /** After this the starter may go without those not ready (Simon 29.10., 01:00). */
    static final int FORCE_SECONDS = 30;

    final UUID starter;
    final BlockPos at;
    final long startedAt;
    /** How close to the block one must stand, and when the starter may go without the others. */
    private final double radius;
    private final int forceSeconds;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> ready = new HashSet<>();

    StartRound(UUID starter, BlockPos at, long now) {
        this(starter, at, now, RADIUS, FORCE_SECONDS, true);
    }

    /**
     * The war leader's round (Kriegsführer, 29 October 2026) has its own reach round the core, and
     * its starter leads the monsters: then he is no member ({@code starterPlays} false).
     */
    StartRound(UUID starter, BlockPos at, long now, double radius, int forceSeconds, boolean starterPlays) {
        this.starter = starter;
        this.at = at;
        this.startedAt = now;
        this.radius = radius;
        this.forceSeconds = forceSeconds;
        if (starterPlays) {
            members.add(starter);
        }
    }

    void add(UUID id) {
        members.add(id);
    }

    boolean has(UUID id) {
        return id != null && members.contains(id);
    }

    boolean isStarter(UUID id) {
        return starter.equals(id);
    }

    List<UUID> members() {
        return new ArrayList<>(members);
    }

    boolean isReady(UUID id) {
        return ready.contains(id);
    }

    int readyCount() {
        return ready.size();
    }

    int size() {
        return members.size();
    }

    /** Marks a member ready; false for someone not in the round. */
    boolean markReady(UUID id) {
        if (!members.contains(id)) {
            return false;
        }
        ready.add(id);
        return true;
    }

    boolean allReady() {
        return !members.isEmpty() && ready.containsAll(members);
    }

    /** Seconds until the starter may go without the others (0 when he may now). */
    int forceIn(long now) {
        return (int) Math.max(0, forceSeconds - (now - startedAt) / 20);
    }

    /** True when someone stands close enough to the block to join or stay. */
    boolean near(ServerPlayer player, ServerLevel level) {
        return player != null && player.level() == level && player.isAlive()
                && player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= radius * radius;
    }

    /**
     * Drops the members who walked off, died or logged out; returns them. The caller ends the
     * round when the starter is among them.
     */
    List<UUID> dropGone(ServerLevel level) {
        List<UUID> gone = new ArrayList<>();
        for (UUID id : new ArrayList<>(members)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (!near(p, level)) {
                members.remove(id);
                ready.remove(id);
                gone.add(id);
            }
        }
        return gone;
    }

    /** The starter's button: those not ready are out (only once FORCE_SECONDS are over and one is ready). */
    List<UUID> force(long now) {
        List<UUID> dropped = new ArrayList<>();
        if (forceIn(now) > 0 || ready.isEmpty()) {
            return dropped;
        }
        for (UUID id : new ArrayList<>(members)) {
            if (!ready.contains(id)) {
                members.remove(id);
                dropped.add(id);
            }
        }
        return dropped;
    }
}
