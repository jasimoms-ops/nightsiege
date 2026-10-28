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
        /** Amount per level as used now (after server tweaks), for the numbers in the skill text. */
        public double amount;
        /** Named numbers of the skill ({@link SkillValues}), for the numbers in the skill text. */
        public java.util.Map<String, Double> values = new java.util.LinkedHashMap<>();
    }

    /** One profile of the viewer: a tree of a class with its own levels and subclass. */
    public static class ProfileLine {
        /** Profile key ("WARRIOR", "WARRIOR#2"). */
        public String key = "";
        public String clazz = "";
        /** Learned subclass, or the one the profile was opened for, or empty. */
        public String subclass = "";
        public String path = "";
        public int ult;
        public int ultMax = 3;
        public boolean playing;
        public boolean complete;
        public int spent;
    }

    /** Levels, costs and reasons of one profile's nodes (the class tab lays them over its node lines). */
    public static class ProfileNodes {
        public java.util.Map<String, Integer> levels = new java.util.HashMap<>();
        public java.util.Map<String, Integer> costs = new java.util.HashMap<>();
        public java.util.Map<String, String> blocked = new java.util.HashMap<>();
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

    /** The dungeon the scouts reported (core window, page "Kundschaft"), or null. */
    public static class ScoutLine {
        /** ExpeditionKind id. */
        public String kind = "";
        public String fragment = "";
        /** Its monsters (entity ids, each once). */
        public List<String> mobs = new ArrayList<>();
        public int coreX;
        public int coreZ;
        public int targetX;
        public int targetZ;
        public boolean built;
        public boolean won;
        public int rooms;
        public int cleared;
        public double championLife;
        /** Skill points for every fighter of a win. */
        public int points;
        /** Players fighting in it now. */
        public int fighters;
        /** The viewer stands in the dungeon's world, at this spot. */
        public boolean sameWorld;
        public int viewerX;
        public int viewerZ;
        /** Terrain letters, mapSize cells a side, row by row from (mapX, mapZ), mapCell blocks each. */
        public String map = "";
        public int mapSize;
        public int mapCell;
        public int mapX;
        public int mapZ;
    }

    public ScoutLine scout;

    /** One wanted poster (update 2 "Fluch & Rache"): a nemesis lurking, on the field tonight, or slain. */
    public static class NemesisLine {
        public String id = "";
        public String name = "";
        /** Entity id ("minecraft:zombie"). */
        public String type = "";
        public int level = 1;
        public List<String> traits = new ArrayList<>();
        public List<String> victims = new ArrayList<>();
        public int kills;
        public int nightsAway;
        /** "lauert" or "erschlagen". */
        public String status = "";
        public String slainBy = "";
        public boolean test;
        /** On the field in the running night. */
        public boolean here;
    }

    // --- the curse altar (core window, page "Fluch & Rache")
    public boolean cursesEnabled;
    public int curseMax;
    /** Stones on the altar for the next night (CurseKind ids). */
    public List<String> curses = new ArrayList<>();
    /** Stones of the running night; the altar is sealed while it runs. */
    public List<String> cursesTonight = new ArrayList<>();
    public boolean altarSealed;
    /** What each curse does: its factor (life 1.3, healing 0.5 ...). */
    public java.util.Map<String, Double> curseValues = new java.util.HashMap<>();
    public double curseCrateChance;
    public double bonusCrateChance;
    public int cursePoints;
    /** A normal night's crate odds, and with the stones on the altar now. */
    public int[] oddsPlain = new int[5];
    public int[] oddsCursed = new int[5];
    public boolean nemesisEnabled;
    public List<NemesisLine> nemeses = new ArrayList<>();

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
    /** The button for an extra siege tonight: switched on, from which level, why not (SiegeRhythm.ExtraBlock), and whether today's night is one. */
    public boolean extraSiegeOn;
    public int extraSiegeFromLevel;
    public String extraSiegeBlock = "";
    public boolean extraSiegeToday;
    public String extraSiegeBy = "";

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
    /** The played profile's class (empty without a class); points can go into every profile. */
    public String myClass = "";
    /** The played profile's key. */
    public String myProfile = "";
    public int points;
    public int earned;
    public int donated;
    public String pathTaken = "";
    /** Warrior subclass (blademaster, berserker, axe, paladin), or empty. */
    public String subclass = "";
    public boolean creative;
    /** Server operator: may reset every player's crate gains from the collection tab. */
    public boolean admin;
    /** The viewer may still change their class once at the core. */
    public boolean classResetAvailable;
    /** Playtest: the class change is not used up (testToolsForEveryone). */
    public boolean classResetUnlimited;
    /** The class the player plays tonight. */
    public String playClass = "";
    /** Classes with at least one profile, oldest first. */
    public java.util.List<String> unlocked = new java.util.ArrayList<>();
    /** Unlocked classes that are fully learned. */
    public java.util.List<String> completeClasses = new java.util.ArrayList<>();
    /** True when an unlock is free and something is left to open with it. */
    public boolean nextClassOpen;
    /** The viewer's profiles, oldest first (Simon, 26 October 2026). */
    public java.util.List<ProfileLine> profiles = new java.util.ArrayList<>();
    /**
     * Node levels per profile key, for every profile: the node lines carry the levels of
     * the played profile of their class (or the oldest one), this map those of each profile.
     */
    public java.util.Map<String, ProfileNodes> profileNodes = new java.util.HashMap<>();
    /** Unlocks earned and not used yet. */
    public int unlocksFree;
    /** What an unlock may open now: new classes ("CLASS") and further subclasses ("CLASS:subclass"). */
    public java.util.List<String> unlockClasses = new java.util.ArrayList<>();
    public java.util.List<String> unlockSubclasses = new java.util.ArrayList<>();
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
