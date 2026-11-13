package com.mki.nightsiege.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruefer 13.11.: numbers in the war leader's client commands are finite or NaN (= none), never Infinity. */
class WarLeaderInputTest {

    @Test
    void clientNumbersAreFiniteOrNone() {
        assertEquals(12.5, WarLeader.parseD(" 12.5 "), 1e-9);
        assertEquals(-3.0, WarLeader.parseD("-3"), 1e-9);
        for (String bad : new String[] {"NaN", "Infinity", "-Infinity", "1e999", "x", ""}) {
            assertTrue(Double.isNaN(WarLeader.parseD(bad)), bad);
        }
    }

    /** Pruefer 13.11.: at most 24 monsters per squad go to the client, spread over the squad (first and last kept). */
    @Test
    void bigSquadsSendAtMostTwentyFourDots() {
        java.util.List<Integer> all = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            all.add(i);
        }
        java.util.List<Integer> sent = WarLeader.spread(all, WarLeader.SYNC_UNITS);
        assertEquals(24, sent.size());
        assertEquals(0, sent.get(0));
        assertEquals(199, sent.get(23));
        assertEquals(24, new java.util.HashSet<>(sent).size());
        assertEquals(all.subList(0, 10), WarLeader.spread(all.subList(0, 10), WarLeader.SYNC_UNITS));
        assertEquals(24, WarLeader.spread(all.subList(0, 25), WarLeader.SYNC_UNITS).size());
    }
}
