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

    /** Window size: the grid trees (warrior, archer) get a bigger one. */
    private int W = 404;
    private int H = 244;
    /** Height of the dark banner with the big title. */
    private static final int BANNER = 26;
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
        STATS(null, "minecraft:clock", "minecraft:textures/block/deepslate_tiles.png", 0xFFFFC94A, "stats"),
        ARCHER(SkillClass.ARCHER, "minecraft:bow", "minecraft:textures/block/spruce_planks.png", 0xFF8BD45C, "archer"),
        WARRIOR(SkillClass.WARRIOR, "minecraft:iron_sword", "minecraft:textures/block/stone_bricks.png", 0xFFFF6B4A, "warrior"),
        GUARDIAN(SkillClass.GUARDIAN, "minecraft:shield", "minecraft:textures/block/polished_andesite.png", 0xFF6FA8FF, "guardian"),
        ENGINEER(SkillClass.ENGINEER, "nightsiege:arrow_turret", "minecraft:textures/block/cut_copper.png", 0xFFF0A040, "engineer"),
        CORE(SkillClass.CORE, "nightsiege:siege_core", "minecraft:textures/block/obsidian.png", 0xFFC88CFF, "core"),
        PERKS(null, "nightsiege:core_fragment_vigilance", "minecraft:textures/block/amethyst_block.png", 0xFFE0A0FF, "perks");

        final SkillClass clazz;
        final String icon;
        final Identifier backdrop;
        final int color;
        final String key;

        Tab(SkillClass clazz, String icon, String backdrop, int color, String key) {
            this.clazz = clazz;
            this.icon = icon;
            this.backdrop = Identifier.parse(backdrop);
            this.color = color;
            this.key = key;
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
    /** Top edge of each path's row, for the highlight behind the player's own path. */
    private final java.util.Map<String, Integer> pathRows = new java.util.HashMap<>();
    private int treeLeft;
    private int treeRight;
    /** Grid trees (the warrior): one way is shown at a time, picked with two buttons. */
    private static String viewWay;
    private boolean gridMode;
    private String shownWay = "";
    private final List<String> ways = new ArrayList<>();
    private int gridTop;
    private int gridSpacing;
    private int gridMaxRow;
    private static final int HALF = 18;
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
        if (activeTab == null) {
            Tab own = ownTab();
            activeTab = own != null ? own : Tab.STATS;
        }
        boolean big = activeTab.clazz != null && stats.skillsEnabled && isGrid(activeTab.clazz.name());
        W = big ? Math.max(404, Math.min(this.width - 16, 700)) : 404;
        H = big ? Math.max(244, Math.min(this.height - 34, 360)) : 244;
        left = (this.width - W) / 2;
        top = (this.height - H - TAB_H + 4) / 2 + TAB_H - 4;
        nodeButtons.clear();
        pathRows.clear();
        gridMode = false;
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

        // The handbook, as one more tab at the far right of the row.
        Button guide = Button.builder(Component.empty(),
                b -> Minecraft.getInstance().setScreenAndShow(new GuideScreen()))
                .bounds(guideTabX(), top - TAB_H + 2, TAB_W, TAB_H - 2).build();
        guide.setAlpha(0f);
        addRenderableWidget(guide);

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

    private int guideTabX() {
        return left + W - 4 - TAB_W;
    }

    private int tabX(int index) {
        return left + 4 + index * (TAB_W + 3);
    }

    private int innerX() {
        return left + 9;
    }

    private int innerY() {
        return top + 6 + BANNER;
    }

    private int innerW() {
        return W - 18;
    }

    private int innerH() {
        return H - 6 - BANNER - 30;
    }

    /** True when the nodes of this class carry fixed places (the warrior's grid). */
    private boolean isGrid(String clazz) {
        for (CoreStats.NodeLine node : stats.nodes) {
            if (clazz.equals(node.clazz) && node.col >= 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // grid trees (warrior, archer): big, draggable, with an info panel
    // ------------------------------------------------------------------

    private static final int G_SLOT = 28;
    private static final int G_TIER = 62;
    private static final int G_HALF = 20;
    /** Width of the info panel: narrower on small screens. */
    private int PANEL_W = 156;
    /** Set once per window, so the tree opens at the player's progress. */
    private boolean panPlaced;
    private static String panFor = "";
    private static final int LABEL_BAND = 24;
    private static double panX;
    private static double panY;
    /** The node shown in the info panel. */
    private static String selectedId;
    private boolean dragging;
    private final List<GridNode> gridNodes = new ArrayList<>();
    private int canvasW;
    private int canvasH;

    private record GridNode(CoreStats.NodeLine node, int cx, int cy) {
    }

    private record Edge(List<GridNode> parents, List<Integer> levels, GridNode child) {
    }

    /** The two subclasses of each way: top lane, bottom lane. */
    private static final java.util.Map<String, String[]> LANES = java.util.Map.of(
            "edge", new String[]{"blademaster", "berserker"},
            "might", new String[]{"axe", "paladin"},
            "bow", new String[]{"swift", "heavy"},
            "xbow", new String[]{"bolter", "trapper"},
            "shield", new String[]{"shieldfighter", "bulwark"},
            "bless", new String[]{"medic", "warden"});
    private static final java.util.Map<String, Integer> LANE_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("blademaster", 0x6FA0FF), java.util.Map.entry("berserker", 0xE04848),
            java.util.Map.entry("axe", 0xD8913A), java.util.Map.entry("paladin", 0xF2D46A),
            java.util.Map.entry("swift", 0x5FD0E8), java.util.Map.entry("heavy", 0xC88A3A),
            java.util.Map.entry("bolter", 0xE87A30), java.util.Map.entry("trapper", 0x6CC46C),
            java.util.Map.entry("shieldfighter", 0x8FB4FF), java.util.Map.entry("bulwark", 0xB0B8C8),
            java.util.Map.entry("medic", 0x7FE08A), java.util.Map.entry("warden", 0xC59BFF));

    private int viewX() {
        return innerX();
    }

    private int viewY() {
        return innerY() + 14;
    }

    private int viewW() {
        return innerW() - PANEL_W - 4;
    }

    private int viewH() {
        return innerH() - 14;
    }

    private int originX() {
        return viewX() + 14 + (int) panX;
    }

    private int originY() {
        return viewY() + LABEL_BAND + 8 + (int) panY;
    }

    private int panelX() {
        return innerX() + innerW() - PANEL_W;
    }

    private void clampPan() {
        double minX = Math.min(0, viewW() - canvasW - 28);
        double minY = Math.min(0, viewH() - canvasH - LABEL_BAND - 16);
        panX = Math.max(minX, Math.min(0, panX));
        panY = Math.max(minY, Math.min(0, panY));
    }

    private boolean inView(double x, double y) {
        return x >= viewX() && x < viewX() + viewW() && y >= viewY() && y < viewY() + viewH();
    }

    private GridNode gridAt(double x, double y) {
        if (!inView(x, y)) {
            return null;
        }
        for (GridNode g : gridNodes) {
            int sx = originX() + g.cx();
            int sy = originY() + g.cy();
            if (x >= sx && x < sx + G_SLOT && y >= sy && y < sy + G_SLOT) {
                return g;
            }
        }
        return null;
    }

    private GridNode gridOf(String id) {
        for (GridNode g : gridNodes) {
            if (g.node().id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    private void layoutGrid(String clazz, boolean buyable) {
        ways.clear();
        gridNodes.clear();
        int maxRow = 0;
        for (CoreStats.NodeLine node : stats.nodes) {
            if (!clazz.equals(node.clazz)) {
                continue;
            }
            maxRow = Math.max(maxRow, node.row);
            if (node.path != null && !node.path.isBlank() && !ways.contains(node.path)) {
                ways.add(node.path);
            }
        }
        boolean own = activeTab == ownTab();
        String taken = own && stats.pathTaken != null ? stats.pathTaken : "";
        if (viewWay == null || !ways.contains(viewWay)) {
            viewWay = !taken.isBlank() && ways.contains(taken) ? taken : ways.isEmpty() ? "" : ways.get(0);
        }
        shownWay = viewWay;
        gridMaxRow = Math.max(1, maxRow);
        canvasW = gridMaxRow * G_TIER + G_SLOT;
        canvasH = 6 * G_HALF + G_SLOT + 14;
        PANEL_W = Math.max(118, Math.min(156, innerW() * 3 / 10));
        // Open at the player's frontier: the last tier with something learned.
        String placeKey = clazz + "/" + shownWay;
        if (!panPlaced || !placeKey.equals(panFor)) {
            panPlaced = true;
            panFor = placeKey;
            int frontier = 0;
            for (CoreStats.NodeLine node : stats.nodes) {
                boolean visible = clazz.equals(node.clazz)
                        && (node.path == null || node.path.isBlank() || node.path.equals(shownWay));
                if (visible && node.level > 0) {
                    frontier = Math.max(frontier, node.row + 1);
                }
            }
            panX = -(Math.min(frontier, gridMaxRow) * G_TIER + G_SLOT / 2.0 - viewW() / 2.0);
            panY = 0;
        }
        clampPan();

        // The way buttons, at the right of the banner.
        int bx = left + W - 10;
        for (int i = ways.size() - 1; i >= 0; i--) {
            String way = ways.get(i);
            Component label = Component.translatable("nightsiege.path." + way);
            int w = Math.max(48, Minecraft.getInstance().font.width(label) + 14);
            bx -= w;
            Button button = Button.builder(label, b -> {
                viewWay = way;
                selectedId = null;
                rebuildWidgets();
            }).bounds(bx, top + 10, w, 14).build();
            button.active = !way.equals(shownWay);
            addRenderableWidget(button);
            bx -= 3;
        }

        for (CoreStats.NodeLine node : stats.nodes) {
            if (!clazz.equals(node.clazz)) {
                continue;
            }
            boolean trunk = node.path == null || node.path.isBlank();
            if (!trunk && !node.path.equals(shownWay)) {
                continue;
            }
            gridNodes.add(new GridNode(node, node.row * G_TIER, Math.max(0, node.col) * G_HALF));
        }

        // The learn button at the foot of the info panel.
        GridNode chosen = selectedId == null ? null : gridOf(selectedId);
        if (chosen != null && buyable && chosen.node().level < chosen.node().maxLevel) {
            CoreStats.NodeLine n = chosen.node();
            Button learn = Button.builder(Component.translatable("nightsiege.ui.learn", n.cost),
                    b -> send("buy:" + n.id)).bounds(panelX() + 8, innerY() + innerH() - 26, PANEL_W - 16, 18).build();
            learn.active = n.blocked == null;
            addRenderableWidget(learn);
        }
    }

    /** Edges: one per requirement term; a term naming several nodes of one tier hangs on a bracket. */
    private List<Edge> gridEdges() {
        List<Edge> edges = new ArrayList<>();
        for (GridNode child : gridNodes) {
            for (List<String[]> any : needsOf(child.node())) {
                List<GridNode> parents = new ArrayList<>();
                List<Integer> levels = new ArrayList<>();
                for (String[] need : any) {
                    GridNode parent = gridOf(need[0]);
                    if (parent != null) {
                        parents.add(parent);
                        levels.add(parseInt(need[1]));
                    }
                }
                if (!parents.isEmpty()) {
                    edges.add(new Edge(parents, levels, child));
                }
            }
        }
        return edges;
    }

    /** The focused node, everything that leads to it and what it leads on to. */
    private java.util.Set<String> focusSet(String id, List<Edge> edges) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (id == null || gridOf(id) == null) {
            return set;
        }
        java.util.ArrayDeque<String> up = new java.util.ArrayDeque<>();
        up.add(id);
        set.add(id);
        while (!up.isEmpty()) {
            String current = up.poll();
            for (Edge edge : edges) {
                if (!edge.child().node().id.equals(current)) {
                    continue;
                }
                for (GridNode parent : edge.parents()) {
                    if (set.add(parent.node().id)) {
                        up.add(parent.node().id);
                    }
                }
            }
        }
        java.util.Set<String> next = new java.util.HashSet<>(java.util.List.of(id));
        for (int depth = 0; depth < 2; depth++) {
            java.util.Set<String> found = new java.util.HashSet<>();
            for (Edge edge : edges) {
                for (GridNode parent : edge.parents()) {
                    if (next.contains(parent.node().id) && !isClosed(edge.child().node())) {
                        found.add(edge.child().node().id);
                    }
                }
            }
            set.addAll(found);
            next = found;
        }
        return set;
    }

    private static boolean isClosed(CoreStats.NodeLine n) {
        return n.level == 0 && ("choice".equals(n.blocked) || "lockedby".equals(n.blocked) || "path".equals(n.blocked));
    }

    private static boolean satisfied(Edge edge, int i) {
        return edge.parents().get(i).node().level >= edge.levels().get(i);
    }

    private static int dim(int color) {
        return (color & 0x00FFFFFF) | 0x50000000;
    }

    private void hline(GuiGraphicsExtractor gui, int x1, int x2, int y, int thick, int color) {
        gui.fill(Math.min(x1, x2), y - thick / 2, Math.max(x1, x2) + 1, y - thick / 2 + thick, color);
    }

    private void vline(GuiGraphicsExtractor gui, int x, int y1, int y2, int thick, int color) {
        gui.fill(x - thick / 2, Math.min(y1, y2), x - thick / 2 + thick, Math.max(y1, y2) + 1, color);
    }

    /** One edge: stubs from the parents (over a bracket when there are several) into the child. */
    private void drawEdge(GuiGraphicsExtractor gui, Edge edge, int color, int bright, int thick) {
        int ox = originX();
        int oy = originY();
        GridNode child = edge.child();
        int cx = ox + child.cx();
        int cy = oy + child.cy() + G_SLOT / 2;
        boolean oneTier = true;
        for (GridNode p : edge.parents()) {
            oneTier &= p.node().row == edge.parents().get(0).node().row;
        }
        if (edge.parents().size() == 1 || !oneTier) {
            for (int i = 0; i < edge.parents().size(); i++) {
                GridNode p = edge.parents().get(i);
                int c = satisfied(edge, i) ? bright : color;
                int px = ox + p.cx() + G_SLOT;
                int py = oy + p.cy() + G_SLOT / 2;
                int bus = cx - 9;
                hline(gui, px, bus, py, thick, c);
                vline(gui, bus, py, cy, thick, c);
                hline(gui, bus, cx, cy, thick, c);
            }
            return;
        }
        // Several nodes of one tier: a bracket behind them, then one line on.
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int bx = ox + edge.parents().get(0).cx() + G_SLOT + 8;
        boolean any = false;
        for (int i = 0; i < edge.parents().size(); i++) {
            GridNode p = edge.parents().get(i);
            int py = oy + p.cy() + G_SLOT / 2;
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
            boolean met = satisfied(edge, i);
            any |= met;
            hline(gui, ox + p.cx() + G_SLOT, bx, py, thick, met ? bright : color);
        }
        int mainColor = any ? bright : color;
        vline(gui, bx, minY, maxY, thick, color);
        int join = Math.max(minY, Math.min(maxY, cy));
        if (any) {
            // Light the bracket only from the learned node to the line that leaves.
            for (int i = 0; i < edge.parents().size(); i++) {
                if (satisfied(edge, i)) {
                    vline(gui, bx, oy + edge.parents().get(i).cy() + G_SLOT / 2, join, thick, bright);
                }
            }
        }
        int bus = cx - 9;
        hline(gui, bx, bus, join, thick, mainColor);
        vline(gui, bus, join, cy, thick, mainColor);
        hline(gui, bus, cx, cy, thick, mainColor);
    }

    private GridNode drawGrid(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int rgb = activeTab.color & 0xFFFFFF;
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();

        // Header line above the tree.
        String line;
        if (activeTab == ownTab()) {
            line = Component.translatable(activeTab.clazz.translationKey()).getString();
            if (stats.pathTaken != null && !stats.pathTaken.isBlank()) {
                line += " · " + Component.translatable("nightsiege.path." + stats.pathTaken).getString();
                if (stats.subclass != null && !stats.subclass.isBlank()) {
                    line += " · " + Component.translatable("nightsiege.subclass." + stats.subclass).getString();
                }
            }
            line += "   " + Component.translatable("nightsiege.ui.points_short", stats.points).getString();
        } else if (!hasClass()) {
            line = Component.translatable("nightsiege.ui.preview_free").getString();
        } else {
            line = Component.translatable("nightsiege.ui.preview_other",
                    Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT))).getString();
        }
        gui.text(font, font.plainSubstrByWidth(line, vw - 4), vx + 4, innerY() + 3,
                activeTab == ownTab() ? GOLD : GRAY, true);

        gui.enableScissor(vx, vy, vx + vw, vy + vh);
        int ox = originX();
        int oy = originY();
        String[] lanes = LANES.get(shownWay);

        // Tier columns: faint stripes and their names.
        for (int row = 0; row <= gridMaxRow; row++) {
            int x = ox + row * G_TIER - 8;
            if (row % 2 == 0) {
                gui.fill(x, vy, x + G_TIER, vy + vh, 0x0CFFFFFF);
            }
            {
                // Steps are just numbered; only the ultimate's step says so.
                String label = row == 8 ? "Ult" : roman(row + 1);
                int cx = ox + row * G_TIER + G_SLOT / 2;
                gui.pose().pushMatrix();
                gui.pose().translate(cx, vy + 6);
                gui.pose().scale(0.75f, 0.75f);
                gui.text(font, label, -font.width(label) / 2, 0, row == 8 ? 0xFFFFD86B : 0xFFD8CFB8, true);
                gui.pose().popMatrix();
            }
        }
        gui.fill(vx, vy + LABEL_BAND - 6, vx + vw, vy + LABEL_BAND - 5, 0x40FFFFFF);

        // The two subclass lanes.
        if (lanes != null) {
            int x1 = ox + 3 * G_TIER - 10;
            int x2 = ox + gridMaxRow * G_TIER + G_SLOT + 10;
            int[][] spans = {{oy - 6, oy + 2 * G_HALF + G_SLOT + 7}, {oy + 4 * G_HALF - 7, oy + 6 * G_HALF + G_SLOT + 10}};
            for (int i = 0; i < 2; i++) {
                int color = LANE_COLORS.getOrDefault(lanes[i], rgb);
                boolean mine = lanes[i].equals(stats.subclass) && activeTab == ownTab();
                gui.fill(x1, spans[i][0], x2, spans[i][1], (mine ? 0x34000000 : 0x1C000000) | color);
                gui.fill(x1, spans[i][0], x2, spans[i][0] + 1, 0x90000000 | color);
                gui.fill(x1, spans[i][0], x1 + 2, spans[i][1], 0xC0000000 | color);
                String name = Component.translatable("nightsiege.subclass." + lanes[i]).getString().toUpperCase(Locale.ROOT);
                int ty = i == 0 ? oy + G_SLOT / 2 - 12 : oy + 5 * G_HALF + G_SLOT + 2;
                gui.pose().pushMatrix();
                gui.pose().translate(ox + 3 * G_TIER + G_SLOT / 2, ty);
                gui.pose().scale(0.75f, 0.75f);
                gui.text(font, name, -font.width(name) / 2, 0, 0xFF000000 | color, true);
                gui.pose().popMatrix();
            }
        }

        // Lines: dim everything, then the routes of the focused node in gold.
        List<Edge> edges = gridEdges();
        GridNode hovered = gridAt(mouseX, mouseY);
        String focusId = hovered != null ? hovered.node().id : selectedId;
        java.util.Set<String> focus = focusSet(focusId, edges);
        boolean focusing = !focus.isEmpty();
        for (int pass = 0; pass < 2; pass++) {
            for (Edge edge : edges) {
                boolean closed = isClosed(edge.child().node());
                boolean learned = edge.child().node().level > 0;
                int later = closed ? 0xFF332E3C : 0xFF77707F;
                int bright = learned ? 0xFF000000 | rgb : closed ? 0xFF4A4452 : 0xFFF0ECF6;
                if (focusing && !focus.contains(edge.child().node().id)) {
                    later = dim(later);
                    bright = dim(bright);
                }
                boolean onRoute = focusing && focus.contains(edge.child().node().id);
                if (onRoute) {
                    boolean parentIn = false;
                    for (GridNode p : edge.parents()) {
                        parentIn |= focus.contains(p.node().id);
                    }
                    onRoute = parentIn;
                }
                if (pass == 0) {
                    drawEdge(gui, edge, 0xFF000000, 0xFF000000, onRoute ? 5 : 4);
                } else if (onRoute) {
                    drawEdge(gui, edge, 0xFFB08A2E, 0xFFFFD86B, 3);
                } else {
                    drawEdge(gui, edge, later, bright, 2);
                }
            }
        }

        // Nodes.
        long pulse = System.currentTimeMillis() / 40 % 50;
        for (GridNode g : gridNodes) {
            CoreStats.NodeLine n = g.node();
            int x = ox + g.cx();
            int y = oy + g.cy();
            boolean maxed = n.level >= n.maxLevel;
            boolean closed = isClosed(n);
            boolean ult = n.row == 8;
            boolean key = n.row == gridMaxRow;
            int border;
            if (maxed) {
                border = 0xFFE8B84A;
            } else if (n.level > 0) {
                border = 0xFF000000 | rgb;
            } else if (n.blocked == null && activeTab == ownTab()) {
                int a = 0x90 + (int) (Math.abs(25 - pulse) * 4);
                border = (a << 24) | 0xF4F0FA;
            } else if (closed) {
                border = 0xFF3A2C30;
            } else {
                border = 0xFF5A5466;
            }
            if (ult || key) {
                gui.fill(x - 3, y - 3, x + G_SLOT + 3, y + G_SLOT + 3, 0xFF000000);
                gui.fill(x - 2, y - 2, x + G_SLOT + 2, y + G_SLOT + 2, maxed || n.level > 0 ? 0xFFE8B84A : 0xFF7A6430);
            }
            gui.fill(x, y, x + G_SLOT, y + G_SLOT, 0xFF000000);
            gui.fill(x + 1, y + 1, x + G_SLOT - 1, y + G_SLOT - 1, border);
            gui.fill(x + 3, y + 3, x + G_SLOT - 3, y + G_SLOT - 3, n.level > 0 ? 0xFF2C2638 : 0xFF1A1720);
            if (n.level > 0) {
                gui.fill(x + 3, y + 3, x + G_SLOT - 3, y + 4, 0x50FFFFFF);
            }
            gui.item(iconOf(n.icon), x + 6, y + 6);
            if (closed) {
                gui.fill(x + 3, y + 3, x + G_SLOT - 3, y + G_SLOT - 3, 0xA0101010);
                for (int i = 5; i < G_SLOT - 5; i++) {
                    gui.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFE04040);
                    gui.fill(x + G_SLOT - 1 - i, y + i, x + G_SLOT - i, y + i + 1, 0xFFE04040);
                }
            } else if (n.level == 0 && n.blocked != null && !"points".equals(n.blocked)) {
                gui.fill(x + 3, y + 3, x + G_SLOT - 3, y + G_SLOT - 3, 0x70000000);
            }
            if (focusing && !focus.contains(n.id)) {
                gui.fill(x - 3, y - 3, x + G_SLOT + 3, y + G_SLOT + 3, 0x80000000);
            }
            if (n.id.equals(selectedId)) {
                int c = 0xFFFFD86B;
                gui.fill(x - 2, y - 2, x + G_SLOT + 2, y - 1, c);
                gui.fill(x - 2, y + G_SLOT + 1, x + G_SLOT + 2, y + G_SLOT + 2, c);
                gui.fill(x - 2, y - 2, x - 1, y + G_SLOT + 2, c);
                gui.fill(x + G_SLOT + 1, y - 2, x + G_SLOT + 2, y + G_SLOT + 2, c);
            }
            // Level pips under the slot.
            int pips = Math.max(1, n.maxLevel);
            int pw = 4;
            int startX = x + G_SLOT / 2 - (pips * (pw + 1) - 1) / 2;
            for (int i = 0; i < pips; i++) {
                int px = startX + i * (pw + 1);
                gui.fill(px, y + G_SLOT + 2, px + pw, y + G_SLOT + 5, 0xFF000000);
                int fill = i < n.level ? (maxed ? 0xFFE8B84A : 0xFF000000 | rgb) : 0xFF3A3644;
                gui.fill(px, y + G_SLOT + 2, px + pw, y + G_SLOT + 4, fill);
            }
            if (n.id.contains("_cross_")) {
                gui.text(font, "↔", x + G_SLOT - 6, y - 4, 0xFF80D0FF, true);
            }
            if (isHunterNode(n.id)) {
                gui.text(font, "◎", x + G_SLOT - 6, y - 4, 0xFF7CFF7C, true);
            }
        }
        gui.disableScissor();

        drawInfoPanel(gui, font, hovered);
        return hovered;
    }

    private static String roman(int n) {
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        String[] tens = {"", "X", "XX"};
        return tens[Math.min(2, n / 10)] + ones[n % 10];
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            String prefix = "";
            for (String word : paragraph.split(" ")) {
                String tryLine = current.length() == 0 ? word : current + " " + word;
                if (font.width(prefix + tryLine) > width && current.length() > 0) {
                    out.add(prefix + current);
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(tryLine);
                }
            }
            out.add(prefix + current);
        }
        return out;
    }

    private String nameOf(String id) {
        return Component.translatable("nightsiege.skill." + id).getString();
    }

    /** Right-hand panel: everything about the selected (or hovered) node, or a small legend. */
    private void drawInfoPanel(GuiGraphicsExtractor gui, Font font, GridNode hovered) {
        int rgb = activeTab.color & 0xFFFFFF;
        int px = panelX();
        int py = innerY();
        int pw = PANEL_W;
        int ph = innerH();
        gui.fill(px, py, px + pw, py + ph, 0xF0100D16);
        gui.fill(px, py, px + 1, py + ph, 0xC0000000 | rgb);
        gui.fill(px, py, px + pw, py + 1, 0x60000000 | rgb);
        int tx = px + 8;
        int tw = pw - 14;
        int y = py + 6;
        GridNode g = hovered != null ? hovered : selectedId == null ? null : gridOf(selectedId);
        if (g == null) {
            gui.text(font, Component.translatable("nightsiege.ui.panel_title").getString(), tx, y, 0xFFFFD86B, true);
            y += 14;
            String help = Component.translatable("nightsiege.ui.panel_help").getString() + "\n"
                    + Component.translatable("nightsiege.ui.grid_hint").getString();
            for (String line : wrap(font, help, tw)) {
                gui.text(font, line, tx, y, 0xFFB8B0C8, false);
                y += 10;
            }
            y += 6;
            int[][] legend = {{0xE8B84A, 0}, {rgb, 1}, {0xF4F0FA, 2}, {0x5A5466, 3}, {0xE04040, 4}, {0xFFD86B, 5}};
            for (int[] entry : legend) {
                gui.fill(tx, y + 1, tx + 8, y + 8, 0xFF000000 | entry[0]);
                gui.text(font, Component.translatable("nightsiege.ui.legend." + entry[1]).getString(), tx + 12, y, 0xFFD0C8DC, false);
                y += 11;
            }
            return;
        }
        CoreStats.NodeLine n = g.node();
        gui.fill(tx - 1, y - 1, tx + 19, y + 19, 0xFF000000 | rgb);
        gui.fill(tx, y, tx + 18, y + 18, 0xFF1A1720);
        gui.item(iconOf(n.icon), tx + 1, y + 1);
        List<String> title = wrap(font, nameOf(n.id), tw - 24);
        for (int i = 0; i < title.size(); i++) {
            gui.text(font, title.get(i), tx + 24, y + (title.size() == 1 ? 5 : i * 10), 0xFFFFFFFF, true);
        }
        y += Math.max(22, title.size() * 10 + 4);
        List<String> lines = new ArrayList<>();
        String sub = Component.translatable("nightsiege.subclass_node." + n.id).getString();
        if (!sub.equals("nightsiege.subclass_node." + n.id)) {
            lines.add("§6" + sub);
        }
        if (n.id.contains("_cross_")) {
            lines.add("§b" + Component.translatable("nightsiege.ui.cross").getString());
        }
        if (isHunterNode(n.id)) {
            lines.add("§a" + Component.translatable("nightsiege.ui.hunter").getString());
        }
        String descKey = "nightsiege.skill." + n.id + ".desc";
        if (Language.getInstance().has(descKey)) {
            lines.add("§7" + Language.getInstance().getOrDefault(descKey).replace("\n", " "));
        }
        lines.add("§f" + Component.translatable("nightsiege.ui.node_level", n.level, n.maxLevel).getString()
                + (n.level < n.maxLevel ? "  §e" + Component.translatable("nightsiege.ui.node_cost", n.cost).getString() : ""));
        // What it needs, term by term.
        List<List<String[]>> needs = needsOf(n);
        if (!needs.isEmpty()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.needs").getString());
            for (List<String[]> any : needs) {
                StringBuilder sb = new StringBuilder();
                boolean met = false;
                for (String[] need : any) {
                    GridNode p = gridOf(need[0]);
                    int lvl = parseInt(need[1]);
                    if (p != null && p.node().level >= lvl) {
                        met = true;
                    }
                    if (sb.length() > 0) {
                        sb.append(Component.translatable("nightsiege.ui.or").getString());
                    }
                    sb.append(nameOf(need[0])).append(lvl > 1 ? " " + lvl : "");
                }
                lines.add((met ? "§a✔ " : "§c✘ ") + sb);
            }
        }
        // What it opens.
        List<String> opens = new ArrayList<>();
        for (Edge edge : gridEdges()) {
            for (GridNode p : edge.parents()) {
                if (p.node().id.equals(n.id) && !opens.contains(nameOf(edge.child().node().id))) {
                    opens.add(nameOf(edge.child().node().id));
                }
            }
        }
        if (!opens.isEmpty()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.opens").getString());
            lines.add("§b→ " + String.join(", ", opens));
        }
        if (n.locks != null && !n.locks.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : n.locks) {
                names.add(nameOf(id));
            }
            lines.add("§c" + Component.translatable("nightsiege.ui.locks", String.join(", ", names)).getString());
        }
        if (n.group != null && !n.group.isBlank()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.choice_hint").getString());
        }
        if (activeTab != ownTab()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.view_only").getString());
        } else if (n.blocked != null && n.level < n.maxLevel) {
            lines.add("§c" + Component.translatable("nightsiege.menu.blocked_" + n.blocked, n.cost).getString());
        }
        int bottom = py + ph - (activeTab == ownTab() ? 32 : 6);
        for (String raw : lines) {
            String color = raw.startsWith("§") ? raw.substring(0, 2) : "";
            String text = raw.startsWith("§") ? raw.substring(2) : raw;
            for (String part : wrap(font, text, tw)) {
                if (y + 9 > bottom) {
                    return;
                }
                gui.text(font, color + part, tx, y, 0xFFFFFFFF, false);
                y += 10;
            }
            y += 1;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!gridMode || confirming != null || !inView(event.x(), event.y())) {
            return false;
        }
        GridNode hit = gridAt(event.x(), event.y());
        if (hit != null) {
            boolean again = hit.node().id.equals(selectedId);
            selectedId = hit.node().id;
            if (doubleClick && again && activeTab == ownTab() && hit.node().blocked == null) {
                send("buy:" + hit.node().id);
            }
            rebuildWidgets();
            return true;
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging && gridMode) {
            panX += dx;
            panY += dy;
            clampPan();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (gridMode && inView(x, y)) {
            panX += (scrollY + scrollX) * 30;
            clampPan();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** The tree from left to right: trunk on the left, one row per path. */
    private void layoutNodes(String clazz, boolean buyable) {
        gridMode = isGrid(clazz);
        if (gridMode) {
            layoutGrid(clazz, buyable);
            return;
        }
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
        treeLeft = x0;
        treeRight = x0 + treeW;
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
            if (index >= 0) {
                pathRows.putIfAbsent(node.path, y);
            }
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
        if (activeTab == null) {
            activeTab = Tab.STATS;
        }

        // Everything decorative first, so the real buttons drawn by super sit on top.
        drawTabs(gui, false);
        drawWindow(gui, font);
        drawBackdrop(gui);
        CoreStats.NodeLine hovered = null;
        if (activeTab == Tab.STATS) {
            drawStats(gui, font);
        } else if (activeTab == Tab.PERKS) {
            drawPerks(gui, font);
        } else if (!stats.skillsEnabled) {
            gui.text(font, Component.translatable("nightsiege.ui.skills_off"), innerX() + 8, innerY() + 8, GRAY, true);
        } else if (gridMode) {
            drawGrid(gui, font, mouseX, mouseY);
        } else {
            drawTreeHeader(gui, font);
            drawPathBand(gui, font);
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
            if (mouseX >= guideTabX() && mouseX < guideTabX() + TAB_W && mouseY >= top - TAB_H + 2 && mouseY < top) {
                tooltip(gui, font, List.of(Component.translatable("nightsiege.guide.title").getString()), mouseX, mouseY);
            } else if (hoveredTab != null) {
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
        if (tab == Tab.PERKS) {
            return Component.translatable("nightsiege.ui.banner_title.perks").getString();
        }
        return Component.translatable(tab.clazz.translationKey()).getString();
    }

    /** Idle tabs sit behind the window; the active one is drawn in front and joins it. */
    private void drawTabs(GuiGraphicsExtractor gui, boolean activeOnly) {
        if (!activeOnly) {
            int x = guideTabX();
            int y = top - TAB_H + 3;
            int h = TAB_H - 3;
            gui.fill(x, y, x + TAB_W, y + h, 0xFF000000);
            gui.fill(x + 1, y + 1, x + TAB_W - 1, y + h, 0xFF9C8A5A);
            gui.fill(x + 1, y + 1, x + TAB_W - 1, y + 2, FRAME_LIGHT);
            gui.fill(x + 1, y + 1, x + 2, y + h, FRAME_LIGHT);
            gui.item(iconOf("nightsiege:siege_guide"), x + 6, y + 5);
        }
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
        drawBanner(gui, font);
    }

    /** A dark strip across the top: the tab's icon, its name large in the tab's colour, a tagline. */
    private void drawBanner(GuiGraphicsExtractor gui, Font font) {
        int x1 = left + 4;
        int x2 = left + W - 4;
        int y1 = top + 4;
        int y2 = y1 + BANNER;
        gui.fill(x1, y1, x2, y2, 0xFF17161B);
        // The tab's colour glows in from the left and fades out.
        int rgb = activeTab.color & 0xFFFFFF;
        int strips = 48;
        int span = (x2 - x1) * 2 / 3;
        for (int i = 0; i < strips; i++) {
            int alpha = (int) (0x58 * (1.0 - i / (double) strips));
            int sx = x1 + span * i / strips;
            int ex = x1 + span * (i + 1) / strips;
            gui.fill(sx, y1, ex, y2, (alpha << 24) | rgb);
        }
        gui.fill(x1, y2 - 1, x2, y2, 0xFF000000 | rgb);
        gui.fill(x1, y1, x2, y1 + 1, 0x40FFFFFF);

        gui.item(iconOf(activeTab.icon), x1 + 6, y1 + 5);
        String name = bannerName(activeTab);
        gui.pose().pushMatrix();
        gui.pose().translate(x1 + 28, y1 + 5);
        gui.pose().scale(2.0f, 2.0f);
        gui.text(font, name, 0, 0, activeTab.color, true);
        gui.pose().popMatrix();
        String tagline = Component.translatable("nightsiege.ui.banner_sub." + activeTab.key).getString();
        int tx = x1 + 28 + font.width(name) * 2 + 10;
        gui.text(font, tagline, tx, y1 + 12, 0xFFB8B4C0, true);

        String badge = null;
        if (activeTab.clazz != null && activeTab == ownTab()) {
            badge = Component.translatable("nightsiege.ui.your_class").getString();
        } else if (activeTab == Tab.CORE) {
            badge = Component.translatable("nightsiege.ui.banner_pot", stats.corePoints).getString();
        }
        if (badge != null && !gridMode) {
            gui.text(font, badge, x2 - 8 - font.width(badge), y1 + 9, 0xFFFFD86B, true);
        }
    }

    private String bannerName(Tab tab) {
        if (tab == Tab.STATS) {
            return Component.translatable("nightsiege.ui.banner_title.stats").getString();
        }
        if (tab == Tab.PERKS) {
            return Component.translatable("nightsiege.ui.banner_title.perks").getString();
        }
        return Component.translatable(tab.clazz.translationKey()).getString();
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
        if (gridMode) {
            // Dark fantasy: deep night with the class colour glowing in from the edges.
            int rgb = activeTab.color & 0xFFFFFF;
            gui.fill(x, y, x + w, y + h, 0xFF0E0C14);
            for (int i = 0; i < 12; i++) {
                int a = 0x16 - i;
                gui.fill(x, y + i, x + w, y + i + 1, (a << 24) | rgb);
                gui.fill(x, y + h - 1 - i, x + w, y + h - i, (a << 24) | rgb);
            }
            return;
        }
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
            line = Component.translatable("nightsiege.ui.my_class", Component.translatable(activeTab.clazz.translationKey())).getString();
            if (gridMode && stats.pathTaken != null && !stats.pathTaken.isBlank()) {
                line += " · " + Component.translatable("nightsiege.path." + stats.pathTaken).getString();
                if (stats.subclass != null && !stats.subclass.isBlank()) {
                    line += " · " + Component.translatable("nightsiege.subclass." + stats.subclass).getString();
                }
            }
            line += "   " + Component.translatable("nightsiege.ui.points", stats.points, stats.earned).getString();
        } else if (!hasClass()) {
            line = Component.translatable("nightsiege.ui.preview_free").getString();
        } else {
            line = Component.translatable("nightsiege.ui.preview_other",
                    Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT))).getString();
        }
        gui.text(font, line, x, y, activeTab == Tab.CORE || activeTab == ownTab() ? GOLD : GRAY, true);
    }

    /** A soft band in the class colour behind the path the player took. */
    private void drawPathBand(GuiGraphicsExtractor gui, Font font) {
        if (activeTab != ownTab() || stats.pathTaken == null || stats.pathTaken.isBlank()) {
            return;
        }
        Integer y = pathRows.get(stats.pathTaken);
        if (y == null) {
            return;
        }
        int rgb = activeTab.color & 0xFFFFFF;
        int x1 = treeLeft + 92 - 12;
        int x2 = Math.min(innerX() + innerW() - 2, treeRight + 12);
        gui.fill(x1, y - 4, x2, y + SLOT + 12, 0x38000000 | rgb);
        gui.fill(x1, y - 4, x1 + 2, y + SLOT + 12, 0xC0000000 | rgb);
    }

    /** The nodes a grid node needs, as alternatives that must all be met. */
    private static List<List<String[]>> needsOf(CoreStats.NodeLine node) {
        List<List<String[]>> all = new ArrayList<>();
        if (node.requires == null || node.requires.isBlank()) {
            return all;
        }
        for (String term : node.requires.split(",")) {
            List<String[]> any = new ArrayList<>();
            for (String option : term.split("\\|")) {
                String o = option.trim();
                if (o.isEmpty()) {
                    continue;
                }
                int colon = o.indexOf(':');
                any.add(colon > 0 ? new String[]{o.substring(0, colon), o.substring(colon + 1)}
                        : new String[]{o, String.valueOf(Math.max(1, node.requiresLevel))});
            }
            all.add(any);
        }
        return all;
    }

    /** Marking and the hunt: the old hunter's skills, open to both archer ways. */
    private static boolean isHunterNode(String id) {
        return id.equals("b_mark") || id.equals("x_mark") || id.equals("b_hunt") || id.equals("x_hunt")
                || id.equals("b_pack") || id.equals("x_pack");
    }

    private NodeButton buttonOf(String id) {
        for (NodeButton button : nodeButtons) {
            if (button.node().id.equals(id)) {
                return button;
            }
        }
        return null;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
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
            boolean closed = "choice".equals(node.blocked) || "lockedby".equals(node.blocked)
                    || ("path".equals(node.blocked) && gridMode);
            if (closed && node.level == 0) {
                gui.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xA0101010);
                for (int i = 4; i < SLOT - 4; i++) {
                    gui.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFE04040);
                    gui.fill(x + SLOT - 1 - i, y + i, x + SLOT - i, y + i + 1, 0xFFE04040);
                }
            }
            if (gridMode && (node.id.contains("_cross_"))) {
                gui.text(font, "↔", x + SLOT - 7, y - 2, 0xFF80D0FF, true);
            }
            if (gridMode && isHunterNode(node.id)) {
                gui.text(font, "◎", x + SLOT - 7, y - 2, 0xFF7CFF7C, true);
            }

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
        if (activeTab == Tab.PERKS) {
            gui.text(font, Component.translatable("nightsiege.ui.footer_perks").getString(), left + 10, top + H - 19, TITLE, false);
            return;
        }
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

    private static final java.util.Map<String, Integer> PERK_COLORS = java.util.Map.of(
            "vitality", 0xFFFF5A64, "bulwark", 0xFFC0C8DC, "tides", 0xFF4FD6E6,
            "flame", 0xFFFF9A3C, "vigilance", 0xFFFFD84A);

    /** The five perks of the main core: stage, fragments to the next one and what they do now. */
    private void drawPerks(GuiGraphicsExtractor gui, Font font) {
        int x = innerX() + 6;
        int y = innerY() + 5;
        String head = Component.translatable(stats.perkInRange ? "nightsiege.ui.perks_in_range" : "nightsiege.ui.perks_out_of_range",
                stats.perkRadius).getString();
        gui.text(font, head, x, y, stats.perkInRange ? GREEN : GOLD, true);
        int rowH = 30;
        int rowY = innerY() + 18;
        int w = innerW() - 12;
        for (CoreStats.PerkLine perk : stats.perks) {
            int color = PERK_COLORS.getOrDefault(perk.id, WHITE);
            gui.fill(x, rowY, x + w, rowY + rowH - 3, 0xC0121116);
            gui.fill(x, rowY, x + 2, rowY + rowH - 3, color);
            gui.fill(x, rowY, x + w, rowY + 1, 0x40FFFFFF);
            gui.item(iconOf("nightsiege:core_fragment_" + perk.id), x + 6, rowY + 5);
            String name = Component.translatable("nightsiege.perk." + perk.id).getString();
            gui.text(font, name, x + 28, rowY + 4, color, true);
            // Five pips for the five stages.
            for (int i = 0; i < 5; i++) {
                int px = x + 28 + i * 11;
                int py = rowY + 15;
                gui.fill(px, py, px + 9, py + 6, 0xFF000000);
                gui.fill(px + 1, py + 1, px + 8, py + 5, i < perk.stage ? color : 0xFF3A3940);
            }
            String now = perk.stage <= 0 ? Component.translatable("nightsiege.ui.perk_none").getString()
                    : perkEffect(perk.id, perk.stage);
            gui.text(font, now, x + 100, rowY + 4, perk.stage > 0 ? WHITE : GRAY, true);
            String next;
            if (perk.nextAt <= 0) {
                next = Component.translatable("nightsiege.ui.perk_max").getString();
            } else {
                next = Component.translatable("nightsiege.ui.perk_next", perk.fragments - perk.stageAt,
                        perk.nextAt - perk.stageAt, perkEffect(perk.id, perk.stage + 1)).getString();
            }
            gui.text(font, next, x + 100, rowY + 15, GRAY, true);
            // Bar to the next stage on the right.
            int barW = 60;
            int bx = x + w - barW - 8;
            int by = rowY + 6;
            gui.fill(bx, by, bx + barW, by + 5, 0xFF000000);
            int span = Math.max(1, perk.nextAt - perk.stageAt);
            int filled = perk.nextAt <= 0 ? barW - 2 : (barW - 2) * (perk.fragments - perk.stageAt) / span;
            gui.fill(bx + 1, by + 1, bx + 1 + filled, by + 4, color);
            String count = perk.fragments + "/15";
            gui.text(font, count, bx + barW - font.width(count), by + 9, GRAY, true);
            rowY += rowH;
        }
    }

    private static String perkEffect(String id, int stage) {
        return switch (id) {
            case "vitality" -> Component.translatable("nightsiege.perk.vitality.now", 2 * stage).getString();
            case "bulwark" -> Component.translatable("nightsiege.perk.bulwark.now", stage, 5 * stage).getString();
            case "tides" -> Component.translatable(stage >= 3 ? "nightsiege.perk.tides.now2" : "nightsiege.perk.tides.now1").getString();
            case "flame" -> Component.translatable(stage >= 3 ? "nightsiege.perk.flame.now_fire" : "nightsiege.perk.flame.now",
                    10 * stage).getString();
            case "vigilance" -> Component.translatable("nightsiege.perk.vigilance.now", 10 * stage, 10 * stage).getString();
            default -> "";
        };
    }

    /** The tally as four cards: next siege, wave strength, record and ranking. */
    private void drawStats(GuiGraphicsExtractor gui, Font font) {
        int gap = 6;
        int colW = (innerW() - 3 * gap) / 2;
        int cardH = (innerH() - 3 * gap) / 2;
        int xL = innerX() + gap;
        int xR = xL + colW + gap;
        int yT = innerY() + gap;
        int yB = yT + cardH + gap;
        drawNextSiege(gui, font, xL, yT, colW, cardH);
        drawWaveStrength(gui, font, xL, yB, colW, cardH);
        drawRecord(gui, font, xR, yT, colW, cardH);
        drawRanking(gui, font, xR, yB, colW, cardH);
    }

    /** A dark translucent card with a coloured title strip. */
    private int card(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h, String titleKey, int color) {
        gui.fill(x, y, x + w, y + h, 0xC0121116);
        gui.fill(x, y, x + w, y + 1, 0x60FFFFFF);
        gui.fill(x, y + h - 1, x + w, y + h, 0x80000000);
        gui.fill(x, y + 1, x + 2, y + h - 1, color);
        gui.text(font, Component.translatable(titleKey).getString(), x + 7, y + 4, color, true);
        gui.fill(x + 7, y + 14, x + w - 7, y + 15, 0x30FFFFFF);
        return y + 19;
    }

    private void drawNextSiege(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h) {
        int cy = card(gui, font, x, y, w, h, "nightsiege.ui.card_next", Tab.STATS.color);
        gui.pose().pushMatrix();
        gui.pose().translate(x + 8, cy);
        gui.pose().scale(1.5f, 1.5f);
        String level = Component.translatable("nightsiege.ui.stat_level", stats.level).getString();
        gui.text(font, level, 0, 0, stats.siegeActive ? RED : WHITE, true);
        gui.pose().popMatrix();
        cy += 17;
        if (stats.siegeActive) {
            gui.text(font, Component.translatable("nightsiege.ui.stat_active").getString(), x + 8, cy, RED, true);
            gui.text(font, Component.translatable("nightsiege.ui.stat_time_left", clock(stats.secondsLeft)).getString(),
                    x + 8, cy + 12, GRAY, true);
            return;
        }
        // Countdown bar.
        int barX = x + 8;
        int barW = w - 16;
        gui.fill(barX - 1, cy - 1, barX + barW + 1, cy + 7, 0xFF000000);
        gui.fill(barX, cy, barX + barW, cy + 6, 0xFF2A2830);
        int filled = barW * Math.max(0, Math.min(100, stats.countdownPercent)) / 100;
        gui.fill(barX, cy, barX + filled, cy + 6, stats.clockRunning ? 0xFF4FC24F : 0xFF8A6A2A);
        gui.fill(barX, cy, barX + filled, cy + 1, 0x60FFFFFF);
        cy += 10;
        gui.text(font, Component.translatable("nightsiege.ui.stat_countdown",
                stats.countdownPercent, stats.countdownMinutesLeft).getString(), x + 8, cy, GRAY, true);
        cy += 12;
        int dot = stats.clockRunning ? 0xFF55FF55 : 0xFFFF5555;
        gui.fill(x + 8, cy + 2, x + 13, cy + 7, dot);
        gui.text(font, Component.translatable("nightsiege.ui.stat_players",
                stats.playersNear, stats.playersRequired).getString(), x + 17, cy, WHITE, true);
        cy += 11;
        gui.text(font, Component.translatable(stats.clockRunning ? "nightsiege.ui.stat_clock_on" : "nightsiege.ui.stat_clock_off",
                stats.coreRadius).getString(), x + 17, cy, stats.clockRunning ? GREEN : GRAY, true);
    }

    private void drawWaveStrength(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h) {
        int cy = card(gui, font, x, y, w, h, "nightsiege.ui.card_waves", 0xFFFF6B4A);
        String[][] rows = {
                {"minecraft:zombie_head", "nightsiege.ui.wave_mobs", String.valueOf(stats.mobsPerWave)},
                {"minecraft:golden_apple", "nightsiege.ui.wave_health", "x" + fmt(stats.healthMultiplier)},
                {"minecraft:iron_sword", "nightsiege.ui.wave_damage", "x" + fmt(stats.damageMultiplier)},
                {"minecraft:spawner", "nightsiege.ui.wave_max", String.valueOf(stats.maxLiveMobs)}};
        for (String[] row : rows) {
            gui.item(iconOf(row[0]), x + 6, cy - 3);
            gui.text(font, Component.translatable(row[1]).getString(), x + 26, cy + 1, GRAY, true);
            gui.text(font, row[2], x + w - 8 - font.width(row[2]), cy + 1, WHITE, true);
            cy += 16;
        }
    }

    private void drawRecord(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h) {
        int cy = card(gui, font, x, y, w, h, "nightsiege.ui.card_record", 0xFF6FA8FF);
        String[][] cells = {
                {"nightsiege.ui.rec_won", String.valueOf(stats.siegesWon), "FF55FF55"},
                {"nightsiege.ui.rec_lost", String.valueOf(stats.siegesLost), "FFFF5555"},
                {"nightsiege.ui.rec_streak", stats.currentStreak + " (" + stats.bestStreak + ")", "FFFFFFFF"},
                {"nightsiege.ui.rec_highest", String.valueOf(stats.highestLevelReached), "FFFFC94A"},
                {"nightsiege.ui.rec_kills", String.valueOf(stats.totalKills), "FFFFFFFF"},
                {"nightsiege.ui.rec_champs", String.valueOf(stats.totalChampionKills), "FFFFAA00"}};
        int cellW = (w - 16) / 2;
        for (int i = 0; i < cells.length; i++) {
            int cx = x + 8 + (i % 2) * cellW;
            int ry = cy + (i / 2) * 21;
            gui.text(font, Component.translatable(cells[i][0]).getString(), cx, ry, 0xFF8C8894, true);
            gui.text(font, cells[i][1], cx, ry + 10, (int) Long.parseLong(cells[i][2], 16), true);
        }
    }

    private void drawRanking(GuiGraphicsExtractor gui, Font font, int x, int y, int w, int h) {
        int cy = card(gui, font, x, y, w, h, "nightsiege.ui.card_ranking", 0xFFFFAA00);
        int[] medal = {0xFFFFD700, 0xFFD0D0D8, 0xFFCD7F32};
        int shown = 0;
        for (CoreStats.PlayerLine player : stats.players) {
            if (shown >= 5) {
                break;
            }
            int color = shown < 3 ? medal[shown] : GRAY;
            String rank = (shown + 1) + ".";
            gui.text(font, rank, x + 8, cy, color, true);
            String name = player.name;
            int maxName = w - 100;
            while (font.width(name) > maxName && name.length() > 3) {
                name = name.substring(0, name.length() - 1);
            }
            gui.text(font, name, x + 22, cy, player.online ? WHITE : GRAY, true);
            String score = Component.translatable("nightsiege.ui.rank_row", player.kills, player.championKills).getString();
            gui.text(font, score, x + w - 8 - font.width(score), cy, color, true);
            cy += 12;
            shown++;
        }
        if (shown == 0) {
            gui.text(font, Component.translatable("nightsiege.ui.rank_empty").getString(), x + 8, cy, GRAY, true);
        }
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
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
        if (gridMode) {
            String sub = Component.translatable("nightsiege.subclass_node." + node.id).getString();
            if (!sub.equals("nightsiege.subclass_node." + node.id)) {
                lines.add("§6" + sub);
            }
            if (node.id.contains("_cross_")) {
                lines.add("§b" + Component.translatable("nightsiege.ui.cross").getString());
            }
            if (isHunterNode(node.id)) {
                lines.add("§a" + Component.translatable("nightsiege.ui.hunter").getString());
            }
            if (node.group != null && !node.group.isBlank()) {
                lines.add("§8" + Component.translatable("nightsiege.ui.choice_hint").getString());
            }
            if (node.locks != null && !node.locks.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (String id : node.locks) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(Component.translatable("nightsiege.skill." + id).getString());
                }
                lines.add("§c" + Component.translatable("nightsiege.ui.locks", names.toString()).getString());
            }
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


    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static ItemStack iconOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
