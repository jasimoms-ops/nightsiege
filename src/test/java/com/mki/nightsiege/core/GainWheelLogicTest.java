package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 3.2: the gain wheel on one key (hold: choose, tap: the last gain) and its cooldown rings. */
class GainWheelLogicTest {

    @Test
    void tapOrHold() {
        assertTrue(GainWheelLogic.isTap(0));
        assertTrue(GainWheelLogic.isTap(GainWheelLogic.HOLD_TICKS - 1));
        assertFalse(GainWheelLogic.isTap(GainWheelLogic.HOLD_TICKS));
    }

    @Test
    void mousePicksThePlace() {
        // three places: 0 up, 1 lower right, 2 lower left
        assertEquals(0, GainWheelLogic.sector(0, -40, 3));
        assertEquals(1, GainWheelLogic.sector(35, 20, 3));
        assertEquals(2, GainWheelLogic.sector(-35, 20, 3));
        // two places: up and down
        assertEquals(0, GainWheelLogic.sector(5, -40, 2));
        assertEquals(1, GainWheelLogic.sector(-5, 40, 2));
        // one place: anywhere outside the middle
        assertEquals(0, GainWheelLogic.sector(0, 30, 1));
    }

    @Test
    void middleAndEmptyPickNothing() {
        assertEquals(-1, GainWheelLogic.sector(3, 4, 3));
        assertEquals(-1, GainWheelLogic.sector(0, -40, 0));
    }

    @Test
    void mouseWheelGoesRound() {
        assertEquals(1, GainWheelLogic.scroll(0, 1, 3));
        assertEquals(2, GainWheelLogic.scroll(0, -1, 3));
        assertEquals(0, GainWheelLogic.scroll(2, 1, 3));
        assertEquals(1, GainWheelLogic.scroll(-1, 1, 3));
        assertEquals(-1, GainWheelLogic.scroll(0, 1, 0));
    }

    @Test
    void tapUsesTheLastGain() {
        assertEquals(2, GainWheelLogic.tapIndex(2, 3));
        assertEquals(0, GainWheelLogic.tapIndex(2, 2));   // the third place is gone (no "Third Hand" now)
        assertEquals(0, GainWheelLogic.tapIndex(-1, 2));
        assertEquals(-1, GainWheelLogic.tapIndex(0, 0));
    }

    @Test
    void cooldownLeftSecondsAndRing() {
        assertEquals(400, GainWheelLogic.leftTicks(600, 200));
        assertEquals(0, GainWheelLogic.leftTicks(600, 700));
        assertEquals(20, GainWheelLogic.seconds(400));
        assertEquals(1, GainWheelLogic.seconds(1));
        assertEquals(0, GainWheelLogic.seconds(0));
        assertEquals(1.0, GainWheelLogic.readyShare(0, 1200), 1e-9);
        assertEquals(0.25, GainWheelLogic.readyShare(900, 1200), 1e-9);
        assertEquals(0.0, GainWheelLogic.readyShare(1200, 1200), 1e-9);
        assertEquals(6, GainWheelLogic.litSegments(0.25, 24));
        assertEquals(24, GainWheelLogic.litSegments(1.0, 24));
        assertEquals(0, GainWheelLogic.litSegments(0.0, 24));
    }
}
