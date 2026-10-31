package com.mki.nightsiege.client;

import com.mki.nightsiege.core.HallBadges;
import com.mki.nightsiege.core.HallOfFame;
import com.mki.nightsiege.core.HallView;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Ruhmeshalle tab of the core window (Simon 30.10.: "plan mir eine größere und detailliertere
 * Stat-Seite im Core", "so coole Ingame-Stats", "Titel und Abzeichen, witzige Werte, auch so eine
 * Sieger-Treppchen-Ansicht"; concept claude/night-siege-ruhmeshalle-konzept.md).
 *
 * <p>Five pages under one banner: the podium (last night or all time, with the defenders standing
 * on it), the chronicle of any defender, the badges with their progress, the curiosities and the
 * bestiary. Everything is drawn here; the window only hands over its inner area, the clicks and
 * the mouse wheel. The data comes from the server as screen payload "hall" (request "hall:open").</p>
 */
final class HallPages {

    private HallPages() {
    }

    // ------------------------------------------------------------------ state

    private enum Page {
        PODIUM("podium", "minecraft:gold_block"),
        CHRONICLE("chronicle", "minecraft:writable_book"),
        BADGES("badges", "minecraft:totem_of_undying"),
        CURIOS("curios", "minecraft:spyglass"),
        BESTIARY("bestiary", "minecraft:zombie_head");

        final String key;
        final String icon;

        Page(String key, String icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    /** The podium's categories, in the order of the buttons. */
    private static final List<String> CATEGORIES = List.of("kills", "dealt", "healed", "intercepted", "bosses", "held", "deaths");

    private static HallView view;
    private static long askedAt;
    private static Page page = Page.PODIUM;
    private static String category = "kills";
    private static boolean lastNight = true;
    /** Whose chronicle and badges are shown (the viewer by default). */
    private static String shownHero;
    private static int scroll;
    private static int maxScroll;
    /** The scrolling part of the page, for the mouse wheel. */
    private static int[] scrollArea;

    private static final int GOLD = 0xFFFFD86B;
    private static final int SILVER = 0xFFC8D0DC;
    private static final int BRONZE = 0xFFCD7F32;
    private static final int NONE = 0xFF5E5870;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF7FE08A;
    private static final int RED = 0xFFE05A5A;

    /** A spot of the page: a click does something, the mouse over it shows a tooltip. */
    private record Hit(int x1, int y1, int x2, int y2, Runnable click, List<String> tip) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private static final List<Hit> hits = new ArrayList<>();
    /** While a page scrolls: spots outside its visible part are left out. */
    private static int[] clip;
    private static Consumer<String> sender = a -> { };

    /** The figures of defenders who are not near (or not online): built once per world. */
    private static final Map<String, RemotePlayer> FIGURES = new HashMap<>();
    private static final Set<String> BROKEN = new HashSet<>();
    private static Object figuresLevel;

    // ------------------------------------------------------------------ the window's side

    static void receive(HallView v) {
        if (v == null) {
            return;
        }
        view = v;
        if (shownHero == null || !v.heroes.containsKey(shownHero)) {
            shownHero = v.heroes.containsKey(v.viewer) ? v.viewer : v.heroes.isEmpty() ? null : v.heroes.keySet().iterator().next();
        }
    }

    /** True when the window should ask the server again (first look, or a few seconds later). */
    static boolean wantsData() {
        return view == null || System.currentTimeMillis() - askedAt > 3000;
    }

    static void asked() {
        askedAt = System.currentTimeMillis();
    }

    static boolean click(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.click() != null && h.contains(mx, my)) {
                h.click().run();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
                return true;
            }
        }
        return false;
    }

    static boolean scroll(double mx, double my, double dy) {
        if (scrollArea == null || maxScroll <= 0) {
            return false;
        }
        if (mx < scrollArea[0] || mx >= scrollArea[2] || my < scrollArea[1] || my >= scrollArea[3]) {
            return false;
        }
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - dy * 14));
        return true;
    }

    /** Draws the hall into the window's inner area; returns the tooltip under the mouse, or null. */
    static List<String> draw(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY,
                             Consumer<String> send) {
        sender = send == null ? a -> { } : send;
        hits.clear();
        scrollArea = null;
        clip = null;
        drawNav(gui, font, x + 4, y + 4, w - 8, mouseX, mouseY);
        int cx = x + 6;
        int cy = y + 26;
        int cw = w - 12;
        int ch = h - 30;
        if (view == null) {
            centered(gui, font, tr("nightsiege.hall.loading"), cx + cw / 2, cy + ch / 2 - 4, PurpleUi.GREY);
            return tipAt(mouseX, mouseY);
        }
        if (!view.logger) {
            String off = font.plainSubstrByWidth(tr("nightsiege.hall.logger_off"), cw);
            gui.text(font, off, cx, cy + ch - 9, RED, true);
            ch -= 12;
        }
        switch (page) {
            case PODIUM -> drawPodium(gui, font, cx, cy, cw, ch, mouseX, mouseY);
            case CHRONICLE -> drawChronicle(gui, font, cx, cy, cw, ch, mouseX, mouseY);
            case BADGES -> drawBadges(gui, font, cx, cy, cw, ch, mouseX, mouseY);
            case CURIOS -> drawCurios(gui, font, cx, cy, cw, ch, mouseX, mouseY);
            case BESTIARY -> drawBestiary(gui, font, cx, cy, cw, ch, mouseX, mouseY);
        }
        return tipAt(mouseX, mouseY);
    }

    private static void hit(int x1, int y1, int x2, int y2, Runnable click, List<String> tip) {
        if (clip != null) {
            x1 = Math.max(x1, clip[0]);
            y1 = Math.max(y1, clip[1]);
            x2 = Math.min(x2, clip[2]);
            y2 = Math.min(y2, clip[3]);
        }
        if (x2 > x1 && y2 > y1) {
            hits.add(new Hit(x1, y1, x2, y2, click, tip));
        }
    }

    private static List<String> tipAt(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.tip() != null && !h.tip().isEmpty() && h.contains(mx, my)) {
                return h.tip();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the pennants

    private static void drawNav(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int mouseX, int mouseY) {
        Page[] pages = Page.values();
        int gap = 4;
        int bw = Math.min(130, (w - gap * (pages.length - 1)) / pages.length);
        int total = bw * pages.length + gap * (pages.length - 1);
        int bx = x + (w - total) / 2;
        boolean icons = bw >= 60;
        for (Page p : pages) {
            boolean active = p == page;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 18;
            int edge = active ? GOLD : hover ? PurpleUi.FRAME_HI : PurpleUi.LINE;
            gui.fill(bx, y, bx + bw, y + 18, edge);
            gui.fill(bx + 1, y + 1, bx + bw - 1, y + 17, active ? 0xFF2A2214 : hover ? PurpleUi.BOX_HOVER : PurpleUi.BOX);
            if (active) {
                // A pennant: the gold edge runs down into a small point under the button.
                gui.fill(bx + bw / 2 - 3, y + 18, bx + bw / 2 + 3, y + 19, GOLD);
                gui.fill(bx + bw / 2 - 1, y + 19, bx + bw / 2 + 1, y + 20, GOLD);
            }
            String label = tr("nightsiege.hall.page." + p.key);
            int tx = bx + 4;
            if (icons) {
                gui.pose().pushMatrix();
                gui.pose().translate(bx + 3, y + 2);
                gui.pose().scale(0.875f, 0.875f);
                gui.item(CoreScreen.iconOf(p.icon), 0, 0);
                gui.pose().popMatrix();
                tx = bx + 20;
            }
            String shown = font.plainSubstrByWidth(label, bx + bw - 3 - tx);
            int color = active ? GOLD : hover ? WHITE : PurpleUi.LIGHT;
            gui.text(font, shown, icons ? tx : bx + (bw - font.width(shown)) / 2, y + 5, color, true);
            Page target = p;
            hit(bx, y, bx + bw, y + 18, () -> {
                page = target;
                scroll = 0;
            }, font.width(label) > bx + bw - 3 - tx ? List.of(label) : null);
            bx += bw + gap;
        }
    }

    // ------------------------------------------------------------------ podium

    /** One place of the podium or the list below it. */
    private record Rank(String uuid, String name, String sub, double value) {
    }

    private static void drawPodium(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (lastNight && "held".equals(category)) {
            category = "kills";
        }
        // The switches: last night / all time, then the categories.
        int bx = x;
        int by = y;
        bx = toggle(gui, font, bx, by, tr("nightsiege.hall.mode.last"), lastNight, mouseX, mouseY, () -> lastNight = true);
        bx = toggle(gui, font, bx, by, tr("nightsiege.hall.mode.total"), !lastNight, mouseX, mouseY, () -> lastNight = false);
        bx += 8;
        for (String cat : CATEGORIES) {
            if (lastNight && "held".equals(cat)) {
                continue;
            }
            String label = tr("nightsiege.hall.cat." + cat);
            if (bx + font.width(label) + 10 > x + w) {
                bx = x;
                by += 16;
            }
            String c = cat;
            bx = toggle(gui, font, bx, by, label, cat.equals(category), mouseX, mouseY, () -> category = c);
        }
        int top = by + 18;
        // The line about the night (or all nights).
        String info;
        if (lastNight) {
            HallOfFame.LastNight n = view.lastNight;
            info = n == null ? tr("nightsiege.hall.no_last")
                    : tr("nightsiege.hall.last_info", n.night, n.level, date(n.date),
                    tr("gewonnen".equals(n.result) ? "nightsiege.hall.result.held" : "nightsiege.hall.result.lost"));
        } else {
            int nights = 0;
            for (HallOfFame.Hero hero : view.heroes.values()) {
                nights = Math.max(nights, hero.nights);
            }
            info = tr("nightsiege.hall.total_info", view.heroes.size(), nights);
        }
        gui.text(font, font.plainSubstrByWidth(info, w), x, top, PurpleUi.GREY, true);
        top += 12;

        List<Rank> ranks = ranks();
        if (ranks.isEmpty()) {
            centered(gui, font, tr(view.heroes.isEmpty() ? "nightsiege.hall.empty" : "nightsiege.hall.nobody"),
                    x + w / 2, top + (y + h - top) / 2 - 4, PurpleUi.GREY);
            return;
        }
        boolean side = w >= 470;
        int podW = side ? w * 3 / 5 : w;
        int listX = side ? x + podW + 8 : x;
        int listW = side ? w - podW - 8 : w;
        int podH = side ? y + h - top : Math.max(120, (y + h - top) * 2 / 3);
        drawStand(gui, font, ranks, x, top, podW, podH, mouseX, mouseY);
        int listY = side ? top : top + podH + 4;
        int listH = y + h - listY;
        drawRest(gui, font, ranks, listX, listY, listW, listH, mouseX, mouseY);
    }

    private static int toggle(GuiGraphicsExtractor gui, Font font, int x, int y, String label, boolean on, int mouseX, int mouseY,
                              Runnable run) {
        int w = font.width(label) + 10;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        PurpleUi.button(gui, font, x, y, w, 14, label, true, hover, on);
        hit(x, y, x + w, y + 14, run, null);
        return x + w + 3;
    }

    private static List<Rank> ranks() {
        List<Rank> out = new ArrayList<>();
        if (lastNight) {
            if (view.lastNight != null) {
                for (HallOfFame.Entry e : view.lastNight.entries) {
                    double v = switch (category) {
                        case "dealt" -> e.dealt;
                        case "healed" -> e.healed;
                        case "intercepted" -> e.intercepted;
                        case "bosses" -> e.bosses;
                        case "deaths" -> e.deaths;
                        default -> e.kills;
                    };
                    if (v > 0) {
                        out.add(new Rank(e.uuid, e.name, e.sub, v));
                    }
                }
            }
        } else {
            for (Map.Entry<String, HallOfFame.Hero> e : view.heroes.entrySet()) {
                HallOfFame.Hero hero = e.getValue();
                double v = switch (category) {
                    case "dealt" -> hero.dealt;
                    case "healed" -> hero.healed;
                    case "intercepted" -> hero.intercepted;
                    case "bosses" -> hero.bosses;
                    case "held" -> hero.held;
                    case "deaths" -> hero.deaths;
                    default -> hero.kills;
                };
                if (v > 0) {
                    out.add(new Rank(e.getKey(), hero.name, HallOfFame.mainSub(hero), v));
                }
            }
        }
        out.sort(Comparator.comparingDouble(Rank::value).reversed().thenComparing(Rank::name));
        return out;
    }

    /** The three steps with the defenders standing on them: second left, first in the middle, third right. */
    private static void drawStand(GuiGraphicsExtractor gui, Font font, List<Rank> ranks, int x, int y, int w, int h,
                                  int mouseX, int mouseY) {
        int colW = Math.min(118, w / 3);
        int startX = x + (w - colW * 3) / 2;
        int[] order = {1, 0, 2};
        int[] steps = {Math.max(30, h / 4), Math.max(22, h * 3 / 16), Math.max(16, h / 8)};
        int[] colors = {GOLD, SILVER, BRONZE};
        int[] shades = {0xFFB8912E, 0xFF8F97A3, 0xFF8A5626};
        int floor = y + h - 2;
        // A floor line the steps stand on.
        gui.fill(x, floor, x + w, floor + 1, PurpleUi.LINE);
        for (int slot = 0; slot < 3; slot++) {
            int place = order[slot];
            if (place >= ranks.size()) {
                continue;
            }
            Rank r = ranks.get(place);
            int sx = startX + slot * colW + 3;
            int sw = colW - 6;
            int stepH = steps[place];
            int stepTop = floor - stepH;
            // The step: lit top, darker front, the place number in the middle.
            gui.fill(sx, stepTop, sx + sw, floor, shades[place]);
            gui.fill(sx, stepTop, sx + sw, stepTop + 3, colors[place]);
            gui.fill(sx, stepTop + 3, sx + 1, floor, colors[place]);
            gui.fill(sx + sw - 1, stepTop + 3, sx + sw, floor, 0xFF000000 | (shades[place] & 0xFEFEFE) >> 1);
            String num = String.valueOf(place + 1);
            float scale = stepH >= 30 ? 2.0f : 1.5f;
            int nw = (int) (font.width(num) * scale);
            gui.pose().pushMatrix();
            gui.pose().translate(sx + (sw - nw) / 2f, stepTop + 6);
            gui.pose().scale(scale, scale);
            gui.text(font, num, 0, 0, 0xFF2A2010, false);
            gui.pose().popMatrix();
            String val = valueText(category, r.value());
            int vy = stepTop + 6 + (int) (9 * scale) + 1;
            if (vy + 8 < floor) {
                gui.text(font, font.plainSubstrByWidth(val, sw - 4), sx + (sw - Math.min(font.width(val), sw - 4)) / 2, vy, 0xFF2A2010, false);
            }
            // The figure on its step, looking at the mouse. The window draws a figure with its
            // middle in the middle of the box, 1.8 blocks tall: the box is laid so its feet touch the step.
            int nameH = 20;
            int room = stepTop - (y + nameH);
            int size = Math.min(sw * 2 / 5, (int) (room / 2.0));
            int nameY = y;
            if (size >= 12) {
                int fy1 = stepTop - 2 * size;
                int fy2 = stepTop + (int) (0.2 * size);
                nameY = Math.max(y, stepTop - (int) (1.95 * size) - nameH);
                LivingEntity fig = figure(r.uuid(), r.name());
                if (fig != null) {
                    try {
                        InventoryScreen.extractEntityInInventoryFollowsMouse(gui, sx + 2, fy1, sx + sw - 2, fy2, size, 0.0625f,
                                mouseX, mouseY, fig);
                    } catch (RuntimeException e) {
                        BROKEN.add(r.uuid());
                    }
                }
            }
            // Name and subclass above the figure.
            String name = font.plainSubstrByWidth(r.name(), sw);
            gui.text(font, name, sx + (sw - font.width(name)) / 2, nameY, place == 0 ? GOLD : WHITE, true);
            String sub = font.plainSubstrByWidth(subName(r.sub()), (int) (sw / 0.75f));
            small(gui, font, sub, sx + (sw - (int) (font.width(sub) * 0.75f)) / 2, nameY + 10, PurpleUi.GREY, 0.75f);
            String uuid = r.uuid();
            hit(sx, y, sx + sw, floor, () -> openChronicle(uuid),
                    List.of(r.name(), "§7" + subName(r.sub()), "§e" + tr("nightsiege.hall.cat." + category) + ": " + val,
                            "§8" + tr("nightsiege.hall.open_chronicle")));
        }
    }

    /** Places 4 to 10 (or all after the third, scrolled). */
    private static void drawRest(GuiGraphicsExtractor gui, Font font, List<Rank> ranks, int x, int y, int w, int h,
                                 int mouseX, int mouseY) {
        if (ranks.size() <= 3 || h < 20) {
            return;
        }
        gui.text(font, tr("nightsiege.hall.rest"), x, y, GOLD, true);
        int ly = y + 12;
        int end = Math.min(ranks.size(), 10);
        for (int i = 3; i < end && ly + 12 <= y + h; i++) {
            Rank r = ranks.get(i);
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ly && mouseY < ly + 12;
            gui.fill(x, ly, x + w, ly + 11, hover ? PurpleUi.BOX_HOVER : (i % 2 == 0 ? 0x601A1622 : 0x30000000));
            gui.text(font, (i + 1) + ".", x + 3, ly + 2, PurpleUi.GREY, true);
            String val = valueText(category, r.value());
            int vw = font.width(val);
            String name = font.plainSubstrByWidth(r.name(), w - 26 - vw - 6);
            gui.text(font, name, x + 22, ly + 2, WHITE, true);
            gui.text(font, val, x + w - 3 - vw, ly + 2, PurpleUi.LIGHT, true);
            String uuid = r.uuid();
            hit(x, ly, x + w, ly + 11, () -> openChronicle(uuid),
                    List.of(r.name(), "§7" + subName(r.sub()), "§8" + tr("nightsiege.hall.open_chronicle")));
            ly += 12;
        }
    }

    private static void openChronicle(String uuid) {
        if (view != null && view.heroes.containsKey(uuid)) {
            shownHero = uuid;
            page = Page.CHRONICLE;
            scroll = 0;
        }
    }

    // ------------------------------------------------------------------ chronicle

    /** The defenders in the order of the arrows: most nights first. */
    private static List<String> heroOrder() {
        List<String> ids = new ArrayList<>(view.heroes.keySet());
        ids.sort(Comparator.comparingInt((String id) -> -view.heroes.get(id).nights)
                .thenComparing(id -> view.heroes.get(id).name.toLowerCase(java.util.Locale.ROOT)));
        return ids;
    }

    /** The row with the arrows, the name and the "me" button. Returns the hero shown, or null. */
    private static HallOfFame.Hero drawPicker(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (view.heroes.isEmpty()) {
            centered(gui, font, tr("nightsiege.hall.empty"), x + w / 2, y + 40, PurpleUi.GREY);
            return null;
        }
        List<String> order = heroOrder();
        if (shownHero == null || !view.heroes.containsKey(shownHero)) {
            shownHero = view.heroes.containsKey(view.viewer) ? view.viewer : order.get(0);
        }
        int index = order.indexOf(shownHero);
        HallOfFame.Hero hero = view.heroes.get(shownHero);
        String prev = order.get((index - 1 + order.size()) % order.size());
        String next = order.get((index + 1) % order.size());
        arrow(gui, font, x, y, "◀", mouseX, mouseY, () -> shownHero = prev);
        String title = hero.title == null || hero.title.isEmpty() ? "" : tr("nightsiege.hall.title." + hero.title);
        String shown = title.isEmpty() ? hero.name : tr("nightsiege.hall.name_title", hero.name, title);
        float scale = 1.5f;
        int room = w - 2 * 20 - 60;
        if (font.width(shown) * scale > room) {
            scale = 1.0f;
        }
        String fit = font.plainSubstrByWidth(shown, (int) (room / scale));
        gui.pose().pushMatrix();
        gui.pose().translate(x + 20, y + (scale > 1 ? 1 : 3));
        gui.pose().scale(scale, scale);
        gui.text(font, fit, 0, 0, title.isEmpty() ? WHITE : GOLD, true);
        gui.pose().popMatrix();
        int ax = x + 20 + (int) (font.width(fit) * scale) + 6;
        arrow(gui, font, ax, y, "▶", mouseX, mouseY, () -> shownHero = next);
        boolean online = view.online.contains(shownHero);
        small(gui, font, tr(online ? "nightsiege.hall.online" : "nightsiege.hall.offline"), ax + 20, y + 5,
                online ? GREEN : PurpleUi.DIM, 0.75f);
        if (!shownHero.equals(view.viewer) && view.heroes.containsKey(view.viewer)) {
            String me = tr("nightsiege.hall.me");
            int mw = font.width(me) + 10;
            boolean hover = mouseX >= x + w - mw && mouseX < x + w && mouseY >= y && mouseY < y + 14;
            PurpleUi.button(gui, font, x + w - mw, y, mw, 14, me, true, hover, false);
            hit(x + w - mw, y, x + w, y + 14, () -> shownHero = view.viewer, null);
        }
        return hero;
    }

    private static void arrow(GuiGraphicsExtractor gui, Font font, int x, int y, String text, int mouseX, int mouseY, Runnable run) {
        boolean hover = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 14;
        PurpleUi.button(gui, font, x, y, 16, 14, text, true, hover, false);
        hit(x, y, x + 16, y + 14, () -> {
            run.run();
            scroll = 0;
        }, null);
    }

    private static void drawChronicle(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        HallOfFame.Hero hero = drawPicker(gui, font, x, y, w, mouseX, mouseY);
        if (hero == null) {
            return;
        }
        int top = y + 20;
        int bottom = y + h;
        // Left: the defender, large, with the class played most.
        int figW = w >= 420 ? 92 : 70;
        PurpleUi.box(gui, x, top, x + figW, bottom, 0xA00E0C14);
        int figBottom = bottom - 26;
        LivingEntity fig = figure(shownHero, hero.name);
        if (fig != null && figBottom - top > 40) {
            int size = Math.max(16, Math.min(figW * 2 / 5, (int) ((figBottom - top - 8) / 2.2)));
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(gui, x + 2, top + 4, x + figW - 2, figBottom, size, 0.0625f,
                        mouseX, mouseY, fig);
            } catch (RuntimeException e) {
                BROKEN.add(shownHero);
            }
        }
        String main = font.plainSubstrByWidth(subName(HallOfFame.mainSub(hero)), (int) ((figW - 4) / 0.8f));
        small(gui, font, main, x + (figW - (int) (font.width(main) * 0.8f)) / 2, bottom - 22, classColor(HallOfFame.mainClass(hero)), 0.8f);
        String nights = tr("nightsiege.hall.nights_short", hero.nights);
        small(gui, font, nights, x + (figW - (int) (font.width(nights) * 0.8f)) / 2, bottom - 12, PurpleUi.GREY, 0.8f);

        // Right: the cards, scrolled.
        int rx = x + figW + 6;
        int rw = x + w - rx - 5;
        beginScroll(gui, rx, top, rx + rw + 5, bottom);
        int yy = top - scroll;
        boolean two = rw >= 400;
        int colW = two ? (rw - 6) / 2 : rw;
        int leftEnd = numbersCard(gui, font, hero, rx, yy, colW, mouseX, mouseY);
        int rcx = two ? rx + colW + 6 : rx;
        int ry = two ? yy : leftEnd + 6;
        ry = sourcesCard(gui, font, hero, rcx, ry, colW) + 6;
        ry = foesCard(gui, font, hero, rcx, ry, colW, mouseX, mouseY) + 6;
        ry = subsCard(gui, font, hero, rcx, ry, colW);
        int below = (two ? Math.max(leftEnd, ry) : ry) + 6;
        int end = recentCard(gui, font, hero, rx, below, rw, mouseX, mouseY);
        endScroll(gui, end - yy, bottom - top);
    }

    /** A card: a dark box with a gold title. Returns the y where its lines start. */
    private static int cardTop(GuiGraphicsExtractor gui, Font font, String title, int x, int y, int w, int lines, int lineH) {
        int h = 16 + lines * lineH + 4;
        PurpleUi.box(gui, x, y, x + w, y + h, 0xC0120F19);
        gui.fill(x + 1, y + 1, x + w - 1, y + 13, 0x40FFC94A);
        gui.text(font, font.plainSubstrByWidth(title, w - 8), x + 5, y + 3, GOLD, true);
        return y + 16;
    }

    private static int numbersCard(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero h, int x, int y, int w, int mouseX, int mouseY) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{tr("nightsiege.hall.chron.nights"), num(h.nights)});
        rows.add(new String[]{tr("nightsiege.hall.chron.held_lost"), num(h.held) + " / " + num(h.lost)});
        rows.add(new String[]{tr("nightsiege.hall.chron.streak"), num(h.streak) + " (" + tr("nightsiege.hall.chron.best", num(h.bestStreak)) + ")"});
        rows.add(new String[]{tr("nightsiege.hall.chron.no_death"), num(h.nightsNoDeath)});
        rows.add(new String[]{tr("nightsiege.hall.chron.kills"), num(h.kills)});
        rows.add(new String[]{tr("nightsiege.hall.chron.assists"), num(h.assists)});
        rows.add(new String[]{tr("nightsiege.hall.chron.deaths"), num(h.deaths)});
        rows.add(new String[]{tr("nightsiege.hall.chron.bosses"), num(h.bosses)});
        rows.add(new String[]{tr("nightsiege.hall.chron.dealt"), num(h.dealt)});
        rows.add(new String[]{tr("nightsiege.hall.chron.taken"), num(h.taken)});
        rows.add(new String[]{tr("nightsiege.hall.chron.intercepted"), num(h.intercepted)});
        rows.add(new String[]{tr("nightsiege.hall.chron.healed"), num(h.healed)});
        if (h.towerKills > 0) {
            rows.add(new String[]{tr("nightsiege.hall.chron.towers"), num(h.towerKills)});
        }
        if (h.droneDamage > 0 || h.droneSeconds > 0) {
            rows.add(new String[]{tr("nightsiege.hall.chron.drone"), num(h.droneDamage)});
        }
        rows.add(new String[]{tr("nightsiege.hall.chron.dungeons"), num(h.dungeonsWon) + " / " + num(h.dungeons)});
        rows.add(new String[]{tr("nightsiege.hall.chron.camps"), num(h.campsDestroyed) + " / " + num(h.camps)});
        rows.add(new String[]{tr("nightsiege.hall.chron.crates"), num(h.crates) + " (" + tr("nightsiege.hall.chron.rare", num(h.rareCrates)) + ")"});
        if (h.maxCurses > 0) {
            rows.add(new String[]{tr("nightsiege.hall.chron.curses"), num(h.maxCurses)});
        }
        int ly = cardTop(gui, font, tr("nightsiege.hall.card.numbers"), x, y, w, rows.size(), 10);
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            int vw = font.width(r[1]);
            if (i % 2 == 1) {
                gui.fill(x + 2, ly - 1, x + w - 2, ly + 9, 0x30000000);
            }
            gui.text(font, font.plainSubstrByWidth(r[0], w - 14 - vw), x + 5, ly, PurpleUi.LIGHT, true);
            gui.text(font, r[1], x + w - 5 - vw, ly, WHITE, true);
            ly += 10;
        }
        return ly + 4;
    }

    private static final List<String> SOURCES = List.of("nah", "fern", "faehigkeit", "turm", "begleiter", "drohne");
    private static final int[] SOURCE_COLORS = {0xFFFF6B4A, 0xFF8BD45C, 0xFFC88CFF, 0xFFF0A040, 0xFF5FD8B8, 0xFF6FA8FF};

    private static int sourcesCard(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero h, int x, int y, int w) {
        double total = 0;
        List<String> used = new ArrayList<>();
        for (String s : SOURCES) {
            double v = h.dealtBy.getOrDefault(s, 0.0);
            if (v > 0) {
                total += v;
                used.add(s);
            }
        }
        if (used.isEmpty()) {
            int ly = cardTop(gui, font, tr("nightsiege.hall.card.sources"), x, y, w, 1, 10);
            gui.text(font, tr("nightsiege.hall.none_yet"), x + 5, ly, PurpleUi.DIM, true);
            return ly + 14;
        }
        int ly = cardTop(gui, font, tr("nightsiege.hall.card.sources"), x, y, w, used.size() + 1, 11);
        // One bar over the whole width, split by source …
        int bx = x + 5;
        int bw = w - 10;
        int cx = bx;
        for (int i = 0; i < SOURCES.size(); i++) {
            double v = h.dealtBy.getOrDefault(SOURCES.get(i), 0.0);
            if (v <= 0) {
                continue;
            }
            int sw = (int) Math.round(bw * v / total);
            gui.fill(cx, ly, Math.min(bx + bw, cx + sw), ly + 6, SOURCE_COLORS[i]);
            cx += sw;
        }
        ly += 11;
        // … and one line per source.
        for (String s : used) {
            int i = SOURCES.indexOf(s);
            double v = h.dealtBy.getOrDefault(s, 0.0);
            gui.fill(x + 5, ly + 1, x + 11, ly + 7, SOURCE_COLORS[i]);
            String val = num(v) + "  " + Math.round(100 * v / total) + "%";
            int vw = font.width(val);
            gui.text(font, font.plainSubstrByWidth(tr("nightsiege.hall.source." + s), w - 24 - vw), x + 15, ly, PurpleUi.LIGHT, true);
            gui.text(font, val, x + w - 5 - vw, ly, WHITE, true);
            ly += 11;
        }
        return ly + 3;
    }

    /** The arch-enemy (who killed this defender most, else who hurt them most) and the favourite prey. */
    private static int foesCard(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero h, int x, int y, int w, int mouseX, int mouseY) {
        String foe = null;
        String foeLine;
        String foeType = null;
        Map.Entry<String, Integer> killer = topInt(h.killedBy);
        if (killer != null) {
            foeType = killer.getKey();
            foe = beastName(foeType);
            foeLine = tr("nightsiege.hall.nemesis_killed", killer.getValue());
        } else {
            Map.Entry<String, Double> hurt = topDouble(h.takenFrom);
            if (hurt != null) {
                foeType = hurt.getKey();
                foe = beastName(foeType);
                foeLine = tr("nightsiege.hall.nemesis_damage", num(hurt.getValue()));
            } else {
                foeLine = tr("nightsiege.hall.none_yet");
            }
        }
        Map.Entry<String, Integer> prey = topInt(h.victims);
        int ly = cardTop(gui, font, tr("nightsiege.hall.card.foes"), x, y, w, 4, 10);
        gui.text(font, tr("nightsiege.hall.nemesis"), x + 5, ly, RED, true);
        if (foeType != null) {
            gui.item(beastIcon(foeType), x + w - 21, ly + 2);
        }
        ly += 10;
        gui.text(font, font.plainSubstrByWidth((foe == null ? "" : foe + " – ") + foeLine, w - 30), x + 9, ly, PurpleUi.LIGHT, true);
        ly += 10;
        gui.text(font, tr("nightsiege.hall.prey"), x + 5, ly, GREEN, true);
        if (prey != null) {
            gui.item(beastIcon(prey.getKey()), x + w - 21, ly + 2);
        }
        ly += 10;
        String preyLine = prey == null ? tr("nightsiege.hall.none_yet") : beastName(prey.getKey()) + " – " + tr("nightsiege.hall.prey_times", num(prey.getValue()));
        gui.text(font, font.plainSubstrByWidth(preyLine, w - 30), x + 9, ly, PurpleUi.LIGHT, true);
        ly += 10;
        return ly + 4;
    }

    private static int subsCard(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero h, int x, int y, int w) {
        List<Map.Entry<String, HallOfFame.SubLine>> subs = new ArrayList<>(h.subs.entrySet());
        subs.sort(Comparator.comparingInt((Map.Entry<String, HallOfFame.SubLine> e) -> -e.getValue().nights));
        int lines = Math.max(1, subs.size());
        int ly = cardTop(gui, font, tr("nightsiege.hall.card.subs"), x, y, w, lines, 10);
        if (subs.isEmpty()) {
            gui.text(font, tr("nightsiege.hall.none_yet"), x + 5, ly, PurpleUi.DIM, true);
            return ly + 14;
        }
        for (Map.Entry<String, HallOfFame.SubLine> e : subs) {
            HallOfFame.SubLine s = e.getValue();
            String val = tr("nightsiege.hall.sub_line", s.nights, s.held, num(s.kills), num(s.dealt));
            int vw = Math.min(font.width(val), w - 60);
            gui.text(font, font.plainSubstrByWidth(subName(e.getKey()), w - 14 - vw), x + 5, ly, classColor(s.clazz), true);
            gui.text(font, font.plainSubstrByWidth(val, vw), x + w - 5 - vw, ly, PurpleUi.LIGHT, true);
            ly += 10;
        }
        return ly + 4;
    }

    /** The last nights as bars: the damage dealt, green when held, red when lost. */
    private static int recentCard(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero h, int x, int y, int w, int mouseX, int mouseY) {
        int chartH = 44;
        if (h.recent.isEmpty()) {
            int ly = cardTop(gui, font, tr("nightsiege.hall.card.recent"), x, y, w, 1, 10);
            gui.text(font, tr("nightsiege.hall.none_yet"), x + 5, ly, PurpleUi.DIM, true);
            return ly + 14;
        }
        PurpleUi.box(gui, x, y, x + w, y + 16 + chartH + 16, 0xC0120F19);
        gui.fill(x + 1, y + 1, x + w - 1, y + 13, 0x40FFC94A);
        gui.text(font, font.plainSubstrByWidth(tr("nightsiege.hall.card.recent"), w - 8), x + 5, y + 3, GOLD, true);
        int ly = y + 16;
        double most = 1;
        for (HallOfFame.NightMini n : h.recent) {
            most = Math.max(most, n.dealt);
        }
        int slots = RECENT;
        int gap = 3;
        int bw = Math.max(6, (w - 10 - gap * (slots - 1)) / slots);
        int bx = x + 5;
        int base = ly + chartH;
        for (HallOfFame.NightMini n : h.recent) {
            int bh = Math.max(2, (int) Math.round((chartH - 2) * n.dealt / most));
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= ly && mouseY < base + 12;
            int color = n.held ? (hover ? 0xFFA0F0A8 : GREEN) : (hover ? 0xFFF08080 : RED);
            gui.fill(bx, base - bh, bx + bw, base, color);
            String label = "N" + n.night;
            small(gui, font, font.plainSubstrByWidth(label, (int) (bw / 0.6f) + 4), bx, base + 3, PurpleUi.GREY, 0.6f);
            List<String> tip = new ArrayList<>();
            tip.add(tr("nightsiege.hall.recent_tip_title", n.night, n.level, date(n.date)));
            tip.add((n.held ? "§a" : "§c") + tr(n.held ? "nightsiege.hall.result.held" : "nightsiege.hall.result.lost"));
            tip.add("§7" + subName(n.sub));
            tip.add("§7" + tr("nightsiege.hall.cat.dealt") + ": §f" + num(n.dealt));
            tip.add("§7" + tr("nightsiege.hall.cat.kills") + ": §f" + num(n.kills));
            tip.add("§7" + tr("nightsiege.hall.cat.deaths") + ": §f" + num(n.deaths));
            hit(bx, ly, bx + bw, base + 12, null, tip);
            bx += bw + gap;
        }
        return base + 16;
    }

    /** The number of nights a hero's chart shows (as on the server). */
    private static final int RECENT = 10;

    // ------------------------------------------------------------------ badges

    private static void drawBadges(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        HallOfFame.Hero hero = drawPicker(gui, font, x, y, w, mouseX, mouseY);
        if (hero == null) {
            return;
        }
        boolean mine = shownHero.equals(view.viewer);
        int[] count = new int[4];
        for (HallBadges.Badge b : HallBadges.ALL) {
            count[Math.max(0, Math.min(3, hero.badges.getOrDefault(b.id(), 0)))]++;
        }
        int top = y + 20;
        String sum = tr("nightsiege.hall.badge_count", count[3], count[2], count[1], HallBadges.ALL.size());
        gui.text(font, font.plainSubstrByWidth(sum, w), x, top, PurpleUi.LIGHT, true);
        top += 11;
        if (mine && count[3] > 0) {
            small(gui, font, font.plainSubstrByWidth(tr("nightsiege.hall.title_hint"), (int) (w / 0.75f)), x, top, PurpleUi.GREY, 0.75f);
            top += 9;
        }
        top += 2;
        int cols = Math.max(1, Math.min(3, (w - 5) / 170));
        int pw = (w - 5 - (cols - 1) * 4) / cols;
        int ph = 34;
        int bottom = y + h;
        beginScroll(gui, x, top, x + w, bottom);
        int yy = top - scroll;
        int i = 0;
        for (HallBadges.Badge b : HallBadges.ALL) {
            int px = x + (i % cols) * (pw + 4);
            int py = yy + (i / cols) * (ph + 4);
            plaque(gui, font, hero, b, mine, px, py, pw, ph, mouseX, mouseY, top, bottom);
            i++;
        }
        int rows = (HallBadges.ALL.size() + cols - 1) / cols;
        endScroll(gui, rows * (ph + 4), bottom - top);
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> BRONZE;
            case 2 -> SILVER;
            case 3 -> GOLD;
            default -> NONE;
        };
    }

    private static void plaque(GuiGraphicsExtractor gui, Font font, HallOfFame.Hero hero, HallBadges.Badge b, boolean mine,
                               int x, int y, int w, int h, int mouseX, int mouseY, int clipTop, int clipBottom) {
        int tier = hero.badges.getOrDefault(b.id(), 0);
        double value = HallBadges.value(b.id(), hero);
        double[] t = view.tiers.getOrDefault(b.id(), new double[]{b.bronze(), b.silver(), b.gold()});
        int color = tierColor(tier);
        boolean titled = b.id().equals(hero.title);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + h, clipBottom);
        PurpleUi.box(gui, x, y, x + w, y + h, hover ? PurpleUi.BOX_HOVER : 0xD0120F19);
        if (titled) {
            gui.fill(x, y, x + w, y + 1, GOLD);
            gui.fill(x, y + h - 1, x + w, y + h, GOLD);
        }
        // The medal: a framed square in the colour of the tier, the icon inside.
        int mx = x + 3;
        int my = y + 3;
        gui.fill(mx, my, mx + 28, my + 28, color);
        gui.fill(mx + 2, my + 2, mx + 26, my + 26, tier == 0 ? 0xFF15121C : 0xFF000000 | (color & 0xFEFEFE) >> 2);
        gui.item(CoreScreen.iconOf(b.icon()), mx + 6, my + 6);
        if (tier == 0) {
            gui.fill(mx + 2, my + 2, mx + 26, my + 26, 0x900E0C14);   // not reached yet: the icon fades
        }
        String name = tr("nightsiege.hall.badge." + b.id());
        String tierName = tier == 0 ? "" : tr("nightsiege.hall.tier." + tier);
        int tw = (int) (font.width(tierName) * 0.75f);
        gui.text(font, font.plainSubstrByWidth(name, w - 40 - tw - 4), x + 35, y + 4, tier == 0 ? PurpleUi.LIGHT : color, true);
        if (tier > 0) {
            small(gui, font, tierName, x + w - 4 - tw, y + 5, color, 0.75f);
        }
        // Progress to the next tier.
        int bx = x + 35;
        int bw = w - 40;
        int by = y + 16;
        gui.fill(bx, by, bx + bw, by + 5, 0xFF0E0C14);
        String prog;
        if (tier >= 3) {
            gui.fill(bx, by, bx + bw, by + 5, GOLD);
            prog = tr("nightsiege.hall.badge_max", badgeValue(b.id(), value));
        } else {
            double from = tier == 0 ? 0 : t[tier - 1];
            double to = t[tier];
            double f = to <= from ? 1 : Math.max(0, Math.min(1, (value - from) / (to - from)));
            gui.fill(bx, by, bx + (int) Math.round(bw * f), by + 5, tierColor(tier + 1));
            prog = badgeValue(b.id(), value) + " / " + badgeValue(b.id(), to) + "  → " + tr("nightsiege.hall.tier." + (tier + 1));
        }
        small(gui, font, font.plainSubstrByWidth(prog, (int) (bw / 0.75f)), bx, by + 8, PurpleUi.GREY, 0.75f);
        if (y + h <= clipTop || y >= clipBottom) {
            return;
        }
        List<String> tip = new ArrayList<>();
        tip.add(name + (tier > 0 ? " · " + tierName : ""));
        tip.add("§7" + tr("nightsiege.hall.badge." + b.id() + ".desc"));
        tip.add("§6" + tr("nightsiege.hall.tier.1") + " " + badgeValue(b.id(), t[0]) + " · §f" + tr("nightsiege.hall.tier.2") + " "
                + badgeValue(b.id(), t[1]) + " · §e" + tr("nightsiege.hall.tier.3") + " " + badgeValue(b.id(), t[2]));
        tip.add("§e" + tr("nightsiege.hall.title_label") + ": " + tr("nightsiege.hall.title." + b.id()));
        Runnable click = null;
        if (mine && tier >= 3) {
            tip.add("§8" + tr(titled ? "nightsiege.hall.title_remove" : "nightsiege.hall.title_take"));
            String id = b.id();
            click = () -> sender.accept("hall:title:" + (titled ? "" : id));
        }
        hit(x, Math.max(y, clipTop), x + w, Math.min(y + h, clipBottom), click, tip);
    }

    private static String badgeValue(String id, double v) {
        return "meisterschuetze".equals(id) ? tr("nightsiege.hall.unit.blocks", dec1(v)) : num(v);
    }

    // ------------------------------------------------------------------ curiosities

    private static void drawCurios(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        HallOfFame.Hero me = view.heroes.get(view.viewer);
        gui.text(font, font.plainSubstrByWidth(tr("nightsiege.hall.curios_intro"), w), x, y, PurpleUi.GREY, true);
        int top = y + 13;
        int bottom = y + h;
        int cols = w >= 460 ? 2 : 1;
        int cw = (w - 5 - (cols - 1) * 4) / cols;
        int chh = 40;
        beginScroll(gui, x, top, x + w, bottom);
        int yy = top - scroll;
        int i = 0;
        for (String key : HallOfFame.CURIOS) {
            int cx = x + (i % cols) * (cw + 4);
            int cy = yy + (i / cols) * (chh + 4);
            HallOfFame.Best best = view.curioBests.get(key);
            boolean summed = HallOfFame.SUMMED.contains(key);
            PurpleUi.box(gui, cx, cy, cx + cw, cy + chh, 0xD0120F19);
            gui.item(CoreScreen.iconOf(curioIcon(key)), cx + 4, cy + 4);
            String name = tr("nightsiege.hall.curio." + key);
            String kind = tr(summed ? "nightsiege.hall.curio_sum" : "nightsiege.hall.curio_best_night");
            int kw = (int) (font.width(kind) * 0.7f);
            gui.text(font, font.plainSubstrByWidth(name, cw - 30 - kw - 4), cx + 24, cy + 4, GOLD, true);
            small(gui, font, kind, cx + cw - 4 - kw, cy + 5, PurpleUi.DIM, 0.7f);
            String record = best == null ? tr("nightsiege.hall.curio_no_record")
                    : tr("nightsiege.hall.curio_record", curioValue(key, best.value), best.name);
            gui.text(font, font.plainSubstrByWidth(record, cw - 28), cx + 24, cy + 16, best != null && best.uuid.equals(view.viewer) ? GOLD : WHITE, true);
            double mine = me == null ? 0 : me.curios.getOrDefault(key, 0.0);
            String own = tr("nightsiege.hall.curio_mine", mine > 0 ? curioValue(key, mine) : "–");
            small(gui, font, font.plainSubstrByWidth(own, (int) ((cw - 28) / 0.8f)), cx + 24, cy + 28, PurpleUi.LIGHT, 0.8f);
            if (cy + chh > top && cy < bottom) {
                List<String> tip = new ArrayList<>();
                tip.add(name);
                tip.add("§7" + tr("nightsiege.hall.curio." + key + ".desc"));
                if (best != null && !best.date.isEmpty()) {
                    tip.add("§8" + tr("nightsiege.hall.curio_date", date(best.date)));
                }
                hit(cx, Math.max(cy, top), cx + cw, Math.min(cy + chh, bottom), null, tip);
            }
            i++;
        }
        int rows = (HallOfFame.CURIOS.size() + cols - 1) / cols;
        endScroll(gui, rows * (chh + 4), bottom - top);
    }

    private static String curioIcon(String key) {
        return switch (key) {
            case "weitesterTreffer" -> "minecraft:spectral_arrow";
            case "blutrausch" -> "minecraft:redstone";
            case "mehrfach" -> "minecraft:netherite_sword";
            case "creeper" -> "minecraft:creeper_head";
            case "kletterer" -> "minecraft:ladder";
            case "halbesHerz" -> "minecraft:glistering_melon_slice";
            case "ohneTreffer" -> "minecraft:feather";
            case "bossLetzterTreffer" -> "minecraft:wither_skeleton_skull";
            case "geschosse" -> "minecraft:arrow";
            case "traenke" -> "minecraft:splash_potion";
            case "sturmschritte" -> "minecraft:wind_charge";
            case "retter" -> "minecraft:golden_apple";
            case "nemesis" -> "minecraft:target";
            default -> "minecraft:book";
        };
    }

    private static String curioValue(String key, double v) {
        return switch (key) {
            case "weitesterTreffer" -> tr("nightsiege.hall.unit.blocks", dec1(v));
            case "ohneTreffer" -> tr("nightsiege.hall.unit.seconds", num(v));
            default -> num(v);
        };
    }

    // ------------------------------------------------------------------ bestiary

    private static void drawBestiary(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<String> types = new ArrayList<>(view.bestiary.keySet());
        types.sort(Comparator.comparingInt((String t) -> -view.bestiary.get(t).seen).thenComparing(t -> t));
        int killed = 0;
        for (HallOfFame.Beast b : view.bestiary.values()) {
            killed += b.killed;
        }
        String head = tr("nightsiege.hall.beast_intro", types.size(), num(killed), view.unseen.size());
        gui.text(font, font.plainSubstrByWidth(head, w), x, y, PurpleUi.GREY, true);
        int top = y + 13;
        int bottom = y + h;
        List<String> all = new ArrayList<>(types);
        all.addAll(view.unseen);
        if (all.isEmpty()) {
            centered(gui, font, tr("nightsiege.hall.empty"), x + w / 2, top + 40, PurpleUi.GREY);
            return;
        }
        int cols = Math.max(1, (w - 5) / 120);
        int tw = (w - 5 - (cols - 1) * 4) / cols;
        int th = 38;
        beginScroll(gui, x, top, x + w, bottom);
        int yy = top - scroll;
        int i = 0;
        for (String type : all) {
            int tx = x + (i % cols) * (tw + 4);
            int ty = yy + (i / cols) * (th + 4);
            HallOfFame.Beast b = view.bestiary.get(type);
            boolean seen = b != null;
            boolean boss = type.startsWith("boss:");
            boolean elite = type.endsWith("+elite");
            int frame = boss ? 0xFFE05A5A : elite ? 0xFFB565FF : PurpleUi.LINE;
            gui.fill(tx, ty, tx + tw, ty + th, frame);
            gui.fill(tx + 1, ty + 1, tx + tw - 1, ty + th - 1, seen ? 0xD0120F19 : 0xD0080610);
            if (seen) {
                gui.item(beastIcon(type), tx + 4, ty + 4);
                String name = beastName(type);
                gui.text(font, font.plainSubstrByWidth(name, tw - 26), tx + 23, ty + 4, boss ? RED : elite ? 0xFFD8A0FF : WHITE, true);
                small(gui, font, tr("nightsiege.hall.beast_seen", num(b.seen)), tx + 23, ty + 15, PurpleUi.LIGHT, 0.7f);
                small(gui, font, tr("nightsiege.hall.beast_killed", num(b.killed)), tx + 23, ty + 22, PurpleUi.LIGHT, 0.7f);
                if (b.killedPlayers > 0) {
                    small(gui, font, tr("nightsiege.hall.beast_players", num(b.killedPlayers)), tx + 23, ty + 29, RED, 0.7f);
                }
            } else {
                // Never met: a black shape and question marks.
                gui.fill(tx + 6, ty + 6, tx + 18, ty + 18, 0xFF000000);
                gui.text(font, "?", tx + 10, ty + 8, 0xFF3A3450, false);
                gui.text(font, "???", tx + 23, ty + 4, PurpleUi.DIM, true);
                small(gui, font, tr("nightsiege.hall.beast_unseen"), tx + 23, ty + 15, PurpleUi.DIM, 0.7f);
            }
            if (ty + th > top && ty < bottom) {
                List<String> tip = new ArrayList<>();
                if (seen) {
                    tip.add(beastName(type));
                    tip.add("§7" + tr("nightsiege.hall.beast_seen", num(b.seen)));
                    tip.add("§7" + tr("nightsiege.hall.beast_killed", num(b.killed)));
                    tip.add("§c" + tr("nightsiege.hall.beast_players", num(b.killedPlayers)));
                    tip.add("§c" + tr("nightsiege.hall.beast_damage", num(b.damageToPlayers)));
                } else {
                    tip.add("???");
                    tip.add("§8" + tr("nightsiege.hall.beast_unseen_tip"));
                }
                hit(tx, Math.max(ty, top), tx + tw, Math.min(ty + th, bottom), null, tip);
            }
            i++;
        }
        int rows = (all.size() + cols - 1) / cols;
        endScroll(gui, rows * (th + 4), bottom - top);
    }

    /** The name of a logger monster type: a new attacker's own name, else the vanilla monster's. */
    private static String beastName(String type) {
        String plain = type;
        boolean boss = plain.startsWith("boss:");
        if (boss) {
            plain = plain.substring(5);
        }
        boolean elite = plain.endsWith("+elite");
        if (elite) {
            plain = plain.substring(0, plain.length() - 6);
        }
        String name;
        if ("maschine".equals(plain)) {
            name = tr("nightsiege.hall.beast_machine");
        } else if (plain.startsWith("nightsiege:")) {
            String key = "nightsiege.foe." + plain.substring("nightsiege:".length());
            name = Language.getInstance().has(key) ? tr(key) : plain.substring("nightsiege:".length());
        } else {
            int colon = plain.indexOf(':');
            String key = colon < 0 ? "entity.minecraft." + plain : "entity." + plain.substring(0, colon) + "." + plain.substring(colon + 1);
            name = Language.getInstance().has(key) ? tr(key) : plain;
        }
        if (elite) {
            name = tr("nightsiege.hall.beast_elite", name);
        }
        if (boss) {
            name = tr("nightsiege.hall.beast_boss", name);
        }
        return name;
    }

    private static ItemStack beastIcon(String type) {
        String look = view == null ? null : view.beastLooks.get(type);
        if (look == null) {
            look = type.startsWith("boss:") ? type.substring(5) : type;
            if (look.endsWith("+elite")) {
                look = look.substring(0, look.length() - 6);
            }
        }
        ItemStack egg = CoreScreen.iconOf(look + "_spawn_egg");
        return egg.isEmpty() ? CoreScreen.iconOf("minecraft:skeleton_skull") : egg;
    }

    // ------------------------------------------------------------------ scrolling

    private static int scrollTop;
    private static int scrollBottom;
    private static int scrollRight;

    private static void beginScroll(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2) {
        scrollArea = new int[]{x1, y1, x2, y2};
        clip = scrollArea;
        scrollTop = y1;
        scrollBottom = y2;
        scrollRight = x2;
        gui.enableScissor(x1, y1, x2, y2);
    }

    private static void endScroll(GuiGraphicsExtractor gui, int contentH, int viewH) {
        gui.disableScissor();
        clip = null;
        maxScroll = Math.max(0, contentH - viewH + 2);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (maxScroll > 0) {
            int track = scrollBottom - scrollTop;
            int knob = Math.max(12, track * viewH / Math.max(1, contentH));
            int ky = scrollTop + (int) ((track - knob) * (scroll / (double) maxScroll));
            gui.fill(scrollRight - 3, scrollTop, scrollRight - 1, scrollBottom, 0x40FFFFFF);
            gui.fill(scrollRight - 3, ky, scrollRight - 1, ky + knob, GOLD);
        }
    }

    // ------------------------------------------------------------------ figures

    /** A defender to stand in the window: the real one when near, else a figure with their name (and skin, when known). */
    private static LivingEntity figure(String uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || uuid == null || BROKEN.contains(uuid)) {
            return null;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.getUUID().toString().equals(uuid)) {
                return p;
            }
        }
        if (figuresLevel != mc.level) {
            FIGURES.clear();
            BROKEN.clear();
            figuresLevel = mc.level;
        }
        RemotePlayer fig = FIGURES.get(uuid);
        if (fig == null) {
            try {
                String n = name == null || name.isBlank() ? "?" : name.length() > 16 ? name.substring(0, 16) : name;
                fig = new RemotePlayer(mc.level, new GameProfile(UUID.fromString(uuid), n));
                FIGURES.put(uuid, fig);
            } catch (RuntimeException e) {
                BROKEN.add(uuid);
                return null;
            }
        }
        return fig;
    }

    // ------------------------------------------------------------------ text

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }

    private static void centered(GuiGraphicsExtractor gui, Font font, String text, int cx, int y, int color) {
        gui.text(font, text, cx - font.width(text) / 2, y, color, true);
    }

    /** A whole number with the thousands mark of the language ("12.345" in German). */
    static String num(double v) {
        long n = Math.round(v);
        String sep = tr("nightsiege.hall.sep");
        String digits = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(sep);
            }
            out.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + out;
    }

    /** One decimal with the decimal mark of the language ("34,5" in German). */
    static String dec1(double v) {
        long tenths = Math.round(v * 10);
        return num(tenths / 10) + tr("nightsiege.hall.decimal") + Math.abs(tenths % 10);
    }

    /** "2026-10-30" in the language's short form ("30.10." in German). */
    private static String date(String iso) {
        if (iso == null || iso.length() < 10 || iso.charAt(4) != '-' || iso.charAt(7) != '-') {
            return iso == null ? "" : iso;
        }
        return tr("nightsiege.hall.date", iso.substring(0, 4), iso.substring(5, 7), iso.substring(8, 10));
    }

    private static String valueText(String cat, double v) {
        return num(v);
    }

    private static String subName(String sub) {
        if (sub == null || sub.isEmpty()) {
            return "–";
        }
        String key = "nightsiege.subclass." + sub;
        if (Language.getInstance().has(key)) {
            return tr(key);
        }
        key = "nightsiege.class." + sub;
        return Language.getInstance().has(key) ? tr(key) : sub;
    }

    private static int classColor(String clazz) {
        return CoreColors.classColor(com.mki.nightsiege.core.SkillClass.byName(clazz));
    }

    private static Map.Entry<String, Integer> topInt(Map<String, Integer> m) {
        Map.Entry<String, Integer> best = null;
        if (m != null) {
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0 && (best == null || e.getValue() > best.getValue())) {
                    best = e;
                }
            }
        }
        return best;
    }

    private static Map.Entry<String, Double> topDouble(Map<String, Double> m) {
        Map.Entry<String, Double> best = null;
        if (m != null) {
            for (Map.Entry<String, Double> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0 && (best == null || e.getValue() > best.getValue())) {
                    best = e;
                }
            }
        }
        return best;
    }
}
