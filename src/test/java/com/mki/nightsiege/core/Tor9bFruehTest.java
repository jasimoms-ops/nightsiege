package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tor 9b P4/P5 (Spieltest 09.11.): trapper base tripwire, shield fighter and bulwark life early on. */
class Tor9bFruehTest {

    @Test
    void shieldFighterAndBulwarkHaveMoreLifeEarly() {
        double[] want = {6, 5, 5, 4, 3, 3, 2, 1, 1, 0, 0};   // world level 1..11
        for (String node : new String[] {"riposte", "g_bastion"}) {
            double atOne = SkillValues.get(node, "hpearly", 0);
            double until = SkillValues.get(node, "hpearlyuntil", 0);
            assertEquals(6, atOne, 1e-9, node);
            assertEquals(10, until, 1e-9, node);
            for (int level = 1; level <= 11; level++) {
                assertEquals(want[level - 1], EarlyBonus.health(atOne, until, level), 1e-9, node + " level " + level);
            }
        }
        assertEquals(0, EarlyBonus.health(6, 10, 25), 1e-9);   // late stays capped at 0
        assertEquals(6, EarlyBonus.health(6, 10, 0), 1e-9);    // no level known: like level 1
        assertEquals(0, SkillValues.get("heavy_blow", "hpearly", 0), 1e-9);   // the axe fighter keeps his own rule
    }

    @Test
    void trapperHasABaseTripwireTheSkillReplaces() {
        assertArrayEquals(new int[] {40, 0}, EarlyBonus.tripSlow(true, false, false));   // Slowness I, 2 s, no skill point
        assertArrayEquals(new int[] {60, 1}, EarlyBonus.tripSlow(true, true, false));    // learned: 3 s Slowness II, not added
        assertArrayEquals(new int[] {60, 9}, EarlyBonus.tripSlow(true, true, true));     // trap master holds fast
        assertArrayEquals(new int[] {60, 1}, EarlyBonus.tripSlow(false, true, false));   // bolter with the skill: as before
        assertNull(EarlyBonus.tripSlow(false, false, false));                             // no trapper, no skill: none
    }
}
