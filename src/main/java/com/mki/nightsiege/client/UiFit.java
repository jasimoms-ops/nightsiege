package com.mki.nightsiege.client;

/**
 * Fitting texts and rows of buttons into a width (Paket 13.11b): first shorter labels, then smaller text, never
 * one thing over another. Pure arithmetic (no Minecraft classes), so JUnit can check it.
 */
public final class UiFit {

    /** Labels and button texts get at most this much smaller before they are cut. */
    public static final float MIN_SCALE = 0.6f;
    /** A button's room beside its text when there is space (the look before 13.11b) … */
    public static final int PAD = 12;
    /** … and when it is tight. PurpleUi.button keeps 3 px each side. */
    public static final int PAD_TIGHT = 8;

    private UiFit() {
    }

    /** The scale (at most 1) at which a text {@code textW} wide fits into {@code maxW}. */
    public static float scaleFor(int textW, int maxW) {
        if (textW <= maxW) {
            return 1f;
        }
        return maxW <= 0 ? 0f : maxW / (float) textW;
    }

    /** Where the chapter line of the core window goes: beside the level, beside it smaller, or a line below. */
    public enum Place { BESIDE, BESIDE_SMALL, BELOW }

    /** The chapter line ({@code textW}) right of the level ({@code levelW}) in a row {@code rowW} wide, gap 6. */
    public static Place beside(int levelW, int textW, int rowW) {
        int room = rowW - levelW - 6;
        float s = scaleFor(textW, room);
        return s >= 1f ? Place.BESIDE : s >= 0.75f ? Place.BESIDE_SMALL : Place.BELOW;
    }

    /** A laid-out row: the width of each button and whether it shows its short label. */
    public record Row(int[] widths, boolean[] shortUsed) {
        public int total(int gap) {
            int sum = 0;
            for (int w : widths) {
                sum += w;
            }
            return sum + gap * Math.max(0, widths.length - 1);
        }
    }

    /**
     * Widths for a row of buttons in {@code avail} pixels with {@code gap} between them.
     * {@code full}/{@code shortW} are the text widths of the long and short label, {@code min} the wished
     * button widths, {@code order} the buttons that switch to their short label first. Steps: wished widths;
     * text width + PAD; short labels one by one; text + PAD_TIGHT; at last every text smaller alike (the
     * button draws it shrunk). The sum never exceeds {@code avail} (unless even 1 px each does not fit).
     */
    public static Row row(int[] full, int[] shortW, int[] min, int[] order, int gap, int avail) {
        int n = full.length;
        int room = avail - gap * Math.max(0, n - 1);
        boolean[] useShort = new boolean[n];
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(min[i], full[i] + PAD);
        }
        if (sum(w) <= room) {
            return new Row(w, useShort);
        }
        int[] text = full.clone();
        if (fits(text, PAD, room)) {
            return grow(text, PAD, min, room, useShort);
        }
        for (int i : order) {
            if (shortW[i] < text[i]) {
                text[i] = shortW[i];
                useShort[i] = true;
                if (fits(text, PAD, room)) {
                    return grow(text, PAD, min, room, useShort);
                }
            }
        }
        if (fits(text, PAD_TIGHT, room)) {
            return grow(text, PAD_TIGHT, min, room, useShort);
        }
        // Every text smaller by the same factor.
        int textSum = sum(text);
        float s = textSum <= 0 ? 1f : Math.max(0f, (room - n * PAD_TIGHT) / (float) textSum);
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(1, (int) Math.floor(text[i] * s) + PAD_TIGHT);
        }
        // Rounding up to 1 px may still overflow on absurd widths: take it from the widest.
        while (sum(w) > room && max(w) > 1) {
            int k = indexOfMax(w);
            w[k]--;
        }
        return new Row(w, useShort);
    }

    private static boolean fits(int[] text, int pad, int room) {
        return sum(text) + pad * text.length <= room;
    }

    /** Text + pad, then the space left goes to the buttons below their wished width, left to right. */
    private static Row grow(int[] text, int pad, int[] min, int room, boolean[] useShort) {
        int n = text.length;
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = text[i] + pad;
        }
        int left = room - sum(w);
        for (int i = 0; i < n && left > 0; i++) {
            int add = Math.min(left, Math.max(0, min[i] - w[i]));
            w[i] += add;
            left -= add;
        }
        return new Row(w, useShort);
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) {
            s += v;
        }
        return s;
    }

    private static int max(int[] a) {
        return a[indexOfMax(a)];
    }

    private static int indexOfMax(int[] a) {
        int k = 0;
        for (int i = 1; i < a.length; i++) {
            if (a[i] > a[k]) {
                k = i;
            }
        }
        return k;
    }
}
