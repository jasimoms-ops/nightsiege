package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 9.1 (i): the build-up before a playtest night – all ready, the 2 min, breaking off, leaving. */
class SetupPhaseTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final long T0 = 1000L;

    private static long sec(int s) {
        return T0 + s * 20L;
    }

    @Test
    void allReadyStartsTenSecondCountdown() {
        SetupPhase p = new SetupPhase(T0);
        List<UUID> both = List.of(A, B);
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(1), both));
        assertTrue(p.toggle(A));
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(2), both), "one of two ready: still waiting");
        assertTrue(p.toggle(B));
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(30), both));
        assertEquals(10, p.secondsLeft(sec(30)));
        assertEquals(SetupPhase.Step.COUNTING, p.tick(sec(39), both));
        assertEquals(SetupPhase.Step.GO, p.tick(sec(40), both));
        assertEquals(40, p.seconds(sec(40)), "the build-up lasted 40 s");
        assertFalse(p.forced());
    }

    @Test
    void nightComesAfterTwoMinutesAtTheLatest() {
        SetupPhase p = new SetupPhase(T0);
        List<UUID> both = List.of(A, B);
        p.toggle(A);
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(109), both));
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(110), both), "the last 10 s count down");
        assertTrue(p.forced());
        p.toggle(A);   // not ready any more: the 2 min stay
        assertEquals(SetupPhase.Step.COUNTING, p.tick(sec(115), both));
        assertEquals(SetupPhase.Step.GO, p.tick(sec(120), both));
        assertEquals(SetupPhase.MAX_SECONDS, p.seconds(sec(120)));
    }

    @Test
    void notReadyAgainBreaksTheCountdownOff() {
        SetupPhase p = new SetupPhase(T0);
        List<UUID> both = List.of(A, B);
        p.toggle(A);
        p.toggle(B);
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(20), both));
        assertFalse(p.toggle(B), "B presses again: not ready");
        assertEquals(SetupPhase.Step.COUNTDOWN_ABORTED, p.tick(sec(25), both));
        assertFalse(p.counting());
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(40), both));
        p.toggle(B);
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(50), both), "ready again: a new countdown");
        assertEquals(SetupPhase.Step.GO, p.tick(sec(60), both));
    }

    @Test
    void newcomerNotReadyBreaksTheCountdownOff() {
        SetupPhase p = new SetupPhase(T0);
        p.toggle(A);
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(5), List.of(A)));
        assertEquals(SetupPhase.Step.COUNTDOWN_ABORTED, p.tick(sec(8), List.of(A, B)));
    }

    @Test
    void playerWhoGoesDoesNotHoldTheOthersUp() {
        SetupPhase p = new SetupPhase(T0);
        p.toggle(A);
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(10), List.of(A, B)));
        // B logs out (no longer online): A alone is everyone.
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(11), List.of(A)));
        assertEquals(SetupPhase.Step.GO, p.tick(sec(21), List.of(A)));
    }

    @Test
    void leavingDropsTheReadyMark() {
        SetupPhase p = new SetupPhase(T0);
        p.toggle(A);
        p.leave(A);
        assertFalse(p.isReady(A));
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(10), List.of(A)));
        assertEquals(SetupPhase.Step.WAIT, p.tick(sec(10), List.of()), "nobody online: nothing starts early");
    }

    @Test
    void breakingOffInTheLastTenSecondsKeepsTheTwoMinutes() {
        SetupPhase p = new SetupPhase(T0);
        p.toggle(A);
        assertEquals(SetupPhase.Step.COUNTDOWN_STARTED, p.tick(sec(105), List.of(A)));
        p.toggle(A);
        assertEquals(SetupPhase.Step.COUNTING, p.tick(sec(112), List.of(A)));
        assertTrue(p.forced());
        assertEquals(8, p.secondsLeft(sec(112)));
        assertEquals(SetupPhase.Step.GO, p.tick(sec(120), List.of(A)));
    }
}
