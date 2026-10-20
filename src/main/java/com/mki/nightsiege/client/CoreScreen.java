package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    private static final int PANEL_H = 214;

    private static final int BG = 0xF0100D14;
    private static final int BORDER = 0xFF4A4458;
    private static final int HEADER = 0xFFFFD27F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9A93A6;
    private static final int GOOD = 0xFF6FD66F;
    private static final int BAD = 0xFFE06666;

    /** Node frame colours, mirroring how advancements read at a glance. */
    private static final int NODE_DONE = 0xFF3C6E3C;
    private static final int NODE_OPEN = 0xFF7A6B2E;
    private static final int NODE_LOCKED = 0xFF2A2730;

    /** Kept between openings so buying a skill does not throw you back. */
    private static int activeTab;

    private final CoreStats stats;
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
        rebuildWidgets();
    }

    /** Re-runs init so the widgets match the new tab. */
    private void rebuildWidgets() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.setScreenAndShow(new CoreScreen(stats));
        }
    }

    // ------------------------------------------------------------------
    // skill tree layout
    // ------------------------------------------------------------------

    private static final String[] BRANCH_ORDER = {"MELEE", "RANGED", "TOUGHNESS", "BASE"};

    private void buildSkillNodes() {
        int columnWidth = (PANEL_W - 24) / BRANCH_ORDER.length;
        for (int column = 0; column < BRANCH_ORDER.length; column++) {
            String branch = BRANCH_ORDER[column];
            int centerX = left + 12 + column * columnWidth + columnWidth / 2;
            for (CoreStats.NodeLine node : stats.nodes) {
                if (!branch.equals(node.branch)) {
                    continue;
                }
                int x = centerX - 13;
                int y = top + 72 + node.row * 36;
                nodeButtons.add(new NodeButton(node, x, y));
                // An invisible-sized button carries the click; the frame and the
                // icon are drawn on top afterwards.
                addRenderableWidget(Button.builder(Component.literal(""), b -> buy(node.id))
                        .bounds(x, y, 26, 26).build());
            }
        }
    }

    private void buy(String nodeId) {
        ClientPlayNetworking.send(new CoreNetworking.BuySkillPayload(nodeId));
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;

        gui.fill(left, top, left + PANEL_W, top + PANEL_H, BG);
        gui.outline(left, top, PANEL_W, PANEL_H, BORDER);
        gui.fill(left, top, left + PANEL_W, top + 18, 0xFF1C1726);
        gui.text(font, Component.translatable("nightsiege.ui.title").getString(),
                left + 8, top + 5, HEADER, false);

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
                    left + 12, top + 60, DIM, false);
        }
    }

    private void drawStats(GuiGraphicsExtractor gui, Font font) {
        int x = left + 12;
        int y = top + 48;

        if (stats.siegeActive) {
            y = line(gui, font, x, y, Component.translatable("nightsiege.ui.active",
                    stats.level, stats.secondsLeft).getString(), BAD);
        } else {
            y = line(gui, font, x, y, Component.translatable("nightsiege.ui.countdown",
                    stats.countdownPercent, stats.countdownMinutesLeft).getString(),
                    stats.clockRunning ? GOOD : DIM);
            y = line(gui, font, x, y, Component.translatable(
                    stats.clockRunning ? "nightsiege.ui.clock_running" : "nightsiege.ui.clock_paused",
                    stats.playersNear, stats.playersRequired, stats.coreRadius).getString(),
                    stats.clockRunning ? GOOD : BAD);
        }
        y += 4;
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.wave",
                stats.level, stats.mobsPerWave, stats.maxLiveMobs).getString(), TEXT);
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.strength",
                fmt(stats.healthMultiplier), fmt(stats.damageMultiplier)).getString(), DIM);
        y += 4;
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.record",
                stats.siegesWon, stats.siegesLost, stats.currentStreak, stats.bestStreak).getString(), TEXT);
        y = line(gui, font, x, y, Component.translatable("nightsiege.ui.totals",
                stats.totalKills, stats.totalChampionKills, stats.highestLevelReached).getString(), DIM);

        y += 6;
        gui.text(font, Component.translatable("nightsiege.ui.ranking").getString(), x, y, HEADER, false);
        y += 12;
        int shown = 0;
        for (CoreStats.PlayerLine player : stats.players) {
            if (shown >= 6) {
                break;
            }
            String row = Component.translatable("nightsiege.ui.player_row",
                    player.name, player.kills, player.championKills, player.fallen).getString();
            gui.text(font, row, x + 4, y, player.online ? TEXT : DIM, false);
            y += 11;
            shown++;
        }
        if (shown == 0) {
            gui.text(font, Component.translatable("nightsiege.ui.no_kills").getString(), x + 4, y, DIM, false);
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
                int x = button.x() + 13;
                int fromY = parent.y() + 26;
                int toY = button.y();
                boolean unlocked = parent.node().level >= button.node().requiresLevel;
                gui.fill(x - 1, fromY, x + 1, toY, unlocked ? 0xFF6FD66F : 0xFF46414F);
            }
        }
    }

    private void drawSkillOverlay(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        gui.text(font, Component.translatable("nightsiege.ui.points", stats.points, stats.earned).getString(),
                left + 12, top + 48, HEADER, false);

        int columnWidth = (PANEL_W - 24) / BRANCH_ORDER.length;
        for (int column = 0; column < BRANCH_ORDER.length; column++) {
            String key = "nightsiege.branch." + BRANCH_ORDER[column].toLowerCase(Locale.ROOT);
            gui.text(font, Component.translatable(key).getString(),
                    left + 14 + column * columnWidth, top + 62, DIM, false);
        }

        CoreStats.NodeLine hovered = null;
        for (NodeButton button : nodeButtons) {
            CoreStats.NodeLine node = button.node();
            int frame = node.level >= node.maxLevel ? NODE_DONE
                    : node.blocked == null ? NODE_OPEN : NODE_LOCKED;
            gui.outline(button.x() - 1, button.y() - 1, 28, 28, frame);
            gui.item(iconOf(node.icon), button.x() + 5, button.y() + 5);
            gui.text(font, node.level + "/" + node.maxLevel,
                    button.x() + 1, button.y() + 27, node.level > 0 ? GOOD : DIM, false);
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
        lines.add(Component.translatable("nightsiege.ui.node_level", node.level, node.maxLevel).getString());
        if (node.level < node.maxLevel) {
            lines.add(Component.translatable("nightsiege.ui.node_cost", node.cost).getString());
        }
        if (node.blocked != null) {
            lines.add(Component.translatable("nightsiege.menu.blocked_" + node.blocked, node.cost).getString());
        }
        if (!node.live) {
            lines.add(Component.translatable("nightsiege.menu.preview").getString());
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length() * 6);
        }
        int boxW = width + 10;
        int boxH = lines.size() * 11 + 6;
        int boxX = Math.min(mouseX + 10, left + PANEL_W - boxW - 2);
        int boxY = Math.min(mouseY + 8, top + PANEL_H - boxH - 2);

        gui.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0000000);
        gui.outline(boxX, boxY, boxW, boxH, BORDER);
        int y = boxY + 4;
        for (int i = 0; i < lines.size(); i++) {
            gui.text(font, lines.get(i), boxX + 5, y, i == 0 ? HEADER : DIM, false);
            y += 11;
        }
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
