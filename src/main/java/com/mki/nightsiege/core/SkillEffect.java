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
    /** Digging monsters take this share longer for every block. */
    MASONRY(0.0, Target.SIEGE),
    /** Defenders on the wall: this share more ranged damage and this share less damage taken. */
    BATTLEMENTS(0.0, Target.SIEGE),
    /** Monsters outside the walls glow. */
    BEACON_FIRE(0.0, Target.SIEGE),
    /** After each night the base is repaired: level 1, 2, 3 mend 40, 70, 100 percent. */
    REBUILD(0.0, Target.SIEGE),

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

    // Warrior (melee boni only count with the weapon of the player's way)
    /** Extra attack speed while holding the weapon of the way. */
    WEAPON_SPEED(0.0, Target.COMBAT),
    /** Hits in quick succession: each one this much stronger, up to five. */
    COMBO(0.0, Target.COMBAT),
    /** Critical hits deal this much more on top. */
    CRIT_POWER(0.0, Target.COMBAT),
    /** Monsters close by are weakened again and again. */
    WARCRY(0.0, Target.COMBAT),
    /** Chance to catch a monster's blow and strike back. */
    PARRY(0.0, Target.COMBAT),
    /** Attack speed that grows the lower the fighter's health: amount at zero health. */
    FRENZY(0.0, Target.COMBAT),
    /** Critical hits give a short burst of strength. */
    CRIT_RAGE(0.0, Target.COMBAT),
    /** A parry heals this much. */
    PARRY_HEAL(0.0, Target.COMBAT),
    /** Chance to avoid a monster's damage altogether. */
    DODGE(0.0, Target.COMBAT),
    /** Melee hits are this much stronger but cost a little health. */
    BLOOD_PRICE(0.0, Target.COMBAT),
    /** Share of a hit that also strikes the monsters next to the target. */
    CLEAVE(0.0, Target.COMBAT),
    /** Extra damage against monsters that are still mostly healthy. */
    HEADHUNTER(0.0, Target.COMBAT),
    /** Players close by take less damage. */
    VOW(0.0, Target.COMBAT),
    /** After a kill the next hit is this much stronger. */
    MOMENTUM(0.0, Target.COMBAT),
    /** Cleave hits heal the fighter and set the undead on fire. */
    CRUSADER(0.0, Target.COMBAT),
    /** Critical hits stagger and deal this much more. */
    HEADSMAN(0.0, Target.COMBAT),
    /** Critical hits on the undead set them on fire and heal. */
    HOLY_STRIKE(0.0, Target.COMBAT),
    /** Hits while falling are this much stronger and shake the ground. */
    EARTHSHOCK(0.0, Target.COMBAT),
    /** Now and then a heavy blow is caught completely. */
    DIVINE_SHIELD(0.0, Target.COMBAT),
    // Archer (only with the weapon of the way)
    /** Chance that a shot does not use up the arrow. */
    QUIVER(0.0, Target.COMBAT),
    /** Arrows go on into this many more monsters behind the target. */
    PIERCE(0.0, Target.COMBAT),
    /** Extra damage per level on hits from more than 15 blocks away. */
    LONGSHOT(0.0, Target.COMBAT),
    /** Every fourth shot is a fan of three. */
    VOLLEY(0.0, Target.ARROW),
    /** Arrows throw monsters back. */
    ARROW_KNOCKBACK(0.0, Target.COMBAT),
    FIRE_ARROWS(0.0, Target.ARROW),
    POISON_ARROWS(0.0, Target.COMBAT),
    /** The extra arrows burn and hit harder. */
    FIRE_VOLLEY(0.0, Target.ARROW),
    /** Marked monsters take this much more again. */
    DEATH_MARK(0.0, Target.COMBAT),
    /** Poisoned monsters hit again spread the poison. */
    VENOM_BURST(0.0, Target.COMBAT),
    /** Chance of a critical arrow hit. */
    ARROW_CRIT(0.0, Target.COMBAT),
    /** A hit gives a moment of speed. */
    RETREAT(0.0, Target.COMBAT),
    /** Movement speed while holding a crossbow. */
    XBOW_SPEED(0.0, Target.COMBAT),
    /** Bolts crack armour: the target takes more damage for a while. */
    BOLT_SUNDER(0.0, Target.COMBAT),
    /** Bolts slow the target; seconds per level. */
    NET_BOLT(0.0, Target.COMBAT),
    /** Bolts burst; damage around the target. */
    EXPLOSIVE(0.0, Target.COMBAT),
    /** Crossbow shots fire two more bolts. */
    MULTI_BOLT(0.0, Target.ARROW),
    /** Bolts throw a net: everything around is slowed. */
    NET_FIELD(0.0, Target.COMBAT),
    /** Bolts hold the target in place for a moment. */
    SHACKLE(0.0, Target.COMBAT),
    /** Monsters coming close trip and are slowed. */
    TRIPWIRE(0.0, Target.COMBAT),
    /** Every bolt bursts, and bursts are bigger. */
    CLUSTER(0.0, Target.COMBAT),
    /** Netted or shackled monsters are marked. */
    HUNTER_NET(0.0, Target.COMBAT),
    /** The tripwire holds fast and hurts. */
    TRAPMASTER(0.0, Target.COMBAT),
    /** Sneaking: bolts hit this much harder. */
    BRACE(0.0, Target.COMBAT),
    /** A kill gives a burst of speed. */
    RELOAD_RUSH(0.0, Target.COMBAT),
    /** Ultimate of the trapper: a rain of nets. */
    NET_RAIN(0.0, Target.ULTIMATE),
    TEMPEST(0.0, Target.COMBAT),
    FIRESTORM(0.0, Target.COMBAT),
    SNIPER(0.0, Target.COMBAT),
    PACK(0.0, Target.COMBAT),
    BARRAGE(0.0, Target.COMBAT),
    WIDE_NET(0.0, Target.COMBAT),
    DEATHTRAP(0.0, Target.COMBAT),
    DEADEYE(0.0, Target.COMBAT),
    SKIRMISHER(0.0, Target.COMBAT),
    SIEGE_ENGINE(0.0, Target.COMBAT),
    WATCHFUL(0.0, Target.COMBAT),

    // Guardian
    /** Extra blocks of the radius in which monsters turn on the guardian. */
    AGGRO(0.0, Target.COMBAT),
    /** Armour and armour toughness while a shield is at hand (iron ward). */
    IRONWARD(0.0, Target.COMBAT),
    /** Blocked hits give a little absorption; the riposte may block longer. */
    SHIELD_WALL(0.0, Target.COMBAT),
    /** Less damage for every monster attacking the guardian (share per monster). */
    BASTION(0.0, Target.COMBAT),
    /** The shield action throws the shield: it bounces between monsters. */
    SHIELD_THROW(0.0, Target.COMBAT),
    /** A perfect block throws back everything in front. */
    SHIELD_BASH(0.0, Target.COMBAT),
    /** Absorption for every monster close by. */
    IRONHIDE(0.0, Target.COMBAT),
    /** Sneaking and blocking: much less damage from all sides, no knockback. */
    ANCHOR(0.0, Target.COMBAT),
    /** After a perfect block the next hit is much stronger. */
    COUNTERSTRIKE(0.0, Target.COMBAT),
    /** Monsters hitting the guardian in melee hurt themselves. */
    THORNS(0.0, Target.COMBAT),
    BOOMERANG(0.0, Target.COMBAT),
    VENGEANCE(0.0, Target.COMBAT),
    HEDGEHOG(0.0, Target.COMBAT),
    /** Low on health: a moment of strong resistance. */
    LAST_STAND(0.0, Target.COMBAT),
    /** Damage caught charges the next hit. */
    REVENGE(0.0, Target.COMBAT),
    /** Now and then the weakest player close by gets a barrier. */
    WARD(0.0, Target.COMBAT),
    /** Share more healing. */
    HEAL_POWER(0.0, Target.COMBAT),
    /** Players close by take less damage. */
    WARD_SIGN(0.0, Target.COMBAT),
    /** The shield action or a right click heals a teammate. */
    HEALING_HAND(0.0, Target.COMBAT),
    TRIAGE(0.0, Target.COMBAT),
    /** The shield action raises a dome monsters cannot enter. */
    DOME(0.0, Target.COMBAT),
    CLEANSE(0.0, Target.COMBAT),
    /** Extra blocks of the heal aura. */
    AURA_RANGE(0.0, Target.COMBAT),
    SELFLESS(0.0, Target.COMBAT),
    LIFEBOND(0.0, Target.COMBAT),
    WELLSPRING(0.0, Target.COMBAT),
    MIRACLE(0.0, Target.COMBAT),
    OVERFLOW(0.0, Target.COMBAT),
    GRACE(0.0, Target.COMBAT),
    DEBRIS(0.0, Target.COMBAT),
    RAMPART(0.0, Target.COMBAT),
    IRON_WILL(0.0, Target.COMBAT),
    SHARED_BURDEN(0.0, Target.COMBAT),
    REBIRTH(0.0, Target.COMBAT),
    LIFESPRING(0.0, Target.COMBAT),
    LIGHT_CIRCLE(0.0, Target.COMBAT),
    ETERNAL_WATCH(0.0, Target.COMBAT),
    LIVING_WALL(0.0, Target.COMBAT),
    BLADESHIELD(0.0, Target.COMBAT),
    SAINT(0.0, Target.COMBAT),
    PATRON(0.0, Target.COMBAT),

    // Engineer
    /** Extra energy of the remote. */
    REMOTE_ENERGY(0.0, Target.TRAP),
    /** Traps hold this share longer before they wear out. */
    TRAP_DURABILITY(0.0, Target.TRAP),
    /** Extra blocks of reach of the remote. */
    REMOTE_RANGE(0.0, Target.TRAP),
    /** Extra ticks a remote overcharge lasts. */
    OVERCHARGE_TIME(0.0, Target.TRAP),
    /** The remote's energy comes back this much faster. */
    RECHARGE(0.0, Target.TRAP),
    AMMO_BELT(0.0, Target.TRAP),
    /** Extra jumps of the tesla coil. */
    CHAIN_LIGHTNING(0.0, Target.TRAP),
    /** Marks of the remote count this much more. */
    SEEKER(0.0, Target.TRAP),
    /** Overcharge triples instead of doubles. */
    OVERVOLT(0.0, Target.TRAP),
    CHARGED_ARROWS(0.0, Target.TRAP),
    GUIDING_BEAM(0.0, Target.TRAP),
    NETWORK(0.0, Target.TRAP),
    BACKUP_POWER(0.0, Target.TRAP),
    /** Extra blocks of range of turrets and tesla coils. */
    TOWER_RANGE(0.0, Target.TRAP),
    SUSTAINED_FIRE(0.0, Target.TRAP),
    ARMOR_PIERCER(0.0, Target.TRAP),
    BALL_LIGHTNING(0.0, Target.TRAP),
    SUPERCONDUCTOR(0.0, Target.TRAP),
    FORTRESS(0.0, Target.TRAP),
    FIELD_TECH(0.0, Target.TRAP),
    /** More free traps every night. */
    FREE_TRAPS(0.0, Target.TRAP),
    /** Fired from afar, traps cost less and hit harder. */
    DETONATOR(0.0, Target.TRAP),
    SHRAPNEL_MINE(0.0, Target.TRAP),
    ACID(0.0, Target.TRAP),
    CHAIN_REACTION(0.0, Target.TRAP),
    ACCELERANT(0.0, Target.TRAP),
    NAPALM(0.0, Target.TRAP),
    PLAGUE_MINE(0.0, Target.TRAP),
    TARGET_BLAST(0.0, Target.TRAP),
    BLAST_POWER(0.0, Target.TRAP),
    SLOW_POISON(0.0, Target.TRAP),
    CARPET_BOMB(0.0, Target.TRAP),
    FIRE_BOMBS(0.0, Target.TRAP),
    PLAGUE(0.0, Target.TRAP),
    ACID_FOG(0.0, Target.TRAP),
    PYROMANIAC(0.0, Target.TRAP),
    PLAGUE_DOCTOR(0.0, Target.TRAP),

    // Masteries: change the ultimate
    CHAIN_EXECUTE(0.0, Target.COMBAT),
    BLOODY_EXECUTE(0.0, Target.COMBAT),
    BLADESTORM(0.0, Target.COMBAT),
    BLOODSTORM(0.0, Target.COMBAT),
    AFTERSHOCK(0.0, Target.COMBAT),
    CRATER(0.0, Target.COMBAT),
    SUNFIRE(0.0, Target.COMBAT),
    REDEMPTION(0.0, Target.COMBAT),
    // Keystones: a big rule with a price
    GLASS_CANNON(0.0, Target.COMBAT),
    UNSTOPPABLE(0.0, Target.COMBAT),
    COLOSSUS(0.0, Target.COMBAT),
    MARTYR(0.0, Target.COMBAT),

    // Summoner (companions): the call, their strength and how they fight
    /** Companions get this share more health. */
    SUMMON_HEALTH(0.0, Target.COMBAT),
    /** Companions follow and fight this many blocks further away. */
    CALL_RANGE(0.0, Target.COMBAT),
    /** Companions deal this share more damage. */
    SUMMON_DAMAGE(0.0, Target.COMBAT),
    /** The staff calls a wolf; every level shortens the pause. */
    PACK_CALL(0.0, Target.COMBAT),
    /** Companions take this share less damage. */
    SUMMON_TOUGHNESS(0.0, Target.COMBAT),
    /** Companions run this share faster. */
    SUMMON_SPEED(0.0, Target.COMBAT),
    /** One more wolf per level (tamer). */
    PACK_SLOTS(0.0, Target.COMBAT),
    /** The staff calls an iron golem instead of wolves (golemsmith); golems grow stronger per level. */
    GOLEM_CALL(0.0, Target.COMBAT),
    ALPHA_WOLF(0.0, Target.COMBAT),
    PACK_HUNT(0.0, Target.COMBAT),
    SNOW_GUARD(0.0, Target.COMBAT),
    HARD_GOLEM(0.0, Target.COMBAT),
    HOWL(0.0, Target.COMBAT),
    WARD_BOND(0.0, Target.COMBAT),
    BONE_GUARD(0.0, Target.COMBAT),
    PACK_LEADER(0.0, Target.COMBAT),
    GOLEM_WORK(0.0, Target.COMBAT),
    MIXED_PACK(0.0, Target.COMBAT),
    WILDNESS(0.0, Target.COMBAT),
    CARE(0.0, Target.COMBAT),
    SOUL_HARVEST(0.0, Target.COMBAT),
    ENDLESS_HUNT(0.0, Target.COMBAT),
    MOON_PACK(0.0, Target.COMBAT),
    TITAN_FIST(0.0, Target.COMBAT),
    ORE_CORE(0.0, Target.COMBAT),
    BEAST_LORD(0.0, Target.COMBAT),
    SHEPHERD(0.0, Target.COMBAT),
    // Summoner, way of the dead: souls, the raised dead and ghosts
    /** Chance for one more soul when a monster falls nearby. */
    SOUL_GREED(0.0, Target.COMBAT),
    /** The staff raises the last fallen monster (for souls); every level lets it stay longer. */
    RAISE_DEAD(0.0, Target.COMBAT),
    /** The raised dead stay this share longer. */
    BIND_RUNE(0.0, Target.COMBAT),
    /** The raised dead get this share more health and damage (necromancer). */
    NECRO_POWER(0.0, Target.COMBAT),
    /** Every raising also calls a ghost (soulcatcher); ghosts grow stronger per level. */
    SOUL_CATCHER(0.0, Target.COMBAT),
    SKELETON_ARCHERS(0.0, Target.COMBAT),
    GHOUL_HUNGER(0.0, Target.COMBAT),
    GHOST_SWARM(0.0, Target.COMBAT),
    SOUL_SHIELD(0.0, Target.COMBAT),
    ROT(0.0, Target.COMBAT),
    CORPSE_BLAST(0.0, Target.COMBAT),
    BONE_LEGION(0.0, Target.COMBAT),
    SOUL_BURN(0.0, Target.COMBAT),
    DEATH_DANCE(0.0, Target.COMBAT),
    UNHOLY_MIGHT(0.0, Target.COMBAT),
    SOUL_PACT(0.0, Target.COMBAT),
    ETERNAL_LEGION(0.0, Target.COMBAT),
    CHAMPION_OF_THE_DEAD(0.0, Target.COMBAT),
    SOUL_FLOOD(0.0, Target.COMBAT),
    ECHO(0.0, Target.COMBAT),
    LICH(0.0, Target.COMBAT),
    SOUL_KEEPER(0.0, Target.COMBAT),

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
    SANCTUARY(0.0, Target.ULTIMATE),
    HOLY_LIGHT(0.0, Target.ULTIMATE),
    BOMBARDMENT(0.0, Target.ULTIMATE),
    OVERCHARGE(0.0, Target.ULTIMATE),
    TOXIC_CLOUD(0.0, Target.ULTIMATE),
    THUNDERSTORM(0.0, Target.ULTIMATE),
    WILD_HUNT(0.0, Target.ULTIMATE),
    TITAN(0.0, Target.ULTIMATE),
    ARMY_OF_THE_DEAD(0.0, Target.ULTIMATE),
    SOUL_STORM(0.0, Target.ULTIMATE);

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
