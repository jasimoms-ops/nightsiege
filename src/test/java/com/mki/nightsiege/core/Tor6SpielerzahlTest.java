package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tor 6.4 (Chef 09.11.): group size – presence, cap, tick gauge. Chef 09.11. 12:05: the solo damping (tables
 * 0.7/0.9/0.95/1.0) stays and the elites' life does not grow with the group; migration 25 changes nothing.
 */
class Tor6SpielerzahlTest {

    private static SiegeConfig oldServer() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = 23;
        c.damageTakenByPlayers = new ArrayList<>(List.of(0.7, 0.9, 0.95, 1.0));
        c.mobHealthByPlayers = new ArrayList<>(List.of(0.7, 0.9, 0.95, 1.0));
        return c;
    }

    @Test
    void freshConfigKeepsSoloDamping() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertEquals(List.of(0.7, 0.9, 0.95, 1.0), fresh.damageTakenByPlayers);
        assertEquals(List.of(0.7, 0.9, 0.95, 1.0), fresh.mobHealthByPlayers);
        assertEquals(0.7, SiegeMath.damageTakenForGroup(1, fresh), 1e-9);
        assertEquals(0.9, SiegeMath.healthForGroup(2, fresh), 1e-9);
        assertEquals(1.0, SiegeMath.damageTakenForGroup(10, fresh), 1e-9);
        assertEquals(0.0, fresh.eliteHealthPerExtraPlayer, 1e-9);
        for (int n = 1; n <= 10; n++) {
            assertEquals(1.0, SiegeMath.groupHealth(n, fresh.eliteHealthPerExtraPlayer), 1e-9);   // elites ×1
        }
        assertEquals(0.5, fresh.bossHealthPerExtraPlayer, 1e-9);
        assertEquals(120, fresh.maxLiveMobsCap);
        assertFalse(fresh.soloHelper);
    }

    @Test
    void serverTableFromVersion23Stays() {
        SiegeConfig server = oldServer();
        server.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(List.of(0.7, 0.9, 0.95, 1.0), server.damageTakenByPlayers);
        assertEquals(List.of(0.7, 0.9, 0.95, 1.0), server.mobHealthByPlayers);
        assertEquals(0.7, SiegeMath.damageTakenForGroup(1, server), 1e-9);
        assertEquals(0.9, SiegeMath.healthForGroup(2, server), 1e-9);
        assertEquals(0.0, server.eliteHealthPerExtraPlayer, 1e-9);
    }

    @Test
    void ownTableStays() {
        SiegeConfig own = oldServer();
        own.damageTakenByPlayers = new ArrayList<>(List.of(0.8, 1.0));
        own.mobHealthByPlayers = new ArrayList<>(List.of(0.85));
        own.sanitize();
        assertEquals(List.of(0.8, 1.0), own.damageTakenByPlayers);
        assertEquals(List.of(0.85), own.mobHealthByPlayers);
    }

    @Test
    void eliteAndBossLifeGrowHalfPerPlayer() {
        assertEquals(1.0, SiegeMath.groupHealth(1, 0.5), 1e-9);
        assertEquals(1.5, SiegeMath.groupHealth(2, 0.5), 1e-9);
        assertEquals(3.0, SiegeMath.groupHealth(5, 0.5), 1e-9);
        assertEquals(5.5, SiegeMath.groupHealth(10, 0.5), 1e-9);
        assertEquals(1.0, SiegeMath.groupHealth(0, 0.5), 1e-9);
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        // the boss: 1 + 0.5 × further (BossPlan), level 1, factor bossHealthMultiplier (Paket 11.11.: 0.35)
        assertEquals(100.0 * 2.5 * c.bossHealthMultiplier * SiegeMath.healthMultiplier(1, c), BossPlan.health(100.0, 1, 4, c), 1e-6);
    }

    @Test
    void numbersGrowWithPlayers() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = 16;   // the server's way: soloScaling 0.6, the cap table
        c.sanitize();
        double one = SiegeMath.mobsWanted(10, 1, c, c.playerScaling);
        assertEquals(one * 4, SiegeMath.mobsWanted(10, 4, c, c.playerScaling), 1e-9);   // 0.6 per player, linear
        assertEquals(0.7, SiegeMath.damageTakenForGroup(1, c), 1e-9);   // the solo damping stays (Chef 09.11. 12:05)
        assertEquals(1.0, SiegeMath.damageTakenForGroup(8, c), 1e-9);
    }

    @Test
    void overflowBecomesElites() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        assertEquals(0.0, SiegeMath.overflowEliteChance(100, 120, c), 1e-9);
        // 150 wanted, cap 120: 30 left out / 3 = 10 elites over 120 monsters
        assertEquals(10.0 / 120.0, SiegeMath.overflowEliteChance(150, 120, c), 1e-9);
        assertEquals(1.0, SiegeMath.overflowEliteChance(10000, 120, c), 1e-9);
        c.overflowEliteCost = 0;
        assertEquals(0.0, SiegeMath.overflowEliteChance(150, 120, c), 1e-9);
    }

    @Test
    void whoCountsForTheNightsSize() {
        long off = 1200, away = 600;
        assertTrue(SiegeMath.countsForSize(true, 0, off, true, 0, away));
        assertTrue(SiegeMath.countsForSize(false, 1199, off, false, 0, away));    // offline within the minute
        assertFalse(SiegeMath.countsForSize(false, 1200, off, false, 0, away));   // gone: no longer counts
        assertTrue(SiegeMath.countsForSize(true, 0, off, false, 599, away));      // walked off, within the grace
        assertFalse(SiegeMath.countsForSize(true, 0, off, false, 600, away));     // left the night
        assertTrue(SiegeMath.countsForSize(true, 0, off, true, 0, away));         // came back: counts again
    }

    @Test
    void tickPercentile() {
        TickLoad t = new TickLoad();
        assertEquals(0.0, t.percentile(0.95), 1e-9);
        for (int i = 0; i < 95; i++) {
            t.add(10.2);
        }
        for (int i = 0; i < 5; i++) {
            t.add(80.0);
        }
        assertEquals(10.5, t.percentile(0.95), 1e-9);
        t.add(80.0);
        assertEquals(80.5, t.percentile(0.95), 1e-9);
        t.add(999.0);
        assertEquals(250.5, t.percentile(1.0), 1e-9);
        t.clear();
        assertEquals(0, t.count());
    }

    @Test
    void soloHelperOnlyWhenSwitchedOnAndAlone() {
        SiegeConfig c = new SiegeConfig();
        assertFalse(SoloHelper.wanted(c, 1));
        c.soloHelper = true;
        assertTrue(SoloHelper.wanted(c, 1));
        assertFalse(SoloHelper.wanted(c, 2));
    }
}
