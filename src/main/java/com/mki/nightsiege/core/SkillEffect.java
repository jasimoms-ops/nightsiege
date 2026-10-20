package com.mki.nightsiege.core;

/**
 * What a skill actually changes.
 *
 * <p>Player effects carry Minecraft's own default as their base; the mod adds
 * its bonus on top. Siege effects instead shift the parameters of the night and
 * never touch a player attribute.</p>
 */
public enum SkillEffect {
    MAX_HEALTH(20.0, Target.PLAYER),
    ATTACK_DAMAGE(1.0, Target.PLAYER),
    ATTACK_SPEED(4.0, Target.PLAYER),
    ARMOR(0.0, Target.PLAYER),
    MOVEMENT_SPEED(0.1, Target.PLAYER),

    /** Extra seconds between two waves. */
    WAVE_DELAY(0.0, Target.SIEGE),
    /** Extra blocks the attackers have to cross before they reach you. */
    SPAWN_DISTANCE(0.0, Target.SIEGE),

    /** Chance for a bow to loose a second arrow. Defined, not yet wired up. */
    ARROW_DOUBLE(0.0, Target.PREVIEW),
    /** Faster arrows. Defined, not yet wired up. */
    ARROW_SPEED(0.0, Target.PREVIEW);

    public enum Target { PLAYER, SIEGE, PREVIEW }

    private final double playerBase;
    private final Target target;

    SkillEffect(double playerBase, Target target) {
        this.playerBase = playerBase;
        this.target = target;
    }

    /** The value a player has without any skill. */
    public double playerBase() {
        return playerBase;
    }

    public Target target() {
        return target;
    }

    /** True once the effect is actually applied in game. */
    public boolean isLive() {
        return target != Target.PREVIEW;
    }
}
