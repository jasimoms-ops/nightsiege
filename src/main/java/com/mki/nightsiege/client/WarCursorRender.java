package com.mki.nightsiege.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tor 10 / K2: draws {@link WarCursor} over the bird's-eye view. Everything is drawn in the screen's
 * layer (after the world, so shader packs cannot hide it) but sized and placed by the world: a ring
 * lies on the ground, wall or roof under the mouse and grows and shrinks with the zoom. Its rim is a
 * white line between two black ones, so it stands out on grass, stone and at night alike.
 */
public final class WarCursorRender {

    static final int WHITE = 0xFFFFFFFF;
    static final int BLACK = 0xFF000000;
    static final int GREEN = 0xFF46E05A;
    static final int RED = 0xFFFF3B3B;
    static final int YELLOW = 0xFFFFD43B;
    static final int GREY = 0xFFA8A8A8;
    private static final int LABEL_BG = 0xD0101018;

    /** World radius of the cross (blocks) for small, normal, large. */
    private static final double[] SIZE = {0.55, 0.8, 1.1};
    /** And its bounds on the screen (GUI pixels), so it stays usable far out and close in. */
    private static final int[] MIN_R = {7, 10, 13};
    private static final int[] MAX_R = {16, 22, 30};

    private WarCursorRender() {
    }

    public static int color(WarCursor.Intent intent) {
        return switch (intent) {
            case MOVE -> GREEN;
            case ATTACK -> RED;
            case SPECIAL -> YELLOW;
            case INVALID -> GREY;
            default -> WHITE;
        };
    }

    /** The cross's radius on the screen (GUI pixels) at the spot it lies on. */
    public static int radius() {
        int size = FxSettings.crossSize();
        double r = SIZE[size] * WarCursor.pixelsPerBlock(WarCursor.worldY());
        return (int) Math.round(Math.max(MIN_R[size], Math.min(MAX_R[size], r)));
    }

    /** The machine's spread round the cross on the screen (GUI pixels, at least 6), 0 without an aiming machine. */
    public static int spreadRadius() {
        double spread = WarCursor.spread();
        if (WarCursor.machine() == null || spread <= 0 || !WarCursor.onGround()) {
            return 0;
        }
        return (int) Math.max(6, Math.round(spread * WarCursor.pixelsPerBlock(WarCursor.worldY())));
    }

