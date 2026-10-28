package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.ModContent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The kit HUD (Simon, 28 October 2026): while the night's kit is worn, a dark iron panel with gold
 * trim replaces the hotbar, the hearts, the armour and the hunger row. Laid out like League of
 * Legends: the class crest, then the abilities (Q, F, X big, the choices on R, G, Y, C, V, B small
 * below), the bars in the middle and the hotbar on the right in two rows (1-5, 6-9 and the off hand).
 * Only abilities the player has learned get a box; a class without any shows no ability section.
 *
 * <p>One texel of the textures (tools/hud/kit_hud.py) is one GUI pixel. The panel sits centred at
 * the bottom and shrinks on narrow screens. Chat, the item name and the action bar move up when they
 * would run into it; Jade, the voice chat icons and the mods on the right keep their places.
 */
public final class KitHud {

    /** Panel height without the arch. */
    static final int H = 46;
    private static final int ARCH_H = 9;
    private static final int CREST_W = 26;
    private static final int BIG = 22;
    private static final int BIG_STEP = 24;
    private static final int SMALL = 14;
    private static final int SMALL_STEP = 15;
    /** Symbol 7, gap 2, bar 62. */
    private static final int BARS_W = 71;
    private static final int BAR_W = 62;
    /** Five slots of 18 side by side (the stack counts must not run into the next slot). */
    private static final int HOT_W = 90;
    private static final int HOT_STEP = 18;

    private static final Identifier PANEL_MID = tex("panel_mid");
    private static final Identifier PANEL_LEFT = tex("panel_left");
    private static final Identifier PANEL_RIGHT = tex("panel_right");
    private static final Identifier PANEL_ARCH = tex("panel_arch");
    private static final Identifier SLOT_ABILITY = tex("slot_ability");
    private static final Identifier SLOT_ABILITY_READY = tex("slot_ability_ready");
    private static final Identifier SLOT_CHOICE = tex("slot_choice");
    private static final Identifier SLOT_CHOICE_CURRENT = tex("slot_choice_current");
    private static final Identifier SLOT_ITEM = tex("slot_item");
    private static final Identifier SLOT_ITEM_SELECTED = tex("slot_item_selected");
    private static final Identifier SLOT_OFFHAND = tex("slot_offhand");
    private static final Identifier BAR_HEALTH = tex("bar_health");
    private static final Identifier BAR_SMALL = tex("bar_small");
    private static final Identifier ICON_HEART = tex("icon_heart");
    private static final Identifier ICON_ARMOR = tex("icon_armor");
    private static final Identifier ICON_FOOD = tex("icon_food");
    private static final Identifier ICON_AIR = tex("icon_air");
    private static final java.util.Map<String, Identifier> CRESTS = new java.util.HashMap<>();

    /** True while some kit item is in the inventory (checked once a tick). */
    private static boolean worn;
    /** The red that is lost shows a moment longer, then runs down (like in League). */
    private static float trail = -1f;
    private static float lastHp = -1f;
    private static long holdUntil;
    private static long lastFrame;
    /** Where the panel was drawn last (screen GUI pixels), for moving the chat out of the way. */
    private static int lastLeft = Integer.MAX_VALUE;
    private static int lastTop = Integer.MAX_VALUE;
    private static int lastArchLeft = Integer.MAX_VALUE;
    private static float lastScale = 1f;

    private KitHud() {
    }

    private static Identifier tex(String name) {
        return ModContent.id("textures/gui/kit_hud/" + name + ".png");
    }

    /** End of every client tick: is the kit worn? */
    public static void tick(Minecraft mc) {
        worn = wearsKit(mc.player);
        if (!worn) {
            trail = -1f;
        }
    }

