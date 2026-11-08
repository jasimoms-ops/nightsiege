package com.mki.nightsiege.core;

/** When a skill fires: plain rules, kept apart from the game classes so a unit test can check them. */
public final class SkillTriggers {

    private SkillTriggers() {
    }

    /**
     * Erdstoß (w_earthshock): a hit while falling, like Minecraft's jump hit. The old rule asked for more
     * than 1.5 blocks of fall, but a jump only lifts the player about 1.25, so it never fired from the ground. Now it fires exactly with Minecraft's jump crit.
     */
    public static boolean earthshock(double fallDistance, boolean onGround, float charge, boolean climbing,
                                     boolean inWater, boolean riding, boolean blind) {
        // Minecraft's jump crit: a charged swing (> 0.9) in the air, not on a ladder, in water, riding or blind.
        return charge > 0.9f && !onGround && !climbing && !inWater && !riding && !blind
                && fallDistance > SkillValues.get("w_earthshock", "fall", 0.0);
    }

    /** Köcherwart (a_quiver): a shot of Infinity (nothing to give back) flies a free extra arrow instead. */
    public static boolean quiverTwin(boolean bow, boolean crossbow, boolean infinityShot) {
        return (bow || crossbow) && infinityShot;   // Tor 2.2: the crossbow way got nothing, though its kit has Infinity too
    }
}