    public static void draw(GuiGraphicsExtractor gui, Font font) {
        if (!WarCursor.shown()) {
            return;
        }
        WarCursor.Intent intent = WarCursor.intent();
        int color = color(intent);
        int cx = (int) Math.round(WarCursor.screenX());
        int cy = (int) Math.round(WarCursor.screenY());
        // With an aiming machine the ring is its spread (where the shots land, at every zoom), else the cross size.
        int r = spreadRadius() > 0 ? spreadRadius() : radius();
        // 1. The machine: its reach round it, a dotted line to the cross, the spread round the cross.
        if (WarCursor.machine() != null) {
            drawMachine(gui, font, cx, cy, color);
        }
        // 2. A special order's preview.
        if (WarCursor.preview() != WarCursor.Preview.NONE) {
            drawPreview(gui, font);
        }
        // 3. The unit under the cross: an outline.
        WarCursor.Hover h = WarCursor.hover();
        if (h != null) {
            double ppb = WarCursor.pixelsPerBlock(h.y());
            int half = (int) Math.round(Math.max(r + 3, (h.kind() == WarCursor.Kind.GATE ? 1.6 : 0.8) * ppb));
            int hc = h.kind() == WarCursor.Kind.DEFENDER || h.kind() == WarCursor.Kind.GATE ? RED : h.kind() == WarCursor.Kind.FLAG ? 0xFFE03030 : GREEN;
            corners(gui, cx, cy, half, hc);
        }
        // 4. The cross itself: a ring with a white rim between black lines, ticks and the sign of the intent.
        ring(gui, cx, cy, r + 2, 1, BLACK, false);
        ring(gui, cx, cy, r + 1, 1, WHITE, false);
        ring(gui, cx, cy, r, 2, color, false);
        ring(gui, cx, cy, r - 2, 1, BLACK, false);
        int tick = Math.max(3, r / 3);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x0 = cx + d[0] * (r + 2);
            int y0 = cy + d[1] * (r + 2);
            int x1 = cx + d[0] * (r + 2 + tick);
            int y1 = cy + d[1] * (r + 2 + tick);
            thick(gui, x0, y0, x1, y1, BLACK, 3);
            line(gui, x0, y0, x1, y1, WHITE);
        }
        sign(gui, intent, cx, cy, Math.max(3, Math.min(10, r - 4)), color);
        // 5. The words: the unit's name and life above, what a click does (or why not) below.
        if (h != null) {
            label(gui, font, cx, cy - r - tick - 12, h, color);
        }
        String text = intent == WarCursor.Intent.INVALID ? WarCursor.reason()
                : !WarCursor.reason().isEmpty() ? WarCursor.reason() : WarCursor.action();
        if (WarCursor.machine() != null && intent != WarCursor.Intent.INVALID) {
            text = (text.isEmpty() ? "" : text + " · ") + WarCursor.tr("nightsiege.war.cursor.distance",
                    Math.round(WarCursor.distance()), Math.round(WarCursor.reach()));
        }
        if (!text.isEmpty()) {
            words(gui, font, text, cx, cy + r + tick + 4, color);
        }
    }

    private static void drawMachine(GuiGraphicsExtractor gui, Font font, int cx, int cy, int color) {
        double[] m = WarCursor.project(WarCursor.machineX(), WarCursor.machineY(), WarCursor.machineZ());
        if (m != null) {
            int mx = (int) Math.round(m[0]);
            int my = (int) Math.round(m[1]);
            int reach = (int) Math.round(WarCursor.reach() * WarCursor.pixelsPerBlock(WarCursor.machineY()));
            ring(gui, mx, my, reach + 1, 1, BLACK, true);
            ring(gui, mx, my, reach, 1, 0xFFFFB347, true);
            int steps = Math.max(4, (int) (Math.hypot(cx - mx, cy - my) / 6));
            for (int k = 1; k < steps; k++) {
                int px = mx + (cx - mx) * k / steps;
                int py = my + (cy - my) * k / steps;
                gui.fill(px - 1, py - 1, px + 2, py + 2, 0x90000000);
                gui.fill(px, py, px + 1, py + 1, 0xE0FFD86B);
            }
        }
        double spread = WarCursor.spread();
        if (spread > 0 && WarCursor.onGround()) {
            int sr = spreadRadius();
            String s = "±" + WarCursor.number(spread);
            int w = (int) Math.ceil(font.width(s) * 0.7f);
            int x = cx + (int) Math.round(sr * 0.72) + 2;
            int y = cy - (int) Math.round(sr * 0.72) - 7;
            gui.fill(x - 2, y - 1, x + w + 2, y + 7, LABEL_BG);
            small(gui, font, s, x, y, color, 0.7f);
        }
    }

    private static void drawPreview(GuiGraphicsExtractor gui, Font font) {
        double[] p = WarCursor.project(WarCursor.previewX(), WarCursor.previewY(), WarCursor.previewZ());
        if (p == null) {
            return;
        }
        int px = (int) Math.round(p[0]);
        int py = (int) Math.round(p[1]);
        double ppb = WarCursor.pixelsPerBlock(WarCursor.previewY());
        String text;
        if (WarCursor.preview() == WarCursor.Preview.BLAST) {
            int br = (int) Math.round(Math.max(4, WarCursor.previewR() * ppb));
            ring(gui, px, py, br + 1, 1, BLACK, false);
            ring(gui, px, py, br, 2, YELLOW, true);
            for (int k = -br + 3; k < br - 2; k += 4) {
                int len = (int) Math.sqrt(Math.max(0, br * br - k * k)) - 2;
                gui.fill(px + k, py - len, px + k + 1, py + len, 0x60FFD43B);
            }
            text = WarCursor.tr("nightsiege.war.cursor.blast");
        } else {
            // A ladder seen from above: two rails and rungs, as long as two blocks.
            int half = (int) Math.round(Math.max(5, ppb));
            int wide = Math.max(3, half / 2);
            gui.fill(px - wide - 2, py - half - 1, px + wide + 3, py + half + 2, BLACK);
            gui.fill(px - wide - 1, py - half, px - wide + 1, py + half + 1, YELLOW);
            gui.fill(px + wide, py - half, px + wide + 2, py + half + 1, YELLOW);
            for (int y = py - half + 2; y < py + half; y += Math.max(3, half / 3)) {
                gui.fill(px - wide + 1, y, px + wide, y + 1, YELLOW);
            }
            text = WarCursor.tr("nightsiege.war.cursor.ladder");
        }
        // Beside the preview (above and below the cross stand its own words).
        float scale = 0.75f;
        int lx = px + (int) Math.round(Math.max(5, (WarCursor.preview() == WarCursor.Preview.BLAST ? WarCursor.previewR() : 1.0) * ppb)) + 4;
        int lw = (int) Math.ceil(font.width(text) * scale);
        lx = Math.min(WarCursor.width() - lw - 4, lx);
        gui.fill(lx - 3, py - 6, lx + lw + 3, py + 4, LABEL_BG);
        small(gui, font, text, lx, py - 4, YELLOW, scale);
    }

    /** The sign of the intent inside the ring. */
    private static void sign(GuiGraphicsExtractor gui, WarCursor.Intent intent, int cx, int cy, int s, int color) {
        switch (intent) {
            case MOVE -> {
                // An arrow down onto the spot: a shaft and a wide head.
                int head = Math.max(3, s / 2 + 1);
                int tip = cy + s;
                gui.fill(cx - 2, cy - s - 1, cx + 2, tip - head + 1, BLACK);
                for (int k = 0; k <= head; k++) {
                    gui.fill(cx - k - 1, tip - k - 1, cx + k + 1, tip - k + 1, BLACK);
                }
                gui.fill(cx - 1, cy - s, cx + 1, tip - head, color);
                for (int k = 0; k < head; k++) {
                    gui.fill(cx - k, tip - k - 1, cx + k, tip - k, color);
                }
            }
            case ATTACK -> {
                // An upright sword: the blade with its point, the guard across, the grip and the pommel.
                int guard = cy + s / 3;
                int half = Math.max(3, s / 2 + 1);
                int end = cy + s;
                gui.fill(cx - 2, cy - s - 1, cx + 2, guard + 1, BLACK);
                gui.fill(cx - half - 1, guard - 1, cx + half + 1, guard + 3, BLACK);
                gui.fill(cx - 2, guard + 2, cx + 2, end + 1, BLACK);
                gui.fill(cx - 1, cy - s, cx + 1, guard, color);
                gui.fill(cx - 1, cy - s - 1 + 1, cx + 1, cy - s + 1, WHITE);
                gui.fill(cx - half, guard, cx + half, guard + 2, color);
                gui.fill(cx - 1, guard + 2, cx + 1, end - 1, 0xFFB07A40);
                gui.fill(cx - 1, end - 1, cx + 1, end, color);
            }
            case SPECIAL -> {
                // A diamond with a point.
                for (int k = 0; k <= s; k++) {
                    int w = s - Math.abs(k - s / 2) * 2;
                    if (w > 0) {
                        gui.fill(cx - w / 2 - 1, cy - s / 2 + k - 1, cx + w / 2 + 2, cy - s / 2 + k + 2, BLACK);
                    }
                }
                for (int k = 0; k <= s; k++) {
                    int w = s - Math.abs(k - s / 2) * 2;
                    if (w > 0) {
                        gui.fill(cx - w / 2, cy - s / 2 + k, cx + w / 2 + 1, cy - s / 2 + k + 1, color);
                    }
                }
                gui.fill(cx, cy - 1, cx + 1, cy + 1, BLACK);
            }
            case INVALID -> {
                int a = Math.max(2, s - 1);
                thick(gui, cx - a, cy - a, cx + a, cy + a, BLACK, 4);
                thick(gui, cx - a, cy + a, cx + a, cy - a, BLACK, 4);
                thick(gui, cx - a, cy - a, cx + a, cy + a, color, 2);
                thick(gui, cx - a, cy + a, cx + a, cy - a, color, 2);
            }
            default -> {
                gui.fill(cx - 2, cy - 2, cx + 3, cy + 3, BLACK);
                gui.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
            }
        }
    }

    /** Name and life of the unit under the cross, in a small dark box. */
    private static void label(GuiGraphicsExtractor gui, Font font, int cx, int y, WarCursor.Hover h, int color) {
        String text = h.life().isEmpty() ? h.name() : h.name() + " · " + h.life();
        float scale = 0.75f;
        int w = (int) Math.ceil(font.width(text) * scale);
        int bar = h.share() >= 0 ? 3 : 0;
        int x = Math.max(2, Math.min(WarCursor.width() - w - 4, cx - w / 2));
        y = Math.max(28, y - bar);
        gui.fill(x - 3, y - 2, x + w + 3, y + 8 + bar, LABEL_BG);
        frame(gui, x - 3, y - 2, x + w + 3, y + 8 + bar, h.kind() == WarCursor.Kind.DEFENDER ? RED : 0xFF5A5070);
        small(gui, font, text, x, y, h.kind() == WarCursor.Kind.DEFENDER ? 0xFFBFE0FF : WHITE, scale);
        if (bar > 0) {
            float share = Math.max(0f, Math.min(1f, h.share()));
            int c = share > 0.6f ? GREEN : share > 0.3f ? YELLOW : RED;
            gui.fill(x, y + 7, x + w, y + 9, 0xFF301010);
            gui.fill(x, y + 7, x + Math.round(w * share), y + 9, c);
        }
    }

    /** One line under the cross. */
    private static void words(GuiGraphicsExtractor gui, Font font, String text, int cx, int y, int color) {
        float scale = 0.75f;
        int w = (int) Math.ceil(font.width(text) * scale);
        int x = Math.max(2, Math.min(WarCursor.width() - w - 4, cx - w / 2));
        y = Math.min(WarCursor.height() - 30, y);
        gui.fill(x - 3, y - 2, x + w + 3, y + 8, LABEL_BG);
        small(gui, font, text, x, y, color, scale);
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A ring of thickness t inward from radius r (dashed: every other bit), only where it is on the screen. */
    static void ring(GuiGraphicsExtractor gui, int cx, int cy, int r, int t, int color, boolean dashed) {
        if (r <= 0) {
            return;
        }
        int n = Math.max(24, (int) Math.ceil(2 * Math.PI * r * 1.4));
        int w = WarCursor.width();
        int h = WarCursor.height();
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            if (dashed && (((int) (a * r / 3.0)) & 1) == 1) {
                continue;   // dashes of three pixels
            }
            int x = (int) Math.round(cx + r * Math.cos(a) - 0.5);
            int y = (int) Math.round(cy + r * Math.sin(a) - 0.5);
            if (x == lastX && y == lastY || x < -t || y < -t || x > w + t || y > h + t) {
                continue;
            }
            lastX = x;
            lastY = y;
            int ox = (int) Math.round(-Math.cos(a) * (t - 1));
            int oy = (int) Math.round(-Math.sin(a) * (t - 1));
            gui.fill(Math.min(x, x + ox), Math.min(y, y + oy), Math.max(x, x + ox) + 1, Math.max(y, y + oy) + 1, color);
        }
    }

    /** Four corner marks of a square round a spot. */
    private static void corners(GuiGraphicsExtractor gui, int cx, int cy, int half, int color) {
        int l = Math.max(3, half / 2);
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                int x = cx + sx * half;
                int y = cy + sy * half;
                thick(gui, x, y, x - sx * l, y, BLACK, 3);
                thick(gui, x, y, x, y - sy * l, BLACK, 3);
                line(gui, x, y, x - sx * l, y, color);
                line(gui, x, y, x, y - sy * l, color);
            }
        }
    }

    static void line(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1, int color) {
        thick(gui, x0, y0, x1, y1, color, 1);
    }

    /** A line of square dots t wide. */
    static void thick(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1, int color, int t) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int lo = -(t - 1) / 2;
        for (int k = 0; k <= steps; k++) {
            int x = steps == 0 ? x0 : x0 + Math.round((x1 - x0) * k / (float) steps);
            int y = steps == 0 ? y0 : y0 + Math.round((y1 - y0) * k / (float) steps);
            gui.fill(x + lo, y + lo, x + lo + t, y + lo + t, color);
        }
    }

    private static void frame(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1, int color) {
        gui.fill(x0, y0, x1, y0 + 1, color);
        gui.fill(x0, y1 - 1, x1, y1, color);
        gui.fill(x0, y0, x0 + 1, y1, color);
        gui.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }
}
