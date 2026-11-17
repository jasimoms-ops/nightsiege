package com.mki.nightsiege.core;

import com.mki.nightsiege.game.SiegeSave;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 F (Simon 17.11. 15:30): every night a 90° side, never the last night's, over many nights all eight. */
class NightArcTest {

    @Test
    void neverTheSameSideTwiceAndAllEightOverManyNights() {
        Random random = new Random(5);
        int last = NightArc.NONE;
        Set<Integer> seen = new HashSet<>();
        for (int night = 1; night <= 200; night++) {
            int d = NightArc.pick(last, random, x -> true);
            assertNotEquals(last, d, "night " + night);
            assertTrue(d >= 0 && d < 8);
            seen.add(d);
            last = d;
        }
        assertEquals(8, seen.size());
    }

    @Test
    void nightsOneToTwentyFourChangeEveryNight() {
        Random random = new Random(24);
        int last = NightArc.NONE;
        Set<Integer> seen = new HashSet<>();
        for (int night = 1; night <= 24; night++) {
            int d = NightArc.pick(last, random, x -> true);
            assertNotEquals(last, d);
            seen.add(d);
            last = d;
        }
        assertTrue(seen.size() >= 6, "sides in 24 nights: " + seen);
    }

    @Test
    void withoutSpawnGroundTheNextSide() {
        // Only south (4) and south-west (5) have ground.
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            int d = NightArc.pick(4, random, x -> x == 4 || x == 5);
            assertEquals(5, d, "never the last one, the next with ground");
        }
        for (int i = 0; i < 50; i++) {
            int d = NightArc.pick(NightArc.NONE, random, x -> x == 4 || x == 5);
            assertTrue(d == 4 || d == 5);
        }
        // No ground anywhere else: still a new side.
        assertNotEquals(3, NightArc.pick(3, random, x -> x == 3));
    }

    @Test
    void theSideIsNinetyDegreesRoundItsMiddle() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals(90.0, cfg.spawnArcWidth, 1e-9);
        assertTrue(cfg.spawnArcRotate);
        int south = 4;
        assertEquals(135.0, NightArc.from(south, 90), 1e-9);
        assertEquals(225.0, NightArc.to(south, 90), 1e-9);
        assertEquals("se", NightArc.key(NightArc.from(south, 90)));
        assertEquals("sw", NightArc.key(NightArc.to(south, 90)));
        assertEquals("nw", NightArc.key(NightArc.from(0, 90)));   // -45°
        try {
            SpawnArc.tonight(NightArc.from(south, 90), NightArc.to(south, 90));
            assertTrue(SpawnArc.contains(cfg, Math.PI / 2));           // south
            assertFalse(SpawnArc.contains(cfg, -Math.PI / 2));         // north
            assertEquals(90.0, SpawnArc.widthDegrees(cfg), 1e-9);
        } finally {
            SpawnArc.clearTonight();
        }
        assertTrue(SpawnArc.all(cfg));   // the config's arc again
    }

    @Test
    void theSideIsKeptInTheSave() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        SiegeSave save = new SiegeSave();
        assertEquals(-1, save.spawnArcDir);
        assertTrue(save.spawnArcDone);
        save.spawnArcDir = 6;
        save.spawnArcDone = false;
        SiegeSave back = gson.fromJson(gson.toJson(save), SiegeSave.class);
        assertEquals(6, back.spawnArcDir);
        assertFalse(back.spawnArcDone);
        SiegeSave old = gson.fromJson("{}", SiegeSave.class);   // a save from before Tor 12
        assertEquals(-1, old.spawnArcDir);
        assertTrue(old.spawnArcDone);
    }
}
