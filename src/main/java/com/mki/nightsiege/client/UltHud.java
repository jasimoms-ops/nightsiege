package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The ultimate's slot right of the hotbar, in the frame of the off-hand slot:
 * the path's icon, the cooldown running down over it, and the key above it.
 */
public final class UltHud {

    private static final Identifier FRAME = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");

    private static UltState state;
    private static long receivedAt;
    private static ItemStack icon = ItemStack.EMPTY;

    private UltHud() {
    }

    private static ItemStack icon2 = ItemStack.EMPTY;

    public static void update(UltState fresh) {
        if (fresh != null && (state == null || !fresh.icon.equals(state.icon))) {
            icon = iconOf(fresh.icon);
        }
        if (fresh != null && (state == null || !fresh.icon2.equals(state.icon2))) {
            icon2 = iconOf(fresh.icon2);
        }
        state = fresh;
        receivedAt = System.currentTimeMillis();
    }

    public static void draw(GuiGraphicsExtractor gui, KeyMapping key) {
        UltState s = state;
        if (s == null || (!s.has && !s.has2)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int x = width / 2 + 91;
        if (s.has) {
            slot(gui, mc, x, icon, s.cooldownTicks, s.totalTicks, s.live, key);
            x += 29;
        }
        // The second ultimate from the crate, in a slot of its own (Simon's list, 27 October 2026).
        if (s.has2) {
            slot(gui, mc, x, icon2, s.cooldown2Ticks, s.total2Ticks, s.live, GainClient.secondUltKey());
        }
    }

    private static void slot(GuiGraphicsExtractor gui, Minecraft mc, int x, ItemStack item, int cooldown, int total,
                             boolean live, KeyMapping key) {
        Font font = mc.font;
        int height = mc.getWindow().getGuiScaledHeight();
        int y = height - 23;
        gui.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, 29, 24);
        int ix = x + 10;
        int iy = height - 19;
        gui.item(item, ix, iy);

        long elapsedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        long left = Math.max(0L, cooldown - elapsedTicks);
        if (!live) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0xA0000000);
        } else if (left > 0 && total > 0) {
            // Dark shade shrinking from the top, like an item cooldown.
            int shade = (int) Math.ceil(16.0 * Math.min(1.0, left / (double) total));
            gui.fill(ix, iy + 16 - shade, ix + 16, iy + 16, 0xB0000000);
            String seconds = String.valueOf((left + 19) / 20);
            gui.text(font, seconds, ix + 8 - font.width(seconds) / 2, iy + 4, 0xFFFFFFFF, true);
        }

        // The key to press, above the slot.
        Component label = key == null ? Component.literal("?") : key.getTranslatedKeyMessage();
        int color = !live ? 0xFF808080 : left > 0 ? 0xFFAAAAAA : 0xFFFFD84A;
        gui.text(font, label, ix + 8 - font.width(label) / 2, y - 10, color, true);
    }

    private static ItemStack iconOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
