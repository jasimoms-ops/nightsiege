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
    /** Curse stones on the altar for the next night (CurseKind ids, update 2). */
    public List<String> curses = new ArrayList<>();
    /** The nemeses: monsters that killed a defender and come back (update 2). */
    public List<NemesisRecord> nemeses = new ArrayList<>();
    /** Counter for the nemeses' ids. */
    public int nemesisCounter;
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
    /** True while a siege holds the overworld clock before dawn (see SiegeManager.holdNight). */
    public boolean clockHeld;
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
     * Everyone who got today's invitation (Simon 31.10.: survival safety): a real night needs at most
     * the config's accepts, otherwise all invited players.
     */
    public List<String> invited = new ArrayList<>();
    /** Day an extra siege was last called at the core, and how often that day. */
    public long extraCalledDay = -1L;
    public int extraCallsToday;
    /** Name of the player who called it. */
    public String extraCalledBy;
    /** Day of the extra siege now invited or running (-1: none), and the regular siege day to go back to. */
    public long extraSiegeDay = -1L;
    public long extraRegularDay = -1L;
    /**
     * The own inventories of defenders while they wear their night kit, by player uuid.
     * Kept in the save, so a crash or restart in the night loses nothing.
     */
    public Map<String, KitStash> kitStashes = new HashMap<>();
    /**
     * Where each player keeps the items of a class's kit (Simon 29.10.: "sort the kit once, not every
     * round"): player → class → item key → inventory slot, noted when the kit goes back.
     */
    public Map<String, Map<String, Map<String, Integer>>> kitLayouts = new HashMap<>();
    /**
     * The kit page (update 6, Simon 4 November 2026): player → class → the crate gains whose items come
     * into the hotbar tonight. A class without an entry takes them on its own (rarest first, as many as
     * fit the free hotbar places).
     */
    public Map<String, Map<String, List<String>>> kitCrateChoice = new HashMap<>();
    /** Class banners (update 6): player → class → the day the last one was fetched (one a day). */
    public Map<String, Map<String, Long>> bannerDays = new HashMap<>();
    /** Class banners waiting for their player (the kit was on, or they were dead): player → classes. */
    public Map<String, List<String>> pendingBanners = new HashMap<>();
    /**
     * Reward crates waiting for their player, by player uuid: "CLASS|KIND|LEVEL" each. Won while
     * offline, or while the night kit is on (Simon 31.10.: crates never go into the kit inventory).
     */
    public Map<String, List<String>> pendingCrates = new HashMap<>();
    /**
     * Kit stash entries that could not be read back (an item of a removed mod, a Minecraft update),
     * by player uuid, as the raw text they were stored as (Simon 31.10.: survival safety). Kept so an
     * admin can recover them; the player is not stuck with a stash that never goes.
     */
    public Map<String, List<String>> kitQuarantine = new HashMap<>();
    /**
     * Playtest restores that still wait for an offline player when a new playtest starts, by player
     * uuid (Simon 31.10.: a second playtest overwrote them). The oldest one stays: it holds the real
     * inventory and skills.
     */
    public Map<String, PlaytestPlayer> playtestRestores = new HashMap<>();
    /**
     * Blocks the crate gains set down for one night only (barricades, banners, ladders,
     * light): "dimension|x|y|z|block id" each. Kept so a crash cannot leave them standing.
     */
    public List<String> tempBlocks = new ArrayList<>();

    /**
     * Chunks where players have set blocks down, by dimension (update 30.10., Simon: "die dungeons oder
     * heerlager dürfen auf keinen fall in irgendwelchen von spieler platzierten blöcken spawnen").
     * Chunk keys as {@code ChunkPos.asLong}.
     */
    public Map<String, java.util.Set<Long>> playerBuilt = new HashMap<>();
    /**
     * War camps whose ground under them is written down, "id|dimension" (update 30.10.): it goes back
     * when the camp goes, chunk by chunk as they load.
     */
    public List<String> campGrounds = new ArrayList<>();

    /** The war camp standing before a siege night (27 October 2026), or null. */
    public CampRecord camp;
    /** Day whose camp has been rolled for (one roll per siege). */
    public long campRolledDay = -1L;
    /** Siege day whose night brings the machines for sure (the camp won and is gone), or -1. */
    public long machinesDay = -1L;
    /**
     * How many machines that night brings: 0 = a camp's (the level's count times machinesCampFactor),
     * more = so many (the machines of a broken-off night come again as they were).
     */
    public int machinesCount = 0;
    /** Players wearing a kit from the camp fight (it goes back when they leave, fall or log in later). */
    public List<String> campKits = new ArrayList<>();

    /** The dungeon the scouts reported (dungeon update, 27 October 2026), or null. A won one stays as its ruin. */
    public ExpeditionRecord expedition;
    /**
     * Dungeons taken away (removed, replaced, the playtest's) whose ground is still coming back,
     * a few milliseconds a tick (update 30.10.); kept so a restart finishes them.
     */
    public List<ExpeditionRecord> expeditionTeardowns = new ArrayList<>();
    /** Players wearing a kit from a dungeon fight (it goes back when they leave, fall or log in later). */
    public List<String> expeditionKits = new ArrayList<>();
    /**
     * Survival safety (Simon 31.10.): where a dungeon or a war camp was taken down. Who stands in a block
     * there while the ground comes back, or logs in later in one (offline during the fight, on the
     * waiting bench), is put on top of the ground (see SafeGround).
     */
    public List<GoneSite> goneSites = new ArrayList<>();

    /** A place a dungeon or a camp was taken down (its area, and whether its ground is still coming back). */
    public static class GoneSite {
        /** "dungeon" or "camp". */
        public String kind = "";
        public int id;
        public String dimension = "";
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        public boolean restoring;
        /** The day it was taken down (the oldest go first when there are too many). */
        public long day;
    }

    /** The playtest running now (Simon, 28 October 2026), or null. Kept so a restart can undo it. */
    public PlaytestState playtest;
    /**
     * What the last playtests brought, newest last (Simon 04.11.: "Playtest 2", more variety): their
     * dungeon kinds, events and bosses come less often in the next one.
     */
    public PlaytestRecent playtestRecent = new PlaytestRecent();

    /** The dungeon kinds, events and bosses of the last playtests (ids, newest last, a few dozen at most). */
    public static class PlaytestRecent {
        public List<String> dungeons = new ArrayList<>();
        public List<String> events = new ArrayList<>();
        public List<String> bosses = new ArrayList<>();
    }

    /**
     * The war leader of a running round (Kriegsführer, 29 October 2026): where his body stood and
     * in which game mode, and the time of day before the round. Kept so a crash or a log-out in
     * the middle of the round still gives him back his place; null when no round runs.
     */
    public WarRestore warLeader;
    /** The war leader's round: its own records (a fun mode, no reward). */
    public WarRecords war = new WarRecords();

    /** The time of day before a round of the Letzte Bastion (Update 7, 07.11.): kept for a crash, -1 when none runs. */
    public int bastionTimeOfDay = -1;
    /** The Letzte Bastion's best runs (Update 7): its only reward is fame. They stay through the progress reset. */
    public List<com.mki.nightsiege.core.BastionMath.Run> bastionRuns = new ArrayList<>();
    /** Rounds of the Letzte Bastion played (counted ones). */
    public int bastionRounds;

    /**
     * The fun mode's lent profiles (Update 7, Simon 06.11.: "jede Klasse ... immer komplett durchgelevelt"):
     * for a round of the Letzte Bastion or the war leader every defender plays a full profile of any
     * subclass. Only in memory: after the round, a crash or a restart the own profile is back untouched.
     */
    public transient Map<String, PlayerSkills> loans = new HashMap<>();

    public static class WarRestore {
        public String id = "";
        public String name = "";
        public String dimension = "";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String gameMode = "survival";
        public int timeOfDay = -1;
    }

    public static class WarRecords {
        public int rounds;
        public int leaderWins;
        public int defenderWins;
        /** The fastest win of a war leader: seconds since the first wave, the wave, who, against how many. */
        public int fastestSeconds = -1;
        public int fastestWave;
        public String fastestBy = "";
        public int fastestDefenders;
        public String fastestDate = "";
        public Map<String, WarPlayer> players = new HashMap<>();

        public WarPlayer of(String id, String name) {
            WarPlayer p = players.computeIfAbsent(id, k -> new WarPlayer());
            if (name != null && !name.isBlank()) {
                p.name = name;
            }
            return p;
        }
    }

    public static class WarPlayer {
        public String name = "";
        public int leaderWins;
        public int leaderLosses;
        public int defenderWins;
        public int defenderLosses;
    }

    /**
     * A playtest: four test nights of rising level, before each a war camp or a dungeon, the
     * skills replayed along each player's planned path. Afterwards everything is as before.
     */
    public static class PlaytestState {
        public int target;
        /** The level of each of the four nights. */
        public List<Integer> levels = new ArrayList<>();
        /** Before each night: "camp" or "dungeon". */
        public List<String> interludes = new ArrayList<>();
        /** Which night brings a boss (index), or -1. */
        public int bossNight = -1;
        public String boss = "";
        /**
         * (Playtest 2, Simon 04.11.) The dungeon kind before each night (ExpeditionKind id, "" before the
         * camp) and the event of each night (SiegeEvent name, "" for the boss night), fixed at the start.
         * Empty in a playtest begun before: the scouts and the night pick as they did.
         */
        public List<String> dungeons = new ArrayList<>();
        public List<String> events = new ArrayList<>();
        /** The night being played (0-3), -1 while the paths are planned. */
        public int step = -1;
        /** plan, pause_interlude, interlude, pause_night, night, after_night, paused, done. */
        public String phase = "plan";
        /**
         * (Playtest 2) Paused because nobody of it was online: the part played again once everyone online
         * is ready – "interlude" (the camp or dungeon of this step), "night" (its night) or "next" (the
         * next step, paused after a night).
         */
        public String pausedPart = "";
        /** Game time the current pause or interlude ends at (-1: none). */
        public long phaseEnds = -1L;
        public boolean finished;
        /** The camp before this night won (or stood untouched): its machines come. */
        public boolean campWon;
        /** Siege day of the playtest's camp, to tell whether it won. */
        public long campDay = -1L;
        /** Lost dungeon attempts when the dungeon of this step began. */
        public int lostAttempts;
        public Map<String, PlaytestPlayer> players = new java.util.LinkedHashMap<>();
        /** What the world had before the playtest took them over. */
        public ExpeditionRecord realExpedition;
        public CampRecord realCamp;
        public long realMachinesDay = -1L;
    }

    /** One player of a playtest: what they had before, their planned path, ready and crates. */
    public static class PlaytestPlayer {
        public String name = "";
        /** Their skills before the playtest (JSON). */
        public String skills = "";
        /** Their inventory before the playtest, slot by slot ("" = empty). */
        public List<String> items = new ArrayList<>();
        /** The planned skill path: every level in the order it was learned. */
        public List<String> path = new ArrayList<>();
        public boolean ready;
        public int cratesGiven;
        public int cratesDue;
        /** The skill points the nights have given so far (the next night adds only the rest). */
        public int pointsGiven;
        /** The playtest ended while they were away: their things come back when they log in. */
        public boolean restorePending;
    }

    /** One nemesis (update 2 "Fluch & Rache"): who it is, whom it slew, whether it still lurks. */
    public static class NemesisRecord {
        public String id = "";
        /** Its own name ("Moderklaue") and the entity it is (an entry id of the monster list). */
        public String name = "";
        public String type = "minecraft:zombie";
        public int level = 1;
        /** Trait ids (NemesisTrait). */
        public List<String> traits = new ArrayList<>();
        /** Names and ids of the defenders it slew, oldest first. */
        public List<String> victims = new ArrayList<>();
        public List<String> victimIds = new ArrayList<>();
        /** The class of its first victim (its trait "class foe" works against that class). */
        public String foeClass = "";
        public int kills;
        /** Nights since it last showed. */
        public int nightsAway;
        /** "lauert" (lurks), "erschlagen" (slain). */
        public String status = "lauert";
        public String slainBy = "";
        public long slainAt;
        /** Made in a test night: it only comes back in test nights. */
        public boolean test;

        public NemesisRecord() {
        }

        public boolean lurking() {
            return "lauert".equals(status);
        }
    }

    /**
     * A Night Siege dungeon. Its blocks follow from the record (see ExpeditionLayout); the
     * fight itself lives in memory only, a restart starts the open room again.
     */
    public static class ExpeditionRecord {
        public int id;
        /** {@link com.mki.nightsiege.core.ExpeditionKind} id. */
        public String kind = "";
        public String dimension = "";
        /** The gatehouse middle at the height one walks on (before it is built: the planned spot, y unknown). */
        public int x;
        public int y;
        public int z;
        /** The way down leads away from the core: 0 south, 1 west, 2 north, 3 east. */
        public int facing;
        /** Guard rooms (by the world level when it was reported). */
        public int rooms = 2;
        public long reportedDay = -1L;
        /** Its place is fixed and it is being set (or stands); see {@link #complete}. */
        public boolean built;
        /**
         * Every block of it is set (update 30.10.: it is built over many ticks; nobody goes in, no
         * ready round, no playtest before this).
         */
        public boolean complete;
        /** Chunks (as ChunkPos longs) whose part of the dungeon is set. */
        public List<Long> chunksDone = new ArrayList<>();
        /** Guard rooms cleared (their gates stay open). */
        public int cleared;
        /** The champion fell: the dungeon is a ruin now. */
        public boolean won;
        /** Attempts so far (for the statistics). */
        public int attempts;
        /** The scouts' map: one letter per cell (terrain), row by row, {@link #mapSize} cells a side. */
        public String map = "";
        public int mapX;
        public int mapZ;
        public int mapCell = 16;
        public int mapSize;
        /** The plan version it was built with (content update: 2, with a snapshot of the ground). */
        public int layout;
        /** After the win: when the dungeon goes (game time), and whether it is being taken down now. */
        public long collapseAt;
        public boolean restoring;
        /**
         * Survival safety (31.10.): the ground round the gatehouse is levelled and the world was saved
         * since (false: written down, but a crash may have lost the levelling; null: a dungeon of before).
         */
        public Boolean levelDone;
    }

    /**
     * The war camp of the besiegers. Its blocks follow from the record (see CampLayout), so
     * only where and how it stands is kept; it is built as soon as its ground is loaded.
     */
    public static class CampRecord {
        /** A number of this camp, also on its monsters (tag), so old ones go when they load. */
        public int id;
        public String dimension = "";
        /** Middle of the camp at the height one walks on (the floor lies one below). */
        public int x;
        public int y;
        public int z;
        /** The siege day this camp belongs to (it stands from the day before). */
        public long day = -1L;
        /** Where the gate opens (towards the core): 0 south, 1 west, 2 north, 3 east. */
        public int facing;
        /** Blocks set and the guards there. */
        public boolean built;
        public boolean manned;
        /** The workshop was destroyed: no machines tonight. */
        public boolean destroyed;
        /** The workshop hit box (its uuid). */
        public String workshop = "";
        public List<String> guards = new ArrayList<>();
        /** Content update: the guards of the tents (the inner ring opens when they have fallen) and the crossbowmen on the towers. */
        public List<String> tentGuards = new ArrayList<>();
        public List<String> crossbows = new ArrayList<>();
        /** The inner ring's gate is open (its tent guards have fallen). */
        public boolean ringOpen;
    }

    /** One defender's handed-in inventory. */
    public static class KitStash {
        /**
         * One entry per inventory slot: the item as text ("snbt:" + SNBT since 31.10., JSON before),
         * or "" for an empty slot. Entries past the inventory's size are loose things (go into free slots).
         */
        public List<String> items = new ArrayList<>();
        /** The game mode before the night turned the player into a spectator, or "" if unchanged. */
        public String gameMode = "";
        /** Items that could not be read back (saves before 31.10.; they move to {@link SiegeSave#kitQuarantine}). */
        public List<String> broken = new ArrayList<>();
        /**
         * Where the swap stands (Simon 31.10.: survival safety): "applying" while the kit goes on,
         * "applied" once the player's own data is saved with it, "restoring" while it goes back.
         * "" (saves from before) counts as applied. A half-done swap after a crash is found by it.
         */
        public String state = "";
        /** The night is over but the player was dead: the own things come back after the respawn. */
        public boolean restoreOnRespawn;
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

    /** Notes that this player got today's invitation. Returns false if already noted. */
    public boolean invite(String uuid) {
        if (invited == null) {
            invited = new ArrayList<>();
        }
        if (uuid == null || invited.contains(uuid)) {
            return false;
        }
        invited.add(uuid);
        return true;
    }

    /** Distinct players invited today who are still signed up (answers included). */
    public int invitedCount() {
        java.util.Set<String> all = new java.util.HashSet<>();
        for (List<String> list : java.util.Arrays.asList(invited, accepted, declined)) {
            if (list != null) {
                for (String id : list) {
                    if (isRegistered(id)) {
                        all.add(id);
                    }
                }
            }
        }
        return all.size();
    }

    /** Answers an invitation: true = accept. Returns false if nothing changed. */
    public boolean vote(String uuid, boolean yes) {
        invite(uuid);   // whoever answers counts as invited (signed up after the invitations went out)
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
        if (invited != null) {
            invited.clear();
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

    /**
     * The records of the statistics window (content update, 29 October 2026). Not part of anyone's
     * progress: a progress reset leaves them standing (the hall of fame).
     */
    public com.mki.nightsiege.core.NightRecords records = new com.mki.nightsiege.core.NightRecords();
    /** The Ruhmeshalle (31.10.): left alone by the progress reset, like the records. */
    public com.mki.nightsiege.core.HallOfFame hall = new com.mki.nightsiege.core.HallOfFame();

    /** Siege nights held so far (real ones, won or lost): numbers the nights for catching up. */
    public int nightsHeld;
    /** The number on the next crown of the Vierer-Nacht (one more after every won one). */
    public int season = 1;
    /**
     * Real nights in the boss rhythm so far (Simon 29.10.: boss nights 4, 8, 12, 16, the Vierer-Nacht
     * on 20 and 40, from 41 every 2nd night two bosses); a lost boss night or Vierer-Nacht does not
     * count. -1 until counted (then from nightsHeld).
     */
    public int bossNights = -1;
    /** The bosses of the boss nights since the last Vierer-Nacht (its four). */
    public List<String> groupBosses = new ArrayList<>();
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

    /**
     * True once the alchemist's potions moved into the tree as nodes (28 October 2026): every
     * alchemist got the first three potions he had, extra potion nodes were paid back.
     */
    public boolean brewsMigrated;

    /**
     * True once the owners of the crate gains that left the crates (Blendgranaten, Köcher; balance
     * update 2, 29 October 2026) got their duplicate points back: one payout per stage they had.
     */
    public boolean retiredGainsPaid;

    /** Pays back the retired crate gains (see {@link #retiredGainsPaid}); returns the points paid. */
    public int payRetiredGains() {
        int paid = 0;
        if (skills == null) {
            return 0;
        }
        for (PlayerSkills own : skills.values()) {
            if (own == null || own.gains == null) {
                continue;
            }
            for (java.util.Map.Entry<String, Integer> retired : com.mki.nightsiege.core.CrateGains.RETIRED.entrySet()) {
                String[] key = retired.getKey().split(":", 2);
                java.util.Map<String, Integer> map = own.gains.get(key[0]);
                Integer stage = map == null ? null : map.get(key[1]);
                if (stage != null && stage > 0) {
                    int points = stage * retired.getValue();
                    own.award(points);
                    paid += points;
                    map.remove(key[1]);
                }
            }
        }
        return paid;
    }

    /** True once Tor 4b.5 moved the engineers' remote gains (zweite_ladung, recycling). */
    public boolean remoteGainsMigrated;

    /**
     * Tor 4b.5: zweite_ladung becomes the Notschalter (ersatzbatterie, one stage; already owning it, the
     * duplicate points of a rare stage instead), recycling pays its rare duplicate points per stage.
     * Runs once; returns the points paid.
     */
    public int migrateRemoteGains() {
        if (remoteGainsMigrated) {
            return 0;
        }
        remoteGainsMigrated = true;
        int paid = 0;
        if (skills == null) {
            return 0;
        }
        for (PlayerSkills own : skills.values()) {
            if (own == null || own.gains == null) {
                continue;
            }
            for (java.util.Map.Entry<String, String> moved : com.mki.nightsiege.core.CrateGains.RENAMED_4B.entrySet()) {
                String[] key = moved.getKey().split(":", 2);
                java.util.Map<String, Integer> map = own.gains.get(key[0]);
                Integer stage = map == null ? null : map.remove(key[1]);
                if (stage != null && stage > 0) {
                    if (map.getOrDefault(moved.getValue(), 0) > 0) {
                        int points = stage * com.mki.nightsiege.core.CrateRarity.RARE.duplicatePoints();
                        own.award(points);
                        paid += points;
                    } else {
                        map.put(moved.getValue(), 1);
                    }
                }
            }
            for (java.util.Map.Entry<String, Integer> retired : com.mki.nightsiege.core.CrateGains.RETIRED_4B.entrySet()) {
                String[] key = retired.getKey().split(":", 2);
                java.util.Map<String, Integer> map = own.gains.get(key[0]);
                Integer stage = map == null ? null : map.remove(key[1]);
                if (stage != null && stage > 0) {
                    int points = stage * retired.getValue();
                    own.award(points);
                    paid += points;
                }
            }
        }
        return paid;
    }

    /** True once Tor 5.4 moved the gains that left the crates onto their successors. */
    public boolean loot5Migrated;

    /**
     * Tor 5.4 (Chef 22:42): fackeln -> baukiste, spektralpfeile -> lichtpfeile, fester_griff -> turmschild,
     * leuchtschild -> schildlaterne (stage 1 of the successor; already owning it, the old gain's duplicate
     * points per stage instead). Stages above a gain's new top (proviant 4 -> 2) pay their duplicate points.
     * Runs once; returns the points paid.
     */
    public int migrateLoot5() {
        if (loot5Migrated) {
            return 0;
        }
        loot5Migrated = true;
        int paid = 0;
        if (skills == null) {
            return 0;
        }
        for (PlayerSkills own : skills.values()) {
            if (own == null || own.gains == null) {
                continue;
            }
            for (java.util.Map.Entry<String, java.util.Map<String, Integer>> byClass : own.gains.entrySet()) {
                java.util.Map<String, Integer> map = byClass.getValue();
                if (map == null) {
                    continue;
                }
                for (com.mki.nightsiege.core.CrateGains.Moved moved : com.mki.nightsiege.core.CrateGains.LOOT_5) {
                    if (!moved.clazz().equals("*") && !moved.clazz().equals(byClass.getKey())) {
                        continue;
                    }
                    Integer stage = map.remove(moved.from());
                    if (stage == null || stage <= 0) {
                        continue;
                    }
                    if (map.getOrDefault(moved.to(), 0) > 0) {
                        int points = stage * moved.rarity().duplicatePoints();
                        own.award(points);
                        paid += points;
                    } else {
                        map.put(moved.to(), 1);
                    }
                }
                for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
                    com.mki.nightsiege.core.CrateGains.Gain gain = com.mki.nightsiege.core.CrateGains.get(e.getKey());
                    if (gain != null && !gain.consumable() && e.getValue() != null && e.getValue() > gain.topStage()) {
                        int points = (e.getValue() - gain.topStage()) * gain.rarity().duplicatePoints();
                        own.award(points);
                        paid += points;
                        e.setValue(gain.topStage());
                    }
                }
            }
        }
        return paid;
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

    /**
     * Nodes that left a tree, with the node that took their place (same ring, cost and levels, so
     * what was learned moves over as it is and the points stay spent). Runs on every load; once
     * moved, nothing is left to move. Soulcatcher (Simon's playtest, 28 October 2026): the soul
     * shield and the soul chain are gone, Seelenschild became Geisterhast, Seelenfessel Geisterschar.
     */
    public static final Map<String, String> RETIRED_NODES = Map.of("d_soulshield", "d_haste", "d_fetter", "d_host");

    /** Moves learned levels of retired nodes to their successors. Returns how many players had one. */
    public int moveRetiredNodes() {
        int moved = 0;
        if (skills == null) {
            return 0;
        }
        for (PlayerSkills own : skills.values()) {
            if (own != null && own.hasAnyNode(RETIRED_NODES.keySet())) {
                own.renameNodes(RETIRED_NODES);
                moved++;
            }
        }
        return moved;
    }

    /** The skills a player plays with now: a fun mode's lent profile (Update 7) if there is one, else their own. */
    public PlayerSkills skillsOf(String playerUuid) {
        if (loans != null && playerUuid != null) {
            PlayerSkills lent = loans.get(playerUuid);
            if (lent != null) {
                return lent;
            }
        }
        return realSkillsOf(playerUuid);
    }

    /** The player's own skills, also while a fun mode lends them a profile (crates, the save). */
    public PlayerSkills realSkillsOf(String playerUuid) {
        if (skills == null) {
            skills = new HashMap<>();
        }
        return skills.computeIfAbsent(playerUuid, key -> new PlayerSkills());
    }

    /** True while the player plays a fun mode's lent profile. */
    public boolean hasLoan(String playerUuid) {
        return loans != null && playerUuid != null && loans.containsKey(playerUuid);
    }

    /** Lends a profile for the fun mode (in memory only). */
    public void lend(String playerUuid, PlayerSkills lent) {
        if (loans == null) {
            loans = new HashMap<>();
        }
        loans.put(playerUuid, lent);
    }

    /** Takes a lent profile back; true when there was one. */
    public boolean endLoan(String playerUuid) {
        return loans != null && playerUuid != null && loans.remove(playerUuid) != null;
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
