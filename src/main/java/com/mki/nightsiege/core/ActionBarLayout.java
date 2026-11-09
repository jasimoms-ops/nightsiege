package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Tor 3.2c (Simon 09.11., draft approved by the chief 03:30): the kit HUD as one action bar at the bottom
 * middle, in the order of the keys: Q E F (C) · X R · 1 2 3 (the gains on hotbar places 1-3) · 4-9 (the
 * weapons). Above it a slim status row: the last message on the left, health, the class gauge and the small
 * hunger and armour lines on the right. Fixed places for the rest: the night bar top middle, the bosses top
 * right, the healers' party list left middle. Pure arithmetic in GUI pixels, so JUnit can check that nothing
 * overlaps; the client draws what this returns.
 */
public final class ActionBarLayout {

    /** An ability field (Q, E, F, C, X, R): 22 square, 23 apart. */
    public static final int ABIL = 22;
    public static final int ABIL_STEP = 23;
    /** A pause without a key of its own (last stand, ...): small, right of the ability group. */
    public static final int TIMER = 13;
    public static final int TIMER_STEP = 14;
    /** The hotbar places (1-3 the gains, 4-9 the weapons): 20 square, side by side. */
    public static final int SLOT = 20;
    /** Gap between two groups, a gold stud in its middle. */
    public static final int SEP = 9;
    public static final int PAD = 4;
    /** The bar's height and the status row above it (the chat's last line ends 40 above the bottom). */
    public static final int BAR_H = 28;
    public static final int STATUS_H = 9;
    public static final int UNIT = 11;
    public static final int UNIT_STEP = 13;
    /** The status row's parts, right to left: hunger/armour lines, health, the class gauge. */
    public static final int MINI_W = 26;
    public static final int HEALTH_W = 82;
    public static final int GAUGE_W = 54;
    /** The healers' party list (PartyHealthHud): width, row height. */
    public static final int PARTY_W = 78;
    public static final int PARTY_ROW = 11;

    public enum Kind { ABILITY, TIMER, ULT, GAIN, LOCKED, HOTBAR, OFFHAND }

