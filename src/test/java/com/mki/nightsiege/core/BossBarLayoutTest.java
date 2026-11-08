package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compact boss bars (Tor 2.3): order, heights, share of the screen, the boss with the big bar. */
class BossBarLayoutTest {

    /** 1080p at GUI scale 2. */
    private static final int GUI_H = 540;

    @Test
    void oneBossStaysAsBefore() {
        List<BossBarLayout.Slot> s = BossBarLayout.layout(new boolean[] {false, true, false}, 1, GUI_H);
        assertEquals(List.of(new BossBarLayout.Slot(0, 12, false), new BossBarLayout.Slot(1, 31, false),
                new BossBarLayout.Slot(2, 50, false)), s);
    }

    @Test
    void fourBossesOneBigThreeSmall() {
        List<BossBarLayout.Slot> s = BossBarLayout.layout(new boolean[] {true, true, true, true}, 2, GUI_H);
        assertEquals(List.of(new BossBarLayout.Slot(2, 12, false), new BossBarLayout.Slot(0, 20, true),
                new BossBarLayout.Slot(1, 30, true), new BossBarLayout.Slot(3, 40, true)), s);
        // before: four big bars down to 69 + 6 = 75; now 48 (GUI pixels, times 2 on screen)
        int before = BossBarLayout.bottom(BossBarLayout.layout(new boolean[] {false, false, false, false}, -1, GUI_H));
        assertEquals(75, before);
        assertEquals(48, BossBarLayout.bottom(s));
        assertTrue(BossBarLayout.bottom(s) * 100 / GUI_H <= 10, "at most a tenth of the screen");
    }

    @Test
    void otherBarsKeepTheirPlace() {
        // night bar, two bosses, the camp, a nemesis: the group stands where the first boss stood
        boolean[] boss = {false, true, true, false, true};
        List<BossBarLayout.Slot> s = BossBarLayout.layout(boss, 4, GUI_H);
        assertEquals(List.of(new BossBarLayout.Slot(0, 12, false), new BossBarLayout.Slot(4, 31, false),
                new BossBarLayout.Slot(1, 39, true), new BossBarLayout.Slot(2, 49, true),
                new BossBarLayout.Slot(3, 70, false)), s);
        // rows do not overlap: each starts below the one before ends
        for (int i = 1; i < s.size(); i++) {
            int prevEnd = s.get(i - 1).mini() ? s.get(i - 1).y() + 9 : s.get(i - 1).y() + 6;
            int top = s.get(i).mini() ? s.get(i).y() : s.get(i).y() - 9;
            assertTrue(top >= prevEnd, "slot " + i);
        }
    }

    @Test
    void badMainFallsBackToFirstBoss() {
        List<BossBarLayout.Slot> s = BossBarLayout.layout(new boolean[] {false, true, true}, 0, GUI_H);
        assertEquals(new BossBarLayout.Slot(1, 31, false), s.get(1));
        assertEquals(new BossBarLayout.Slot(2, 39, true), s.get(2));
    }

    @Test
    void neverPastAThirdOfTheScreen() {
        boolean[] boss = new boolean[30];
        java.util.Arrays.fill(boss, true);
        for (int h : new int[] {240, 360, 540}) {
            List<BossBarLayout.Slot> s = BossBarLayout.layout(boss, 0, h);
            assertTrue(s.get(s.size() - 1).y() < h / 3, "h=" + h);
        }
        boolean[] plain = new boolean[30];
        List<BossBarLayout.Slot> s = BossBarLayout.layout(plain, -1, 240);
        assertTrue(s.get(s.size() - 1).y() < 80);
    }

    @Test
    void bigBarFollowsTheLastHitButHolds() {
        BossBarLayout.Focus<String> f = new BossBarLayout.Focus<>();
        List<String> ids = List.of("a", "b", "c");
        assertEquals("a", f.pick(ids, List.of(1f, 1f, 1f), 0));
        assertEquals("b", f.pick(ids, List.of(1f, 0.9f, 1f), 100));      // b hit, a never was
        assertEquals("b", f.pick(ids, List.of(1f, 0.9f, 0.8f), 200));    // c hit, but b only 0.1 s ago
        assertEquals("c", f.pick(ids, List.of(1f, 0.9f, 0.7f), 3200));   // b left alone for 3 s
        assertEquals("b", f.pick(List.of("a", "b"), List.of(1f, 0.9f), 3300));   // c gone: the last one hit before it
    }
}
