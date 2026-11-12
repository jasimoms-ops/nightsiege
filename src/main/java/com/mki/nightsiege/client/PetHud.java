package com.mki.nightsiege.client;

import com.mki.nightsiege.core.ActionBarLayout;
import com.mki.nightsiege.core.PetRows;
import com.mki.nightsiege.game.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Paket 11.11. Z2 (Simon 12.11.: "der Wolfbändiger soll jeden Wolf einzeln in der Leiste sehen mit LP-Anzeige"):
 * a row per companion left of the action bar (or of the vanilla hotbar without a kit): its head, a health bar
 * (green, yellow, red) and "14/20"; the alpha with a gold frame, the fallen grey with the seconds until the next
 * call. At most 8 rows, then "+N". The server sends the rows every half second ("pets" screen payload).
 */
public final class PetHud {

    private static final int PLATE = 0xC0100C0A;
    private static final int RIM = 0xFF3A2C1C;
    private static final int GOLD = 0xFFC9A55A;
    private static final int GOLD_HI = 0xFFE8C56B;
    private static final int TEXT = 0xFFF4EBDD;
    private static final int GREY = 0xFF8C8478;

    /** The face on each mob's texture: u, v, size, texture width, texture height. */
    private record Face(Identifier tex, int u, int v, int w, int h, int texW, int texH) {
    }

