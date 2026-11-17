package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;

import java.util.List;

/**
 * Tor 12 (Simon 17.11.): what the server last said about the gathering at the core before a real night.
 * Only state, no drawing: the core window and the HUD read it. Reset when leaving the server.
 */
public final class GatherClientState {

    /** {@link CoreNetworking.GatherPayload#NONE}, {@code READY} or {@code COUNTDOWN}. */
    public static volatile int phase = CoreNetworking.GatherPayload.NONE;
    /** READY: seconds until the night starts anyway; COUNTDOWN: seconds until it starts. */
    public static volatile int seconds;
    /** This player pressed ready. */
    public static volatile boolean selfReady;
    /** The participants in order (name, uuid, ready). */
    public static volatile List<CoreNetworking.GatherEntry> entries = List.of();
    /** Client game time of the last update (to count the seconds down between two packets). */
    public static volatile long receivedAt;

    private GatherClientState() {
    }

    /** True while the gathering shows (ready phase or countdown). */
    public static boolean active() {
        return phase != CoreNetworking.GatherPayload.NONE;
    }

    /** Participants who pressed ready. */
    public static int readyCount() {
        int n = 0;
        for (CoreNetworking.GatherEntry e : entries) {
            if (e.ready()) {
                n++;
            }
        }
        return n;
    }

    /** "Bereit" pressed (the core window's button): toggles on the server, the answer comes as a new state. */
    public static void sendReady() {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(CoreNetworking.GatherReadyPayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CoreNetworking.GatherReadyPayload.INSTANCE);
        }
    }

    public static void receive(CoreNetworking.GatherPayload payload, long gameTime) {
        phase = payload.phase();
        seconds = payload.seconds();
        selfReady = payload.selfReady();
        entries = List.copyOf(payload.entries());
        receivedAt = gameTime;
    }

    public static void reset() {
        phase = CoreNetworking.GatherPayload.NONE;
        seconds = 0;
        selfReady = false;
        entries = List.of();
        receivedAt = 0L;
    }
}
