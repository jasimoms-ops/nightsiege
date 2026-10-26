package com.mki.nightsiege.client;

import com.mki.nightsiege.core.BrewKind;
import com.mki.nightsiege.core.BrewMenuData;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The alchemist's potion menu (Simon, 26 October 2026): sneak + right click with the remote.
 * Four potions for the players, three against the monsters; the locked ones say which node
 * of the tree opens them. A click picks the potion the next zone throws, and closes.
 */
public class BrewMenuScreen extends Screen {

    private static final int BACKDROP = 0xA0000000;
    private static final int FRAME = 0xFF3A3450;
    private static final int PANEL = 0xF0121019;
    private static final int BOX = 0xFF1A1622;
    private static final int LINE = 0xFF2C2740;
    private static final int GOLD = 0xFFFFD86B;
    private static final int GREY = 0xFF8F879F;
    private static final int LIGHT = 0xFFCFC7DD;
    private static final int GREEN = 0xFF7CD67C;
    private static final int RED = 0xFFE07A6A;

    private final BrewMenuData data;
    private final List<BrewMenuData.Entry> help = new ArrayList<>();
    private final List<BrewMenuData.Entry> harm = new ArrayList<>();

    private int left;
    private int top;
    private int w;
    private int h;
    private int cardW;
    private int cardH;
    private int rowHelp;
    private int rowHarm;

