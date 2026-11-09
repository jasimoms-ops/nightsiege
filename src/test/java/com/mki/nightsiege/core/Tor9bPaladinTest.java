package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 9b (P1): the paladin's golden hearts from jump hits (core/JumpWard). */
class Tor9bPaladinTest {

    private static final UUID P = UUID.randomUUID();

    @Test
    void valuesAsDecided() {
        assertEquals(2.0, JumpWard.perTarget(), 1e-6);   // one golden heart per monster
        assertEquals(8.0, JumpWard.cap(), 1e-6);         // four hearts at most
        assertEquals(200, JumpWard.time());              // 10 s
    }

    @Test
    void everyMonsterStruckGivesOneHeartUpToTheCap() {
        JumpWard w = new JumpWard();
        assertEquals(2f, w.onJumpHit(P, 1, 0f, 0), 1e-6);
        assertEquals(6f, w.onJumpHit(P, 3, 2f, 10), 1e-6);   // three monsters in the Erdstoß splash
        assertEquals(0f, w.onJumpHit(P, 5, 8f, 20), 1e-6);   // at the cap: nothing more, however many jumps
        assertEquals(8f, w.own(P), 1e-6);
        // more monsters than the cap allows: only up to it
        JumpWard v = new JumpWard();
        assertEquals(8f, v.onJumpHit(P, 6, 0f, 0), 1e-6);
    }

    @Test
    void theCapCountsOnlyThisSource() {
        JumpWard w = new JumpWard();
        // 8 absorption of another source (iron hide, golden apple) do not block the jump hearts
        assertEquals(2f, w.onJumpHit(P, 1, 8f, 0), 1e-6);
        assertEquals(6f, w.onJumpHit(P, 4, 10f, 5), 1e-6);
        assertEquals(8f, w.own(P), 1e-6);
    }

    @Test
    void goneTenSecondsAfterTheLastJumpHit() {
        JumpWard w = new JumpWard();
        w.onJumpHit(P, 1, 0f, 0);
        w.onJumpHit(P, 1, 2f, 100);                     // a new hit renews the time
        assertEquals(0f, w.tick(P, 4f, 299), 1e-6);    // still 9.95 s after the last
        assertEquals(4f, w.tick(P, 4f, 300), 1e-6);    // now its share goes
        assertEquals(0f, w.own(P), 1e-6);
        assertEquals(0f, w.tick(P, 4f, 400), 1e-6);    // and only once
    }

    @Test
    void otherAbsorptionStaysAndDamageEatsTheJumpHeartsFirst() {
        JumpWard w = new JumpWard();
        w.onJumpHit(P, 4, 0f, 0);                      // 8 of its own
        assertEquals(0f, w.tick(P, 16f, 10), 1e-6);    // another source adds 8 (16 now)
        assertEquals(0f, w.tick(P, 13f, 20), 1e-6);    // a hit takes 3: from the jump hearts
        assertEquals(5f, w.own(P), 1e-6);
        assertEquals(5f, w.tick(P, 13f, 200), 1e-6);   // expiry takes 5, the other 8 stay
        // all absorption gone by damage: nothing to take, the next jump hit gives anew
        w.onJumpHit(P, 2, 0f, 300);
        assertEquals(0f, w.tick(P, 0f, 310), 1e-6);
        assertEquals(0f, w.own(P), 1e-6);
        assertEquals(2f, w.onJumpHit(P, 1, 0f, 320), 1e-6);
    }
}
