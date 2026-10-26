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
        // The class overview (Simon, 26 October 2026): replaces the five class tabs.
        CLASSES(null, "minecraft:armor_stand", "minecraft:textures/block/dark_oak_planks.png", 0xFFFFD86B, "classes"),
        ARCHER(SkillClass.ARCHER, "minecraft:bow", "minecraft:textures/block/spruce_planks.png", 0xFF8BD45C, "archer"),
        WARRIOR(SkillClass.WARRIOR, "minecraft:iron_sword", "minecraft:textures/block/stone_bricks.png", 0xFFFF6B4A, "warrior"),
        GUARDIAN(SkillClass.GUARDIAN, "minecraft:shield", "minecraft:textures/block/polished_andesite.png", 0xFF6FA8FF, "guardian"),
        ENGINEER(SkillClass.ENGINEER, "nightsiege:arrow_turret", "minecraft:textures/block/cut_copper.png", 0xFFF0A040, "engineer"),
        SUMMONER(SkillClass.SUMMONER, "nightsiege:summoning_staff", "minecraft:textures/block/mossy_stone_bricks.png", 0xFF5FD8B8, "summoner"),
        CORE(SkillClass.CORE, "nightsiege:siege_core", "minecraft:textures/block/obsidian.png", 0xFFC88CFF, "core"),
        COLLECTION(null, "nightsiege:reward_crate", "minecraft:textures/block/polished_blackstone_bricks.png", 0xFFB565FF, "collection"),
        PERKS(null, "nightsiege:night_fragment_blood", "minecraft:textures/block/polished_blackstone.png", 0xFFE0A0FF, "perks");

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
        GuideScreen.summonerVisible = stats != null && stats.summoner;
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
        GuideScreen.summonerVisible = fresh != null && fresh.summoner;
        if (confirming != null && fresh.unlocked != null && fresh.unlocked.contains(confirming.name())) {
            confirming = null;
        }
        rebuildWidgets();
    }

    private boolean hasClass() {
        return stats.myClass != null && !stats.myClass.isBlank();
    }

    /** True when this tab's tree is the player's to learn: their class, or the shared core. */
    private boolean ownsTree() {
        return activeTab == Tab.CORE || (activeTab != null && activeTab == ownTab());
    }

    /** The tabs this viewer gets: the summoner only for admins (or everyone, by config). */
    private Tab[] visibleTabs() {
        List<Tab> out = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            // The class trees have no tab of their own any more: the overview leads to them.
            if (tab.clazz == null || tab.clazz.isShared()) {
                out.add(tab);
            }
        }
        return out.toArray(new Tab[0]);
    }

    /** A class's own tree (reached from the overview). */
    private static boolean isClassTree(Tab tab) {
        return tab != null && tab.clazz != null && !tab.clazz.isShared();
    }

    private static Tab tabOf(SkillClass clazz) {
        for (Tab tab : Tab.values()) {
            if (tab.clazz == clazz) {
                return tab;
            }
        }
        return Tab.CLASSES;
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
        if (activeTab == Tab.SUMMONER && !stats.summoner) {
            activeTab = null;
        }
        if (activeTab == null) {
            activeTab = Tab.CLASSES;
        }
        boolean big = activeTab == Tab.CLASSES
                || (activeTab.clazz != null && stats.skillsEnabled && isGrid(activeTab.clazz.name()));
        W = big ? Math.max(404, Math.min(this.width - 16, 760)) : 404;
        H = big ? Math.min(this.height - 34, 440) : 244;
        // Small screens (high GUI scale): the window shrinks, so the tabs and buttons stay visible.
        H = Math.max(196, Math.min(H, this.height - TAB_H - 4));
        W = Math.min(W, this.width - 4);
        left = (this.width - W) / 2;
        top = (this.height - H - TAB_H + 4) / 2 + TAB_H - 4;
        nodeButtons.clear();
        pathRows.clear();
        gridMode = false;
        if (activeTab == null) {
            activeTab = Tab.CLASSES;
        }

        if ((confirmingReset || confirming != null) && activeTab.clazz != null && stats.skillsEnabled
                && isGrid(activeTab.clazz.name())) {
            // The map stays visible behind the question, but cannot be used.
            quiet = true;
            layoutNodes(activeTab.clazz.name(), false);
            quiet = false;
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
        Tab[] tabs = visibleTabs();
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

        if (activeTab == Tab.STATS) {
            // Answer today's invitation, or sign off.
            int bx = left + 8;
            if (stats.voteOpen) {
                Component yes = Component.translatable("nightsiege.ui.vote_yes");
                Component no = Component.translatable("nightsiege.ui.vote_no");
                int yw = Math.max(60, font.width(yes) + 12);
                int nw = Math.max(60, font.width(no) + 12);
                Button yb = Button.builder(yes, b -> send("vote:yes")).bounds(bx, footerY, yw, 18).build();
                yb.active = !stats.myVote.equals("yes");
                addRenderableWidget(yb);
                Button nb = Button.builder(no, b -> send("vote:no")).bounds(bx + yw + 4, footerY, nw, 18).build();
                nb.active = !stats.myVote.equals("no");
                addRenderableWidget(nb);
                bx += yw + nw + 8;
            }
            if (!stats.siegeActive && !stats.nightComing) {
                Component leave = Component.translatable("nightsiege.ui.unregister");
                int lw = Math.max(70, font.width(leave) + 12);
                addRenderableWidget(Button.builder(leave, b -> {
                    send("roster:leave");
                    onClose();
                }).bounds(left + W - 70 - lw, footerY, lw, 18).build());
            }
        }

        if (activeTab == Tab.COLLECTION) {
            // One small button per class: whose kit the collection shows.
            int bx = innerX() + 4;
            for (SkillClass c : SkillClass.values()) {
                if (c.isShared() || (c.adminOnly() && !stats.summoner)) {
                    continue;
                }
                Component label = Component.translatable(c.translationKey());
                int bw = font.width(label) + 12;
                Button b = Button.builder(label, btn -> {
                    collectionClass = c.name();
                    rebuildWidgets();
                }).bounds(bx, innerY() + 3, bw, 16).build();
                b.active = !c.name().equals(collectionClass());
                addRenderableWidget(b);
                bx += bw + 3;
            }
            return;
        }
        if (activeTab == Tab.CLASSES) {
            layoutClasses();
            return;
        }
        if (activeTab.clazz == null || !stats.skillsEnabled) {
            return;
        }
        boolean own = activeTab == Tab.CORE || activeTab == ownTab();
        layoutNodes(activeTab.clazz.name(), own);

        if (activeTab == Tab.CORE) {
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.donate"),
                    b -> send("donate:5")).bounds(left + W - 130, footerY, 64, 18).build());
        }
        // Choosing and playing a class happen in the overview (tab Klassen) now.
        int footerX = left + 8;
        if (isClassTree(activeTab)) {
            // Back to the class overview.
            Component backLabel = Component.translatable("nightsiege.ui.back_classes");
            int kw = Math.max(60, font.width(backLabel) + 12);
            addRenderableWidget(Button.builder(backLabel, b -> {
                activeTab = Tab.CLASSES;
                rebuildWidgets();
            }).bounds(footerX, footerY, kw, 18).build());
            footerX += kw + 4;
        }
        if (stats.creative && activeTab != Tab.CORE) {
            Component grant = Component.translatable("nightsiege.ui.dev_grant");
            Component wipe = Component.translatable("nightsiege.ui.dev_reset");
            int gw = Math.max(56, font.width(grant) + 12);
            int rw = Math.max(56, font.width(wipe) + 12);
            addRenderableWidget(Button.builder(grant, b -> send("dev:50")).bounds(footerX, footerY, gw, 18).build());
            addRenderableWidget(Button.builder(wipe, b -> send("dev:reset")).bounds(footerX + gw + 4, footerY, rw, 18).build());

            footerX += gw + rw + 8;
            if (activeTab == Tab.COLLECTION) {
                // Creative: start the collection of the shown class over (crate gains for testing).
                Component gains = Component.translatable("nightsiege.ui.dev_gains");
                int kw = Math.max(70, font.width(gains) + 12);
                String clazz = collectionClass();
                addRenderableWidget(Button.builder(gains, b -> send("dev:gains:" + clazz)).bounds(footerX, footerY, kw, 18).build());
                footerX += kw + 4;
                Component take = Component.translatable("nightsiege.ui.dev_crate");
                int cw = Math.max(60, font.width(take) + 12);
                addRenderableWidget(Button.builder(take, b -> send("dev:crate:" + clazz)).bounds(footerX, footerY, cw, 18).build());
                footerX += cw + 4;
            }
        }
        if (stats.classResetAvailable && activeTab == ownTab()) {
            Component label = Component.translatable(stats.classResetUnlimited ? "nightsiege.ui.class_reset_test"
                    : "nightsiege.ui.class_reset");
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
    private static final double RING0 = 104;
    private static final double RING = 46;
    /** Half the angle of a way's fan; its seven columns split it evenly. */
    private static final double FAN = Math.toRadians(40);
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
            "chem", new String[]{"demolition", "alchemist"},
            "wild", new String[]{"tamer", "golemsmith"},
            "dead", new String[]{"necromancer", "soulcatcher"});
    private static final java.util.Map<String, Integer> LANE_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("blademaster", 0x6FA0FF), java.util.Map.entry("berserker", 0xE04848),
            java.util.Map.entry("axe", 0xD8913A), java.util.Map.entry("paladin", 0xF2D46A),
            java.util.Map.entry("swift", 0x5FD0E8), java.util.Map.entry("heavy", 0xC88A3A),
            java.util.Map.entry("bolter", 0xE87A30), java.util.Map.entry("trapper", 0x6CC46C),
            java.util.Map.entry("shieldfighter", 0x8FB4FF), java.util.Map.entry("bulwark", 0xB0B8C8),
            java.util.Map.entry("medic", 0x7FE08A), java.util.Map.entry("warden", 0xC59BFF),
            java.util.Map.entry("gunner", 0xE0B050), java.util.Map.entry("electrician", 0x6FD8FF),
            java.util.Map.entry("demolition", 0xF07040), java.util.Map.entry("alchemist", 0x8BD35A),
            java.util.Map.entry("tamer", 0xD9A066), java.util.Map.entry("golemsmith", 0xC8D0D8),
            java.util.Map.entry("necromancer", 0x9A7AE0), java.util.Map.entry("soulcatcher", 0x6FE0D0));

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
            return new double[]{(col - 3) * 24.0, trunkNodes == 1 ? 0.0 : (n.row - 0.5) * 42.0};
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
    /** Nodes on the trunk of the shown tree (the core has a single one, drawn in the middle). */
    private int trunkNodes;
    /** Set while the map is laid out only to be drawn behind a question. */
    private boolean quiet;

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
        trunkNodes = 0;
        for (CoreStats.NodeLine node : stats.nodes) {
            if (clazz.equals(node.clazz) && (node.path == null || node.path.isBlank())) {
                trunkNodes++;
            }
        }
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
        if (quiet) {
            return;   // only drawn behind a question: no buttons
        }
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

    /** The focused node, what it needs directly and what it opens directly. */
    private java.util.Set<String> focusSet(String id, List<Edge> edges) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (id == null || gridOf(id) == null) {
            return set;
        }
        set.add(id);
        for (Edge edge : edges) {
            if (edge.child().node().id.equals(id)) {
                for (GridNode parent : edge.parents()) {
                    set.add(parent.node().id);
                }
            }
            for (GridNode parent : edge.parents()) {
                if (parent.node().id.equals(id) && !isClosed(edge.child().node())) {
                    set.add(edge.child().node().id);
                }
            }
        }
        return set;
    }

    /**
     * Every node that learning this one would shut for good: the other nodes of
     * its choice, the nodes it locks (or that lock it), the other way when it is
     * the first step on a way, and everything that can then only be reached
     * through one of those.
     */
    private java.util.Set<String> wouldClose(CoreStats.NodeLine h, String taken) {
        java.util.Set<String> closed = new java.util.HashSet<>();
        if (h == null || h.level > 0 || isClosed(h)) {
            return closed;
        }
        java.util.Map<String, CoreStats.NodeLine> same = new java.util.HashMap<>();
        for (CoreStats.NodeLine n : stats.nodes) {
            if (h.clazz.equals(n.clazz)) {
                same.put(n.id, n);
            }
        }
        boolean picksWay = h.path != null && !h.path.isBlank() && (taken == null || taken.isBlank())
                && !"CORE".equals(h.clazz);
        for (CoreStats.NodeLine n : same.values()) {
            if (n == h || n.level > 0 || isClosed(n)) {
                continue;
            }
            boolean shut = h.group != null && !h.group.isBlank() && h.group.equals(n.group);
            shut |= h.locks != null && h.locks.contains(n.id);
            shut |= n.locks != null && n.locks.contains(h.id);
            shut |= picksWay && n.path != null && !n.path.isBlank() && !n.path.equals(h.path);
            if (shut) {
                closed.add(n.id);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (CoreStats.NodeLine n : same.values()) {
                if (n == h || n.level > 0 || closed.contains(n.id) || isClosed(n)) {
                    continue;
                }
                for (List<String[]> any : needsOf(n)) {
                    boolean open = false;
                    boolean byThis = false;
                    for (String[] need : any) {
                        CoreStats.NodeLine other = same.get(need[0]);
                        int wanted = parseLevel(need[1]);
                        if (other != null && other.level >= wanted) {
                            open = true;
                        } else if (closed.contains(need[0])) {
                            byThis = true;
                        } else if (other != null && !isClosed(other)) {
                            open = true;
                        }
                    }
                    if (!open && byThis) {
                        closed.add(n.id);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return closed;
    }

    private static int parseLevel(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** A red cross in tree units over a node that the focused node would shut. */
    private void doomCross(GuiGraphicsExtractor gui, double cx, double cy, double half, double thick) {
        line(gui, cx - half, cy - half, cx + half, cy + half, thick + 2, 0xC0000000);
        line(gui, cx - half, cy + half, cx + half, cy - half, thick + 2, 0xC0000000);
        line(gui, cx - half, cy - half, cx + half, cy + half, thick, 0xFFFF3B30);
        line(gui, cx - half, cy + half, cx + half, cy - half, thick, 0xFFFF3B30);
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

    /** Half one colour, half the other. */
    private static int mix(int a, int b) {
        return (((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2) << 16
                | (((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2) << 8
                | ((a & 0xFF) + (b & 0xFF)) / 2;
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
            core = 0xFF000000 | mix(lane, 0x9C96AA);   // not reachable yet: a quiet grey-tinted star
        }
        if (faded || aside) {
            core = (core & 0x00FFFFFF) | (faded ? 0x90000000 : 0x88000000);
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
        boolean core = activeTab == Tab.CORE;
        boolean own = ownsTree();
        String taken = own && !core && stats.pathTaken != null ? stats.pathTaken : "";

        // Header line above the tree.
        String head;
        if (core) {
            head = Component.translatable("nightsiege.ui.core_pot", stats.corePoints, stats.donated).getString();
        } else if (own) {
            head = Component.translatable(activeTab.clazz.translationKey()).getString();
            if (!taken.isBlank()) {
                head += " · " + Component.translatable("nightsiege.path." + taken).getString();
                if (stats.subclass != null && !stats.subclass.isBlank()) {
                    head += " · " + Component.translatable("nightsiege.subclass." + stats.subclass).getString();
                }
            }
            head += "   " + Component.translatable("nightsiege.ui.points_short", stats.points).getString();
            if (stats.playClass != null && !stats.playClass.isBlank() && !stats.playClass.equals(stats.myClass)) {
                head += "   " + Component.translatable("nightsiege.ui.playing",
                        Component.translatable("nightsiege.class." + stats.playClass.toLowerCase(Locale.ROOT))).getString();
            }
        } else if (stats.completeClasses.contains(activeTab.clazz.name())) {
            head = Component.translatable(activeTab.clazz.translationKey()).getString() + " · "
                    + Component.translatable("nightsiege.ui.class_done").getString()
                    + (activeTab.clazz.name().equals(stats.playClass)
                    ? "   " + Component.translatable("nightsiege.ui.playing_now").getString() : "");
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
        GridNode doomer = focusId == null ? null : gridOf(focusId);
        java.util.Set<String> doomed = wouldClose(doomer == null ? null : doomer.node(), taken);
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

        // Lines only for the node under the mouse (or the chosen one): what it needs and what it opens.
        double thin = stars ? Math.max(1.0, 1.2 * px) : Math.max(2.0, 1.4 * px);
        GridNode focusNode = focusId == null ? null : gridOf(focusId);
        if (focusNode != null) {
            for (int pass = 0; pass < 2; pass++) {
                for (Edge edge : edges) {
                    boolean in = edge.child() == focusNode;
                    boolean termMet = false;   // "one of these": one is enough, the others turn grey
                    for (int i = 0; i < edge.parents().size(); i++) {
                        termMet |= satisfied(edge, i);
                    }
                    for (int i = 0; i < edge.parents().size(); i++) {
                        GridNode p = edge.parents().get(i);
                        boolean out = p == focusNode && !isClosed(edge.child().node());
                        if (!in && !out) {
                            continue;
                        }
                        int color = !in ? 0xFF7FC8FF : satisfied(edge, i) ? 0xFF6FE07A : termMet ? 0x90A8A0B4 : 0xFFFF6A5A;
                        if (pass == 0) {
                            line(gui, p.cx(), p.cy(), edge.child().cx(), edge.child().cy(), thin + 2 * px, 0xB0000000);
                        } else {
                            line(gui, p.cx(), p.cy(), edge.child().cx(), edge.child().cy(), thin, color);
                        }
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
                if (doomed.contains(n.id)) {
                    doomCross(gui, g.cx(), g.cy(), 5 * px, Math.max(1.5, 1.6 * px));
                }
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
            if (doomed.contains(n.id)) {
                // What the node under the mouse would shut: a bright red cross on top of everything.
                doomCross(gui, g.cx(), g.cy(), N_SLOT / 2.0 - 2, Math.max(2.0, 2 * px));
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
            if (way.equals(taken)) {
                name = "★ " + name;   // the player's own way
            }
            int color = faded ? 0xFF6A6474 : way.equals(taken) ? 0xFFFFD86B : 0xFF000000 | rgb;
            int nx = w == 0 ? vx + 6 : vx + vw - 6 - font.width(name);
            gui.fill(nx - 3, vy + 3, nx + font.width(name) + 3, vy + 15, 0x70000000);
            gui.text(font, name, nx, vy + 5, color, true);
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
        if (core && stats.baseKnown) {
            // How the base around the core stands: closed, or where the wall is open.
            String base;
            int color;
            if (stats.baseClosed) {
                base = Component.translatable("nightsiege.ui.base_closed", stats.baseWidth, stats.baseDepth).getString();
                color = GREEN;
            } else if (stats.baseGap != null && !stats.baseGap.isBlank()) {
                base = Component.translatable("nightsiege.ui.base_gap", stats.baseGap).getString();
                color = RED;
            } else {
                base = Component.translatable("nightsiege.ui.base_open", stats.baseWidth, stats.baseDepth).getString();
                color = RED;
            }
            base = font.plainSubstrByWidth(base, vw - 40);
            gui.fill(vx + 3, vy + vh - 16, vx + 9 + font.width(base), vy + vh - 3, 0x90000000);
            gui.text(font, base, vx + 6, vy + vh - 14, color, true);
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

    /** The core's rings: the foundation in the middle, then the extensions of each way. */
    private static String coreRingName(int row) {
        if (row < 2) {
            return Component.translatable("nightsiege.ui.core_tier.base").getString();
        }
        return Component.translatable("nightsiege.ui.ring", roman(row - 1),
                Component.translatable("nightsiege.ui.core_tier.ring")).getString();
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
        int tw = pw - 16;
        GridNode g = hovered != null ? hovered : selectedId == null ? null : gridOf(selectedId);
        // Long texts scroll with the mouse wheel; a new node starts at the top again.
        String shown = g == null ? "" : g.node().id;
        if (!shown.equals(panelFor)) {
            panelFor = shown;
            panelScroll = 0;
        }
        int top0 = py + 6;
        int bottom = py + ph - (g != null && ownsTree() ? 32 : 4);
        panelTop = py + 1;
        panelBottom = bottom;
        int y = top0 - panelScroll;
        gui.enableScissor(px + 1, py + 1, px + pw, bottom);
        y = panelText(gui, font, g, rgb, tx, tw, y);
        gui.disableScissor();
        int content = y + panelScroll - top0;
        int visible = bottom - top0;
        panelMax = Math.max(0, content - visible);
        panelScroll = Math.max(0, Math.min(panelMax, panelScroll));
        if (panelMax > 0) {
            // A thin scroll bar on the right edge.
            int barH = Math.max(12, visible * visible / content);
            int barY = top0 + (visible - barH) * panelScroll / panelMax;
            gui.fill(px + pw - 4, top0, px + pw - 2, bottom, 0x40FFFFFF);
            gui.fill(px + pw - 4, barY, px + pw - 2, barY + barH, 0xC0000000 | rgb);
        }
    }

    /** Draws the panel's text from y on and returns where it ended (it may run past the bottom). */
    private int panelText(GuiGraphicsExtractor gui, Font font, GridNode g, int rgb, int tx, int tw, int y) {
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
            int[][] legend = {{0xE8B84A, 0}, {rgb, 1}, {0xF4F0FA, 2}, {0x5A5466, 3}, {0xE04040, 4}};
            for (int[] entry : legend) {
                gui.fill(tx, y + 1, tx + 8, y + 8, 0xFF000000 | entry[0]);
                gui.text(font, Component.translatable("nightsiege.ui.legend." + entry[1]).getString(), tx + 12, y, 0xFFD0C8DC, false);
                y += 11;
            }
            return y;
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
        lines.add("§8" + (activeTab == Tab.CORE ? coreRingName(n.row) : ringName(n.row)));
        String sub = Component.translatable("nightsiege.subclass_node." + n.id).getString();
        if (!sub.equals("nightsiege.subclass_node." + n.id)) {
            lines.add("§6" + sub);
        }
        if (n.id.contains("_cross_")) {
            lines.add("§b" + Component.translatable("nightsiege.ui.cross").getString());
        }
        int shuts = wouldClose(n, activeTab == ownTab() && stats.pathTaken != null ? stats.pathTaken : "").size();
        if (shuts > 0) {
            lines.add("§c" + Component.translatable("nightsiege.ui.would_close", shuts).getString());
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
        if (!ownsTree()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.view_only").getString());
        } else if (n.blocked != null && n.level < n.maxLevel) {
            lines.add("§c" + Component.translatable("nightsiege.menu.blocked_" + n.blocked, n.cost).getString());
        }
        for (String raw : lines) {
            String color = raw.startsWith("§") ? raw.substring(0, 2) : "";
            String text = raw.startsWith("§") ? raw.substring(2) : raw;
            for (String part : wrap(font, text, tw)) {
                gui.text(font, color + part, tx, y, 0xFFFFFFFF, false);
                y += 10;
            }
            y += 1;
        }
        return y + 4;
    }

    /** How far the info panel is scrolled, and for which node. */
    private static int panelScroll;
    private static String panelFor = "";
    private int panelMax;
    private int panelTop;
    private int panelBottom;

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!gridMode || confirming != null || confirmingReset || !inView(event.x(), event.y())) {
            return false;
        }
        panning = true;
        panned = 0;
        GridNode hit = gridAt(event.x(), event.y());
        if (hit != null) {
            boolean again = hit.node().id.equals(selectedId);
            selectedId = hit.node().id;
            if (doubleClick && again && ownsTree() && hit.node().blocked == null) {
                send("buy:" + hit.node().id);
            }
            rebuildWidgets();
        }
        return true;
    }

    /** Set while a mouse button went down inside the map: the map follows the mouse. */
    private boolean panning;
    private double panned;

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (panning && gridMode) {
            camX -= dx / zoom;
            camY -= dy / zoom;
            clampCam();
            panned += Math.abs(dx) + Math.abs(dy);
            saveView(shownClass);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (panning) {
            panning = false;
            if (panned < 3 && selectedId != null && gridAt(event.x(), event.y()) == null) {
                selectedId = null;   // a click into the empty sky clears the panel
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    /** The mouse wheel zooms in and out, around the point under the mouse. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (gridMode && confirming == null && !confirmingReset && x >= panelX() && x < panelX() + PANEL_W
                && y >= panelTop && y < panelBottom + 32) {
            panelScroll = (int) Math.max(0, Math.min(panelMax, panelScroll - scrollY * 12));
            return true;
        }
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
        // The core tab keeps a line for the base under its header.
        int areaTop = innerY() + (activeTab == Tab.CORE ? 32 : 20);
        int areaH = innerH() - (activeTab == Tab.CORE ? 36 : 24);
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
        } else if (activeTab == Tab.CLASSES) {
            drawClasses(gui, font, mouseX, mouseY);
        } else if (activeTab == Tab.PERKS) {
            drawPerks(gui, font);
        } else if (activeTab == Tab.COLLECTION) {
            hoveredGain = drawCollection(gui, font, mouseX, mouseY);
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
            } else if (hoveredGain != null && activeTab == Tab.COLLECTION) {
                gainTooltip(gui, font, hoveredGain, mouseX, mouseY);
            }
        }
    }

    private Tab tabAt(int mouseX, int mouseY) {
        Tab[] tabs = visibleTabs();
        for (int i = 0; i < tabs.length; i++) {
            int x = tabX(i);
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= top - TAB_H + 2 && mouseY < top) {
                return tabs[i];
            }
        }
        return null;
    }

    private String tabName(Tab tab) {
        if (tab == Tab.CLASSES) {
            return Component.translatable("nightsiege.ui.banner_title.classes").getString();
        }
        if (tab == Tab.STATS) {
            return Component.translatable("nightsiege.ui.tab_stats").getString();
        }
        if (tab == Tab.COLLECTION) {
            return Component.translatable("nightsiege.ui.banner_title.collection").getString();
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
        Tab[] tabs = visibleTabs();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            boolean active = tab == activeTab || (tab == Tab.CLASSES && isClassTree(activeTab));
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
        if (tab == Tab.CLASSES) {
            return Component.translatable("nightsiege.ui.banner_title.classes").getString();
        }
        if (tab == Tab.STATS) {
            return Component.translatable("nightsiege.ui.banner_title.stats").getString();
        }
        if (tab == Tab.COLLECTION) {
            return Component.translatable("nightsiege.ui.banner_title.collection").getString();
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
        if (gridMode || activeTab == Tab.CLASSES) {
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
        if (activeTab == Tab.COLLECTION || activeTab == Tab.PERKS) {
            // Vault and altar: dark stone, a warm (gold) or violet glow from the edge.
            gui.enableScissor(x, y, x + w, y + h);
            for (int ty = y; ty < y + h; ty += 16) {
                for (int tx = x; tx < x + w; tx += 16) {
                    gui.blit(RenderPipelines.GUI_TEXTURED, activeTab.backdrop, tx, ty, 0f, 0f, 16, 16, 16, 16);
                }
            }
            gui.disableScissor();
            gui.fill(x, y, x + w, y + h, 0xA8000000);
            int rgb = activeTab == Tab.COLLECTION ? 0xE8B84A : 0x9A5CFF;
            for (int i = 0; i < 16; i++) {
                int a = Math.max(0, 0x20 - i * 2);
                if (activeTab == Tab.COLLECTION) {
                    gui.fill(x, y + i, x + w, y + i + 1, (a << 24) | rgb);
                } else {
                    gui.fill(x, y + h - 1 - i, x + w, y + h - i, (a << 24) | rgb);
                }
                gui.fill(x + i, y, x + i + 1, y + h, ((a / 2) << 24) | rgb);
                gui.fill(x + w - 1 - i, y, x + w - i, y + h, ((a / 2) << 24) | rgb);
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
        if (activeTab == Tab.CORE && stats.baseKnown) {
            String base;
            int color;
            if (stats.baseClosed) {
                base = Component.translatable("nightsiege.ui.base_closed", stats.baseWidth, stats.baseDepth).getString();
                color = GREEN;
            } else if (stats.baseGap != null && !stats.baseGap.isBlank()) {
                base = Component.translatable("nightsiege.ui.base_gap", stats.baseGap).getString();
                color = RED;
            } else {
                base = Component.translatable("nightsiege.ui.base_open", stats.baseWidth, stats.baseDepth).getString();
                color = RED;
            }
            gui.text(font, base, x, y + 12, color, true);
        }
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

    // ------------------------------------------------------------------
    // the class overview (tab Klassen)
    // ------------------------------------------------------------------

    /** The cards of the overview: x, y, width, height and the height of the stand box. */
    private record ClassCard(CoreStats.ClassLine line, SkillClass clazz, int x, int y, int w, int h, int standH) {
    }

    private final List<ClassCard> classCards = new ArrayList<>();

    private List<CoreStats.ClassLine> classLines() {
        List<CoreStats.ClassLine> out = new ArrayList<>();
        if (stats.classLines != null) {
            for (CoreStats.ClassLine line : stats.classLines) {
                if (SkillClass.byName(line.id) != null) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    /** True when this class may be chosen now: the first class, or the next one after an ultimate at level 3. */
    private boolean choosableNow(CoreStats.ClassLine line) {
        return !line.unlocked && !stats.siegeActive && (!hasClass() || stats.nextClassOpen);
    }

    private void layoutClasses() {
        classCards.clear();
        List<CoreStats.ClassLine> lines = classLines();
        if (lines.isEmpty()) {
            return;
        }
        int gap = 5;
        int n = lines.size();
        int x0 = innerX() + 4;
        int y0 = innerY() + 14;
        int cw = (innerW() - 8 - (n - 1) * gap) / n;
        int ch = innerY() + innerH() - 4 - y0;
        int buttons = 2 * 16 + 6;
        int textH = 34;
        int standH = Math.max(24, ch - buttons - textH - 16);
        for (int i = 0; i < n; i++) {
            CoreStats.ClassLine line = lines.get(i);
            SkillClass clazz = SkillClass.byName(line.id);
            int x = x0 + i * (cw + gap);
            ClassCard card = new ClassCard(line, clazz, x, y0, cw, ch, standH);
            classCards.add(card);
            int by = y0 + ch - 4 - 16;
            int bw = cw - 8;
            // Bottom: the tree. Above it: what can be done with the class now.
            addRenderableWidget(Button.builder(Component.translatable("nightsiege.ui.class_tree"), b -> {
                activeTab = tabOf(clazz);
                rebuildWidgets();
            }).bounds(x + 4, by, bw, 16).build());
            Button action = null;
            if (!line.unlocked && choosableNow(line)) {
                Component label = Component.translatable(hasClass() ? "nightsiege.ui.class_unlock" : "nightsiege.ui.class_pick");
                action = Button.builder(label, b -> openSubclassPick(clazz)).bounds(x + 4, by - 18, bw, 16).build();
            } else if (line.unlocked && (line.subclass == null || line.subclass.isBlank())) {
                action = Button.builder(Component.translatable("nightsiege.ui.class_subpick"), b ->
                        Minecraft.getInstance().setScreenAndShow(new SubclassPickScreen(clazz, SubclassPickScreen.Mode.FIX,
                                this, line.way))).bounds(x + 4, by - 18, bw, 16).build();
            } else if (line.unlocked && !line.played) {
                Button play = Button.builder(Component.translatable("nightsiege.ui.class_play"), b -> send("play:" + clazz.name()))
                        .bounds(x + 4, by - 18, bw, 16).build();
                play.active = !stats.siegeActive;
                action = play;
            }
            if (action != null) {
                addRenderableWidget(action);
            }
        }
    }

    /** Choosing a class (first or next): the subclass window decides way and subclass with it. */
    private void openSubclassPick(SkillClass clazz) {
        SubclassPickScreen.Mode mode = hasClass() ? SubclassPickScreen.Mode.NEXT : SubclassPickScreen.Mode.FIRST;
        Minecraft.getInstance().setScreenAndShow(new SubclassPickScreen(clazz, mode, this, ""));
    }

    private void drawClasses(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        // One line above the cards: where the points go.
        SkillClass leveling = SkillClass.byName(stats.myClass);
        String head = hasClass()
                ? Component.translatable("nightsiege.ui.classes_head", stats.points,
                        leveling == null ? Component.literal("?") : Component.translatable(leveling.translationKey())).getString()
                : Component.translatable("nightsiege.ui.classes_first").getString();
        if (hasClass() && stats.nextClassOpen) {
            head += "  " + Component.translatable("nightsiege.ui.classes_next").getString();
        }
        gui.text(font, font.plainSubstrByWidth(head, innerW() - 12), innerX() + 6, innerY() + 3,
                stats.nextClassOpen ? 0xFFFFD86B : 0xFFCFC7DD, true);
        for (ClassCard card : classCards) {
            drawClassCard(gui, font, card, mouseX, mouseY);
        }
    }

    private void drawClassCard(GuiGraphicsExtractor gui, Font font, ClassCard card, int mouseX, int mouseY) {
        CoreStats.ClassLine line = card.line();
        int x = card.x();
        int y = card.y();
        int w = card.w();
        int h = card.h();
        boolean open = line.unlocked || choosableNow(line);
        int classColor = CoreColors.classColor(card.clazz());
        gui.fill(x, y, x + w, y + h, line.played ? 0xFFFFD86B : 0xFF2C2740);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, line.unlocked ? 0xFF1A1622 : 0xFF131118);
        gui.fill(x + 1, y + 1, x + w - 1, y + 3, open ? classColor : 0xFF2C2740);

        // Name and status.
        String name = Component.translatable(card.clazz().translationKey()).getString();
        float ns = Math.min(1.0f, Math.max(0.6f, (w - 10f) / Math.max(1, font.width(name))));
        gui.pose().pushMatrix();
        gui.pose().translate(x + 5, y + 6);
        gui.pose().scale(ns, ns);
        gui.text(font, name, 0, 0, open ? 0xFFFFFFFF : 0xFF8F879F, true);
        gui.pose().popMatrix();

        // The armour stand of the class's subclass, a shadow while there is none.
        int sy = y + 17;
        int sh = card.standH();
        gui.fill(x + 4, sy, x + w - 4, sy + sh, 0xFF2C2740);
        gui.fill(x + 5, sy + 1, x + w - 5, sy + sh - 1, 0xFF0E0C14);
        boolean own = line.unlocked && ClassStands.has(line.subclass);
        ClassStands.draw(gui, own ? line.subclass : ClassStands.firstOf(card.clazz()), !own, x + 6, sy + 2, w - 12, sh - 4);
        String badgeKey;
        int badgeFg;
        int badgeBg;
        if (line.unlocked && line.complete) {
            badgeKey = "nightsiege.pick.done";
            badgeFg = 0xFFFFD86B;
            badgeBg = 0xFF3A3010;
        } else if (line.unlocked && line.ultDone) {
            badgeKey = "nightsiege.pick.ult3";
            badgeFg = 0xFFFFD86B;
            badgeBg = 0xFF3A3010;
        } else if (line.leveling) {
            badgeKey = "nightsiege.pick.leveling";
            badgeFg = 0xFF7FE08A;
            badgeBg = 0xFF12301A;
        } else if (line.unlocked) {
            badgeKey = "nightsiege.pick.open";
            badgeFg = 0xFF9CC4FF;
            badgeBg = 0xFF162236;
        } else if (choosableNow(line)) {
            badgeKey = "nightsiege.pick.choosable";
            badgeFg = 0xFFFFD86B;
            badgeBg = 0xFF3A3010;
        } else {
            badgeKey = "nightsiege.pick.locked";
            badgeFg = 0xFF8F879F;
            badgeBg = 0xFF24222A;
        }
        String badge = Component.translatable(badgeKey).getString();
        int bw = (int) (font.width(badge) * 0.6f) + 6;
        gui.fill(x + w - 6 - bw, sy + 3, x + w - 6, sy + 12, badgeBg);
        smallText(gui, font, badge, x + w - 3 - bw, sy + 5, badgeFg, 0.6f);
        if (line.played) {
            String played = Component.translatable("nightsiege.ui.class_played").getString();
            smallText(gui, font, played, x + 8, sy + 5, 0xFFFFD86B, 0.6f);
        }

        // Subclass, points and the bar.
        int ty = sy + sh + 3;
        String sub;
        if (own) {
            sub = Component.translatable("nightsiege.subclass." + line.subclass).getString();
        } else if (line.unlocked) {
            sub = Component.translatable("nightsiege.ui.class_no_sub").getString();
        } else {
            sub = Component.translatable(choosableNow(line) ? "nightsiege.ui.class_choose_hint" : "nightsiege.ui.class_locked_hint").getString();
        }
        smallText(gui, font, font.plainSubstrByWidth(sub, (int) ((w - 10) / 0.7f)), x + 5, ty, own ? 0xFFCFC7DD : 0xFF8F879F, 0.7f);
        if (line.unlocked) {
            String pts = line.total > 0 ? Component.translatable("nightsiege.pick.points_of", line.spent, line.total).getString()
                    : Component.translatable("nightsiege.pick.points", line.spent).getString();
            smallText(gui, font, font.plainSubstrByWidth(pts, (int) ((w - 10) / 0.6f)), x + 5, ty + 9, 0xFF8F879F, 0.6f);
            int barY = ty + 17;
            gui.fill(x + 5, barY, x + w - 5, barY + 4, 0xFF2C2740);
            gui.fill(x + 6, barY + 1, x + w - 6, barY + 3, 0xFF0E0C14);
            if (line.total > 0) {
                int full = w - 12;
                int fill = line.complete ? full : Math.min(full, full * line.spent / Math.max(1, line.total));
                gui.fill(x + 6, barY + 1, x + 6 + fill, barY + 3, line.complete || line.ultDone ? 0xFFFFD86B : 0xFF4CC35A);
            }
        }
    }

    private void drawFooter(GuiGraphicsExtractor gui, Font font) {
        if (activeTab == Tab.PERKS) {
            gui.text(font, Component.translatable("nightsiege.ui.footer_perks").getString(), left + 10, top + H - 19, TITLE, false);
            return;
        }
        if (activeTab == Tab.COLLECTION || activeTab == Tab.CLASSES) {
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
        Component name = Component.translatable(confirming.translationKey());
        if (hasClass()) {
            // Opening the next class: new points go there from now on.
            drawDialog(gui, font, Component.translatable("nightsiege.ui.next_title", name).getString(),
                    Component.translatable("nightsiege.ui.next_warn1").getString(), 0xFFFFD86B,
                    Component.translatable("nightsiege.ui.next_warn2").getString(), 0xFFD0C8DC);
            return;
        }
        drawDialog(gui, font, Component.translatable("nightsiege.ui.confirm_title", name).getString(),
                Component.translatable("nightsiege.ui.confirm_warn1").getString(), 0xFFFF6A5A,
                Component.translatable("nightsiege.ui.confirm_warn2").getString(), 0xFFD0C8DC);
    }

    private void drawResetConfirm(GuiGraphicsExtractor gui, Font font) {
        drawDialog(gui, font, Component.translatable(stats.classResetUnlimited ? "nightsiege.ui.class_reset_title_test"
                        : "nightsiege.ui.class_reset_title").getString(),
                Component.translatable("nightsiege.ui.class_reset_warn1", stats.earned - stats.donated).getString(), 0xFFD0C8DC,
                Component.translatable(stats.classResetUnlimited ? "nightsiege.ui.class_reset_warn2_test"
                        : "nightsiege.ui.class_reset_warn2").getString(), 0xFFFF6A5A);
    }

    /** A dark question box over the dimmed map, edged in the colour of the class. */
    private void drawDialog(GuiGraphicsExtractor gui, Font font, String title, String first, int firstColor,
                            String second, int secondColor) {
        int rgb = activeTab.color & 0xFFFFFF;
        int w = Math.max(240, Math.max(font.width(title), Math.max(font.width(first), font.width(second))) + 28);
        int h = 92;
        int x = left + (W - w) / 2;
        int y = top + H / 2 - 50;
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        gui.fill(x, y, x + w, y + h, 0xF8100D16);
        gui.fill(x, y, x + w, y + 1, 0xFF000000 | rgb);
        gui.fill(x, y + h - 1, x + w, y + h, 0x80000000 | rgb);
        gui.fill(x, y, x + 1, y + h, 0x80000000 | rgb);
        gui.fill(x + w - 1, y, x + w, y + h, 0x80000000 | rgb);
        int cx = x + w / 2;
        gui.text(font, title, cx - font.width(title) / 2, y + 10, 0xFF000000 | rgb, true);
        gui.text(font, first, cx - font.width(first) / 2, y + 28, firstColor, true);
        gui.text(font, second, cx - font.width(second) / 2, y + 40, secondColor, true);
    }

    private static final java.util.Map<String, Integer> PERK_COLORS = java.util.Map.of(
            "vitality", 0xFFFF5A64, "bulwark", 0xFFC0C8DC, "tides", 0xFF4FD6E6,
            "flame", 0xFFFF9A3C, "vigilance", 0xFFFFD84A);

    /** The five perks of the main core: stage, fragments to the next one and what they do now. */
    /** The night fragments: which one sits in the core, and what each one does. */
    /** Which class the collection tab shows (kept while the game runs). */
    private static String collectionClass;
    private com.mki.nightsiege.core.CrateGains.Gain hoveredGain;

    private String collectionClass() {
        if (collectionClass == null || SkillClass.byName(collectionClass) == null) {
            collectionClass = stats.playClass != null && !stats.playClass.isBlank() ? stats.playClass
                    : stats.myClass != null && !stats.myClass.isBlank() ? stats.myClass : SkillClass.WARRIOR.name();
        }
        return collectionClass;
    }

    private int gainStage(String clazz, String gain) {
        java.util.Map<String, Integer> map = stats.gains == null ? null : stats.gains.get(clazz);
        Integer v = map == null ? null : map.get(gain);
        return v == null ? 0 : v;
    }

    /** The collection: every crate gain of one class's kit, by rarity; owned, half or missing. */
    private com.mki.nightsiege.core.CrateGains.Gain drawCollection(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        // The treasure vault: every rarity a shelf, every gain a little showcase that lights
        // up in its rarity's colour once it is drawn.
        String clazz = collectionClass();
        SkillClass c = SkillClass.byName(clazz);
        java.util.List<com.mki.nightsiege.core.CrateGains.Gain> gains = com.mki.nightsiege.core.CrateGains.forCollection(c);
        int owned = 0;
        for (com.mki.nightsiege.core.CrateGains.Gain g : gains) {
            if (g.level(gainStage(clazz, g.id())) > 0 || (g.consumable() && gainStage(clazz, g.id()) > 0)) {
                owned++;
            }
        }
        int x = innerX() + 6;
        int right = innerX() + innerW() - 6;
        int y = innerY() + 23;
        String head = Component.translatable("nightsiege.ui.collection_head",
                Component.translatable(c.translationKey()), owned, gains.size()).getString();
        gui.text(font, head, x, y, 0xFFFFD86B, true);
        // Overall progress, a golden bar right of the heading.
        int barX = x + font.width(head) + 10;
        if (barX < right - 40) {
            gui.fill(barX, y + 2, right, y + 7, 0xFF2A2018);
            gui.fill(barX + 1, y + 3, right - 1, y + 6, 0xFF120E0A);
            int fill = (right - 2 - barX) * owned / Math.max(1, gains.size());
            gui.fill(barX + 1, y + 3, barX + 1 + fill, y + 6, 0xFFE8B84A);
        }
        y += 13;
        int labelW = 70;
        int perRow = 8;
        int tileW = Math.max(22, (right - x - labelW) / perRow);
        int bottom = innerY() + innerH() - 13;
        int rows = com.mki.nightsiege.core.CrateRarity.values().length;
        // The shelves share the height: showcases between 18 and 24 pixels.
        int box = Math.max(18, Math.min(Math.min(tileW - 3, 24), (bottom - y) / rows - 7));
        int shelfH = box + 6;
        int step = Math.max(shelfH + 1, Math.min(shelfH + 6, (bottom - y) / rows));
        com.mki.nightsiege.core.CrateGains.Gain hovered = null;
        long now = System.currentTimeMillis();
        for (com.mki.nightsiege.core.CrateRarity rarity : com.mki.nightsiege.core.CrateRarity.values()) {
            int rgb = rarity.color() & 0xFFFFFF;
            int color = 0xFF000000 | rgb;
            int have = 0;
            int all = 0;
            for (com.mki.nightsiege.core.CrateGains.Gain g : gains) {
                if (g.rarity() == rarity) {
                    all++;
                    int st = gainStage(clazz, g.id());
                    if (g.level(st) > 0 || (g.consumable() && st > 0)) {
                        have++;
                    }
                }
            }
            // The plaque on the left.
            gui.fill(x, y, x + labelW - 6, y + box, 0xC0100C0A);
            gui.fill(x, y, x + 2, y + box, color);
            gui.text(font, font.plainSubstrByWidth(Component.translatable(rarity.translationKey()).getString(), labelW - 12),
                    x + 5, y + 3, color, true);
            String count = have + "/" + all;
            smallText(gui, font, count, x + 5, y + box - 8, 0xFFB0A898, 0.7f);
            int pb = x + 5 + (int) (font.width(count) * 0.7f) + 3;
            if (pb < x + labelW - 9) {
                gui.fill(pb, y + box - 6, x + labelW - 9, y + box - 4, 0xFF2A2420);
                gui.fill(pb, y + box - 6, pb + (x + labelW - 9 - pb) * have / Math.max(1, all), y + box - 4, color);
            }
            // The shelf.
            int sx = x + labelW;
            gui.fill(sx - 2, y + box + 1, right, y + box + 3, 0xFF5A4028);
            gui.fill(sx - 2, y + box + 3, right, y + box + 5, 0xFF3B2A1A);
            gui.fill(sx - 2, y + box + 5, right, y + box + 6, 0x80000000);
            int tx = sx;
            for (com.mki.nightsiege.core.CrateGains.Gain g : gains) {
                if (g.rarity() != rarity) {
                    continue;
                }
                if (tx + box > right) {
                    break;
                }
                int stage = gainStage(clazz, g.id());
                boolean got = g.level(stage) > 0 || (g.consumable() && stage > 0);
                boolean half = !got && stage > 0;
                boolean over = mouseX >= tx && mouseX < tx + box && mouseY >= y && mouseY < y + box;
                // The showcase: glass edges, a glow in the rarity colour when drawn.
                int edge = got ? color : half ? 0xFFC9963A : 0xFF3A3430;
                if (over) {
                    edge = 0xFFFFFFFF;
                }
                gui.fill(tx, y, tx + box, y + box, edge);
                gui.fill(tx + 1, y + 1, tx + box - 1, y + box - 1, 0xFF0C0A0E);
                if (got) {
                    int pulse = rarity.ordinal() >= 3 ? (int) (24 + 16 * Math.sin(now / 400.0 + tx)) : 28;
                    gui.fill(tx + 1, y + 1, tx + box - 1, y + box - 1, (pulse << 24) | rgb);
                    gui.fill(tx + 1, y + 1, tx + box - 1, y + 2, 0x60FFFFFF);
                }
                int ix = tx + (box - 16) / 2;
                int iy = y + (box - 16) / 2;
                gui.item(iconOf(com.mki.nightsiege.core.CrateGains.icon(g.id())), ix, iy);
                if (!got && !half) {
                    gui.fill(tx + 1, y + 1, tx + box - 1, y + box - 1, 0xB80C0A0E);   // not drawn yet: in the dark
                }
                if (!g.ready()) {
                    smallText(gui, font, "…", tx + box - 6, y + 1, 0xFF6A6474, 0.8f);
                } else if (got && g.topStage() > 1 && !g.twoPulls()) {
                    String st = stage + "/" + g.topStage();
                    smallText(gui, font, st, tx + box - 1 - (int) (font.width(st) * 0.6f), y + box - 6, 0xFFFFFFFF, 0.6f);
                } else if (got) {
                    smallText(gui, font, "✔", tx + box - 6, y + 1, color, 0.7f);
                } else if (half) {
                    smallText(gui, font, "½", tx + box - 6, y + 1, 0xFFFFC46B, 0.7f);
                }
                if (over) {
                    hovered = g;
                }
                tx += tileW;
            }
            y += step;
        }
        String foot = Component.translatable("nightsiege.ui.collection_foot").getString();
        gui.text(font, font.plainSubstrByWidth(foot, innerW() - 12), x, innerY() + innerH() - 10, 0xFFB0A898, false);
        return hovered;
    }

    private static void smallText(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }

    private void gainTooltip(GuiGraphicsExtractor gui, Font font, com.mki.nightsiege.core.CrateGains.Gain g, int mouseX, int mouseY) {
        java.util.List<String> lines = new ArrayList<>();
        lines.add(Component.translatable("nightsiege.gain." + g.id()).getString());
        lines.add("§8" + Component.translatable(g.rarity().translationKey()).getString()
                + (g.general() ? " · " + Component.translatable("nightsiege.ui.collection_general").getString() : ""));
        String descKey = "nightsiege.gain." + g.id() + ".desc";
        if (Language.getInstance().has(descKey)) {
            for (String part : wrap(font, Language.getInstance().getOrDefault(descKey), 200)) {
                lines.add("§7" + part);
            }
        }
        Component keyLine = KeyHints.line(com.mki.nightsiege.core.KeyHint.forGain(g.id()));
        if (keyLine != null) {
            lines.add("§b" + keyLine.getString());
        }
        int stage = gainStage(collectionClass(), g.id());
        if (!g.ready()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.collection_soon").getString());
        } else if (g.twoPulls()) {
            lines.add(stage >= 2 ? "§a" + Component.translatable("nightsiege.ui.collection_have").getString()
                    : stage == 1 ? "§6" + Component.translatable("nightsiege.ui.collection_half").getString()
                    : "§8" + Component.translatable("nightsiege.ui.collection_missing").getString());
        } else if (g.topStage() > 1) {
            lines.add((stage > 0 ? "§a" : "§8") + Component.translatable("nightsiege.ui.collection_stage", stage, g.topStage()).getString());
        } else {
            lines.add(stage > 0 ? "§a" + Component.translatable("nightsiege.ui.collection_have").getString()
                    : "§8" + Component.translatable("nightsiege.ui.collection_missing").getString());
        }
        if (!g.way().isEmpty()) {
            lines.add("§8" + Component.translatable("nightsiege.ui.collection_way",
                    Component.translatable("nightsiege.path." + g.way())).getString());
        }
        tooltip(gui, font, lines, mouseX, mouseY);
    }

    private void drawPerks(GuiGraphicsExtractor gui, Font font) {
        // The ritual altar: an inscription on top, the six fragments as rune tablets,
        // the one in the core glowing in its colour.
        com.mki.nightsiege.core.NightFragment set = com.mki.nightsiege.core.NightFragment.byName(stats.nightFragment);
        int x = innerX() + 8;
        int w = innerW() - 16;
        int y = innerY() + 6;
        String head = set == null
                ? Component.translatable("nightsiege.ui.fragment_none").getString()
                : Component.translatable("nightsiege.ui.fragment_set",
                        Component.translatable("item.nightsiege.night_fragment_" + set.id()),
                        Component.translatable("nightsiege.event." + set.event().name().toLowerCase(Locale.ROOT))).getString();
        List<String> headLines = wrap(font, head, w - 16);
        int plateH = headLines.size() * 10 + 8;
        int accent = set == null ? 0xFF8A70B8 : set.color();
        gui.fill(x, y, x + w, y + plateH, 0xD0100C16);
        gui.fill(x, y, x + w, y + 1, accent);
        gui.fill(x, y + plateH - 1, x + w, y + plateH, 0x60000000);
        int ty = y + 5;
        for (String line : headLines) {
            gui.text(font, line, x + 8, ty, set == null ? 0xFFC8BCD8 : 0xFFF0E0FF, true);
            ty += 10;
        }
        int gridY = y + plateH + 6;
        int cols = 3;
        int gap = 6;
        int tabW = (w - (cols - 1) * gap) / cols;
        int tabH = Math.max(40, (innerY() + innerH() - 4 - gridY - gap) / 2);
        long now = System.currentTimeMillis();
        com.mki.nightsiege.core.NightFragment[] all = com.mki.nightsiege.core.NightFragment.values();
        for (int i = 0; i < all.length; i++) {
            com.mki.nightsiege.core.NightFragment fragment = all[i];
            int tx = x + (i % cols) * (tabW + gap);
            int tyy = gridY + (i / cols) * (tabH + gap);
            int color = fragment.color() | 0xFF000000;
            int rgb = color & 0xFFFFFF;
            boolean chosen = fragment == set;
            if (chosen) {
                int glow = (int) (60 + 40 * Math.sin(now / 350.0));
                gui.fill(tx - 2, tyy - 2, tx + tabW + 2, tyy + tabH + 2, (glow << 24) | rgb);
            }
            // The stone tablet with its rune edge.
            gui.fill(tx, tyy, tx + tabW, tyy + tabH, chosen ? color : 0xFF2A2533);
            gui.fill(tx + 1, tyy + 1, tx + tabW - 1, tyy + tabH - 1, 0xF0141119);
            gui.fill(tx + 1, tyy + 1, tx + tabW - 1, tyy + 3, (0x90 << 24) | rgb);
            for (int k = 0; k < 6; k++) {
                gui.fill(tx + 1, tyy + 3 + k, tx + tabW - 1, tyy + 4 + k, ((0x22 - k * 5) << 24) | rgb);
            }
            // The socket with the fragment.
            int sx = tx + 6;
            int sy = tyy + 8;
            gui.fill(sx + 1, sy, sx + 21, sy + 22, 0xFF0A080E);
            gui.fill(sx, sy + 1, sx + 22, sy + 21, 0xFF0A080E);
            gui.fill(sx + 2, sy + 2, sx + 20, sy + 20, (0x30 << 24) | rgb);
            gui.item(iconOf("nightsiege:night_fragment_" + fragment.id()), sx + 3, sy + 3);
            int textX = sx + 26;
            int textW = tx + tabW - 4 - textX;
            String name = Component.translatable("item.nightsiege.night_fragment_" + fragment.id()).getString();
            if (font.width(name) <= textW) {
                gui.text(font, name, textX, sy + 1, color, true);
            } else {
                float sc = Math.max(0.7f, (float) textW / font.width(name));
                smallText(gui, font, font.plainSubstrByWidth(name, (int) (textW / sc)), textX, sy + 2, color, sc);
            }
            String event = "→ " + Component.translatable("nightsiege.event." + fragment.event().name().toLowerCase(Locale.ROOT)).getString();
            gui.text(font, font.plainSubstrByWidth(event, textW), textX, sy + 12, WHITE, true);
            String where = Component.translatable("nightsiege.ui.fragment_where." + fragment.id()).getString();
            int wy = sy + 26;
            for (String line : wrap(font, where, (int) ((tabW - 12) / 0.7f))) {
                if (wy + 7 > tyy + tabH - (chosen ? 13 : 3)) {
                    break;
                }
                smallText(gui, font, line, tx + 6, wy, 0xFF9A90AA, 0.7f);
                wy += 7;
            }
            if (chosen) {
                // "In the core": a little gold plate on the tablet's lower edge.
                String mark = "★ " + Component.translatable("nightsiege.ui.fragment_in_core").getString();
                int mw = (int) (font.width(mark) * 0.7f);
                int my = tyy + tabH - 11;
                gui.fill(tx + tabW - mw - 10, my, tx + tabW - 3, my + 9, 0xF0302408);
                gui.fill(tx + tabW - mw - 10, my, tx + tabW - 3, my + 1, 0xFFFFD86B);
                smallText(gui, font, mark, tx + tabW - mw - 6, my + 2, 0xFFFFD86B, 0.7f);
            }
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
            if (stats.killsRequired > 0) {
                gui.text(font, Component.translatable("nightsiege.ui.stat_kills", stats.nightKills, stats.killsRequired).getString(),
                        x + 8, cy + 24, stats.nightKills >= stats.killsRequired ? GREEN : GOLD, true);
            }
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
        // When the next night comes: in days, or today with the vote.
        String when;
        if (stats.nightComing) {
            when = Component.translatable("nightsiege.ui.rhythm_tonight").getString();
        } else if (stats.voteOpen) {
            when = Component.translatable("nightsiege.ui.rhythm_vote").getString();
        } else if (stats.daysUntilSiege == 0) {
            when = Component.translatable("nightsiege.ui.rhythm_today").getString();
        } else if (stats.daysUntilSiege > 0) {
            when = Component.translatable("nightsiege.ui.rhythm_days", stats.daysUntilSiege).getString();
        } else {
            when = Component.translatable("nightsiege.ui.rhythm_none").getString();
        }
        gui.text(font, when, x + 8, cy, stats.voteOpen || stats.nightComing ? GOLD : GRAY, true);
        cy += 12;
        boolean enough = stats.accepted >= stats.acceptsRequired;
        int dot = stats.voteOpen || stats.nightComing ? (enough ? 0xFF55FF55 : 0xFFFFAA00) : 0xFF8C8894;
        gui.fill(x + 8, cy + 2, x + 13, cy + 7, dot);
        String line = stats.voteOpen || stats.nightComing
                ? Component.translatable("nightsiege.ui.rhythm_accepts", stats.accepted, stats.acceptsRequired).getString()
                : Component.translatable("nightsiege.ui.rhythm_registered", stats.registeredCount).getString();
        gui.text(font, line, x + 17, cy, WHITE, true);
        cy += 11;
        if (stats.voteOpen || stats.nightComing) {
            String mine = stats.myVote.equals("yes") ? "nightsiege.ui.rhythm_me_yes"
                    : stats.myVote.equals("no") ? "nightsiege.ui.rhythm_me_no" : "nightsiege.ui.rhythm_me_open";
            gui.text(font, Component.translatable(mine).getString(), x + 17, cy,
                    stats.myVote.equals("yes") ? GREEN : stats.myVote.equals("no") ? RED : GOLD, true);
        }
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
        Component keyLine = KeyHints.line(com.mki.nightsiege.core.KeyHint.byName(node.key));
        if (keyLine != null) {
            lines.add("§b" + keyLine.getString());
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

    private void tooltip(GuiGraphicsExtractor gui, Font font, List<String> raw, int mouseX, int mouseY) {
        // Long lines are wrapped, so the box always fits on the screen; a colour code carries over.
        int maxW = Math.max(120, Math.min(230, this.width - 24));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            String code = line.length() >= 2 && line.charAt(0) == '\u00a7' ? line.substring(0, 2) : "";
            List<String> parts = wrap(font, code.isEmpty() ? line : line.substring(2), maxW);
            for (int j = 0; j < parts.size(); j++) {
                lines.add(code + parts.get(j));
            }
            if (i == 0 && parts.size() > 1) {
                firstLines = parts.size();
            }
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxW = width + 10;
        int boxH = lines.size() * 11 + 6;
        int boxX = Math.max(2, Math.min(mouseX + 10, this.width - boxW - 2));
        int boxY = Math.max(2, Math.min(mouseY + 8, this.height - boxH - 2));
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
            gui.text(font, lines.get(i), boxX + 5, y, i < firstLines ? WHITE : GRAY, false);
            y += 11;
        }
        firstLines = 1;
    }

    /** How many lines the title of the current tooltip took after wrapping. */
    private int firstLines = 1;


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
