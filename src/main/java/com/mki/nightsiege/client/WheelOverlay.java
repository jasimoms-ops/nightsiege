package com.mki.nightsiege.client;

import com.mki.nightsiege.core.WheelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * picks it. It is drawn over the HUD, not as a screen, so the player keeps walking while it is
 * open (Simon, 27 October 2026); the mouse moves the wheel's pointer instead of the camera
 * (MouseWheelPointerMixin). Same look as the bird's-eye view's bar: dark panels, thin frames,
 * gold for the choice pointed at, the current one marked.
 */
public final class WheelOverlay {

    private static final int GOLD = 0xFFFFD86B;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int GREEN = 0xFF7CD67C;
    private static final int RED = 0xFFE05A5A;
    private static final int PANEL = 0xD8121019;
    private static final int LINE = 0xFF2C2740;
    private static final int SLOT = 26;
    /** Pixels from the middle before the pointer points at a choice. */
    private static final int DEAD_ZONE = 16;

    private static WheelData data;
    /** The pointer, in GUI pixels from the middle of the screen. */
    private static double pointerX;
    private static double pointerY;
    private static int hovered = -1;

    private WheelOverlay() {
    }

    public static boolean isOpen() {
        return data != null;
    }

    static void open(WheelData wheel) {
        data = wheel;
        pointerX = 0;
        pointerY = 0;
        hovered = -1;
    }

    public static void close() {
        data = null;
        hovered = -1;
    }

    /** Mouse movement while the wheel is open (raw window pixels) moves the pointer, not the camera. */
    public static void movePointer(double dx, double dy) {
        if (data == null) {
            return;
        }
        double scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        pointerX += dx / scale;
        pointerY += dy / scale;
        double max = radius() + SLOT;
        double len = Math.hypot(pointerX, pointerY);
        if (len > max) {
            pointerX *= max / len;
            pointerY *= max / len;
        }
    }

    /**
     * Start of every client tick: letting go of the key (or a left click) picks the choice
     * pointed at. Clicks are taken here so a pick does not also swing or use the held item.
     */
    public static void tick(Minecraft client) {
        if (data == null) {
            return;
        }
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            close();
            return;
        }
        boolean clicked = false;
        while (client.options.keyAttack.consumeClick()) {
            clicked = true;
        }
        while (client.options.keyUse.consumeClick()) {
            // nothing: right click does not use the held item while choosing
        }
        if (clicked || !WheelClient.physicallyDown(client, GainClient.quiverKey())) {
            pick(pointedAt());
        }
    }

    /** For the client test: the index pointed at, or -1. */
    public static int pointed() {
        return data == null ? -1 : pointedAt();
    }

    private static int radius() {
        if (data == null) {
            return 58;
        }
        int n = Math.max(1, data.options.size());
        return Math.max(58, (int) Math.ceil((SLOT + 10) * n / (2 * Math.PI)));
    }

    private static int[] slotCenter(int i, int width, int height) {
        int n = Math.max(1, data.options.size());
        double a = -Math.PI / 2 + 2 * Math.PI * i / n;
        int r = radius();
        return new int[]{width / 2 + (int) Math.round(Math.cos(a) * r), height / 2 + (int) Math.round(Math.sin(a) * r)};
    }

    /** The choice the pointer points at: the nearest by angle once outside the middle. */
    private static int pointedAt() {
        double dx = pointerX;
        double dy = pointerY;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE || data.options.isEmpty()) {
            return -1;
        }
        int n = data.options.size();
        double a = Math.atan2(dy, dx) + Math.PI / 2;   // 0 = up, clockwise (screen y points down)
        a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return (int) Math.round(a / (2 * Math.PI / n)) % n;
    }

    private static void pick(int index) {
        if (index >= 0 && index < data.options.size()) {
            WheelData.Option o = data.options.get(index);
            if (o.open && !o.current) {
                WheelClient.send("wheel:pick:" + o.id);
            }
        }
        close();
    }

    /** The HUD layer: drawn while the wheel is open. */
    public static void draw(GuiGraphicsExtractor gui) {
        if (data == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        hovered = pointedAt();
        int cx = width / 2;
        int cy = height / 2;
        int r = radius();
        // A light shade only: the fight goes on and the player may be walking.
        gui.fill(cx - r - SLOT, cy - r - SLOT, cx + r + SLOT, cy + r + SLOT, 0x38000000);

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
            int[] c = slotCenter(hovered, width, height);
            for (int k = 3; k <= 10; k++) {
                int px = cx + (c[0] - cx) * k / 13;
                int py = cy + (c[1] - cy) * k / 13;
                gui.fill(px - 1, py - 1, px + 1, py + 1, GOLD);
            }
        }

        // The choices.
        for (int i = 0; i < data.options.size(); i++) {
            WheelData.Option o = data.options.get(i);
            int[] c = slotCenter(i, width, height);
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
        String title = Component.translatable(data.title.isEmpty() ? "nightsiege.wheel.title" : data.title).getString();
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

        // The pointer: a small gold mark where the mouse points.
        int px = cx + (int) Math.round(pointerX);
        int py = cy + (int) Math.round(pointerY);
        gui.fill(px - 3, py, px + 4, py + 1, GOLD);
        gui.fill(px, py - 3, px + 1, py + 4, GOLD);
    }

    private static WheelData.Option current() {
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
