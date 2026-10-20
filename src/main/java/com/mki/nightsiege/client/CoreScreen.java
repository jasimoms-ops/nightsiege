package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The siege core's window, built like the advancement screen: tabs along the
 * top, a framed grey window, and on each tab a textured backdrop with the
 * skill tree running from left to right.
 *
 * <p>One tab for the tally, one for each class and one for the shared core
 * tree. Every class can be looked at; only the player's own one can be bought
 * from. Choosing a class asks for confirmation, because it is for good.</p>
 */
public class CoreScreen extends Screen {

    private static final int W = 404;
    private static final int H = 232;
    private static final int TAB_W = 28;
    private static final int TAB_H = 26;
    private static final int SLOT = 26;
    private static final int ROW_H = 40;

    // Vanilla window greys.
    private static final int FRAME_FILL = 0xFFC6C6C6;
    private static final int FRAME_LIGHT = 0xFFFFFFFF;
    private static final int FRAME_SHADOW = 0xFF555555;
    private static final int FRAME_DARK = 0xFF373737;
    private static final int TITLE = 0xFF404040;
    private static final int TAB_IDLE = 0xFF8B8B8B;

    // Text colours of Minecraft's own codes.
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;

    // Tooltip look.
    private static final int BG = 0xF0100010;
    private static final int EDGE_TOP = 0x505000FF;
    private static final int EDGE_BOTTOM = 0x5028007F;

    private enum Tab {
        STATS(null, "minecraft:clock", "minecraft:textures/block/deepslate_tiles.png"),
        ARCHER(SkillClass.ARCHER, "minecraft:bow", "minecraft:textures/block/spruce_planks.png"),
        WARRIOR(SkillClass.WARRIOR, "minecraft:iron_sword", "minecraft:textures/block/stone_bricks.png"),
        GUARDIAN(SkillClass.GUARDIAN, "minecraft:shield", "minecraft:textures/block/polished_andesite.png"),
        ENGINEER(SkillClass.ENGINEER, "nightsiege:arrow_turret", "minecraft:textures/block/cut_copper.png"),
        CORE(SkillClass.CORE, "nightsiege:siege_core", "minecraft:textures/block/obsidian.png");

        final SkillClass clazz;
        final String icon;
        final Identifier backdrop;

        Tab(SkillClass clazz, String icon, String backdrop) {
            this.clazz = clazz;
            this.icon = icon;
            this.backdrop = Identifier.parse(backdrop);
        }
    }

    /** Kept between openings, so buying a skill does not throw you back. */
    private static Tab activeTab;
    /** The window currently on screen, if any. */
    private static CoreScreen showing;

    private CoreStats stats;
    /** Class waiting for the player to confirm the choice; null when no dialog is open. */
    private SkillClass confirming;
    private final List<NodeButton> nodeButtons = new ArrayList<>();
    private int left;
    private int top;

    private record NodeButton(CoreStats.NodeLine node, int x, int y) {
    }

    public CoreScreen(CoreStats stats) {
        super(Component.translatable("nightsiege.ui.title"));
        this.stats = stats;
    }

    public static CoreScreen openInstance() {
        return showing;
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

    /** New numbers from the server, e.g. after buying a skill. Keeps the window open. */
    public void update(CoreStats fresh) {
        this.stats = fresh;
        if (confirming != null && hasClass()) {
            confirming = null;
        }
        rebuildWidgets();
    }

    private boolean hasClass() {
        return stats.myClass != null && !stats.myClass.isBlank();
    }

    private Tab ownTab() {
        if (!hasClass()) {
            return null;
        }
        for (Tab tab : Tab.values()) {
            if (tab.clazz != null && tab.clazz.name().equals(stats.myClass)) {
                return tab;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // widgets
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        showing = this;
        left = (this.width - W) / 2;
        top = (this.height - H - TAB_H + 4) / 2 + TAB_H - 4;
        nodeButtons.clear();
        if (activeTab == null) {
            Tab own = ownTab();
            activeTab = own != null ? own : Tab.STATS;
        }

        if (confirming != null) {
            int dx = left + W / 2;
            int dy = top + H / 2;
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.confirm"), b -> {
                send("class:" + confirming.name());
                confirming = null;
                rebuildWidgets();
            }).bounds(dx - 104, dy + 18, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                confirming = null;
                rebuildWidgets();
            }).bounds(dx + 4, dy + 18, 100, 20).build());
            return;
        }

