package com.mki.nightsiege.client;

import com.mki.nightsiege.core.NightRecords;
import com.mki.nightsiege.core.NightStatsData;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * After a night (Simon's playtest, 25 October 2026): how it went and a table with every
 * defender's kills, damage dealt and taken, healing and falls. The best values of a
 * column are gold, the night's MVP gets a star. Opens a moment after the night ends,
 * once no other window is open; /nightsiege statistik (or the line in the chat) shows
 * it again.
 */
public class NightStatsScreen extends Screen {

    private static final int BACKDROP = 0xB0000000;
    private static final int FRAME = 0xFF3A3450;
    private static final int PANEL = 0xF0121019;
    private static final int BOX = 0xFF1A1622;
    private static final int BOX_DARK = 0xFF0E0C14;
    private static final int LINE = 0xFF2C2740;
    private static final int GOLD = 0xFFFFD86B;
    private static final int RED = 0xFFE05A5A;
    private static final int GREEN = 0xFF7FE08A;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int WHITE = 0xFFFFFFFF;

    /** Rows shown at most; more defenders scroll with the mouse wheel. */
    private static final int MAX_ROWS = 8;
    private static final int ROW_H = 20;

    private static NightStatsData pending;
    private static int pendingDelay;
    private static int pendingWait;

    private final NightStatsData data;
    private int left;
    private int top;
    private int w;
    private int h;
    private int scroll;
    /** The records page (content update, 29 October 2026) instead of the night's table. */
    private boolean records;
    /** The class whose subclasses the records page shows, or null. */
    private String openClass;
    private static final String[] RECORD_CLASSES = {"ARCHER", "WARRIOR", "GUARDIAN", "ENGINEER", "SUMMONER"};
    private int recordTop;

    public NightStatsScreen(NightStatsData data) {
        super(Component.translatable("nightsiege.stats.title"));
        this.data = data;
        this.records = data.recordsOnly || data.openRecords;
    }

    /** The night just ended: the window comes in a moment, when no other window is open. */
    public static void queue(NightStatsData data) {
        pending = data;
        pendingDelay = 50;
        pendingWait = 1200;
    }

    /** Every client tick. */
    public static void tick(Minecraft client) {
        if (pending == null) {
            return;
        }
        if (client.player == null) {
            pending = null;
            return;
        }
        if (pendingDelay > 0) {
            pendingDelay--;
            return;
        }
        if (client.gui.screen() == null) {
            client.setScreenAndShow(new NightStatsScreen(pending));
            pending = null;
        } else if (--pendingWait <= 0) {
            pending = null;   // the line in the chat still opens it
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int shownRows() {
        return Math.max(1, Math.min(MAX_ROWS, data.rows.size()));
    }

    @Override
    protected void init() {
        w = Math.max(340, Math.min(520, this.width - 8));
        h = records ? Math.min(this.height - 8, 300) : Math.min(this.height - 8, 78 + shownRows() * ROW_H + 32);
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        scroll = Math.max(0, Math.min(scroll, data.rows.size() - shownRows()));
        Button close = Button.builder(Component.translatable("nightsiege.stats.close"), b -> onClose())
                .bounds(left + w - 8 - 90, top + h - 8 - 20, 90, 20).build();
        addRenderableWidget(close);
        // The two pages: the night and the records.
        int bx = left + 8;
        int by = top + h - 8 - 20;
        if (!data.recordsOnly) {
            Component night = Component.translatable("nightsiege.stats.tab.night");
            Button nb = Button.builder(night, b -> {
                records = false;
                rebuildWidgets();
            }).bounds(bx, by, Minecraft.getInstance().font.width(night) + 16, 20).build();
            nb.active = records;
            addRenderableWidget(nb);
            bx += nb.getWidth() + 4;
        }
        if (data.records != null) {
            Component rec = Component.translatable("nightsiege.stats.tab.records");
            Button rb = Button.builder(rec, b -> {
                records = true;
                rebuildWidgets();
            }).bounds(bx, by, Minecraft.getInstance().font.width(rec) + 16, 20).build();
            rb.active = !records;
            addRenderableWidget(rb);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (records && data.records != null && event.x() >= left + 8 && event.x() < left + w - 8) {
            // A click on a class row opens (or closes) its subclasses.
            int y = recordTop;
            for (String clazz : RECORD_CLASSES) {
                if (event.y() >= y && event.y() < y + 22) {
                    openClass = clazz.equals(openClass) ? null : clazz;
                    return true;
                }
                y += 22 + (clazz.equals(openClass) ? 4 * 16 : 0);
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, data.rows.size() - shownRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
        return true;
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, BACKDROP);
        int x2 = left + w;
        int y2 = top + h;
        gui.fill(left - 1, top - 1, x2 + 1, y2 + 1, FRAME);
        gui.fill(left, top, x2, y2, PANEL);
        gui.fill(left, top, x2, top + 1, data.held() ? 0xFF6A8A4A : 0xFF8A3A3A);
        if (records) {
            drawRecords(gui, font, mouseX, mouseY);
        } else {
            drawHeader(gui, font);
            drawTable(gui, font);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
        PurpleUi.skinButtons(gui, Minecraft.getInstance().font, children(), null, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor gui, Font font) {
        String event = data.event == null || data.event.isBlank() || "none".equals(data.event) ? ""
                : Component.translatable("nightsiege.event." + data.event).getString();
        String line = Component.translatable(data.test ? "nightsiege.stats.sub_test" : "nightsiege.stats.sub",
                data.level).getString() + (event.isEmpty() ? "" : " · " + event);
        small(gui, font, line.toUpperCase(Locale.ROOT), left + 10, top + 8, GREY, 0.75f);
        String title = Component.translatable("nightsiege.stats.result." + data.result).getString();
        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 18);
        gui.pose().scale(1.3f, 1.3f);
        gui.text(font, title, 0, 0, data.held() ? GREEN : RED, true);
        gui.pose().popMatrix();

        // Three boxes on the right: kill goal, waves, time.
        int bw = 62;
        int by = top + 6;
        int bx = left + w - 8 - 3 * bw - 8;
        boolean goal = data.required <= 0 || data.kills >= data.required;
        box(gui, font, bx, by, bw, Component.translatable("nightsiege.stats.kill_goal").getString(),
                data.required > 0 ? data.kills + " / " + data.required : String.valueOf(data.kills), goal ? GREEN : RED);
        box(gui, font, bx + bw + 4, by, bw, Component.translatable("nightsiege.stats.waves").getString(),
                String.valueOf(data.waves), WHITE);
        box(gui, font, bx + 2 * (bw + 4), by, bw, Component.translatable("nightsiege.stats.time").getString(),
                (data.seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", data.seconds % 60), WHITE);
    }

    private void box(GuiGraphicsExtractor gui, Font font, int x, int y, int bw, String label, String value, int color) {
        gui.fill(x, y, x + bw, y + 28, LINE);
        gui.fill(x + 1, y + 1, x + bw - 1, y + 27, BOX_DARK);
        small(gui, font, label, x + 5, y + 4, GREY, 0.65f);
        float scale = Math.min(1.25f, (bw - 10f) / Math.max(1, font.width(value)));
        gui.pose().pushMatrix();
        gui.pose().translate(x + 5, y + 13);
        gui.pose().scale(scale, scale);
        gui.text(font, value, 0, 0, color, true);
        gui.pose().popMatrix();
    }

    /** Column x positions: name and class on the left, the numbers right-aligned after. */
    private int[] columns() {
        int numbers = 5;
        int numW = Math.max(46, Math.min(62, (w - 20 - 170) / numbers));
        int first = left + w - 10 - numbers * numW;
        return new int[]{left + 10, left + 10 + Math.min(110, (first - left - 10) / 2), first, numW};
    }

    private void drawTable(GuiGraphicsExtractor gui, Font font) {
        int[] col = columns();
        int numW = col[3];
        int y = top + 44;
        gui.fill(left + 8, y, left + w - 8, y + 14, BOX_DARK);
        small(gui, font, Component.translatable("nightsiege.stats.col.player").getString(), col[0] + 2, y + 4, GREY, 0.7f);
        small(gui, font, Component.translatable("nightsiege.stats.col.class").getString(), col[1], y + 4, GREY, 0.7f);
        String[] heads = {"kills", "dealt", "taken", "healed", "deaths"};
        for (int i = 0; i < heads.length; i++) {
            String head = Component.translatable("nightsiege.stats.col." + heads[i]).getString();
            int right = col[2] + (i + 1) * numW - 4;
            small(gui, font, head, right - (int) (font.width(head) * 0.7f), y + 4, GREY, 0.7f);
        }
        // The best value of each column (fewest falls).
        double[] best = {0, 0, 0, 0, Double.MAX_VALUE};
        for (NightStatsData.Row row : data.rows) {
            best[0] = Math.max(best[0], row.kills);
            best[1] = Math.max(best[1], row.dealt);
            best[2] = Math.max(best[2], row.taken);
            best[3] = Math.max(best[3], row.healed);
            best[4] = Math.min(best[4], row.deaths);
        }
        String own = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getScoreboardName();
        y += 16;
        for (int r = 0; r < shownRows() && r + scroll < data.rows.size(); r++) {
            int index = r + scroll;
            NightStatsData.Row row = data.rows.get(index);
            boolean mvp = index == data.mvp;
            int rowColor = row.name.equals(own) ? 0xFF241E33 : BOX;
            gui.fill(left + 8, y, left + w - 8, y + ROW_H - 2, mvp ? 0xFF4A3E1C : LINE);
            gui.fill(left + 9, y + 1, left + w - 9, y + ROW_H - 3, rowColor);
            // Name, with the star of the MVP.
            String name = font.plainSubstrByWidth(row.name, col[1] - col[0] - 18);
            gui.text(font, name, col[0] + 2, y + 5, mvp ? GOLD : WHITE, true);
            if (mvp) {
                small(gui, font, "★ MVP", col[0] + 4 + font.width(name), y + 6, GOLD, 0.7f);
            }
            // Class icon and name (with the subclass).
            SkillClass clazz = SkillClass.byName(row.clazz);
            gui.pose().pushMatrix();
            gui.pose().translate(col[1], y + 2);
            gui.pose().scale(0.875f, 0.875f);
            gui.item(new ItemStack(item(icon(clazz))), 0, 0);
            gui.pose().popMatrix();
            String cls = clazz == null ? "–" : Component.translatable(clazz.translationKey()).getString();
            if (row.subclass != null && !row.subclass.isBlank()) {
                cls = Component.translatable("nightsiege.subclass." + row.subclass).getString();
            }
            cls = font.plainSubstrByWidth(cls, (int) ((col[2] - col[1] - 22) / 0.75f));
            small(gui, font, cls, col[1] + 17, y + 6, LIGHT, 0.75f);
            // The numbers.
            String[] values = {String.valueOf(row.kills), num(row.dealt), num(row.taken), num(row.healed),
                    String.valueOf(row.deaths)};
            boolean[] top = {row.kills > 0 && row.kills >= best[0], row.dealt > 0 && row.dealt >= best[1],
                    row.taken > 0 && row.taken >= best[2], row.healed > 0 && row.healed >= best[3],
                    data.rows.size() > 1 && row.deaths <= best[4]};
            for (int i = 0; i < values.length; i++) {
                int right = col[2] + (i + 1) * numW - 4;
                int color = i == 4 && row.deaths > 0 ? RED : top[i] && i != 2 ? GOLD : WHITE;
                gui.text(font, values[i], right - font.width(values[i]), y + 5, color, true);
            }
            y += ROW_H;
        }
        if (data.rows.size() > shownRows()) {
            String more = Component.translatable("nightsiege.stats.scroll", scroll + 1,
                    Math.min(data.rows.size(), scroll + shownRows()), data.rows.size()).getString();
            small(gui, font, more, left + 10, top + h - 8 - 13, GREY, 0.7f);
        } else {
            small(gui, font, Component.translatable("nightsiege.stats.hint").getString(), left + 10, top + h - 8 - 13, GREY, 0.7f);
        }
    }

    // ------------------------------------------------------------------
    // the records page
    // ------------------------------------------------------------------

    private void drawRecords(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        NightRecords.View v = data.records;
        small(gui, font, Component.translatable("nightsiege.records.sub").getString().toUpperCase(Locale.ROOT),
                left + 10, top + 8, GREY, 0.75f);
        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 18);
        gui.pose().scale(1.3f, 1.3f);
        gui.text(font, Component.translatable("nightsiege.stats.tab.records").getString(), 0, 0, GOLD, true);
        gui.pose().popMatrix();
        if (v == null) {
            return;
        }
        // The streaks on the right of the title.
        String streak = Component.translatable("nightsiege.records.server_streak", v.serverStreak, v.serverBest).getString();
        small(gui, font, streak, left + w - 10 - (int) (font.width(streak) * 0.75f), top + 9, LIGHT, 0.75f);
        String best = v.streakBest > 0
                ? Component.translatable("nightsiege.records.best_streak", v.streakName, v.streakBest).getString()
                : Component.translatable("nightsiege.records.no_streak").getString();
        small(gui, font, best, left + w - 10 - (int) (font.width(best) * 0.75f), top + 19, GOLD, 0.75f);
        String mine = Component.translatable("nightsiege.records.my_streak", v.myStreak, v.myBest).getString();
        small(gui, font, mine, left + w - 10 - (int) (font.width(mine) * 0.75f), top + 29, GREY, 0.75f);

        int y = top + 44;
        int nameW = Math.min(130, (w - 20) / 4);
        int colW = (w - 20 - nameW) / NightRecords.STATS.length;
        gui.fill(left + 8, y, left + w - 8, y + 14, BOX_DARK);
        small(gui, font, Component.translatable("nightsiege.stats.col.class").getString(), left + 12, y + 4, GREY, 0.7f);
        for (int i = 0; i < NightRecords.STATS.length; i++) {
            String head = Component.translatable("nightsiege.records.stat." + NightRecords.STATS[i]).getString();
            int right = left + 10 + nameW + (i + 1) * colW - 4;
            small(gui, font, head, right - (int) (font.width(head) * 0.7f), y + 4, GREY, 0.7f);
        }
        y += 16;
        recordTop = y;
        boolean any = !v.classes.isEmpty();
        for (String clazz : RECORD_CLASSES) {
            boolean open = clazz.equals(openClass);
            boolean hover = mouseX >= left + 8 && mouseX < left + w - 8 && mouseY >= y && mouseY < y + 20;
            gui.fill(left + 8, y, left + w - 8, y + 20, open ? 0xFF4A3E1C : LINE);
            gui.fill(left + 9, y + 1, left + w - 9, y + 19, hover ? 0xFF241E33 : BOX);
            SkillClass c = SkillClass.byName(clazz);
            gui.pose().pushMatrix();
            gui.pose().translate(left + 12, y + 2);
            gui.pose().scale(0.875f, 0.875f);
            gui.item(new ItemStack(item(icon(c))), 0, 0);
            gui.pose().popMatrix();
            String name = (open ? "▼ " : "▶ ") + (c == null ? clazz : Component.translatable(c.translationKey()).getString());
            gui.text(font, font.plainSubstrByWidth(name, nameW - 24), left + 30, y + 6, WHITE, true);
            for (int i = 0; i < NightRecords.STATS.length; i++) {
                String stat = NightRecords.STATS[i];
                cell(gui, font, find(v.classes, v.classStats, clazz, null, stat), find(v.mine, v.mineStats, clazz, null, stat),
                        stat, left + 10 + nameW + i * colW, y, colW);
            }
            y += 22;
            if (open) {
                for (String sub : subclassesOf(clazz)) {
                    gui.fill(left + 20, y, left + w - 8, y + 15, BOX_DARK);
                    String subName = Component.translatable("nightsiege.subclass." + sub).getString();
                    small(gui, font, font.plainSubstrByWidth(subName, (int) ((nameW - 20) / 0.8f)), left + 26, y + 4, LIGHT, 0.8f);
                    for (int i = 0; i < NightRecords.STATS.length; i++) {
                        String stat = NightRecords.STATS[i];
                        NightRecords.Entry e = find(v.subclasses, v.subStats, clazz, sub, stat);
                        String text = e == null ? "–" : value(stat, e.value) + "  " + e.player;
                        int right = left + 10 + nameW + (i + 1) * colW - 4;
                        text = font.plainSubstrByWidth(text, (int) ((colW - 6) / 0.7f));
                        small(gui, font, text, right - (int) (font.width(text) * 0.7f), y + 5, e == null ? GREY : LIGHT, 0.7f);
                    }
                    y += 16;
                }
            }
        }
        String hint = Component.translatable(any ? "nightsiege.records.hint" : "nightsiege.records.empty").getString();
        small(gui, font, font.plainSubstrByWidth(hint, (int) ((w - 230) / 0.7f)), left + 10, top + h - 8 - 32, GREY, 0.7f);
    }

    /** One record cell: the value large, who holds it small; the viewer's own best when it is not the record. */
    private void cell(GuiGraphicsExtractor gui, Font font, NightRecords.Entry e, NightRecords.Entry own, String stat,
                      int x, int y, int colW) {
        int right = x + colW - 4;
        String value = e == null ? "–" : value(stat, e.value);
        String self = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getScoreboardName();
        boolean mine = e != null && e.player.equals(self);
        gui.text(font, value, right - font.width(value), y + 2, e == null ? GREY : mine ? GOLD : WHITE, true);
        if (e != null) {
            String who = e.player;
            if (own != null && !mine) {
                who += " · " + Component.translatable("nightsiege.records.you", value(stat, own.value)).getString();
            }
            who = font.plainSubstrByWidth(who, (int) ((colW - 6) / 0.6f));
            small(gui, font, who, right - (int) (font.width(who) * 0.6f), y + 12, mine ? GOLD : GREY, 0.6f);
        }
    }

    private static NightRecords.Entry find(java.util.List<NightRecords.Entry> list, java.util.List<String> stats,
                                           String clazz, String sub, String stat) {
        for (int i = 0; i < list.size() && i < stats.size(); i++) {
            NightRecords.Entry e = list.get(i);
            if (stats.get(i).equals(stat) && clazz.equals(e.clazz) && (sub == null || sub.equals(e.subclass))) {
                return e;
            }
        }
        return null;
    }

    private static java.util.List<String> subclassesOf(String clazz) {
        java.util.List<String> out = new java.util.ArrayList<>();
        SkillClass c = SkillClass.byName(clazz);
        for (com.mki.nightsiege.core.SkillTree.SubclassInfo info : com.mki.nightsiege.core.SkillTree.subclassInfosOf(c)) {
            out.add(info.key());
        }
        return out;
    }

    private static String value(String stat, double v) {
        return "kills".equals(stat) ? String.valueOf((int) Math.round(v)) : num(v) + " ❤";
    }

    /** Damage and healing in hearts, as the players think of them. */
    private static String num(double halfHearts) {
        double hearts = halfHearts / 2.0;
        return hearts >= 100 ? String.valueOf(Math.round(hearts)) : String.format(Locale.GERMANY, "%.1f", hearts);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    private static String icon(SkillClass clazz) {
        if (clazz == null) {
            return "minecraft:barrier";
        }
        return switch (clazz) {
            case WARRIOR -> "minecraft:iron_sword";
            case ARCHER -> "minecraft:bow";
            case GUARDIAN -> "minecraft:shield";
            case SUMMONER -> "nightsiege:summoning_staff";
            default -> "nightsiege:arrow_turret";
        };
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("chest")) : item;
    }
}
