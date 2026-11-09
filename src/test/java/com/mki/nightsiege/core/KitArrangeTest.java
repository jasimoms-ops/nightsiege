package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The kit page's arrangement (update 6): crate items only in the hotbar, as many as fit. */
class KitArrangeTest {

    @Test
    void nightHeldNeverOnGainPlaces() {
        assertEquals(5, KitArrange.nightHeld(5, 4));
        assertEquals(3, KitArrange.nightHeld(0, 0), "a choice on place 1 goes to the first weapon");
        assertEquals(8, KitArrange.nightHeld(2, 3), "the wheel back from place 4 goes round to 9");
        assertEquals(3, KitArrange.nightHeld(0, 8), "the wheel on from place 9 goes round to 4");
        for (int s = 0; s < KitArrange.HOTBAR; s++) {
            for (int b = 0; b < KitArrange.HOTBAR; b++) {
                assertTrue(KitArrange.nightHeld(s, b) >= KitArrange.FIRST);
            }
        }
    }

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
        t.add(crate("d", "uncommon", 1));
        int[] s = KitArrange.place(t, null, null);
        assertTrue(s[8] >= 0 && s[8] < 9, "the epic one comes");
        assertTrue(s[9] >= 0 && s[9] < 9, "the rare one comes");
        assertTrue(s[10] >= 0 && s[10] < 9, "the uncommon one comes");
        assertEquals(-1, s[7], "the common one stays at home: no hotbar place left");
        for (int i = 0; i < 7; i++) {
            assertTrue(s[i] >= KitArrange.FIRST, "own things not on the gain places 1-3: " + i);
        }
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
        for (int i = 3; i < 9; i++) {
            t.add(own("thing" + i));
            layout.put("thing" + i, i);
        }
        for (int i = 0; i < 3; i++) {
            t.add(crate("c" + i, "gain" + i, 1));
            layout.put("c" + i, i);
        }
        t.add(crate("spear", "wurfspeere", 2));
        int[] s = KitArrange.place(t, layout, List.of("gain0", "gain1", "gain2", "wurfspeere"));
        assertEquals(-1, s[9]);
    }

    /** Tor 3.2b: a weapon kept on place 1-3 (an older layout) moves to place 4 or later. */
    @Test
    void weaponsLeaveTheGainPlaces() {
        List<KitArrange.Thing> t = warrior();
        int[] s = KitArrange.place(t, Map.of("sword", 0, "beef", 2), null);
        assertTrue(s[0] >= KitArrange.FIRST && s[0] < KitArrange.HOTBAR, "sword at " + s[0]);
        assertTrue(s[1] >= KitArrange.FIRST && s[1] < KitArrange.HOTBAR, "beef at " + s[1]);
        int[] auto = KitArrange.place(t, null, null);
        assertTrue(auto[0] >= KitArrange.FIRST, "sword at " + auto[0]);
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
        sent.put("sword", 4);
        sent.put("beef", 4);
        sent.put("cobblestone", 1);
        sent.put("spear", 12);
        sent.put("banner", 3);
        sent.put("unknown", 5);
        Map<String, Integer> out = KitArrange.clean(t, sent);
        assertEquals(4, out.get("sword"));
        assertFalse(out.containsKey("beef"), "slot 4 is taken");
        assertFalse(out.containsKey("cobblestone"), "own things not on the gain places 1-3");
        assertFalse(out.containsKey("spear"), "a crate item may not sit in the inventory");
        assertEquals(3, out.get("banner"));
        assertFalse(out.containsKey("unknown"));
    }
}
