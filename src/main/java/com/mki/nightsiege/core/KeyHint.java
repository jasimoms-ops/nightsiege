package com.mki.nightsiege.core;

import java.util.Map;
import java.util.Set;

/**
 * Which key (or key combination) a skill, a crate gain or a kit gadget needs. The
 * client turns it into a line like "Key: R" with the player's real key bindings, so a
 * rebound key shows as it is.
 *
 * <p>Since 26 October 2026 (Simon: "write the key next to every ability") every node of a
 * class tree and every crate gain has a line: the key it needs, or {@link #NONE}, which
 * the screens show as "no key of its own". Skills that only improve an action (the
 * masteries of an ult, the remote's upgrades) show the key of that action.
 */
public enum KeyHint {
    /** No key of its own: the skill works by itself or with normal fighting. */
    NONE,
    /** The ultimate key (R). */
    ULT,
    /** The ultimate key, looking at a monster close by (the execute). */
    ULT_LOOK,
    /** The ultimate key, then shoot (arrow storm, power shot). */
    ULT_SHOT,
    /** A mastery: improves the ultimate, so it is the ultimate key. */
    ULT_UPGRADE,
    /** The second-ult key (X). */
    ULT2,
    /** The quiver key (G). */
    QUIVER,
    /** Special arrows: the quiver key picks them, then shoot. */
    QUIVER_SHOT,
    /** Jump again while in the air. */
    AIR_JUMP,
    /** Jump and hit while falling. */
    JUMP_ATTACK,
    /** Right click with the item. */
    USE,
    /** Right click on a block (to place something). */
    USE_BLOCK,
    /** Right click on a block with the trap or turret in hand. */
    PLACE_TRAP,
    /** Right click on a teammate. */
    USE_MATE,
    /** The healing hand: right click on a teammate, or the shield action. */
    USE_MATE_OR_SHIELD,
    /** The shield action: block, then left click. */
    SHIELD_ACTION,
    /** Hold the shield up (block). */
    BLOCK,
    /** Raise the shield just before the hit (the perfect block). */
    PERFECT_BLOCK,
    /** Sneak. */
    SNEAK,
    /** Sneak while shooting. */
    SNEAK_SHOOT,
    /** Sneak while blocking. */
    SNEAK_BLOCK,
    /** Draw the bow or crossbow. */
    DRAW,
    /** The X key (it was sneak and right click until 25 October 2026). */
    SNEAK_USE,
    /** The remote: right click, or the X key. */
    REMOTE,
    /** The remote: right click on an own trap (overcharge). */
    REMOTE_BOOST,
    /** The remote: right click on a monster (mark). */
    REMOTE_MARK,
    /** The remote: the X key on a trap (reload). */
    REMOTE_RELOAD,
    /** The remote: the X key on a mortar, flame trap or potion tower (detonate). */
    REMOTE_DETONATE,
    /** The summoner's staff: right click calls, the X key at a monster sends the pack. */
    STAFF,
    /** The staff: right click (call or raise). */
    STAFF_CALL,
    /** The staff: the X key looking at an own minion. */
    STAFF_X_MINION,
    /** The staff: the X key without a monster in sight. */
    STAFF_X_FREE;

    /** Skills by id: they win over the effect (a few share an effect with a passive skill). */
    private static final Map<String, KeyHint> NODES = Map.ofEntries(
            Map.entry("g_hedgehog", SNEAK_BLOCK),
            Map.entry("g_wellspring", SHIELD_ACTION),
            Map.entry("g_boomerang", SHIELD_ACTION),
            Map.entry("g_miracle", USE_MATE_OR_SHIELD));

    /** Skills by what they do. */
    private static final Map<SkillEffect, KeyHint> EFFECTS = Map.ofEntries(
            Map.entry(SkillEffect.TRAP, PLACE_TRAP),
            Map.entry(SkillEffect.SHIELD_THROW, SHIELD_ACTION),
            Map.entry(SkillEffect.DOME, SHIELD_ACTION),
            Map.entry(SkillEffect.HEALING_HAND, USE_MATE_OR_SHIELD),
            Map.entry(SkillEffect.ANCHOR, SNEAK_BLOCK),
            Map.entry(SkillEffect.SHIELD_BASH, PERFECT_BLOCK),
            Map.entry(SkillEffect.COUNTERSTRIKE, PERFECT_BLOCK),
            Map.entry(SkillEffect.VENGEANCE, PERFECT_BLOCK),
            Map.entry(SkillEffect.COUNTER, BLOCK),
            Map.entry(SkillEffect.SHIELD_WALL, BLOCK),
            Map.entry(SkillEffect.EARTHSHOCK, JUMP_ATTACK),
            Map.entry(SkillEffect.BRACE, SNEAK_SHOOT),
            Map.entry(SkillEffect.PACK_CALL, STAFF_CALL),
            Map.entry(SkillEffect.GOLEM_CALL, STAFF_CALL),
            Map.entry(SkillEffect.RAISE_DEAD, STAFF_CALL),
            Map.entry(SkillEffect.CORPSE_BLAST, STAFF_X_MINION),
            Map.entry(SkillEffect.SOUL_SHIELD, STAFF_X_FREE),
            Map.entry(SkillEffect.DETONATOR, REMOTE_DETONATE),
            Map.entry(SkillEffect.AMMO_BELT, REMOTE_RELOAD),
            Map.entry(SkillEffect.OVERCHARGE_TIME, REMOTE_BOOST),
            Map.entry(SkillEffect.NETWORK, REMOTE_BOOST),
            Map.entry(SkillEffect.SEEKER, REMOTE_MARK),
            Map.entry(SkillEffect.REMOTE_ENERGY, REMOTE),
            Map.entry(SkillEffect.REMOTE_RANGE, REMOTE),
            Map.entry(SkillEffect.RECHARGE, REMOTE),
            // ultimates with something to do besides the key
            Map.entry(SkillEffect.EXECUTE, ULT_LOOK),
            Map.entry(SkillEffect.ARROW_STORM, ULT_SHOT),
            Map.entry(SkillEffect.POWER_SHOT, ULT_SHOT));