    private static final Map<String, Face> FACES = Map.of(
            "minecraft:wolf", new Face(Identifier.withDefaultNamespace("textures/entity/wolf/wolf_tame.png"), 4, 4, 6, 6, 64, 32),
            "minecraft:iron_golem", new Face(Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png"), 8, 8, 8, 8, 128, 128),
            "minecraft:skeleton", new Face(Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"), 8, 8, 8, 8, 64, 32),
            "minecraft:snow_golem", new Face(Identifier.withDefaultNamespace("textures/entity/snow_golem/snow_golem.png"), 8, 8, 8, 8, 64, 64),
            "minecraft:zombie", new Face(Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png"), 8, 8, 8, 8, 64, 64));

    private static volatile List<PetRows.Pet> pets = List.of();
    private static volatile long receivedAt;

    /** For the tests and the chat: where the rows were drawn last (screen GUI pixels), empty for none. */
    public static volatile List<ActionBarLayout.Rect> lastRects = List.of();
    /** For the tests: the texts of the rows drawn last, in their order, and the "+N" ("" for none). */
    public static volatile List<String> lastTexts = List.of();
    public static volatile String lastMore = "";

    private PetHud() {
    }

    /** The server's rows (JSON of {@link PetRows.Pets}, empty for none). */
    public static void set(String json) {
        List<PetRows.Pet> list = List.of();
        if (json != null && !json.isBlank()) {
            try {
                PetRows.Pets all = Json.fromJson(json, PetRows.Pets.class);
                list = all == null || all.pets == null ? List.of() : List.copyOf(all.pets);
            } catch (RuntimeException e) {
                list = List.of();
            }
        }
        pets = list;
        receivedAt = System.currentTimeMillis();
    }

    public static void clear() {
        pets = List.of();
        lastRects = List.of();
    }

    /** True while there are rows: the kit HUD then leaves out its small companion symbols. */
    public static boolean shown() {
        return !pets.isEmpty();
    }

    /** For the tests: the rows received last. */
    public static List<PetRows.Pet> pets() {
        return pets;
    }

    public static void draw(GuiGraphicsExtractor gui) {
        Minecraft mc = Minecraft.getInstance();
        List<PetRows.Pet> list = pets;
        if (list.isEmpty() || mc.player == null || mc.player.isSpectator()) {
            lastRects = List.of();
            lastTexts = List.of();
            lastMore = "";
            return;
        }
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        ActionBarLayout.Layout bar = KitHud.active() ? KitHud.lastLayout : null;
        int barLeft;
        if (bar != null) {
            barLeft = bar.left();
        } else {
            boolean offLeft = !mc.player.getOffhandItem().isEmpty()
                    && mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            barLeft = sw / 2 - 91 - (offLeft ? 29 : 0);
        }
        long waited = Math.max(0L, (System.currentTimeMillis() - receivedAt) / 50L);
        int shown = Math.min(PetRows.MAX_ROWS, list.size());
        String[] texts = new String[shown];
        int textW = 0;
        for (int i = 0; i < shown; i++) {
            PetRows.Pet p = list.get(i);
            texts[i] = PetRows.number(p, Math.max(0L, p.waitTicks - waited));
            textW = Math.max(textW, font.width(texts[i]));
        }
        String more = list.size() > shown ? "+" + (list.size() - shown) : "";
        int rowW = PetRows.TEXT_X + textW + 2;
        PetRows.Place place = PetRows.place(barLeft, sh, list.size(), rowW, font.width(more) + 4);
        for (int i = 0; i < place.rows().size(); i++) {
            ActionBarLayout.Rect r = place.rows().get(i);
            gui.pose().pushMatrix();
            gui.pose().translate(r.x(), r.y());
            if (place.scale() < 1f) {
                gui.pose().scale(place.scale(), place.scale());
            }
            row(gui, font, list.get(i), texts[i], rowW);
            gui.pose().popMatrix();
        }
        if (place.more() != null) {
            ActionBarLayout.Rect m = place.more();
            gui.pose().pushMatrix();
            gui.pose().translate(m.x(), m.y());
            if (place.scale() < 1f) {
                gui.pose().scale(place.scale(), place.scale());
            }
            int w = font.width(more) + 4;
            gui.fill(0, 0, w, PetRows.ROW_H, PLATE);
            gui.text(font, more, 2, 1, TEXT, true);
            gui.pose().popMatrix();
        }
        List<ActionBarLayout.Rect> rects = new java.util.ArrayList<>(place.rows());
        if (place.more() != null) {
            rects.add(place.more());
        }
        lastRects = List.copyOf(rects);
        lastTexts = List.of(texts);
        lastMore = more;
    }

    /** One row at 0,0 in row pixels: rim, head, health bar, number. */
    private static void row(GuiGraphicsExtractor gui, Font font, PetRows.Pet p, String text, int w) {
        int h = PetRows.ROW_H;
        gui.fill(0, 0, w, h, PLATE);
        int rim = p.alpha ? GOLD : RIM;
        gui.fill(0, 0, w, 1, rim);
        gui.fill(0, h - 1, w, h, rim);
        gui.fill(0, 1, 1, h - 1, rim);
        gui.fill(w - 1, 1, w, h - 1, rim);
        if (p.alpha) {
            gui.fill(0, 0, 2, 1, GOLD_HI);
            gui.fill(w - 2, 0, w, 1, GOLD_HI);
        }
        // the head (7 x 7)
        Face face = FACES.get(p.type);
        int tint = p.dead ? 0xFF5A5A5A : 0xFFFFFFFF;
        if (face != null) {
            gui.blit(RenderPipelines.GUI_TEXTURED, face.tex(), 1, 1, face.u(), face.v(), 7, 7, face.w(), face.h(),
                    face.texW(), face.texH(), tint);
        } else {
            gui.pose().pushMatrix();
            gui.pose().translate(1f, 1f);
            gui.pose().scale(7f / 16f, 7f / 16f);
            gui.item(UltHud.icon(p.icon), 0, 0);
            gui.pose().popMatrix();
            if (p.dead) {
                gui.fill(1, 1, 8, 8, 0xA0202020);
            }
        }
        // the health bar
        int bx = 10;
        int bw = PetRows.TEXT_X - 2 - bx;
        gui.fill(bx, 3, bx + bw, 6, p.dead ? 0xFF2A2724 : 0xFF1A0E0C);
        float share = PetRows.share(p);
        int fill = Math.round(bw * share);
        if (fill <= 0 && !p.dead && p.hp > 0f) {
            fill = 1;   // a sliver while it still lives
        }
        if (fill > 0) {
            gui.fill(bx, 3, bx + fill, 6, PetRows.color(share));
        }
        gui.text(font, text, PetRows.TEXT_X, 1, p.dead ? GREY : TEXT, true);
    }
}
