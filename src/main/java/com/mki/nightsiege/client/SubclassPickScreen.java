package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Choosing the subclass of a class (Simon's design of 26 October 2026): the four subclasses
 * as armour stands in their outfits, grouped by way. The choice settles way and subclass
 * at once; the other three are shut in the tree. Comes after the first class choice, after
 * a new class is opened (the ultimate of the last one at level 3), and for a class that was
 * chosen before this window existed.
 */
public class SubclassPickScreen extends Screen {

    /** Why the window is open: the first class, the next class, or a class without a subclass. */
    public enum Mode { FIRST, NEXT, FIX }

    private static final int BACKDROP = 0xB0000000;
    private static final int FRAME = 0xFF3A3450;
    private static final int PANEL = 0xF0121019;
    private static final int BOX = 0xFF1A1622;
    private static final int BOX_DARK = 0xFF0E0C14;
    private static final int LINE = 0xFF2C2740;
    private static final int GOLD = 0xFFFFD86B;
    private static final int RED = 0xFFE05A5A;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int WHITE = 0xFFFFFFFF;

    private final SkillClass clazz;
    private final Mode mode;
    private final Screen back;
    /** A way already taken (FIX for older progress): the other way's cards cannot be chosen. */
    private final String lockedWay;
    private final List<SkillTree.SubclassInfo> subs;
    private int picked;

    private int left;
    private int top;
    private int w;
    private int h;
    private int cardsY;
    private int cardsH;
    private int cardW;
    private final int[] cardX = new int[4];

    public SubclassPickScreen(SkillClass clazz, Mode mode, Screen back, String lockedWay) {
        super(Component.translatable("nightsiege.subpick.title", Component.translatable(clazz.translationKey())));
        this.clazz = clazz;
        this.mode = mode;
        this.back = back;
        this.lockedWay = lockedWay == null ? "" : lockedWay;
        this.subs = SkillTree.subclassesOf(clazz);
        this.picked = 0;
        for (int i = 0; i < subs.size(); i++) {
            if (choosable(i)) {
                picked = i;
                break;
            }
        }
    }

    private boolean choosable(int i) {
        return i >= 0 && i < subs.size() && (lockedWay.isEmpty() || lockedWay.equals(subs.get(i).way()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(back);
    }

    @Override
    protected void init() {
        w = Math.max(380, Math.min(640, this.width - 8));
        h = Math.max(220, Math.min(380, this.height - 8));
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        int headerH = h >= 300 ? 50 : 40;
        cardsY = top + headerH + 12;
        cardsH = top + h - 30 - cardsY;
        int gap = 5;
        int wayGap = 12;
        cardW = (w - 16 - 2 * gap - wayGap) / 4;
        for (int i = 0; i < 4; i++) {
            cardX[i] = left + 8 + i * (cardW + gap) + (i >= 2 ? wayGap - gap : 0);
        }
        for (int i = 0; i < subs.size() && i < 4; i++) {
            int index = i;
            Button card = Button.builder(Component.empty(), b -> {
                picked = index;
                rebuildWidgets();
            }).bounds(cardX[i], cardsY, cardW, cardsH).build();
            card.active = choosable(i);
            card.setAlpha(0f);
            addRenderableWidget(card);
        }
        Component backLabel = Component.translatable(mode == Mode.FIX ? "nightsiege.subpick.back" : "nightsiege.subpick.other_class");
        addRenderableWidget(Button.builder(backLabel, b -> onClose())
                .bounds(left + 8, top + h - 24, Math.max(90, font.width(backLabel) + 12), 18).build());
        SkillTree.SubclassInfo sub = subs.isEmpty() ? null : subs.get(picked);
        Component confirm = Component.translatable("nightsiege.subpick.confirm",
                sub == null ? Component.literal("?") : Component.translatable("nightsiege.subclass." + sub.key()));
        int cw = Math.max(120, font.width(confirm) + 16);
        Button ok = Button.builder(confirm, b -> confirm()).bounds(left + w - 8 - cw, top + h - 24, cw, 18).build();
        ok.active = sub != null && choosable(picked);
        ok.setAlpha(0f);
        addRenderableWidget(ok);
    }

    private void confirm() {
        if (subs.isEmpty() || !choosable(picked)) {
            return;
        }
        SkillTree.SubclassInfo sub = subs.get(picked);
        String action = mode == Mode.FIX ? "sub:" + clazz.name() + ":" + sub.node() : "class:" + clazz.name() + ":" + sub.node();
        ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload(action));
        // The server answers with the core window; until then the old one stays.
        Minecraft.getInstance().setScreenAndShow(back);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, BACKDROP);
        gui.fill(left - 1, top - 1, left + w + 1, top + h + 1, FRAME);
        gui.fill(left, top, left + w, top + h, PANEL);
        gui.fill(left, top, left + w, top + 1, 0xFF4A4266);

        String key = switch (mode) {
            case FIRST -> "nightsiege.subpick.first";
            case NEXT -> "nightsiege.subpick.next";
            case FIX -> "nightsiege.subpick.fix";
        };
        small(gui, font, Component.translatable(key).getString(), left + 10, top + 7, RED, 0.75f);
        String title = Component.translatable("nightsiege.subpick.title", "").getString();
        String className = Component.translatable(clazz.translationKey()).getString();
        float ts = h >= 300 ? 1.3f : 1.1f;
        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 16);
        gui.pose().scale(ts, ts);
        gui.text(font, title, 0, 0, GOLD, true);
        gui.text(font, className, font.width(title), 0, CoreColors.classColor(clazz), true);
        gui.pose().popMatrix();
        int subY = top + 16 + (int) (10 * ts) + 3;
        wrapped(gui, font, Component.translatable("nightsiege.subpick.hint").getString(), left + 10, subY, w - 20, GREY, 0.75f, 2);

        // The two ways: a line with the name above each pair of cards.
        for (int pair = 0; pair < 2 && pair * 2 < subs.size(); pair++) {
            int x0 = cardX[pair * 2];
            int x1 = cardX[Math.min(subs.size() - 1, pair * 2 + 1)] + cardW;
            String way = Component.translatable("nightsiege.subpick.way",
                    Component.translatable("nightsiege.path." + subs.get(pair * 2).way())).getString();
            int tw = (int) (font.width(way) * 0.75f);
            int mid = (x0 + x1) / 2;
            int ly = cardsY - 8;
            gui.fill(x0, ly + 3, mid - tw / 2 - 4, ly + 4, LINE);
            gui.fill(mid + tw / 2 + 4, ly + 3, x1, ly + 4, LINE);
            small(gui, font, way, mid - tw / 2, ly, LIGHT, 0.75f);
        }
        for (int i = 0; i < subs.size() && i < 4; i++) {
            drawCard(gui, font, i, mouseX, mouseY);
        }

        // Footer: what is chosen, and the gold button.
        SkillTree.SubclassInfo sub = subs.isEmpty() ? null : subs.get(picked);
        if (sub != null) {
            String chosen = Component.translatable("nightsiege.subpick.chosen",
                    Component.translatable("nightsiege.subclass." + sub.key())).getString();
            int cwText = (int) (font.width(chosen) * 0.7f);
            small(gui, font, chosen, left + w / 2 - cwText / 2, top + h - 18, GREY, 0.7f);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
        Component confirm = Component.translatable("nightsiege.subpick.confirm",
                sub == null ? Component.literal("?") : Component.translatable("nightsiege.subclass." + sub.key()));
        int cw = Math.max(120, font.width(confirm) + 16);
        int bx = left + w - 8 - cw;
        int by = top + h - 24;
        boolean over = mouseX >= bx && mouseX < bx + cw && mouseY >= by && mouseY < by + 18;
        gui.fill(bx, by, bx + cw, by + 18, 0xFF8A6A20);
        gui.fill(bx + 1, by + 1, bx + cw - 1, by + 17, over ? 0xFFFFE48A : GOLD);
        gui.fill(bx + 1, by + 16, bx + cw - 1, by + 17, 0xFFC9A440);
        gui.text(font, confirm, bx + (cw - font.width(confirm)) / 2, by + 5, 0xFF2A2010, false);
        // The lock note top right.
        String lock = Component.translatable("nightsiege.subpick.lock").getString();
        int lw = (int) (font.width(lock) * 0.65f);
        small(gui, font, lock, left + w - 10 - lw, top + 8, GREY, 0.65f);
    }

    private void drawCard(GuiGraphicsExtractor gui, Font font, int i, int mouseX, int mouseY) {
        SkillTree.SubclassInfo sub = subs.get(i);
        int x = cardX[i];
        int y = cardsY;
        boolean selected = i == picked;
        boolean open = choosable(i);
        boolean hover = open && mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardsH;
        if (selected) {
            gui.fill(x - 2, y - 2, x + cardW + 2, y + cardsH + 2, 0x30FFD86B);
        }
        gui.fill(x, y, x + cardW, y + cardsH, selected ? GOLD : hover ? 0xFF6A5F80 : LINE);
        gui.fill(x + 1, y + 1, x + cardW - 1, y + cardsH - 1, selected ? 0xFF231C33 : open ? BOX : 0xFF131118);

        // The armour stand on its dark plinth.
        int textH = cardsH >= 190 ? 74 : 58;
        int standH = Math.max(36, cardsH - textH - 8);
        int sx = x + 4;
        int sy = y + 4;
        gui.fill(sx, sy, x + cardW - 4, sy + standH, LINE);
        gui.fill(sx + 1, sy + 1, x + cardW - 5, sy + standH - 1, BOX_DARK);
        ClassStands.draw(gui, sub.key(), !open, sx + 2, sy + 3, cardW - 12, standH - 6);
        if (selected) {
            String mark = Component.translatable("nightsiege.subpick.picked").getString();
            int mw = (int) (font.width(mark) * 0.65f) + 6;
            gui.fill(x + cardW - 6 - mw, sy + 3, x + cardW - 6, sy + 12, 0xFF3A3010);
            small(gui, font, mark, x + cardW - 3 - mw, sy + 5, GOLD, 0.65f);
        }

        // Name, what it does, its signature skill.
        int ty = sy + standH + 4;
        String name = Component.translatable("nightsiege.subclass." + sub.key()).getString();
        float ns = Math.min(1.1f, Math.max(0.7f, (cardW - 10f) / Math.max(1, font.width(name))));
        gui.pose().pushMatrix();
        gui.pose().translate(x + 5, ty);
        gui.pose().scale(ns, ns);
        gui.text(font, name, 0, 0, open ? (selected ? GOLD : WHITE) : GREY, true);
        gui.pose().popMatrix();
        ty += (int) (9 * ns) + 3;
        int roleLines = Math.max(1, (y + cardsH - 14 - ty) / 7);
        ty = wrapped(gui, font, Component.translatable("nightsiege.subpick.role." + sub.key()).getString(),
                x + 5, ty, cardW - 10, open ? 0xFFB8B0C8 : 0xFF6F687E, 0.65f, Math.min(4, roleLines));
        String skill = Component.translatable("nightsiege.skill." + sub.node()).getString();
        int chipY = y + cardsH - 12;
        int chipW = (int) (font.width(skill) * 0.6f) + 6;
        gui.fill(x + 5, chipY, Math.min(x + cardW - 5, x + 5 + chipW), chipY + 9, 0xFF1E1A2A);
        small(gui, font, font.plainSubstrByWidth(skill, (int) ((cardW - 14) / 0.6f)), x + 8, chipY + 2, LIGHT, 0.6f);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    private static int wrapped(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int width, int color,
                               float scale, int maxLines) {
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), (int) (width / scale));
        int lineH = (int) Math.ceil(9 * scale) + 1;
        int n = 0;
        for (FormattedCharSequence line : lines) {
            if (n >= maxLines) {
                break;
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x, y + n * lineH);
            gui.pose().scale(scale, scale);
            gui.text(font, line, 0, 0, color, false);
            gui.pose().popMatrix();
            n++;
        }
        return y + Math.max(1, n) * lineH;
    }

    /** The keys of all subclasses in the window (for the test). */
    public List<String> keys() {
        List<String> out = new ArrayList<>();
        for (SkillTree.SubclassInfo s : subs) {
            out.add(s.key());
        }
        return out;
    }
}
