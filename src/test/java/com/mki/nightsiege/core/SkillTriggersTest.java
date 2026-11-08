package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 2.2: skills that never fired (production/qa/tor-2/skillfehler.md). */
class SkillTriggersTest {

    @Test
    void earthshockFiresOnAnOrdinaryJumpHit() {
        // A jump lifts the player about 1.25 blocks; the hit lands somewhere on the way down.
        assertTrue(SkillTriggers.earthshock(0.3, false));
        assertTrue(SkillTriggers.earthshock(1.0, false));
        assertTrue(SkillTriggers.earthshock(3.0, false));
        // Standing on the ground is no hit from the fall.
        assertFalse(SkillTriggers.earthshock(0.0, true));
        assertFalse(SkillTriggers.earthshock(0.0, false));
    }

    @Test
    void quiverExtraArrowWithTheCrossbowToo() {
        assertTrue(SkillTriggers.quiverTwin(true, false, true));
        assertTrue(SkillTriggers.quiverTwin(false, true, true));   // the crossbow way has the quiver as well
        assertFalse(SkillTriggers.quiverTwin(false, true, false)); // a real arrow comes back instead
        assertFalse(SkillTriggers.quiverTwin(false, false, true));
    }
}