    public BrewMenuScreen(BrewMenuData data) {
        super(Component.translatable("nightsiege.brewmenu.title"));
        this.data = data;
        for (BrewMenuData.Entry e : data.brews) {
            BrewKind kind = BrewKind.byId(e.id);
            if (kind == null) {
                continue;
            }
            (kind.forPlayers() ? help : harm).add(e);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.max(320, Math.min(460, this.width - 8));
        h = Math.max(210, Math.min(262, this.height - 8));
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        int gap = 5;
        cardW = (w - 16 - 3 * gap) / 4;
        cardH = Math.max(52, (h - 92) / 2);
        rowHelp = top + 54;
        rowHarm = rowHelp + cardH + 16;
        addCards(help, rowHelp, gap);
        addCards(harm, rowHarm, gap);
    }

    private void addCards(List<BrewMenuData.Entry> list, int y, int gap) {
        for (int i = 0; i < list.size(); i++) {
            BrewMenuData.Entry entry = list.get(i);
            Button card = Button.builder(Component.translatable("nightsiege.brew." + entry.id), b -> pick(entry))
                    .bounds(cardX(i, gap), y, cardW, cardH).build();
            card.active = entry.open;
            card.setAlpha(0f);
            addRenderableWidget(card);
        }
    }

    private int cardX(int i, int gap) {
        return left + 8 + i * (cardW + gap);
    }

    private void pick(BrewMenuData.Entry entry) {
        if (!entry.open) {
            return;
        }
        if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
            ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("brew:" + entry.id));
        }
        data.selected = entry.id;
        Minecraft.getInstance().setScreenAndShow(null);
    }

    /** A lingering potion in the potion's own colour. */
    private static ItemStack icon(BrewKind kind) {
        ItemStack stack = new ItemStack(Items.LINGERING_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(kind.color()),
                List.of(), Optional.empty()));
        return stack;
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, BACKDROP);
        gui.fill(left - 1, top - 1, left + w + 1, top + h + 1, FRAME);
        gui.fill(left, top, left + w, top + h, PANEL);
        gui.fill(left, top, left + w, top + 1, 0xFF4A4266);

        gui.pose().pushMatrix();
        gui.pose().translate(left + 10, top + 8);
        gui.pose().scale(1.2f, 1.2f);
        gui.text(font, Component.translatable("nightsiege.brewmenu.title").getString(), 0, 0, GOLD, true);
        gui.pose().popMatrix();
        String status = data.cooldown > 0
                ? Component.translatable("nightsiege.brewmenu.wait", data.cooldown).getString()
                : Component.translatable("nightsiege.brewmenu.ready").getString();
        int sw = (int) (font.width(status) * 0.75f);
        small(gui, font, status, left + w - 10 - sw, top + 10, data.cooldown > 0 ? RED : GREEN, 0.75f);
        wrapped(gui, font, Component.translatable("nightsiege.brewmenu.hint", data.reach, data.zoneSeconds,
                data.pauseSeconds).getString(), left + 10, top + 22, w - 20, GREY, 0.7f, 2);

        heading(gui, font, Component.translatable("nightsiege.brewmenu.help").getString(), rowHelp - 9, GREEN);
        for (int i = 0; i < help.size(); i++) {
            drawCard(gui, font, help.get(i), cardX(i, 5), rowHelp, mouseX, mouseY);
        }
        heading(gui, font, Component.translatable("nightsiege.brewmenu.harm").getString(), rowHarm - 9, RED);
        for (int i = 0; i < harm.size(); i++) {
            drawCard(gui, font, harm.get(i), cardX(i, 5), rowHarm, mouseX, mouseY);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void heading(GuiGraphicsExtractor gui, Font font, String text, int y, int color) {
        int tw = (int) (font.width(text) * 0.75f);
        small(gui, font, text, left + 10, y, color, 0.75f);
        gui.fill(left + 14 + tw, y + 3, left + w - 10, y + 4, LINE);
    }

    private void drawCard(GuiGraphicsExtractor gui, Font font, BrewMenuData.Entry entry, int x, int y, int mouseX, int mouseY) {
        BrewKind kind = BrewKind.byId(entry.id);
        boolean selected = entry.id.equals(data.selected);
        boolean hover = entry.open && mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH;
        if (selected) {
            gui.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, 0x30FFD86B);
        }
        gui.fill(x, y, x + cardW, y + cardH, selected ? GOLD : hover ? 0xFF6A5F80 : LINE);
        gui.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, selected ? 0xFF231C33 : entry.open ? BOX : 0xFF131118);
        // A stripe in the potion's colour on the left edge.
        int color = 0xFF000000 | kind.color();
        gui.fill(x + 1, y + 1, x + 3, y + cardH - 1, entry.open ? color : 0xFF3A3640);

        gui.pose().pushMatrix();
        gui.pose().translate(x + 6, y + 5);
        gui.pose().scale(1.25f, 1.25f);
        gui.item(icon(kind), 0, 0);
        gui.pose().popMatrix();
        if (!entry.open) {
            gui.fill(x + 6, y + 5, x + 26, y + 25, 0x90131118);
        }
        String name = Component.translatable("nightsiege.brew." + entry.id).getString();
        float ns = Math.min(0.9f, Math.max(0.6f, (cardW - 34f) / Math.max(1, font.width(name))));
        gui.pose().pushMatrix();
        gui.pose().translate(x + 29, y + 8);
        gui.pose().scale(ns, ns);
        gui.text(font, name, 0, 0, entry.open ? (selected ? GOLD : 0xFFFFFFFF) : GREY, true);
        gui.pose().popMatrix();
        if (selected) {
            small(gui, font, Component.translatable("nightsiege.brewmenu.selected").getString(), x + 29, y + 18, GOLD, 0.6f);
        }
        int ty = y + 28;
        int lines = Math.max(1, (y + cardH - 4 - ty) / 7);
        if (entry.open) {
            wrapped(gui, font, Component.translatable("nightsiege.brew." + entry.id + ".hint").getString(),
                    x + 6, ty, cardW - 10, LIGHT, 0.6f, lines);
        } else {
            wrapped(gui, font, Component.translatable("nightsiege.brewmenu.locked", needText(entry.need)).getString(),
                    x + 6, ty, cardW - 10, 0xFF8A7F9A, 0.6f, lines);
        }
    }

    /** "Trankwerfer Stufe 2" or "Giftgebräu oder Ätzschleim". */
    private static String needText(String need) {
        StringBuilder out = new StringBuilder();
        String[] options = need == null ? new String[0] : need.split("\\|");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                out.append(Component.translatable("nightsiege.brewmenu.or").getString());
            }
            String[] parts = options[i].split(":");
            String node = Component.translatable("nightsiege.skill." + parts[0]).getString();
            out.append(parts.length > 1 ? Component.translatable("nightsiege.brewmenu.level", node, parts[1]).getString() : node);
        }
        return out.toString();
    }

    private static void small(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int color, float scale) {
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, text, 0, 0, color, false);
        gui.pose().popMatrix();
    }

    private static void wrapped(GuiGraphicsExtractor gui, Font font, String text, int x, int y, int width, int color,
                                float scale, int maxLines) {
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), (int) (width / scale));
        int lineH = (int) Math.ceil(9 * scale) + 1;
        int n = 0;
        for (FormattedCharSequence line : lines) {
            if (n >= maxLines) {
                break;
            }
            gui.pose().pushMatrix();
            gui.pose().translate(x, y + n * lineH);
            gui.pose().scale(scale, scale);
            gui.text(font, line, 0, 0, color, false);
            gui.pose().popMatrix();
            n++;
        }
    }

    /** What the menu offers (for the test). */
    public BrewMenuData data() {
        return data;
    }
}
