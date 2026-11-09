package com.mki.nightsiege.core;

/**
 * The latecomer system (Tor 6.7, formula D; it replaces the multiple waves "missed + 1, at most 4 times").
 * <ul>
 *   <li>Floor: at the start of a night a defender has at least the points of a steady player minus
 *       {@code floorNights} nights.</li>
 *   <li>Pool: every night missed adds one night's points, at most {@code floorNights} nights and never more
 *       than the gap to a steady player. While it lasts, every wave the team holds gives its points once
 *       more, also to the fallen; the extra comes out of the pool.</li>
 *   <li>Mentor: the others in a night with a pool player count a mentor night in the hall of fame.</li>
 * </ul>
 * Pure sums; the expected points come in from {@code TestKit.pointsBefore} (the same sum as the test kits).
 */
public final class CatchUp {

    private CatchUp() {
    }

    /** Nights missed since the last one stood in ({@code lastNight} -1: never). */
    public static int missed(int nightNumber, int lastNight) {
        return Math.max(0, nightNumber - Math.max(-1, lastNight) - 1);
    }

    /** Points the floor adds now: up to {@code floorPoints}, nothing for who has that many. */
    public static int floorAdd(int earned, int floorPoints) {
        return Math.max(0, floorPoints - Math.max(0, earned));
    }

    /**
     * The pool after a defender joins: {@code perNight} more for every night missed, at most {@code cap}
     * and never above the gap to a steady player ({@code expected - earned}).
     */
    public static int pool(int pool, int missed, int perNight, int cap, int expected, int earned) {
        long grown = (long) Math.max(0, pool) + (long) Math.max(0, missed) * Math.max(0, perNight);
        int gap = Math.max(0, expected - Math.max(0, earned));
        return (int) Math.max(0, Math.min(Math.min(grown, Math.max(0, cap)), gap));
    }

    /**
     * One wave held by the team: the pool pays the wave's points once more ({@code waves} wave credits,
     * {@code wavesPerPoint} of them a point, each worth {@code multiplier}). Returns the points added.
     */
    public static int bonus(PlayerSkills skills, int waves, int wavesPerPoint, double multiplier) {
        if (skills.catchUpPool <= 0 || waves <= 0) {
            skills.catchUpFraction = 0.0;
            return 0;
        }
        double gained = Math.max(0.0, skills.catchUpFraction)
                + waves * Math.max(0.0, multiplier) / Math.max(1, wavesPerPoint);
        int points = Math.min(skills.catchUpPool, (int) Math.floor(gained + 1e-9));
        skills.catchUpPool -= points;
        skills.catchUpFraction = skills.catchUpPool > 0 ? Math.max(0.0, gained - points) : 0.0;
        skills.award(points);
        return points;
    }
}
