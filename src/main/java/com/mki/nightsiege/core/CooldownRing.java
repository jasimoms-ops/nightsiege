package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Tor 3.3 (Simon 08.11.: "Ring + Sekunden"): the pause of an ability as a blocky ring around its box.
 * The ring is the box's outermost pixel row; the part still to wait is lit, clockwise from the top
 * middle, and shrinks back to it as the pause runs out. Plain arithmetic, so the tests can check it.
 */
public final class CooldownRing {

    private CooldownRing() {
    }

    /** Pixels in the ring of a box {@code size} wide. */
    public static int perimeter(int size) {
        return size <= 0 ? 0 : size == 1 ? 1 : 4 * size - 4;
    }

    /** Ring pixels to light for {@code share} (0 to 1) of the pause left; a pause running shows at least one. */
    public static int lit(int size, double share) {
        if (!(share > 0.0)) {
            return 0;
        }
        int p = perimeter(size);
        return Math.max(1, Math.min(p, (int) Math.ceil(share * p)));
    }

    /** The {@code i}-th ring pixel (relative {x, y}), counted clockwise from the top middle. */
    public static int[] cell(int size, int i) {
        int p = perimeter(size);
        if (p <= 1) {
            return new int[]{0, 0};
        }
        int k = Math.floorMod(i + size / 2, p);   // from the top left corner, clockwise
        int n = size - 1;
        if (k < n) {
            return new int[]{k, 0};
        }
        k -= n;
        if (k < n) {
            return new int[]{n, k};
        }
        k -= n;
        if (k < n) {
            return new int[]{n - k, n};
        }
        k -= n;
        return new int[]{0, n - k};
    }

    /**
     * Rectangles {x0, y0, x1, y1} (ends exclusive) covering the lit part of the ring of a box at
     * {@code x, y}; neighbouring pixels on one side are joined, so a box needs at most five.
     */
    public static List<int[]> runs(int x, int y, int size, double share) {
        List<int[]> out = new ArrayList<>();
        int count = lit(size, share);
        int[] run = null;
        for (int i = 0; i < count; i++) {
            int[] c = cell(size, i);
            int cx = x + c[0];
            int cy = y + c[1];
            if (run != null && joins(run, cx, cy)) {
                run[0] = Math.min(run[0], cx);
                run[1] = Math.min(run[1], cy);
                run[2] = Math.max(run[2], cx + 1);
                run[3] = Math.max(run[3], cy + 1);
                continue;
            }
            run = new int[]{cx, cy, cx + 1, cy + 1};
            out.add(run);
        }
        return out;
    }

    private static boolean joins(int[] run, int cx, int cy) {
        boolean row = run[3] - run[1] == 1 && cy == run[1] && (cx == run[2] || cx == run[0] - 1);
        boolean col = run[2] - run[0] == 1 && cx == run[0] && (cy == run[3] || cy == run[1] - 1);
        return row || col;
    }
}
