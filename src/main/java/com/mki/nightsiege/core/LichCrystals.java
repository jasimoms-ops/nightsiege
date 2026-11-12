package com.mki.nightsiege.core;

import java.util.function.IntPredicate;

/**
 * Where the bone lich's soul crystals stand (Chef 12.11. 09:50, boss sim: one stood 48 blocks out, a roof
 * came before the floor): at most {@link #MAX_RADIUS} from the core, on the standable ground nearest the
 * core's own level.
 */
public final class LichCrystals {
    /** At most this far from the core (blocks); the lich himself hovers at most 30 out. */
    public static final double MAX_RADIUS = 24.0;
    /** Heights tried, relative to one above the core: its own level first, then one down, one up, and so on. */
    static final int[] HEIGHTS = {0, -1, 1, -2, 2, -3, 3, -4, -5};
    /** No standable height found. */
    public static final int NONE = Integer.MIN_VALUE;

    private LichCrystals() {
    }

    /** Distance from the core: three inside the base's edge in that direction, at most {@link #MAX_RADIUS}. */
    public static double radius(double edge) {
        return Math.min(MAX_RADIUS, edge > 0 ? Math.max(3.0, edge - 3.0) : 7.0);
    }

    /** The first height out of {@link #HEIGHTS} that is standable, or {@link #NONE}. */
    public static int pick(IntPredicate standable) {
        for (int dy : HEIGHTS) {
            if (standable.test(dy)) {
                return dy;
            }
        }
        return NONE;
    }
}
