package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 3.3 (Simon 08.11.: "Ring + Sekunden"): the pause ring around an ability box. */
class CooldownRingTest {

    private static Set<Long> pixels(List<int[]> runs) {
        Set<Long> out = new HashSet<>();
        for (int[] r : runs) {
            for (int x = r[0]; x < r[2]; x++) {
                for (int y = r[1]; y < r[3]; y++) {
                    assertTrue(out.add(((long) x << 32) | (y & 0xFFFFFFFFL)), "no pixel twice");
                }
            }
        }
        return out;
    }

    @Test
    void fullRingIsTheWholeEdgeOnly() {
        for (int size : new int[]{10, 12, 20, 22}) {
            List<int[]> runs = CooldownRing.runs(5, 7, size, 1.0);
            Set<Long> px = pixels(runs);
            assertEquals(4 * size - 4, px.size(), "size " + size);
            assertTrue(runs.size() <= 5, "joined into sides: " + runs.size());
            for (long p : px) {
                int x = (int) (p >> 32) - 5;
                int y = (int) p - 7;
                assertTrue(x == 0 || y == 0 || x == size - 1 || y == size - 1, "on the edge");
            }
        }
    }

    @Test
    void ringStartsTopMiddleAndRunsClockwise() {
        assertArrayEquals(new int[]{6, 0}, CooldownRing.cell(12, 0));
        assertArrayEquals(new int[]{7, 0}, CooldownRing.cell(12, 1));
        assertArrayEquals(new int[]{11, 0}, CooldownRing.cell(12, 5));   // top right corner
        assertArrayEquals(new int[]{11, 1}, CooldownRing.cell(12, 6));   // then down the right side
        assertArrayEquals(new int[]{5, 0}, CooldownRing.cell(12, 43));   // the last one closes the ring
    }

    @Test
    void ringShrinksWithThePause() {
        assertEquals(0, CooldownRing.lit(20, 0.0));
        assertTrue(CooldownRing.runs(0, 0, 20, 0.0).isEmpty());
        assertEquals(1, CooldownRing.lit(20, 0.0001));   // a pause running always shows
        assertEquals(38, CooldownRing.lit(20, 0.5));
        assertEquals(76, CooldownRing.lit(20, 1.0));
        assertEquals(76, CooldownRing.lit(20, 3.0));      // never beyond the ring
        assertEquals(19, pixels(CooldownRing.runs(0, 0, 20, 0.25)).size());
        int last = Integer.MAX_VALUE;
        for (int left = 160; left >= 0; left -= 20) {
            int lit = CooldownRing.lit(20, HudSync.shade(left, 160));
            assertTrue(lit <= last, "never grows");
            last = lit;
        }
        assertEquals(0, last);
    }
}
