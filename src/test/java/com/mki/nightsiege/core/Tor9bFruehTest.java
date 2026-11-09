package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tor 9b P4/P5 (Spieltest 09.11.): trapper base tripwire, shield fighter and bulwark life early on. */
class Tor9bFruehTest {

    @Test
    void trapperHasABaseTripwireTheSkillReplaces() {
        assertArrayEquals(new int[] {40, 0}, EarlyBonus.tripSlow(true, false, false));   // Slowness I, 2 s, no skill point
        assertArrayEquals(new int[] {60, 1}, EarlyBonus.tripSlow(true, true, false));    // learned: 3 s Slowness II, not added
        assertArrayEquals(new int[] {60, 9}, EarlyBonus.tripSlow(true, true, true));     // trap master holds fast
        assertArrayEquals(new int[] {60, 1}, EarlyBonus.tripSlow(false, true, false));   // bolter with the skill: as before
        assertNull(EarlyBonus.tripSlow(false, false, false));                             // no trapper, no skill: none
    }
}
