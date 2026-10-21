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

    // --- perks of the main core
    public List<PerkLine> perks = new ArrayList<>();
    public int perkRadius;
    public boolean perkInRange;

    // --- siege state
    public boolean siegeActive;
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
    public boolean creative;
    public boolean skillsEnabled;

    /** The shared core tree. */
    public int corePoints;
    public int coreEarned;

    public List<PlayerLine> players = new ArrayList<>();
    public List<NodeLine> nodes = new ArrayList<>();
}
