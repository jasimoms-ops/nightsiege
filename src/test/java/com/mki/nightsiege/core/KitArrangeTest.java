package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The kit page's arrangement (update 6): crate items only in the hotbar, as many as fit. */
class KitArrangeTest {

    private static KitArrange.Thing own(String key) {
        return new KitArrange.Thing(key, "", false, 0);
    }

    private static KitArrange.Thing back(String key) {
        return new KitArrange.Thing(key, "", true, 0);
    }

    private static KitArrange.Thing crate(String key, String gain, int rank) {
        return new KitArrange.Thing(key, gain, false, rank);
    }

    private static List<KitArrange.Thing> warrior() {
        List<KitArrange.Thing> t = new ArrayList<>();
        t.add(own("sword"));
        t.add(own("beef"));
        t.add(back("cobblestone"));
        t.add(crate("spear", "wurfspeere", 2));
        t.add(crate("banner", "kriegsbanner", 3));
        t.add(crate("axe|wurfbeil", "wurfbeile", 1));
        t.add(crate("potion", "heiltrank", 1));
        t.add(crate("potion", "heiltrank", 1));
        t.add(back("wooden_pickaxe"));
        t.add(back("wooden_shovel"));
        return t;
    }

    @Test
    void automaticPutsToolsUpAndCrateItemsIntoTheHotbar() {
        List<KitArrange.Thing> t = warrior();
        int[] s = KitArrange.place(t, null, null);
        for (int i = 0; i < t.size(); i++) {
            assertTrue(s[i] >= 0, "everything fits: " + t.get(i));
            if (t.get(i).crate()) {
                assertTrue(s[i] < KitArrange.HOTBAR, "crate item in the hotbar: " + t.get(i));
            }
            if (t.get(i).back()) {
                assertTrue(s[i] >= KitArrange.HOTBAR, "tools and stone in the inventory: " + t.get(i));
            }
        }
    }

    @Test
    void crateItemsWithoutRoomStayHomeRarestFirst() {
        List<KitArrange.Thing> t = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            t.add(own("thing" + i));
        }
        t.add(crate("a", "common", 0));
        t.add(crate("b", "epic", 3));
        t.add(crate("c", "rare", 2));
        int[] s = KitArrange.place(t, null, null);
        assertTrue(s[8] >= 0 && s[8] < 9, "the epic one comes");
        assertTrue(s[9] >= 0 && s[9] < 9, "the rare one comes");
        assertEquals(-1, s[7], "the common one stays at home: no hotbar place left");
    }

    @Test
    void onlyTheChosenCrateItemsCome() {
        List<KitArrange.Thing> t = warrior();
        int[] s = KitArrange.place(t, null, List.of("kriegsbanner"));
        assertEquals(-1, s[3], "throwing spears not chosen");
        assertTrue(s[4] >= 0 && s[4] < 9, "war banner chosen");
        assertEquals(-1, s[6]);
        assertEquals(-1, s[7]);
    }

    @Test
    void theLayoutIsKeptButCrateItemsNeverGoUp() {
        List<KitArrange.Thing> t = warrior();
        Map<String, Integer> layout = Map.of("sword", 4, "beef", 20, "spear", 15, "banner", 0, "cobblestone", 8);
        int[] s = KitArrange.place(t, layout, List.of("wurfspeere", "kriegsbanner"));
        assertEquals(4, s[0]);
        assertEquals(20, s[1]);
        assertEquals(8, s[2], "stone may stay in the hotbar when the player keeps it there");
        assertEquals(0, s[4]);
        assertTrue(s[3] >= 0 && s[3] < 9, "the spears were kept up in the inventory: back into the hotbar");
    }

    @Test
    void aFullHotbarLeavesCrateItemsAtHome() {
        List<KitArrange.Thing> t = new ArrayList<>();
        java.util.Map<String, Integer> layout = new java.util.HashMap<>();
        for (int i = 0; i < 9; i++) {
            t.add(own("thing" + i));
            layout.put("thing" + i, i);
        }
        t.add(crate("spear", "wurfspeere", 2));
        int[] s = KitArrange.place(t, layout, List.of("wurfspeere"));
        assertEquals(-1, s[9]);
    }

    @Test
    void severalStacksOfOneKindAreNumbered() {
        List<KitArrange.Thing> t = warrior();
        List<String> keys = KitArrange.numbered(t);
        assertEquals("potion", keys.get(6));
        assertEquals("potion#2", keys.get(7));
    }

    @Test
    void cleaningDropsCrateItemsBelowTheHotbarAndDoubleSlots() {
        List<KitArrange.Thing> t = warrior();
        Map<String, Integer> sent = new java.util.HashMap<>();
        sent.put("sword", 0);
        sent.put("beef", 0);
        sent.put("spear", 12);
        sent.put("banner", 3);
        sent.put("unknown", 5);
        Map<String, Integer> out = KitArrange.clean(t, sent);
        assertEquals(0, out.get("sword"));
        assertFalse(out.containsKey("beef"), "slot 0 is taken");
        assertFalse(out.containsKey("spear"), "a crate item may not sit in the inventory");
        assertEquals(3, out.get("banner"));
        assertFalse(out.containsKey("unknown"));
    }
}
