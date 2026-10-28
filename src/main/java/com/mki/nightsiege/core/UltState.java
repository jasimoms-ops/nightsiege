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
     * True while the ability keys (Q, F, X and the bar keys) belong to Night Siege alone: in the night as a
     * defender, or with a class item in hand (27 October 2026).
     */
    public boolean keys;
    /** The second ultimate from the crate (27 October 2026: its own slot next to the ult). */
    public boolean has2;
    public String icon2 = "";
    /** Translation key of the second ultimate's name. */
    public String name2 = "";
    public int cooldown2Ticks;
    public int total2Ticks;

    /**
     * Kit HUD (Simon, 28 October 2026): true when the server hands out kits at all. Then the
     * abilities only show inside the kit HUD; without kits the old bar stays.
     */
    public boolean kitsOn;
    /** The class in lower case ("warrior", "archer" ...) for the crest of the kit HUD; empty without one. */
    public String clazz = "";
    /** The subclass ("blademaster" ...) for its own crest; empty before one is chosen. */
    public String sub = "";

    /**
     * Kit HUD v2 (29 October 2026): the class's gauge in the bars (souls, remote energy, shield
     * energy); hidden when {@link #gauge} is false.
     */
    public boolean gauge;
    public float gaugeValue;
    public float gaugeMax;
    /** Translation key of the gauge's name. */
    public String gaugeName = "";
    public int gaugeColor;
    /** The left wing: the summoner's companions or the engineer's towers, grouped by kind. */
    public java.util.List<Unit> units = new java.util.ArrayList<>();

    /** One kind of companion or tower in the left wing. */
    public static class Unit {
        public String icon = "";
        public int count;
        /** Most of this kind (towers), 0 when there is no limit to show. */
        public int max;
        /** The weakest one's share of its health, -1 when it has none. */
        public float health = -1f;
    }

    /** Fähigkeit 2 (F): shown when it does something for this class (remote, staff, healing). */
    public boolean hasAb2;
    public String iconAb2 = "";
    /** Translation key of what F does for this class. */
    public String nameAb2 = "";

    /**
     * The ability bar (Simon, 27 October 2026, instead of the wheel): every spell, potion,
     * healing ability or arrow kind on a key of its own (R, G, Y, C, V, B), in this order.
     */
    public java.util.List<Slot> slots = new java.util.ArrayList<>();

    /** One ability of the bar. */
    public static class Slot {
        public String id = "";
        public String icon = "";
        /** Inventory slot whose stack is the icon (arrow kinds), -1 none. */
        public int invSlot = -1;
        /** A lingering potion of this colour as the icon (potions), -1 none. */
        public int color = -1;
        /** Translation key of its name. */
        public String name = "";
        /** Translation key of the line under the name (cost and the like), with its arguments. */
        public String sub = "";
        public java.util.List<String> subArgs = new java.util.ArrayList<>();
        /** Picked (the arrow kind in use). */
        public boolean current;
        public int cooldownTicks;
        public int totalTicks;
    }
}
