package com.mki.nightsiege.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Z4: the particle budget of the ability effects, per player who caused them (Simon 12.11.: 6 players
 * and 120 monsters may cost at most 10 % FPS). A bucket per source: it refills {@link #REFILL} a tick up
 * to {@link #CAPACITY}; on top of that all sources together get at most {@link #FRAME_CAP} a tick.
 * Plain Java, no Minecraft: the client keeps one, the tests check it.
 *
 * <p>The scale of the full effects against the old ones (Simon's numbers): particles {@link #SIZE}× as
 * big, {@link #COUNT}× as many, {@link #LIFE}× as long to see.
 */
public final class FxBudget {

    public static final double SIZE = 1.75;
    public static final double COUNT = 1.5;
    public static final double LIFE = 1.5;
    /** Smoke, clouds, poofs, explosions: size, count and time at most this (Chef 12.11. 18:10). */
    public static final double DIM = 1.25;

    /** Particles one player's effects may start at once (an ultimate fits). */
    public static final int CAPACITY = 480;
    /** Refill per tick and player: 240 a second on average. */
    public static final int REFILL = 12;
    /** All sources together in one tick. */
    public static final int FRAME_CAP = 500;

    private final Map<Integer, int[]> buckets = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int frameLeft = FRAME_CAP;

    /**
     * How many of {@code wanted} particles of source {@code source} may start in game tick {@code now};
     * the granted ones are taken from the budget.
     */
    public int take(int source, int wanted, long now) {
        if (now != tick) {
            tick = now;
            frameLeft = FRAME_CAP;
        }
        int[] b = buckets.computeIfAbsent(source, k -> new int[]{CAPACITY, (int) now});
        long passed = Math.max(0, now - b[1]);
        b[0] = (int) Math.min(CAPACITY, b[0] + passed * REFILL);
        b[1] = (int) now;
        int granted = Math.max(0, Math.min(wanted, Math.min(b[0], frameLeft)));
        b[0] -= granted;
        frameLeft -= granted;
        return granted;
    }

    /** Forget all sources (new world). */
    public void clear() {
        buckets.clear();
        frameLeft = FRAME_CAP;
    }

    /** The most particles one source can start over {@code ticks} ticks from a full bucket. */
    public static int maxOver(int ticks) {
        return CAPACITY + REFILL * Math.max(0, ticks);
    }
}
