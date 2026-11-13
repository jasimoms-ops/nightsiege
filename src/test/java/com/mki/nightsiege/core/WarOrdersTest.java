package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kriegsführer 3, K1 (Simon 13.11.2026: "mehr Gewalt über die Armee"): picking, groups, orders in a row,
 * stances, special orders, and the leader's order before the unit's own will.
 */
class WarOrdersTest {

    // ------------------------------------------------------------------ picking

    @Test
    void clickPicksOneShiftAddsAndTakesAway() {
        WarSelection s = new WarSelection();
        s.click(3, false);
        s.click(5, false);
        assertEquals("5", s.ids());
        s.click(7, true);
        assertEquals("5,7", s.ids());
        s.click(5, true);
        assertEquals("7", s.ids());
        assertEquals(7, s.first());
    }

    @Test
    void frameReplacesOrAdds() {
        WarSelection s = new WarSelection();
        s.click(1, false);
        s.box(List.of(2, 3), false);
        assertEquals("2,3", s.ids());
        s.box(List.of(4), true);
        assertEquals("2,3,4", s.ids());
        s.box(List.of(), false);
        assertTrue(s.isEmpty());
        assertEquals(-1, s.first());
    }

    @Test
    void ctrlAPicksAll() {
        WarSelection s = new WarSelection();
        s.click(9, false);
        s.all(List.of(1, 2, 3));
        assertEquals("1,2,3", s.ids());
    }

    // ------------------------------------------------------------------ groups

    @Test
    void groupsKeepAndCome() {
        WarSelection s = new WarSelection();
        s.box(List.of(1, 2), false);
        s.store(1);
        s.click(5, false);
        s.store(2);
        s.clear();
        assertFalse(s.recall(1, 1000, false));
        assertEquals("1,2", s.ids());
        assertFalse(s.recall(2, 2000, false));
        assertEquals("5", s.ids());
        assertFalse(s.recall(1, 3000, true));
        assertEquals("5,1,2", s.ids());
        assertEquals(1, s.groupOf(2));
        assertEquals(0, s.groupOf(9));
    }

    @Test
    void groupTwiceMovesTheCamera() {
        WarSelection s = new WarSelection();
        s.click(4, false);
        s.store(3);
        assertFalse(s.recall(3, 10_000, false));
        assertTrue(s.recall(3, 10_000 + WarSelection.DOUBLE_MS - 1, false));
        assertFalse(s.recall(3, 20_000, false));
        assertFalse(s.recall(4, 20_100, false));   // an empty group: nothing, no camera
    }

    @Test
    void deadSquadsLeavePickAndGroups() {
        WarSelection s = new WarSelection();
        s.box(List.of(1, 2, 3), false);
        s.store(1);
        s.retain(List.of(2));
        assertEquals("2", s.ids());
        assertEquals(List.of(2), s.group(1));
        s.retain(List.of());
        assertEquals(List.of(), s.group(1));
    }

    // ------------------------------------------------------------------ orders in a row

