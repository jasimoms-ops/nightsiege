package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** The playtest window (Simon, 28 October 2026): plan, nights, team, ready and crates. */
public class PlaytestData {

    /** plan, pause_interlude, interlude, pause_night, night, after_night, done. */
    public String phase = "";
    /** The night being played (0-3), -1 while planning. */
    public int step = -1;
    public int target;
    public List<Night> nights = new ArrayList<>();
    /** Seconds left in a pause or interlude, -1 when there is no countdown. */
    public int seconds = -1;
    public boolean ready;
    public boolean hasClass;
    public String clazz = "";
    public String subclass = "";
    /** Levels in the planned path. */
    public int planned;
    /** Levels learned for the coming night. */
    public int learned;
    /** Skill points of the coming night (while planning: the planning points left). */
    public int points;
    /** Crates waiting to be taken. */
    public int crates;
    public List<Mate> team = new ArrayList<>();

    public static class Night {
        public int level;
        /** camp or dungeon. */
        public String interlude = "";
        /** Boss id when this night brings one, else empty. */
        public String boss = "";
        public boolean done;
    }

    public static class Mate {
        public String name = "";
        public String clazz = "";
        public String subclass = "";
        public int planned;
        public boolean ready;
        public boolean online;
    }
}
