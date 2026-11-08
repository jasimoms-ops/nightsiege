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

}
