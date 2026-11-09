package com.mki.nightsiege.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KillingHitTest {

    @Test
    void killingHitCountsTheLifeBefore() {
        // Golem punch: scaled 37 against a zombie with 16 life -> 16 really went off.
        assertEquals(16f, KillingHit.lost(false, 16f, 0f, -21f, 0f), 1e-6);
    }

    @Test
    void absorptionCountsToo() {
        assertEquals(9f, KillingHit.lost(false, 5f, 4f, 0f, 0f), 1e-6);
    }

    @Test
    void reportedHitIsNotCountedTwice() {
        assertEquals(0f, KillingHit.lost(true, 16f, 0f, 0f, 0f), 1e-6);
    }

    @Test
    void nothingLostIsZero() {
        assertEquals(0f, KillingHit.lost(false, 0f, 0f, 0f, 0f), 1e-6);
    }
}
