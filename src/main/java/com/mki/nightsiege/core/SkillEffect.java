package com.mki.nightsiege.core;

/**
 * What a skill actually changes.
 *
 * <p>Player effects carry Minecraft's own default as their base; the mod adds
 * its bonus on top. Siege effects shift the parameters of the night. Arrow
 * effects are applied to the arrows a player looses. Trap effects unlock and
 * upgrade the engineer's traps.</p>
 */
public enum SkillEffect {
    MAX_HEALTH(20.0, Target.PLAYER),
    ATTACK_DAMAGE(1.0, Target.PLAYER),
    ATTACK_SPEED(4.0, Target.PLAYER),
    ARMOR(0.0, Target.PLAYER),
    MOVEMENT_SPEED(0.1, Target.PLAYER),
    KNOCKBACK_RESIST(0.0, Target.PLAYER),

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
    POWER_SHOT(0.0, Target.ARROW),

    /** Unlocks a trap; every further level is one tier stronger. */
    TRAP(0.0, Target.TRAP),

    /** Melee bonus that grows the lower the warrior's health: amount at zero health. */
    RAGE(0.0, Target.COMBAT),
    /** Share of melee damage dealt that heals the warrior. */
    LIFESTEAL(0.0, Target.COMBAT),
    /** Chance for a melee hit to deal half again as much. */
    CRIT_CHANCE(0.0, Target.COMBAT),
    /** Extra melee damage against the champion of a siege. */
    CHAMPION_BANE(0.0, Target.COMBAT),
    /** Damage thrown back at a monster whose hit was caught on a shield. */
    COUNTER(0.0, Target.COMBAT),
    /** Health restored to nearby players every few seconds. */
    HEAL_AURA(0.0, Target.COMBAT),

    /** Ultimate: spin and hit everything around. Level is its rank. */
    WHIRLWIND(0.0, Target.ULTIMATE),
    /** Ultimate: finish a badly hurt monster outright. */
    EXECUTE(0.0, Target.ULTIMATE),
    /** Ultimate: ram the shield forward and throw back everything in front. */
    SHIELD_CHARGE(0.0, Target.ULTIMATE),
    /** Ultimate: pull every monster around onto the guardian, who hardens. */
    TAUNT(0.0, Target.ULTIMATE),
    /** Ultimate, passive: once a night a falling teammate is caught. */
    GUARDIAN_ANGEL(0.0, Target.ULTIMATE);

    public enum Target { PLAYER, SIEGE, ARROW, TRAP, COMBAT, ULTIMATE }

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
