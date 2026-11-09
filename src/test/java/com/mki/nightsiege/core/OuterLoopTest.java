package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 6.6: the outer loop (Formel C), its cap, the restart and the "decided early" measure. */
class OuterLoopTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
    }

    @Test
    void winAndLossSteps() {
        SiegeConfig c = cfg();
        assertEquals(0.02, OuterLoop.next(0.0, true, c), 1e-9);
        assertEquals(-0.047, OuterLoop.next(0.0, false, c), 1e-9);
    }

    @Test
    void cappedAtFifteen() {
        SiegeConfig c = cfg();
        double o = 0;
        for (int i = 0; i < 50; i++) {
            o = OuterLoop.next(o, true, c);
        }
        assertEquals(0.15, o, 1e-9);
        for (int i = 0; i < 50; i++) {
            o = OuterLoop.next(o, false, c);
        }
        assertEquals(-0.15, o, 1e-9);
        assertEquals(0.15, OuterLoop.clamp(0.9, c), 1e-9);
        assertEquals(0.0, OuterLoop.clamp(Double.NaN, c), 1e-9);
    }

    @Test
    void settlesNearSeventyPercent() {
        // 7 wins and 3 losses: 7 x 0.02 - 3 x 0.047 = -0.001, about level.
        SiegeConfig c = cfg();
        double o = 0;
        for (int i = 0; i < 10; i++) {
            o = OuterLoop.next(o, i % 10 < 7, c);
        }
        assertEquals(0.0, o, 0.002);
    }

    @Test
    void keptOverRestart() throws Exception {
        // The offset lives in the save (SiegeSave.outerLoop, a plain double field): written and read back as JSON.
        SiegeConfig c = cfg();
        double o = OuterLoop.next(OuterLoop.next(0, false, c), true, c);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Holder h = new Holder();
        h.outerLoop = o;
        Holder back = gson.fromJson(gson.toJson(h), Holder.class);
        assertEquals(o, back.outerLoop, 1e-12);
        assertEquals(1.0 + o, OuterLoop.factor(back.outerLoop, c), 1e-12);
        c.smartNightOuterLoop = false;
        assertEquals(1.0, OuterLoop.factor(back.outerLoop, c), 1e-12);
    }

    static final class Holder {
        double outerLoop;
    }

    @Test
    void decidedEarly() {
        assertTrue(OuterLoop.decidedEarly(false, 0.2, 0.8));    // lost in the first third
        assertFalse(OuterLoop.decidedEarly(false, 0.5, 0.8));   // lost late, tight
        assertTrue(OuterLoop.decidedEarly(true, 1.0, 0.15));    // never above 0.2
        assertFalse(OuterLoop.decidedEarly(true, 1.0, 0.4));
        assertFalse(OuterLoop.decidedEarly(true, 1.0, -1));     // no look taken
    }

    @Test
    void migrationSetsTargetsKeepsOwn() {
        SiegeConfig old = new SiegeConfig();
        old.settingsVersion = 24;
        old.smartNightFewerPerStep = 0.05;
        old.smartNightDownStress = 0.45;
        old.sanitize();
        assertEquals(0.03, old.smartNightFewerPerStep, 1e-9);
        assertEquals(0.50, old.smartNightDownStress, 1e-9);
        assertEquals(SiegeConfig.SETTINGS_VERSION, old.settingsVersion);
        SiegeConfig own = new SiegeConfig();
        own.settingsVersion = 24;
        own.smartNightFewerPerStep = 0.08;
        own.sanitize();
        assertEquals(0.08, own.smartNightFewerPerStep, 1e-9);
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(0.03, fresh.smartNightFewerPerStep, 1e-9);
    }
}
