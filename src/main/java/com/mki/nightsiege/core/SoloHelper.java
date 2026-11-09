package com.mki.nightsiege.core;

/**
 * Tor 6.4, only the frame (Chef 09.11.; decided after Tor 8): "Solo unter 65 % -> Helfer statt weniger Monster".
 * When the solo nights of the final simulation win less than {@code soloHelperBelow} (0.65) on their level, a lone
 * defender is to get a helper at his side (like Deep Rock Galactic's drone Bosco) instead of a smaller night, so solo
 * keeps the same monsters as everyone. Switched on by {@code soloHelper} (off by default); what the helper is and
 * does is not built yet, the night only asks {@link #wanted} and logs it.
 */
public final class SoloHelper {
    private SoloHelper() {
    }

    /** True when tonight's lone defender should get the helper: switched on and exactly one defender here. */
    public static boolean wanted(SiegeConfig cfg, int presentDefenders) {
        return cfg != null && cfg.soloHelper && presentDefenders == 1;
    }
}
