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

    /** Tooltip look: dark iron with a gold edge (sprites tooltip/kit_background, kit_frame). */
    private static final Identifier TIP_STYLE = com.mki.nightsiege.game.ModContent.id("kit");
    /** The ability fields drawn last (x, y of each 18x18 field) and the tooltip each one has. */
    private static final List<int[]> CELL_AT = new java.util.ArrayList<>();
    private static final List<com.mki.nightsiege.core.AbilityTip> CELL_TIP = new java.util.ArrayList<>();
    private static final List<String> CELL_KEY = new java.util.ArrayList<>();

    private KitInventory() {
    }

    /** Centre of the i-th ability field drawn last, in GUI pixels (the gametest points the mouse there); null if none. */
    public static int[] cellCenter(int i) {
        return i < 0 || i >= CELL_AT.size() ? null : new int[]{CELL_AT.get(i)[0] + 9, CELL_AT.get(i)[1] + 9};
    }

    /** The tooltip of the ability under the mouse (Simon 08.11.), drawn on top of everything. */
    public static void drawTips(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        for (int i = 0; i < CELL_AT.size(); i++) {
            int[] at = CELL_AT.get(i);
            com.mki.nightsiege.core.AbilityTip tip = CELL_TIP.get(i);
            if (tip == null || mouseX < at[0] || mouseX >= at[0] + 18 || mouseY < at[1] || mouseY >= at[1] + 18) {
                continue;
            }
            Font font = Minecraft.getInstance().font;
            net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
            List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
            for (String line : AbilityTipText.lines(tip, CELL_KEY.get(i), k -> language.has(k) ? language.getOrDefault(k) : null)) {
                lines.addAll(font.split(Component.literal(line), 220));
            }
            gui.setTooltipForNextFrame(font, lines, mouseX, mouseY, TIP_STYLE);
            return;
        }
    }

    /** The tooltip of a field: Q, F, X from the server; a crate gain (N, M, J) from its own name and text. */
    private static com.mki.nightsiege.core.AbilityTip tipFor(UltState s, UltHud.Cell c, int gainIndex) {
        String slot = c.key() == NightSiegeClient.ultimateKey() ? "q" : c.key() == GainClient.orderKey() ? "f"
                : c.key() == GainClient.secondUltKey() ? "x" : null;
        if (slot != null) {
            if (s.tips != null) {
                for (com.mki.nightsiege.core.AbilityTip t : s.tips) {
                    if (slot.equals(t.slot)) {
                        return t;
                    }
                }
            }
            return null;
        }
        if (s.gains == null || gainIndex < 0 || gainIndex >= s.gains.size()) {
            return null;
        }
        UltState.Slot g = s.gains.get(gainIndex);
        com.mki.nightsiege.core.AbilityTip t = new com.mki.nightsiege.core.AbilityTip();
        t.slot = "gain";
        t.main.name = g.name;
        t.main.desc = g.name + ".desc";
        t.cooldownTicks = g.totalTicks > 0 ? g.totalTicks : AbilityTipText.NO_COOLDOWN_LINE;
        return t;
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
        CELL_AT.clear();
        CELL_TIP.clear();
        CELL_KEY.clear();
        if (s != null) {
            int gainIndex = 0;
            List<UltHud.Cell> cells = UltHud.fixedCells(s, NightSiegeClient.ultimateKey());
            int ax = left + 133 - (cells.size() * 20 - 2) / 2;
            int ay = top + 59;
            for (UltHud.Cell c : cells) {
                KitHud.blit(gui, SLOT, ax, ay, 18, 18);
                gui.item(c.icon(), ax + 1, ay + 1);
                String key = c.key() == null ? "?" : c.key().getTranslatedKeyMessage().getString();
                boolean gain = c.key() != NightSiegeClient.ultimateKey() && c.key() != GainClient.orderKey()
                        && c.key() != GainClient.secondUltKey();
                CELL_AT.add(new int[]{ax, ay});
                CELL_TIP.add(tipFor(s, c, gain ? gainIndex : -1));
                CELL_KEY.add(key);
                if (gain) {
                    gainIndex++;
                }
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
