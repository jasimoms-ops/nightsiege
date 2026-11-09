package com.mki.nightsiege.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 6.7: the latecomer system (floor, pool, paying it off) and old saves running on. */
class CatchUpTest {

    @Test
    void missedNights() {
        assertEquals(0, CatchUp.missed(5, 4));
        assertEquals(2, CatchUp.missed(5, 2));
        assertEquals(9, CatchUp.missed(9, -1));
        assertEquals(0, CatchUp.missed(0, -1));
    }

    @Test
    void floorLiftsOnlyWhoIsBelow() {
        // Steady player 60 points, floor two nights behind = 48.
        assertEquals(18, CatchUp.floorAdd(30, 48));
        assertEquals(0, CatchUp.floorAdd(48, 48));
        assertEquals(0, CatchUp.floorAdd(55, 48));
        assertEquals(48, CatchUp.floorAdd(0, 48));
    }

    @Test
    void poolGrowsPerMissedNightCappedAndNeverPastTheGap() {
        // expected 60, two nights behind = 48 -> cap 12, one night = 6.
        assertEquals(6, CatchUp.pool(0, 1, 6, 12, 60, 54));    // missed one: exactly the gap
        assertEquals(12, CatchUp.pool(0, 5, 6, 12, 60, 48));   // missed five, after the floor: capped at 2 nights
        assertEquals(3, CatchUp.pool(0, 2, 6, 12, 60, 57));    // only the gap
        assertEquals(0, CatchUp.pool(0, 3, 6, 12, 60, 70));    // ahead of the steady player: nothing
        assertEquals(12, CatchUp.pool(10, 1, 6, 12, 60, 40));  // an old pool adds up, still capped
        assertEquals(0, CatchUp.pool(4, 0, 6, 12, 60, 60));    // caught up meanwhile: the pool goes
    }

    @Test
    void bonusDoublesTheWavePointsAndPaysOffThePool() {
        PlayerSkills s = new PlayerSkills();
        s.catchUpPool = 4;
        int normal = 0;
        int extra = 0;
        // wavesPerPoint 2, factor 1.5: like the normal wave points (0.75 a wave).
        for (int wave = 0; wave < 4; wave++) {
            normal += WaveXp.survive(s, 1, 2, 1.5);
            extra += CatchUp.bonus(s, 1, 2, 1.5);
        }
        assertEquals(3, normal);
        assertEquals(3, extra);          // x2 while the pool lasts
        assertEquals(1, s.catchUpPool);
        for (int wave = 0; wave < 4; wave++) {
            extra += CatchUp.bonus(s, 1, 2, 1.5);
        }
        assertEquals(4, extra);          // never more than the pool
        assertEquals(0, s.catchUpPool);
        assertEquals(0.0, s.catchUpFraction);
        assertEquals(0, CatchUp.bonus(s, 1, 2, 1.5));
        assertEquals(3 + 4, s.earned);   // 3 normal points and 4 from the pool
    }

    @Test
    void bonusAlsoForTheFallen() {
        // The fallen get no normal wave points, the pool still pays (formula D).
        PlayerSkills s = new PlayerSkills();
        s.catchUpPool = 10;
        int got = 0;
        for (int wave = 0; wave < 4; wave++) {
            got += CatchUp.bonus(s, 1, 2, 1.5);
        }
        assertEquals(3, got);
        assertEquals(7, s.catchUpPool);
        assertEquals(3, s.earned);
        assertEquals(3, s.points());
    }

    @Test
    void oldSavesRunOn() {
        Gson gson = new Gson();
        // A player from before Tor 6.7: no pool fields, the last night kept.
        PlayerSkills old = gson.fromJson("{\"points\":12,\"earned\":40,\"lastNight\":7,\"waveCredit\":1}", PlayerSkills.class);
        assertEquals(0, old.catchUpPool);
        assertEquals(0.0, old.catchUpFraction);
        assertEquals(7, old.lastNight);
        assertEquals(2, CatchUp.missed(10, old.lastNight));   // the old count of missed nights goes on
        // An old server config: the new system is on, the old catchUpMax stays for switching back.
        SiegeConfig cfg = gson.fromJson("{\"catchUpMax\":3}", SiegeConfig.class);
        assertTrue(cfg.lateJoiners);
        assertEquals(2, cfg.lateJoinerNights);
        assertEquals(3, cfg.catchUpMax);
    }
}
