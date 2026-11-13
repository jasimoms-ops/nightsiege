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
}
