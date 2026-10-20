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
    /** Melee reach in blocks. */
    REACH(3.0, Target.PLAYER),

    /** Extra seconds between two waves. */
    WAVE_DELAY(0.0, Target.SIEGE),
    /** Extra blocks the attackers have to cross. */
    SPAWN_DISTANCE(0.0, Target.SIEGE),

    /** Pulls a half drawn shot towards full power. */
    DRAW_ASSIST(0.0, Target.ARROW),
    /** A second arrow beside the first. */
    DOUBLE_SHOT(0.0, Target.ARROW),
    /** Flat extra damage on arrows from a bow or crossbow. */
    ARROW_DAMAGE(0.0, Target.ARROW),
    /** Flat extra damage on bolts from a crossbow only. */
    CROSSBOW_DAMAGE(0.0, Target.ARROW),
    /** Share of a bolt's damage that also hits the monsters around the target. */
    SHRAPNEL(0.0, Target.ARROW),
    /** Arrow hits mark the target: it glows and takes this much more damage. */
    MARK(0.0, Target.ARROW),

    /** Unlocks a trap; every further level is one tier stronger. */
    TRAP(0.0, Target.TRAP),
    /** Raises the tier of one trap above what its own node allows. */
    TRAP_BOOST(0.0, Target.TRAP),

    /** Melee bonus that grows the lower the fighter's health: amount at zero health. */
    RAGE(0.0, Target.COMBAT),
    /** Share of melee damage dealt that heals the fighter. */
    LIFESTEAL(0.0, Target.COMBAT),
    /** Chance for a melee hit to deal half again as much. */
    CRIT_CHANCE(0.0, Target.COMBAT),
    /** Extra melee damage against the champion of a siege. */
    CHAMPION_BANE(0.0, Target.COMBAT),
    /** Damage thrown back at a monster whose hit was caught on a shield. */
    COUNTER(0.0, Target.COMBAT),
    /** Health restored to nearby players every few seconds. */
    HEAL_AURA(0.0, Target.COMBAT),
    /** Spear hits also strike the monster behind, for this share of the damage. */
    IMPALE(0.0, Target.COMBAT),
    /** Axe hits make the target take this much more damage for a while. */
    SUNDER(0.0, Target.COMBAT),
    /** Flat extra damage on axe hits, which also stagger. */
    HEAVY_BLOW(0.0, Target.COMBAT),
    /** Extra melee damage against the undead. */
    SMITE(0.0, Target.COMBAT),
    /** Once a night a falling teammate is caught. Fires by itself. */
    GUARDIAN_ANGEL(0.0, Target.COMBAT),

    // Ultimates: one per path, fired with the ultimate key. The level is the rank.
    ARROW_STORM(0.0, Target.ULTIMATE),
    POWER_SHOT(0.0, Target.ULTIMATE),
    BOLT_VOLLEY(0.0, Target.ULTIMATE),
    HUNTERS_MARK(0.0, Target.ULTIMATE),
    WHIRLWIND(0.0, Target.ULTIMATE),
    EXECUTE(0.0, Target.ULTIMATE),
    LANCE_THRUST(0.0, Target.ULTIMATE),
    GROUND_SLAM(0.0, Target.ULTIMATE),
    TAUNT(0.0, Target.ULTIMATE),
    MASS_HEAL(0.0, Target.ULTIMATE),
    SHIELD_CHARGE(0.0, Target.ULTIMATE),
    HOLY_LIGHT(0.0, Target.ULTIMATE),
    BOMBARDMENT(0.0, Target.ULTIMATE),
    OVERCHARGE(0.0, Target.ULTIMATE),
    TOXIC_CLOUD(0.0, Target.ULTIMATE),
    THUNDERSTORM(0.0, Target.ULTIMATE);

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
