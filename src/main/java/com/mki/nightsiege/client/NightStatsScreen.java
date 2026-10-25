package com.mki.nightsiege.client;

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

    public NightStatsScreen(NightStatsData data) {
        super(Component.translatable("nightsiege.stats.title"));
        this.data = data;
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
        h = Math.min(this.height - 8, 78 + shownRows() * ROW_H + 32);
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        scroll = Math.max(0, Math.min(scroll, data.rows.size() - shownRows()));
        Button close = Button.builder(Component.translatable("nightsiege.stats.close"), b -> onClose())
                .bounds(left + w - 8 - 90, top + h - 8 - 20, 90, 20).build();
        addRenderableWidget(close);
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
        drawHeader(gui, font);
        drawTable(gui, font);
        super.extractRenderState(gui, mouseX, mouseY, delta);
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
