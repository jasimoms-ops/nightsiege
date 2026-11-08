package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 2 (08.11.): the HUD's pauses and the healers' party list. */
class HudSyncTest {

    @Test
    void pauseLeftIsExact() {
        assertEquals(160, HudSync.left(1160, 1000));
        assertEquals(0, HudSync.left(1000, 1000));
        assertEquals(0, HudSync.left(900, 1000));      // over: never negative
        assertEquals(0, HudSync.left(0, 5_000_000));   // never set
        assertEquals(Integer.MAX_VALUE, HudSync.left(Long.MAX_VALUE, 0));
    }

    @Test
    void clientRunsThePauseDown() {
        assertEquals(160, HudSync.shown(160, 0));
        assertEquals(60, HudSync.shown(160, 100));
        assertEquals(0, HudSync.shown(160, 400));
        assertEquals(160, HudSync.shown(160, -5));     // a clock step back changes nothing
        assertEquals(8, HudSync.seconds(160));
        assertEquals(1, HudSync.seconds(1));            // "1" until the very end
        assertEquals(0, HudSync.seconds(0));
        assertEquals(0.5, HudSync.shade(80, 160), 1e-9);
        assertEquals(1.0, HudSync.shade(500, 160), 1e-9);   // creative's short pause: never above full
        assertEquals(0.0, HudSync.shade(80, 0), 1e-9);      // no total: no shade
    }

    @Test
    void partyHoldsOnlyTheOtherDefendersEachOnce() {
        List<HudSync.Mate> all = new ArrayList<>();
        all.add(new HudSync.Mate("me", "Medic", 20, 20, 0));
        all.add(new HudSync.Mate("a", "Anna", 18, 20, 0));
        all.add(new HudSync.Mate("b", "Ben", 4, 20, 0));
        all.add(new HudSync.Mate("a", "Anna", 18, 20, 0));   // twice
        all.add(null);
        all.add(new HudSync.Mate("", "nobody", 1, 20, 0));
        List<HudSync.Mate> out = HudSync.party("me", all, HudSync.PARTY_MAX);
        assertEquals(2, out.size());
        assertEquals("b", out.get(0).id);   // the worst off first
        assertEquals("a", out.get(1).id);
        assertTrue(out.stream().noneMatch(m -> m.id.equals("me")));
    }

    @Test
    void partyIsCappedAndDropsTheHealthiest() {
        List<HudSync.Mate> all = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            all.add(new HudSync.Mate("p" + i, "P" + i, i + 1, 20, 0));
        }
        List<HudSync.Mate> out = HudSync.party("x", all, HudSync.PARTY_MAX);
        assertEquals(HudSync.PARTY_MAX, out.size());
        assertEquals("p0", out.get(0).id);
        assertTrue(out.stream().noneMatch(m -> m.id.equals("p14")));
        assertTrue(HudSync.party("x", all, 0).isEmpty());
        assertTrue(HudSync.party("x", null, 5).isEmpty());
    }

    @Test
    void shareAndColours() {
        assertEquals(0f, new HudSync.Mate("a", "A", -3, 20, 0).share(), 1e-6);
        assertEquals(1f, new HudSync.Mate("a", "A", 30, 20, 0).share(), 1e-6);
        assertEquals(0f, new HudSync.Mate("a", "A", 5, 0, 0).share(), 1e-6);
        assertNotEquals(HudSync.barColors(0.9f)[0], HudSync.barColors(0.1f)[0]);
        assertNotEquals(HudSync.barColors(0.5f)[0], HudSync.barColors(0.1f)[0]);
    }

    @Test
    void partyGoesOverTheWireUnchanged() {
        HudSync.Party p = new HudSync.Party();
        p.mates.add(new HudSync.Mate("a", "Anna", 7.5f, 24, 2));
        String json = new com.google.gson.Gson().toJson(p);
        HudSync.Party back = new com.google.gson.Gson().fromJson(json, HudSync.Party.class);
        assertEquals(1, back.mates.size());
        assertEquals("Anna", back.mates.get(0).name);
        assertEquals(7.5f, back.mates.get(0).health, 1e-6);
        assertEquals(2f, back.mates.get(0).absorb, 1e-6);
        assertTrue(json.length() < 120, "small packet: " + json.length());
    }

    /** Tor 3.5: colour by state, hearts as shown, which group a player sees. */
    @Test
    void partyStateHeartsAndGroup() {
        assertEquals(HudSync.State.WELL, HudSync.state(new HudSync.Mate("a", "A", 20f, 20f, 0f)));
        assertEquals(HudSync.State.HURT, HudSync.state(new HudSync.Mate("a", "A", 10f, 20f, 0f)));
        assertEquals(HudSync.State.LOW, HudSync.state(new HudSync.Mate("a", "A", 5f, 20f, 0f)));
        assertEquals(HudSync.State.DOWN, HudSync.state(new HudSync.Mate("a", "A", 0f, 20f, 4f)));
        assertEquals(HudSync.State.WELL, HudSync.state(new HudSync.Mate("a", "A", 13f, 20f, 0f)));   // 65 %
        assertEquals(HudSync.State.HURT, HudSync.state(new HudSync.Mate("a", "A", 12f, 20f, 0f)));   // 60 %: not well
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (HudSync.State st : HudSync.State.values()) {
            colors.add(HudSync.stateColor(st));
        }
        assertEquals(4, colors.size(), "four distinct colours");
        assertEquals("10", HudSync.hearts(20f));
        assertEquals("7.5", HudSync.hearts(15f));
        assertEquals("7.5", HudSync.hearts(14.2f));   // a scratch still shows the half heart
        assertEquals("0.5", HudSync.hearts(0.3f));
        assertEquals("0", HudSync.hearts(0f));
        List<String> siege = List.of("a", "b");
        List<String> camp = List.of("c", "d");
        List<String> dungeon = List.of("e");
        assertSame(siege, HudSync.groupOf("a", siege, camp, dungeon));
        assertSame(camp, HudSync.groupOf("d", siege, camp, dungeon));
        assertSame(dungeon, HudSync.groupOf("e", siege, camp, dungeon));
        assertNull(HudSync.groupOf("z", siege, camp, dungeon));
        assertNull(HudSync.groupOf("a", null, null, null));
    }
}
