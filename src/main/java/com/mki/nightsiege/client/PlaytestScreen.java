package com.mki.nightsiege.client;

import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.core.PlaytestData;
import com.mki.nightsiege.core.SkillClass;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The playtest window (Simon, 28 October 2026): what comes (four nights, before each a war camp or
 * a dungeon), the team with their planned paths and who is ready, and three buttons – class and
 * skill tree, the crates one would have by now, ready. While the paths are planned, closing the core
 * window comes back here.
 */
public class PlaytestScreen extends Screen {

    /** The last state the server sent (for the core window's way back and a refresh). */
    private static PlaytestData last;

    private PlaytestData data;
    private int left;
    private int top;
    private int w;
    private int h;
    private final List<Button> primary = new ArrayList<>();

    public PlaytestScreen(PlaytestData data) {
        super(Component.translatable("nightsiege.playtest.title"));
        this.data = data;
        last = data;
    }

    /** A state from the server: kept, and shown if the window is open (or opened when asked). */
    public static void receive(PlaytestData data, boolean open) {
        last = data;
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof PlaytestScreen screen) {
            if (data == null || "done".equals(data.phase)) {
                screen.onClose();
            } else {
                screen.data = data;
                screen.rebuildWidgets();
            }
        } else if (open && data != null && !"done".equals(data.phase)) {
            client.setScreenAndShow(new PlaytestScreen(data));
        }
    }

    /** A new connection knows nothing of an old playtest. */
    public static void clear() {
        last = null;
    }

    /** True while this player plans their path: the core window leads back here. */
    public static boolean planning() {
        return last != null && "plan".equals(last.phase);
    }

    /** Opens the window again with the last state (from the core window). */
    public static void reopen() {
        if (last != null) {
            Minecraft.getInstance().setScreenAndShow(new PlaytestScreen(last));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean plan() {
        return "plan".equals(data.phase);
    }

    @Override
    protected void init() {
        primary.clear();
        w = Math.max(340, Math.min(480, this.width - 8));
        h = Math.max(220, Math.min(280, this.height - 8));
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        int y = top + h - 8 - 20;
        int x = left + 8;
        Component tree = Component.translatable(data.hasClass ? "nightsiege.playtest.btn_tree" : "nightsiege.playtest.btn_class");
        int tw = Math.max(110, font.width(tree) + 14);
        Button treeButton = Button.builder(tree, b -> send("tree")).bounds(x, y, tw, 20).build();
        addRenderableWidget(treeButton);
        if (plan() && !data.hasClass) {
            primary.add(treeButton);
        }
        x += tw + 4;
        Component crates = Component.translatable("nightsiege.playtest.btn_crates", data.crates);
        int cw = Math.max(96, font.width(crates) + 14);
        Button crateButton = Button.builder(crates, b -> send("crates")).bounds(x, y, cw, 20).build();
        crateButton.active = data.crates > 0 && data.hasClass;
        addRenderableWidget(crateButton);
        if (!plan() && data.crates > 0) {
            primary.add(crateButton);
        }
        int right = left + w - 8;
        if (plan()) {
            Component ready = Component.translatable(data.ready ? "nightsiege.playtest.btn_unready" : "nightsiege.playtest.btn_ready");
            int rw = Math.max(90, font.width(ready) + 14);
            Button readyButton = Button.builder(ready, b -> send("ready")).bounds(right - rw, y, rw, 20).build();
            readyButton.active = data.hasClass;
            addRenderableWidget(readyButton);
            if (!data.ready && data.hasClass) {
                primary.add(readyButton);
            }
        } else {
            Component close = Component.translatable("nightsiege.playtest.btn_close");
            int rw = Math.max(80, font.width(close) + 14);
            addRenderableWidget(Button.builder(close, b -> onClose()).bounds(right - rw, y, rw, 20).build());
        }
    }

    private void send(String action) {
        ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("playtest:" + action));
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, PurpleUi.BACKDROP);
        PurpleUi.window(gui, left, top, left + w, top + h);
        drawHeader(gui, font);
        int bodyTop = top + 44;
        int split = left + w / 2 + 10;
        drawSchedule(gui, font, left + 8, bodyTop, split - 6 - (left + 8));
        drawTeam(gui, font, split, bodyTop, left + w - 8 - split);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        PurpleUi.skinButtons(gui, font, children(), primary, mouseX, mouseY);
    }

    private String title() {
        int night = Math.max(0, data.step) + 1;
        String interlude = data.step >= 0 && data.step < data.nights.size() ? data.nights.get(data.step).interlude : "";
        return switch (data.phase) {
            case "plan" -> Component.translatable("nightsiege.playtest.t_plan").getString();
            case "pause_interlude" -> Component.translatable("nightsiege.playtest.t_soon_" + interlude).getString();
            case "interlude" -> Component.translatable("nightsiege.playtest.t_running_" + interlude).getString();
            case "pause_night" -> Component.translatable("nightsiege.playtest.t_night_soon", night).getString();
            case "night" -> Component.translatable("nightsiege.playtest.t_night", night).getString();
            case "after_night" -> Component.translatable("nightsiege.playtest.t_night_over", night).getString();
            default -> Component.translatable("nightsiege.playtest.t_done").getString();
        };
    }

    private void drawHeader(GuiGraphicsExtractor gui, Font font) {
        small(gui, font, Component.translatable("nightsiege.playtest.head", data.target).getString().toUpperCase(Locale.ROOT),
                left + 10, top + 8, PurpleUi.GREY, 0.75f);
        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 18);
        gui.pose().scale(1.3f, 1.3f);
        gui.text(font, title(), 0, 0, PurpleUi.GOLD, true);
        gui.pose().popMatrix();
        if (data.seconds >= 0) {
            String clock = (data.seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", data.seconds % 60);
            gui.pose().pushMatrix();
            gui.pose().translate(left + w - 10 - font.width(clock) * 1.3f, top + 18);
            gui.pose().scale(1.3f, 1.3f);
            gui.text(font, clock, 0, 0, PurpleUi.LIGHT, true);
            gui.pose().popMatrix();
        }
        // One line about the own path under the title.
        String line;
        if (plan()) {
            line = data.hasClass
                    ? Component.translatable("nightsiege.playtest.l_plan", className(data.clazz, data.subclass), data.planned).getString()
                    : Component.translatable("nightsiege.playtest.l_plan_none").getString();
        } else {
            line = Component.translatable("nightsiege.playtest.l_run", data.learned, data.planned, data.points).getString();
        }
        small(gui, font, font.plainSubstrByWidth(line, (int) ((w - 20) / 0.8f)), left + 10, top + 32, PurpleUi.LIGHT, 0.8f);
    }

    private void drawSchedule(GuiGraphicsExtractor gui, Font font, int x, int y, int width) {
        int bottom = top + h - 36;
        PurpleUi.box(gui, x, y, x + width, bottom, PurpleUi.BOX_DARK);
        small(gui, font, Component.translatable("nightsiege.playtest.schedule").getString(), x + 6, y + 5, PurpleUi.GREY, 0.7f);
        int rowY = y + 16;
        int rowH = Math.max(22, Math.min(30, (bottom - rowY - 4) / Math.max(1, data.nights.size())));
        for (int i = 0; i < data.nights.size(); i++) {
            PlaytestData.Night night = data.nights.get(i);
            boolean current = i == data.step && !plan();
            int fill = current ? PurpleUi.BOX_HOVER : PurpleUi.BOX;
            gui.fill(x + 4, rowY, x + width - 4, rowY + rowH - 3, fill);
            if (current) {
                gui.fill(x + 4, rowY, x + 6, rowY + rowH - 3, PurpleUi.GOLD);
            }
            int color = night.done ? PurpleUi.DIM : current ? PurpleUi.GOLD : PurpleUi.LIGHT;
            String mark = night.done ? "✓" : String.valueOf(i + 1);
            gui.text(font, mark, x + 10, rowY + (rowH - 3 - 8) / 2, night.done ? PurpleUi.GREEN : color, false);
            String before = Component.translatable("nightsiege.playtest.i_" + night.interlude).getString();
            String nightText = Component.translatable("nightsiege.playtest.n_level", night.level).getString();
            small(gui, font, before + "  →  " + nightText, x + 22, rowY + 4, color, 0.8f);
            if (!night.boss.isEmpty()) {
                String boss = Component.translatable("nightsiege.playtest.n_boss",
                        Component.translatable("nightsiege.boss." + night.boss)).getString();
                small(gui, font, font.plainSubstrByWidth(boss, (int) ((width - 30) / 0.7f)), x + 22, rowY + 13,
                        night.done ? PurpleUi.DIM : PurpleUi.RED, 0.7f);
            } else {
                small(gui, font, Component.translatable("nightsiege.playtest.n_random").getString(), x + 22, rowY + 13,
                        PurpleUi.DIM, 0.7f);
            }
            rowY += rowH;
        }
    }

    private void drawTeam(GuiGraphicsExtractor gui, Font font, int x, int y, int width) {
        int bottom = top + h - 36;
        PurpleUi.box(gui, x, y, x + width, bottom, PurpleUi.BOX_DARK);
        small(gui, font, Component.translatable("nightsiege.playtest.team").getString(), x + 6, y + 5, PurpleUi.GREY, 0.7f);
        int rowY = y + 16;
        int rowH = 20;
        int shown = Math.max(1, (bottom - rowY - 2) / rowH);
        for (int i = 0; i < data.team.size() && i < shown; i++) {
            PlaytestData.Mate mate = data.team.get(i);
            gui.fill(x + 4, rowY, x + width - 4, rowY + rowH - 2, PurpleUi.BOX);
            String status;
            int statusColor;
            if (!mate.online) {
                status = Component.translatable("nightsiege.playtest.away").getString();
                statusColor = PurpleUi.DIM;
            } else if (plan()) {
                status = mate.ready ? "✓" : "…";
                statusColor = mate.ready ? PurpleUi.GREEN : PurpleUi.GREY;
            } else {
                status = "";
                statusColor = PurpleUi.GREY;
            }
            int statusW = font.width(status);
            gui.text(font, font.plainSubstrByWidth(mate.name, width - 20 - statusW), x + 8, rowY + 2, PurpleUi.LIGHT, false);
            gui.text(font, status, x + width - 8 - statusW, rowY + 5, statusColor, false);
            String cls = mate.clazz.isEmpty() ? Component.translatable("nightsiege.playtest.no_class").getString()
                    : className(mate.clazz, mate.subclass) + " · "
                    + Component.translatable("nightsiege.playtest.levels", mate.planned).getString();
            small(gui, font, font.plainSubstrByWidth(cls, (int) ((width - 16) / 0.7f)), x + 8, rowY + 11, PurpleUi.GREY, 0.7f);
            rowY += rowH;
        }
        if (data.team.size() > shown) {
            small(gui, font, "+" + (data.team.size() - shown), x + width - 20, bottom - 9, PurpleUi.GREY, 0.7f);
        }
    }

    private static String className(String clazz, String subclass) {
        if (subclass != null && !subclass.isBlank()) {
            return Component.translatable("nightsiege.subclass." + subclass).getString();
        }
        SkillClass c = SkillClass.byName(clazz);
        return c == null ? clazz : Component.translatable(c.translationKey()).getString();
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }
}
