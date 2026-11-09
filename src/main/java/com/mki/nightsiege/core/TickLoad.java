package com.mki.nightsiege.core;

/**
 * Tor 6.4: the server's tick times of a stretch of the night, for the 95th percentile (the live cap belongs where
 * it reaches 50 ms, measured in Tor 7/8). A histogram of 0.5 ms steps up to 250 ms, longer ticks in the last step.
 */
public final class TickLoad {
    private static final double STEP_MS = 0.5;
    private final int[] counts = new int[501];
    private int total;

    public void add(double ms) {
        int i = (int) Math.floor(Math.max(0.0, ms) / STEP_MS);
        counts[Math.min(counts.length - 1, i)]++;
        total++;
    }

    public int count() {
        return total;
    }

    /** The tick time p (0..1) of all ticks lie at or under (upper edge of its step), 0 without ticks. */
    public double percentile(double p) {
        if (total == 0) {
            return 0.0;
        }
        int need = (int) Math.ceil(Math.max(0.0, Math.min(1.0, p)) * total);
        int seen = 0;
        for (int i = 0; i < counts.length; i++) {
            seen += counts[i];
            if (seen >= Math.max(1, need)) {
                return (i + 1) * STEP_MS;
            }
        }
        return counts.length * STEP_MS;
    }

    public void clear() {
        java.util.Arrays.fill(counts, 0);
        total = 0;
    }
}
