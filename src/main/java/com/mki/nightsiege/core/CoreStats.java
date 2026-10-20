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
        public String branch = "";
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

    // --- skills
    public int points;
    public int earned;
    public boolean skillsEnabled;

    public List<PlayerLine> players = new ArrayList<>();
    public List<NodeLine> nodes = new ArrayList<>();
}
