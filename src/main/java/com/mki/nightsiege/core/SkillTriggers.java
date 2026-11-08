package com.mki.nightsiege.core;

/** When a skill fires: plain rules, kept apart from the game classes so a unit test can check them. */
public final class SkillTriggers {

    private SkillTriggers() {
    }

    /**
     * Erdstoß (w_earthshock): a hit while falling, like Minecraft's jump hit. The old rule asked for more
     * than 1.5 blocks of fall, but a jump only lifts the player about 1.25, so it never fired from the ground.
     */
    public static boolean earthshock(double fallDistance, boolean onGround) {
        return !onGround && fallDistance > SkillValues.get("w_earthshock", "fall", 0.0);
    }

    /** Köcherwart (a_quiver): a shot of Infinity (nothing to give back) flies a free extra arrow instead. */
    public static boolean quiverTwin(boolean bow, boolean crossbow, boolean infinityShot) {
        return bow && infinityShot;
    }
}
