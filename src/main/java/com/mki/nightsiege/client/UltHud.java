package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import net.minecraft.client.KeyMapping;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ability bar left of the off-hand slot (Simon, 27 October 2026, instead of the wheel): the
 * ultimate (Q), Fähigkeit 2 (F), the second ultimate (X), then every spell, potion, healing
 * ability or arrow kind on a key of its own (R, G, Y, C, V, B). Since the hotfix of 20:48 a list,
 * one line each – key, symbol, name, the seconds still to wait – so eight abilities no longer run
 * into the hotbar and every symbol says what it is. The arrow kind in use has a gold frame.
 */
public final class UltHud {

    private static final int ROW = 14;
    private static final int ICON = 12;
    private static final float TEXT = 0.75f;

    private static UltState state;
    private static long receivedAt;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private UltHud() {
    }

    public static void update(UltState fresh) {
        state = fresh;
        receivedAt = System.currentTimeMillis();
    }

    /** For the tests: what the bar shows right now. */
    public static UltState state() {
        return state;
    }

    private record Cell(ItemStack icon, KeyMapping key, String name, int cooldown, int total, boolean current) {
    }

    public static void draw(GuiGraphicsExtractor gui, KeyMapping ultKey) {
        UltState s = state;
        if (s == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<Cell> cells = new ArrayList<>();
        if (s.has) {
            cells.add(new Cell(icon(s.icon), ultKey, text("nightsiege.skill." + s.node), s.cooldownTicks, s.totalTicks, false));
        }
        if (s.hasAb2) {
            cells.add(new Cell(icon(s.iconAb2), GainClient.orderKey(), text(s.nameAb2), 0, 0, false));
        }
        if (s.has2) {
            cells.add(new Cell(icon(s.icon2), GainClient.secondUltKey(), text(s.name2), s.cooldown2Ticks, s.total2Ticks, false));
        }
        if (s.slots != null) {
            for (int i = 0; i < s.slots.size() && i < GainClient.SLOTS; i++) {
                UltState.Slot slot = s.slots.get(i);
                ItemStack icon = iconOf(mc, slot);
                String name = slot.name == null || slot.name.isEmpty() ? icon.getHoverName().getString() : text(slot.name);
                cells.add(new Cell(icon, GainClient.slotKey(i), name, slot.cooldownTicks, slot.totalTicks, slot.current));
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        // Right edge: just left of the off-hand slot (it sits 29 px left of the hotbar).
        int right = width / 2 - 91 - 29 - 4;
        int row = Math.max(11, Math.min(ROW, (height - 70) / cells.size()));
        long elapsedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        int keyW = 10;
        int textX = keyW + ICON + 6;
        int widest = 0;
        for (Cell c : cells) {
            widest = Math.max(widest, (int) Math.ceil(font.width(c.name() + "  99s") * TEXT));
        }
        int blockW = Math.min(right - 2, textX + widest + 4);
        int nameRoom = Math.max(20, blockW - textX - 4);
        int x0 = right - blockW;
        int y0 = height - 3 - cells.size() * row;
        gui.fill(x0 - 2, y0 - 2, right, height - 1, 0x90000000);
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int y = y0 + i * row;
            long left = Math.max(0L, c.cooldown() - elapsedTicks);
            boolean waiting = s.live && left > 0 && c.total() > 0;
            // The key.
            String key = c.key() == null ? "?" : c.key().getTranslatedKeyMessage().getString();
            if (key.length() > 3) {
                key = key.substring(0, 3);
            }
            int keyColor = !s.live ? 0xFF808080 : waiting ? 0xFFAAAAAA : 0xFFFFD84A;
            small(gui, font, key, x0 + keyW / 2 - (int) (font.width(key) * TEXT) / 2, y + (row - 7) / 2, keyColor);
            // The symbol, a gold frame for the arrow kind in use, its cooldown shading.
            int ix = x0 + keyW + 2;
            int iy = y + (row - ICON) / 2;
            if (c.current()) {
                gui.fill(ix - 1, iy - 1, ix + ICON + 1, iy + ICON + 1, PurpleUi.GOLD);
                gui.fill(ix, iy, ix + ICON, iy + ICON, 0xF0121019);
            }
            gui.pose().pushMatrix();
            gui.pose().translate(ix, iy);
            gui.pose().scale(ICON / 16f, ICON / 16f);
            gui.item(c.icon(), 0, 0);
            gui.pose().popMatrix();
            if (!s.live) {
                gui.fill(ix, iy, ix + ICON, iy + ICON, 0xA0000000);
            } else if (waiting) {
                int shade = (int) Math.ceil(ICON * Math.min(1.0, left / (double) c.total()));
                gui.fill(ix, iy + ICON - shade, ix + ICON, iy + ICON, 0xB0000000);
            }
            // The name, and the seconds still to wait.
            String name = font.plainSubstrByWidth(c.name(), (int) (nameRoom / TEXT) - (waiting ? font.width(" 99s") : 0));
            int nameColor = !s.live ? 0xFF808080 : waiting ? 0xFFAAAAAA : c.current() ? PurpleUi.GOLD : 0xFFFFFFFF;
            small(gui, font, name, x0 + textX, y + (row - 7) / 2, nameColor);
            if (waiting) {
                String sec = ((left + 19) / 20) + "s";
                small(gui, font, sec, x0 + textX + (int) Math.ceil(font.width(name + " ") * TEXT), y + (row - 7) / 2, 0xFFFF7070);
            }
        }
    }

    private static String text(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return Component.translatable(key).getString();
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(TEXT, TEXT);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }

    private static ItemStack iconOf(Minecraft mc, UltState.Slot slot) {
        if (slot.invSlot >= 0 && mc.player != null) {
            ItemStack stack = mc.player.getInventory().getItem(slot.invSlot);
            if (!stack.isEmpty()) {
                return stack.copyWithCount(1);
            }
        }
        if (slot.color >= 0) {
            ItemStack stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(slot.color),
                    List.of(), Optional.empty()));
            return stack;
        }
        return icon(slot.icon);
    }

    private static ItemStack icon(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ICONS.computeIfAbsent(itemId, k -> {
            Identifier id = Identifier.tryParse(k);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }
}
