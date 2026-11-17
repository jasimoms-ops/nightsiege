package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 F (Simon 17.11.: "dann sollte eine Nacht maximal 8 Minuten dauern"): every real night ≤ 8:00. */
class NightClockTest {

    /** The config with the night's cap of the plan (maxDurationSeconds 480; the migration comes with v31). */
    private static SiegeConfig eight() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.maxDurationSeconds = 480;
        return cfg;
    }

    @Test
    void normalEventExtraAndWardenNightsLastAtMostTheCap() {
        SiegeConfig cfg = eight();
        for (int level = 1; level <= cfg.maxLevel; level++) {
            // normal, event and extra nights and the warden's (he comes before dawn, dawn ends it) share the clock
            assertTrue(SiegeMath.durationSeconds(level, cfg) <= 480, "level " + level);
        }
        assertEquals(9600, NightClock.capTicks(cfg));
    }

    @Test
    void theBossComesAfterThreeMinutesAndHisClockEndsAtTheCap() {
        SiegeConfig cfg = eight();
        int total = SiegeMath.durationSeconds(cfg.maxLevel, cfg) * 20;
        assertEquals(3600, BossNight.arrivalTicks(total, cfg));   // 3:00
        for (int level = 1; level <= cfg.maxLevel; level++) {
            int night = SiegeMath.durationSeconds(level, cfg) * 20;
            int arrival = BossNight.arrivalTicks(night, cfg);
            for (int at = arrival; at <= night; at += 200) {   // the boss may come late (no spot at first)
                int limit = NightClock.bossPhaseLimit(night, at, cfg);
                assertTrue(at + limit <= 9600, "level " + level + " arrival " + at + " limit " + limit);
                assertTrue(limit >= night - at, "the clock never cuts the normal night short");
            }
        }
    }

    @Test
    void theBossClockKeepsToALongerCapToo() {
        SiegeConfig cfg = new SiegeConfig();   // whatever the cap, the Bossuhr ends there
        int night = SiegeMath.durationSeconds(cfg.maxLevel, cfg) * 20;
        int at = BossNight.arrivalTicks(night, cfg);
        assertTrue(at + NightClock.bossPhaseLimit(night, at, cfg) <= NightClock.capTicks(cfg));
    }

    @Test
    void theFinaleFitsItsPairsAndPausesIntoEightMinutes() {
        SiegeConfig cfg = eight();
        int total = NightClock.capTicks(cfg);
        for (int atOnce = 1; atOnce <= 4; atOnce++) {
            cfg.bossGroupAtOnce = atOnce;
            int pair = NightClock.finalePairTicks(4, total, cfg);
            int last = NightClock.finaleLastPairAt(4, total, cfg);
            assertTrue(last + pair <= total, "atOnce " + atOnce + ": last pair at " + last + " + " + pair);
            assertTrue(pair >= 60 * 20, "every pair gets a minute at least (" + pair + ")");
        }
        cfg.bossGroupAtOnce = 2;
        assertEquals(2, NightClock.finalePairs(4, cfg));
        assertEquals(3600, NightClock.finalePairTicks(4, total, cfg));   // 3:00 as before: it fits
        assertEquals(400 + 3600, NightClock.finaleLastPairAt(4, total, cfg));
    }
}
