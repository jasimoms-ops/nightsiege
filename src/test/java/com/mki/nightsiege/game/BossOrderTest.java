package com.mki.nightsiege.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chef 13.11.: normal boss nights are unchanged; only an ordered boss in a war leader's round is exempt. */
class BossOrderTest {

    @Test
    void withoutAnOrderTheBossAimsAndWalksHomeAsBefore() {
        boolean led = BossService.followsOrder(false, true);   // no war leader's round: no order counts
        assertFalse(led);
        assertFalse(BossService.followsOrder(true, false));     // a round, but no order for its squad
        for (long now = 0; now < 200; now++) {
            assertTrue(BossService.seeksDefender(now, false, false, false) == (now % 20 == 0), "tick " + now);
        }
        assertFalse(BossService.seeksDefender(20, false, true, false));   // has a living target
        assertFalse(BossService.seeksDefender(20, false, false, true));   // the lich keeps its own aim
        assertTrue(BossService.walksHome(65.0 * 65.0, false, led));
        assertFalse(BossService.walksHome(63.0 * 63.0, false, led));
        assertFalse(BossService.walksHome(65.0 * 65.0, true, led));        // a defender is near it
    }

    @Test
    void anOrderedBossInAWarRoundFollowsTheOrder() {
        boolean led = BossService.followsOrder(true, true);
        assertTrue(led);
        assertFalse(BossService.seeksDefender(20, led, false, false));
        assertFalse(BossService.walksHome(100.0 * 100.0, false, led));
    }
}
