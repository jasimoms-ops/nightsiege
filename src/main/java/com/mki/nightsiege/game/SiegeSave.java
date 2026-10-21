package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the mod remembers between restarts.
 *
 * <p>It lives in the world folder as {@code nightsiege-state.json}, so every
 * world keeps its own cores, levels, skills and traps.</p>
 */
public class SiegeSave {
    public List<CoreRecord> cores = new ArrayList<>();
    public List<TrapRecord> traps = new ArrayList<>();
    /** Dungeon cores placed in structures so far. */
    public List<DungeonRecord> dungeons = new ArrayList<>();
    /** Structures a dungeon map pointed at: these always hold a core. Entries are "dimension|chunkX|chunkZ". */
    public List<String> forcedDungeons = new ArrayList<>();
    /** Core fragments inserted into the main core, per perk id. */
    public Map<String, Integer> perkFragments = new HashMap<>();
    /** True once the free starter core has been handed out in this world. */
    public boolean starterCoreGiven;
    /** Uuids of players who already received the guide book. */
    public List<String> guideGiven = new ArrayList<>();
    /** Uuids of players who already received the handbook item (replaces the old book). */
    public List<String> handbookGiven = new ArrayList<>();

    public int siegesWon;
    public int siegesLost;
    public int currentStreak;
    public int bestStreak;
    public int totalKills;
    public int totalChampionKills;
    public int highestLevel;
    /** Player uuid to their tally. */
    public Map<String, PlayerStats> playerStats = new HashMap<>();

    /** Countdown state, so a restart does not reset the clock. */
    public long readyTicks;
    public long requiredTicks;


    /** Every player keeps their own class, experience and levels. */
    public Map<String, PlayerSkills> skills = new HashMap<>();
    /** The shared core tree, paid for by donations. */
    public PlayerSkills coreSkills = new PlayerSkills();

    public CoreRecord findCore(String dimension, int x, int y, int z) {
        if (cores == null) {
            return null;
        }
        for (CoreRecord core : cores) {
            if (core != null && core.matches(dimension, x, y, z)) {
                return core;
            }
        }
        return null;
    }

    public void removeCore(String dimension, int x, int y, int z) {
        if (cores != null) {
            cores.removeIf(core -> core == null || core.matches(dimension, x, y, z));
        }
    }

    /** True when the handbook still has to be handed out to this player. */
    public boolean needsGuide(String playerUuid) {
        if (handbookGiven == null) {
            handbookGiven = new ArrayList<>();
        }
        return playerUuid != null && !handbookGiven.contains(playerUuid);
    }

    public void markGuideGiven(String playerUuid) {
        if (handbookGiven == null) {
            handbookGiven = new ArrayList<>();
        }
        if (playerUuid != null && !handbookGiven.contains(playerUuid)) {
            handbookGiven.add(playerUuid);
        }
    }

    public PlayerStats statsOf(String playerUuid, String name) {
        if (playerStats == null) {
            playerStats = new HashMap<>();
        }
        PlayerStats stats = playerStats.computeIfAbsent(playerUuid, key -> new PlayerStats());
        if (name != null && !name.isBlank()) {
            stats.name = name;
        }
        return stats;
    }

    public PlayerSkills skillsOf(String playerUuid) {
        if (skills == null) {
            skills = new HashMap<>();
        }
        return skills.computeIfAbsent(playerUuid, key -> new PlayerSkills());
    }

    public PlayerSkills coreSkills() {
        if (coreSkills == null) {
            coreSkills = new PlayerSkills();
        }
        return coreSkills;
    }

    /** Wipes every player's skill progress. Used when a lost siege resets the core. */
    /** A lost siege wipes every tree, the shared one included. */
    public void resetAllSkills() {
        if (skills != null) {
            for (PlayerSkills entry : skills.values()) {
                entry.reset();
            }
        }
        coreSkills().reset();
    }
}
