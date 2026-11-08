package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simon 08.11.: the monsters only from north-east to south-east (spawnArcFrom 45, spawnArcTo 135). */
class SpawnArcTest {

    @Test
    void allAroundByDefault() {
        SiegeConfig cfg = new SiegeConfig();
        assertTrue(SpawnArc.all(cfg));
        assertTrue(SpawnArc.contains(cfg, Math.PI));
    }

    @Test
    void northEastToSouthEast() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.spawnArcFrom = 45;
        cfg.spawnArcTo = 135;
        assertEquals(90.0, SpawnArc.widthDegrees(cfg), 1e-9);
        assertTrue(SpawnArc.contains(cfg, 0.0));                    // east
        assertTrue(SpawnArc.contains(cfg, Math.toRadians(-44)));    // almost north-east
        assertTrue(SpawnArc.contains(cfg, Math.toRadians(44)));     // almost south-east
        assertFalse(SpawnArc.contains(cfg, Math.toRadians(-90)));   // north
        assertFalse(SpawnArc.contains(cfg, Math.toRadians(90)));    // south
        assertFalse(SpawnArc.contains(cfg, Math.PI));               // west
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertTrue(SpawnArc.contains(cfg, SpawnArc.random(cfg, r)));
            assertTrue(SpawnArc.contains(cfg, SpawnArc.fold(cfg, Math.PI, r)));
            assertTrue(SpawnArc.contains(cfg, SpawnArc.spread(cfg, 0.0, i % 3, 3)));
        }
    }

    @Test
    void acrossNorth() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.spawnArcFrom = 315;
        cfg.spawnArcTo = 45;
        assertEquals(90.0, SpawnArc.widthDegrees(cfg), 1e-9);
        assertTrue(SpawnArc.contains(cfg, Math.toRadians(-90)));    // north
        assertFalse(SpawnArc.contains(cfg, Math.toRadians(90)));    // south
    }
}
