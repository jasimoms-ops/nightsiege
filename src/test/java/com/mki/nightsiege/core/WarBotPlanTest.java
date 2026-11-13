package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 10: the bot war leader's choices (focus, the wall's weakest spot, machines, retreat, shopping). */
class WarBotPlanTest {

    private static WarBotPlan.Defender d(String name, double x, double z, float health) {
        return new WarBotPlan.Defender(name, x, 64, z, health, 20f);
    }

    @Test
    void focusTakesTheWeakestOfThoseAlone() {
        List<WarBotPlan.Defender> ds = List.of(d("a", 0, 0, 4), d("b", 2, 0, 18), d("c", 40, 0, 12), d("e", 0, 40, 15));
        assertEquals(2, WarBotPlan.focus(ds));   // a is weaker but has b next to him; c and e are alone, c weaker
    }

    @Test
    void focusWithoutAnyoneAloneTakesTheWeakest() {
        List<WarBotPlan.Defender> ds = List.of(d("a", 0, 0, 14), d("b", 2, 0, 6), d("c", 0, 2, 10));
        assertEquals(1, WarBotPlan.focus(ds));
        assertEquals(-1, WarBotPlan.focus(List.of()));
        assertEquals(0, WarBotPlan.focus(List.of(d("solo", 5, 5, 20))));
    }

    @Test
    void weakestSpotIsTheLeastWatched() {
        List<double[]> spots = List.of(new double[]{20, 0, 1}, new double[]{-20, 0, 0}, new double[]{0, 20, 0});
        List<WarBotPlan.Defender> ds = List.of(d("a", 18, 0, 20), d("b", 0, 15, 20), d("c", -30, 0, 20));
        assertEquals(1, WarBotPlan.quietest(spots, ds));   // one watcher each: the farthest (west, 10 blocks)
        List<WarBotPlan.Defender> two = List.of(d("a", 18, 0, 20), d("b", 0, 15, 20));
        assertEquals(1, WarBotPlan.quietest(spots, two));   // nobody in the west
        assertEquals(-1, WarBotPlan.quietest(List.of(), two));
    }

    @Test
    void onATieTheGateGoesFirst() {
        List<double[]> spots = List.of(new double[]{20, 0, 0}, new double[]{-20, 0, 1});
        assertEquals(1, WarBotPlan.quietest(spots, List.of()));
    }

    @Test
    void machinesShootAtGroupsAndThoseUpHigh() {
        List<WarBotPlan.Defender> ds = List.of(d("a", 0, 0, 20), d("b", 30, 0, 20), d("c", 31, 1, 20), d("e", 32, 0, 20));
        int t = WarBotPlan.machineTarget(ds, 64);
        assertTrue(t >= 1, "one of the group of three");
        List<WarBotPlan.Defender> high = List.of(new WarBotPlan.Defender("tower", 0, 70, 0, 20, 20), d("b", 30, 0, 20));
        assertEquals(0, WarBotPlan.machineTarget(high, 64));
        assertEquals(-1, WarBotPlan.machineTarget(List.of(), 64));
    }

    @Test
    void hurtSquadsPullBack() {
        assertTrue(WarBotPlan.retreat(5, 20));
        assertFalse(WarBotPlan.retreat(7, 20));
        assertFalse(WarBotPlan.retreat(0, 0));
    }

    @Test
    void theArmyGrowsWithTheGroup() {
        assertTrue(WarBotPlan.assaultSize(6) > WarBotPlan.assaultSize(2));
        assertEquals(1, WarBotPlan.ladders(2));
        assertEquals(3, WarBotPlan.ladders(6));
        assertFalse(WarBotPlan.shopping(2).contains("eisenritter"));
        assertTrue(WarBotPlan.shopping(6).stream().filter("eisenritter"::equals).count() >= 2);
        for (String id : WarBotPlan.shopping(6)) {
            assertNotNull(WarShop.squad(id), id);
        }
    }

