package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Boss bars: Tor 2.3 compact middle (order, heights, the big bar) and Tor 3.0c corner list (top right, effects free). */
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
        // night bar, two bosses, the camp, a nemesis: the other bars keep their order, the group comes last
        boolean[] boss = {false, true, true, false, true};
        List<BossBarLayout.Slot> s = BossBarLayout.layout(boss, 4, GUI_H);
        assertEquals(List.of(new BossBarLayout.Slot(0, 12, false), new BossBarLayout.Slot(3, 31, false),
                new BossBarLayout.Slot(4, 50, false), new BossBarLayout.Slot(1, 58, true),
                new BossBarLayout.Slot(2, 68, true)), s);
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
        // the big bars are 19 apart, as vanilla's loop counts them
        List<BossBarLayout.Slot> big = BossBarLayout.layout(new boolean[] {true, false, true, false}, 2, GUI_H)
                .stream().filter(x -> !x.mini()).toList();
        for (int i = 0; i < big.size(); i++) {
            assertEquals(BossBarLayout.TOP + i * BossBarLayout.BIG, big.get(i).y());
        }
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

    // ---- Tor 3.0c: the boss list in the top right corner ----

    /** 1080p at GUI scale 2 and 3, and the small test window (854x480) at scale 2. */
    private static final int[][] SCREENS = {{960, 540}, {640, 360}, {427, 240}};
    /** Effect icons shown: none, two (good and bad, as in the scene), good only, bad only, many. */
    private static final int[][] EFFECTS = {{0, 0}, {1, 1}, {2, 0}, {0, 3}, {8, 8}};

    @Test
    void cornerIsTopRightAndKeepsTheMiddleFree() {
        for (int[] scr : SCREENS) {
            for (int[] fx : EFFECTS) {
                for (int n : new int[] {1, 4}) {
                    String at = scr[0] + "x" + scr[1] + " effects " + fx[0] + "/" + fx[1] + " bosses " + n;
                    BossBarLayout.Corner c = BossBarLayout.corner(scr[0], scr[1], n, fx[0], fx[1], false);
                    BossBarLayout.Rect p = c.panel();
                    assertEquals(n, c.rows().size(), at);
                    assertTrue(p.x() >= scr[0] * 2 / 3, "right third: " + at);
                    assertEquals(scr[0] - BossBarLayout.EDGE, p.x() + p.w(), "at the right edge: " + at);
                    // the night bar in the middle (182 wide, name from y 3, bar to y 18) stays free
                    BossBarLayout.Rect night = new BossBarLayout.Rect(scr[0] / 2 - 91, 3, 182, 16);
                    assertFalse(p.overlaps(night), "night bar: " + at);
                    for (BossBarLayout.Rect icon : BossBarLayout.effectIcons(scr[0], fx[0], fx[1], false)) {
                        assertFalse(p.overlaps(icon), "effect icon " + icon + ": " + at);
                    }
                    for (int i = 0; i < n; i++) {
                        BossBarLayout.Rect r = c.rows().get(i);
                        assertTrue(r.x() >= p.x() && r.x() + r.w() <= p.x() + p.w() && r.y() >= p.y()
                                && r.y() + r.h() <= p.y() + p.h(), "row inside the panel: " + at);
                        if (i > 0) {
                            assertFalse(r.overlaps(c.rows().get(i - 1)), "rows apart: " + at);
                        }
                    }
                }
            }
        }
    }

    @Test
    void cornerStartsBelowTheEffectRows() {
        assertEquals(BossBarLayout.EDGE, BossBarLayout.cornerTop(0, 0, false));
        assertEquals(1 + 24 + 3, BossBarLayout.cornerTop(3, 0, false));
        assertEquals(1 + 26 + 24 + 3, BossBarLayout.cornerTop(1, 1, false));
        assertEquals(1 + 26 + 24 + 3, BossBarLayout.cornerTop(0, 1, false));
        assertEquals(16 + 24 + 3, BossBarLayout.cornerTop(1, 0, true));
        // four bosses with Strength and Slowness at GUI scale 2: 54 to 108 of 540, a tenth of the height, a seventh of the width
        BossBarLayout.Corner c = BossBarLayout.corner(960, 540, 4, 1, 1, false);
        assertEquals(new BossBarLayout.Rect(960 - 2 - 124, 54, 124, 4 * 12 + 6), c.panel());
    }

    @Test
    void cornerNeverPastHalfTheScreen() {
        for (int[] scr : SCREENS) {
            BossBarLayout.Corner c = BossBarLayout.corner(scr[0], scr[1], 30, 1, 1, false);
            assertTrue(c.panel().y() + c.panel().h() <= scr[1] / 2, scr[0] + "x" + scr[1]);
            assertTrue(c.rows().size() >= 4, "four bosses fit: " + scr[0] + "x" + scr[1]);
        }
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