    private static final Set<String> SECOND_ULTS = Set.of("schlachtentrance", "lockpfeil", "magnetfeld", "spiegelschild",
            "seelentausch");

    private static final Map<String, KeyHint> GAINS = Map.ofEntries(
            Map.entry("koecher", QUIVER),
            Map.entry("lichtpfeile", QUIVER_SHOT),
            Map.entry("spektralpfeile", QUIVER_SHOT),
            Map.entry("kletterpfeile", QUIVER_SHOT),
            Map.entry("sturmpfeile", QUIVER_SHOT),
            Map.entry("sprungstiefel", AIR_JUMP),
            Map.entry("zielfernrohr", DRAW),
            Map.entry("wurfspeere", USE),
            Map.entry("wurfbeile", USE),
            Map.entry("wurfperlen", USE),
            Map.entry("heiltrank", USE),
            Map.entry("verbandskasten", USE),
            Map.entry("blendgranaten", USE),
            Map.entry("oelfass", USE),
            Map.entry("kletterhaken", USE),
            Map.entry("kampfhorn", USE),
            Map.entry("tarnumhang", USE),
            Map.entry("signalhorn", USE),
            Map.entry("mauerkelle", USE),
            Map.entry("ersatzbatterie", USE),
            Map.entry("kriegsbanner", USE_BLOCK),
            Map.entry("barrikaden", USE_BLOCK),
            Map.entry("baukiste", USE_BLOCK),
            Map.entry("fackeln", USE_BLOCK),
            Map.entry("notfallmine", PLACE_TRAP),
            Map.entry("leibwaechter", USE_MATE),
            Map.entry("turmschild", BLOCK),
            Map.entry("leichtschild", BLOCK),
            Map.entry("deckung", BLOCK),
            Map.entry("doppelstab", STAFF_CALL),
            Map.entry("rueckrufpfeife", USE),
            Map.entry("geisterross", USE),
            Map.entry("futternapf", USE),
            Map.entry("totenglocke", USE));

    /** Kit gadgets (by their item id in the custom data). */
    private static final Map<String, KeyHint> GADGETS = Map.ofEntries(
            Map.entry("wurfspeer", USE),
            Map.entry("wurfbeil", USE),
            Map.entry("blendgranate", USE),
            Map.entry("oelfass", USE),
            Map.entry("kletterhaken", USE),
            Map.entry("kampfhorn", USE),
            Map.entry("tarnumhang", USE),
            Map.entry("signalhorn", USE),
            Map.entry("mauerkelle", USE),
            Map.entry("ersatzbatterie", USE),
            Map.entry("kriegsbanner", USE_BLOCK),
            Map.entry("barrikade", USE_BLOCK),
            Map.entry("leibwaechter", USE_MATE),
            Map.entry("rueckrufpfeife", USE),
            Map.entry("geisterross", USE),
            Map.entry("futternapf", USE),
            Map.entry("totenglocke", USE));

    /**
     * The key a skill needs. Ultimates use the ult key; a skill that needs an ultimate
     * (a mastery) improves it and shows the ult key too.
     */
    public static KeyHint forNode(SkillTree tree, SkillNode node) {
        if (node == null) {
            return NONE;
        }
        KeyHint byId = NODES.get(node.id());
        if (byId != null) {
            return byId;
        }
        KeyHint byEffect = EFFECTS.get(node.effect());
        if (byEffect != null) {
            return byEffect;
        }
        if (node.effect().target() == SkillEffect.Target.ULTIMATE) {
            return ULT;
        }
        if (tree != null) {
            for (java.util.List<SkillNode.Need> any : node.needs()) {
                for (SkillNode.Need need : any) {
                    SkillNode parent = tree.get(need.id());
                    if (parent != null && parent.effect().target() == SkillEffect.Target.ULTIMATE) {
                        return ULT_UPGRADE;
                    }
                }
            }
        }
        return NONE;
    }

    /** The key a skill needs, by id. */
    public static KeyHint forNode(SkillTree tree, String id) {
        return id == null || tree == null ? NONE : forNode(tree, tree.get(id));
    }

    public static KeyHint forGain(String id) {
        if (id == null) {
            return NONE;
        }
        if (SECOND_ULTS.contains(id)) {
            return ULT2;
        }
        return GAINS.getOrDefault(id, NONE);
    }

    public static KeyHint forGadget(String id) {
        return id == null ? NONE : GADGETS.getOrDefault(id, NONE);
    }

    public static KeyHint byName(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
