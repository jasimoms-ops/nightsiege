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
    void championShareGoesByLevelBands() {
        SiegeConfig c = cfg();
        assertEquals(0.04, c.championPercent(1), 1e-9);
        assertEquals(0.04, c.championPercent(9), 1e-9);
        assertEquals(0.05, c.championPercent(10), 1e-9);
        assertEquals(0.05, c.championPercent(19), 1e-9);
        assertEquals(0.06, c.championPercent(20), 1e-9);
        assertEquals(0.06, c.championPercent(60), 1e-9);
        assertEquals(0.5, c.championPercentShield, 1e-9);
        // A hand-edited config cannot break it.
        c.championPercentDamage = null;
        c.championPercentDamageSteps = null;
        c.hitLifestealLowFactor = Double.NaN;
        c.sanitize();
        assertEquals(0.04, c.championPercent(5), 1e-9);
        assertEquals(2.5, c.hitLifestealLevelFactor(1), 1e-9);
    }
}
