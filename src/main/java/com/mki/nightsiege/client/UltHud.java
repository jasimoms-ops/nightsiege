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
 * The ability bar left of the off-hand slot (Simon, 27 October 2026, instead of the wheel):
 * the ultimate (Q), Fähigkeit 2 (F), the second ultimate (X), then every spell, potion,
 * healing ability or arrow kind on a key of its own (R, G, Y, C, V, B). Each slot shows its
 * key above it and its cooldown running down over it; the arrow kind in use has a gold frame.
 */
public final class UltHud {

    private static final int CELL = 22;
    private static final int GAP = 5;

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

    private record Cell(ItemStack icon, KeyMapping key, int cooldown, int total, boolean current) {
    }

    public static void draw(GuiGraphicsExtractor gui, KeyMapping ultKey) {
        UltState s = state;
        if (s == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Cell> fixed = new ArrayList<>();
        if (s.has) {
            fixed.add(new Cell(icon(s.icon), ultKey, s.cooldownTicks, s.totalTicks, false));
        }
        if (s.hasAb2) {
            fixed.add(new Cell(icon(s.iconAb2), GainClient.orderKey(), 0, 0, false));
        }
        if (s.has2) {
            fixed.add(new Cell(icon(s.icon2), GainClient.secondUltKey(), s.cooldown2Ticks, s.total2Ticks, false));
        }
        List<Cell> choices = new ArrayList<>();
        if (s.slots != null) {
            for (int i = 0; i < s.slots.size() && i < GainClient.SLOTS; i++) {
                UltState.Slot slot = s.slots.get(i);
                choices.add(new Cell(iconOf(mc, slot), GainClient.slotKey(i), slot.cooldownTicks, slot.totalTicks, slot.current));
            }
        }
        if (fixed.isEmpty() && choices.isEmpty()) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        // Right edge: just left of the off-hand slot (it sits 29 px left of the hotbar).
        int right = width / 2 - 91 - 29 - 3;
        int total = (fixed.size() + choices.size()) * CELL + (!fixed.isEmpty() && !choices.isEmpty() ? GAP : 0);
        int x = Math.max(2, right - total);
        int y = height - 22;
        for (Cell c : fixed) {
            cell(gui, mc, x, y, c, s.live);
            x += CELL;
        }
        if (!fixed.isEmpty() && !choices.isEmpty()) {
            x += GAP;
        }
        for (Cell c : choices) {
            cell(gui, mc, x, y, c, s.live);
            x += CELL;
        }
    }

    private static void cell(GuiGraphicsExtractor gui, Minecraft mc, int x, int y, Cell c, boolean live) {
        Font font = mc.font;
        int edge = c.current() ? PurpleUi.GOLD : PurpleUi.FRAME_HI;
        gui.fill(x, y, x + CELL - 1, y + CELL - 1, edge);
        gui.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, 0xD0121019);
        int ix = x + 2;
        int iy = y + 2;
        gui.item(c.icon(), ix, iy);
        long elapsedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        long left = Math.max(0L, c.cooldown() - elapsedTicks);
        if (!live) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0xA0000000);
        } else if (left > 0 && c.total() > 0) {
            int shade = (int) Math.ceil(16.0 * Math.min(1.0, left / (double) c.total()));
            gui.fill(ix, iy + 16 - shade, ix + 16, iy + 16, 0xB0000000);
            String seconds = String.valueOf((left + 19) / 20);
            gui.text(font, seconds, ix + 8 - font.width(seconds) / 2, iy + 4, 0xFFFFFFFF, true);
        }
        Component label = c.key() == null ? Component.literal("?") : c.key().getTranslatedKeyMessage();
        String text = label.getString();
        if (text.length() > 3) {
            text = text.substring(0, 3);
        }
        int color = !live ? 0xFF808080 : left > 0 ? 0xFFAAAAAA : 0xFFFFD84A;
        gui.text(font, text, x + CELL / 2 - font.width(text) / 2, y - 9, color, true);
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
