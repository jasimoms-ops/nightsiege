package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The siege machines of the war camp (concept 27 October 2026, Simon: "übernehmen wir alles
 * genauso"). They stand 40–60 blocks in front of the wall, have two crew each and their own
 * life; what they break comes back after the night.
 */
public enum SiegeMachineKind {
    /** Throws boulders that tear holes in the wall. */
    TREBUCHET("trebuchet", 160, 9.0),
    /** A high arc over the wall, a red circle warns where it lands. */
    MORTAR("mortar", 120, 7.0),
    /** Shoots at players on the wall and in towers, behind a wooden shield. */
    BALLISTA("ballista", 110, 4.0),
    /** Fire pots: the ground and wood catch fire. */
    FIRE("fire", 120, 8.0),
    /** Carcasses that lie as poison clouds. */
    PLAGUE("plague", 120, 10.0),
    /** Hooks with a rope on the wall: monsters climb up. */
    HOOK("hook", 110, 14.0);

    private final String id;
    private final int baseHealth;
    private final double seconds;

    SiegeMachineKind(String id, int baseHealth, double seconds) {
        this.id = id;
        this.baseHealth = baseHealth;
        this.seconds = seconds;
    }

    public String id() {
        return id;
    }

    /** Life at level 1; more with the level (see {@link #health}). */
    public int baseHealth() {
        return baseHealth;
    }

    /** Seconds between two shots. */
    public double seconds() {
        return seconds;
    }

    public String translationKey() {
        return "nightsiege.machine." + id;
    }

    /** Life of a machine at this level: the base, +6 % per level. */
    public double health(int level) {
        return baseHealth * (1.0 + 0.06 * Math.max(0, level - 1));
    }

    public static SiegeMachineKind byId(String id) {
        for (SiegeMachineKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Machines in a night of this level: 1–5 one, 6–10 two, 11–15 three, 16–20 four, then one
     * more every five levels (21–25 five, 26–30 six …).
     */
    public static int countFor(int level) {
        int l = Math.max(1, level);
        return (l - 1) / 5 + 1;
    }

    /** Which machines come: every kind once before any comes twice, in a random order. */
    public static List<SiegeMachineKind> pick(int count, Random random) {
        List<SiegeMachineKind> out = new ArrayList<>();
        List<SiegeMachineKind> bag = new ArrayList<>();
        while (out.size() < count) {
            if (bag.isEmpty()) {
                bag.addAll(List.of(values()));
            }
            out.add(bag.remove(random.nextInt(bag.size())));
        }
        return out;
    }
}