    private static boolean wearsKit(LocalPlayer p) {
        if (p == null) {
            return false;
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (KitService.isKit(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** True while the kit HUD takes the place of the vanilla hotbar and bars. */
    public static boolean active() {
        if (!worn) {
            return false;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !p.isSpectator() && !DroneClient.localDrone();
    }

    /** For the tests. */
    public static boolean worn() {
        return worn;
    }

    // ------------------------------------------------------------------
    // other HUD parts out of the way
    // ------------------------------------------------------------------

    /** How far the chat goes up: only when the kit HUD is on and the chat reaches over it. */
    public static int chatLift() {
        if (!active()) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            return 0;   // open chat: the clicks on its lines must stay where they are drawn
        }
        int height = mc.getWindow().getGuiScaledHeight();
        double scale = mc.options.chatScale().get();
        int right = 4 + (int) Math.ceil((Math.floor(mc.options.chatWidth().get() * 280.0 + 40.0) + 8) * scale);
        if (right <= lastLeft) {
            return 0;
        }
        // The chat's last line ends 40 px above the bottom; over the arch it must clear that too.
        int top = right > lastArchLeft ? lastTop : lastTop + Math.round(ARCH_H * lastScale);
        return Math.max(0, (height - 40) - top + 2);
    }

    /** How far the name of the held item and the action bar go up. */
    public static int overlayLift() {
        if (!active()) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        int height = mc.getWindow().getGuiScaledHeight();
        int tooltip = height - 59 + (mc.player != null && mc.player.isCreative() ? 14 : 0);
        int archTop = lastTop - ARCH_H;
        return Math.max(0, tooltip + 10 - archTop);
    }

    /** How far the subtitles (bottom right) go up. */
    public static int subtitleLift() {
        if (!active()) {
            return 0;
        }
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(0, (height - 35) - lastTop + 12);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    public static void draw(GuiGraphicsExtractor gui, KeyMapping ultKey) {
        if (!active()) {
            lastLeft = Integer.MAX_VALUE;
            lastTop = Integer.MAX_VALUE;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        UltState s = UltHud.state();
        List<UltHud.Cell> big = s == null ? List.of() : UltHud.fixedCells(s, ultKey);
        List<UltHud.Cell> small = s == null ? List.of() : UltHud.choiceCells(s, mc);
        boolean live = s != null && s.live;

        // The layout from left to right; sections without boxes take no room.
        int x = 8;
        int crestX = x;
        x += CREST_W + 4;
        int bigW = big.isEmpty() ? 0 : big.size() * BIG_STEP - 2;
        int smallW = small.isEmpty() ? 0 : small.size() * SMALL_STEP - 1;
        int abilW = Math.max(bigW, smallW);
        int abilX = x;
        if (abilW > 0) {
            x += abilW + 7;
        }
        int barsX = x;
        x += BARS_W + 5;
        int hotX = x;
        x += HOT_W;
        int w = x + 5;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float scale = Math.min(1f, (sw - 4) / (float) w);
        int shownW = Math.round(w * scale);
        int left = (sw - shownW) / 2;
        int top = sh - Math.round(H * scale);
        lastLeft = left;
        lastTop = top - Math.round(ARCH_H * scale);
        lastArchLeft = left + Math.round((w / 2f - 28) * scale);
        lastScale = scale;

        gui.pose().pushMatrix();
        gui.pose().translate(left, top);
        if (scale < 1f) {
            gui.pose().scale(scale, scale);
        }
        panel(gui, w);
        crest(gui, crestX, s);
        if (abilW > 0) {
            int bx = abilX + (abilW - bigW) / 2;
            int by = small.isEmpty() ? 5 + (39 - BIG) / 2 : 7;
            for (UltHud.Cell c : big) {
                ability(gui, mc.font, bx, by, BIG, c, live);
                bx += BIG_STEP;
            }
            int cx = abilX + (abilW - smallW) / 2;
            int cy = big.isEmpty() ? 5 + (39 - SMALL) / 2 : 30;
            for (UltHud.Cell c : small) {
                ability(gui, mc.font, cx, cy, SMALL, c, live);
                cx += SMALL_STEP;
            }
        }
        bars(gui, mc.font, p, barsX);
        hotbar(gui, mc.font, p, hotX);
        gui.pose().popMatrix();
    }

    private static void blit(GuiGraphicsExtractor gui, Identifier id, int x, int y, int w, int h) {
        gui.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
    }

    private static void panel(GuiGraphicsExtractor gui, int w) {
        blit(gui, PANEL_LEFT, 0, 0, 12, H);
        for (int x = 12; x < w - 12; x += 32) {
            int part = Math.min(32, w - 12 - x);
            gui.blit(RenderPipelines.GUI_TEXTURED, PANEL_MID, x, 0, 0f, 0f, part, H, 32, H);
        }
        blit(gui, PANEL_RIGHT, w - 12, 0, 12, H);
        blit(gui, PANEL_ARCH, w / 2 - 28, -ARCH_H, 56, 10);
    }

    private static void crest(GuiGraphicsExtractor gui, int x, UltState s) {
        String clazz = s == null || s.clazz == null ? "" : s.clazz;
        ItemStack icon = switch (clazz) {
            case "warrior" -> new ItemStack(Items.IRON_SWORD);
            case "archer" -> new ItemStack(Items.BOW);
            case "guardian" -> new ItemStack(Items.SHIELD);
            case "engineer" -> UltHud.icon("nightsiege:arrow_turret");
            case "summoner" -> ModContent.STAFF != null ? new ItemStack(ModContent.STAFF) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
        String name = switch (clazz) {
            case "warrior", "archer", "guardian", "engineer", "summoner" -> clazz;
            default -> "none";
        };
        blit(gui, CRESTS.computeIfAbsent(name, n -> tex("crest_" + n)), x, 9, CREST_W, 30);
        if (!icon.isEmpty()) {
            gui.item(icon, x + 5, 9 + 6);
        }
    }

    /** One ability box: big (22, Q F X) or small (14, the choices). */
    private static void ability(GuiGraphicsExtractor gui, Font font, int x, int y, int size, UltHud.Cell c, boolean live) {
        long left = Math.max(0L, c.cooldown() - UltHud.elapsedTicks());
        boolean bigBox = size == BIG;
        boolean gold = bigBox ? live && c.total() > 0 && left == 0 : c.current();
        Identifier frame = bigBox ? (gold ? SLOT_ABILITY_READY : SLOT_ABILITY) : (gold ? SLOT_CHOICE_CURRENT : SLOT_CHOICE);
        blit(gui, frame, x, y, size, size);
        int icon = bigBox ? 16 : 10;
        int ix = x + (size - icon) / 2;
        int iy = y + (size - icon) / 2;
        if (bigBox) {
            gui.item(c.icon(), ix, iy);
        } else {
            gui.pose().pushMatrix();
            gui.pose().translate(ix, iy);
            gui.pose().scale(icon / 16f, icon / 16f);
            gui.item(c.icon(), 0, 0);
            gui.pose().popMatrix();
        }
        if (!live) {
            gui.fill(ix, iy, ix + icon, iy + icon, 0xA0000000);
        } else if (left > 0 && c.total() > 0) {
            int shade = (int) Math.ceil(icon * Math.min(1.0, left / (double) c.total()));
            gui.fill(ix, iy + icon - shade, ix + icon, iy + icon, 0xB0000000);
            String seconds = String.valueOf((left + 19) / 20);
            if (bigBox) {
                gui.text(font, seconds, ix + 8 - font.width(seconds) / 2, iy + 4, 0xFFFFFFFF, true);
            } else {
                small(gui, font, seconds, ix + icon / 2f - font.width(seconds) / 4f, iy + 3, 0xFFFFFFFF);
            }
        }
        Component label = c.key() == null ? Component.literal("?") : c.key().getTranslatedKeyMessage();
        String text = label.getString();
        if (text.length() > 3) {
            text = text.substring(0, 3);
        }
        int color = !live ? 0xFF9A9080 : left > 0 ? 0xFFB8AC98 : 0xFFF0D27A;
        small(gui, font, text, x + size - 2.5f - font.width(text) / 2f, y + size - 6.5f, color);
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, float x, float y, int color) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(0.5f, 0.5f);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }

    // ------------------------------------------------------------------
    // bars
    // ------------------------------------------------------------------

    private static void bars(GuiGraphicsExtractor gui, Font font, LocalPlayer p, int x) {
        int bx = x + 9;
        // Health, with what the shield of absorption adds in gold.
        float hp = Math.max(0f, p.getHealth());
        float max = Math.max(1f, p.getMaxHealth());
        float absorb = Math.max(0f, p.getAbsorptionAmount());
        float total = Math.max(max, hp + absorb);
        blit(gui, ICON_HEART, x, 10, 7, 7);
        blit(gui, BAR_HEALTH, bx, 9, BAR_W, 10);
        int inner = BAR_W - 4;
        int ix = bx + 2;
        int iy = 9 + 2;
        long now = System.currentTimeMillis();
        if (trail < hp) {
            trail = hp;
        }
        if (hp < lastHp - 0.01f) {
            holdUntil = now + 450;
        }
        if (now > holdUntil && trail > hp) {
            float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
            trail = Math.max(hp, trail - dt * max * 0.8f);
        }
        lastHp = hp;
        lastFrame = now;
        int hpW = Math.round(inner * hp / total);
        int trailW = Math.round(inner * Math.min(trail, total) / total);
        int absW = Math.round(inner * (hp + absorb) / total) - hpW;
        if (trailW > hpW) {
            gui.fill(ix + hpW, iy, ix + trailW, iy + 6, 0xFFE8C9A0);
        }
        int body;
        int light;
        if (p.hasEffect(MobEffects.WITHER)) {
            body = 0xFF3B3532;
            light = 0xFF5E5650;
        } else if (p.hasEffect(MobEffects.POISON)) {
            body = 0xFF6A8A22;
            light = 0xFF94B43C;
        } else if (p.isFullyFrozen()) {
            body = 0xFF5F98BE;
            light = 0xFF9CCDE8;
        } else {
            body = 0xFFA8261F;
            light = 0xFFD8443A;
        }
        if (hp <= max * 0.2f && (now / 300) % 2 == 0) {
            light = 0xFFFF6A5A;   // low: the top line flickers
        }
        bar(gui, ix, iy, hpW, 6, body, light);
        if (absW > 0) {
            bar(gui, ix + hpW, iy, absW, 6, 0xFFC99A2E, 0xFFF0CC5A);
        }
        // A thin notch for every heart (two points), or for every five when that gets too tight.
        float step = inner * 2f / total;
        int per = step >= 4f ? 2 : 10;
        for (int v = per; v < total; v += per) {
            int nx = ix + Math.round(inner * v / total);
            gui.fill(nx, iy + 3, nx + 1, iy + 6, 0x60000000);
        }
        String hpText = (int) Math.ceil(hp) + (absorb > 0.5f ? "+" + (int) Math.ceil(absorb) : "") + "/" + (int) Math.ceil(max);
        small(gui, font, hpText, ix + inner / 2f - font.width(hpText) / 4f, iy + 1, 0xFFF4EBDD);

        // Armour, or the air while under water.
        int air = p.getAirSupply();
        int maxAir = Math.max(1, p.getMaxAirSupply());
        boolean airShown = p.isUnderWater() || air < maxAir;
        int ay = 22;
        if (airShown) {
            blit(gui, ICON_AIR, x, ay, 7, 7);
            smallBar(gui, font, bx, ay, Math.max(0, air) / (float) maxAir, 0xFF3F7FB8, 0xFF8CC8EE,
                    String.valueOf((Math.max(0, air) + 19) / 20) + "s", -1f);
        } else {
            int armor = p.getArmorValue();
            blit(gui, ICON_ARMOR, x, ay, 7, 7);
            smallBar(gui, font, bx, ay, Math.min(1f, armor / 20f), 0xFF5E6B7A, 0xFF98A6B6, String.valueOf(armor), -1f);
        }

        // Hunger, with the saturation as a lighter line on top (like AppleSkin, which only draws on the vanilla row).
        int food = p.getFoodData().getFoodLevel();
        float sat = p.getFoodData().getSaturationLevel();
        boolean hungry = p.hasEffect(MobEffects.HUNGER);
        blit(gui, ICON_FOOD, x, 33, 7, 7);
        smallBar(gui, font, bx, 33, food / 20f, hungry ? 0xFF667A2A : 0xFFB0702A, hungry ? 0xFF8EA040 : 0xFFDDA050,
                String.valueOf(food), Math.min(1f, sat / 20f));
    }

    /** A bar of 8: inner 58 x 4, value text on the right. */
    private static void smallBar(GuiGraphicsExtractor gui, Font font, int x, int y, float share, int body, int light,
                                 String text, float extra) {
        blit(gui, BAR_SMALL, x, y, BAR_W, 8);
        int inner = BAR_W - 4;
        int w = Math.round(inner * Math.max(0f, Math.min(1f, share)));
        bar(gui, x + 2, y + 2, w, 4, body, light);
        if (extra > 0f) {
            int sw = Math.round(inner * extra);
            gui.fill(x + 2, y + 2, x + 2 + sw, y + 3, 0xFFFFE08A);
        }
        for (int v = 2; v < 20; v += 2) {
            int nx = x + 2 + Math.round(inner * v / 20f);
            gui.fill(nx, y + 4, nx + 1, y + 6, 0x50000000);
        }
        small(gui, font, text, x + BAR_W - 3 - font.width(text) / 2f, y + 2, 0xFFF4EBDD);
    }

    private static void bar(GuiGraphicsExtractor gui, int x, int y, int w, int h, int body, int light) {
        if (w <= 0) {
            return;
        }
        gui.fill(x, y, x + w, y + h, body);
        gui.fill(x, y, x + w, y + 1, light);
        gui.fill(x, y + h - 1, x + w, y + h, 0x40000000);
    }

    // ------------------------------------------------------------------
    // hotbar
    // ------------------------------------------------------------------

    private static void hotbar(GuiGraphicsExtractor gui, Font font, LocalPlayer p, int x) {
        Inventory inv = p.getInventory();
        int selected = inv.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (i != selected) {
                blit(gui, SLOT_ITEM, slotX(x, i), slotY(i), 18, 18);
            }
        }
        blit(gui, SLOT_OFFHAND, x + 4 * HOT_STEP, 24, 18, 18);
        if (selected >= 0 && selected < 9) {
            blit(gui, SLOT_ITEM_SELECTED, slotX(x, selected), slotY(selected), 18, 18);
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                gui.item(stack, slotX(x, i) + 1, slotY(i) + 1);
                gui.itemDecorations(font, stack, slotX(x, i) + 1, slotY(i) + 1);
            }
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            gui.item(off, x + 4 * HOT_STEP + 1, 25);
            gui.itemDecorations(font, off, x + 4 * HOT_STEP + 1, 25);
        }
    }

    private static int slotX(int x, int i) {
        return x + (i < 5 ? i : i - 5) * HOT_STEP;
    }

    private static int slotY(int i) {
        return i < 5 ? 6 : 24;
    }
}
