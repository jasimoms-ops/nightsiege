package com.mki.nightsiege.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The build-up before a playtest night (Tor 9.1 i, Simon 09.11. 00:32: "Techniker und Bogenschützen
 * brauchen Zeit zum Positionieren"). Kit and towers are there from its start; every player presses
 * "Bereit". Once everyone online is ready a 10 s countdown runs – pressing "Bereit" again (not ready)
 * or a newcomer who is not ready breaks it off. After 2 min at the latest the night comes: the last
 * 10 s are a countdown nobody can break off. Pure logic in game ticks (20 per second), for the tests.
 */
public final class SetupPhase {

    public static final int MAX_SECONDS = 120;
    public static final int COUNTDOWN_SECONDS = 10;

    /** What a tick brought. */
    public enum Step { WAIT, COUNTDOWN_STARTED, COUNTDOWN_ABORTED, COUNTING, GO }

    private final long start;
    private final Set<UUID> ready = new HashSet<>();
    private long countdownEnds = -1L;
    /** The countdown at the end of the 2 min: it cannot be broken off. */
    private boolean forced;

    public SetupPhase(long now) {
        this.start = now;
    }

    /** "Bereit" pressed: toggles; returns whether the player is ready now. */
    public boolean toggle(UUID id) {
        if (!ready.remove(id)) {
            ready.add(id);
            return true;
        }
        return false;
    }

    public boolean isReady(UUID id) {
        return ready.contains(id);
    }

    /** The player left the playtest (not just offline: the ready mark goes). */
    public void leave(UUID id) {
        ready.remove(id);
    }

    /** How many of these are ready. */
    public int readyCount(Collection<UUID> online) {
        int n = 0;
        for (UUID id : online) {
            if (ready.contains(id)) {
                n++;
            }
        }
        return n;
    }

    private boolean allReady(Collection<UUID> online) {
        return !online.isEmpty() && readyCount(online) == online.size();
    }

    /** Once per tick (or second) with the players of the playtest who are online now. */
    public Step tick(long now, Collection<UUID> online) {
        long deadline = start + MAX_SECONDS * 20L;
        if (countdownEnds >= 0) {
            if (now >= countdownEnds) {
                return Step.GO;
            }
            if (!forced && !allReady(online)) {
                countdownEnds = -1L;
                if (now >= deadline - COUNTDOWN_SECONDS * 20L) {
                    forced = true;   // broken off inside the last 10 s: the night still comes at 2 min
                    countdownEnds = deadline;
                    return Step.COUNTING;
                }
                return Step.COUNTDOWN_ABORTED;
            }
            return Step.COUNTING;
        }
        if (now >= deadline - COUNTDOWN_SECONDS * 20L) {
            forced = true;
            countdownEnds = deadline;
            return now >= deadline ? Step.GO : Step.COUNTDOWN_STARTED;
        }
        if (allReady(online)) {
            countdownEnds = now + COUNTDOWN_SECONDS * 20L;
            return Step.COUNTDOWN_STARTED;
        }
        return Step.WAIT;
    }

    public boolean counting() {
        return countdownEnds >= 0;
    }

    public boolean forced() {
        return forced;
    }

    /** Seconds until the night: the countdown's, else until the 2 min are over. */
    public int secondsLeft(long now) {
        long end = countdownEnds >= 0 ? countdownEnds : start + MAX_SECONDS * 20L;
        return (int) Math.max(0, (end - now + 19) / 20);
    }

    /** Seconds since the build-up began (for the night statistics). */
    public int seconds(long now) {
        return (int) Math.max(0, (now - start) / 20);
    }
}