    // Tor 10 botkauf: the bot tops up under the live cap with what still fits, and says why it stops.
    private static final WarBotPlan.Offer ZOMBIES = new WarBotPlan.Offer("zombies", 4, 4);
    private static final WarBotPlan.Offer KNIGHT = new WarBotPlan.Offer("eisenritter", 5, 1);
    private static final WarBotPlan.Offer GRAPPLE = new WarBotPlan.Offer("enterhaken", 3, 1);

    @Test
    void pickTakesASmallSquadWhenTheBigOnesNoLongerFit() {
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 50; i++) {
            WarBotPlan.Offer o = WarBotPlan.pick(List.of(ZOMBIES, ZOMBIES, ZOMBIES, KNIGHT), 100, 2, r);
            assertEquals("eisenritter", o.id());   // room 2: four zombies do not fit, the knight does
        }
    }

    @Test
    void pickRespectsThePurseAndTheCap() {
        java.util.Random r = new java.util.Random(2);
        assertNull(WarBotPlan.pick(List.of(ZOMBIES, KNIGHT), 100, 0, r));       // the field is full
        assertNull(WarBotPlan.pick(List.of(ZOMBIES, KNIGHT), 3, 40, r));        // too poor for both
        assertEquals("enterhaken", WarBotPlan.pick(List.of(ZOMBIES, KNIGHT, GRAPPLE), 3, 40, r).id());
    }

    @Test
    void stopSaysFullOrPoor() {
        assertEquals("voll", WarBotPlan.stop(List.of(ZOMBIES, KNIGHT), 100, 0));
        assertEquals("arm", WarBotPlan.stop(List.of(ZOMBIES, KNIGHT), 3, 40));
    }

    @Test
    void pickSpreadsOverTheList() {
        java.util.Random r = new java.util.Random(3);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(WarBotPlan.pick(List.of(ZOMBIES, KNIGHT, GRAPPLE), 100, 40, r).id());
        }
        assertEquals(3, seen.size());
    }

    @Test
    void archersAgainstDefendersStandingHigh() {
        List<WarBotPlan.Defender> defs = List.of(
                new WarBotPlan.Defender("a", 0, 70, 0, 20f, 20f),   // on a tower
                new WarBotPlan.Defender("b", 5, 64, 0, 20f, 20f));
        assertEquals(1, WarBotPlan.high(defs, 64));
        long base = WarBotPlan.shopping(2).stream().filter("skelette"::equals).count();
        long more = WarBotPlan.shopping(2, 1).stream().filter("skelette"::equals).count();
        assertEquals(base + 2, more);
        assertEquals(WarBotPlan.shopping(2, 3).size(), WarBotPlan.shopping(2, 9).size());   // at most three count
    }

    // Tor 10 bot fix (krieg6/krieg8): no retreat two seconds after every storm, no ladders against an open base.
    @Test
    void aThinnedSquadIsNotPulledBackRightAfterBeingSent() {
        // Four zombies of 20 life, three fallen: 20 of 80 (25 %) on the old measure would retreat at once.
        assertTrue(WarBotPlan.retreat(20f, 80f));
        assertFalse(WarBotPlan.retreatSinceSent(20f, 20f));   // sent with 20: nothing lost in this storm
        assertFalse(WarBotPlan.retreatSinceSent(7f, 20f));    // 35 % of what it was sent with
        assertTrue(WarBotPlan.retreatSinceSent(5f, 20f));     // 25 %: it lost most in this storm
        assertFalse(WarBotPlan.retreatSinceSent(5f, 0f));     // never sent: no retreat
    }

    @Test
    void ladderAndWallOnlyAgainstAClosedBase() {
        assertTrue(WarBotPlan.walled(true, true));
        assertFalse(WarBotPlan.walled(true, false));    // the sim's castle: open, 0 gates
        assertFalse(WarBotPlan.walled(false, false));   // no scan
    }
}
