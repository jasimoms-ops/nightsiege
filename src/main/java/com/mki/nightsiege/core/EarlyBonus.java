package com.mki.nightsiege.core;

/**
 * Tor 9b (Spieltest 09.11.2026): help for the first world levels, before the skill points are there.
 * P4: every trapper has a base tripwire (Slowness I 2 s) without a skill point; P5: shield fighter and
 * bulwark have more life early on.
 */
public final class EarlyBonus {

    private EarlyBonus() {
    }

    /**
     * Extra max health: {@code atOne} on world level 1, falling in a straight line to 0 on level
     * {@code until} and staying 0 above it, in whole points (6, 5, 5, 4, 3, 3, 2, 1, 1, 0 for 6 and 10).
     */
    public static double health(double atOne, double until, int level) {
        if (atOne <= 0 || until <= 1 || level >= until) {
            return 0.0;
        }
        double share = (until - Math.max(1, level)) / (until - 1);
        return Math.round(atOne * share);
    }

    /**
     * The tripwire's slow as {ticks, amplifier}, or null when the player has none. A trapper has the
     * base one ("a_netbolt" trip/triplevel); the learned tripwire replaces it where it is bigger (no
     * doubling), the trap master holds them fast.
     */
    public static int[] tripSlow(boolean trapper, boolean tripwire, boolean master) {
        int ticks = 0;
        int amp = -1;
        if (trapper) {
            ticks = (int) SkillValues.get("a_netbolt", "trip", 40);
            amp = (int) SkillValues.get("a_netbolt", "triplevel", 0);
        }
        if (tripwire) {
            ticks = Math.max(ticks, (int) SkillValues.get("a_tripwire", "dur", 40));
            amp = Math.max(amp, master ? 9 : 1);
        }
        return ticks <= 0 || amp < 0 ? null : new int[] {ticks, amp};
    }
}
