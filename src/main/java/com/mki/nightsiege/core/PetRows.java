package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Paket 11.11. Z2 (Simon 12.11.: "der Wolfbändiger soll jeden Wolf einzeln in der Leiste sehen mit LP-Anzeige"):
 * one row per companion left of the action bar - head, health bar (green, yellow, red) and "14/20"; the alpha
 * in gold, the fallen grey with the pause until the next call. Server and client share the data, the order and
 * the placing; pure arithmetic in GUI pixels so JUnit can check that nothing runs into the chat or the voice chat.
 */
public final class PetRows {

    /** At most this many rows; the rest is a "+N". */
    public static final int MAX_ROWS = 8;
    /** Rows in one column: four of them stay in the bar's height, below the chat's last line. */
    public static final int PER_COL = 4;
    public static final int ROW_H = 9;
    /** Head, gap and the health bar before the number. */
    public static final int TEXT_X = 30;
    /** Between the rows and the bar's left end, and between two columns. */
    public static final int GAP = 4;
    public static final int COL_GAP = 3;
    /** Kept free at the left edge (Simple Voice Chat's microphone icon). */
    public static final int LEFT_FREE = 22;
    /** Ticks between two looks at the companions' life (half a second). */
    public static final int EVERY = 10;
    /** Ticks after which the same rows go out again (a lost packet heals itself). */
    public static final int REFRESH = 200;
    /** How long a fallen companion without a pause stays as a grey row (one minute). */
    public static final int FALLEN_SHOWN = 1200;

    private static final List<String> KINDS = List.of("wolf", "spirit", "golem", "titan", "snow", "skeleton", "ghost",
            "minion", "mount", "hawk");

    private PetRows() {
    }

    /** What the server sends ("pets" screen payload). */
    public static class Pets {
        public List<Pet> pets = new ArrayList<>();
    }

    /** One companion. */
    public static class Pet {
        /** Entity type id ("minecraft:wolf") for the head. */
        public String type = "";
        /** Item id drawn when there is no head for the type (its spawn egg). */
        public String icon = "";
        /** wolf, golem, snow, skeleton, spirit, titan, minion, ghost, mount, hawk. */
        public String kind = "";
        public float hp;
        public float max;
        public boolean alpha;
        public boolean dead;
        /** Ticks until it can be called again (dead ones), 0 for none. */
        public int waitTicks;
        /** Called in this order (game time), so the rows do not jump. */
        public long born;
    }

    /** Wolves first (the alpha on top), then golems and the rest; within a kind the living before the fallen. */
    public static List<Pet> sorted(List<Pet> pets) {
        List<Pet> out = new ArrayList<>(pets);
        out.sort(Comparator.comparingInt((Pet p) -> kindOrder(p.kind))
                .thenComparing(p -> p.dead)
                .thenComparing(p -> !p.alpha)
                .thenComparingLong(p -> p.born));
        return out;
    }

    private static int kindOrder(String kind) {
        int i = KINDS.indexOf(kind == null ? "" : kind);
        return i < 0 ? KINDS.size() : i;
    }

    /** Green above half, yellow above a quarter, red below. */
    public static int color(float share) {
        return share > 0.5f ? 0xFF5FC04A : share > 0.25f ? 0xFFE0B040 : 0xFFD8443A;
    }

    public static float share(Pet p) {
        return p.dead || p.max <= 0f ? 0f : Math.max(0f, Math.min(1f, p.hp / p.max));
    }

    /** "14/20" (health rounded up: "1" until it is really gone); fallen: the seconds until the call, else "0/20". */
    public static String number(Pet p, long waitLeft) {
        if (p.dead && waitLeft > 0) {
            return HudSync.seconds(waitLeft) + "s";
        }
        int hp = p.dead ? 0 : (int) Math.ceil(Math.max(0f, p.hp) - 1.0e-4f);
        return hp + "/" + Math.max(1, Math.round(p.max));
    }

    /** Where the rows go: one rect per shown row, the "+N" tag (null without one) and the whole area. */
    public record Place(List<ActionBarLayout.Rect> rows, ActionBarLayout.Rect more, ActionBarLayout.Rect all,
                        float scale) {
    }

    /**
     * The rows left of the bar's left end {@code barLeft}, bottom at {@code bottom} (the screen's bottom).
     * Up to four rows a column, the first column next to the bar, all within the bar's height (below the chat);
     * if two columns do not fit, they shrink (to 0.6); if even that does not fit, one column goes up (the chat
     * moves up over it). {@code rowW} is the row's width (head, bar, widest number), {@code moreW} the tag's.
     */
    public static Place place(int barLeft, int bottom, int count, int rowW, int moreW) {
        int shown = Math.max(0, Math.min(MAX_ROWS, count));
        if (shown == 0) {
            return new Place(List.of(), null, null, 1f);
        }
        int room = barLeft - GAP - LEFT_FREE;
        int cols = (shown + PER_COL - 1) / PER_COL;
        int gridW = cols * rowW + (cols - 1) * COL_GAP;
        float scale = Math.min(1f, room / (float) gridW);
        boolean grid = scale >= 0.6f;
        if (!grid) {
            scale = Math.max(0.5f, Math.min(1f, room / (float) rowW));
        }
        int w = Math.max(1, Math.round(rowW * scale));
        int h = Math.max(1, Math.round(ROW_H * scale));
        int gap = Math.round(COL_GAP * scale);
        int right = barLeft - GAP;
        List<ActionBarLayout.Rect> rows = new ArrayList<>();
        int perCol = grid ? PER_COL : MAX_ROWS;
        int inFirst = Math.min(perCol, shown);
        int top = bottom - 1 - inFirst * h;
        for (int i = 0; i < shown; i++) {
            int col = i / perCol;
            int row = i % perCol;
            rows.add(new ActionBarLayout.Rect(Math.max(0, right - (col + 1) * w - col * gap), top + row * h, w, h));
        }
        ActionBarLayout.Rect more = null;
        if (count > shown) {
            int mw = Math.max(1, Math.round(moreW * scale));
            more = new ActionBarLayout.Rect(right - mw, top - h - 1, mw, h);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        List<ActionBarLayout.Rect> parts = new ArrayList<>(rows);
        if (more != null) {
            parts.add(more);
        }
        for (ActionBarLayout.Rect r : parts) {
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.right());
            maxY = Math.max(maxY, r.bottom());
        }
        return new Place(List.copyOf(rows), more, new ActionBarLayout.Rect(minX, minY, maxX - minX, maxY - minY), scale);
    }
}
