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
    /** Event already rolled for the next siege (an eclipse comes at noon), or null. */
    public String pendingEvent;
    /** A night fragment set into the core: the next siege is a special night of its event. */
    public String nightFragment;
    /** The siege running (or the one just rolled) is a special night. */
    public boolean specialNight;
    /** Clock day the trader caravan last came. */
    public long lastCaravanDay = -1000L;
    /** True once the free starter core has been handed out in this world. */
    public boolean starterCoreGiven;
    /** Uuids of players who already received the guide book. */
    public List<String> guideGiven = new ArrayList<>();
    /** Uuids of players who already received the handbook item (replaces the old book). */
    public List<String> handbookGiven = new ArrayList<>();

    public int siegesWon;
    public int siegesLost;
    /** Sieges since the last boss fell (-1: not counted yet, set from the sieges played). */
    public int siegesSinceBoss = -1;
    /** The bosses still to come before the bag is filled again (ids, see BossKind). */
    public java.util.List<String> bossBag = new java.util.ArrayList<>();
    /** The boss of the last boss night, so the next bag does not start with it. */
    public String lastBoss;
    /** The boss waiting for the coming boss night (drawn once, kept when the night is lost). */
    public String nextBoss;
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

    /** Players who signed up at the core (uuids). Everyone else is left alone by the mod. */
    public List<String> registered = new ArrayList<>();
    /** Day of the next siege night (in-game day), or -1 if none is set yet. */
    public long nextSiegeDay = -1L;
    /** Day the invitations for a siege last went out, with the answers. */
    public long voteDay = -1L;
    public List<String> accepted = new ArrayList<>();
    public List<String> declined = new ArrayList<>();
    /**
     * The own inventories of defenders while they wear their night kit, by player uuid.
     * Kept in the save, so a crash or restart in the night loses nothing.
     */
    public Map<String, KitStash> kitStashes = new HashMap<>();
    /** Reward crates won while offline, by player uuid: "CLASS|KIND|LEVEL" each. */
    public Map<String, List<String>> pendingCrates = new HashMap<>();
    /**
     * Blocks the crate gains set down for one night only (barricades, banners, ladders,
     * light): "dimension|x|y|z|block id" each. Kept so a crash cannot leave them standing.
     */
    public List<String> tempBlocks = new ArrayList<>();

    /** One defender's handed-in inventory. */
    public static class KitStash {
        /** One entry per inventory slot: the item as JSON, or "" for an empty slot. */
        public List<String> items = new ArrayList<>();
        /** The game mode before the night turned the player into a spectator, or "" if unchanged. */
        public String gameMode = "";
        /** Items that could not be read back (kept, so they are not lost). */
        public List<String> broken = new ArrayList<>();
    }

    public boolean isRegistered(String uuid) {
        return uuid != null && registered != null && registered.contains(uuid);
    }

    public boolean register(String uuid) {
        if (registered == null) {
            registered = new ArrayList<>();
        }
        if (uuid == null || registered.contains(uuid)) {
            return false;
        }
        registered.add(uuid);
        return true;
    }

    public boolean unregister(String uuid) {
        if (registered == null || uuid == null) {
            return false;
        }
        if (accepted != null) {
            accepted.remove(uuid);
        }
        if (declined != null) {
            declined.remove(uuid);
        }
        return registered.remove(uuid);
    }

    /** Answers an invitation: true = accept. Returns false if nothing changed. */
    public boolean vote(String uuid, boolean yes) {
        if (accepted == null) {
            accepted = new ArrayList<>();
        }
        if (declined == null) {
            declined = new ArrayList<>();
        }
        List<String> into = yes ? accepted : declined;
        List<String> other = yes ? declined : accepted;
        other.remove(uuid);
        if (into.contains(uuid)) {
            return false;
        }
        into.add(uuid);
        return true;
    }

    public void clearVotes() {
        if (accepted != null) {
            accepted.clear();
        }
        if (declined != null) {
            declined.clear();
        }
    }


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

    /** Siege nights held so far (real ones, won or lost): numbers the nights for catching up. */
    public int nightsHeld;
    /** Version of the balancing the points below were earned under. */
    public int balanceVersion;
    public static final int BALANCE_VERSION = 1;

    /**
     * Version 1 (October 2026): experience only from survived waves. Points earned
     * the old way (one per kill) are far too many, so everyone starts again:
     * classes stay, levels and points (and the shared core tree) go.
     * Returns how many players started again.
     */
    public int migrateBalance() {
        if (balanceVersion >= BALANCE_VERSION) {
            return 0;
        }
        int count = 0;
        if (skills != null) {
            for (PlayerSkills own : skills.values()) {
                if (own != null) {
                    own.restartProgress();
                    count++;
                }
            }
        }
        if (coreSkills != null) {
            coreSkills.restartProgress();
        }
        balanceVersion = BALANCE_VERSION;
        return count;
    }

    /** Version of the skill trees the levels below were learned in. */
    public int skillTreeVersion;
    public static final int SKILL_TREE_VERSION = 8;

    /**
     * Version 8 (26 October 2026, big balance update): everyone starts over. Points, levels,
     * classes, crate gains, crates still waiting and the core tree are gone (Simon: "we are
     * only testing, reset everything").
     * Version 7 (24 October 2026): the chemistry mine and poison trap became the mortar and the
     * brewer tower (learned levels move over), and the blademaster's combo and keen edge swapped
     * places (combo is the subclass node now, its levels are those of the old keen edge).
     * Version 6 rebuilt the shared core tree (masonry, battlements, rebuilding,
     * beacon fire instead of the watchtower); the core gets all its points back.
     * Version 5 rebuilt the engineer (mechanics or chemistry, the remote); every engineer gets all points back.
     * Version 4 rebuilt the guardian (shield or blessing); every guardian gets all points back.
     * Version 3 rebuilt the archer (bow or crossbow); every archer gets all points back.
     * Version 2 rebuilt the warrior (two ways instead of four paths; the paladin
     * moved over from the guardian, the spear path left). Every warrior, and every
     * guardian who had taken the paladin path, gets all points back to choose anew.
     * Returns how many players were reset.
     */
    public int migrateSkills() {
        if (skillTreeVersion >= SKILL_TREE_VERSION) {
            return 0;
        }
        java.util.Set<String> moved = java.util.Set.of("smite", "holy_vigor", "holy_light",
                "long_reach", "impale", "lance_thrust");
        // Version 3 rebuilt the archer the same way (bow or crossbow).
        boolean archers = skillTreeVersion < 3;
        boolean guardians = skillTreeVersion < 4;
        boolean engineers = skillTreeVersion < 5;
        boolean warriors = skillTreeVersion < 2;
        int reset = 0;
        if (skills != null) {
            for (PlayerSkills own : skills.values()) {
                if (own == null || own.levels == null) {
                    continue;
                }
                boolean touched = false;
                for (String id : moved) {
                    if (own.levelOf(id) > 0) {
                        touched = true;
                        break;
                    }
                }
                if ((warriors && (own.clazz() == com.mki.nightsiege.core.SkillClass.WARRIOR || touched))
                        || (archers && own.clazz() == com.mki.nightsiege.core.SkillClass.ARCHER)
                        || (guardians && own.clazz() == com.mki.nightsiege.core.SkillClass.GUARDIAN)
                        || (engineers && own.clazz() == com.mki.nightsiege.core.SkillClass.ENGINEER)) {
                    own.points = Math.max(0, own.earned - own.donated);
                    own.levels.clear();
                    reset++;
                }
            }
        }
        if (skillTreeVersion < 6 && coreSkills != null && coreSkills.levels != null) {
            coreSkills.points = Math.max(0, coreSkills.earned - coreSkills.donated - coreSkills.lost);
            coreSkills.levels.clear();
            if (coreSkills.history != null) {
                coreSkills.history.clear();
            }
        }
        if (skillTreeVersion < 7 && skills != null) {
            Map<String, String> renames = Map.of("blast_mine", "mortar_tower", "poison_trap", "potion_tower",
                    "keen_edge", "w_combo", "w_combo", "keen_edge");
            for (PlayerSkills own : skills.values()) {
                if (own != null) {
                    own.renameNodes(renames);
                }
            }
        }
        if (skillTreeVersion < 8) {
            reset = skills == null ? 0 : skills.size();
            if (skills != null) {
                skills.clear();
            }
            if (pendingCrates != null) {
                pendingCrates.clear();
            }
            coreSkills = new PlayerSkills();
        }
        skillTreeVersion = SKILL_TREE_VERSION;
        return reset;
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
