package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Balance update 2 (Simon, 29 October 2026): the low-level life steal and the champions' share. */
class BalanceFightTest {

    private SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        return c;
    }

    @Test
    void lifestealFallsFromTwoAndAHalfToOne() {
        SiegeConfig c = cfg();
        assertEquals(2.5, c.hitLifestealLevelFactor(1), 1e-9);
        assertEquals(1.75, c.hitLifestealLevelFactor(8), 1e-9);   // halfway between level 1 and 15
        assertEquals(1.0, c.hitLifestealLevelFactor(15), 1e-9);
        assertEquals(1.0, c.hitLifestealLevelFactor(40), 1e-9);
        assertEquals(2.5, c.hitLifestealLevelFactor(0), 1e-9);   // below level 1 counts as level 1
        assertEquals(1.0, c.hitLifestealMinHeal, 1e-9);
        assertEquals(10, c.hitLifestealMinTicks);
        assertEquals(0.5, c.hitLifestealCleave, 1e-9);
    }

    @Test
    void championShareIs30InTheDungeonAndInANight() {
        // Simon 30.10. 23:52, 04.11. 23:19: 30 % of the max health per hit, in the dungeon and in a night.
        SiegeConfig c = cfg();
        assertEquals(0.30, c.championPercent(1), 1e-9);
        assertEquals(0.30, c.championPercent(20), 1e-9);
        assertEquals(0.30, c.championPercent(60), 1e-9);
        assertEquals(0.30, c.championPercent(10, false), 1e-9);
        assertEquals(0.30, c.championPercent(10, true), 1e-9);
        assertEquals(0.5, c.championPercentShield, 1e-9);
        // A hand-edited config cannot break it.
        c.championPercentDamage = null;
        c.championPercentDamageSteps = null;
        c.championNightPercent = Double.NaN;
        c.hitLifestealLowFactor = Double.NaN;
        c.sanitize();
        assertEquals(0.30, c.championPercent(5), 1e-9);
        assertEquals(0.30, c.championPercent(5, true), 1e-9);
        assertEquals(2.5, c.hitLifestealLevelFactor(1), 1e-9);
    }

    @Test
    void update18BringsTheChampionShareAndTheCampWithoutGrace() {
        // The server's config of 31.10. (settings 17, champions 4-6 %, camp grace 10 s).
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 17;
        server.championPercentDamage = new double[] {0.04, 0.05, 0.06};
        server.championPercentDamageSteps = new int[] {10, 20};
        server.campGraceSeconds = 10;
        server.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(0.30, server.championPercent(25), 1e-9);
        assertEquals(0.30, server.championPercent(25, true), 1e-9);
        assertEquals(0, server.campGraceSeconds);
    }
}
