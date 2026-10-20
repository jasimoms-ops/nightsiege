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
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The siege core's window. Two pages: the tally on the left tab, the skill
 * tree on the right, laid out like the advancement screen with framed icons
 * and lines running from each node to the one it unlocks.
 */
public class CoreScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 244;

    // Minecraft's own tooltip look: near black fill with a violet double edge.
    private static final int BG = 0xF0100010;
    private static final int EDGE_TOP = 0x505000FF;
    private static final int EDGE_BOTTOM = 0x5028007F;

    // The colours of Minecraft's own text codes, so the window reads as vanilla.
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK_GRAY = 0xFF555555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    // Recessed slot, drawn the way vanilla draws inventory slots.
    private static final int SLOT_BG = 0xFF1B1821;
    private static final int SLOT_SHADOW = 0xFF0D0B11;
    private static final int SLOT_DONE = 0xFF55FF55;
    private static final int SLOT_OPEN = 0xFFFFAA00;
    private static final int SLOT_LOCKED = 0xFF3A3540;

    /** Kept between openings so buying a skill does not throw you back. */
    private static int activeTab;

    private CoreStats stats;
    private final List<NodeButton> nodeButtons = new ArrayList<>();
    private int left;
    private int top;

    private record NodeButton(CoreStats.NodeLine node, int x, int y) {
    }

    public CoreScreen(CoreStats stats) {
        super(Component.translatable("nightsiege.ui.title"));
        this.stats = stats;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_W) / 2;
        top = (this.height - PANEL_H) / 2;
        nodeButtons.clear();

        addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.tab_stats"),
                        b -> switchTab(0))
                .bounds(left + 8, top + 22, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.tab_skills"),
                        b -> switchTab(1))
                .bounds(left + 102, top + 22, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W - 60, top + PANEL_H - 24, 52, 18).build());

        if (activeTab == 1 && stats.skillsEnabled) {
            buildSkillNodes();
        }
    }

    private void switchTab(int tab) {
        activeTab = tab;
        reopen();
    }

    /** Opens the window again so the widgets match the new tab. */
    private void reopen() {
        // Rebuilding the widgets in place, instead of opening a new screen,
        // avoids the one frame of black between two screens.
        rebuildWidgets();
    }

    /** New numbers from the server, e.g. after buying a skill. Keeps the window open. */
    public void update(CoreStats fresh) {
        this.stats = fresh;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    // skill tree layout
    // ------------------------------------------------------------------

    /** The viewing player's own tree on the left, the shared core tree right. */
    private void buildSkillNodes() {
        if (stats.myClass == null || stats.myClass.isBlank()) {
            buildClassChoice();
            return;
        }
        addColumns(new String[]{stats.myClass, SkillClass.CORE.name()});

        addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.donate"),
                        b -> send("donate:5"))
                .bounds(left + PANEL_W - 128, top + PANEL_H - 24, 64, 18).build());
        if (stats.creative) {
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_grant"),
                            b -> send("dev:50"))
                    .bounds(left + 8, top + PANEL_H - 24, 74, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_reset"),
                            b -> send("dev:reset"))
                    .bounds(left + 86, top + PANEL_H - 24, 74, 18).build());
        }
    }

    /** Before a class is picked the page is a choice of four. */
    private void buildClassChoice() {
        SkillClass[] options = {SkillClass.ARCHER, SkillClass.WARRIOR,
                SkillClass.GUARDIAN, SkillClass.ENGINEER};
        for (int i = 0; i < options.length; i++) {
            SkillClass option = options[i];
            addRenderableWidget(Button.builder(Component.translatable(option.translationKey()),
                            b -> send("class:" + option.name()))
                    .bounds(left + 20 + (i % 2) * 150, top + 90 + (i / 2) * 26, 140, 20).build());
        }
    }

    private void addColumns(String[] columns) {
        int columnWidth = (PANEL_W - 24) / columns.length;
        for (int column = 0; column < columns.length; column++) {
            String name = columns[column];
            int centerX = left + 12 + column * columnWidth + columnWidth / 2;
            for (CoreStats.NodeLine node : stats.nodes) {
                if (!name.equals(node.clazz)) {
                    continue;
                }
                // The two paths of a class sit side by side below their shared trunk.
                int offset = "swift".equals(node.path) ? -34 : "heavy".equals(node.path) ? 34 : 0;
                int x = centerX - 13 + offset;
                int y = top + 98 + node.row * 30;
                nodeButtons.add(new NodeButton(node, x, y));
                addRenderableWidget(Button.builder(Component.literal(""), b -> send("buy:" + node.id))
                        .bounds(x, y, 26, 26).build());
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

        darkPanel(gui, left, top, PANEL_W, PANEL_H);
        gui.text(font, Component.translatable("nightsiege.ui.title").getString(),
                left + 8, top + 7, GOLD, false);
        gui.fill(left + 6, top + 18, left + PANEL_W - 6, top + 19, DARK_GRAY);

        // Lines first, so the node buttons drawn by super sit on top of them.
        if (activeTab == 1 && stats.skillsEnabled) {
            drawConnections(gui);
        }

        super.extractRenderState(gui, mouseX, mouseY, delta);

        if (activeTab == 0) {
            drawStats(gui, font);
        } else if (stats.skillsEnabled) {
            drawSkillOverlay(gui, font, mouseX, mouseY);
        } else {
            gui.text(font, Component.translatable("nightsiege.ui.skills_off").getString(),
                    left + 12, top + 60, GRAY, false);
        }
    }

    private void drawStats(GuiGraphicsExtractor gui, Font font) {
        int x = left + 12;
        int y = top + 48;

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
        gui.text(font, Component.translatable("nightsiege.ui.ranking").getString(), x, y, GOLD, false);
        y += 12;
        int shown = 0;
        for (CoreStats.PlayerLine player : stats.players) {
            if (shown >= 6) {
                break;
            }
            String row = Component.translatable("nightsiege.ui.player_row",
                    player.name, player.kills, player.championKills, player.fallen).getString();
            gui.text(font, row, x + 4, y, player.online ? WHITE : GRAY, false);
            y += 11;
            shown++;
        }
        if (shown == 0) {
            gui.text(font, Component.translatable("nightsiege.ui.no_kills").getString(), x + 4, y, GRAY, false);
        }
    }

    private void drawConnections(GuiGraphicsExtractor gui) {
        for (NodeButton button : nodeButtons) {
            if (button.node().requires == null) {
                continue;
            }
            for (NodeButton parent : nodeButtons) {
                if (!parent.node().id.equals(button.node().requires)) {
                    continue;
                }
                int px = parent.x() + 13;
                int cx = button.x() + 13;
                int fromY = parent.y() + 26;
                int toY = button.y();
                int midY = (fromY + toY) / 2;
                int color = parent.node().level >= button.node().requiresLevel ? 0xFF6FD66F : 0xFF46414F;
                // Down, across, down: straight for a chain, an elbow where the paths split.
                gui.fill(px - 1, fromY, px + 1, midY + 1, color);
                gui.fill(Math.min(px, cx) - 1, midY - 1, Math.max(px, cx) + 1, midY + 1, color);
                gui.fill(cx - 1, midY, cx + 1, toY, color);
            }
        }
    }

    private void drawSkillOverlay(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        if (stats.myClass == null || stats.myClass.isBlank()) {
            gui.text(font, Component.translatable("nightsiege.ui.pick_class").getString(),
                    left + 12, top + 50, GOLD, false);
            gui.text(font, Component.translatable("nightsiege.ui.pick_hint").getString(),
                    left + 12, top + 64, GRAY, false);
            return;
        }

        gui.text(font, Component.translatable("nightsiege.ui.my_class",
                        Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT)))
                .getString(), left + 12, top + 48, GOLD, false);
        gui.text(font, Component.translatable("nightsiege.ui.points", stats.points, stats.earned).getString(),
                left + 12, top + 60, WHITE, false);
        gui.text(font, Component.translatable("nightsiege.ui.core_pot", stats.corePoints, stats.donated).getString(),
                left + 12, top + 72, GRAY, false);

        int columnWidth = (PANEL_W - 24) / 2;
        gui.text(font, Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT)).getString(),
                left + 14, top + 86, GRAY, false);
        gui.text(font, Component.translatable("nightsiege.class.core").getString(),
                left + 14 + columnWidth, top + 86, GRAY, false);

        CoreStats.NodeLine hovered = null;
        for (NodeButton button : nodeButtons) {
            CoreStats.NodeLine node = button.node();
            int frame = node.level >= node.maxLevel ? SLOT_DONE
                    : node.blocked == null ? SLOT_OPEN : SLOT_LOCKED;
            slot(gui, button.x() - 1, button.y() - 1, 28, frame);
            gui.item(iconOf(node.icon), button.x() + 5, button.y() + 5);
            // Beside the slot, so it never runs into the slot below.
            gui.text(font, node.level + "/" + node.maxLevel,
                    button.x() + 30, button.y() + 9, node.level > 0 ? GREEN : GRAY, false);
            if (mouseX >= button.x() && mouseX < button.x() + 26
                    && mouseY >= button.y() && mouseY < button.y() + 26) {
                hovered = node;
            }
        }
        if (hovered != null) {
            drawTooltip(gui, font, hovered, mouseX, mouseY);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor gui, Font font, CoreStats.NodeLine node, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        lines.add(Component.translatable("nightsiege.skill." + node.id).getString());
        String descKey = "nightsiege.skill." + node.id + ".desc";
        if (Language.getInstance().has(descKey)) {
            for (String part : Language.getInstance().getOrDefault(descKey).split("\n")) {
                lines.add(part);
            }
        }
        lines.add(Component.translatable("nightsiege.ui.node_level", node.level, node.maxLevel).getString());
        if (node.level < node.maxLevel) {
            lines.add(Component.translatable("nightsiege.ui.node_cost", node.cost).getString());
        }
        if (node.blocked != null) {
            lines.add(Component.translatable("nightsiege.menu.blocked_" + node.blocked, node.cost).getString());
        }
        if (node.path != null && !node.path.isBlank()) {
            lines.add(Component.translatable("nightsiege.ui.path_" + node.path).getString());
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxW = width + 10;
        int boxH = lines.size() * 11 + 6;
        int boxX = Math.min(mouseX + 10, left + PANEL_W - boxW - 2);
        int boxY = Math.min(mouseY + 8, top + PANEL_H - boxH - 2);

        darkPanel(gui, boxX, boxY, boxW, boxH);
        int y = boxY + 4;
        for (int i = 0; i < lines.size(); i++) {
            gui.text(font, lines.get(i), boxX + 5, y, i == 0 ? WHITE : GRAY, false);
            y += 11;
        }
    }

    /** The dark panel Minecraft itself uses for tooltips. */
    private static void darkPanel(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        gui.fill(x + 1, y, x2 - 1, y + 1, BG);
        gui.fill(x + 1, y2 - 1, x2 - 1, y2, BG);
        gui.fill(x, y + 1, x2, y2 - 1, BG);
        gui.fillGradient(x + 1, y + 2, x + 2, y2 - 2, EDGE_TOP, EDGE_BOTTOM);
        gui.fillGradient(x2 - 2, y + 2, x2 - 1, y2 - 2, EDGE_TOP, EDGE_BOTTOM);
        gui.fill(x + 1, y + 1, x2 - 1, y + 2, EDGE_TOP);
        gui.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, EDGE_BOTTOM);
    }

    /** A recessed square, the way an inventory slot is drawn. */
    private static void slot(GuiGraphicsExtractor gui, int x, int y, int size, int frame) {
        gui.fill(x, y, x + size, y + size, SLOT_BG);
        gui.fill(x, y, x + size, y + 1, SLOT_SHADOW);
        gui.fill(x, y, x + 1, y + size, SLOT_SHADOW);
        gui.fill(x + size - 1, y, x + size, y + size, frame);
        gui.fill(x, y + size - 1, x + size, y + size, frame);
    }

    private int line(GuiGraphicsExtractor gui, Font font, int x, int y, String text, int color) {
        gui.text(font, text, x, y, color, false);
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
