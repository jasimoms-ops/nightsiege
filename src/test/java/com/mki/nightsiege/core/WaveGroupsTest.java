package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 F (Simon 17.11.): every wave in 2–4 groups from all around, evenly apart, turned each wave. */
class WaveGroupsTest {

    @Test
    void twoToFourGroupsByDefault() {
        SiegeConfig cfg = new SiegeConfig();
        Random random = new Random(7);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            int n = WaveGroups.count(cfg, random);
            assertTrue(n >= 2 && n <= 4, "groups " + n);
            seen.add(n);
        }
        assertEquals(Set.of(2, 3, 4), seen);
    }

    @Test
    void theGroupsAreEvenlyApartAllAround() {
        SiegeConfig cfg = new SiegeConfig();
        for (int n = 2; n <= 4; n++) {
            double[] a = WaveGroups.angles(cfg, n, 0.37);
            assertEquals(n, a.length);
            for (int i = 0; i < n; i++) {
                double gap = WaveGroups.wrap(a[(i + 1) % n] - a[i]);
                assertEquals(2 * Math.PI / n, gap, 1e-9, "n=" + n + " gap " + i);
            }
        }
    }

    @Test
    void everyWaveIsTurned() {
        SiegeConfig cfg = new SiegeConfig();
        Random random = new Random(3);
        Set<Integer> firstSlices = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            double first = WaveGroups.angles(cfg, 3, random.nextDouble())[0];
            firstSlices.add((int) (first / (2 * Math.PI / 3) * 12));   // twelve bins over one step of 120°
        }
        assertTrue(firstSlices.size() >= 8, "turns seen " + firstSlices);
    }

    @Test
    void aGroupCanBeTurnedOntoAGate() {
        SiegeConfig cfg = new SiegeConfig();
        double gate = Math.toRadians(200);
        for (int n = 2; n <= 4; n++) {
            double best = Double.MAX_VALUE;
            for (double a : WaveGroups.angles(cfg, n, WaveGroups.turnTowards(cfg, n, gate))) {
                best = Math.min(best, Math.abs(WaveGroups.wrap(a - gate + Math.PI) - Math.PI));
            }
            assertEquals(0.0, best, 1e-9, "one group on the gate, n=" + n);
        }
    }

    @Test
    void withAnArcTheGroupsStayInIt() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.spawnArcFrom = 45;
        cfg.spawnArcTo = 135;
        Random random = new Random(11);
        for (int i = 0; i < 100; i++) {
            for (double a : WaveGroups.angles(cfg, 2 + i % 3, random.nextDouble())) {
                assertTrue(SpawnArc.contains(cfg, a), "angle " + Math.toDegrees(a));
            }
        }
    }
}