    @Test
    void shiftQueuesWithoutShiftReplaces() {
        WarOrders.Queue q = new WarOrders.Queue();
        q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, 1, 1), false);
        q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, 2, 2), true);
        q.give(WarOrders.Order.at(WarOrders.Kind.ATTACK_MOVE, 3, 3), true);
        assertEquals(3, q.size());
        assertEquals(1, q.current().x());
        assertEquals(2, q.next().x());
        assertEquals(3, q.next().x());
        assertNull(q.next());
        assertTrue(q.isEmpty());
        q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, 1, 1), true);
        q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, 9, 9), false);
        assertEquals(1, q.size());
        assertEquals(9, q.current().x());
    }

    @Test
    void holdAndStormEndTheRowAndTheRowHasAnEnd() {
        WarOrders.Queue q = new WarOrders.Queue();
        q.give(WarOrders.Order.at(WarOrders.Kind.HOLD, 0, 0), false);
        q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, 5, 5), true);   // after a hold nothing waits: it replaces
        assertEquals(1, q.size());
        assertEquals(WarOrders.Kind.MOVE, q.current().kind());
        for (int i = 0; i < 20; i++) {
            q.give(WarOrders.Order.at(WarOrders.Kind.MOVE, i, i), true);
        }
        assertEquals(WarOrders.MAX_QUEUE, q.size());
    }

    // ------------------------------------------------------------------ whom it may fight

    @Test
    void moveOnlyFightsBackRetreatNever() {
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.MOVE, WarOrders.Stance.AGGRESSIVE, false, false, 2));
        assertTrue(WarOrders.mayEngage(WarOrders.Kind.MOVE, WarOrders.Stance.AGGRESSIVE, false, true, 2));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.RETREAT, WarOrders.Stance.AGGRESSIVE, true, true, 0));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.SPECIAL, WarOrders.Stance.AGGRESSIVE, true, true, 0));
    }

    @Test
    void attackOnlyItsTargetAttackMarchAndStormEveryone() {
        assertTrue(WarOrders.mayEngage(WarOrders.Kind.ATTACK, WarOrders.Stance.PASSIVE, true, false, 50));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.ATTACK, WarOrders.Stance.AGGRESSIVE, false, true, 1));
        assertTrue(WarOrders.mayEngage(WarOrders.Kind.ATTACK_MOVE, WarOrders.Stance.PASSIVE, false, false, 50));
        assertTrue(WarOrders.mayEngage(WarOrders.Kind.STORM, WarOrders.Stance.DEFENSIVE, false, false, 50));
    }

    @Test
    void holdFightsWithinSixBlocks() {
        assertTrue(WarOrders.mayEngage(WarOrders.Kind.HOLD, WarOrders.Stance.AGGRESSIVE, false, false, 6.0));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.HOLD, WarOrders.Stance.AGGRESSIVE, false, true, 6.5));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.HOLD, WarOrders.Stance.PASSIVE, false, true, 1.0));
    }

    @Test
    void stancesWhileStanding() {
        assertTrue(WarOrders.mayEngage(null, WarOrders.Stance.AGGRESSIVE, false, false, WarOrders.LEASH));
        assertFalse(WarOrders.mayEngage(null, WarOrders.Stance.AGGRESSIVE, false, false, WarOrders.LEASH + 1));
        assertTrue(WarOrders.mayEngage(null, WarOrders.Stance.DEFENSIVE, false, true, 20));
        assertTrue(WarOrders.mayEngage(null, WarOrders.Stance.DEFENSIVE, false, false, 5));
        assertFalse(WarOrders.mayEngage(null, WarOrders.Stance.DEFENSIVE, false, false, 8));
        assertFalse(WarOrders.mayEngage(null, WarOrders.Stance.PASSIVE, false, true, 1));
        assertEquals(WarOrders.Stance.DEFENSIVE, WarOrders.Stance.AGGRESSIVE.next());
        assertEquals(WarOrders.Stance.PASSIVE, WarOrders.Stance.DEFENSIVE.next());
        assertEquals(WarOrders.Stance.AGGRESSIVE, WarOrders.Stance.PASSIVE.next());
        assertEquals(WarOrders.Stance.AGGRESSIVE, WarOrders.Stance.byId("?"));
    }

    @Test
    void theOrderComesBeforeTheOwnWill() {
        // Standing aggressive it would fight a defender 3 blocks off; with a move order it walks on.
        assertTrue(WarOrders.mayEngage(null, WarOrders.Stance.AGGRESSIVE, false, false, 3));
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.MOVE, WarOrders.Stance.AGGRESSIVE, false, false, 3));
        // An attack order on one defender: no other one, even close by.
        assertFalse(WarOrders.mayEngage(WarOrders.Kind.ATTACK, WarOrders.Stance.AGGRESSIVE, false, false, 1));
    }

    // ------------------------------------------------------------------ special orders

    @Test
    void everyUnitTypeHasItsSpecialOrder() {
        assertEquals(WarOrders.Special.LADDER, WarOrders.specialOf("leitertraeger"));
        assertEquals(WarOrders.Special.BLAST, WarOrders.specialOf(WarShop.SAPPER));
        assertEquals(WarOrders.Special.TUNNEL, WarOrders.specialOf("tunnelgraeber"));
        assertEquals(WarOrders.Special.GRAPPLE, WarOrders.specialOf("enterhaken"));
        assertEquals(WarOrders.Special.STANDARD, WarOrders.specialOf("standarte"));
        assertEquals(WarOrders.Special.AIM, WarOrders.specialOf("machine.trebuchet"));
        assertEquals(WarOrders.Special.BOSS, WarOrders.specialOf("boss.grave_king"));
        assertEquals(WarOrders.Special.NONE, WarOrders.specialOf("zombies"));
        assertEquals(WarOrders.Special.NONE, WarOrders.specialOf(null));
        // Every squad of the shop with a job of its own is named.
        for (WarShop.Squad sq : WarShop.SQUADS) {
            if (sq.ownWill()) {
                assertNotEquals(WarOrders.Special.NONE, WarOrders.specialOf(sq.id()), sq.id());
            }
        }
        assertFalse(WarOrders.Special.BOSS.needsSpot());
        assertTrue(WarOrders.Special.BOSS.hasNow());
        assertTrue(WarOrders.Special.BLAST.hasNow());
        assertTrue(WarOrders.Special.LADDER.needsSpot());
        assertFalse(WarOrders.Special.LADDER.hasNow());
    }

    @Test
    void bossAbilityWaitsItsCooldown() {
        assertEquals(0, WarOrders.cooldownLeft(-1, 100));
        long cd = WarOrders.BOSS_COOLDOWN_SECONDS * 20L;
        assertEquals(cd, WarOrders.cooldownLeft(1000, 1000));
        assertEquals(1, WarOrders.cooldownLeft(1000, 1000 + cd - 1));
        assertEquals(0, WarOrders.cooldownLeft(1000, 1000 + cd));
    }

    // ------------------------------------------------------------------ what a click gives

    @Test
    void rightClickAndKeysGiveTheRightOrder() {
        WarOrders.Kind none = null;
        assertNull(WarOrders.intent(false, none, true, false, false));
        assertNull(WarOrders.intent(true, none, false, false, false));
        assertEquals(WarOrders.Kind.MOVE, WarOrders.intent(true, none, true, false, false));
        assertEquals(WarOrders.Kind.ATTACK, WarOrders.intent(true, none, true, true, false));
        assertEquals(WarOrders.Kind.ATTACK, WarOrders.intent(true, none, true, false, true));
        assertEquals(WarOrders.Kind.ATTACK_MOVE, WarOrders.intent(true, WarOrders.Kind.ATTACK, true, false, false));
        assertEquals(WarOrders.Kind.ATTACK, WarOrders.intent(true, WarOrders.Kind.ATTACK, true, true, false));
        assertEquals(WarOrders.Kind.MOVE, WarOrders.intent(true, WarOrders.Kind.MOVE, true, true, true));
        assertEquals(WarOrders.Kind.SPECIAL, WarOrders.intent(true, WarOrders.Kind.SPECIAL, true, false, false));
        assertNull(WarOrders.intent(true, WarOrders.Kind.SPECIAL, false, true, false));
    }

    @Test
    void theBaseEdgeDecidesAttack() {
        List<Double> edge = List.of(10.0, 10.0, 20.0, 20.0);   // east and south 10, west and north 20
        assertTrue(WarOrders.onBase(edge, 0, 0, 11, 0.5, 1.5));
        assertFalse(WarOrders.onBase(edge, 0, 0, 13, 0.5, 1.5));
        assertTrue(WarOrders.onBase(edge, 0, 0, -18, -0.5, 1.5));
        assertFalse(WarOrders.onBase(List.of(), 0, 0, 1, 1, 1.5));
        assertTrue(WarOrders.arrived(10, 10, 12, 11));
        assertFalse(WarOrders.arrived(10, 10, 14, 10));
    }

    @Test
    void kindsAndColours() {
        for (WarOrders.Kind k : WarOrders.Kind.values()) {
            assertEquals(k, WarOrders.Kind.byId(k.id()));
        }
        assertNull(WarOrders.Kind.byId("gather"));
        assertEquals(WarOrders.Kind.MOVE.color(), WarOrders.Kind.RETREAT.color());
        assertNotEquals(WarOrders.Kind.MOVE.color(), WarOrders.Kind.ATTACK.color());
        assertNotEquals(WarOrders.Kind.ATTACK.color(), WarOrders.Kind.SPECIAL.color());
    }
}
