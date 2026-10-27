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
        String shown = font.plainSubstrByWidth(text, w - 6);
        int color = primary && active ? GOLD_TEXT : !active ? DIM : hover ? GOLD : LIGHT;
        gui.text(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, color, primary && active ? false : true);
    }

    /**
     * Paints every visible button of a screen in the violet look (after the screen drew its
     * widgets). Buttons without a label are click areas over drawn things and stay unseen.
     */
    public static void skinButtons(GuiGraphicsExtractor gui, Font font, List<? extends GuiEventListener> children,
                                   Collection<?> primary, int mouseX, int mouseY) {
        for (GuiEventListener child : children) {
            if (!(child instanceof Button b) || !b.visible) {
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
