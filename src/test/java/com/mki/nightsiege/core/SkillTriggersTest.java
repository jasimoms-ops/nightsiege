package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 2.2: skills that never fired (production/qa/tor-2/skillfehler.md). */
class SkillTriggersTest {

    @Test
    void earthshockFiresOnAnOrdinaryJumpHit() {
        // A jump lifts the player about 1.25 blocks; the hit lands somewhere on the way down.
        assertTrue(SkillTriggers.earthshock(0.3, false, 1f, false, false, false, false));
        assertTrue(SkillTriggers.earthshock(1.0, false, 1f, false, false, false, false));
        assertTrue(SkillTriggers.earthshock(3.0, false, 1f, false, false, false, false));
        // Standing on the ground is no hit from the fall.
        assertFalse(SkillTriggers.earthshock(0.0, true, 1f, false, false, false, false));
        assertFalse(SkillTriggers.earthshock(0.0, false, 1f, false, false, false, false));
    }

    @Test
    void earthshockOnlyOnAVanillaJumpCrit() {
        assertTrue(SkillTriggers.earthshock(0.5, false, 0.95f, false, false, false, false));
        assertFalse(SkillTriggers.earthshock(0.5, false, 0.5f, false, false, false, false));   // spam click
        assertFalse(SkillTriggers.earthshock(0.5, false, 1f, true, false, false, false));      // ladder, vine
        assertFalse(SkillTriggers.earthshock(0.5, false, 1f, false, true, false, false));      // water
        assertFalse(SkillTriggers.earthshock(0.5, false, 1f, false, false, true, false));      // riding
        assertFalse(SkillTriggers.earthshock(0.5, false, 1f, false, false, false, true));      // blind
    }

    @Test
    void quiverExtraArrowWithTheCrossbowToo() {
        assertTrue(SkillTriggers.quiverTwin(true, false, true, true));
        assertTrue(SkillTriggers.quiverTwin(false, true, true, true));   // the crossbow way has the quiver as well
        assertFalse(SkillTriggers.quiverTwin(false, true, false, true)); // a real arrow comes back instead
        assertFalse(SkillTriggers.quiverTwin(false, false, true, true));
    }

    @Test
    void quiverOnlyForTheMainArrowOfAMultishot() {
        // Multishot: three arrows in one tick, all of Infinity; only the first (middle) one may get a twin.
        var gate = new SkillTriggers.FirstOfTick();
        java.util.UUID p = java.util.UUID.randomUUID();
        int twins = 0;
        for (int i = 0; i < 3; i++) {
            if (SkillTriggers.quiverTwin(false, true, true, gate.first(p, 100L))) {
                twins++;
            }
        }
        assertEquals(1, twins);
        assertTrue(SkillTriggers.quiverTwin(false, true, true, gate.first(p, 125L)));   // the next shot again
    }
}