        // Tabs: invisible buttons below the drawn tabs.
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            invisible(Button.builder(Component.empty(), b -> {
                activeTab = tab;
                rebuildWidgets();
            }).bounds(tabX(i), top - TAB_H + 2, TAB_W, TAB_H - 2).build());
        }

        int footerY = top + H - 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 62, footerY, 54, 18).build());

        if (activeTab.clazz == null || !stats.skillsEnabled) {
            return;
        }
        boolean own = activeTab == Tab.CORE || activeTab == ownTab();
        layoutNodes(activeTab.clazz.name(), own);

        if (activeTab == Tab.CORE) {
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.donate"),
                    b -> send("donate:5")).bounds(left + W - 130, footerY, 64, 18).build());
        } else if (!hasClass()) {
            SkillClass pick = activeTab.clazz;
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.choose_class",
                    Component.translatable(pick.translationKey())), b -> {
                confirming = pick;
                rebuildWidgets();
            }).bounds(left + W - 200, footerY, 134, 18).build());
        }
        if (stats.creative && activeTab != Tab.CORE) {
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_grant"),
                    b -> send("dev:50")).bounds(left + 8, footerY, 70, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_reset"),
                    b -> send("dev:reset")).bounds(left + 82, footerY, 56, 18).build());
        }
    }

    private void invisible(Button button) {
        button.setAlpha(0f);
        addRenderableWidget(button);
    }

    private int tabX(int index) {
        return left + 4 + index * (TAB_W + 3);
    }

    private int innerX() {
        return left + 9;
    }

    private int innerY() {
        return top + 18;
    }

    private int innerW() {
        return W - 18;
    }

    private int innerH() {
        return H - 18 - 30;
    }

    /** The tree from left to right: trunk on the left, one row per path. */
    private void layoutNodes(String clazz, boolean buyable) {
        List<String> paths = new ArrayList<>();
        int maxColumn = 0;
        for (CoreStats.NodeLine node : stats.nodes) {
            if (!clazz.equals(node.clazz)) {
                continue;
            }
            maxColumn = Math.max(maxColumn, node.row);
            if (node.path != null && !node.path.isBlank() && !paths.contains(node.path)) {
                paths.add(node.path);
            }
        }
        int rows = Math.max(1, paths.size());
        int spacing = maxColumn == 0 ? 0 : Math.min(92, (innerW() - 60 - SLOT) / maxColumn);
        int treeW = maxColumn * spacing + SLOT;
        int x0 = innerX() + (innerW() - treeW) / 2;
        int areaTop = innerY() + 20;
        int areaH = innerH() - 24;
        int rowsTop = areaTop + (areaH - rows * ROW_H) / 2;

        for (CoreStats.NodeLine node : stats.nodes) {
            if (!clazz.equals(node.clazz)) {
                continue;
            }
            int index = paths.indexOf(node.path);
            double row = index < 0 ? (rows - 1) / 2.0 : index;
            int x = x0 + node.row * spacing;
            int y = rowsTop + (int) Math.round(row * ROW_H) + (ROW_H - SLOT) / 2 - 4;
            nodeButtons.add(new NodeButton(node, x, y));
            if (buyable) {
                invisible(Button.builder(Component.empty(), b -> send("buy:" + node.id))
                        .bounds(x, y, SLOT, SLOT).build());
            }
        }
    }

    private void send(String action) {
        ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;

        // Everything decorative first, so the real buttons drawn by super sit on top.
        drawTabs(gui, false);
        drawWindow(gui, font);
        drawBackdrop(gui);
        CoreStats.NodeLine hovered = null;
        if (activeTab == Tab.STATS) {
            drawStats(gui, font);
        } else if (!stats.skillsEnabled) {
            gui.text(font, Component.translatable("nightsiege.ui.skills_off"), innerX() + 8, innerY() + 8, GRAY, true);
        } else {
            drawTreeHeader(gui, font);
            drawConnections(gui);
            hovered = drawNodes(gui, font, mouseX, mouseY);
        }
        drawTabs(gui, true);
        drawFooter(gui, font);
        if (confirming != null) {
            gui.fill(0, 0, this.width, this.height, 0x90000000);
            drawConfirm(gui, font);
        }

        super.extractRenderState(gui, mouseX, mouseY, delta);

        if (confirming == null) {
            Tab hoveredTab = tabAt(mouseX, mouseY);
            if (hoveredTab != null) {
                tooltip(gui, font, List.of(tabName(hoveredTab)), mouseX, mouseY);
            } else if (hovered != null) {
                drawNodeTooltip(gui, font, hovered, mouseX, mouseY);
            }
        }
    }

    private Tab tabAt(int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int x = tabX(i);
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= top - TAB_H + 2 && mouseY < top) {
                return tabs[i];
            }
        }
        return null;
    }

    private String tabName(Tab tab) {
        if (tab == Tab.STATS) {
            return Component.translatable("nightsiege.ui.tab_stats").getString();
        }
        return Component.translatable(tab.clazz.translationKey()).getString();
    }

    /** Idle tabs sit behind the window; the active one is drawn in front and joins it. */
    private void drawTabs(GuiGraphicsExtractor gui, boolean activeOnly) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            boolean active = tab == activeTab;
            if (active != activeOnly) {
                continue;
            }
            int x = tabX(i);
            int y = top - TAB_H + (active ? 0 : 3);
            int h = active ? TAB_H + 2 : TAB_H - 3;
            int fill = active ? FRAME_FILL : TAB_IDLE;
            gui.fill(x, y, x + TAB_W, y + h, 0xFF000000);
            gui.fill(x + 1, y + 1, x + TAB_W - 1, y + h, fill);
            gui.fill(x + 1, y + 1, x + TAB_W - 1, y + 2, FRAME_LIGHT);
            gui.fill(x + 1, y + 1, x + 2, y + h, FRAME_LIGHT);
            gui.fill(x + TAB_W - 2, y + 2, x + TAB_W - 1, y + h, active ? FRAME_FILL : FRAME_SHADOW);
            gui.item(iconOf(tab.icon), x + 6, y + (active ? 6 : 5));
        }
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
        String title = Component.translatable("nightsiege.ui.title").getString() + " - " + tabName(activeTab);
        gui.text(font, title, left + 9, top + 6, TITLE, false);
    }

    /** The tab's own block texture, tiled like the advancement backgrounds. */
    private void drawBackdrop(GuiGraphicsExtractor gui) {
        int x = innerX();
        int y = innerY();
        int w = innerW();
        int h = innerH();
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME_DARK);
        gui.fill(x, y + h, x + w + 1, y + h + 1, FRAME_LIGHT);
        gui.fill(x + w, y, x + w + 1, y + h + 1, FRAME_LIGHT);
        gui.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) {
            for (int tx = x; tx < x + w; tx += 16) {
                gui.blit(RenderPipelines.GUI_TEXTURED, activeTab.backdrop, tx, ty, 0f, 0f, 16, 16, 16, 16);
            }
        }
        gui.disableScissor();
        // A light veil so lines and text stay readable on every texture.
        gui.fill(x, y, x + w, y + h, 0x55000000);
    }

    private void drawTreeHeader(GuiGraphicsExtractor gui, Font font) {
        int x = innerX() + 6;
        int y = innerY() + 6;
        String line;
        if (activeTab == Tab.CORE) {
            line = Component.translatable("nightsiege.ui.core_pot", stats.corePoints, stats.donated).getString();
        } else if (activeTab == ownTab()) {
            line = Component.translatable("nightsiege.ui.my_class", Component.translatable(activeTab.clazz.translationKey())).getString()
                    + "   " + Component.translatable("nightsiege.ui.points", stats.points, stats.earned).getString();
        } else if (!hasClass()) {
            line = Component.translatable("nightsiege.ui.preview_free").getString();
        } else {
            line = Component.translatable("nightsiege.ui.preview_other",
                    Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT))).getString();
        }
        gui.text(font, line, x, y, activeTab == Tab.CORE || activeTab == ownTab() ? GOLD : GRAY, true);
    }

    /** Lines like the advancement screen: a black outline with a white core. */
    private void drawConnections(GuiGraphicsExtractor gui) {
        for (int pass = 0; pass < 2; pass++) {
            for (NodeButton child : nodeButtons) {
                if (child.node().requires == null) {
                    continue;
                }
                for (NodeButton parent : nodeButtons) {
                    if (!parent.node().id.equals(child.node().requires)) {
                        continue;
                    }
                    boolean open = parent.node().level >= child.node().requiresLevel;
                    int color = pass == 0 ? 0xFF000000 : open ? WHITE : 0xFF6B6B6B;
                    int thick = pass == 0 ? 2 : 1;
                    int x1 = parent.x() + SLOT;
                    int y1 = parent.y() + SLOT / 2;
                    int x2 = child.x();
                    int y2 = child.y() + SLOT / 2;
                    int mid = (x1 + x2) / 2;
                    gui.fill(x1, y1 - thick, mid + thick, y1 + thick, color);
                    gui.fill(mid - thick, Math.min(y1, y2) - thick, mid + thick, Math.max(y1, y2) + thick, color);
                    gui.fill(mid - thick, y2 - thick, x2, y2 + thick, color);
                }
            }
        }
    }

    private CoreStats.NodeLine drawNodes(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        CoreStats.NodeLine hovered = null;
        for (NodeButton button : nodeButtons) {
            CoreStats.NodeLine node = button.node();
            int x = button.x();
            int y = button.y();
            boolean maxed = node.level >= node.maxLevel;
            int fill;
            if (maxed) {
                fill = 0xFFE3A62B;          // gold, like a completed advancement
            } else if (node.level > 0) {
                fill = 0xFF4F8A3A;          // green: started
            } else if (node.blocked == null) {
                fill = 0xFF8B8B8B;          // grey: can be bought now
            } else {
                fill = 0xFF4A4A4A;          // dark: not yet
            }
            gui.fill(x + 1, y, x + SLOT - 1, y + SLOT, 0xFF000000);
            gui.fill(x, y + 1, x + SLOT, y + SLOT - 1, 0xFF000000);
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, fill);
            gui.fill(x + 1, y + 1, x + SLOT - 1, y + 2, 0x60FFFFFF);
            gui.fill(x + 1, y + 1, x + 2, y + SLOT - 1, 0x60FFFFFF);
            gui.fill(x + 1, y + SLOT - 2, x + SLOT - 1, y + SLOT - 1, 0x60000000);
            gui.fill(x + SLOT - 2, y + 1, x + SLOT - 1, y + SLOT - 1, 0x60000000);
            gui.item(iconOf(node.icon), x + 5, y + 5);

            String level = node.level + "/" + node.maxLevel;
            int color = maxed ? GOLD : node.level > 0 ? GREEN : node.blocked == null ? YELLOW : GRAY;
            gui.text(font, level, x + SLOT / 2 - font.width(level) / 2, y + SLOT + 2, color, true);

            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                hovered = node;
            }
        }
        return hovered;
    }

    private void drawFooter(GuiGraphicsExtractor gui, Font font) {
        if (activeTab == Tab.STATS || stats.creative) {
            return;
        }
        String text;
        if (activeTab == Tab.CORE) {
            text = Component.translatable("nightsiege.ui.footer_core").getString();
        } else if (activeTab == ownTab()) {
            text = Component.translatable("nightsiege.ui.footer_own").getString();
        } else if (hasClass()) {
            text = Component.translatable("nightsiege.ui.footer_view").getString();
        } else {
            return;
        }
        gui.text(font, text, left + 10, top + H - 19, TITLE, false);
    }

    private void drawConfirm(GuiGraphicsExtractor gui, Font font) {
        int w = 240;
        int h = 84;
        int x = left + (W - w) / 2;
        int y = top + (H - h) / 2 - 8;
        gui.fill(x, y, x + w, y + h, 0xFF000000);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_FILL);
        gui.fill(x + 1, y + 1, x + w - 1, y + 3, FRAME_LIGHT);
        gui.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, FRAME_SHADOW);
        Component name = Component.translatable(confirming.translationKey());
        centered(gui, font, Component.translatable("nightsiege.ui.confirm_title", name).getString(), x + w / 2, y + 10, TITLE);
        centered(gui, font, Component.translatable("nightsiege.ui.confirm_warn1").getString(), x + w / 2, y + 26, 0xFFAA0000);
        centered(gui, font, Component.translatable("nightsiege.ui.confirm_warn2").getString(), x + w / 2, y + 38, TITLE);
    }

    private static void centered(GuiGraphicsExtractor gui, Font font, String text, int cx, int y, int color) {
        gui.text(font, text, cx - font.width(text) / 2, y, color, false);
    }

    private void drawStats(GuiGraphicsExtractor gui, Font font) {
        int x = innerX() + 8;
        int y = innerY() + 8;

        if (stats.siegeActive) {
            y = line(gui, font, x, y, Component.translatable("nightsiege.ui.active",
                    stats.level, stats.secondsLeft).getString(), RED);
        } else {
            y = line(gui, font, x, y, Component.translatable("nightsiege.ui.countdown",
                    stats.countdownPercent, stats.countdownMinutesLeft).getString(),
                    stats.clockRunning ? GREEN : GRAY);
            y = line(gui, font, x, y, Component.translatable(
                    stats.clockRunning ? "nightsiege.ui.clock_running" : "nightsiege.ui.clock_paused",
                    stats.playersNear, stats.playersRequired, stats.coreRadius).getString(),
                    stats.clockRunning ? GREEN : RED);
        }
        y += 4;
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.wave",
                stats.level, stats.mobsPerWave, stats.maxLiveMobs).getString(), WHITE);
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.strength",
                fmt(stats.healthMultiplier), fmt(stats.damageMultiplier)).getString(), GRAY);
        y += 4;
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.record",
                stats.siegesWon, stats.siegesLost, stats.currentStreak, stats.bestStreak).getString(), WHITE);
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.totals",
                stats.totalKills, stats.totalChampionKills, stats.highestLevelReached).getString(), GRAY);

        y += 6;
        gui.text(font, Component.translatable("nightsiege.ui.ranking").getString(), x, y, GOLD, true);
        y += 12;
        int shown = 0;
        for (CoreStats.PlayerLine player : stats.players) {
            if (shown >= 6) {
                break;
            }
            String row = Component.translatable("nightsiege.ui.player_row",
                    player.name, player.kills, player.championKills, player.fallen).getString();
            gui.text(font, row, x + 4, y, player.online ? WHITE : GRAY, true);
            y += 11;
            shown++;
        }
        if (shown == 0) {
            gui.text(font, Component.translatable("nightsiege.ui.no_kills").getString(), x + 4, y, GRAY, true);
        }
    }

    private void drawNodeTooltip(GuiGraphicsExtractor gui, Font font, CoreStats.NodeLine node, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        lines.add(Component.translatable("nightsiege.skill." + node.id).getString());
        String descKey = "nightsiege.skill." + node.id + ".desc";
        if (Language.getInstance().has(descKey)) {
            for (String part : Language.getInstance().getOrDefault(descKey).split("\n")) {
                lines.add("§7" + part);
            }
        }
        lines.add(Component.translatable("nightsiege.ui.node_level", node.level, node.maxLevel).getString());
        if (node.level < node.maxLevel) {
            lines.add(Component.translatable("nightsiege.ui.node_cost", node.cost).getString());
        }
        boolean viewOnly = activeTab != Tab.CORE && activeTab != ownTab();
        if (viewOnly) {
            lines.add(Component.translatable("nightsiege.ui.view_only").getString());
        } else if (node.blocked != null) {
            lines.add(Component.translatable("nightsiege.menu.blocked_" + node.blocked, node.cost).getString());
        }
        if (node.path != null && !node.path.isBlank()) {
            lines.add(Component.translatable("nightsiege.ui.path_" + node.path).getString());
        }
        tooltip(gui, font, lines, mouseX, mouseY);
    }

    private void tooltip(GuiGraphicsExtractor gui, Font font, List<String> lines, int mouseX, int mouseY) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxW = width + 10;
        int boxH = lines.size() * 11 + 6;
        int boxX = Math.min(mouseX + 10, this.width - boxW - 2);
        int boxY = Math.min(mouseY + 8, this.height - boxH - 2);
        int x2 = boxX + boxW;
        int y2 = boxY + boxH;
        gui.fill(boxX + 1, boxY, x2 - 1, y2, BG);
        gui.fill(boxX, boxY + 1, x2, y2 - 1, BG);
        gui.fill(boxX + 1, boxY + 1, x2 - 1, boxY + 2, EDGE_TOP);
        gui.fill(boxX + 1, y2 - 2, x2 - 1, y2 - 1, EDGE_BOTTOM);
        gui.fill(boxX + 1, boxY + 2, boxX + 2, y2 - 2, EDGE_TOP);
        gui.fill(x2 - 2, boxY + 2, x2 - 1, y2 - 2, EDGE_TOP);
        int y = boxY + 4;
        for (int i = 0; i < lines.size(); i++) {
            gui.text(font, lines.get(i), boxX + 5, y, i == 0 ? WHITE : GRAY, false);
            y += 11;
        }
    }

    private int line(GuiGraphicsExtractor gui, Font font, int x, int y, String text, int color) {
        gui.text(font, text, x, y, color, true);
        return y + 11;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static ItemStack iconOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
