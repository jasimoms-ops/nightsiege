package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.CrateRarity;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The opened reward crate: a band of gains runs past a golden marker, slows
 * down and stops on the gain won (the server rolled it already). Below, the
 * gain is shown with what it does and how far it has come.
 */
public class CrateScreen extends Screen {

    private static final int W = 460;
    private static final int H = 250;
    private static final int CARD = 64;
    private static final int GAP = 6;
    private static final int STEP = CARD + GAP;
    private static final int VIEW_W = 420;
    private static final int VIEW_H = 92;
    /** Milliseconds the band spins. */
    private static final long SPIN = 5200L;

    private final CrateRoll roll;
    private long started = System.currentTimeMillis();
    private final double jitter;
    private int lastCard = -1;
    private boolean revealed;
    private int left;
    private int top;

    public CrateScreen(CrateRoll roll) {
        super(Component.translatable("nightsiege.crate.title"));
        this.roll = roll;
        this.jitter = (Math.random() - 0.5) * (CARD - 14);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 76, top + H - 28, 64, 20).build());
    }

    /** 0 at the start, 1 when the band stands. */
    private double progress() {
        return Math.min(1.0, (System.currentTimeMillis() - started) / (double) SPIN);
    }

    private boolean done() {
        return progress() >= 1.0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, 0xA0000000);
        // Window.
        int x2 = left + W;
        int y2 = top + H;
        gui.fill(left - 1, top - 1, x2 + 1, y2 + 1, 0xFF3A2E4E);
        gui.fill(left, top, x2, y2, 0xFF000000);
        gui.fill(left + 1, top + 1, x2 - 1, y2 - 1, 0xF014101C);
        gui.fill(left + 1, top + 1, x2 - 1, top + 2, 0xFF2E2640);
        gui.fill(left + 1, top + 1, left + 2, y2 - 1, 0xFF2E2640);

        // Header: crate, title, class and night.
        gui.item(new ItemStack(item("nightsiege:reward_crate")), left + 12, top + 10);
        gui.pose().pushMatrix();
        gui.pose().translate(left + 34, top + 9);
        gui.pose().scale(1.5f, 1.5f);
        gui.text(font, Component.translatable("nightsiege.crate.title").getString(), 0, 0, 0xFFFFD86B, true);
        gui.pose().popMatrix();
        SkillClass clazz = SkillClass.byName(roll.clazz);
        String sub = (clazz == null ? "" : Component.translatable(clazz.translationKey()).getString() + " · ")
                + Component.translatable("nightsiege.crate.kind." + CrateKind.byName(roll.kind).key()).getString()
                + " · " + Component.translatable("nightsiege.crate.level", roll.level).getString();
        if (roll.lucky) {
            sub += " · " + Component.translatable("nightsiege.crate.lucky").getString();
        }
        gui.text(font, sub, left + 34, top + 24, 0xFFB8B0C8, false);
        // Odds of this crate.
        int ox = x2 - 10;
        CrateRarity[] all = CrateRarity.values();
        int total = 0;
        for (int w : roll.odds) {
            total += Math.max(0, w);
        }
        for (int i = all.length - 1; i >= 0; i--) {
            int pct = total <= 0 || i >= roll.odds.length ? 0 : Math.round(100f * roll.odds[i] / total);
            String label = pct + "%";
            int lw = font.width(label) + 12;
            ox -= lw + 3;
            gui.fill(ox, top + 12, ox + lw, top + 24, 0xFF0E0B14);
            gui.fill(ox + 3, top + 15, ox + 9, top + 21, 0xFF000000 | all[i].color());
            gui.text(font, label, ox + 11, top + 14, 0xFFE8E2F0, false);
        }

        // The band.
        int vx = left + (W - VIEW_W) / 2;
        int vy = top + 44;
        gui.fill(vx - 2, vy - 2, vx + VIEW_W + 2, vy + VIEW_H + 2, 0xFF000000);
        gui.fill(vx, vy, vx + VIEW_W, vy + VIEW_H, 0xFF0E0B14);
        double p = progress();
        double eased = 1.0 - Math.pow(1.0 - p, 3.2);
        double finalOffset = roll.winIndex * STEP + CARD / 2.0 - VIEW_W / 2.0 + jitter;
        double offset = finalOffset * eased;
        int under = (int) Math.floor((offset + VIEW_W / 2.0) / STEP);
        if (under != lastCard && !done()) {
            lastCard = under;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
        }
        gui.enableScissor(vx, vy, vx + VIEW_W, vy + VIEW_H);
        for (int i = 0; i < roll.band.size(); i++) {
            int cx = (int) Math.round(vx + i * STEP - offset);
            if (cx + CARD < vx || cx > vx + VIEW_W) {
                continue;
            }
            CrateRoll.Slot slot = roll.band.get(i);
            int color = 0xFF000000 | CrateRarity.byName(slot.rarity).color();
            boolean win = done() && i == roll.winIndex;
            int cy = vy + 6;
            gui.fill(cx, cy, cx + CARD, cy + CARD + 14, win ? color : 0xFF2A2236);
            gui.fill(cx + 1, cy + 1, cx + CARD - 1, cy + CARD + 13, 0xFF1B1624);
            gui.fill(cx + 1, cy + 1, cx + CARD - 1, cy + 3, (color & 0x00FFFFFF) | 0x90000000);
            gui.fill(cx, cy + CARD + 8, cx + CARD, cy + CARD + 14, color);
            gui.pose().pushMatrix();
            gui.pose().translate(cx + CARD / 2f - 16, cy + 10);
            gui.pose().scale(2f, 2f);
            gui.item(new ItemStack(item(slot.icon)), 0, 0);
            gui.pose().popMatrix();
            String name = font.plainSubstrByWidth(Component.translatable("nightsiege.gain." + slot.gain).getString(), CARD - 4);
            gui.text(font, name, cx + (CARD - font.width(name)) / 2, cy + CARD - 14, 0xFFE8E2F0, false);
        }
        gui.disableScissor();
        // The marker.
        int mx = vx + VIEW_W / 2;
        gui.fill(mx - 1, vy - 4, mx + 1, vy + VIEW_H + 4, 0xFFFFD86B);
        gui.fill(mx - 4, vy - 4, mx + 4, vy - 2, 0xFFFFD86B);
        gui.fill(mx - 4, vy + VIEW_H + 2, mx + 4, vy + VIEW_H + 4, 0xFFFFD86B);

        // The result.
        if (done()) {
            if (!revealed) {
                revealed = true;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                        roll.duplicate ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
            }
            drawResult(gui, font, vy + VIEW_H + 10);
        } else {
            String hint = Component.translatable("nightsiege.crate.spinning").getString();
            gui.text(font, hint, left + (W - font.width(hint)) / 2, vy + VIEW_H + 24, 0xFF8F879F, false);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawResult(GuiGraphicsExtractor gui, Font font, int y) {
        CrateRarity rarity = CrateRarity.byName(roll.rarity);
        int color = 0xFF000000 | rarity.color();
        int x = left + 14;
        // The won card, large.
        gui.fill(x - 1, y - 1, x + 51, y + 51, color);
        gui.fill(x, y, x + 50, y + 50, 0xFF1B1624);
        gui.pose().pushMatrix();
        gui.pose().translate(x + 9, y + 9);
        gui.pose().scale(2f, 2f);
        gui.item(new ItemStack(item(roll.icon)), 0, 0);
        gui.pose().popMatrix();
        int tx = x + 60;
        gui.text(font, Component.translatable(rarity.translationKey()).getString().toUpperCase(java.util.Locale.ROOT),
                tx, y, color, true);
        gui.pose().pushMatrix();
        gui.pose().translate(tx, y + 11);
        gui.pose().scale(1.5f, 1.5f);
        gui.text(font, Component.translatable("nightsiege.gain." + roll.gain).getString(), 0, 0, 0xFFFFFFFF, true);
        gui.pose().popMatrix();
        String descKey = "nightsiege.gain." + roll.gain + ".desc";
        String desc = Language.getInstance().has(descKey) ? Language.getInstance().getOrDefault(descKey) : "";
        int ty = y + 26;
        for (String line : wrap(font, desc, W - 170)) {
            gui.text(font, line, tx, ty, 0xFFCFC7DD, false);
            ty += 10;
        }
        String state;
        int stateColor;
        if (roll.duplicate) {
            state = Component.translatable("nightsiege.crate.duplicate", roll.points).getString();
            stateColor = 0xFFFFD86B;
        } else if (roll.twoPulls && roll.stage % 2 == 1) {
            state = Component.translatable("nightsiege.crate.half").getString();
            stateColor = 0xFFFFC46B;
        } else if (roll.topStage > 1 && !roll.twoPulls) {
            state = Component.translatable("nightsiege.crate.stage", roll.stage, roll.topStage).getString();
            stateColor = 0xFF8FE39A;
        } else {
            state = Component.translatable("nightsiege.crate.new").getString();
            stateColor = 0xFF8FE39A;
        }
        gui.text(font, state, tx, ty + 2, stateColor, false);
        String count = Component.translatable("nightsiege.crate.collection", roll.collected, roll.total).getString();
        gui.text(font, count, tx, ty + 13, 0xFFB8B0C8, false);
        if (roll.infernite) {
            String inf = Component.translatable("nightsiege.crate.infernite_short").getString();
            gui.item(new ItemStack(item("nightsiege:infernite_ingot")), left + W - 100, top + H - 52);
            gui.text(font, inf, left + W - 82, top + H - 48, 0xFFFF8A3A, true);
        }
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String attempt = current.length() == 0 ? word : current + " " + word;
            if (font.width(attempt) > width && current.length() > 0) {
                out.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(attempt);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id == null ? "" : id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("chest")) : item;
    }

    /** True while the band still turns (for the client test). */
    public boolean spinning() {
        return !done();
    }

    /** Skips straight to the result (for the client test). */
    public void finishNow() {
        started = System.currentTimeMillis() - SPIN - 10;
    }
}
