package com.mki.nightsiege.client;

import com.mki.nightsiege.core.ClassPickData;
import com.mki.nightsiege.core.SkillClass;
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

import java.util.Locale;

/**
 * Before a night (at the horn, or in a test's preparation): which of the
 * unlocked classes the player defends with tonight. The kit follows the choice.
 */
public class ClassPickScreen extends Screen {

    private int W = 470;
    private static final int H = 236;
    /** Card width: four cards and the team column share the window. */
    private int CARD_W = 82;
    private static final int TEAM_W = 74;
    private static final int CARD_H = 122;

    private final ClassPickData data;
    private final long opened = System.currentTimeMillis();
    private String chosen;
    private int left;
    private int top;

    public ClassPickScreen(ClassPickData data) {
        super(Component.translatable("nightsiege.pick.title"));
        this.data = data;
        this.chosen = data.play == null ? "" : data.play;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        W = Math.max(330, Math.min(470, this.width - 10));
        CARD_W = Math.max(56, (W - 24 - TEAM_W - 4 * 6) / 4);
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        for (int i = 0; i < data.classes.size(); i++) {
            ClassPickData.Entry entry = data.classes.get(i);
            Button card = Button.builder(Component.empty(), b -> {
                chosen = entry.id;
                rebuildWidgets();
            }).bounds(cardX(i), top + 46, CARD_W, CARD_H).build();
            card.active = entry.unlocked;
            card.setAlpha(0f);
            addRenderableWidget(card);
        }
        SkillClass pick = SkillClass.byName(chosen);
        Component ready = Component.translatable("nightsiege.pick.ready",
                pick == null ? Component.literal("?") : Component.translatable(pick.translationKey()));
        addRenderableWidget(Button.builder(ready, b -> {
            if (pick != null) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("play:" + pick.name()));
            }
            onClose();
        }).bounds(left + W - 148, top + H - 28, 136, 20).build());
    }

    private int cardX(int index) {
        return left + 12 + index * (CARD_W + 6);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, 0x90000000);
        int x2 = left + W;
        int y2 = top + H;
        gui.fill(left - 1, top - 1, x2 + 1, y2 + 1, 0xFF3A2E4E);
        gui.fill(left, top, x2, y2, 0xFF000000);
        gui.fill(left + 1, top + 1, x2 - 1, y2 - 1, 0xF014101C);
        gui.fill(left + 1, top + 1, x2 - 1, top + 2, 0xFF2E2640);

        gui.text(font, Component.translatable("nightsiege.pick.horn", data.level).getString(), left + 12, top + 9, 0xFFE05A5A, false);
        gui.pose().pushMatrix();
        gui.pose().translate(left + 12, top + 21);
        gui.pose().scale(1.3f, 1.3f);
        gui.text(font, Component.translatable("nightsiege.pick.title").getString(), 0, 0, 0xFFFFD86B, true);
        gui.pose().popMatrix();
        int secs = Math.max(0, data.seconds - (int) ((System.currentTimeMillis() - opened) / 1000L));
        String timer = Component.translatable("nightsiege.pick.timer", secs / 60, String.format(Locale.ROOT, "%02d", secs % 60)).getString();
        gui.text(font, timer, x2 - 12 - font.width(timer), top + 9, 0xFFFFFFFF, true);

        for (int i = 0; i < data.classes.size(); i++) {
            drawCard(gui, font, data.classes.get(i), cardX(i), top + 46, mouseX, mouseY);
        }

        // Team on the right.
        int tx = cardX(data.classes.size()) + 4;
        int tw = x2 - 12 - tx;
        gui.fill(tx, top + 46, tx + tw, top + 46 + CARD_H, 0xFF0E0B14);
        gui.text(font, Component.translatable("nightsiege.pick.team").getString(), tx + 6, top + 51, 0xFFFFD86B, false);
        int ty = top + 64;
        for (ClassPickData.Mate mate : data.team) {
            SkillClass c = SkillClass.byName(mate.play);
            gui.text(font, font.plainSubstrByWidth(mate.name, tw - 10), tx + 6, ty, 0xFFFFFFFF, false);
            gui.text(font, c == null ? "-" : Component.translatable(c.translationKey()).getString(), tx + 6, ty + 10,
                    0xFF8FE39A, false);
            ty += 24;
            if (ty > top + 46 + CARD_H - 20) {
                break;
            }
        }

        // The kit of tonight.
        String armor = Component.translatable("nightsiege.pick.armor." + data.armor.toLowerCase(Locale.ROOT)).getString();
        String kit = Component.translatable("nightsiege.pick.kit", armor, roman(data.protection), roman(data.sharpness)).getString();
        gui.text(font, kit, left + 12, y2 - 22, 0xFFCFC7DD, false);
        gui.text(font, Component.translatable("nightsiege.pick.hint").getString(), left + 12, y2 - 12, 0xFF8F879F, false);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawCard(GuiGraphicsExtractor gui, Font font, ClassPickData.Entry entry, int x, int y, int mouseX, int mouseY) {
        boolean selected = entry.id.equals(chosen);
        boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        int border = selected ? 0xFFFFD86B : hover && entry.unlocked ? 0xFF8A7A50 : 0xFF000000;
        gui.fill(x, y, x + CARD_W, y + CARD_H, border);
        gui.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, selected ? 0xFF221A30 : entry.unlocked ? 0xFF1B1624 : 0xFF110E17);
        SkillClass clazz = SkillClass.byName(entry.id);
        gui.pose().pushMatrix();
        gui.pose().translate(x + 8, y + 8);
        gui.pose().scale(2f, 2f);
        gui.item(new ItemStack(item(entry.unlocked ? icon(clazz) : "minecraft:barrier")), 0, 0);
        gui.pose().popMatrix();
        String tag = entry.unlocked ? (entry.complete ? "nightsiege.pick.done" : entry.leveling ? "nightsiege.pick.leveling" : "nightsiege.pick.open")
                : "nightsiege.pick.locked";
        int tagColor = !entry.unlocked ? 0xFF8F879F : entry.complete ? 0xFFFFD86B : 0xFF8FE39A;
        String name = clazz == null ? entry.id : Component.translatable(clazz.translationKey()).getString();
        gui.text(font, font.plainSubstrByWidth(name, CARD_W - 10), x + 6, y + 46, entry.unlocked ? 0xFFFFFFFF : 0xFF8F879F, true);
        gui.text(font, font.plainSubstrByWidth(Component.translatable(tag).getString(), CARD_W - 10), x + 6, y + 58, tagColor, false);
        if (entry.unlocked) {
            String way = entry.way == null || entry.way.isBlank() ? Component.translatable("nightsiege.pick.noway").getString()
                    : Component.translatable("nightsiege.path." + entry.way).getString();
            gui.text(font, font.plainSubstrByWidth(way, CARD_W - 10), x + 6, y + 74, 0xFFB8B0C8, false);
            if (entry.subclass != null && !entry.subclass.isBlank()) {
                gui.text(font, font.plainSubstrByWidth(Component.translatable("nightsiege.subclass." + entry.subclass).getString(),
                        CARD_W - 10), x + 6, y + 85, 0xFFB8B0C8, false);
            }
            gui.text(font, Component.translatable("nightsiege.pick.points", entry.spent).getString(), x + 6, y + CARD_H - 14,
                    0xFFA49CB4, false);
        }
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

    private static String roman(int n) {
        String[] r = {"0", "I", "II", "III", "IV", "V", "VI"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
