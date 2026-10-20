package com.mki.nightsiege.core;

/**
 * What a skill actually changes.
 *
 * <p>Player effects carry Minecraft's own default as their base; the mod adds
 * its bonus on top. Siege effects shift the parameters of the night. Arrow
 * effects are applied to the arrows a player looses.</p>
 */
public enum SkillEffect {
    MAX_HEALTH(20.0, Target.PLAYER),
    ATTACK_DAMAGE(1.0, Target.PLAYER),
    ATTACK_SPEED(4.0, Target.PLAYER),
    ARMOR(0.0, Target.PLAYER),
    MOVEMENT_SPEED(0.1, Target.PLAYER),

    /** Extra seconds between two waves. */
    WAVE_DELAY(0.0, Target.SIEGE),
    /** Extra blocks the attackers have to cross. */
    SPAWN_DISTANCE(0.0, Target.SIEGE),

    /** Pulls a half drawn shot towards full power. */
    DRAW_ASSIST(0.0, Target.ARROW),
    /** A second arrow beside the first. */
    DOUBLE_SHOT(0.0, Target.ARROW),
    /** Flat extra damage on arrows from a bow. */
    ARROW_DAMAGE(0.0, Target.ARROW),
    /** A charged shot on a cooldown that hits far harder. */
    POWER_SHOT(0.0, Target.ARROW);

    public enum Target { PLAYER, SIEGE, ARROW }

    private final double playerBase;
    private final Target target;

    SkillEffect(double playerBase, Target target) {
        this.playerBase = playerBase;
        this.target = target;
    }

    public double playerBase() {
        return playerBase;
    }

    public Target target() {
        return target;
    }

    /** Everything is wired up now; kept so the window can still mark previews. */
    public boolean isLive() {
        return true;
    }
}
