package com.mki.nightsiege.core;

/**
 * Experience from sieges: every wave a defender survives counts, and a number
 * of survived waves makes one point. Players who missed nights catch up: in the
 * next night they stand in, every wave counts once more for each night missed.
 */
public final class WaveXp {

    private WaveXp() {
    }

    /** How many times a wave counts for a player: missed nights plus one, at most {@code max}. */
    public static int catchUp(int nightsHeld, int lastNight, int max) {
        int missed = Math.max(0, nightsHeld - Math.max(-1, lastNight) - 1);
        return Math.max(1, Math.min(Math.max(1, max), missed + 1));
    }

    /**
     * Adds survived waves to a player and turns full sets into points.
     * Returns the points earned now.
     */
    public static int survive(PlayerSkills skills, int waves, int wavesPerPoint) {
        if (waves <= 0) {
            return 0;
        }
        int per = Math.max(1, wavesPerPoint);
        skills.waveCredit = Math.max(0, skills.waveCredit) + waves;
        int points = skills.waveCredit / per;
        skills.waveCredit -= points * per;
        skills.award(points);
        return points;
    }
}
