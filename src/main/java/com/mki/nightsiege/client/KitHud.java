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

    /** Panel height without the arches (v2, 29 October 2026: 10 % smaller). */
    static final int H = 41;
    private static final int ARCH_H = 12;
    private static final int ARCH_W = 80;
    private static final int WING_W = 64;
    private static final int CREST_W = 28;
    private static final int CREST_H = 38;
    private static final int BIG = 20;
    private static final int BIG_STEP = 22;
    private static final int SMALL = 13;
    private static final int SMALL_STEP = 14;
    /** Symbol 7, gap 2, bar 56. */
    private static final int BARS_W = 65;
    private static final int BAR_W = 56;
    /** Five slots of 18 side by side (the stack counts must not run into the next slot). */
    private static final int HOT_W = 90;
    private static final int HOT_STEP = 18;
    private static final int WING_SLOT = 11;

    private static final Identifier PANEL_MID = tex("panel_mid");
    private static final Identifier PANEL_LEFT = tex("panel_left");
    private static final Identifier PANEL_RIGHT = tex("panel_right");
    private static final Identifier PANEL_ARCH = tex("panel_arch");
    private static final Identifier PANEL_WING = tex("panel_wing");
    private static final Identifier SLOT_WING = tex("slot_wing");
    private static final Identifier ICON_CLASS = tex("icon_class");
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

    /** The action bar's last text (v2: shown in the middle arch) and until when. */
    private static Component message;
    private static long messageUntil;

    /**
     * Every action bar text (Hud.setOverlayMessage). The steady lines (souls, spell) have their
     * place in the bar now and are left out.
     */
    public static void overlayMessage(Component text) {
        if (text == null) {
            return;
        }
        if (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            String key = t.getKey();
            if (key.equals("nightsiege.msg.souls") || key.equals("nightsiege.msg.spell_hud")) {
                return;
            }
        }
        message = text;
        messageUntil = System.currentTimeMillis() + 3000L;
    }

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

    private KitHud() {
    }

    static Identifier tex(String name) {
        return ModContent.id("textures/gui/kit_hud/" + name + ".png");
    }

    /** End of every client tick: is the kit worn? */
    public static void tick(Minecraft mc) {
        worn = wearsKit(mc.player);
        if (!worn) {
            trail = -1f;
        }
        com.mki.nightsiege.core.KitView.hideCrafting = worn
                && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
        // The chat's last line ends 40 px above the bottom; the arches stand over the panel's top.
        return Math.max(0, (height - 40) - lastTop + 2);
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
        int x = 6;
        int crestX = x;
        x += CREST_W + 3;
        int bigW = big.isEmpty() ? 0 : big.size() * BIG_STEP - 2;
        int smallW = small.isEmpty() ? 0 : small.size() * SMALL_STEP - 1;
        int abilW = Math.max(bigW, smallW);
        int abilX = x;
        if (abilW > 0) {
            x += abilW + 6;
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

        gui.pose().pushMatrix();
        gui.pose().translate(left, top);
        if (scale < 1f) {
            gui.pose().scale(scale, scale);
        }
        int leftWing = Math.max(WING_W / 2 + 2, (crestX + abilX + Math.max(abilW, 0)) / 2);
        int rightWing = Math.min(w - WING_W / 2 - 2, hotX + HOT_W / 2);
        panel(gui, w, leftWing, rightWing);
        drawCrest(gui, crestX, H - CREST_H - 2, s);
        if (abilW > 0) {
            int bx = abilX + (abilW - bigW) / 2;
            int by = small.isEmpty() ? (H - BIG) / 2 : 4;
            for (UltHud.Cell c : big) {
                ability(gui, mc.font, bx, by, BIG, c, live);
                bx += BIG_STEP;
            }
            int cx = abilX + (abilW - smallW) / 2;
            int cy = big.isEmpty() ? (H - SMALL) / 2 : 25;
            for (UltHud.Cell c : small) {
                ability(gui, mc.font, cx, cy, SMALL, c, live);
                cx += SMALL_STEP;
            }
        }
        bars(gui, mc.font, p, barsX, s);
        hotbar(gui, mc.font, p, hotX);
        units(gui, mc.font, leftWing, s);
        effects(gui, mc.font, p, rightWing);
        message(gui, mc.font, w / 2);
        gui.pose().popMatrix();
    }

    static void blit(GuiGraphicsExtractor gui, Identifier id, int x, int y, int w, int h) {
        gui.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
    }

    private static void panel(GuiGraphicsExtractor gui, int w, int leftWing, int rightWing) {
        blit(gui, PANEL_WING, leftWing - WING_W / 2, -ARCH_H + 1, WING_W, ARCH_H);
        blit(gui, PANEL_WING, rightWing - WING_W / 2, -ARCH_H + 1, WING_W, ARCH_H);
        blit(gui, PANEL_ARCH, w / 2 - ARCH_W / 2, -ARCH_H + 1, ARCH_W, ARCH_H);
        blit(gui, PANEL_LEFT, 0, 0, 12, H);
        for (int x = 12; x < w - 12; x += 32) {
            int part = Math.min(32, w - 12 - x);
            gui.blit(RenderPipelines.GUI_TEXTURED, PANEL_MID, x, 0, 0f, 0f, part, H, 32, H);
        }
        blit(gui, PANEL_RIGHT, w - 12, 0, 12, H);
    }

    /** The left wing: companions or towers by kind, with a count and the weakest one's health. */
    private static void units(GuiGraphicsExtractor gui, Font font, int cx, UltState s) {
        if (s == null || s.units == null || s.units.isEmpty()) {
            return;
        }
        int n = Math.min(4, s.units.size());
        int x = cx - (n * (WING_SLOT + 2) - 2) / 2;
        int y = -ARCH_H + 3;
        for (int i = 0; i < n; i++) {
            UltState.Unit u = s.units.get(i);
            blit(gui, SLOT_WING, x, y, WING_SLOT, WING_SLOT);
            gui.pose().pushMatrix();
            gui.pose().translate(x + 1.5f, y + 1.5f);
            gui.pose().scale(0.5f, 0.5f);
            gui.item(UltHud.icon(u.icon), 0, 0);
            gui.pose().popMatrix();
            if (u.health >= 0f) {
                int hw = Math.round((WING_SLOT - 2) * u.health);
                gui.fill(x + 1, y + WING_SLOT - 2, x + WING_SLOT - 1, y + WING_SLOT - 1, 0xFF1A0E0C);
                gui.fill(x + 1, y + WING_SLOT - 2, x + 1 + hw, y + WING_SLOT - 1,
                        u.health > 0.5f ? 0xFF5FC04A : u.health > 0.25f ? 0xFFE0B040 : 0xFFD8443A);
            }
            String count = u.max > 0 ? u.count + "/" + u.max : String.valueOf(u.count);
            small(gui, font, count, x + WING_SLOT - 0.5f - font.width(count) / 2f, y + WING_SLOT - 5.5f, 0xFFF4EBDD);
            x += WING_SLOT + 2;
        }
    }

    /** The right wing: the active effects with their time left (blinking in the last ten seconds). */
    private static void effects(GuiGraphicsExtractor gui, Font font, LocalPlayer p, int cx) {
        List<net.minecraft.world.effect.MobEffectInstance> list = new java.util.ArrayList<>(p.getActiveEffects());
        list.removeIf(e -> !e.showIcon());
        if (list.isEmpty()) {
            return;
        }
        list.sort((a, b) -> Integer.compare(a.getDuration(), b.getDuration()));
        int n = Math.min(4, list.size());
        int x = cx - (n * (WING_SLOT + 2) - 2) / 2;
        int y = -ARCH_H + 3;
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            net.minecraft.world.effect.MobEffectInstance e = list.get(i);
            blit(gui, e.getEffect().value().isBeneficial() ? SLOT_WING : SLOT_CHOICE, x, y, WING_SLOT, WING_SLOT);
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
            if (!ending || (now / 250) % 2 == 0) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(e.getEffect()),
                        x + 1, y + 1, 9, 9);
            }
            if (!e.isInfiniteDuration()) {
                int sec = e.getDuration() / 20;
                String t = sec >= 60 ? (sec / 60) + "m" : sec + "s";
                small(gui, font, t, x + WING_SLOT - 0.5f - font.width(t) / 2f, y + WING_SLOT - 5.5f, 0xFFF4EBDD);
            }
            x += WING_SLOT + 2;
        }
    }

    /** The middle arch: the action bar's text, fading out. */
    private static void message(GuiGraphicsExtractor gui, Font font, int cx) {
        long now = System.currentTimeMillis();
        if (message == null || now > messageUntil) {
            return;
        }
        float fade = Math.min(1f, (messageUntil - now) / 500f);
        int alpha = Math.max(16, Math.round(255 * fade));
        int width = font.width(message);
        float scale = Math.max(0.5f, Math.min(0.75f, (ARCH_W - 14) / (float) Math.max(1, width)));
        float shown = width * scale;
        if (shown > ARCH_W - 10) {
            // too long for the arch: a dark plate behind the words
            int half = Math.round(shown / 2f) + 4;
            gui.fill(cx - half, -ARCH_H - 1, cx + half, -ARCH_H + 6, (Math.round(0xC0 * fade) << 24) | 0x100C0A);
        }
        gui.pose().pushMatrix();
        gui.pose().translate(cx - shown / 2f, -ARCH_H + 5.5f - 4f * scale);
        gui.pose().scale(scale, scale);
        gui.text(font, message, 0, 0, (alpha << 24) | 0xF4E6C8, true);
        gui.pose().popMatrix();
    }

    /**
     * The crest v2 (28 x 38, Simon 29 October 2026): the class's colour, a division per subclass, the
     * emblem of the class or subclass in the middle and an ornament on top.
     */
    static void drawCrest(GuiGraphicsExtractor gui, int x, int y, UltState s) {
        String clazz = s == null || s.clazz == null ? "" : s.clazz;
        String sub = s == null || s.sub == null ? "" : s.sub;
        String name = switch (clazz) {
            case "warrior", "archer", "guardian", "engineer", "summoner" ->
                    !sub.isEmpty() && com.mki.nightsiege.core.KitWeapons.of(sub) != null ? sub : clazz;
            default -> "none";
        };
        blit(gui, CRESTS.computeIfAbsent(name, n -> tex("crest_" + n)), x, y, CREST_W, CREST_H);
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

    private static void bars(GuiGraphicsExtractor gui, Font font, LocalPlayer p, int x, UltState s) {
        int bx = x + 9;
        // Health, with what the shield of absorption adds in gold.
        float hp = Math.max(0f, p.getHealth());
        float max = Math.max(1f, p.getMaxHealth());
        float absorb = Math.max(0f, p.getAbsorptionAmount());
        float total = Math.max(max, hp + absorb);
        boolean gauge = s != null && s.gauge && s.gaugeMax > 0f;
        // three rows of 7 under the health bar; without a gauge the rows spread out
        int hy = gauge ? 4 : 6;
        int r1 = 15;
        int r2 = gauge ? 23 : 20;
        int r3 = gauge ? 31 : 29;
        blit(gui, ICON_HEART, x, hy + 1, 7, 7);
        blit(gui, BAR_HEALTH, bx, hy, BAR_W, 10);
        int inner = BAR_W - 4;
        int ix = bx + 2;
        int iy = hy + 2;
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
        float step = inner * 2f / total;
        int per = step >= 4f ? 2 : 10;
        for (int v = per; v < total; v += per) {
            int nx = ix + Math.round(inner * v / total);
            gui.fill(nx, iy + 3, nx + 1, iy + 6, 0x60000000);
        }
        String hpText = (int) Math.ceil(hp) + (absorb > 0.5f ? "+" + (int) Math.ceil(absorb) : "") + "/" + (int) Math.ceil(max);
        small(gui, font, hpText, ix + inner / 2f - font.width(hpText) / 4f, iy + 1, 0xFFF4EBDD);

        // The class's gauge (souls, remote energy, shield energy).
        if (gauge) {
            int c = s.gaugeColor | 0xFF000000;
            int lightC = 0xFF000000 | Math.min(0xFF, ((c >> 16) & 0xFF) + 50) << 16
                    | Math.min(0xFF, ((c >> 8) & 0xFF) + 50) << 8 | Math.min(0xFF, (c & 0xFF) + 50);
            String text = s.gaugeMax == 100f && s.gaugeName.endsWith("shield")
                    ? Math.round(s.gaugeValue) + "%"
                    : (int) Math.floor(s.gaugeValue) + "/" + Math.round(s.gaugeMax);
            blit(gui, ICON_CLASS, x, r1, 7, 7);
            smallBar(gui, font, bx, r1, s.gaugeValue / s.gaugeMax, c, lightC, text, -1f);
        }

        // Armour, or the air while under water.
        int air = p.getAirSupply();
        int maxAir = Math.max(1, p.getMaxAirSupply());
        boolean airShown = p.isUnderWater() || air < maxAir;
        if (airShown) {
            blit(gui, ICON_AIR, x, r2, 7, 7);
            smallBar(gui, font, bx, r2, Math.max(0, air) / (float) maxAir, 0xFF3F7FB8, 0xFF8CC8EE,
                    String.valueOf((Math.max(0, air) + 19) / 20) + "s", -1f);
        } else {
            int armor = p.getArmorValue();
            blit(gui, ICON_ARMOR, x, r2, 7, 7);
            smallBar(gui, font, bx, r2, Math.min(1f, armor / 20f), 0xFF5E6B7A, 0xFF98A6B6, String.valueOf(armor), -1f);
        }

        // Hunger, with the saturation as a lighter line on top (like AppleSkin, which only draws on the vanilla row).
        int food = p.getFoodData().getFoodLevel();
        float sat = p.getFoodData().getSaturationLevel();
        boolean hungry = p.hasEffect(MobEffects.HUNGER);
        blit(gui, ICON_FOOD, x, r3, 7, 7);
        smallBar(gui, font, bx, r3, food / 20f, hungry ? 0xFF667A2A : 0xFFB0702A, hungry ? 0xFF8EA040 : 0xFFDDA050,
                String.valueOf(food), Math.min(1f, sat / 20f));
    }

    /** A bar of 7: inner 52 x 3, value text on the right. */
    private static void smallBar(GuiGraphicsExtractor gui, Font font, int x, int y, float share, int body, int light,
                                 String text, float extra) {
        blit(gui, BAR_SMALL, x, y, BAR_W, 7);
        int inner = BAR_W - 4;
        int w = Math.round(inner * Math.max(0f, Math.min(1f, share)));
        bar(gui, x + 2, y + 2, w, 3, body, light);
        if (extra > 0f) {
            int sw = Math.round(inner * extra);
            gui.fill(x + 2, y + 2, x + 2 + sw, y + 3, 0xFFFFE08A);
        }
        for (int v = 2; v < 20; v += 2) {
            int nx = x + 2 + Math.round(inner * v / 20f);
            gui.fill(nx, y + 4, nx + 1, y + 5, 0x50000000);
        }
        small(gui, font, text, x + BAR_W - 3 - font.width(text) / 2f, y + 1.5f, 0xFFF4EBDD);
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
        blit(gui, SLOT_OFFHAND, x + 4 * HOT_STEP, 21, 18, 18);
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
            gui.item(off, x + 4 * HOT_STEP + 1, 22);
            gui.itemDecorations(font, off, x + 4 * HOT_STEP + 1, 22);
        }
    }

    private static int slotX(int x, int i) {
        return x + (i < 5 ? i : i - 5) * HOT_STEP;
    }

    private static int slotY(int i) {
        return i < 5 ? 4 : 21;
    }
}
