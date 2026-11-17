package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tor 12 F (Simon 17.11. 15:28): no kill goal; won = over with a defender standing, lost = all fallen. */
class NightEndTest {

    @Test
    void wonWhenTheNightIsOverAndSomeoneStands() {
        assertEquals(NightEnd.End.WON, NightEnd.outcome(1, 3, true, false));
        assertEquals(NightEnd.End.GOES_ON, NightEnd.outcome(2, 0, false, false));
    }

    @Test
    void lostWhenAllFellOrTheBossStandsAtDawn() {
        assertEquals(NightEnd.End.LOST, NightEnd.outcome(0, 2, false, false));
        assertEquals(NightEnd.End.LOST, NightEnd.outcome(0, 2, true, false));
        assertEquals(NightEnd.End.LOST, NightEnd.outcome(3, 0, true, true));
    }

    @Test
    void killsNeverMakeAHeldNightNotCount() {
        assertEquals("gewonnen", NightEnd.result(true));   // no "ziel_verfehlt" any more
        assertEquals("verloren", NightEnd.result(false));
    }
}
