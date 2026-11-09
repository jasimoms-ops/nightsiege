package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the inventory shows when the mouse rests on an ability (Simon 08.11.: "da sollte stehen was die
 * spezielle fähigkeit macht wenn man darüber hovert"). Built on the server from the same numbers the
 * ability uses ({@link SkillValues}, the player's skill nodes) and sent with the {@link UltState} as JSON.
 */
public class AbilityTip {
    /** Which field: "q" (ultimate), "f" (ability 2), "x" (second ultimate), else the bar ability's id (Tor 3.4). */
    public String slot = "";
    /** Tor 3.4: a bar ability's key from {@link AbilityKeys} (ABILITY_1 to ABILITY_4); empty for the class's own fields. */
    public String key = "";
    /** The ability itself: name, text and its numbers. */
    public Part main = new Part();
    /** Full cooldown in ticks as the game uses it now; -1 when the ability has none of its own. */
    public int cooldownTicks = -1;
    /** Learned skills that change this ability (ult masteries, heals, staff and remote skills). */
    public List<Part> mods = new ArrayList<>();

    /** One text with its numbers (the placeholders of {@code SkillText}). */
    public static class Part {
        /** Translation key of the name. */
        public String name = "";
        /** Translation key of the text with placeholders. */
        public String desc = "";
        /** Amount per level ({a}) and the top level ({m}). */
        public double amount;
        public int maxLevel;
        /** The level the player has; a rank list {x*} marks this rank. 0: no level shown. */
        public int level;
        public Map<String, Double> values = new LinkedHashMap<>();
    }
}
