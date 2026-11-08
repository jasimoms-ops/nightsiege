package com.mki.nightsiege.client;

import com.mki.nightsiege.core.HudSync;
import com.mki.nightsiege.game.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tor 2 (Simon's play test 08.11., the medic: "Anzeige aller Mitspieler mit Leben"): a compact list of the
 * other defenders with their life, for the healers, at the right edge of the screen, a little above the
 * middle. Left stay chat and Simple Voice Chat, the top stays Jade's. Dark plate, bronze trim, the kit HUD's
 * bar colours. The server sends the list every half second only to healers (HealthView.tickParty).
 */
public final class PartyHealthHud {

    private static final int W = 78;
    private static final int ROW = 11;
    private static final int PLATE = 0xC0100C0A;
    private static final int TRIM = 0xFF5A4630;
    private static final int GOLD = 0xFFC9A45A;
    private static final net.minecraft.resources.Identifier HEART = KitHud.tex("icon_heart");

    private static List<HudSync.Mate> mates = List.of();

    private PartyHealthHud() {
    }

    /** The server's list (JSON of {@link HudSync.Party}, empty for nothing). */
    public static void set(String json) {
        if (json == null || json.isBlank()) {
            mates = List.of();
            return;
        }
        try {
            HudSync.Party party = Json.fromJson(json, HudSync.Party.class);
            mates = party == null || party.mates == null ? List.of() : List.copyOf(party.mates);
        } catch (RuntimeException e) {
            mates = List.of();
        }
    }

    public static void clear() {
        mates = List.of();
    }

    public static void draw(GuiGraphicsExtractor gui) {
        List<HudSync.Mate> list = mates;
        Minecraft mc = Minecraft.getInstance();
        if (list.isEmpty() || mc.player == null) {
            return;
        }
        Font font = mc.font;
        int n = Math.min(HudSync.PARTY_MAX, list.size());
        int h = 9 + n * ROW + 2;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw - W - 3;
        int y = Math.max(24, sh / 2 - h / 2 - 20);
        // the plate with a bronze trim and gold corner studs
        gui.fill(x, y, x + W, y + h, PLATE);
        gui.fill(x, y, x + W, y + 1, TRIM);
        gui.fill(x, y + h - 1, x + W, y + h, TRIM);
        gui.fill(x, y, x + 1, y + h, TRIM);
        gui.fill(x + W - 1, y, x + W, y + h, TRIM);
        for (int[] c : new int[][]{{x, y}, {x + W - 2, y}, {x, y + h - 2}, {x + W - 2, y + h - 2}}) {
            gui.fill(c[0], c[1], c[0] + 2, c[1] + 2, GOLD);
        }
        String title = Component.translatable("nightsiege.hud.party.title").getString();
        small(gui, font, title, x + W / 2f - font.width(title) / 4f, y + 2.5f, GOLD);
        gui.fill(x + 4, y + 7, x + W - 4, y + 8, TRIM);
        for (int i = 0; i < n; i++) {
            HudSync.Mate m = list.get(i);
            int ry = y + 9 + i * ROW;
            float share = m.share();
            // Tor 3.5 (Simon 08.11.): name and hearts in the colour of the state (green, yellow, red, grey: fallen).
            HudSync.State state = HudSync.state(m);
            boolean down = state == HudSync.State.DOWN;
            int color = HudSync.stateColor(state);
            String name = m.name == null ? "?" : m.name;
            if (font.width(name) / 2 > 40) {
                name = name.substring(0, Math.min(name.length(), 13)) + "…";
            }
            small(gui, font, name, x + 4, ry + 0.5f, color);
            String hp = down ? Component.translatable("nightsiege.hud.party.down").getString()
                    : HudSync.hearts(m.health);
            float hx = x + W - 4 - font.width(hp) / 2f;
            small(gui, font, hp, hx, ry + 0.5f, color);
            if (!down) {
                KitHud.blit(gui, HEART, Math.round(hx) - 6, ry, 5, 5);
            }
            // the bar: dark well, life, gold for barriers
            int bx = x + 4;
            int bw = W - 8;
            int by = ry + 6;
            gui.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF1A1410);
            int[] col = HudSync.barColors(share);
            int lw = Math.round(bw * share);
            if (lw > 0) {
                gui.fill(bx, by, bx + lw, by + 3, col[0]);
                gui.fill(bx, by, bx + lw, by + 1, col[1]);
            }
            if (m.absorb > 0.5f && m.max > 0f) {
                int aw = Math.min(bw - lw, Math.round(bw * m.absorb / m.max));
                if (aw > 0) {
                    gui.fill(bx + lw, by, bx + lw + aw, by + 3, 0xFFC99A2E);
                    gui.fill(bx + lw, by, bx + lw + aw, by + 1, 0xFFF0CC5A);
                }
            }
        }
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, float x, float y, int color) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(0.5f, 0.5f);
        gui.text(font, text, 0, 0, color, true);
        gui.pose().popMatrix();
    }
}
