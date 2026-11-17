package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 F (Chef 17.11.): the core's life instead of the kill goal. */
class CoreLifeTest {

    @Test
    void hundredLifeByDefault() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals(100, cfg.coreHealth);
        assertEquals(3.0, cfg.coreHitRange, 1e-9);
        assertTrue(CoreLife.on(cfg));
        cfg.coreHealth = 0;
        assertFalse(CoreLife.on(cfg));
    }

    @Test
    void eachKindOfMonsterHitsAsSet() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals(1, CoreLife.damage(CoreLife.rank(false, false, false, false), cfg));    // normal
        assertEquals(3, CoreLife.damage(CoreLife.rank(false, true, false, false), cfg));     // elite
        assertEquals(3, CoreLife.damage(CoreLife.rank(false, false, true, false), cfg));     // champion
        assertEquals(3, CoreLife.damage(CoreLife.rank(false, false, false, true), cfg));     // nemesis
        assertEquals(10, CoreLife.damage(CoreLife.rank(true, true, false, false), cfg));     // boss, even an elite one
    }

    @Test
    void lifeNeverBelowZeroAndNoHealing() {
        assertEquals(97, CoreLife.afterHit(100, 3));
        assertEquals(0, CoreLife.afterHit(5, 10));
        assertEquals(50, CoreLife.afterHit(50, -4));   // a negative hit does not heal
    }

    @Test
    void reachIsThreeBlocks() {
        assertTrue(CoreLife.inReach(2.0, 0.0, 2.0, 3.0));
        assertFalse(CoreLife.inReach(2.5, 0.0, 2.5, 3.0));
        assertFalse(CoreLife.inReach(0.0, 4.0, 0.0, 3.0));   // on a wall high above
    }

    @Test
    void warningsAtHalfAndAQuarter() {
        assertEquals(50, CoreLife.warning(51, 50, 100));
        assertEquals(0, CoreLife.warning(50, 49, 100));
        assertEquals(25, CoreLife.warning(27, 24, 100));
        assertEquals(25, CoreLife.warning(60, 20, 100));   // both at once: the worse one
        assertEquals(0, CoreLife.warning(80, 70, 100));
    }

    @Test
    void winAndLossRules() {
        // Lost: the core at 0, or every defender fallen.
        assertEquals(CoreLife.End.LOST, CoreLife.outcome(true, 0, 3, 0, false, false));
        assertEquals(CoreLife.End.LOST, CoreLife.outcome(true, 40, 0, 2, false, false));
        // The core off (scenes): its 0 does not end the night.
        assertEquals(CoreLife.End.GOES_ON, CoreLife.outcome(false, 0, 1, 0, false, false));
        // Won at dawn with the core alive, whatever the kills; a boss night only with the boss down.
        assertEquals(CoreLife.End.WON, CoreLife.outcome(true, 1, 1, 3, true, false));
        assertEquals(CoreLife.End.LOST, CoreLife.outcome(true, 80, 2, 0, true, true));
        assertEquals(CoreLife.End.GOES_ON, CoreLife.outcome(true, 80, 2, 0, false, false));
    }
}
