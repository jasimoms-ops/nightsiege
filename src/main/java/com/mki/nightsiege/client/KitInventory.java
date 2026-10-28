package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * The inventory (E) while the kit is worn (Simon, 28 October 2026, variant B): the slots stay where
 * Minecraft has them, the look is dark iron and gold, and where the 2x2 crafting grid was sits a plate
 * with the subclass crest, the kit's values (max health, armour, attack damage), the class name and the
 * abilities Q, F, X.
 */
public final class KitInventory {

    private static final Identifier BACKGROUND = KitHud.tex("inventory");
    private static final Identifier SLOT = KitHud.tex("slot_item");
    private static final Identifier ICON_HEART = KitHud.tex("icon_heart");
    private static final Identifier ICON_ARMOR = KitHud.tex("icon_armor");
    private static final Identifier ICON_DAMAGE = KitHud.tex("icon_damage");

    private KitInventory() {
    }

    /** In place of the vanilla background (left, top: the panel's corner). */
    public static void drawBackground(GuiGraphicsExtractor gui, int left, int top) {
        KitHud.blit(gui, BACKGROUND, left, top, 176, 166);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        Font font = mc.font;
        UltState s = UltHud.state();
        KitHud.drawCrest(gui, left + 99, top + 8, s);

        // The kit's values right of the crest.
        int vx = left + 131;
        value(gui, font, ICON_HEART, vx, top + 15, fmt(p.getMaxHealth()));
        value(gui, font, ICON_ARMOR, vx, top + 26, String.valueOf(p.getArmorValue()));
        value(gui, font, ICON_DAMAGE, vx, top + 37, fmt((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        // The name of the subclass (or the class) on a plate.
        String sub = s == null || s.sub == null ? "" : s.sub;
        String clazz = s == null || s.clazz == null ? "" : s.clazz;
        Component name = !sub.isEmpty() ? Component.translatable("nightsiege.subclass." + sub)
                : !clazz.isEmpty() ? Component.translatable("nightsiege.class." + clazz)
                : Component.translatable("nightsiege.kithud.kit");
        String text = name.getString();
        int plateX = left + 99;
        int plateW = 68;
        int plateY = top + 47;
        gui.fill(plateX, plateY, plateX + plateW, plateY + 11, 0xFF0A0807);
        gui.fill(plateX + 1, plateY + 1, plateX + plateW - 1, plateY + 10, 0xFF4A3816);
        gui.fill(plateX + 2, plateY + 2, plateX + plateW - 2, plateY + 9, 0xFF1D1813);
        float scale = Math.min(1f, (plateW - 6) / (float) Math.max(1, font.width(text)));
        gui.pose().pushMatrix();
        gui.pose().translate(plateX + plateW / 2f - font.width(text) * scale / 2f, plateY + 6f - 4f * scale);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, 0xFFE8C56B, true);
        gui.pose().popMatrix();

        // The abilities the player has (Q, F, X), with their keys.
        if (s != null) {
            List<UltHud.Cell> cells = UltHud.fixedCells(s, NightSiegeClient.ultimateKey());
            int ax = left + 133 - (cells.size() * 20 - 2) / 2;
            int ay = top + 59;
            for (UltHud.Cell c : cells) {
                KitHud.blit(gui, SLOT, ax, ay, 18, 18);
                gui.item(c.icon(), ax + 1, ay + 1);
                String key = c.key() == null ? "?" : c.key().getTranslatedKeyMessage().getString();
                if (key.length() > 3) {
                    key = key.substring(0, 3);
                }
                gui.pose().pushMatrix();
                gui.pose().translate(ax + 16.5f - font.width(key) / 2f, ay + 12.5f);
                gui.pose().scale(0.5f, 0.5f);
                gui.text(font, key, 0, 0, 0xFFF0D27A, true);
                gui.pose().popMatrix();
                ax += 20;
            }
        }
    }

    private static void value(GuiGraphicsExtractor gui, Font font, Identifier icon, int x, int y, String text) {
        KitHud.blit(gui, icon, x, y, 7, 7);
        gui.text(font, text, x + 10, y, 0xFFF4EBDD, true);
    }

    private static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
