package com.mki.nightsiege.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.Collection;
import java.util.List;

/**
 * The dark violet look of the class choice, the night statistics and the wheel (Simon,
 * 27 October 2026: the whole core window in the same design). Colours and a button skin that
 * is painted over Minecraft's stone buttons.
 */
public final class PurpleUi {

    public static final int BACKDROP = 0xB0000000;
    public static final int FRAME = 0xFF3A3450;
    public static final int FRAME_HI = 0xFF4A4266;
    public static final int PANEL = 0xF4121019;
    public static final int PANEL_SOLID = 0xFF121019;
    public static final int BOX = 0xFF1A1622;
    public static final int BOX_HOVER = 0xFF262033;
    public static final int BOX_DARK = 0xFF0E0C14;
    public static final int LINE = 0xFF2C2740;
    public static final int GOLD = 0xFFFFD86B;
    public static final int GOLD_HOVER = 0xFFFFE48A;
    public static final int GOLD_EDGE = 0xFF8A6A20;
    public static final int GOLD_SHADE = 0xFFC9A440;
    public static final int GOLD_TEXT = 0xFF2A2010;
    public static final int GREY = 0xFF8F879F;
    public static final int DIM = 0xFF5E5870;
    public static final int LIGHT = 0xFFCFC7DD;
    public static final int RED = 0xFFE05A5A;
    public static final int GREEN = 0xFF7FE08A;

    private PurpleUi() {
    }

    /** A window: one line of frame, the dark panel and a lighter line along the top. */
    public static void window(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2) {
        gui.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, FRAME);
        gui.fill(x1, y1, x2, y2, PANEL);
        gui.fill(x1, y1, x2, y1 + 1, FRAME_HI);
    }

    /** A box inside a window: a thin line around a darker fill. */
    public static void box(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2, int fill) {
        gui.fill(x1, y1, x2, y2, LINE);
        gui.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** One button: gold for the main action, dark with a violet frame otherwise. */
    public static void button(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, String text,
                              boolean active, boolean hover, boolean primary) {
        if (primary && active) {
            gui.fill(x, y, x + w, y + h, GOLD_EDGE);
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? GOLD_HOVER : GOLD);
            gui.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, GOLD_SHADE);
        } else {
            int edge = !active ? LINE : hover ? GOLD : FRAME_HI;
            gui.fill(x, y, x + w, y + h, edge);
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, !active ? BOX_DARK : hover ? BOX_HOVER : BOX);
        }
        int color = primary && active ? GOLD_TEXT : !active ? DIM : hover ? GOLD : LIGHT;
        fitText(gui, font, text, x + 3, y, w - 6, h, color, primary && active ? false : true, true);
    }

    /**
     * One line of text in a box {@code maxW} x {@code h} (vertically centred): too long, it gets smaller (down to
     * {@link UiFit#MIN_SCALE}) instead of being cut; only beyond that it is cut (Paket 13.11b).
     */
    public static void fitText(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int maxW, int h,
                               int color, boolean shadow, boolean centred) {
        int tw = font.width(text);
        float s = Math.max(UiFit.MIN_SCALE, UiFit.scaleFor(tw, maxW));
        if (s >= 1f) {
            gui.text(font, text, centred ? x + (maxW - tw) / 2 : x, y + (h - 8) / 2, color, shadow);
            return;
        }
        String shown = tw * s > maxW ? font.plainSubstrByWidth(text, (int) (maxW / s)) : text;
        float sw = font.width(shown) * s;
        gui.pose().pushMatrix();
        gui.pose().translate(centred ? x + (maxW - sw) / 2f : x, y + (h - 8 * s) / 2f);
        gui.pose().scale(s, s);
        gui.text(font, shown, 0, 0, color, shadow);
        gui.pose().popMatrix();
    }

    /** True when a button {@code w} wide shows {@code text} whole (at most shrunk to {@link UiFit#MIN_SCALE}). */
    public static boolean fitsButton(Font font, String text, int w) {
        return font.width(text) * UiFit.MIN_SCALE <= w - 6;
    }

    /** A small text wrapped into {@code maxW}; returns the height used. */
    public static int wrapSmall(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int maxW, int color,
                                float scale, boolean shadow) {
        int lineH = Math.round(10 * scale);
        int dy = 0;
        for (net.minecraft.util.FormattedCharSequence part
                : font.split(net.minecraft.network.chat.FormattedText.of(text), (int) (maxW / scale))) {
            gui.pose().pushMatrix();
            gui.pose().translate(x, y + dy);
            gui.pose().scale(scale, scale);
            gui.text(font, part, 0, 0, color, shadow);
            gui.pose().popMatrix();
            dy += lineH;
        }
        return dy;
    }

    /**
     * Paints every visible button of a screen in the violet look (after the screen drew its
     * widgets). Buttons without a label are click areas over drawn things and stay unseen.
     */
    public static void skinButtons(GuiGraphicsExtractor gui, Font font, List<? extends GuiEventListener> children,
                                   Collection<?> primary, int mouseX, int mouseY) {
        skinButtons(gui, font, children, primary, null, mouseX, mouseY);
    }

    /** As above; the buttons in {@code skip} draw themselves (a screen's own gold button, say). */
    public static void skinButtons(GuiGraphicsExtractor gui, Font font, List<? extends GuiEventListener> children,
                                   Collection<?> primary, Collection<?> skip, int mouseX, int mouseY) {
        for (GuiEventListener child : children) {
            if (!(child instanceof Button b) || !b.visible || (skip != null && skip.contains(b))) {
                continue;
            }
            String text = b.getMessage().getString();
            if (text.isEmpty()) {
                continue;
            }
            boolean hover = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
            button(gui, font, b.getX(), b.getY(), b.getWidth(), b.getHeight(), text, b.active, hover,
                    primary != null && primary.contains(b));
        }
    }
}
