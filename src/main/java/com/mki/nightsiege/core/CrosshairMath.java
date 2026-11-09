package com.mki.nightsiege.core;

/** Where the vanilla crosshair's middle pixel is, and a rounding that keeps a ring symmetric (Simon 09.11.). */
public final class CrosshairMath {
    private CrosshairMath() {
    }

    /** The middle pixel of the 15-pixel vanilla crosshair drawn at (size - 15) / 2. */
    public static int center(int guiScaledSize) {
        return (guiScaledSize - 15) / 2 + 7;
    }

    /** Rounds away from zero alike on both sides (+6.5 → 7, −6.5 → −7), unlike Math.round (−6.5 → −6). */
    public static int symRound(double v) {
        return (int) Math.signum(v) * (int) Math.round(Math.abs(v));
    }
}
