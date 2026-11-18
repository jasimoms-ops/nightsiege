package com.mki.nightsiege.core;

/**
 * Experience from sieges: every wave a defender survives counts, and a number
 * of survived waves makes one point (Tor 12 I: the old catch-up of missed nights is gone,
 * the Teil-Angleich lifts players who fell behind, see game.LevelSync).
 */
public final class WaveXp {

    private WaveXp() {
    }

    /**
     * Adds survived waves to a player and turns full sets into points.
     * Returns the points earned now.
     */
    public static int survive(PlayerSkills skills, int waves, int wavesPerPoint) {
        return survive(skills, waves, wavesPerPoint, 1.0);
    }

    /**
     * The same with a factor on the points: full sets of waves give {@code multiplier}
     * points each; the fraction left over waits for the next set.
     */
    public static int survive(PlayerSkills skills, int waves, int wavesPerPoint, double multiplier) {
        if (waves <= 0) {
            return 0;
        }
        int per = Math.max(1, wavesPerPoint);
        skills.waveCredit = Math.max(0, skills.waveCredit) + waves;
        int sets = skills.waveCredit / per;
        skills.waveCredit -= sets * per;
        double gained = sets * Math.max(0.0, multiplier) + Math.max(0.0, skills.pointFraction);
        int points = (int) Math.floor(gained + 1e-9);
        skills.pointFraction = Math.max(0.0, gained - points);
        skills.award(points);
        return points;
    }
}
