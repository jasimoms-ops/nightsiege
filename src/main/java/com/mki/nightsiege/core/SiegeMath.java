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
        double raw = cfg.baseMobsPerBatch + cfg.mobsPerLevel * growthSteps(level, cfg);
        double players = 1.0 + cfg.playerScaling * Math.max(0, playerCount - 1);
        int result = (int) Math.round(raw * players);
        return Math.max(1, Math.min(cfg.maxLiveMobs, result));
    }

    /** Health multiplier applied to every siege mob. */
    public static double healthMultiplier(int level, SiegeConfig cfg) {
        int capped = Math.min(Math.max(1, level), Math.max(1, cfg.softCapLevel));
        double raw = Math.min(cfg.maxHealthMultiplier, 1.0 + cfg.healthPerLevel * (capped - 1));
        // Above the soft cap it keeps growing slowly, past the old ceiling.
        return raw + cfg.healthPerLevel * stepsAboveCap(level, cfg);
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
