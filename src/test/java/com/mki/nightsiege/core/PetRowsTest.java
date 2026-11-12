package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paket 11.11. Z2: the companions' rows left of the action bar. */
class PetRowsTest {

    private static PetRows.Pet pet(String kind, float hp, float max, boolean alpha, boolean dead, long born) {
        PetRows.Pet p = new PetRows.Pet();
        p.kind = kind;
        p.hp = hp;
        p.max = max;
        p.alpha = alpha;
        p.dead = dead;
        p.born = born;
        return p;
    }

    @Test
    void wolvesFirstAlphaOnTopFallenLast() {
        List<PetRows.Pet> in = List.of(pet("golem", 50, 100, false, false, 1), pet("wolf", 5, 20, false, true, 2),
                pet("wolf", 14, 20, false, false, 3), pet("wolf", 30, 40, true, false, 4), pet("skeleton", 10, 20, false, false, 0));
        List<PetRows.Pet> out = PetRows.sorted(in);
        assertTrue(out.get(0).alpha);
        assertEquals(3, out.get(1).born);
        assertTrue(out.get(2).dead);
        assertEquals("golem", out.get(3).kind);
        assertEquals("skeleton", out.get(4).kind);
    }

    @Test
    void numberAndColour() {
        assertEquals("14/20", PetRows.number(pet("wolf", 14f, 20f, false, false, 0), 0));
        assertEquals("1/20", PetRows.number(pet("wolf", 0.3f, 20f, false, false, 0), 0));
        assertEquals("12s", PetRows.number(pet("wolf", 0f, 20f, false, true, 0), 231));
        assertEquals("0/20", PetRows.number(pet("wolf", 0f, 20f, false, true, 0), 0));
        assertEquals(0xFF5FC04A, PetRows.color(0.8f));
        assertEquals(0xFFE0B040, PetRows.color(0.4f));
        assertEquals(0xFFD8443A, PetRows.color(0.1f));
    }

    /** At GUI 2 and 3 on 1080p, 2, 4 and 8 rows stay in the bar's height left of it, off chat and voice chat. */
    @Test
    void rowsStayBelowTheChatAndLeftOfTheBar() {
        int[][] screens = {{960, 540, 320}, {640, 360, 160}};
        for (int[] s : screens) {
            for (int n : new int[]{2, 4, 8, 10}) {
                PetRows.Place p = PetRows.place(s[2], s[1], n, 61, 16);
                assertEquals(Math.min(8, n), p.rows().size());
                assertEquals(1f, p.scale(), 1e-6);
                ActionBarLayout.Rect chat = ActionBarLayout.chat(s[1], ActionBarLayout.chatRight(1.0, 1.0), 10);
                List<ActionBarLayout.Rect> voice = ActionBarLayout.voiceChat(s[1]);
                for (ActionBarLayout.Rect r : p.rows()) {
                    assertFalse(r.overlaps(chat), "chat " + r);
                    for (ActionBarLayout.Rect v : voice) {
                        assertFalse(r.overlaps(v), "voice chat " + r);
                    }
                    assertTrue(r.right() <= s[2] - PetRows.GAP, "bar " + r);
                    assertTrue(r.bottom() <= s[1], "bottom " + r);
                }
                if (n > 8) {
                    assertNotNull(p.more());
                } else {
                    assertNull(p.more());
                }
            }
        }
    }

    /** A narrow screen: two columns shrink, and with even less room one column goes up instead. */
    @Test
    void narrowScreensShrinkOrStack() {
        PetRows.Place small = PetRows.place(130, 300, 8, 61, 16);
        assertTrue(small.scale() < 1f && small.scale() >= 0.6f, "scale " + small.scale());
        PetRows.Place tiny = PetRows.place(70, 300, 8, 61, 16);
        List<Integer> xs = new ArrayList<>();
        for (ActionBarLayout.Rect r : tiny.rows()) {
            xs.add(r.x());
            assertTrue(r.x() >= 0);
        }
        assertEquals(1, xs.stream().distinct().count(), "one column " + xs);
        assertTrue(tiny.all().y() < 300 - 37, "goes up");
    }
}
