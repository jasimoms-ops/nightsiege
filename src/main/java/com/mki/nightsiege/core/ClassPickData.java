package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** What the class window before a night shows; travels as JSON. */
public class ClassPickData {

    /** One class card. */
    public static class Entry {
        public String id = "";
        public boolean unlocked;
        public boolean leveling;
        public boolean complete;
        public String way = "";
        public String subclass = "";
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
    /** The class being levelled (for the text on locked cards) and whether the next one may open. */
    public String leveling = "";
    public boolean nextOpen;
    /** Armour material of the kit tonight (IRON, DIAMOND, NETHERITE, INFERNITE). */
    public String armor = "";
    public int protection;
    public int sharpness;
    public List<Entry> classes = new ArrayList<>();
    public List<Mate> team = new ArrayList<>();
}
