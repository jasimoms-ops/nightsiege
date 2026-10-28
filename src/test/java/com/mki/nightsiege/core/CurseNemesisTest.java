package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Update 2 "Fluch & Rache" (Simon 28 October 2026): the curse altar's odds and the nemesis rules. */
class CurseNemesisTest {

    private static final int[][] CHAIN = {
            {60, 25, 10, 4, 1}, {45, 30, 16, 7, 2}, {30, 30, 22, 13, 5}, {20, 27, 27, 18, 8}};

    @Test
    void everyCurseLiftsTheOddsHalfAStep() {
        assertArrayEquals(CHAIN[0], CurseKind.shiftOdds(CHAIN, 0, 0));
        // One curse: halfway between a normal and an event night.
        assertArrayEquals(new int[]{53, 28, 13, 6, 2}, CurseKind.shiftOdds(CHAIN, 0, 1));
        // Two curses: a normal night's crate rolls like an event night's.
        assertArrayEquals(CHAIN[1], CurseKind.shiftOdds(CHAIN, 0, 2));
        // Three on a fragment night: past the top it stays at the cursed table.
        assertArrayEquals(CHAIN[3], CurseKind.shiftOdds(CHAIN, 2, 3));
        assertArrayEquals(CHAIN[3], CurseKind.shiftOdds(CHAIN, 2, 8));
        // Better crates every step: the share of rare and better only grows.
        int last = -1;
        for (int c = 0; c <= 6; c++) {
            int[] o = CurseKind.shiftOdds(CHAIN, 0, c);
            int rare = o[2] + o[3] + o[4];
            assertTrue(rare >= last, "curses " + c);
            last = rare;
        }
    }

    @Test
    void curseIdsAreUniqueAndFound() {
        Set<String> ids = new HashSet<>();
        for (CurseKind k : CurseKind.values()) {
            assertTrue(ids.add(k.id()), k.id());
            assertEquals(k, CurseKind.byId(k.id()));
            assertEquals(k, CurseKind.byId(k.name()));
            assertTrue(k.translationKey().startsWith("nightsiege.curse."));
        }
        assertNull(CurseKind.byId("nichts"));
        assertNull(CurseKind.byId(null));
    }

    @Test
    void curseSettingsAreSane() {
        SiegeConfig c = new SiegeConfig();
        c.curseMax = 99;
        c.curseHealth = -1;
        c.curseHeal = Double.NaN;
        c.nemesisMaxLevel = 0;
        c.nemesisReturnChance = 3;
        c.sanitize();
        assertEquals(CurseKind.values().length, c.curseMax);
        assertEquals(1.3, c.curseHealth, 1e-9);
        assertEquals(0.5, c.curseHeal, 1e-9);
        assertEquals(5, c.nemesisMaxLevel);
        assertEquals(1.0, c.nemesisReturnChance, 1e-9);
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(3, fresh.curseMax);
        assertArrayEquals(new int[]{20, 27, 27, 18, 8}, fresh.crateOddsCursed);
    }

    @Test
    void nemesisNamesAreTheirOwn() {
        Random r = new Random(7);
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            String name = NemesisLore.name(r, taken);
            assertFalse(name.isBlank());
            assertTrue(Character.isUpperCase(name.charAt(0)), name);
            assertTrue(taken.add(name), "twice: " + name);
        }
    }

    @Test
    void nemesisGrowsWithItsRank() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        List<String> none = new ArrayList<>();
        assertEquals(2.5, NemesisLore.healthFactor(c, 1, none), 1e-9);
        assertEquals(3.5, NemesisLore.healthFactor(c, 3, none), 1e-9);
        assertEquals(2.5 * NemesisLore.TOUGH_HEALTH, NemesisLore.healthFactor(c, 1, List.of("zaeh")), 1e-9);
        assertEquals(1.3, NemesisLore.damageFactor(c, 1, none), 1e-9);
        assertEquals(1.6, NemesisLore.damageFactor(c, 3, none), 1e-9);
        assertEquals(1.3 * NemesisLore.WILD_DAMAGE, NemesisLore.damageFactor(c, 1, List.of("wild")), 1e-9);
        assertEquals(1.2, NemesisLore.speedFactor(List.of("flink")), 1e-9);
        assertEquals("nightsiege.nemesis.title.1", NemesisLore.titleKey(0));
        assertEquals("nightsiege.nemesis.title.5", NemesisLore.titleKey(9));
    }

    @Test
    void everyPromotionTeachesANewTrait() {
        Random r = new Random(3);
        List<String> owned = new ArrayList<>();
        for (int i = 0; i < NemesisLore.Trait.values().length; i++) {
            NemesisLore.Trait t = NemesisLore.newTrait(owned, r);
            assertNotNull(t);
            assertFalse(owned.contains(t.id()));
            owned.add(t.id());
        }
        assertNull(NemesisLore.newTrait(owned, r), "it knows them all");
        for (NemesisLore.Trait t : NemesisLore.Trait.values()) {
            assertEquals(t, NemesisLore.Trait.byId(t.id()));
        }
    }

    @Test
    void aNemesisComesBackInALaterNight() {
        assertFalse(NemesisLore.returns(0, 1.0, 0.0), "never in the night it fled");
        assertTrue(NemesisLore.returns(1, 0.6, 0.59));
        assertFalse(NemesisLore.returns(1, 0.6, 0.61));
        assertFalse(NemesisLore.returns(2, 0.6, 0.9));
        assertTrue(NemesisLore.returns(3, 0.0, 0.99), "after two nights away it always comes");
    }
}
