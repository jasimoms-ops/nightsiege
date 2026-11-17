package com.mki.nightsiege.core;

/**
 * Tor 12 F (Simon 17.11.: "dann sollte eine Nacht maximal 8 Minuten dauern"): every real night lasts at most
 * maxDurationSeconds (the ready phase does not count): normal, event, extra, warden and boss nights and the
 * finale. The Bossuhr ends at that time at the latest; the finale's pairs and pauses fit into it.
 * Not: the war leader's round, the Letzte Bastion, dungeons and camps.
 */
public final class NightClock {

    private NightClock() {
    }

    /** The longest night in ticks. */
    public static int capTicks(SiegeConfig cfg) {
        return Math.max(20, cfg.maxDurationSeconds) * 20;
    }

    /**
     * Bossuhr: the boss phase's limit from the boss's arrival ({@code arrivedAt} ticks into the night), as
     * {@link BossNight#phaseLimitTicks}, but over at the night's cap at the latest.
     */
    public static int bossPhaseLimit(int totalTicks, int arrivedAt, SiegeConfig cfg) {
        int limit = BossNight.phaseLimitTicks(totalTicks - arrivedAt, cfg.bossPhaseMaxSeconds);
        return Math.max(0, Math.min(limit, capTicks(cfg) - arrivedAt));
    }

    /** Pairs (groups of bossGroupAtOnce) the finale's bosses come in. */
    public static int finalePairs(int bosses, SiegeConfig cfg) {
        int atOnce = Math.max(1, cfg.bossGroupAtOnce);
        return Math.max(1, (Math.max(0, bosses) + atOnce - 1) / atOnce);
    }

    /**
     * The finale: ticks after which the next pair comes even when one of the last still stands —
     * bossGroupPairSeconds, shorter if the pairs would not fit into the night after the first pause.
     */
    public static int finalePairTicks(int bosses, int totalTicks, SiegeConfig cfg) {
        int room = totalTicks - cfg.bossGroupPauseSeconds * 20;
        return Math.max(20, Math.min(cfg.bossGroupPairSeconds * 20, room / finalePairs(bosses, cfg)));
    }

    /** The finale: the latest tick of the night at which its last pair comes. */
    public static int finaleLastPairAt(int bosses, int totalTicks, SiegeConfig cfg) {
        return cfg.bossGroupPauseSeconds * 20 + (finalePairs(bosses, cfg) - 1) * finalePairTicks(bosses, totalTicks, cfg);
    }
}
