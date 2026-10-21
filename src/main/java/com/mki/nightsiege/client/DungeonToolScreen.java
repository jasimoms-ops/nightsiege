package com.mki.nightsiege.client;

import com.mki.nightsiege.core.DungeonMath;
import com.mki.nightsiege.core.DungeonTheme;
import com.mki.nightsiege.core.DungeonTier;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.DungeonToolState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Creative test tool for dungeons: every dungeon kind in a list, with travel,
 * a test fight with free settings right where you stand, and reset.
 */
public class DungeonToolScreen extends Screen {

    private static final int W = 384;
    private static final int H = 232;
    private static final int ROW = 17;
    private static final int ROWS = 10;
    private static final int LIST_W = 178;

    private static final int FRAME_FILL = 0xFFC6C6C6;
    private static final int FRAME_LIGHT = 0xFFFFFFFF;
    private static final int FRAME_SHADOW = 0xFF555555;
    private static final int FRAME_DARK = 0xFF373737;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GOLD = 0xFFFFAA00;
    private static final int PURPLE = 0xFFD58CFF;
    private static final Identifier BACKDROP = Identifier.parse("minecraft:textures/block/polished_blackstone_bricks.png");

    private static DungeonToolScreen showing;
    // Kept between openings.
    private static int selected;
    private static int scroll;
    private static String settingsFor;
    private static int waves;
    private static DungeonTier tier;
    private static int progress;
    private static boolean boss = true;
    private static DungeonTheme theme;

    private DungeonToolState state;
    private int left;
    private int top;

    public DungeonToolScreen(DungeonToolState state) {
        super(Component.translatable("nightsiege.tool.title"));
        this.state = state;
    }

    public static DungeonToolScreen openInstance() {
        return showing;
    }

    public void update(DungeonToolState fresh) {
        this.state = fresh;
        rebuildWidgets();
    }