    /** A rectangle in GUI pixels. */
    public record Rect(int x, int y, int w, int h) {
        public boolean overlaps(Rect o) {
            return w > 0 && h > 0 && o.w > 0 && o.h > 0
                    && x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    /**
     * One field: {@code key} is its name in the bar's order ("Q", "E", "F", "C", "T0".., "X", "R", "1".."9", "OFF"),
     * {@code index} the box of its list (ability place 0-3, timer, gain place, hotbar place 0-8), {@code at} local.
     */
    public record Field(Kind kind, String key, int index, Rect at) {
    }

    /**
     * What the bar shows: {@code places} which of Q E F C have an ability (Q E F are always drawn, C only
     * when used), {@code timers} pauses without a key, {@code ult2} the second ult on R, {@code gainPlaces}
     * 2 or 3 (place 3 needs the gain "Dritte Hand", grey with a lock without it), {@code gauge} a class gauge,
     * {@code units} companions or towers (0-4), {@code offhand} something in the off hand.
     */
    public record Input(boolean[] places, int timers, boolean ult2, int gainPlaces, boolean gauge, int units,
                        boolean offhand) {
    }

    /**
     * The bar on screen: {@code left}, {@code top} the bar's top left corner, {@code scale} below 1 on very
     * narrow screens; every rect inside is local (relative to left/top, unscaled).
     */
    public record Layout(int left, int top, float scale, int width, List<Field> fields, List<Integer> studs,
                         Rect status, Rect message, Rect gauge, Rect health, Rect minis, Rect units) {

        /** A local rect on the screen. */
        public Rect screen(Rect r) {
            return new Rect(left + Math.round(r.x * scale), top + Math.round(r.y * scale),
                    Math.round(r.w * scale), Math.round(r.h * scale));
        }

        /** The bar's frame on the screen. */
        public Rect barOnScreen() {
            return screen(new Rect(0, 0, width, BAR_H));
        }

        /** Everything the bar takes on the screen: frame, status row, unit row. */
        public List<Rect> parts() {
            List<Rect> out = new ArrayList<>();
            out.add(barOnScreen());
            out.add(screen(status));
            if (units != null) {
                out.add(screen(units));
            }
            return out;
        }

        /** The field of that key, or null. */
        public Field field(String key) {
            for (Field f : fields) {
                if (f.key.equals(key)) {
                    return f;
                }
            }
            return null;
        }
    }

    /** The bar's keys in their order (C only with an ability there, R only with a second ult). */
    public static final List<String> ORDER = List.of("Q", "E", "F", "C", "X", "R", "1", "2", "3", "4", "5", "6", "7", "8", "9", "OFF");

    private ActionBarLayout() {
    }

    public static Layout layout(int guiW, int guiH, Input in) {
        List<Field> fields = new ArrayList<>();
        List<Integer> studs = new ArrayList<>();
        int abilY = (BAR_H - ABIL) / 2;
        int slotY = (BAR_H - SLOT) / 2;
        int x = PAD;
        String[] letters = {"Q", "E", "F", "C"};
        for (int i = 0; i < 4; i++) {
            if (i == 3 && (in.places.length < 4 || !in.places[3])) {
                continue;
            }
            fields.add(new Field(Kind.ABILITY, letters[i], i, new Rect(x, abilY, ABIL, ABIL)));
            x += ABIL_STEP;
        }
        x -= ABIL_STEP - ABIL;
        int timers = Math.max(0, Math.min(4, in.timers));
        if (timers > 0) {
            x += 2;
            for (int i = 0; i < timers; i++) {
                fields.add(new Field(Kind.TIMER, "T" + i, i, new Rect(x, (BAR_H - TIMER) / 2, TIMER, TIMER)));
                x += TIMER_STEP;
            }
            x -= TIMER_STEP - TIMER;
        }
        studs.add(x + SEP / 2);
        x += SEP;
        fields.add(new Field(Kind.ULT, "X", 0, new Rect(x, abilY, ABIL, ABIL)));
        x += ABIL;
        if (in.ult2) {
            x += ABIL_STEP - ABIL;
            fields.add(new Field(Kind.ULT, "R", 1, new Rect(x, abilY, ABIL, ABIL)));
            x += ABIL;
        }
        studs.add(x + SEP / 2);
        x += SEP;
        for (int i = 0; i < 3; i++) {
            Kind kind = i < Math.max(0, in.gainPlaces) ? Kind.GAIN : Kind.LOCKED;
            fields.add(new Field(kind, String.valueOf(i + 1), i, new Rect(x, slotY, SLOT, SLOT)));
            x += SLOT;
        }
        for (int i = 3; i < 9; i++) {
            fields.add(new Field(Kind.HOTBAR, String.valueOf(i + 1), i, new Rect(x, slotY, SLOT, SLOT)));
            x += SLOT;
        }
        if (in.offhand) {
            x += 4;
            fields.add(new Field(Kind.OFFHAND, "OFF", 0, new Rect(x, slotY, SLOT, SLOT)));
            x += SLOT;
        }
        int width = x + PAD;

        // the status row above the bar: message | gauge | health | hunger and armour
        int sy = -STATUS_H - 1;
        Rect status = new Rect(PAD, sy, width - 2 * PAD, STATUS_H);
        int r = width - PAD;
        Rect minis = new Rect(r - MINI_W, sy + 1, MINI_W, STATUS_H - 2);
        r -= MINI_W + 3;
        Rect health = new Rect(r - HEALTH_W, sy + 1, HEALTH_W, 7);
        r -= HEALTH_W + 3;
        Rect gauge = null;
        if (in.gauge) {
            gauge = new Rect(r - GAUGE_W, sy + 1, GAUGE_W, 7);
            r -= GAUGE_W + 3;
        }
        Rect message = new Rect(PAD, sy, Math.max(0, r - PAD), STATUS_H);
        Rect units = null;
        int n = Math.max(0, Math.min(4, in.units));
        if (n > 0) {
            int uw = n * UNIT_STEP - (UNIT_STEP - UNIT);
            units = new Rect(width - PAD - uw, sy - UNIT - 2, uw, UNIT);
        }

        float scale = Math.min(1f, (guiW - 4) / (float) width);
        int left = (guiW - Math.round(width * scale)) / 2;
        int top = guiH - Math.round(BAR_H * scale);
        return new Layout(left, top, scale, width, List.copyOf(fields), List.copyOf(studs), status, message, gauge,
                health, minis, units);
    }

    /** The chat when closed: {@code lines} lines up to 40 px above the bottom, as wide as the chat setting allows. */
    public static Rect chat(int guiH, int chatRight, int lines) {
        return new Rect(0, guiH - 40 - lines * 9, chatRight, lines * 9);
    }

    /** The chat's right edge (as vanilla computes it, chatWidth 0-1, scale 0-1). */
    public static int chatRight(double chatWidth, double chatScale) {
        return 4 + (int) Math.ceil((Math.floor(chatWidth * 280.0 + 40.0) + 8) * chatScale);
    }

    /** How far the chat has to go up so that its last line stays above every part of the bar it would touch. */
    public static int chatLift(List<Rect> parts, int guiH, int chatRight) {
        int lift = 0;
        for (Rect p : parts) {
            if (p.x < chatRight) {
                lift = Math.max(lift, (guiH - 40) - p.y + 2);
            }
        }
        return lift;
    }

    /** The healers' party list: left middle (Tor 3.2c; before it was right middle, under the boss list). */
    public static Rect party(int guiH, int rows) {
        int h = 9 + rows * PARTY_ROW + 2;
        return new Rect(3, Math.max(24, guiH / 2 - h / 2 - 20), PARTY_W, h);
    }

    /** The night bar at the top middle (vanilla's first boss bar, 182 wide). */
    public static Rect nightBar(int guiW) {
        return new Rect(guiW / 2 - 91, 3, 182, 16);
    }

    /** Where Jade's box usually sits: top middle, pushed below the night bar (Jade moves under boss bars itself). */
    public static Rect jade(int guiW) {
        return new Rect(guiW / 2 - 80, 3 + 24, 160, 34);
    }

    /** Simple Voice Chat: the group list top left and the microphone icon bottom left (default places). */
    public static List<Rect> voiceChat(int guiH) {
        return List.of(new Rect(2, 2, 60, 12), new Rect(4, guiH - 20, 16, 16));
    }
}
