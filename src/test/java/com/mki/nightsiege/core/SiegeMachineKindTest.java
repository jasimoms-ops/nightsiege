package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The siege machines' numbers (concept 27 October 2026). */
class SiegeMachineKindTest {

    @Test
    void machinesPerNightFollowTheLevel() {
        assertEquals(1, SiegeMachineKind.countFor(1));
        assertEquals(1, SiegeMachineKind.countFor(5));
        assertEquals(2, SiegeMachineKind.countFor(6));
        assertEquals(2, SiegeMachineKind.countFor(10));
        assertEquals(3, SiegeMachineKind.countFor(11));
        assertEquals(4, SiegeMachineKind.countFor(20));
        assertEquals(5, SiegeMachineKind.countFor(21));
        assertEquals(5, SiegeMachineKind.countFor(25));
        assertEquals(6, SiegeMachineKind.countFor(26));
        assertEquals(1, SiegeMachineKind.countFor(0));
    }

    @Test
    void everyKindComesOnceBeforeAnyComesTwice() {
        Random random = new Random(7);
        for (int n = 1; n <= 12; n++) {
            List<SiegeMachineKind> picked = SiegeMachineKind.pick(n, random);
            assertEquals(n, picked.size());
            Set<SiegeMachineKind> firstSix = EnumSet.noneOf(SiegeMachineKind.class);
            firstSix.addAll(picked.subList(0, Math.min(6, n)));
            assertEquals(Math.min(6, n), firstSix.size(), "no repeats among the first six");
        }
    }

    @Test
    void lifeGrowsWithTheLevel() {
        for (SiegeMachineKind kind : SiegeMachineKind.values()) {
            assertEquals(kind.baseHealth(), kind.health(1), 1e-9);
            assertTrue(kind.health(20) > kind.health(10));
            assertEquals(kind, SiegeMachineKind.byId(kind.id()));
            assertTrue(kind.seconds() > 0);
        }
        assertNull(SiegeMachineKind.byId("catapult"));
    }

    @Test
    void campSettingsAreClamped() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.campChance = 3.0;
        cfg.campMinDistance = 10;
        cfg.campMaxDistance = 5;
        cfg.machineCrew = 0;
        cfg.sanitize();
        assertEquals(1.0, cfg.campChance, 1e-9);
        assertEquals(40, cfg.campMinDistance);
        assertTrue(cfg.campMaxDistance >= cfg.campMinDistance);
        assertEquals(1, cfg.machineCrew);
    }
}
