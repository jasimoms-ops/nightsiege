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
    /** The dialog for the one free class change is open. */
    private boolean confirmingReset;
    private final List<NodeButton> nodeButtons = new ArrayList<>();
    /** Top edge of each path's row, for the highlight behind the player's own path. */
    private final java.util.Map<String, Integer> pathRows = new java.util.HashMap<>();
    private int treeLeft;
    private int treeRight;
    /** Class trees are drawn as a star map with zoom. */
    private boolean gridMode;
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
        W = big ? Math.max(404, Math.min(this.width - 16, 760)) : 404;
        H = big ? Math.max(244, Math.min(this.height - 34, 440)) : 244;
        left = (this.width - W) / 2;
        top = (this.height - H - TAB_H + 4) / 2 + TAB_H - 4;
        nodeButtons.clear();
        pathRows.clear();
        gridMode = false;
        if (activeTab == null) {
            Tab own = ownTab();
            activeTab = own != null ? own : Tab.STATS;
        }

        if (confirmingReset) {
            int dx = left + W / 2;
            int dy = top + H / 2;
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.confirm"), b -> {
                send("classreset");
                confirmingReset = false;
                rebuildWidgets();
            }).bounds(dx - 104, dy + 18, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                confirmingReset = false;
                rebuildWidgets();
            }).bounds(dx + 4, dy + 18, 100, 20).build());
            return;
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
        int footerX = left + 8;
        if (stats.creative && activeTab != Tab.CORE) {
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_grant"),
                    b -> send("dev:50")).bounds(footerX, footerY, 70, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.dev_reset"),
                    b -> send("dev:reset")).bounds(footerX + 74, footerY, 56, 18).build());
            footerX += 134;
        }
        if (stats.classResetAvailable && activeTab == ownTab()) {
            Component label = Component.translatable("nightsiege.ui.class_reset");
            addRenderableWidget(Button.builder(label, b -> {
                confirmingReset = true;
                rebuildWidgets();
            }).bounds(footerX, footerY, Math.max(90, font.width(label) + 12), 18).build());
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
    // class trees as a star map: trunk in the middle, the two ways reach out
    // to the left and the right, every tier is a ring. The mouse wheel zooms
    // in and out at the mouse; a click shows a node in the panel on the right.
    // ------------------------------------------------------------------

    /** Size of a node in tree units (one unit is one pixel at full zoom). */
    private static final int N_SLOT = 24;
    /** Radius of the first ring of a way, and the distance between rings. */
    private static final double RING0 = 96;
    private static final double RING = 40;
    /** Half the angle of a way's fan; its seven columns split it evenly. */
    private static final double FAN = Math.toRadians(45);
    private static final double COL_STEP = FAN / 3;
    /** Below this zoom the tree is drawn as a constellation of small stars. */
    private static final double STAR_ZOOM = 0.42;
    private static final double MAX_ZOOM = 1.6;
    /** Width of the info panel: narrower on small screens. */
    private int PANEL_W = 156;
    /** Where each class tab looks: centre x, centre y, zoom. Kept while the game runs. */
    private static final java.util.Map<String, double[]> VIEWS = new java.util.HashMap<>();
    private double camX;
    private double camY;
    private double zoom = 1.0;
    private double fitZoom = 0.4;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    /** The node shown in the info panel. */
    private static String selectedId;
    private final List<GridNode> gridNodes = new ArrayList<>();

    /** A node and its place in tree units (centre). */
    private record GridNode(CoreStats.NodeLine node, double cx, double cy) {
    }

    private record Edge(List<GridNode> parents, List<Integer> levels, GridNode child) {
    }

    /** The two subclasses of each way: upper side, lower side. */
    private static final java.util.Map<String, String[]> LANES = java.util.Map.of(
            "edge", new String[]{"blademaster", "berserker"},
            "might", new String[]{"axe", "paladin"},
            "bow", new String[]{"swift", "heavy"},
            "xbow", new String[]{"bolter", "trapper"},
            "shield", new String[]{"shieldfighter", "bulwark"},
            "bless", new String[]{"medic", "warden"},
            "mech", new String[]{"gunner", "electrician"},
            "chem", new String[]{"demolition", "alchemist"});
    private static final java.util.Map<String, Integer> LANE_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("blademaster", 0x6FA0FF), java.util.Map.entry("berserker", 0xE04848),
            java.util.Map.entry("axe", 0xD8913A), java.util.Map.entry("paladin", 0xF2D46A),
            java.util.Map.entry("swift", 0x5FD0E8), java.util.Map.entry("heavy", 0xC88A3A),
            java.util.Map.entry("bolter", 0xE87A30), java.util.Map.entry("trapper", 0x6CC46C),
            java.util.Map.entry("shieldfighter", 0x8FB4FF), java.util.Map.entry("bulwark", 0xB0B8C8),
            java.util.Map.entry("medic", 0x7FE08A), java.util.Map.entry("warden", 0xC59BFF),
            java.util.Map.entry("gunner", 0xE0B050), java.util.Map.entry("electrician", 0x6FD8FF),
            java.util.Map.entry("demolition", 0xF07040), java.util.Map.entry("alchemist", 0x8BD35A));

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

    private int panelX() {
        return innerX() + innerW() - PANEL_W;
    }

    private boolean inView(double x, double y) {
        return x >= viewX() && x < viewX() + viewW() && y >= viewY() && y < viewY() + viewH();
    }

    private double screenX(double wx) {
        return viewX() + viewW() / 2.0 + (wx - camX) * zoom;
    }

    private double screenY(double wy) {
        return viewY() + viewH() / 2.0 + (wy - camY) * zoom;
    }

    private double worldX(double sx) {
        return camX + (sx - viewX() - viewW() / 2.0) / zoom;
    }

    private double worldY(double sy) {
        return camY + (sy - viewY() - viewH() / 2.0) / zoom;
    }

    /** Where a node sits: trunk in the middle, each way a fan of rings to its side. */
    private double[] place(CoreStats.NodeLine n) {
        int col = n.col < 0 ? 3 : n.col;
        if (n.path == null || n.path.isBlank()) {
            return new double[]{(col - 3) * 20.0, (n.row - 0.5) * 38.0};
        }
        double r = ringRadius(n.row);
        double a = angleOf(Math.max(0, ways.indexOf(n.path)), col);
        return new double[]{r * Math.cos(a), r * Math.sin(a)};
    }

    /** The angle of a column: way 0 fans out to the left, way 1 to the right; column 0 is on top. */
    private static double angleOf(int way, int col) {
        double a = (col - 3) * COL_STEP;
        return way == 0 ? Math.PI - a : a;
    }

    private double angleOf(GridNode g) {
        CoreStats.NodeLine n = g.node();
        return angleOf(Math.max(0, ways.indexOf(n.path)), n.col < 0 ? 3 : n.col);
    }

    private static int ringRadius(int row) {
        return (int) Math.round(RING0 + Math.max(0, row - 2) * RING);
    }

    private void clampCam() {
        zoom = Math.max(fitZoom * 0.9, Math.min(MAX_ZOOM, zoom));
        camX = Math.max(minX, Math.min(maxX, camX));
        camY = Math.max(minY, Math.min(maxY, camY));
    }

    private void saveView(String clazz) {
        VIEWS.put(clazz, new double[]{camX, camY, zoom});
    }

    private GridNode gridAt(double x, double y) {
        if (!inView(x, y)) {
            return null;
        }
        double wx = worldX(x);
        double wy = worldY(y);
        double reach = Math.max(N_SLOT / 2.0 + 1, 6.0 / zoom);
        GridNode best = null;
        double bestD = Double.MAX_VALUE;
        for (GridNode g : gridNodes) {
            double dx = Math.abs(wx - g.cx());
            double dy = Math.abs(wy - g.cy());
            if (dx <= reach && dy <= reach && dx + dy < bestD) {
                bestD = dx + dy;
                best = g;
            }
        }
        return best;
    }

    private GridNode gridOf(String id) {
        for (GridNode g : gridNodes) {
            if (g.node().id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    private String shownClass = "";

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
        gridMaxRow = Math.max(1, maxRow);
        PANEL_W = Math.max(124, Math.min(170, innerW() * 3 / 10));
        minX = Double.MAX_VALUE;
        maxX = -Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;
        for (CoreStats.NodeLine node : stats.nodes) {
            if (!clazz.equals(node.clazz)) {
                continue;
            }
            double[] p = place(node);
            gridNodes.add(new GridNode(node, p[0], p[1]));
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (gridNodes.isEmpty()) {
            minX = maxX = minY = maxY = 0;
        }
        // A little room around the outer ring; the way names sit in the corners of the view.
        double spanX = maxX - minX + 2 * (N_SLOT + 14);
        double spanY = maxY - minY + 2 * (N_SLOT + 10);
        fitZoom = Math.min(viewW() / spanX, viewH() / spanY);
        shownClass = clazz;
        double[] view = VIEWS.get(clazz);
        if (view == null) {
            camX = (minX + maxX) / 2.0;
            camY = (minY + maxY) / 2.0;
            zoom = fitZoom;
        } else {
            camX = view[0];
            camY = view[1];
            zoom = view[2];
        }
        clampCam();

        // The learn button at the foot of the info panel.
        GridNode chosen = selectedId == null ? null : gridOf(selectedId);
        if (chosen != null && buyable && chosen.node().level < chosen.node().maxLevel) {
            CoreStats.NodeLine n = chosen.node();
            Button learn = Button.builder(Component.translatable("nightsiege.ui.learn", n.cost),
                    b -> send("buy:" + n.id)).bounds(panelX() + 8, innerY() + innerH() - 26, PANEL_W - 16, 18).build();
            learn.active = n.blocked == null;
            addRenderableWidget(learn);
        }
        // Zoom buttons: the whole tree, or the player's way.
        addRenderableWidget(Button.builder(Component.literal("⤢"), b -> {
            camX = (minX + maxX) / 2.0;
            camY = (minY + maxY) / 2.0;
            zoom = fitZoom;
            saveView(shownClass);
        }).bounds(viewX() + viewW() - 22, viewY() + viewH() - 20, 18, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("nightsiege.ui.zoom_all")))
                .build());
    }

    /** Edges: one per requirement term and parent. */
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
        for (int depth = 0; depth < 1; depth++) {
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
        return (color & 0x00FFFFFF) | 0x46000000;
    }

    private int laneColor(CoreStats.NodeLine n, int fallback) {
        return n.lane == null || n.lane.isBlank() ? fallback : LANE_COLORS.getOrDefault(n.lane, fallback);
    }

    /** A straight line in tree units, drawn as one turned rectangle. */
    private void line(GuiGraphicsExtractor gui, double x1, double y1, double x2, double y2, double thick, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) {
            return;
        }
        gui.pose().pushMatrix();
        gui.pose().translate((float) x1, (float) y1);
        gui.pose().rotate((float) Math.atan2(dy, dx));
        int t = Math.max(1, (int) Math.round(thick));
        gui.fill(0, -t / 2, (int) Math.round(len), -t / 2 + t, color);
        gui.pose().popMatrix();
    }

    /** The colour of a line: lit when met, dark when shut, faint when the eye is elsewhere. */
    private int edgeColor(CoreStats.NodeLine child, boolean met, boolean closed, int rgb, boolean dimmed, boolean stars) {
        int color = met ? 0xFF000000 | laneColor(child, rgb)
                : closed ? 0xFF3A3442 : (stars ? 0x80000000 : 0xC0000000) | laneColor(child, 0xD8D0E6);
        return dimmed ? dim(color) : color;
    }

    /**
     * "One of these" whose parents sit side by side on one ring of the child's way: drawn as
     * that piece of the ring plus one line to the child, instead of one line per parent.
     */
    private boolean isBus(Edge edge) {
        if (edge.parents().size() < 2) {
            return false;
        }
        CoreStats.NodeLine child = edge.child().node();
        if (child.path == null || child.path.isBlank()) {
            return false;
        }
        int row = edge.parents().get(0).node().row;
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (GridNode p : edge.parents()) {
            if (p.node().row != row || row < 2 || row >= child.row || !child.path.equals(p.node().path)) {
                return false;
            }
            lo = Math.min(lo, angleOf(p));
            hi = Math.max(hi, angleOf(p));
        }
        // No stranger may sit on the piece of ring between them.
        for (GridNode g : gridNodes) {
            CoreStats.NodeLine n = g.node();
            if (n.row == row && child.path.equals(n.path) && !edge.parents().contains(g)) {
                double a = angleOf(g);
                if (a > lo && a < hi) {
                    return false;
                }
            }
        }
        return true;
    }

    private void drawBus(GuiGraphicsExtractor gui, Edge edge, double thick, int color) {
        double r = ringRadius(edge.parents().get(0).node().row);
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (GridNode p : edge.parents()) {
            lo = Math.min(lo, angleOf(p));
            hi = Math.max(hi, angleOf(p));
        }
        int steps = Math.max(1, (int) Math.ceil((hi - lo) * r / 10));
        double lastX = r * Math.cos(lo);
        double lastY = r * Math.sin(lo);
        for (int i = 1; i <= steps; i++) {
            double a = lo + (hi - lo) * i / steps;
            double x = r * Math.cos(a);
            double y = r * Math.sin(a);
            line(gui, lastX, lastY, x, y, thick, color);
            lastX = x;
            lastY = y;
        }
        double a = Math.max(lo, Math.min(hi, angleOf(edge.child())));
        line(gui, r * Math.cos(a), r * Math.sin(a), edge.child().cx(), edge.child().cy(), thick, color);
    }

    /** A rectangle given by its centre and half sizes in tree units. */
    private static void box(GuiGraphicsExtractor gui, double cx, double cy, double hw, double hh, int color) {
        gui.fill((int) Math.round(cx - hw), (int) Math.round(cy - hh), (int) Math.round(cx + hw), (int) Math.round(cy + hh), color);
    }

    /** A node seen from far away: a small star, bright when learned, twinkling when it can be learned. */
    private void drawStar(GuiGraphicsExtractor gui, GridNode g, double px, boolean big, boolean own, int lane,
                          boolean faded, boolean aside, long pulse) {
        CoreStats.NodeLine n = g.node();
        double cx = g.cx();
        double cy = g.cy();
        boolean maxed = n.level >= n.maxLevel;
        double arm = (big ? 3.5 : 2.5) * px;
        int core;
        int glow = 0;
        if (maxed) {
            core = 0xFFFFE08A;
            glow = 0x55E8B84A;
        } else if (n.level > 0) {
            core = 0xFF000000 | lane;
            glow = 0x48000000 | lane;
        } else if (n.blocked == null && own) {
            core = ((0xA0 + (int) (Math.abs(25 - pulse) * 3)) << 24) | 0xF4F0FA;
            glow = 0x30FFFFFF;
        } else if (isClosed(n)) {
            core = 0xFF4A2A30;
            arm = 1.5 * px;
        } else {
            core = 0xFF000000 | ((lane >> 1) & 0x7F7F7F);
        }
        if (faded || aside) {
            core = (core & 0x00FFFFFF) | (faded ? 0x50000000 : 0x78000000);
            glow = 0;
        }
        if (glow != 0) {
            box(gui, cx, cy, arm + 1.5 * px, arm + 1.5 * px, glow);
            box(gui, cx, cy, arm, arm, glow);
        }
        box(gui, cx, cy, arm + 0.5 * px, 0.5 * px, core);
        box(gui, cx, cy, 0.5 * px, arm + 0.5 * px, core);
        box(gui, cx, cy, 1.0 * px, 1.0 * px, core);
        if (n.level > 0 && !faded) {
            box(gui, cx, cy, 0.5 * px, 0.5 * px, 0xFFFFFFFF);
        }
        if (n.id.equals(selectedId)) {
            double s = arm + 3 * px;
            int c = 0xFFFFD86B;
            box(gui, cx, cy - s, s, 0.5 * px, c);
            box(gui, cx, cy + s, s, 0.5 * px, c);
            box(gui, cx - s, cy, 0.5 * px, s, c);
            box(gui, cx + s, cy, 0.5 * px, s, c);
        }
    }

    /** The class's own night scene behind the tree; it drifts a little with the view. */
    private void drawScene(GuiGraphicsExtractor gui, int vx, int vy, int vw, int vh) {
        Identifier scene = Identifier.fromNamespaceAndPath("nightsiege",
                "textures/gui/skills/" + activeTab.key + ".png");
        int texW = 480;
        int texH = 270;
        int regionW = texW;
        int regionH = (int) Math.round(texW * vh / (double) vw);
        if (regionH > texH) {
            regionH = texH;
            regionW = (int) Math.round(texH * vw / (double) vh);
        }
        double driftX = (camX - (minX + maxX) / 2.0) / Math.max(1.0, maxX - minX) * (texW - regionW);
        double driftY = (camY - (minY + maxY) / 2.0) / Math.max(1.0, maxY - minY) * (texH - regionH);
        float u = (float) Math.max(0, Math.min(texW - regionW, (texW - regionW) / 2.0 + driftX * 0.5));
        float v = (float) Math.max(0, Math.min(texH - regionH, (texH - regionH) / 2.0 + driftY * 0.5));
        gui.blit(RenderPipelines.GUI_TEXTURED, scene, vx, vy, u, v, vw, vh, regionW, regionH, texW, texH);
        gui.fill(vx, vy, vx + vw, vy + vh, 0x30000000);   // a touch darker, so the stars stand out
        // A soft dark edge so the tree stands out.
        for (int i = 0; i < 10; i++) {
            int a = (10 - i) * 6;
            gui.fill(vx, vy + i, vx + vw, vy + i + 1, a << 24);
            gui.fill(vx, vy + vh - 1 - i, vx + vw, vy + vh - i, a << 24);
            gui.fill(vx + i, vy, vx + i + 1, vy + vh, a << 24);
            gui.fill(vx + vw - 1 - i, vy, vx + vw - i, vy + vh, a << 24);
        }
    }

    private GridNode drawGrid(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int rgb = activeTab.color & 0xFFFFFF;
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();
        boolean own = activeTab == ownTab();
        String taken = own && stats.pathTaken != null ? stats.pathTaken : "";

        // Header line above the tree.
        String head;
        if (own) {
            head = Component.translatable(activeTab.clazz.translationKey()).getString();
            if (!taken.isBlank()) {
                head += " · " + Component.translatable("nightsiege.path." + taken).getString();
                if (stats.subclass != null && !stats.subclass.isBlank()) {
                    head += " · " + Component.translatable("nightsiege.subclass." + stats.subclass).getString();
                }
            }
            head += "   " + Component.translatable("nightsiege.ui.points_short", stats.points).getString();
        } else if (!hasClass()) {
            head = Component.translatable("nightsiege.ui.preview_free").getString();
        } else {
            head = Component.translatable("nightsiege.ui.preview_other",
                    Component.translatable("nightsiege.class." + stats.myClass.toLowerCase(Locale.ROOT))).getString();
        }
        gui.text(font, font.plainSubstrByWidth(head, vw - 4), vx + 4, innerY() + 3, own ? GOLD : GRAY, true);

        gui.enableScissor(vx, vy, vx + vw, vy + vh);
        drawScene(gui, vx, vy, vw, vh);

        GridNode hovered = gridAt(mouseX, mouseY);
        String focusId = hovered != null ? hovered.node().id : selectedId;
        List<Edge> edges = gridEdges();
        java.util.Set<String> focus = focusSet(focusId, edges);
        boolean focusing = !focus.isEmpty();
        long pulse = System.currentTimeMillis() / 40 % 50;

        gui.pose().pushMatrix();
        gui.pose().translate((float) (vx + vw / 2.0 - camX * zoom), (float) (vy + vh / 2.0 - camY * zoom));
        gui.pose().scale((float) zoom, (float) zoom);
        double px = 1.0 / zoom;   // one screen pixel in tree units

        boolean stars = zoom < STAR_ZOOM;   // far out: the tree as a constellation

        // Rings: one faint dotted arc per ring and way.
        int dot = (int) Math.max(1, Math.round(px));
        for (int w = 0; w < ways.size(); w++) {
            boolean faded = !taken.isBlank() && !taken.equals(ways.get(w));
            for (int row = 2; row <= gridMaxRow; row++) {
                double r = ringRadius(row);
                double span = FAN + 0.07;
                int dots = Math.max(8, (int) (r * span * 2 / 12));
                int color = row == 8 ? 0x48FFD86B : faded ? 0x12FFFFFF : 0x24FFFFFF;
                for (int i = 0; i <= dots; i++) {
                    double a = -span + 2 * span * i / dots + (w == 0 ? Math.PI : 0);
                    int x = (int) Math.round(r * Math.cos(a));
                    int y = (int) Math.round(r * Math.sin(a));
                    gui.fill(x, y, x + dot, y + dot, color);
                }
            }
        }

        // Lines: dark outline first (only close up), then colour; the focused route in gold.
        // "One of these" between neighbours on a ring is drawn as a piece of that ring.
        double thin = stars ? px : Math.max(2.0, 1.4 * px);
        for (int pass = stars ? 1 : 0; pass < 2; pass++) {
            for (Edge edge : edges) {
                CoreStats.NodeLine child = edge.child().node();
                boolean closed = isClosed(child);
                boolean faded = !taken.isBlank() && child.path != null && !child.path.isBlank()
                        && !taken.equals(child.path);
                if (isBus(edge)) {
                    boolean met = false;
                    boolean onRoute = false;
                    for (int i = 0; i < edge.parents().size(); i++) {
                        met |= satisfied(edge, i);
                        onRoute |= focusing && focus.contains(child.id) && focus.contains(edge.parents().get(i).node().id);
                    }
                    int color = edgeColor(child, met, closed, rgb, (focusing && !onRoute) || faded, stars);
                    if (pass == 0) {
                        if (!faded) {
                            drawBus(gui, edge, thin + 2 * px, 0x90000000);
                        }
                    } else {
                        drawBus(gui, edge, onRoute ? thin + px : thin, onRoute ? 0xFFFFD86B : color);
                    }
                    continue;
                }
                for (int i = 0; i < edge.parents().size(); i++) {
                    GridNode p = edge.parents().get(i);
                    boolean onRoute = focusing && focus.contains(child.id) && focus.contains(p.node().id);
                    int color = edgeColor(child, satisfied(edge, i), closed, rgb, (focusing && !onRoute) || faded, stars);
                    if (pass == 0) {
                        if (!faded) {
                            line(gui, p.cx(), p.cy(), edge.child().cx(), edge.child().cy(), thin + 2 * px, 0x90000000);
                        }
                    } else if (onRoute) {
                        line(gui, p.cx(), p.cy(), edge.child().cx(), edge.child().cy(), thin + px, 0xFFFFD86B);
                    } else {
                        line(gui, p.cx(), p.cy(), edge.child().cx(), edge.child().cy(), thin, color);
                    }
                }
            }
        }

        // Nodes.
        boolean icons = zoom >= 0.42;
        int h = N_SLOT / 2;
        for (GridNode g : gridNodes) {
            CoreStats.NodeLine n = g.node();
            int x = (int) Math.round(g.cx()) - h;
            int y = (int) Math.round(g.cy()) - h;
            boolean maxed = n.level >= n.maxLevel;
            boolean closed = isClosed(n);
            boolean big = n.row == 8 || n.row == gridMaxRow || (n.lane != null && !n.lane.isBlank()
                    && n.id.equals(laneRoot(n.lane)));
            boolean faded = !taken.isBlank() && n.path != null && !n.path.isBlank() && !taken.equals(n.path);
            int lane = laneColor(n, rgb);
            if (stars) {
                drawStar(gui, g, px, big, own, lane, faded, focusing && !focus.contains(n.id), pulse);
                continue;
            }
            // A soft glow in the subclass colour behind lane nodes.
            if (n.lane != null && !n.lane.isBlank() && !faded) {
                gui.fill(x - 4, y - 4, x + N_SLOT + 4, y + N_SLOT + 4, 0x28000000 | lane);
                gui.fill(x - 2, y - 2, x + N_SLOT + 2, y + N_SLOT + 2, 0x30000000 | lane);
            }
            int border;
            if (maxed) {
                border = 0xFFE8B84A;
            } else if (n.level > 0) {
                border = 0xFF000000 | lane;
            } else if (n.blocked == null && own) {
                int a = 0x90 + (int) (Math.abs(25 - pulse) * 4);
                border = (a << 24) | 0xF4F0FA;
            } else if (closed) {
                border = 0xFF3A2C30;
            } else {
                border = 0xFF000000 | ((lane >> 1) & 0x7F7F7F);
            }
            if (big) {
                gui.fill(x - 3, y - 3, x + N_SLOT + 3, y + N_SLOT + 3, 0xFF000000);
                gui.fill(x - 2, y - 2, x + N_SLOT + 2, y + N_SLOT + 2, maxed || n.level > 0 ? 0xFFE8B84A : 0xFF7A6430);
            }
            gui.fill(x, y, x + N_SLOT, y + N_SLOT, 0xFF000000);
            gui.fill(x + 1, y + 1, x + N_SLOT - 1, y + N_SLOT - 1, border);
            gui.fill(x + 3, y + 3, x + N_SLOT - 3, y + N_SLOT - 3, n.level > 0 ? 0xFF2C2638 : 0xFF15121C);
            if (icons) {
                gui.item(iconOf(n.icon), x + 4, y + 4);
            } else {
                gui.fill(x + 6, y + 6, x + N_SLOT - 6, y + N_SLOT - 6, 0xFF000000 | (n.level > 0 ? lane : (lane >> 1) & 0x7F7F7F));
            }
            if (closed) {
                gui.fill(x + 3, y + 3, x + N_SLOT - 3, y + N_SLOT - 3, 0xA0101010);
                for (int i = 4; i < N_SLOT - 4; i++) {
                    gui.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFE04040);
                    gui.fill(x + N_SLOT - 1 - i, y + i, x + N_SLOT - i, y + i + 1, 0xFFE04040);
                }
            } else if (n.level == 0 && n.blocked != null && !"points".equals(n.blocked)) {
                gui.fill(x + 3, y + 3, x + N_SLOT - 3, y + N_SLOT - 3, 0x70000000);
            }
            if (faded || (focusing && !focus.contains(n.id))) {
                gui.fill(x - 4, y - 4, x + N_SLOT + 4, y + N_SLOT + 4, faded ? 0xA0000000 : 0x80000000);
            }
            if (n.id.equals(selectedId)) {
                int c = 0xFFFFD86B;
                gui.fill(x - 2, y - 2, x + N_SLOT + 2, y - 1, c);
                gui.fill(x - 2, y + N_SLOT + 1, x + N_SLOT + 2, y + N_SLOT + 2, c);
                gui.fill(x - 2, y - 2, x - 1, y + N_SLOT + 2, c);
                gui.fill(x + N_SLOT + 1, y - 2, x + N_SLOT + 2, y + N_SLOT + 2, c);
            }
            // Level pips under the slot.
            if (zoom >= 0.55) {
                int pips = Math.max(1, n.maxLevel);
                int pw = 4;
                int startX = x + N_SLOT / 2 - (pips * (pw + 1) - 1) / 2;
                for (int i = 0; i < pips; i++) {
                    int sx = startX + i * (pw + 1);
                    gui.fill(sx, y + N_SLOT + 2, sx + pw, y + N_SLOT + 5, 0xFF000000);
                    int fill = i < n.level ? (maxed ? 0xFFE8B84A : 0xFF000000 | lane) : 0xFF3A3644;
                    gui.fill(sx, y + N_SLOT + 2, sx + pw, y + N_SLOT + 4, fill);
                }
            }
        }
        gui.pose().popMatrix();

        // Labels in screen size, so they stay readable at any zoom.
        double midR = (ringRadius(2) + ringRadius(gridMaxRow)) / 2.0;
        for (int w = 0; w < ways.size(); w++) {
            String way = ways.get(w);
            boolean faded = !taken.isBlank() && !taken.equals(way);
            // The way's name in its top corner of the view: left way left, right way right.
            String name = Component.translatable("nightsiege.path." + way).getString().toUpperCase(Locale.ROOT);
            int color = faded ? 0xFF6A6474 : 0xFF000000 | rgb;
            int nx = w == 0 ? vx + 6 : vx + vw - 6 - font.width(name);
            gui.fill(nx - 3, vy + 3, nx + font.width(name) + 3, vy + 15, 0x70000000);
            gui.text(font, name, nx, vy + 5, color, true);
            if (way.equals(taken)) {
                String mine = Component.translatable("nightsiege.ui.your_way").getString();
                gui.text(font, mine, w == 0 ? vx + 6 : vx + vw - 6 - font.width(mine), vy + 17, 0xFFFFD86B, true);
            }
            // The subclasses just outside the fan: the upper one above it, the lower one below it.
            String[] lanes = LANES.get(way);
            if (lanes == null) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                double a = angleOf(w, i == 0 ? 0 : 6) + (i == 0 ? -1 : 1) * (w == 0 ? -1 : 1) * Math.toRadians(11);
                int cx = (int) screenX(midR * Math.cos(a));
                int cy = (int) screenY(midR * Math.sin(a)) - 4;
                String label = Component.translatable("nightsiege.subclass." + lanes[i]).getString();
                int lw = font.width(label);
                int axis = (int) screenX(0);
                int lx = w == 0 ? Math.min(cx - lw / 2, axis - 6 - lw) : Math.max(cx - lw / 2, axis + 6);
                if (cy < vy + 2 || cy > vy + vh - 10 || lx + lw < vx || lx > vx + vw) {
                    continue;
                }
                int lc = faded ? 0xFF6A6474 : 0xFF000000 | LANE_COLORS.getOrDefault(lanes[i], rgb);
                gui.text(font, label, lx, cy, lc, true);
            }
        }
        gui.disableScissor();

        drawInfoPanel(gui, font, hovered);
        return hovered;
    }

    /** The subclass node of a lane (where its name is written). */
    private String laneRoot(String lane) {
        for (GridNode g : gridNodes) {
            if (lane.equals(g.node().lane) && g.node().row == 3) {
                return g.node().id;
            }
        }
        return "";
    }

    /** "Ring III · Talent": which ring of its way a node sits on (the trunk has no ring). */
    private static String ringName(int row) {
        String kind = Component.translatable("nightsiege.ui.tier." + row).getString();
        return row < 2 ? kind : Component.translatable("nightsiege.ui.ring", roman(row - 1), kind).getString();
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
        lines.add("§8" + ringName(n.row));
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
        if (!gridMode || confirming != null || confirmingReset || !inView(event.x(), event.y())) {
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
        if (selectedId != null) {
            selectedId = null;   // a click into the empty sky clears the panel
            rebuildWidgets();
        }
        return true;
    }

    /** The mouse wheel zooms in and out, around the point under the mouse. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (gridMode && inView(x, y) && confirming == null && !confirmingReset) {
            double wx = worldX(x);
            double wy = worldY(y);
            zoom *= Math.pow(1.2, scrollY);
            clampCam();
            camX = wx - (x - viewX() - viewW() / 2.0) / zoom;
            camY = wy - (y - viewY() - viewH() / 2.0) / zoom;
            clampCam();
            saveView(shownClass);
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
        } else if (confirmingReset) {
            gui.fill(0, 0, this.width, this.height, 0x90000000);
            drawResetConfirm(gui, font);
        }

        super.extractRenderState(gui, mouseX, mouseY, delta);

        if (confirming == null && !confirmingReset) {
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

    private void drawResetConfirm(GuiGraphicsExtractor gui, Font font) {
        int w = 260;
        int h = 84;
        int x = left + (W - w) / 2;
        int y = top + (H - h) / 2 - 8;
        gui.fill(x, y, x + w, y + h, 0xFF000000);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_FILL);
        gui.fill(x + 1, y + 1, x + w - 1, y + 3, FRAME_LIGHT);
        gui.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, FRAME_SHADOW);
        centered(gui, font, Component.translatable("nightsiege.ui.class_reset_title").getString(), x + w / 2, y + 10, TITLE);
        centered(gui, font, Component.translatable("nightsiege.ui.class_reset_warn1", stats.earned - stats.donated).getString(),
                x + w / 2, y + 26, TITLE);
        centered(gui, font, Component.translatable("nightsiege.ui.class_reset_warn2").getString(), x + w / 2, y + 38, 0xFFAA0000);
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
