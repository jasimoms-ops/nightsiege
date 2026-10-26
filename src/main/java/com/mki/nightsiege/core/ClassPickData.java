package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** What the class window before a night shows; travels as JSON. */
public class ClassPickData {

    /** One profile card ("Krieger – Berserker"). */
    public static class Entry {
        /** Profile key ("WARRIOR", "WARRIOR#2"); what "play:" sends back. */
        public String id = "";
        /** The profile's class. */
        public String clazz = "";
        /** Level of the profile's ultimate and its top level. */
        public int ult;
        public int ultMax = 3;
        public boolean unlocked;
        public boolean leveling;
        public boolean complete;
        public String way = "";
        public String subclass = "";
        /** The ultimate is at level 3 (the next class may open). */
        public boolean ultDone;
        public int spent;
        /** Points the whole class costs on the way taken (0 = unknown). */
        public int total;
        /** The kit tonight with this class: every item as JSON (empty for locked classes). */
        public List<String> kit = new ArrayList<>();
    }

    /** A fellow defender and the class they play. */
    public static class Mate {
        public String name = "";
        public String play = "";
        /** The subclass of the profile they play, or empty. */
        public String subclass = "";
        /** Has confirmed a class in the window (or never had to). */
        public boolean ready;
        /** The player looking at the window. */
        public boolean self;

        public Mate() {
        }

        public Mate(String name, String play) {
            this.name = name;
            this.play = play;
        }
    }

    /** The played profile's key. */
    public String play = "";
    public int seconds;
    public int level;
    /** Tonight's event (SiegeEvent name), empty when unknown or none. */
    public String event = "";
    /** A test night (no invitations). */
    public boolean test;
    /** Accepted invitations and how many are needed (real nights). */
    public int accepts;
    public int required;
    /** Unlocks not used yet and whether one may be used now (in the core window). */
    public int unlocksFree;
    public boolean nextOpen;
    /** Armour material of the kit tonight (IRON, DIAMOND, NETHERITE, INFERNITE). */
    public String armor = "";
    public int protection;
    public int sharpness;
    public List<Entry> classes = new ArrayList<>();
    public List<Mate> team = new ArrayList<>();
}
