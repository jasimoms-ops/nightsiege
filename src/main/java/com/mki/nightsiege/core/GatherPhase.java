package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tor 12 (Simon 17.11.: "wir sammeln … jeder der die nacht annimmt zum core tp … dann bekommen alle das kit und
 * können ready drücken wenn alle die teilnehmen ready gedrückt haben geht es los"): the gathering at the core
 * between the horn and the start of a real night. Pure logic over game ticks, no Minecraft classes.
 *
 * <p>ANNOUNCE (5 s: "in 5 s to the core") → TELEPORT → READY (everyone toggles ready; the clock stands) →
 * all ready: COUNTDOWN 3-2-1 → START. At the latest {@code maxReadySeconds} after the teleport: TIME_UP
 * (the night starts with everyone at the core, or is called off when nobody is there).</p>
 */
public final class GatherPhase {

    public enum Phase { NONE, ANNOUNCE, READY, COUNTDOWN }

    /** What the server does after a tick. */
    public enum Step { NONE, TELEPORT, WARN_60, WARN_10, ALL_READY, COUNT, START, TIME_UP }

    /** "In 5 s to the core." */
    public static final int ANNOUNCE_TICKS = 100;
    public static final int COUNTDOWN_SECONDS = 3;

    private Phase phase = Phase.NONE;
    private long teleportAt;
    private long deadline;
    private long countdownEnd;
    private int shownCount;
    private boolean warned60;
    private boolean warned10;
    /** Participants in the order they came: uuid → name. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Set<UUID> ready = new HashSet<>();

    public Phase phase() {
        return phase;
    }

    public boolean active() {
        return phase != Phase.NONE;
    }

    /** The horn: the announcement runs, the teleport follows after {@link #ANNOUNCE_TICKS}. */
    public void begin(long now) {
        reset();
        phase = Phase.ANNOUNCE;
        teleportAt = now + ANNOUNCE_TICKS;
    }

    public void reset() {
        phase = Phase.NONE;
        members.clear();
        ready.clear();
        warned60 = false;
        warned10 = false;
        shownCount = 0;
    }

    /** A participant at the core (after the teleport, a late-comer, or back at the core); true when new. */
    public boolean join(UUID id, String name) {
        if (phase != Phase.READY && phase != Phase.COUNTDOWN || id == null) {
            return false;
        }
        return members.putIfAbsent(id, name == null ? "" : name) == null;
    }

    /** Only these stay participants (online and at the core); the others lose their place and their ready. */
    public void retain(Collection<UUID> atCore) {
        if (phase != Phase.READY) {
            return;   // in the countdown nobody drops out: it starts with whoever is at the core then
        }
        members.keySet().retainAll(atCore);
        ready.retainAll(members.keySet());
    }

    /** "Bereit" pressed: the new state, null when not a participant or not in the ready phase. */
    public Boolean toggle(UUID id) {
        if (phase != Phase.READY || !members.containsKey(id)) {
            return null;
        }
        if (!ready.remove(id)) {
            ready.add(id);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public boolean isMember(UUID id) {
        return members.containsKey(id);
    }

    public boolean isReady(UUID id) {
        return ready.contains(id);
    }

    public int memberCount() {
        return members.size();
    }

    public int readyCount() {
        return ready.size();
    }

    /** uuid → name, in the order they came. */
    public Map<UUID, String> members() {
        return java.util.Collections.unmodifiableMap(members);
    }

    public boolean allReady() {
        return !members.isEmpty() && ready.containsAll(members.keySet());
    }

    /** Seconds shown: until the teleport, until the night starts anyway, or of the countdown. */
    public int secondsLeft(long now) {
        long end = switch (phase) {
            case ANNOUNCE -> teleportAt;
            case READY -> deadline;
            case COUNTDOWN -> countdownEnd;
            case NONE -> now;
        };
        return (int) Math.max(0L, (end - now + 19) / 20);
    }

    /** One server tick; maxReadySeconds = gatherReadyMaxSeconds. */
    public Step tick(long now, int maxReadySeconds) {
        switch (phase) {
            case ANNOUNCE -> {
                if (now >= teleportAt) {
                    phase = Phase.READY;
                    deadline = now + Math.max(1, maxReadySeconds) * 20L;
                    warned60 = maxReadySeconds <= 60;
                    warned10 = maxReadySeconds <= 10;
                    return Step.TELEPORT;
                }
                return Step.NONE;
            }
            case READY -> {
                if (allReady()) {
                    phase = Phase.COUNTDOWN;
                    countdownEnd = now + COUNTDOWN_SECONDS * 20L;
                    shownCount = COUNTDOWN_SECONDS;
                    return Step.ALL_READY;
                }
                if (now >= deadline) {
                    phase = Phase.NONE;
                    return Step.TIME_UP;
                }
                int left = secondsLeft(now);
                if (!warned60 && left <= 60) {
                    warned60 = true;
                    return Step.WARN_60;
                }
                if (!warned10 && left <= 10) {
                    warned10 = true;
                    return Step.WARN_10;
                }
                return Step.NONE;
            }
            case COUNTDOWN -> {
                if (now >= countdownEnd) {
                    phase = Phase.NONE;
                    return Step.START;
                }
                int left = secondsLeft(now);
                if (left < shownCount) {
                    shownCount = left;
                    return Step.COUNT;
                }
                return Step.NONE;
            }
            default -> {
                return Step.NONE;
            }
        }
    }

    /**
     * Who is brought to the core at the horn (and as a late-comer): signed up and accepted, not busy in a
     * playtest or another round, alive and not spectating (Plan Tor 12 point 1).
     */
    public static boolean bringsToCore(boolean registered, boolean accepted, boolean busyElsewhere, boolean dead,
                                       boolean spectator) {
        return registered && accepted && !busyElsewhere && !dead && !spectator;
    }

    /** True for the nights that gather (real nights by the rhythm: normal, boss, finale, extra siege, eclipse). */
    public static boolean gathers(SiegeConfig cfg, boolean rhythmNight) {
        return cfg != null && cfg.gatherTeleport && rhythmNight;
    }

    /** The time of day the clock jumps to when the night starts: the eclipse has its own. */
    public static int startTime(SiegeConfig cfg, boolean eclipse) {
        return eclipse ? cfg.eclipseStartDayTime : cfg.startDayTime;
    }

    /**
     * Spots 3–6 blocks around the core, nearest first (then by angle, so players spread around it):
     * {dx, dz} pairs.
     */
    public static List<int[]> ringOffsets(int min, int max) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= min && d <= max + 0.5) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        out.sort(Comparator.<int[]>comparingDouble(o -> Math.round(Math.sqrt(o[0] * o[0] + o[1] * o[1])))
                .thenComparingDouble(o -> Math.atan2(o[1], o[0])));
        return out;
    }

    /** "2:41" for the bar. */
    public static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
