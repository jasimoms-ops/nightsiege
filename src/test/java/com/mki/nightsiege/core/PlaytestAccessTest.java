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
    void nonOpTargetLevelUpTo25() {
        assertNull(PlaytestAccess.levelProblem(false, 1));
        assertNull(PlaytestAccess.levelProblem(false, 25));
        assertEquals("nightsiege.playtest.level_limit", PlaytestAccess.levelProblem(false, 26));
        assertEquals("nightsiege.playtest.level_limit", PlaytestAccess.levelProblem(false, 0));
        assertNull(PlaytestAccess.levelProblem(true, 1000));
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

    @Test
    void nonOpOnlyWithThirtyMinutesBeforeTheNight() {
        long min = PlaytestAccess.MIN_TICKS_BEFORE_NIGHT;
        assertEquals(20L * 60 * 30, min);
        assertEquals("nightsiege.playtest.night_soon", PlaytestAccess.nightProblem(false, min - 1));
        assertEquals("nightsiege.playtest.night_soon", PlaytestAccess.nightProblem(false, 0));
        assertNull(PlaytestAccess.nightProblem(false, min));
        assertNull(PlaytestAccess.nightProblem(false, -1));   // no siege day planned
        assertNull(PlaytestAccess.nightProblem(true, 0));     // OP as before
    }

    @Test
    void idleWatchWarnsAfterNineAndEndsAfterTenMinutes() {
        long limit = PlaytestAccess.IDLE_LIMIT_TICKS;
        long warn = limit - PlaytestAccess.IDLE_WARN_BEFORE_TICKS;
        assertEquals(20L * 60 * 10, limit);
        assertEquals(PlaytestAccess.Idle.NONE, PlaytestAccess.idleStep(true, warn - 1, false));
        assertEquals(PlaytestAccess.Idle.WARN, PlaytestAccess.idleStep(true, warn, false));
        assertEquals(PlaytestAccess.Idle.NONE, PlaytestAccess.idleStep(true, warn + 20, true));   // once
        assertEquals(PlaytestAccess.Idle.END, PlaytestAccess.idleStep(true, limit, true));
        assertEquals(PlaytestAccess.Idle.END, PlaytestAccess.idleStep(true, limit, false));
        assertEquals(PlaytestAccess.Idle.NONE, PlaytestAccess.idleStep(false, limit * 10, false));   // OP start
    }
}
