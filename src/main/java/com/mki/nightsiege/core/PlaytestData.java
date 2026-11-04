package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** The playtest window (Simon, 28 October 2026): plan, nights, team, ready and crates. */
public class PlaytestData {

    /** plan, pause_interlude, interlude, pause_night, night, after_night, paused, done. */
    public String phase = "";
    /** While paused (nobody was online, Playtest 2): the part that comes again – interlude, night or next. */
    public String pausedPart = "";
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
        /** The dungeon kind before it (ExpeditionKind id), empty before the camp (Playtest 2). */
        public String dungeon = "";
        /** The night's event (SiegeEvent name), empty for the boss night or when it is rolled (Playtest 2). */
        public String event = "";
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
