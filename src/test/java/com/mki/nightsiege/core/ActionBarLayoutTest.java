package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 3.2c: the action bar in key order, nothing overlapping at 1080p with GUI 2 and 3, place 3 locked without "Dritte Hand". */
class ActionBarLayoutTest {

    /** 1920x1080 at GUI 2 and 3. */
    private static final int[][] SCREENS = {{960, 540}, {640, 360}};

    private static List<ActionBarLayout.Input> inputs() {
        return List.of(
                // Klingenmeister: Q E F, X R, gauge, 2 gains
                new ActionBarLayout.Input(new boolean[]{true, true, true, false}, 0, true, 2, true, 0, false),
                // Sanitäter: Q E F, timers, 3 gains, off hand
                new ActionBarLayout.Input(new boolean[]{true, true, true, false}, 2, true, 3, true, 0, true),
                // Totenbeschwörer: Q E F C, X R, souls, four kinds of companions
                new ActionBarLayout.Input(new boolean[]{true, true, true, true}, 0, true, 3, true, 4, true),
                // Elektriker: Q E F C, timers 4, everything at once
                new ActionBarLayout.Input(new boolean[]{true, true, true, true}, 4, true, 3, true, 4, true));
    }

    @Test
    void fieldsInKeyOrder() {
        for (ActionBarLayout.Input in : inputs()) {
            ActionBarLayout.Layout l = ActionBarLayout.layout(960, 540, in);
            int lastOrder = -1;
            int lastRight = Integer.MIN_VALUE;
            for (ActionBarLayout.Field f : l.fields()) {
                assertTrue(f.at().x() >= lastRight, f.key() + " starts after the field before it");
                lastRight = f.at().right();
                if (f.kind() == ActionBarLayout.Kind.TIMER) {
                    continue;
                }
                int order = ActionBarLayout.ORDER.indexOf(f.key());
                assertTrue(order > lastOrder, f.key() + " comes after the key before it");
                lastOrder = order;
            }
            assertNotNull(l.field("X"));
            assertEquals(in.places()[3], l.field("C") != null);
            for (int i = 1; i <= 9; i++) {
                assertNotNull(l.field(String.valueOf(i)), "hotbar place " + i);
            }
            assertEquals(ActionBarLayout.Kind.HOTBAR, l.field("4").kind(), "weapons from place 4 on");
            // the timers sit between F/C and X
            for (ActionBarLayout.Field f : l.fields()) {
                if (f.kind() == ActionBarLayout.Kind.TIMER) {
                    assertTrue(f.at().x() > l.field("F").at().x() && f.at().right() < l.field("X").at().x());
                }
            }
        }
    }

    @Test
    void placeThreeLockedWithoutThirdHand() {
        ActionBarLayout.Layout two = ActionBarLayout.layout(960, 540, inputs().get(0));
        assertEquals(ActionBarLayout.Kind.GAIN, two.field("1").kind());
        assertEquals(ActionBarLayout.Kind.GAIN, two.field("2").kind());
        assertEquals(ActionBarLayout.Kind.LOCKED, two.field("3").kind());
        ActionBarLayout.Layout three = ActionBarLayout.layout(960, 540, inputs().get(1));
        assertEquals(ActionBarLayout.Kind.GAIN, three.field("3").kind());
    }

    @Test
    void nothingOverlapsAt1080p() {
        for (int[] s : SCREENS) {
            int w = s[0];
            int h = s[1];
            for (ActionBarLayout.Input in : inputs()) {
                ActionBarLayout.Layout l = ActionBarLayout.layout(w, h, in);
                String at = "gui " + (1920 / w) + " " + in;
                assertEquals(1f, l.scale(), at + ": full size");
                // fields: inside the bar, not on each other
                List<ActionBarLayout.Rect> rects = new ArrayList<>();
                for (ActionBarLayout.Field f : l.fields()) {
                    ActionBarLayout.Rect r = l.screen(f.at());
                    for (ActionBarLayout.Rect o : rects) {
                        assertFalse(r.overlaps(o), at + ": " + f.key() + " overlaps");
                    }
                    rects.add(r);
                    ActionBarLayout.Rect bar = l.barOnScreen();
                    assertTrue(r.x() >= bar.x() && r.right() <= bar.right() && r.y() >= bar.y() && r.bottom() <= bar.bottom(),
                            at + ": " + f.key() + " inside the bar");
                }
                // status row parts apart
                List<ActionBarLayout.Rect> status = new ArrayList<>(List.of(l.message(), l.health(), l.minis()));
                if (l.gauge() != null) {
                    status.add(l.gauge());
                }
                for (int i = 0; i < status.size(); i++) {
                    for (int j = i + 1; j < status.size(); j++) {
                        assertFalse(status.get(i).overlaps(status.get(j)), at + ": status parts " + i + "/" + j);
                    }
                }
                assertTrue(l.message().w() >= 80, at + ": message room " + l.message().w());
                // the bar against chat, party list, voice chat, night bar, Jade, bosses and effects
                int chatRight = ActionBarLayout.chatRight(1.0, 1.0);
                ActionBarLayout.Rect chat = ActionBarLayout.chat(h, chatRight, 10);
                ActionBarLayout.Rect party = ActionBarLayout.party(h, HudSync.PARTY_MAX);
                BossBarLayout.Corner corner = BossBarLayout.corner(w, h, 2, 1, 1, false);
                BossBarLayout.Rect cp = corner.panel();
                List<ActionBarLayout.Rect> others = new ArrayList<>(List.of(chat, party, ActionBarLayout.nightBar(w),
                        ActionBarLayout.jade(w), new ActionBarLayout.Rect(cp.x(), cp.y(), cp.w(), cp.h())));
                others.addAll(ActionBarLayout.voiceChat(h));
                for (BossBarLayout.Rect e : BossBarLayout.effectIcons(w, 1, 1, false)) {
                    others.add(new ActionBarLayout.Rect(e.x(), e.y(), e.w(), e.h()));
                }
                assertEquals(0, ActionBarLayout.chatLift(l.parts(), h, chatRight), at + ": chat stays where it is");
                for (ActionBarLayout.Rect part : l.parts()) {
                    for (ActionBarLayout.Rect o : others) {
                        assertFalse(part.overlaps(o), at + ": bar part " + part + " overlaps " + o);
                    }
                }
                // the fixed places themselves stay apart
                for (int i = 0; i < others.size(); i++) {
                    for (int j = i + 1; j < others.size(); j++) {
                        assertFalse(others.get(i).overlaps(others.get(j)), at + ": " + others.get(i) + " / " + others.get(j));
                    }
                }
                assertTrue(party.x() < w / 3, at + ": party list on the left");
            }
        }
    }
}
