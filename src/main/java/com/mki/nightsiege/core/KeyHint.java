package com.mki.nightsiege.core;

import java.util.Map;
import java.util.Set;

/**
 * Which key (or key combination) a skill, a crate gain or a kit gadget needs. The
 * client turns it into a line like "Key: R" with the player's real key bindings, so a
 * rebound key shows as it is.
 */
public enum KeyHint {
    NONE,
    /** The ultimate key (R). */
    ULT,
    /** The second-ult key (X). */
    ULT2,
    /** The quiver key (G). */
    QUIVER,
    /** Jump again while in the air. */
    AIR_JUMP,
    /** Right click with the item. */
    USE,
    /** Right click on a block (to place something). */
    USE_BLOCK,
    /** Right click on a teammate. */
    USE_MATE,
    /** The healing hand: right click on a teammate, or the shield action. */
    USE_MATE_OR_SHIELD,
    /** The shield action: block, then left click. */
    SHIELD_ACTION,
    /** Sneak. */
    SNEAK,
    /** Sneak while blocking. */
    SNEAK_BLOCK,
    /** Draw the bow or crossbow. */
    DRAW,
    /** The remote's second order (the X key since 25 October 2026; it was sneak and right click). */
    SNEAK_USE,
    /** The remote: right click, or the X key for the second order. */
    REMOTE,
    /** The summoner's staff: right click calls, sneak and right click at a monster sends the pack. */
    STAFF;

    /** Skills whose effect is fired by a key; every ultimate uses the ult key on top. */
    private static final Map<String, KeyHint> NODES = Map.ofEntries(
            Map.entry("g_shieldthrow", SHIELD_ACTION),
            Map.entry("g_dome", SHIELD_ACTION),
            Map.entry("g_healing_hand", USE_MATE_OR_SHIELD),
            Map.entry("g_anchor", SNEAK_BLOCK),
            Map.entry("a_bracing", SNEAK),
            Map.entry("e_detonator", SNEAK_USE),
            Map.entry("arrow_turret", USE_BLOCK),
            Map.entry("s_packcall", STAFF),
            Map.entry("s_golemsmith", STAFF),
            Map.entry("d_raise", STAFF),
            Map.entry("d_corpse", SNEAK_USE),
            Map.entry("d_soulshield", SNEAK_USE));

    private static final Set<String> SECOND_ULTS = Set.of("schlachtentrance", "lockpfeil", "magnetfeld", "spiegelschild",
            "seelentausch");

    private static final Map<String, KeyHint> GAINS = Map.ofEntries(
            Map.entry("koecher", QUIVER),
            Map.entry("sprungstiefel", AIR_JUMP),
            Map.entry("zielfernrohr", DRAW),
            Map.entry("wurfspeere", USE),
            Map.entry("wurfbeile", USE),
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
            Map.entry("leibwaechter", USE_MATE),
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

    /** The key a skill needs; ultimates always use the ult key. */
    public static KeyHint forNode(String id, boolean ultimate) {
        if (ultimate) {
            return ULT;
        }
        return id == null ? NONE : NODES.getOrDefault(id, NONE);
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
