package com.mki.nightsiege.core;

import java.util.Random;

/**
 * Tor 12 F (Simon 17.11. 15:23: "Dann dürfen Monster wieder im 360° Radius spannen"): every wave splits into
 * raidGroupsMin to raidGroupsMax groups around the base, spread evenly and turned anew each wave. With a
 * spawn arc set in the config the groups spread over the arc instead. Game angles: 0 east, π/2 south.
 */
public final class WaveGroups {

    private WaveGroups() {
    }

    /** How many groups the next wave has: raidGroupsMin to raidGroupsMax (at least 1). */
    public static int count(SiegeConfig cfg, Random random) {
        int lo = Math.max(1, Math.min(cfg.raidGroupsMin, cfg.raidGroupsMax));
        int hi = Math.max(lo, cfg.raidGroupsMax);
        return lo + random.nextInt(hi - lo + 1);
    }

    /** Width of one group's step in radians: the circle (or the arc) over {@code n}. */
    public static double step(SiegeConfig cfg, int n) {
        return Math.toRadians(SpawnArc.widthDegrees(cfg)) / Math.max(1, n);
    }

    private static double start(SiegeConfig cfg) {
        return SpawnArc.all(cfg) ? 0.0 : Math.toRadians(cfg.spawnArcFrom - 90.0);
    }

    /** The {@code n} group angles, evenly apart, turned by {@code turn} (0 to 1 of a step). */
    public static double[] angles(SiegeConfig cfg, int n, double turn) {
        int count = Math.max(1, n);
        double step = step(cfg, count);
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = wrap(start(cfg) + (i + turn) * step);
        }
        return out;
    }

    /** The turn that puts one group right on {@code angle} (a gate). */
    public static double turnTowards(SiegeConfig cfg, int n, double angle) {
        double t = wrap(angle - start(cfg)) / step(cfg, n);
        return t - Math.floor(t);
    }

    static double wrap(double a) {
        double full = 2 * Math.PI;
        return ((a % full) + full) % full;
    }
}
