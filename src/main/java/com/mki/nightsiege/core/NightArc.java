package com.mki.nightsiege.core;

import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Tor 12 F (Simon 17.11. 15:30: "die monster können jaa pro nacht immer von einer seite kommen also eine nacht
 * süd west und bist süd ost andere nacht nord west bis nord ost usw"): at the start of every night a side of
 * spawnArcWidth degrees (90) round one of the eight compass directions, at random and never the last night's.
 * Over many nights the monsters come from all around. A side without spawn ground gives way to the next one.
 * Directions: 0 north, 1 north-east … 7 north-west (compass degrees = 45 × direction, clockwise from north).
 */
public final class NightArc {

    public static final int NONE = -1;
    public static final int COUNT = 8;
    private static final String[] KEYS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private NightArc() {
    }

    /**
     * Tonight's direction: a random one other than {@code last}; without spawn ground there ({@code hasGround})
     * the next one clockwise (never {@code last}). If no other has ground, the random one.
     */
    public static int pick(int last, Random random, IntPredicate hasGround) {
        int others = last >= 0 && last < COUNT ? COUNT - 1 : COUNT;
        int roll = random.nextInt(others);
        int first = last >= 0 && last < COUNT && roll >= last ? roll + 1 : roll;
        for (int step = 0; step < COUNT; step++) {
            int d = (first + step) % COUNT;
            if (d != last && hasGround.test(d)) {
                return d;
            }
        }
        return first;
    }

    /** The side's start and end in compass degrees (spawnArcFrom/To of the night). */
    public static double from(int direction, double width) {
        return direction * 45.0 - width / 2.0;
    }

    public static double to(int direction, double width) {
        return direction * 45.0 + width / 2.0;
    }

    /** The direction's middle as a game angle (0 east, π/2 south). */
    public static double gameAngle(int direction) {
        return Math.toRadians(direction * 45.0 - 90.0);
    }

    /** The compass key ("sw") of a compass angle in degrees, for "from south-west to south-east". */
    public static String key(double compassDegrees) {
        return KEYS[(int) Math.floorMod(Math.round(compassDegrees / 45.0), (long) COUNT)];
    }
}
