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
    }

    /** A fellow defender and the class they play. */
    public static class Mate {
        public String name = "";
        public String play = "";

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
    /** Armour material of the kit tonight (IRON, DIAMOND, NETHERITE, INFERNITE). */
    public String armor = "";
    public int protection;
    public int sharpness;
    public List<Entry> classes = new ArrayList<>();
    public List<Mate> team = new ArrayList<>();
}
