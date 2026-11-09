package com.mki.nightsiege.core;

/** All difficulty formulas. Pure arithmetic so it can be unit tested. */
public final class SiegeMath {

    private SiegeMath() {
    }

    /**
     * Siege level: starts at 1 and rises with every siege survived (and, if set,
     * with the age of the world). A lost night keeps its level: it comes again.
     */
    public static int level(int siegesWon, long ingameDays, SiegeConfig cfg) {
        long fromDays = cfg.daysPerLevel <= 0 ? 0L : Math.max(0L, ingameDays) / cfg.daysPerLevel;
        long raw = 1L + Math.max(0, siegesWon) + fromDays;
        return (int) Math.min(cfg.maxLevel, Math.max(1L, raw));
    }

    /** How long one siege lasts, in seconds. */
    public static int durationSeconds(int level, SiegeConfig cfg) {
        long raw = (long) cfg.baseDurationSeconds
                + (long) cfg.durationSecondsPerLevel * Math.max(0, level - 1);
        return (int) Math.min(cfg.maxDurationSeconds, raw);
    }

    /**
     * Steps of growth a level brings above level 1: the full step up to the soft
     * cap (20), then only growthAboveSoftCap of a step per level, without end.
     */
    public static double growthSteps(int level, SiegeConfig cfg) {
        int soft = Math.max(1, cfg.softCapLevel);
        int below = Math.min(Math.max(1, level), soft) - 1;
        int above = Math.max(0, level - soft);
        return below + above * Math.max(0.0, cfg.growthAboveSoftCap);
    }

    /** Levels above the soft cap, weighted with growthAboveSoftCap (0 up to the soft cap). */
    private static double stepsAboveCap(int level, SiegeConfig cfg) {
        return Math.max(0, level - Math.max(1, cfg.softCapLevel)) * Math.max(0.0, cfg.growthAboveSoftCap);
    }

    /** How many mobs one batch spawns. */
    public static int mobsPerBatch(int level, int playerCount, SiegeConfig cfg) {
        return mobsPerBatch(level, playerCount, cfg, cfg.playerScaling, cfg.maxLiveMobs);
    }

    /** As above with the share per further player and the cap given (the playtest scales per player). */
    public static int mobsPerBatch(int level, int playerCount, SiegeConfig cfg, double perPlayer, int maxLive) {
        return Math.max(1, Math.min(Math.max(1, maxLive), (int) Math.round(mobsWanted(level, playerCount, cfg, perPlayer))));
    }

    /**
     * The monsters a batch would bring without any cap: the level's monsters times
     * (soloScaling + perPlayer × further players), per-player scaling of 29 October 2026.
     */
    public static double mobsWanted(int level, int playerCount, SiegeConfig cfg, double perPlayer) {
        double raw = cfg.baseMobsPerBatch + cfg.mobsPerLevel * growthSteps(level, cfg);
        double players = Math.max(0.0, cfg.soloScaling) + Math.max(0.0, perPlayer) * Math.max(0, playerCount - 1);
        return raw * players;
    }

    /** The cap of live monsters on a normal night: grows by livePerPlayer for every further player, at most maxLiveMobsCap. */
    public static int liveCap(int playerCount, SiegeConfig cfg) {
        int highest = Math.max(1, cfg.maxLiveMobsCap);
        if (cfg.liveCapByPlayers != null && !cfg.liveCapByPlayers.isEmpty()) {
            java.util.List<Integer> table = cfg.liveCapByPlayers;
            int i = Math.max(0, playerCount - 1);
            int v = i < table.size() ? table.get(i) : table.get(table.size() - 1) + cfg.liveCapStep * (i - table.size() + 1);
            return Math.max(1, Math.min(highest, v));
        }
        double grown = cfg.maxLiveMobs * (1.0 + Math.max(0.0, cfg.livePerPlayer) * Math.max(0, playerCount - 1));
        return (int) Math.max(1, Math.min(Math.max(cfg.maxLiveMobs, cfg.maxLiveMobsCap), Math.round(grown)));
    }

    /**
     * Feld-Stärke (Simon 30.10.): the reference field of this group size (fieldRefCaps) over the cap it
     * really gets, at least 1; 1 without a reference.
     */
    public static double fieldStrength(int playerCount, int cap, SiegeConfig cfg) {
        if (cfg.fieldRefCaps == null || cfg.fieldRefCaps.isEmpty() || cap <= 0) {
            return 1.0;
        }
        java.util.List<Integer> table = cfg.fieldRefCaps;
        int i = Math.max(0, playerCount - 1);
        int ref = i < table.size() ? table.get(i) : table.get(table.size() - 1) + cfg.liveCapStep * (i - table.size() + 1);
        return Math.max(1.0, ref / (double) cap);
    }

