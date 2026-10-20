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

    public static void update(UltState fresh) {
        if (fresh != null && (state == null || !fresh.icon.equals(state.icon))) {
            icon = iconOf(fresh.icon);
        }
        state = fresh;
        receivedAt = System.currentTimeMillis();
    }

    public static void draw(GuiGraphicsExtractor gui, KeyMapping key) {
        UltState s = state;
        if (s == null || !s.has) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = width / 2 + 91;
        int y = height - 23;

        gui.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, 29, 24);
        int ix = x + 10;
        int iy = height - 19;
        gui.item(icon, ix, iy);

        long elapsedTicks = (System.currentTimeMillis() - receivedAt) / 50L;
        long left = Math.max(0L, s.cooldownTicks - elapsedTicks);
        if (!s.live) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0xA0000000);
        } else if (left > 0 && s.totalTicks > 0) {
            // Dark shade shrinking from the top, like an item cooldown.
            int shade = (int) Math.ceil(16.0 * left / s.totalTicks);
            gui.fill(ix, iy + 16 - shade, ix + 16, iy + 16, 0xB0000000);
            String seconds = String.valueOf((left + 19) / 20);
            gui.text(font, seconds, ix + 8 - font.width(seconds) / 2, iy + 4, 0xFFFFFFFF, true);
        }

        // The key to press, above the slot.
        Component label = key == null ? Component.literal("?") : key.getTranslatedKeyMessage();
        int color = !s.live ? 0xFF808080 : left > 0 ? 0xFFAAAAAA : 0xFFFFD84A;
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
