package com.mki.nightsiege.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure logic of the human bots (Tor 6.0). */
class BotHumanTest {

    @Test
    void prioritiesByName() {
        assertEquals(3, BotHuman.prio("zombie", true));
        assertEquals(2, BotHuman.prio("Siege_Catapult", false));
        assertEquals(2, BotHuman.prio("executioner", false));
        assertEquals(0, BotHuman.prio("zombie", false));
        assertEquals(0, BotHuman.prio(null, false));
    }

    @Test
    void nearestWithoutBonusPriorityWithBonus() {
        double[] dist = {3, 8, 12};
        int[] prio = {0, 2, 0};
        double[] hp = {1, 1, 1};
        assertEquals(0, BotHuman.pick(dist, prio, hp, 0));
        assertEquals(1, BotHuman.pick(dist, prio, hp, 4));   // 8 - 8 = 0 beats 3
        assertEquals(0, BotHuman.pick(dist, prio, hp, 2));   // 8 - 4 = 4 loses to 3
        assertEquals(-1, BotHuman.pick(new double[0], new int[0], new double[0], 4));
    }

    @Test
    void weakOnesAreFinishedFirst() {
        assertEquals(1, BotHuman.pick(new double[]{4, 5}, new int[]{0, 0}, new double[]{1, 0.1}, 4));
    }

    @Test
    void bossNoticedAfterDelay() {
        assertFalse(BotHuman.noticed(500, -1, 100));
        assertFalse(BotHuman.noticed(550, 500, 100));
        assertTrue(BotHuman.noticed(600, 500, 100));
    }

    @Test
    void wallShiftWithinBounds() {
        assertEquals(0, BotHuman.wallShift(2, 0));
        assertEquals(4, BotHuman.wallShift(2, -2.0));
        assertEquals(6, BotHuman.wallShift(2, 9));
    }
}
