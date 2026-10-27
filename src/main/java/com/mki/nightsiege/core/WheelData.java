package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The selection wheel of Fähigkeit 3 (Simon, 27 October 2026, like the weapon wheel in GTA):
 * held, the key opens a ring of choices – arrow kinds, spells, potions, healing abilities –
 * and letting go over one picks it. Travels from the server to the client as JSON.
 */
public class WheelData {
    /** Translation key of the wheel's title. */
    public String title = "";
    /** Translation key of the line under the title (what the pick is for), may be empty. */
    public String hint = "";
    public List<Option> options = new ArrayList<>();

    public static class Option {
        /** Sent back on a pick. */
        public String id = "";
        /** Item drawn in the slot (an item id), when no slot or colour is given. */
        public String icon = "";
        /** Inventory slot whose stack is the icon (and its name when {@link #name} is empty); -1 none. */
        public int slot = -1;
        /** A lingering potion of this colour as the icon; -1 none. */
        public int color = -1;
        /** Translation key of the name (empty: the name of the stack in {@link #slot}). */
        public String name = "";
        /** Translation key of the small line under the name, with its arguments. */
        public String sub = "";
        public List<String> subArgs = new ArrayList<>();
        /** False: shown greyed out, cannot be picked (not learned yet). */
        public boolean open = true;
        /** The choice in use right now. */
        public boolean current;
    }
}