    /** Share of the monsters' damage a defender takes in a night with this many defenders. */
    public static double damageTakenForGroup(int playerCount, SiegeConfig cfg) {
        if (cfg.damageTakenByPlayers != null && !cfg.damageTakenByPlayers.isEmpty()) {
            java.util.List<Double> table = cfg.damageTakenByPlayers;
            return table.get(Math.min(table.size() - 1, Math.max(0, playerCount - 1)));
        }
        return playerCount <= 1 ? cfg.soloDamageTaken : 1.0;
    }

    /** Life of the night's monsters for a group of this size (mobHealthByPlayers; 1 without a table). */
    public static double healthForGroup(int playerCount, SiegeConfig cfg) {
        if (cfg.mobHealthByPlayers != null && !cfg.mobHealthByPlayers.isEmpty()) {
            java.util.List<Double> table = cfg.mobHealthByPlayers;
            return table.get(Math.min(table.size() - 1, Math.max(0, playerCount - 1)));
        }
        return 1.0;
    }

    /**
     * Tor 6.4: life of an elite or a boss for this many defenders here: 1 + perPlayer × further players
     * (Diablo III's +50 % per player; normal monsters grow in number instead).
     */
    public static double groupHealth(int playerCount, double perPlayer) {
        return 1.0 + Math.max(0.0, perPlayer) * Math.max(0, playerCount - 1);
    }

    /**
     * Tor 6.4: the extra elite chance of each monster of a wave whose wanted size goes beyond the cap:
     * (wanted − cap) ÷ overflowEliteCost elites spread over the cap's monsters, at most 1; 0 within the cap or off.
     */
    public static double overflowEliteChance(double wanted, int cap, SiegeConfig cfg) {
        if (cfg.overflowEliteCost <= 0 || cap <= 0 || wanted <= cap) {
            return 0.0;
        }
        return Math.min(1.0, (wanted - cap) / cfg.overflowEliteCost / cap);
    }

    /**
     * Tor 6.4: whether a defender of the night still counts for its size: online and in range, or out of it
     * (offline, or away from the night) for less than its grace. Coming back he counts again at once.
     */
    public static boolean countsForSize(boolean online, long offlineTicks, long offlineGrace,
                                        boolean inRange, long awayTicks, long awayGrace) {
        if (!online) {
            return offlineTicks < offlineGrace;
        }
        return inRange || awayTicks < awayGrace;
    }

    /** Life factor for monsters a capped batch could not bring (overflowHealth; 1 = no overflow). */
    public static double overflowHealthFactor(double wanted, int cap, SiegeConfig cfg) {
        if (cfg.overflowHealth <= 0 || cap <= 0 || wanted <= cap) {
            return 1.0;
        }
        return 1.0 + cfg.overflowHealth * (wanted / cap - 1.0);
    }

    /** Health multiplier applied to every siege mob. */
    public static double healthMultiplier(int level, SiegeConfig cfg) {
        int capped = Math.min(Math.max(1, level), Math.max(1, cfg.softCapLevel));
        double raw = Math.min(cfg.maxHealthMultiplier, 1.0 + cfg.healthPerLevel * (capped - 1));
        // Above the soft cap it keeps growing slowly, past the old ceiling.
        return raw + cfg.healthPerLevel * stepsAboveCap(level, cfg);
    }

    /**
     * A fixed skill damage on this world level: grows like the monsters' life (+healthPerLevel per level),
     * the same rule as the newer F abilities ({@code f_dmg}) and the shield skills (Tor 4).
     */
    public static double skillDamage(double base, int level, SiegeConfig cfg) {
        return base * healthMultiplier(level, cfg);
    }

    /** Attack damage multiplier applied to every siege mob. */
    public static double damageMultiplier(int level, SiegeConfig cfg) {
        int capped = Math.min(Math.max(1, level), Math.max(1, cfg.softCapLevel));
        double raw = Math.min(cfg.maxDamageMultiplier, 1.0 + cfg.damagePerLevel * (capped - 1));
        return raw + cfg.damagePerLevel * stepsAboveCap(level, cfg);
    }

    /** In-game day for a given total world time. */
    public static long dayOf(long gameTime) {
        return Math.max(0L, gameTime) / 24000L;
    }

    /** Time within the current in-game day, 0 to 23999. */
    public static int timeOfDay(long gameTime) {
        return (int) (Math.max(0L, gameTime) % 24000L);
    }

    /**
     * Kills a night asks for: the share of its monsters, but never more than
     * that share of what has come and can still come.
     */
    public static int killsRequired(double share, int quota, int spawned, int wavesLeft, int perWave) {
        if (share <= 0) {
            return 0;
        }
        int possible = spawned + Math.max(0, wavesLeft) * Math.max(0, perWave);
        return (int) Math.min(quota, Math.ceil(share * possible));
    }

    /** The kill goal set at the start of a night. */
    public static int killQuota(double share, int waves, int perWave) {
        return (int) Math.ceil(Math.max(0, share) * Math.max(0, waves) * Math.max(0, perWave));
    }
}
