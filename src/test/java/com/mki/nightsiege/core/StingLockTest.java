package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prüfer-Fund 4b: seven Statikfelder (Netzwerk) of one player sting a monster once per pulse, not seven times. */
class StingLockTest {

    @Test
    void sevenFieldsOnePulseOneSting() {
        StingLock lock = new StingLock();
        UUID player = UUID.randomUUID();
        int hits = 0;
        for (int field = 0; field < 7; field++) {
            if (lock.tryHit(player, 42, 100, 20)) {
                hits++;
            }
        }
        assertEquals(1, hits);
    }

    @Test
    void nextPulseStingsAgainAndOffsetCastDoesNotStack() {
        StingLock lock = new StingLock();
        UUID player = UUID.randomUUID();
        assertTrue(lock.tryHit(player, 7, 100, 20));
        assertFalse(lock.tryHit(player, 7, 110, 20));   // a second cast pulsing in between
        assertTrue(lock.tryHit(player, 7, 120, 20));   // the next pulse
        int perSecond = 0;
        for (long t = 200; t < 400; t++) {   // 10 seconds, two casts pulsing at offsets 0 and 7
            if ((t % 20 == 0 || t % 20 == 7) && lock.tryHit(player, 7, t, 20)) {
                perSecond++;
            }
        }
        assertEquals(10, perSecond);
    }

    @Test
    void otherMonstersAndOtherPlayersApart() {
        StingLock lock = new StingLock();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(lock.tryHit(a, 1, 100, 20));
        assertTrue(lock.tryHit(a, 2, 100, 20));
        assertTrue(lock.tryHit(b, 1, 100, 20));
        lock.clear();
        assertTrue(lock.tryHit(a, 1, 101, 20));
    }
}
