package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the core's window shows, in one plain object.
 *
 * <p>It travels to the client as JSON inside a single network message, so the
 * screen never has to ask the server a second question while it is open.</p>
 */
public class CoreStats {

    /** One player's line in the kill ranking. */
    public static class PlayerLine {
        public String name = "";
        public int kills;
        public int championKills;
        public int fallen;
        public boolean online;

        public PlayerLine() {
        }

        public PlayerLine(String name, int kills, int championKills, int fallen, boolean online) {
            this.name = name;
            this.kills = kills;
            this.championKills = championKills;
            this.fallen = fallen;
            this.online = online;
        }
    }

    /** One node as the client needs to draw it. */
    public static class NodeLine {
        public String id = "";
        public String clazz = "";
        public String path = "";
        public String icon = "";
        public int row;
        public int level;
        public int maxLevel;
        public int cost;
        public String requires;
        public int requiresLevel;
        public String blocked;
        public boolean live;
        /** Choice group (one node per group), place from the top, nodes it closes. */
        public String group = "";
        public int col = -1;
        public List<String> locks = new ArrayList<>();
        /** The subclass this node belongs to, or empty for nodes shared by the way. */
        public String lane = "";
        /** The key this skill needs ({@link KeyHint} name), or empty. */
        public String key = "";
    }

    /** One perk of the main core. */
    public static class PerkLine {
        public String id = "";
        public int fragments;
        public int stage;
        /** Fragments in total for the next stage; 0 at the top stage. */
        public int nextAt;
        /** Fragments in total for the current stage. */
        public int stageAt;
    }

    // --- the base around the core, as the scan found it
    public boolean baseKnown;
    public boolean baseClosed;
    public int baseWidth;
    public int baseDepth;
    public int baseRadius;
    /** Where the way in leaves an open base ("x y z"), or empty. */
    public String baseGap = "";

    // --- night fragment set into the core (name of a NightFragment), or null
    public String nightFragment;

    // --- perks of the main core
    public List<PerkLine> perks = new ArrayList<>();
    public int perkRadius;
    public boolean perkInRange;

    // --- siege state
    public boolean siegeActive;
    /** Kills of the running night and the kills it asks for (0 = no goal). */
    public int nightKills;
    public int killsRequired;
    public int level;
    public int secondsLeft;
    public String eventKey = "";

    // --- countdown
    public int countdownPercent;
    public int countdownMinutesLeft;
    public int playersNear;
    public int playersRequired;
    public int coreRadius;
    public boolean clockRunning;
    /** The rhythm: days until the next siege day (0 = today), the vote of today and the viewer's answer. */
    public int daysUntilSiege = -1;
    public boolean voteOpen;
    public int accepted;
    public int acceptsRequired;
    public int registeredCount;
    /** "yes", "no" or "" when the viewer has not answered today's invitation. */
    public String myVote = "";
    /** The horn has sounded today: the night is coming. */
    public boolean nightComing;

    // --- wave strength
    public int mobsPerWave;
    public double healthMultiplier;
    public double damageMultiplier;
    public int maxLiveMobs;

    // --- record
    public int siegesWon;
    public int siegesLost;
    public int bestStreak;
    public int currentStreak;
    public int totalKills;
    public int totalChampionKills;
    public int highestLevelReached;

    // --- the viewing player's own tree
    public String myClass = "";
    public int points;
    public int earned;
    public int donated;
    public String pathTaken = "";
    /** Warrior subclass (blademaster, berserker, axe, paladin), or empty. */
    public String subclass = "";
    public boolean creative;
    /** The viewer may still change their class once at the core. */
    public boolean classResetAvailable;
    /** Playtest: the class change is not used up (testToolsForEveryone). */
    public boolean classResetUnlimited;
    /** The class the player plays tonight (may differ from {@link #myClass}, the one being levelled). */
    public String playClass = "";
    /** Unlocked classes, oldest first. */
    public java.util.List<String> unlocked = new java.util.ArrayList<>();
    /** Unlocked classes that are fully learned. */
    public java.util.List<String> completeClasses = new java.util.ArrayList<>();
    /** True when the levelled class is full and the next one may be opened. */
    public boolean nextClassOpen;
    /** The viewer may see and choose the summoner (admins, or everyone by config). */
    public boolean summoner;
    /** One card of the class overview (core window, tab Klassen). */
    public static class ClassLine {
        public String id = "";
        public boolean unlocked;
        public boolean leveling;
        public boolean played;
        public boolean complete;
        /** The ultimate is at level 3. */
        public boolean ultDone;
        /** Subclass key (learned or fixed with the class), empty when none yet. */
        public String subclass = "";
        public String way = "";
        public int spent;
        public int total;
    }
    public List<ClassLine> classLines = new ArrayList<>();
    /** Crate gains per class: gain id to stage. */
    public java.util.Map<String, java.util.Map<String, Integer>> gains = new java.util.HashMap<>();
    public boolean skillsEnabled;

    /** The shared core tree. */
    public int corePoints;
    public int coreEarned;

    public List<PlayerLine> players = new ArrayList<>();
    public List<NodeLine> nodes = new ArrayList<>();
}
