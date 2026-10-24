package com.mki.nightsiege.client;

import com.mki.nightsiege.core.ClassPickData;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.KitService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Before a night (at the horn, or in a test's preparation): which class the player defends
 * with tonight. Built after Simon's design: class cards with their progress, the kit of
 * the chosen class with the real items, the defenders and who is ready, a countdown.
 */
public class ClassPickScreen extends Screen {

    // Colours (dark night panel, gold for the chosen one).
    private static final int BACKDROP = 0xB0000000;
    private static final int FRAME = 0xFF3A3450;
    private static final int PANEL = 0xF0121019;
    private static final int BOX = 0xFF1A1622;
    private static final int BOX_DARK = 0xFF0E0C14;
    private static final int LINE = 0xFF2C2740;
    private static final int GOLD = 0xFFFFD86B;
    private static final int RED = 0xFFE05A5A;
    private static final int GREEN = 0xFF7FE08A;
    private static final int ORANGE = 0xFFFFA640;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int WHITE = 0xFFFFFFFF;

    private ClassPickData data;
    private long opened = System.currentTimeMillis();
    private String chosen;
    private final Map<String, List<ItemStack>> kits = new HashMap<>();

    private int left;
    private int top;
    private int w;
    private int h;
    private int cardsX;
    private int cardsY;
    private int cardW;
    private int cardH;
    private int teamX;
    private int teamW;

    public ClassPickScreen(ClassPickData data) {
        super(Component.translatable("nightsiege.pick.title"));
        this.data = data;
        this.chosen = data.play == null ? "" : data.play;
        decodeKits();
    }

    /** New state from the server (someone else got ready): keeps the own choice. */
    public void update(ClassPickData fresh) {
        this.data = fresh;
        this.opened = System.currentTimeMillis();
        decodeKits();
        rebuildWidgets();
    }

    private void decodeKits() {
        kits.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (ClassPickData.Entry entry : data.classes) {
            List<ItemStack> list = new ArrayList<>();
            if (entry.kit != null) {
                for (String json : entry.kit) {
                    ItemStack stack = KitService.decode(json, mc.level.registryAccess());
                    if (!stack.isEmpty()) {
                        list.add(stack);
                    }
                }
            }
            kits.put(entry.id, list);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.max(380, Math.min(540, this.width - 8));
        h = Math.max(220, Math.min(300, this.height - 8));
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        teamW = Math.max(100, Math.min(132, w / 4));
        teamX = left + w - 8 - teamW;
        cardsX = left + 8;
        cardsY = top + 40;
        int area = teamX - 6 - cardsX;
        cardW = (area - 3 * 5) / 4;
        cardH = Math.max(96, Math.min(124, (h - 40) * 46 / 100));

        for (int i = 0; i < data.classes.size() && i < 4; i++) {
            ClassPickData.Entry entry = data.classes.get(i);
            Button card = Button.builder(Component.empty(), b -> {
                chosen = entry.id;
                rebuildWidgets();
            }).bounds(cardX(i), cardsY, cardW, cardH).build();
            card.active = entry.unlocked;
            card.setAlpha(0f);
            addRenderableWidget(card);
        }
        SkillClass pick = SkillClass.byName(chosen);
        Component label = Component.translatable("nightsiege.pick.ready",
                pick == null ? Component.literal("?") : Component.translatable(pick.translationKey()));
        Button ready = Button.builder(label, b -> {
            if (pick != null) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("play:" + pick.name()));
            }
            onClose();
        }).bounds(teamX + 6, top + h - 8 - 26, teamW - 12, 20).build();
        ready.setAlpha(0f);
        addRenderableWidget(ready);
    }

    private int cardX(int index) {
        return cardsX + index * (cardW + 5);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, BACKDROP);
        int x2 = left + w;
        int y2 = top + h;
        gui.fill(left - 1, top - 1, x2 + 1, y2 + 1, FRAME);
        gui.fill(left, top, x2, y2, PANEL);
        gui.fill(left, top, x2, top + 1, 0xFF4A4266);

        drawHeader(gui, font);
        for (int i = 0; i < data.classes.size() && i < 4; i++) {
            drawCard(gui, font, data.classes.get(i), cardX(i), cardsY, mouseX, mouseY);
        }
        ItemStack hovered = drawKit(gui, font, mouseX, mouseY);
        drawTeam(gui, font, mouseX, mouseY);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        if (!hovered.isEmpty()) {
            gui.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor gui, Font font) {
        String level = String.valueOf(data.level);
        String event = data.event == null || data.event.isEmpty() ? ""
                : Component.translatable("nightsiege.event." + data.event.toLowerCase(Locale.ROOT)).getString()
                .toUpperCase(Locale.ROOT);
        String key = data.test ? "nightsiege.pick.test" : "nightsiege.pick.horn";
        String line = Component.translatable(event.isEmpty() ? key : key + "_event", level, event).getString();
        small(gui, font, line, left + 10, top + 8, RED, 0.75f);
        float titleScale = w >= 460 ? 1.3f : 1.1f;
        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 18);
        gui.pose().scale(titleScale, titleScale);
        gui.text(font, Component.translatable("nightsiege.pick.title").getString(), 0, 0, GOLD, true);
        gui.pose().popMatrix();

        // Countdown box.
        int bw = 74;
        int bx = left + w - 8 - bw;
        int by = top + 6;
        gui.fill(bx, by, bx + bw, by + 28, LINE);
        gui.fill(bx + 1, by + 1, bx + bw - 1, by + 27, BOX_DARK);
        String label = Component.translatable("nightsiege.pick.starts_in").getString();
        small(gui, font, label, bx + bw - 5 - (int) (font.width(label) * 0.7f), by + 4, GREY, 0.7f);
        int secs = Math.max(0, data.seconds - (int) ((System.currentTimeMillis() - opened) / 1000L));
        String time = (secs / 60) + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
        gui.pose().pushMatrix();
        gui.pose().translate(bx + bw - 5 - font.width(time) * 1.5f, by + 12);
        gui.pose().scale(1.5f, 1.5f);
        gui.text(font, time, 0, 0, secs <= 10 ? RED : WHITE, true);
        gui.pose().popMatrix();
    }

    private void drawCard(GuiGraphicsExtractor gui, Font font, ClassPickData.Entry entry, int x, int y, int mouseX, int mouseY) {
        boolean selected = entry.id.equals(chosen);
        boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH;
        if (selected) {
            gui.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, 0x30FFD86B);
        }
        int border = selected ? GOLD : hover && entry.unlocked ? 0xFF6A5F80 : LINE;
        gui.fill(x, y, x + cardW, y + cardH, border);
        int bg = selected ? 0xFF231C33 : entry.unlocked ? BOX : 0xFF131118;
        gui.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, bg);

        SkillClass clazz = SkillClass.byName(entry.id);
        // Icon box.
        gui.fill(x + 5, y + 5, x + 27, y + 27, LINE);
        gui.fill(x + 6, y + 6, x + 26, y + 26, BOX_DARK);
        if (entry.unlocked) {
            gui.item(new ItemStack(item(icon(clazz))), x + 8, y + 8);
        } else {
            padlock(gui, x + 10, y + 9, 0xFF5A5468);
        }
        String name = clazz == null ? entry.id : Component.translatable(clazz.translationKey()).getString();
        int textX = x + 31;
        int textW = x + cardW - 4 - textX;
        gui.text(font, font.plainSubstrByWidth(name, textW), textX, y + 7, entry.unlocked ? WHITE : GREY, true);
        badge(gui, font, entry, textX, y + 18);

        int ty = y + 31;
        String way;
        if (!entry.unlocked) {
            way = Component.translatable("nightsiege.pick.noway").getString();
        } else if (entry.way == null || entry.way.isBlank()) {
            way = Component.translatable("nightsiege.pick.noway").getString();
        } else {
            way = Component.translatable("nightsiege.path." + entry.way).getString();
            if (entry.subclass != null && !entry.subclass.isBlank()) {
                way += " · " + Component.translatable("nightsiege.subclass." + entry.subclass).getString();
            }
        }
        ty = wrapped(gui, font, way, x + 5, ty, cardW - 10, entry.unlocked ? LIGHT : GREY, 0.75f, 2);

        // Points and the bar.
        int barColor = entry.complete ? GOLD : entry.leveling ? 0xFF4CC35A : 0xFF6FA8FF;
        if (entry.unlocked) {
            String points = entry.total > 0
                    ? Component.translatable("nightsiege.pick.points_of", entry.spent, entry.total).getString()
                    : Component.translatable("nightsiege.pick.points", entry.spent).getString();
            small(gui, font, points, x + 5, ty + 2, GREY, 0.75f);
        }
        int barY = ty + 11;
        gui.fill(x + 5, barY, x + cardW - 5, barY + 5, LINE);
        gui.fill(x + 6, barY + 1, x + cardW - 6, barY + 4, BOX_DARK);
        if (entry.unlocked && entry.total > 0) {
            int full = cardW - 12;
            int fill = entry.complete ? full : Math.min(full, full * entry.spent / Math.max(1, entry.total));
            gui.fill(x + 6, barY + 1, x + 6 + fill, barY + 4, barColor);
        }

        // What it means.
        String text = description(entry);
        wrapped(gui, font, text, x + 5, barY + 9, cardW - 10, entry.unlocked ? 0xFFB8B0C8 : 0xFF6F687E, 0.75f,
                Math.max(1, (y + cardH - 4 - (barY + 9)) / 7));
    }

    private String description(ClassPickData.Entry entry) {
        SkillClass leveling = SkillClass.byName(data.leveling);
        Component lev = leveling == null ? Component.literal("?") : Component.translatable(leveling.translationKey());
        if (!entry.unlocked) {
            return Component.translatable(data.nextOpen ? "nightsiege.pick.desc_locked_open" : "nightsiege.pick.desc_locked", lev)
                    .getString();
        }
        if (entry.complete) {
            return Component.translatable("nightsiege.pick.desc_done").getString();
        }
        if (entry.leveling) {
            return Component.translatable("nightsiege.pick.desc_leveling").getString();
        }
        return Component.translatable("nightsiege.pick.desc_open", lev).getString();
    }

    private void badge(GuiGraphicsExtractor gui, Font font, ClassPickData.Entry entry, int x, int y) {
        String key;
        int fg;
        int bg;
        if (!entry.unlocked) {
            key = "nightsiege.pick.locked";
            fg = GREY;
            bg = 0xFF24222A;
        } else if (entry.complete) {
            key = "nightsiege.pick.done";
            fg = GOLD;
            bg = 0xFF3A3010;
        } else if (entry.leveling) {
            key = "nightsiege.pick.leveling";
            fg = GREEN;
            bg = 0xFF12301A;
        } else {
            key = "nightsiege.pick.open";
            fg = 0xFF9CC4FF;
            bg = 0xFF162236;
        }
        String text = Component.translatable(key).getString();
        int tw = (int) Math.ceil(font.width(text) * 0.7f);
        gui.fill(x, y, x + tw + 6, y + 9, bg);
        small(gui, font, text, x + 3, y + 2, fg, 0.7f);
    }

    /** The kit of the chosen class: real items, counts and their tooltips. Returns the hovered one. */
    private ItemStack drawKit(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x = cardsX;
        int y = cardsY + cardH + 7;
        int x2 = teamX - 6;
        int y2 = top + h - 8;
        gui.fill(x, y, x2, y2, LINE);
        gui.fill(x + 1, y + 1, x2 - 1, y2 - 1, BOX_DARK);
        SkillClass pick = SkillClass.byName(chosen);
        String title = Component.translatable("nightsiege.pick.kit_title",
                pick == null ? Component.literal("?") : Component.translatable(pick.translationKey())).getString();
        gui.text(font, title, x + 7, y + 6, GOLD, false);
        String safe = Component.translatable("nightsiege.pick.kit_safe").getString();
        int safeW = (int) (font.width(safe) * 0.7f);
        if (x + 7 + font.width(title) + 8 + safeW < x2 - 7) {
            small(gui, font, safe, x2 - 7 - safeW, y + 8, GREY, 0.7f);
        }
        List<ItemStack> items = kits.getOrDefault(chosen, List.of());
        if (items.isEmpty()) {
            small(gui, font, Component.translatable("nightsiege.pick.kit_none").getString(), x + 7, y + 22, GREY, 0.75f);
            return ItemStack.EMPTY;
        }
        int slot = 20;
        int gap = 3;
        int perRow = Math.max(1, (x2 - x - 12 + gap) / (slot + gap));
        int sx = x + 7;
        int sy = y + 19;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < items.size(); i++) {
            int col = i % perRow;
            int row = i / perRow;
            int px = sx + col * (slot + gap);
            int py = sy + row * (slot + gap);
            if (py + slot > y2 - 4) {
                break;
            }
            boolean over = mouseX >= px && mouseX < px + slot && mouseY >= py && mouseY < py + slot;
            gui.fill(px, py, px + slot, py + slot, over ? 0xFF6A5F80 : LINE);
            gui.fill(px + 1, py + 1, px + slot - 1, py + slot - 1, over ? 0xFF2A2338 : BOX);
            ItemStack stack = items.get(i);
            gui.item(stack, px + 2, py + 2);
            gui.itemDecorations(font, stack, px + 2, py + 2);
            if (over) {
                hovered = stack;
            }
        }
        return hovered;
    }

    private void drawTeam(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        int x = teamX;
        int y = cardsY;
        int x2 = teamX + teamW;
        int y2 = top + h - 8;
        gui.fill(x, y, x2, y2, LINE);
        gui.fill(x + 1, y + 1, x2 - 1, y2 - 1, BOX_DARK);
        gui.text(font, font.plainSubstrByWidth(Component.translatable("nightsiege.pick.team").getString(), teamW - 12),
                x + 6, y + 6, GOLD, false);
        String sub = data.test ? Component.translatable("nightsiege.pick.test_team").getString()
                : Component.translatable("nightsiege.pick.accepts", data.accepts, data.required).getString();
        small(gui, font, sub, x + 6, y + 17, GREY, 0.7f);

        int my = y + 27;
        int readyTop = y2 - 8 - 26 - 22;
        for (ClassPickData.Mate mate : data.team) {
            if (my + 22 > readyTop) {
                break;
            }
            gui.fill(x + 5, my, x2 - 5, my + 21, LINE);
            gui.fill(x + 6, my + 1, x2 - 6, my + 20, BOX);
            boolean ready = mate.ready;
            boolean self = mate.self;
            SkillClass c = SkillClass.byName(self ? chosen : mate.play);
            String cls = c == null ? "-" : Component.translatable(c.translationKey()).getString();
            int dot = self || ready ? 0xFF4CC35A : ORANGE;
            gui.fill(x + 10, my + 5, x + 14, my + 9, dot);
            String name = self ? Component.translatable("nightsiege.pick.you").getString() : mate.name;
            gui.text(font, font.plainSubstrByWidth(name, teamW - 30), x + 18, my + 3, WHITE, false);
            String state = ready || self ? Component.translatable("nightsiege.pick.mate_ready", cls).getString()
                    : Component.translatable("nightsiege.pick.mate_picking").getString();
            small(gui, font, font.plainSubstrByWidth(state, (int) ((teamW - 30) / 0.7f)), x + 18, my + 13,
                    ready || self ? GREEN : ORANGE, 0.7f);
            my += 24;
        }

        // The rule and the ready button.
        wrapped(gui, font, Component.translatable("nightsiege.pick.hint").getString(), x + 6, readyTop + 2, teamW - 12,
                GREY, 0.7f, 3);
        int bx = teamX + 6;
        int by = top + h - 8 - 26;
        int bw = teamW - 12;
        boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 20;
        gui.fill(bx, by, bx + bw, by + 20, 0xFF8A6A20);
        gui.fill(bx + 1, by + 1, bx + bw - 1, by + 19, over ? 0xFFFFE48A : GOLD);
        gui.fill(bx + 1, by + 18, bx + bw - 1, by + 19, 0xFFC9A440);
        SkillClass pick = SkillClass.byName(chosen);
        String label = Component.translatable("nightsiege.pick.ready",
                pick == null ? Component.literal("?") : Component.translatable(pick.translationKey())).getString();
        label = font.plainSubstrByWidth(label, bw - 6);
        gui.text(font, label, bx + (bw - font.width(label)) / 2, by + 6, 0xFF2A2010, false);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    /** Wrapped small text; returns the y below it. */
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

    /** A small padlock for locked classes. */
    private static void padlock(GuiGraphicsExtractor gui, int x, int y, int color) {
        gui.fill(x + 3, y, x + 9, y + 2, color);
        gui.fill(x + 2, y + 1, x + 4, y + 7, color);
        gui.fill(x + 8, y + 1, x + 10, y + 7, color);
        gui.fill(x, y + 6, x + 12, y + 15, color);
        gui.fill(x + 5, y + 9, x + 7, y + 13, 0xFF1A1622);
    }

    private static String icon(SkillClass clazz) {
        if (clazz == null) {
            return "minecraft:barrier";
        }
        return switch (clazz) {
            case WARRIOR -> "minecraft:iron_sword";
            case ARCHER -> "minecraft:bow";
            case GUARDIAN -> "minecraft:shield";
            default -> "nightsiege:arrow_turret";
        };
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("chest")) : item;
    }
}
