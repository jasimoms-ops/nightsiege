package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What the kit page of the core window gets from the server (screen payload "kit", update 6): the
 * kit of one class as it will be handed out tonight, the crate items that may come into the hotbar,
 * the gains that always count and the gain keys.
 */
public class KitPageView {
    /** The class shown. */
    public String clazz = "";
    /** The player's own classes (the pennants). */
    public List<String> classes = new ArrayList<>();
    /** The night's level the kit is built for. */
    public int level;
    /** True when the player chose the crate items himself (else they come on their own). */
    public boolean chosen;

    /** The 36 inventory slots (hotbar 0-8, the rest 9-35). */
    public List<Slot> slots = new ArrayList<>();
    /** Helmet, chestplate, leggings, boots and the off hand (item JSON, "" when empty). */
    public List<String> armor = new ArrayList<>();
    public String offhand = "";

    /** The crate items with a place of their own, by gain. */
    public List<Option> options = new ArrayList<>();
    /** Crate gains without a place of their own: they always count (enchantments, armour, quiver …). */
    public List<String> passive = new ArrayList<>();

    /** The gain keys N, M (J with "Dritte Hand"). */
    public int keyCount;
    public List<String> keys = new ArrayList<>();
    public List<String> keyOwned = new ArrayList<>();

    /** One slot of the kit. */
    public static class Slot {
        /** Item JSON, "" for an empty slot. */
        public String item = "";
        /** The numbered layout key (KitArrange.numbered), "" for an empty slot. */
        public String key = "";
        /** The crate gain the stack comes from, "" for the kit's own things. */
        public String gain = "";
    }

    /** One crate gain with items of its own. */
    public static class Option {
        public String gain = "";
        /** Its stacks (item JSON) and their numbered layout keys, in order. */
        public List<String> items = new ArrayList<>();
        public List<String> keys = new ArrayList<>();
        /** True while its stacks lie in the hotbar tonight. */
        public boolean taken;
        /** CrateRarity name. */
        public String rarity = "";
    }
}
