package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Chef 12.11. 09:50: the lich's crystals at most 24 blocks from the core, on the floor rather than a roof. */
class LichCrystalsTest {

    @Test
    void radiusStaysWithin24() {
        assertEquals(24.0, LichCrystals.radius(61.0), 1e-9);   // burg map: one crystal stood 48 out
        assertEquals(24.0, LichCrystals.radius(27.0), 1e-9);
        assertEquals(17.0, LichCrystals.radius(20.0), 1e-9);
        assertEquals(3.0, LichCrystals.radius(4.0), 1e-9);
        assertEquals(7.0, LichCrystals.radius(0.0), 1e-9);    // open base
    }

    @Test
    void floorBeforeRoof() {
        Set<Integer> standable = Set.of(3, -1);                // a roof three up, the floor one down
        assertEquals(-1, LichCrystals.pick(standable::contains));
        assertEquals(0, LichCrystals.pick(Set.of(0, 3)::contains));
        assertEquals(3, LichCrystals.pick(Set.of(3)::contains));
        assertEquals(LichCrystals.NONE, LichCrystals.pick(dy -> false));
    }
}
