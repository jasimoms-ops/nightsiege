package com.mki.nightsiege.client;

import com.mki.nightsiege.core.TowerMenuData;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.ModContent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The engineer's tower menu (Simon, 27 October 2026), opened with a right click of the remote:
 * every own tower and trap with its magazine, wear and state. Reloading and strengthening a
 * tower (Simon 19:15: instead of repairs – nothing breaks any more) each have a code – arrows
 * like the stratagems in Helldivers, typed with WASD or the arrow keys – and work a few seconds
 * later. With the copper golem, two towers can be given to it. Dark violet panels, gold accents.
 */
public class TowerMenuScreen extends Screen {

    private static final int GOLD = 0xFFFFD86B;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int GREEN = 0xFF7CD67C;
    private static final int RED = 0xFFE05A5A;
    private static final int AMBER = 0xFFFFB347;
    private static final int PANEL = 0xE0121019;
    private static final int ROW = 0xC01A1626;
    private static final int ROW_OVER = 0xE0241E34;
    private static final int LINE = 0xFF2C2740;
    private static final int ROW_H = 24;
    private static final int BTN_W = 50;
    private static final String RELOAD = "reload";
    private static final String BUFF = "buff";
    /** The copper golem's mark (looked up by id: the copper blocks are a weathering collection in 26.2). */
    private static final ItemStack COPPER = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("copper_block")));
    private static final String[] ARROW = {"▲", "▼", "◀", "▶"};

    private TowerMenuData data;
    private long ticks;
    private long dataAt;
    private int scroll;
    /** The row whose code is being typed, or null, and what the code does ("reload" or "buff"). */
    private TowerMenuData.Row coding;
    private String codingAction = RELOAD;
    private int[] code = new int[0];
    private int typed;
    private long wrongUntil;
    private final Random random = new Random();

    public TowerMenuScreen(TowerMenuData data) {
        super(Component.translatable("nightsiege.towers.title"));
        this.data = data;
    }

    /** A fresh state from the server (after a pick or when a fix is done). */
    public void refresh(TowerMenuData fresh) {
        if (fresh == null) {
            return;
        }
        String keep = coding == null ? null : key(coding);
        this.data = fresh;
        this.dataAt = ticks;
        if (keep != null) {
            coding = null;
            for (TowerMenuData.Row r : fresh.rows) {
                if (key(r).equals(keep) && can(r, codingAction, 0) && r.working == 0) {
                    coding = r;
                }
            }
            if (coding == null) {
                code = new int[0];
                typed = 0;
            }
        }
    }

    private static String key(TowerMenuData.Row r) {
        return r.x + ":" + r.y + ":" + r.z;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public void tick() {
        ticks++;
    }

    // ------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------

    private int panelW() {
        return Math.min(380, this.width - 16);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int listTop() {
        return panelY() + 30;
    }

    private int panelY() {
        return Math.max(6, (this.height - panelH()) / 2);
    }

    private int panelH() {
        return Math.min(this.height - 12, 30 + visibleRows() * ROW_H + 70);
    }

    private int visibleRows() {
        return Math.max(2, Math.min(8, (this.height - 12 - 100) / ROW_H));
    }

    /** The reload button; the strengthen button right of it, then the golem's field. */
    private int buttonX() {
        return panelX() + panelW() - 6 - 128;
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, data.rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(most, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int top = listTop();
        for (int i = 0; i < visibleRows(); i++) {
            int index = scroll + i;
            if (index >= data.rows.size()) {
                break;
            }
            TowerMenuData.Row row = data.rows.get(index);
            int y = top + i * ROW_H;
            if (my < y || my >= y + ROW_H - 2) {
                continue;
            }
            int bx = buttonX();
            if (mx >= bx && mx < bx + BTN_W && canStart(row, RELOAD)) {
                startCode(row, RELOAD);
                return true;
            }
            if (mx >= bx + BTN_W + 4 && mx < bx + 2 * BTN_W + 4 && canStart(row, BUFF)) {
                startCode(row, BUFF);
                return true;
            }
            if (data.golem && isTower(row) && mx >= bx + 108 && mx < bx + 126) {
                send("tower:golem:" + key(row));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (coding != null && code.length > 0) {
            int dir = switch (k) {
                case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> 0;
                case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> 1;
                case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> 2;
                case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> 3;
                default -> -1;
            };
            if (dir >= 0) {
                if (code[typed] == dir) {
                    typed++;
                    click(1.4f + 0.1f * typed);
                    if (typed >= code.length) {
                        send((BUFF.equals(codingAction) ? "tower:buff:" : "tower:fix:") + key(coding));
                        click(2.0f);
                        coding.working = data.fixSeconds * 20;
                        coding = null;
                        code = new int[0];
                        typed = 0;
                    }
                } else {
                    typed = 0;   // wrong arrow: from the start, like in Helldivers
                    wrongUntil = ticks + 8;
                    click(0.5f);
                }
                return true;
            }
            if (k == GLFW.GLFW_KEY_BACKSPACE) {
                coding = null;
                code = new int[0];
                typed = 0;
                return true;
            }
        } else if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_SPACE) {
            // Enter: the code of the first tower that needs a reload, else the first to strengthen.
            for (String action : new String[] {RELOAD, BUFF}) {
                for (TowerMenuData.Row row : data.rows) {
                    if (canStart(row, action)) {
                        startCode(row, action);
                        return true;
                    }
                }
            }
        }
        return super.keyPressed(event);
    }

    private boolean canStart(TowerMenuData.Row row, String action) {
        return row.inRange && workingLeft(row) <= 0 && can(row, action, buffLeft(row));
    }

    /** Reload: the trap is not full. Strengthen: a tower with shots left, not strengthened (or in its last 5 s). */
    private static boolean can(TowerMenuData.Row row, String action, long buffLeft) {
        if (BUFF.equals(action)) {
            return row.tower && !(row.mag > 0 && row.ammo <= 0) && buffLeft <= 100;
        }
        return !row.need.isEmpty();
    }

    private long buffLeft(TowerMenuData.Row row) {
        return row.buffed <= 0 ? 0 : Math.max(0, row.buffed - (ticks - dataAt));
    }

    private void startCode(TowerMenuData.Row row, String action) {
        coding = row;
        codingAction = action;
        int n = BUFF.equals(action) ? data.buffArrows : data.reloadArrows;
        code = new int[Math.max(1, n)];
        for (int i = 0; i < code.length; i++) {
            code[i] = random.nextInt(4);
        }
        typed = 0;
        click(1.2f);
    }

    private long workingLeft(TowerMenuData.Row row) {
        return row.working <= 0 ? 0 : row.working - (ticks - dataAt);
    }

    private static boolean isTower(TowerMenuData.Row row) {
        TrapKind kind = TrapKind.byId(row.kind);
        return kind != null && kind.isTower();
    }

    private static void send(String action) {
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        }
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x0 = panelX();
        int y0 = panelY();
        int w = panelW();
        int h = panelH();
        panel(gui, x0, y0, x0 + w, y0 + h, PANEL);
        gui.fill(x0, y0, x0 + w, y0 + 2, GOLD);

        // Title line: the menu, how far the remote reaches, the golem's towers.
        gui.text(font, this.title.getString(), x0 + 8, y0 + 8, GOLD, true);
        String info = Component.translatable("nightsiege.towers.info", data.range).getString();
        if (data.golem) {
            long given = data.rows.stream().filter(r -> r.golem).count();
            info += "  ·  " + Component.translatable("nightsiege.towers.golem_count", given, data.golemMax).getString();
        }
        small(gui, font, info, x0 + w - 8 - (int) (font.width(info) * 0.7f), y0 + 10, LIGHT, 0.7f);

        // The rows.
        int top = listTop();
        if (data.rows.isEmpty()) {
            small(gui, font, Component.translatable("nightsiege.towers.none").getString(), x0 + 10, top + 6, GREY, 0.8f);
        }
        for (int i = 0; i < visibleRows(); i++) {
            int index = scroll + i;
            if (index >= data.rows.size()) {
                break;
            }
            row(gui, font, data.rows.get(index), x0 + 6, top + i * ROW_H, w - 12, mouseX, mouseY);
        }
        if (data.rows.size() > visibleRows()) {
            String more = (scroll + 1) + "–" + Math.min(data.rows.size(), scroll + visibleRows()) + " / " + data.rows.size();
            small(gui, font, more, x0 + w - 8 - (int) (font.width(more) * 0.6f), top + visibleRows() * ROW_H - 1, GREY, 0.6f);
        }

        // The code panel, or the hint.
        int cy = top + visibleRows() * ROW_H + 8;
        if (coding != null && code.length > 0) {
            String what = Component.translatable(BUFF.equals(codingAction) ? "nightsiege.towers.code_buff"
                    : "nightsiege.towers.code_reload", name(coding)).getString();
            small(gui, font, what, x0 + (w - (int) (font.width(what) * 0.8f)) / 2, cy, LIGHT, 0.8f);
            int box = 22;
            int gap = 4;
            int total = code.length * box + (code.length - 1) * gap;
            int bx = x0 + (w - total) / 2;
            boolean wrong = ticks < wrongUntil;
            for (int i = 0; i < code.length; i++) {
                int x = bx + i * (box + gap);
                int frame = wrong ? RED : i < typed ? GOLD : i == typed ? LIGHT : LINE;
                gui.fill(x - 1, cy + 11, x + box + 1, cy + 13 + box, frame);
                gui.fill(x, cy + 12, x + box, cy + 12 + box, i < typed ? 0xF03A2F12 : ROW);
                String a = ARROW[code[i]];
                int color = i < typed ? GOLD : wrong ? RED : 0xFFFFFFFF;
                gui.pose().pushMatrix();
                gui.pose().translate(x + box / 2f - font.width(a) * 0.75f, cy + 15);
                gui.pose().scale(1.5f, 1.5f);
                gui.text(font, a, 0, 0, color, true);
                gui.pose().popMatrix();
            }
            String keys = Component.translatable("nightsiege.towers.code_keys").getString();
            small(gui, font, keys, x0 + (w - (int) (font.width(keys) * 0.65f)) / 2, cy + 40, GREY, 0.65f);
        } else {
            String hint = Component.translatable("nightsiege.towers.hint", data.reloadArrows, data.buffArrows,
                    data.buffPercent, data.buffSeconds).getString();
            small(gui, font, hint, x0 + (w - (int) (font.width(hint) * 0.65f)) / 2, cy + 6, GREY, 0.65f);
            if (data.golem) {
                String g = Component.translatable("nightsiege.towers.golem_hint", data.golemMax).getString();
                small(gui, font, g, x0 + (w - (int) (font.width(g) * 0.65f)) / 2, cy + 18, GREY, 0.65f);
            }
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor gui, Font font, TowerMenuData.Row row, int x, int y, int w, int mouseX, int mouseY) {
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H - 2;
        boolean selected = coding != null && key(coding).equals(key(row));
        gui.fill(x, y, x + w, y + ROW_H - 2, selected ? 0xF02A2238 : over ? ROW_OVER : ROW);
        long strong = buffLeft(row);
        int stripe = !row.need.isEmpty() ? AMBER : strong > 0 ? GOLD : GREEN;
        gui.fill(x, y, x + 2, y + ROW_H - 2, stripe);
        gui.item(icon(row), x + 4, y + 3);
        String name = name(row);
        String dist = row.dist + " m";
        gui.text(font, font.plainSubstrByWidth(name, 120), x + 24, y + 3, row.inRange ? 0xFFFFFFFF : GREY, true);
        small(gui, font, dist + (row.inRange ? "" : " · " + Component.translatable("nightsiege.towers.far").getString()),
                x + 24, y + 13, GREY, 0.65f);

        // The state in the middle: a magazine bar, the wear, broken, at work.
        int sx = x + 150;
        int sw = buttonX() - sx - 8;
        long left = workingLeft(row);
        if (left > 0) {
            String t = Component.translatable("nightsiege.towers.working", (left + 19) / 20).getString();
            small(gui, font, t, sx, y + 7, LIGHT, 0.75f);
        } else if (row.mag > 0) {
            bar(gui, sx, y + 6, sw, row.ammo / (double) row.mag, row.ammo == 0 ? RED : row.ammo * 4 <= row.mag ? AMBER : GREEN);
            small(gui, font, Component.translatable("nightsiege.towers.ammo", row.ammo, row.mag).getString(), sx, y + 13, LIGHT, 0.6f);
            if (strong > 0) {
                String b = Component.translatable("nightsiege.towers.buffed", (strong + 19) / 20).getString();
                small(gui, font, b, sx + sw - (int) (font.width(b) * 0.6f), y + 13, GOLD, 0.6f);
            }
        } else if (strong > 0) {
            small(gui, font, Component.translatable("nightsiege.towers.buffed", (strong + 19) / 20).getString(), sx, y + 7, GOLD, 0.75f);
        } else if (row.fullUses > 0) {
            bar(gui, sx, y + 6, sw, row.uses / (double) row.fullUses, row.uses * 4 <= row.fullUses ? AMBER : GREEN);
            small(gui, font, Component.translatable("nightsiege.towers.wear", row.uses, row.fullUses).getString(), sx, y + 13, LIGHT, 0.6f);
        } else {
            small(gui, font, Component.translatable("nightsiege.towers.ready").getString(), sx, y + 7, GREEN, 0.75f);
        }

        // The actions on the right – reload, strengthen – and the golem's mark.
        int bx = buttonX();
        if (left <= 0 && can(row, RELOAD, strong)) {
            button(gui, font, bx, y, Component.translatable("nightsiege.towers.reload").getString(), row.inRange, mouseX, mouseY);
        }
        if (left <= 0 && can(row, BUFF, strong)) {
            button(gui, font, bx + BTN_W + 4, y, Component.translatable("nightsiege.towers.buff").getString(), row.inRange, mouseX, mouseY);
        }
        if (data.golem && isTower(row)) {
            int gx = bx + 108;
            boolean overG = mouseX >= gx && mouseX < gx + 18 && mouseY >= y && mouseY < y + ROW_H - 2;
            gui.fill(gx - 1, y + 2, gx + 19, y + ROW_H - 3, row.golem ? GOLD : overG ? LIGHT : LINE);
            gui.fill(gx, y + 3, gx + 18, y + ROW_H - 4, row.golem ? 0xF03A2F12 : 0xF0181424);
            gui.pose().pushMatrix();
            gui.pose().translate(gx + 2, y + 4);
            gui.pose().scale(0.85f, 0.85f);
            gui.item(COPPER, 0, 0);
            gui.pose().popMatrix();
            if (!row.golem) {
                gui.fill(gx, y + 3, gx + 18, y + ROW_H - 4, 0x80000000);
            }
        }
    }

    private static void button(GuiGraphicsExtractor gui, Font font, int bx, int y, String label, boolean can, int mouseX, int mouseY) {
        boolean over = can && mouseX >= bx && mouseX < bx + BTN_W && mouseY >= y && mouseY < y + ROW_H - 2;
        int frame = !can ? LINE : over ? GOLD : 0xFF7C6A3A;
        gui.fill(bx - 1, y + 2, bx + BTN_W + 1, y + ROW_H - 3, frame);
        gui.fill(bx, y + 3, bx + BTN_W, y + ROW_H - 4, over ? 0xF03A2F12 : 0xF0181424);
        float scale = Math.min(0.7f, (BTN_W - 4) / (float) Math.max(1, font.width(label)));
        small(gui, font, label, bx + BTN_W / 2 - (int) (font.width(label) * scale) / 2, y + 8, can ? GOLD : GREY, scale);
    }

    private static void bar(GuiGraphicsExtractor gui, int x, int y, int w, double share, int color) {
        gui.fill(x, y, x + w, y + 4, 0xFF0C0A12);
        gui.fill(x, y, x + (int) Math.round(w * Math.max(0.0, Math.min(1.0, share))), y + 4, color);
    }

    private static ItemStack icon(TowerMenuData.Row row) {
        TrapKind kind = TrapKind.byId(row.kind);
        Item item = kind == null ? null : ModContent.TRAP_ITEMS.get(kind);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String name(TowerMenuData.Row row) {
        return Component.translatable("block.nightsiege." + row.kind).getString();
    }

    private static void panel(GuiGraphicsExtractor gui, int x0, int y0, int x1, int y1, int color) {
        gui.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, LINE);
        gui.fill(x0, y0, x1, y1, color);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    /** For the client test: the reload code of a row, typed right. */
    public void testFix(int rowIndex) {
        testCode(rowIndex, RELOAD);
    }

    /** For the client test: the strengthen code of a row, typed right. */
    public void testBuff(int rowIndex) {
        testCode(rowIndex, BUFF);
    }

    /** For the client test: the first row of this trap kind (-1: none). */
    public int rowOf(String kind) {
        for (int i = 0; i < data.rows.size(); i++) {
            if (data.rows.get(i).kind.equals(kind)) {
                return i;
            }
        }
        return -1;
    }

    private void testCode(int rowIndex, String action) {
        if (rowIndex < 0 || rowIndex >= data.rows.size() || !canStart(data.rows.get(rowIndex), action)) {
            return;
        }
        startCode(data.rows.get(rowIndex), action);
        int[] all = code.clone();
        for (int dir : all) {
            int key = switch (dir) {
                case 0 -> GLFW.GLFW_KEY_W;
                case 1 -> GLFW.GLFW_KEY_S;
                case 2 -> GLFW.GLFW_KEY_A;
                default -> GLFW.GLFW_KEY_D;
            };
            keyPressed(new KeyEvent(key, 0, 0));
        }
    }

    /** For the client test: open a code without typing it (for the picture). */
    public void testShowCode(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < data.rows.size() && canStart(data.rows.get(rowIndex), BUFF)) {
            startCode(data.rows.get(rowIndex), BUFF);
            typed = Math.min(2, code.length - 1);
        }
    }

    public List<TowerMenuData.Row> rows() {
        return new ArrayList<>(data.rows);
    }
}
