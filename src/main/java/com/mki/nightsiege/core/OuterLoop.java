package com.mki.nightsiege.core;

/**
 * The outer loop of the smart night (Tor 6.6, Formel C): a server-wide offset on the life of the normal
 * monsters, moved between real nights. A won night +smartNightOuterWin, a lost one −smartNightOuterLoss
 * (0.02 / 0.047: the ratio 2.33 settles at 70 % won nights), never beyond ±smartNightOuterCap.
 * Test, playtest, war leader and bastion nights neither count nor feel it. The offset lives in the
 * world's save (SiegeSave.outerLoop), so a restart keeps it.
 */
public final class OuterLoop {

    private OuterLoop() {
    }

    /** The offset after a real night: won +win, lost −loss, inside ±cap. */
    public static double next(double offset, boolean won, SiegeConfig cfg) {
        double step = won ? cfg.smartNightOuterWin : -cfg.smartNightOuterLoss;
        return clamp(offset + step, cfg);
    }

    /** The offset inside its bounds (a hand-edited save or a smaller cap). */
    public static double clamp(double offset, SiegeConfig cfg) {
        if (Double.isNaN(offset) || Double.isInfinite(offset)) {
            return 0.0;
        }
        double cap = Math.max(0.0, cfg.smartNightOuterCap);
        return Math.max(-cap, Math.min(cap, offset));
    }

    /** Life factor of the normal monsters of a real night (1 when the loop is off). */
    public static double factor(double offset, SiegeConfig cfg) {
        return cfg.smartNightOuterLoop ? 1.0 + clamp(offset, cfg) : 1.0;
    }

    /**
     * Formel C's measure: a night decided early, lost in its first third or with a pressure never above
     * {@link #CALM} (peak below 0 = no look was taken; then only the first rule counts).
     */
    public static boolean decidedEarly(boolean won, double elapsedShare, double peakStress) {
        boolean lostEarly = !won && elapsedShare < 1.0 / 3.0;
        boolean calm = peakStress >= 0 && peakStress <= CALM;
        return lostEarly || calm;
    }

    /** "Druck nie über 0,2": a night this calm was never in doubt. */
    public static final double CALM = 0.2;
}
