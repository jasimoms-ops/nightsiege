package com.mki.nightsiege.core;

import java.util.Random;

/**
 * The side the night's monsters come from (Simon 08.11.: "die Gegner-Wellen nur noch aus Nordost bis Südost
 * vom Core aus"). Set in the config as compass degrees, clockwise from north: {@code spawnArcFrom} 45 and
 * {@code spawnArcTo} 135 is north-east over east to south-east. 0 to 360 (the default) is all around.
 * The angles here are the game's: 0 east, π/2 south, as {@code atan2(z, x)} from the core.
 */
public final class SpawnArc {

    private SpawnArc() {
    }

    /** Width of the arc in degrees, 360 for all around. */
    public static double widthDegrees(SiegeConfig cfg) {
        double span = cfg.spawnArcTo - cfg.spawnArcFrom;
        if (span >= 360.0 || span <= -360.0) {
            return 360.0;
        }
        double w = ((span % 360.0) + 360.0) % 360.0;
        return w == 0.0 ? 360.0 : w;
    }

    public static boolean all(SiegeConfig cfg) {
        return widthDegrees(cfg) >= 360.0;
    }

    /** The arc's start as a game angle (compass − 90°). */
    private static double start(SiegeConfig cfg) {
        return Math.toRadians(cfg.spawnArcFrom - 90.0);
    }

    /** Whether a game angle lies in the arc. */
    public static boolean contains(SiegeConfig cfg, double angle) {
        if (all(cfg) || Double.isNaN(angle)) {
            return true;
        }
        double d = angle - start(cfg);
        d = ((d % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return d <= Math.toRadians(widthDegrees(cfg)) + 1e-9;
    }

    /** A random game angle in the arc (all around without one). */
    public static double random(SiegeConfig cfg, Random random) {
        return start(cfg) + random.nextDouble() * Math.toRadians(widthDegrees(cfg));
    }

    /** The angle itself if it lies in the arc, else a random one in it. */
    public static double fold(SiegeConfig cfg, double angle, Random random) {
        return contains(cfg, angle) ? angle : random(cfg, random);
    }

    /** The {@code i}-th of {@code n} angles spread evenly over the arc (for machines), starting at an offset. */
    public static double spread(SiegeConfig cfg, double offset, int i, int n) {
        double w = Math.toRadians(widthDegrees(cfg));
        if (all(cfg)) {
            return offset + i * w / Math.max(1, n);
        }
        return start(cfg) + (i + 0.5) * w / Math.max(1, n);
    }
}
