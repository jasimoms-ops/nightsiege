package com.mki.nightsiege.client;

import com.mki.nightsiege.core.WheelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * The wheel of Fähigkeit 3 (Simon, 27 October 2026, like GTA's weapon wheel): the choices sit
 * on a ring around the middle of the screen; the mouse points at one, letting go of the key
 * picks it. Same look as the bird's-eye view's bar: dark panels, thin frames, gold for the
 * choice under the mouse, the current one marked.
 */
public class WheelScreen extends Screen {

    private static final int GOLD = 0xFFFFD86B;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int GREEN = 0xFF7CD67C;
    private static final int RED = 0xFFE05A5A;
    private static final int PANEL = 0xD8121019;
    private static final int LINE = 0xFF2C2740;
    private static final int SLOT = 26;
    /** Pixels from the middle before the mouse points at a choice. */
    private static final int DEAD_ZONE = 16;

    private final WheelData data;
    private int hovered = -1;

    public WheelScreen(WheelData data) {
        super(Component.translatable(data.title.isEmpty() ? "nightsiege.wheel.title" : data.title));
        this.data = data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No blur: the fight goes on behind the wheel. */
    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, this.width, this.height, 0x50000000);
    }

    private int radius() {
        int n = Math.max(1, data.options.size());
        return Math.max(58, (int) Math.ceil((SLOT + 10) * n / (2 * Math.PI)));
    }

    private int[] slotCenter(int i) {
        int n = Math.max(1, data.options.size());
        double a = -Math.PI / 2 + 2 * Math.PI * i / n;
        int r = radius();
        return new int[]{this.width / 2 + (int) Math.round(Math.cos(a) * r), this.height / 2 + (int) Math.round(Math.sin(a) * r)};
    }

    /** The choice the mouse points at: the nearest by angle once outside the middle. */
    private int pointedAt(double mouseX, double mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE || data.options.isEmpty()) {
            return -1;
        }
        int n = data.options.size();
        double a = Math.atan2(dy, dx) + Math.PI / 2;   // 0 = up, clockwise (screen y points down)
        a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return (int) Math.round(a / (2 * Math.PI / n)) % n;
    }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            onClose();
            return;
        }
        if (!WheelClient.physicallyDown(client, GainClient.quiverKey())) {
            double mx = client.mouseHandler.getScaledXPos(client.getWindow());
            double my = client.mouseHandler.getScaledYPos(client.getWindow());
            pick(pointedAt(mx, my));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        pick(pointedAt(event.x(), event.y()));
        return true;
    }

    private void pick(int index) {
        if (index >= 0 && index < data.options.size()) {
            WheelData.Option o = data.options.get(index);
            if (o.open && !o.current) {
                WheelClient.send("wheel:pick:" + o.id);
            }
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        hovered = pointedAt(mouseX, mouseY);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int r = radius();

        // The ring: a dotted circle, gold towards the choice under the mouse.
        int n = Math.max(1, data.options.size());
        int dots = 96;
        for (int k = 0; k < dots; k++) {
            double a = -Math.PI / 2 + 2 * Math.PI * k / dots;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            boolean near = hovered >= 0 && Math.abs(angleDiff(a + Math.PI / 2, 2 * Math.PI * hovered / n)) < Math.PI / n;
            gui.fill(px - 1, py - 1, px + 1, py + 1, near ? GOLD : LINE);
        }
        // A pointer from the middle to the choice.
        if (hovered >= 0) {
            int[] c = slotCenter(hovered);
            for (int k = 3; k <= 10; k++) {
                int px = cx + (c[0] - cx) * k / 13;
                int py = cy + (c[1] - cy) * k / 13;
                gui.fill(px - 1, py - 1, px + 1, py + 1, GOLD);
            }
        }

        // The choices.
        for (int i = 0; i < data.options.size(); i++) {
            WheelData.Option o = data.options.get(i);
            int[] c = slotCenter(i);
            int x0 = c[0] - SLOT / 2;
            int y0 = c[1] - SLOT / 2;
            boolean over = i == hovered;
            int frame = over ? GOLD : o.current ? 0xFF7C6A3A : LINE;
            gui.fill(x0 - 2, y0 - 2, x0 + SLOT + 2, y0 + SLOT + 2, frame);
            gui.fill(x0, y0, x0 + SLOT, y0 + SLOT, o.open ? (over ? 0xF0221C30 : PANEL) : 0xD00C0B10);
            ItemStack icon = iconOf(client, o);
            if (!icon.isEmpty()) {
                gui.item(icon, c[0] - 8, c[1] - 8);
            }
            if (!o.open) {
                gui.fill(x0, y0, x0 + SLOT, y0 + SLOT, 0x90000000);
            }
            if (o.current) {
                gui.fill(x0 + SLOT - 6, y0 + 2, x0 + SLOT - 2, y0 + 6, GOLD);   // in use now
            }
        }

        // The middle: title, the choice under the mouse and its line.
        String title = this.title.getString();
        WheelData.Option shown = hovered >= 0 ? data.options.get(hovered) : current();
        String name = shown == null ? "" : nameOf(client, shown);
        String sub = shown == null ? "" : subOf(shown);
        int w = Math.max(font.width(title), Math.max(font.width(name), (int) (font.width(sub) * 0.75f))) + 14;
        w = Math.min(w, r * 2 - SLOT - 8);
        int h = 36;
        gui.fill(cx - w / 2 - 1, cy - h / 2 - 1, cx + w / 2 + 1, cy + h / 2 + 1, LINE);
        gui.fill(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, PANEL);
        small(gui, font, font.plainSubstrByWidth(title, (int) ((w - 6) / 0.75f)), cx, cy - 14, GOLD, 0.75f);
        int nameColor = shown == null ? GREY : !shown.open ? GREY : 0xFFFFFFFF;
        small(gui, font, font.plainSubstrByWidth(name, w - 6), cx, cy - 4, nameColor, 1.0f);
        int subColor = shown == null ? GREY : !shown.open ? GREY : shown.sub.endsWith(".wait") ? RED : GREEN;
        small(gui, font, font.plainSubstrByWidth(sub, (int) ((w - 6) / 0.7f)), cx, cy + 8, subColor, 0.7f);

        // Under the ring: what the pick is for, and how to leave.
        String hint = data.hint.isEmpty() ? "" : Component.translatable(data.hint).getString();
        String foot = Component.translatable("nightsiege.wheel.foot",
                GainClient.quiverKey().getTranslatedKeyMessage()).getString();
        int by = cy + r + SLOT / 2 + 8;
        if (!hint.isEmpty()) {
            int hw = (int) (font.width(hint) * 0.75f) + 10;
            gui.fill(cx - hw / 2, by - 2, cx + hw / 2, by + 9, PANEL);
            small(gui, font, hint, cx, by, LIGHT, 0.75f);
            by += 13;
        }
        small(gui, font, foot, cx, by, GREY, 0.7f);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private WheelData.Option current() {
        for (WheelData.Option o : data.options) {
            if (o.current) {
                return o;
            }
        }
        return null;
    }

    private static double angleDiff(double a, double b) {
        double d = (a - b) % (2 * Math.PI);
        if (d > Math.PI) {
            d -= 2 * Math.PI;
        } else if (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        return d;
    }

    private static ItemStack iconOf(Minecraft client, WheelData.Option o) {
        if (o.slot >= 0 && client.player != null) {
            ItemStack stack = client.player.getInventory().getItem(o.slot);
            if (!stack.isEmpty()) {
                return stack.copyWithCount(1);
            }
        }
        if (o.color >= 0) {
            ItemStack stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(o.color),
                    List.of(), Optional.empty()));
            return stack;
        }
        Identifier id = Identifier.tryParse(o.icon == null ? "" : o.icon);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String nameOf(Minecraft client, WheelData.Option o) {
        if (!o.name.isEmpty()) {
            return Component.translatable(o.name).getString();
        }
        if (o.slot >= 0 && client.player != null) {
            return client.player.getInventory().getItem(o.slot).getHoverName().getString();
        }
        return o.id;
    }

    private static String subOf(WheelData.Option o) {
        if (o.sub.isEmpty()) {
            return "";
        }
        return Component.translatable(o.sub, o.subArgs.toArray()).getString();
    }

    /** Centred text at a scale. */
    private static void small(GuiGraphicsExtractor gui, Font font, String text, int cx, int y, int color, float scale) {
        if (text.isEmpty()) {
            return;
        }
        gui.pose().pushMatrix();
        gui.pose().translate(cx - font.width(text) * scale / 2f, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }
}