    @Override
    public void removed() {
        if (showing == this) {
            showing = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DungeonToolState.Entry current() {
        if (state.entries.isEmpty()) {
            return null;
        }
        selected = Math.max(0, Math.min(state.entries.size() - 1, selected));
        return state.entries.get(selected);
    }

    /** Settings start at the kind's own values whenever another kind is picked. */
    private void defaultsFor(DungeonToolState.Entry entry) {
        if (entry == null || entry.key.equals(settingsFor)) {
            return;
        }
        settingsFor = entry.key;
        tier = DungeonTier.byName(entry.tier);
        if (tier == null) {
            tier = DungeonTier.EASY;
        }
        theme = DungeonTheme.byName(entry.theme);
        if (theme == null) {
            theme = DungeonTheme.RUINS;
        }
        waves = tier.waves();
        progress = 0;
        boss = true;
    }

    @Override
    protected void init() {
        showing = this;
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        DungeonToolState.Entry entry = current();
        defaultsFor(entry);
        int maxScroll = Math.max(0, state.entries.size() - ROWS);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        // The list: invisible buttons over the drawn rows.
        int listY = top + 28;
        for (int i = 0; i < ROWS && scroll + i < state.entries.size(); i++) {
            int index = scroll + i;
            Button row = Button.builder(Component.empty(), b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(left + 8, listY + i * ROW, LIST_W, ROW).build();
            row.setAlpha(0f);
            addRenderableWidget(row);
        }

        if (entry == null) {
            return;
        }
        int rx = left + LIST_W + 18;
        int rw = W - LIST_W - 26;
        int y = top + 92;
        int small = 16;
        int valueW = 54;
        int labelW = rw - 2 * small - valueW - 4;
        // Waves
        stepper(rx + labelW, y, small, valueW,
                () -> waves = Math.max(1, waves - 1), () -> waves = Math.min(9, waves + 1));
        y += 18;
        // Tier
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.dungeon.tier." + tier.key()), b -> {
            tier = DungeonTier.values()[(tier.ordinal() + 1) % DungeonTier.values().length];
            rebuildWidgets();
        }).bounds(rx + labelW, y, 2 * small + valueW + 4, 16).build());
        y += 18;
        // Progress in spent points, in steps of 25
        stepper(rx + labelW, y, small, valueW,
                () -> progress = Math.max(0, progress - DungeonMath.POINTS_PER_LEVEL),
                () -> progress = Math.min(DungeonMath.POINTS_PER_LEVEL * DungeonMath.MAX_PROGRESS_LEVELS, progress + DungeonMath.POINTS_PER_LEVEL));
        y += 18;
        // Boss
        addRenderableWidget(Button.builder(Component.translatable(boss ? "options.on" : "options.off"), b -> {
            boss = !boss;
            rebuildWidgets();
        }).bounds(rx + labelW, y, 2 * small + valueW + 4, 16).build());
        y += 18;
        // Theme
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.dungeon.theme." + theme.key()), b -> {
            theme = DungeonTheme.values()[(theme.ordinal() + 1) % DungeonTheme.values().length];
            rebuildWidgets();
        }).bounds(rx + labelW, y, 2 * small + valueW + 4, 16).build());

        int by = top + H - 48;
        int half = (rw - 4) / 2;
        String key = entry.key;
        Button travel = Button.builder(Component.translatable("nightsiege.tool.travel"), b -> {
            send("tp:" + key);
            onClose();
        }).bounds(rx, by, half, 18).build();
        travel.active = entry.installed;
        addRenderableWidget(travel);
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.tool.test"), b -> {
            send("test:" + key + ":" + waves + ":" + tier.name() + ":" + progress + ":" + (boss ? 1 : 0) + ":" + theme.name());
            onClose();
        }).bounds(rx + half + 4, by, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.tool.reset"), b -> send("reset:" + key))
                .bounds(rx, by + 22, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.tool.reset_all"), b -> send("reset"))
                .bounds(rx + half + 4, by + 22, half, 18).build());
    }

    private void stepper(int x, int y, int small, int valueW, Runnable down, Runnable up) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            down.run();
            rebuildWidgets();
        }).bounds(x, y, small, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            up.run();
            rebuildWidgets();
        }).bounds(x + small + valueW + 4, y, small, 16).build());
    }

    private static void send(String action) {
        ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("dungeon:" + action));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, state.entries.size() - ROWS);
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        drawWindow(gui, font);
        drawList(gui, font, mouseX, mouseY);
        drawDetails(gui, font);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawWindow(GuiGraphicsExtractor gui, Font font) {
        int x2 = left + W;
        int y2 = top + H;
        gui.fill(left, top, x2, y2, 0xFF000000);
        gui.fill(left + 1, top + 1, x2 - 1, y2 - 1, FRAME_FILL);
        gui.fill(left + 1, top + 1, x2 - 1, top + 3, FRAME_LIGHT);
        gui.fill(left + 1, top + 1, left + 3, y2 - 1, FRAME_LIGHT);
        gui.fill(left + 1, y2 - 3, x2 - 1, y2 - 1, FRAME_SHADOW);
        gui.fill(x2 - 3, top + 1, x2 - 1, y2 - 1, FRAME_SHADOW);
        // Banner
        int bx1 = left + 4;
        int bx2 = x2 - 4;
        int by1 = top + 4;
        gui.fill(bx1, by1, bx2, by1 + 20, 0xFF17161B);
        for (int i = 0; i < 40; i++) {
            int alpha = (int) (0x58 * (1.0 - i / 40.0));
            int sx = bx1 + (bx2 - bx1) * 2 / 3 * i / 40;
            int ex = bx1 + (bx2 - bx1) * 2 / 3 * (i + 1) / 40;
            gui.fill(sx, by1, ex, by1 + 20, (alpha << 24) | (PURPLE & 0xFFFFFF));
        }
        gui.fill(bx1, by1 + 19, bx2, by1 + 20, PURPLE);
        gui.item(CoreScreen.iconOf("nightsiege:dungeon_compass"), bx1 + 4, by1 + 2);
        gui.text(font, Component.translatable("nightsiege.tool.title").getString(), bx1 + 24, by1 + 6, PURPLE, true);
        String hint = Component.translatable("nightsiege.tool.hint").getString();
        gui.text(font, hint, bx2 - 6 - font.width(hint), by1 + 6, 0xFFB8B4C0, true);

        // Inner panels on stone
        panel(gui, left + 7, top + 27, LIST_W + 2, ROWS * ROW + 2);
        panel(gui, left + LIST_W + 14, top + 27, W - LIST_W - 21, H - 34);
    }

    private static void panel(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME_DARK);
        gui.fill(x, y + h, x + w + 1, y + h + 1, FRAME_LIGHT);
        gui.fill(x + w, y, x + w + 1, y + h + 1, FRAME_LIGHT);
        gui.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) {
            for (int tx = x; tx < x + w; tx += 16) {
                gui.blit(RenderPipelines.GUI_TEXTURED, BACKDROP, tx, ty, 0f, 0f, 16, 16, 16, 16);
            }
        }
        gui.disableScissor();
        gui.fill(x, y, x + w, y + h, 0x88000000);
    }

    private void drawList(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x = left + 8;
        int y = top + 28;
        for (int i = 0; i < ROWS && scroll + i < state.entries.size(); i++) {
            int index = scroll + i;
            DungeonToolState.Entry entry = state.entries.get(index);
            int ry = y + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW;
            if (index == selected) {
                gui.fill(x, ry, x + LIST_W, ry + ROW, 0x66D58CFF);
                gui.fill(x, ry, x + 2, ry + ROW, PURPLE);
            } else if (hover) {
                gui.fill(x, ry, x + LIST_W, ry + ROW, 0x30FFFFFF);
            }
            DungeonTier t = DungeonTier.byName(entry.tier);
            int tierColor = t == null ? GRAY : t.color();
            // Installed dot
            gui.fill(x + 5, ry + 6, x + 10, ry + 11, entry.installed ? 0xFF55FF55 : 0xFF555555);
            String name = Component.translatable("nightsiege.dungeon.type." + entry.key).getString();
            while (name.length() > 3 && font.width(name) > LIST_W - 46) {
                name = name.substring(0, name.length() - 2) + "…";
            }
            gui.text(font, name, x + 14, ry + 5, entry.installed ? WHITE : GRAY, true);
            String tag = Component.translatable("nightsiege.dungeon.tier_short." + entry.tier.toLowerCase(java.util.Locale.ROOT)).getString();
            gui.text(font, tag, x + LIST_W - 6 - font.width(tag), ry + 5, tierColor, true);
            if (entry.beaten > 0) {
                gui.text(font, "✔", x + LIST_W - 18 - font.width(tag), ry + 5, 0xFF55FF55, true);
            }
        }
        // Scroll bar
        int total = state.entries.size();
        if (total > ROWS) {
            int barX = x + LIST_W - 2;
            int barH = ROWS * ROW;
            int knob = Math.max(12, barH * ROWS / total);
            int ky = y + (barH - knob) * scroll / Math.max(1, total - ROWS);
            gui.fill(barX, y, barX + 2, y + barH, 0x40000000);
            gui.fill(barX, ky, barX + 2, ky + knob, 0xC0FFFFFF);
        }
    }

    private void drawDetails(GuiGraphicsExtractor gui, Font font) {
        DungeonToolState.Entry entry = current();
        int x = left + LIST_W + 18;
        int y = top + 31;
        if (entry == null) {
            gui.text(font, Component.translatable("nightsiege.tool.empty").getString(), x, y, GRAY, true);
            return;
        }
        DungeonTier t = DungeonTier.byName(entry.tier);
        gui.text(font, Component.translatable("nightsiege.dungeon.type." + entry.key).getString(), x, y, GOLD, true);
        y += 11;
        gui.text(font, entry.mod + (entry.installed ? "" : "  " + Component.translatable("nightsiege.tool.missing").getString()),
                x, y, entry.installed ? GRAY : 0xFFFF5555, true);
        y += 12;
        gui.text(font, Component.translatable("nightsiege.tool.kind",
                Component.translatable("nightsiege.dungeon.tier." + entry.tier.toLowerCase(java.util.Locale.ROOT)),
                Component.translatable("nightsiege.dungeon.theme." + entry.theme.toLowerCase(java.util.Locale.ROOT))).getString(),
                x, y, t == null ? WHITE : t.color(), true);
        y += 11;
        gui.text(font, Component.translatable("nightsiege.tool.count", entry.found, entry.beaten).getString(), x, y, WHITE, true);

        // Settings of the test fight
        int sy = top + 80;
        gui.text(font, Component.translatable("nightsiege.tool.settings").getString(), x, sy, PURPLE, true);
        gui.fill(x, sy + 9, left + W - 12, sy + 10, 0x40FFFFFF);
        String lv = Component.translatable("nightsiege.tool.level", DungeonMath.level(tier, progress)).getString();
        gui.text(font, lv, left + W - 12 - font.width(lv), sy, GRAY, true);
        int rw = W - LIST_W - 26;
        int small = 16;
        int valueW = 54;
        int labelW = rw - 2 * small - valueW - 4;
        String[] labels = {"waves", "tier", "progress", "boss", "theme"};
        int ly = top + 96;
        for (String label : labels) {
            gui.text(font, Component.translatable("nightsiege.tool." + label).getString(), x, ly, WHITE, true);
            ly += 18;
        }
        centered(gui, font, String.valueOf(waves), x + labelW + small + 2 + valueW / 2, top + 96, WHITE);
        centered(gui, font, String.valueOf(progress), x + labelW + small + 2 + valueW / 2, top + 132, WHITE);
    }

    private static void centered(GuiGraphicsExtractor gui, Font font, String text, int cx, int y, int color) {
        gui.text(font, text, cx - font.width(text) / 2, y, color, true);
    }
}
