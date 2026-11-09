package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 9.1 j: who may start and stop a playtest. */
class PlaytestAccessTest {

    private static final long CD = PlaytestAccess.START_COOLDOWN_TICKS;

    @Test
    void nonOpMayStartAloneWhenNothingRuns() {
        assertNull(PlaytestAccess.startProblem(false, true, false, false, 100, -1));
    }

    @Test
    void nobodyStartsDuringANightOrASecondPlaytest() {
        for (boolean op : new boolean[]{false, true}) {
            assertEquals("nightsiege.cmd.already_active", PlaytestAccess.startProblem(op, true, false, true, 100, -1));
            assertEquals("nightsiege.playtest.already", PlaytestAccess.startProblem(op, true, true, false, 100, -1));
            assertEquals("nightsiege.playtest.already", PlaytestAccess.startProblem(op, true, true, true, 100, -1));
        }
    }

    @Test
    void cooldownHoldsForNonOpsOnly() {
        long start = 1000;
        assertEquals("nightsiege.playtest.cooldown", PlaytestAccess.startProblem(false, true, false, false, start + 1, start));
        assertEquals("nightsiege.playtest.cooldown", PlaytestAccess.startProblem(false, true, false, false, start + CD - 1, start));
        assertNull(PlaytestAccess.startProblem(false, true, false, false, start + CD, start));
        assertNull(PlaytestAccess.startProblem(true, true, false, false, start + 1, start));
        assertEquals(600, PlaytestAccess.cooldownSecondsLeft(start, start));
        assertEquals(1, PlaytestAccess.cooldownSecondsLeft(start + CD - 1, start));
        assertEquals(0, PlaytestAccess.cooldownSecondsLeft(start + CD, start));
        assertEquals(0, PlaytestAccess.cooldownSecondsLeft(5, -1));
        assertEquals(0, PlaytestAccess.cooldownSecondsLeft(5, 50));   // the tick count began again (restart)
    }

    @Test
    void consoleCountsAsOp() {
        assertNull(PlaytestAccess.startProblem(true, false, false, false, 1, 0));
        assertEquals("nightsiege.cmd.player_only", PlaytestAccess.startProblem(false, false, false, false, 1, -1));
    }

    @Test
    void stopOnlyForStarterParticipantOrOp() {
        assertTrue(PlaytestAccess.mayStop(true, false, false));
        assertTrue(PlaytestAccess.mayStop(false, true, false));
        assertTrue(PlaytestAccess.mayStop(false, false, true));
        assertFalse(PlaytestAccess.mayStop(false, false, false));
    }

    @Test
    void joinHoldsFiveMinutes() {
        assertTrue(PlaytestAccess.joinHolds(100, 100));
        assertTrue(PlaytestAccess.joinHolds(100 + PlaytestAccess.JOIN_WINDOW_TICKS, 100));
        assertFalse(PlaytestAccess.joinHolds(101 + PlaytestAccess.JOIN_WINDOW_TICKS, 100));
        assertFalse(PlaytestAccess.joinHolds(50, 100));
        assertFalse(PlaytestAccess.joinHolds(100, -1));
        assertEquals(10, PlaytestAccess.MAX_PLAYERS);
    }
}
