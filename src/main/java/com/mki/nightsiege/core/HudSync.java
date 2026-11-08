package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tor 2 (Simon's play test 08.11.: "kein sichtbarer Cooldown außer G und Ult", the medic: "Anzeige aller
 * Mitspieler mit Leben"): the plain arithmetic behind the HUD's pauses and the healers' party list, shared
 * by server and client and checked by the unit tests.
 */
public final class HudSync {

    /** At most this many mates in the healers' list (a full night has ten defenders). */
    public static final int PARTY_MAX = 9;
    /** Ticks between two looks at the party's life (half a second). */
    public static final int PARTY_EVERY = 10;
    /** Ticks after which the same list goes out again (a lost packet heals itself). */
    public static final int PARTY_REFRESH = 200;

    private HudSync() {
    }

    /** Ticks left of a pause that ends at {@code readyAt} (0 once over, never negative or beyond an int). */
    public static int left(long readyAt, long now) {
        long d = readyAt - now;
        return d <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, d);
    }

    /** On the client: what is left of {@code sent} ticks {@code elapsed} ticks after the state came. */
    public static long shown(int sent, long elapsed) {
        return Math.max(0L, sent - Math.max(0L, elapsed));
    }

    /** Whole seconds shown on a box (rounded up: "1" until the very end). */
    public static long seconds(long leftTicks) {
        return leftTicks <= 0L ? 0L : (leftTicks + 19L) / 20L;
    }

    /** Share of a box to shade for the pause still running, 0 to 1. */
    public static double shade(long leftTicks, int totalTicks) {
        if (leftTicks <= 0L || totalTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, leftTicks / (double) totalTicks);
    }

    /** One mate in the healers' list. */
    public static final class Mate {
        public String id = "";
        public String name = "";
        public float health;
        public float max = 20f;
        /** Absorption (the guardians' barriers), drawn in gold behind the life. */
        public float absorb;

        public Mate() {
        }

        public Mate(String id, String name, float health, float max, float absorb) {
            this.id = id;
            this.name = name;
            this.health = health;
            this.max = max;
            this.absorb = absorb;
        }

        /** Life share, 0 to 1. */
        public float share() {
            return max <= 0f ? 0f : Math.max(0f, Math.min(1f, health / max));
        }
    }

    /** What the server sends a healer. */
    public static final class Party {
        public List<Mate> mates = new ArrayList<>();
    }

    /**
     * The healer's list: every defender but himself, each once, the worst off first (ties by name),
     * at most {@code max}; the healthiest drop out when there are too many.
     */
    public static List<Mate> party(String selfId, List<Mate> all, int max) {
        List<Mate> out = new ArrayList<>();
        if (all == null || max <= 0) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (Mate m : all) {
            if (m == null || m.id == null || m.id.isEmpty() || m.id.equals(selfId) || !seen.add(m.id)) {
                continue;
            }
            out.add(m);
        }
        out.sort(Comparator.comparingDouble(Mate::share).thenComparing(m -> m.name == null ? "" : m.name));
        while (out.size() > max) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Tor 3.5 (Simon 08.11.): a mate's state, shown in the list's colours. */
    public enum State { WELL, HURT, LOW, DOWN }

    /** Green above 60 % life, yellow above 30 %, red below, grey when fallen. */
    public static State state(Mate m) {
        if (m == null || m.health <= 0f) {
            return State.DOWN;
        }
        float share = m.share();
        return share > 0.6f ? State.WELL : share > 0.3f ? State.HURT : State.LOW;
    }

    /** Text colour of a state (the bar colours' light tone; grey for the fallen). */
    public static int stateColor(State state) {
        return switch (state) {
            case WELL -> 0xFF7FAF45;
            case HURT -> 0xFFE0B04A;
            case LOW -> 0xFFD8443A;
            case DOWN -> 0xFF8A8078;
        };
    }

    /** Hearts of life as shown ("7.5", "10"): half a heart per point, a scratch still shows half a heart. */
    public static String hearts(float health) {
        if (health <= 0f) {
            return "0";
        }
        int halves = (int) Math.ceil(health - 1e-4f);
        return halves % 2 == 0 ? String.valueOf(halves / 2) : (halves / 2) + ".5";
    }

    /**
     * Which group a player's list shows (Tor 3.5): the siege's defenders first, then a war camp's fighters,
     * then a dungeon's (the playtest runs through these three). Null: in none of them, no list.
     */
    public static List<String> groupOf(String selfId, List<String> siege, List<String> camp, List<String> dungeon) {
        for (List<String> group : java.util.Arrays.asList(siege, camp, dungeon)) {
            if (group != null && group.contains(selfId)) {
                return group;
            }
        }
        return null;
    }

    /** Body and highlight colour of a mate's bar: red-brown palette of the kit HUD, gold-green when well. */
    public static int[] barColors(float share) {
        if (share > 0.6f) {
            return new int[]{0xFF4E7A2A, 0xFF7FAF45};
        }
        if (share > 0.3f) {
            return new int[]{0xFFB0802A, 0xFFE0B04A};
        }
        return new int[]{0xFFA8261F, 0xFFD8443A};
    }
}
