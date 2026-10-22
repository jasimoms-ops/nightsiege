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

    /** How many mobs one batch spawns. */
    public static int mobsPerBatch(int level, int playerCount, SiegeConfig cfg) {
        double raw = cfg.baseMobsPerBatch + cfg.mobsPerLevel * Math.max(0, level - 1);
        double players = 1.0 + cfg.playerScaling * Math.max(0, playerCount - 1);
        int result = (int) Math.round(raw * players);
        return Math.max(1, Math.min(cfg.maxLiveMobs, result));
    }

    /** Health multiplier applied to every siege mob. */
    public static double healthMultiplier(int level, SiegeConfig cfg) {
        double raw = 1.0 + cfg.healthPerLevel * Math.max(0, level - 1);
        return Math.min(cfg.maxHealthMultiplier, raw);
    }

    /** Attack damage multiplier applied to every siege mob. */
    public static double damageMultiplier(int level, SiegeConfig cfg) {
        double raw = 1.0 + cfg.damagePerLevel * Math.max(0, level - 1);
        return Math.min(cfg.maxDamageMultiplier, raw);
    }

    /** In-game day for a given total world time. */
    public static long dayOf(long gameTime) {
        return Math.max(0L, gameTime) / 24000L;
    }

    /** Time within the current in-game day, 0 to 23999. */
    public static int timeOfDay(long gameTime) {
        return (int) (Math.max(0L, gameTime) % 24000L);
    }
}
