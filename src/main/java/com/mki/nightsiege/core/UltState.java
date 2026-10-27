package com.mki.nightsiege.core;

/** What the ultimate slot next to the hotbar shows. Travels as JSON once a second. */
public class UltState {
    /** False when the player has no ultimate yet: the slot is hidden. */
    public boolean has;
    /** Skill node id, for the name. */
    public String node = "";
    /** Item id drawn in the slot. */
    public String icon = "";
    /** Ticks until it can fire again; 0 when ready. */
    public int cooldownTicks;
    /** Full cooldown in ticks, for the shading. */
    public int totalTicks;
    /** False outside a siege (and outside creative): the slot is greyed out. */
    public boolean live;
    /**
     * True while the ability keys (Q, F, Y) belong to Night Siege alone: in the night as a
     * defender, or with a class item in hand (27 October 2026).
     */
    public boolean keys;
    /** The second ultimate from the crate (27 October 2026: its own slot next to the ult). */
    public boolean has2;
    public String icon2 = "";
    public int cooldown2Ticks;
    public int total2Ticks;
}
