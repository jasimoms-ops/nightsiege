package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tor 10 (Chef 13.11. 03:20): a gate's state for the war leader's cross, as gates have no life. */
class WarGateStateTest {

    @Test
    void archIsAlwaysOpen() {
        assertEquals("open", WarLeaderData.gateState(true, false, false));
        assertEquals("open", WarLeaderData.gateState(true, true, false));
    }

    @Test
    void doorStates() {
        assertEquals("closed", WarLeaderData.gateState(false, true, false));
        assertEquals("open", WarLeaderData.gateState(false, true, true));
        assertEquals("broken", WarLeaderData.gateState(false, false, false));
    }

    @Test
    void olderServerSendsNoState() {
        assertEquals("", new WarLeaderData.Target().state);
    }
}
