package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 9b P3: the caster curb on players. */
class Tor9bCasterCurbTest {

    @Test
    void casterBlindnessAndSlownessCut() {
        // Illusioner blindness 20 s -> 2 s; iceologer slowness III 5 s -> I 3 s.
        assertArrayEquals(new int[]{40, 0}, CasterCurb.cap(CasterCurb.Kind.BLIND, 400, 0, 1000, -1));
        assertArrayEquals(new int[]{60, 0}, CasterCurb.cap(CasterCurb.Kind.SLOW, 100, 2, 1000, -1));
        // Short and weak ones stay; endless ones get the cap.
        assertArrayEquals(new int[]{20, 0}, CasterCurb.cap(CasterCurb.Kind.SLOW, 20, 0, 1000, -1));
        assertArrayEquals(new int[]{40, 0}, CasterCurb.cap(CasterCurb.Kind.BLIND, -1, 0, 1000, -1));
    }

    @Test
    void sameEffectOnlyAfterSixSeconds() {
        assertNull(CasterCurb.cap(CasterCurb.Kind.BLIND, 400, 0, 1000 + 119, 1000));
        assertArrayEquals(new int[]{40, 0}, CasterCurb.cap(CasterCurb.Kind.BLIND, 400, 0, 1000 + 120, 1000));
        assertEquals(120, CasterCurb.LOCK_TICKS);
    }

    @Test
    void castersKnownById() {
        assertTrue(CasterCurb.casterId("minecraft:evoker"));
        assertTrue(CasterCurb.casterId("friendsandfoes:iceologer"));
        assertTrue(CasterCurb.casterId("friendsandfoes:illusioner"));
        assertFalse(CasterCurb.casterId("minecraft:pillager"));
        assertFalse(CasterCurb.casterId(null));
    }
}
