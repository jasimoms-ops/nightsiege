package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CrateRarity;
import com.mki.nightsiege.core.CrateSummary;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "Alle öffnen" (Tor 9.1 h, Simon 09.11.): every crate opened at once, shown as one overview in the
 * frame of the crate window – one line per gain with its rarity, how often it came, the stage reached
 * and the skill points of the duplicates; at the top the sums. The server rolled them all already.
 */
public class CrateSummaryScreen extends Screen {

    private static final int ROW = 24;

    private final CrateSummary summary;
    private int W = 460;
    private int H = 250;
    private int left;
    private int top;
    private int scroll;
    private boolean sounded;

    public CrateSummaryScreen(CrateSummary summary) {
        super(Component.translatable("nightsiege.crate.all_title"));
        this.summary = summary;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        PlaytestScreen.afterCrate();   // opened from the playtest menu: back there
    }

    @Override
    protected void init() {
        W = Math.max(300, Math.min(460, this.width - 10));
        int want = 44 + Math.max(2, summary.entries.size()) * ROW + 40;
        H = Math.max(150, Math.min(want, this.height - 8));
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        scroll = 0;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 76, top + H - 28, 64, 20).build());
    }

    private int listTop() {
        return top + 44;
    }

    private int listBottom() {
        return top + H - 34;
    }

    private int maxScroll() {
        return Math.max(0, summary.entries.size() * ROW - (listBottom() - listTop()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY < 0 ? ROW : -ROW)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        if (!sounded) {
            sounded = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
            CrateSummary.Entry best = summary.best();
            if (best != null && CrateRarity.byName(best.rarity).ordinal() >= CrateRarity.RARE.ordinal()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 1.0f));
            }
        }
        gui.fill(0, 0, this.width, this.height, 0xA0000000);
        // Window, as the crate window.
        int x2 = left + W;
        int y2 = top + H;
        gui.fill(left - 1, top - 1, x2 + 1, y2 + 1, 0xFF3A2E4E);
        gui.fill(left, top, x2, y2, 0xFF000000);
        gui.fill(left + 1, top + 1, x2 - 1, y2 - 1, 0xF014101C);
        gui.fill(left + 1, top + 1, x2 - 1, top + 2, 0xFF2E2640);
        gui.fill(left + 1, top + 1, left + 2, y2 - 1, 0xFF2E2640);

        // Header: crate, title, sums.
        gui.item(new ItemStack(item("nightsiege:reward_crate")), left + 12, top + 10);
        gui.pose().pushMatrix();
        gui.pose().translate(left + 34, top + 9);
        gui.pose().scale(1.5f, 1.5f);
        gui.text(font, Component.translatable("nightsiege.crate.all_title").getString(), 0, 0, 0xFFFFD86B, true);
        gui.pose().popMatrix();
        String sums = Component.translatable("nightsiege.crate.all_sums", summary.crates, summary.fresh,
                summary.duplicates, summary.points).getString();
        gui.text(font, font.plainSubstrByWidth(sums, W - 46), left + 34, top + 24, 0xFFB8B0C8, false);

        // The list.
        int lx = left + 10;
        int lw = W - 20;
        int ly = listTop();
        int lb = listBottom();
        gui.fill(lx - 1, ly - 1, lx + lw + 1, lb + 1, 0xFF000000);
        gui.fill(lx, ly, lx + lw, lb, 0xFF0E0B14);
        gui.enableScissor(lx, ly, lx + lw, lb);
        int y = ly + 2 - scroll;
        for (CrateSummary.Entry e : summary.entries) {
            if (y + ROW >= ly && y <= lb) {
                drawRow(gui, font, e, lx + 4, y, lw - 8);
            }
            y += ROW;
        }
        gui.disableScissor();
        if (maxScroll() > 0) {
            int bar = Math.max(10, (lb - ly) * (lb - ly) / (summary.entries.size() * ROW));
            int by = ly + (lb - ly - bar) * scroll / maxScroll();
            gui.fill(lx + lw - 3, by, lx + lw - 1, by + bar, 0xFF5A4C78);
        }
        // Bottom: the collection of the class.
        String count = Component.translatable("nightsiege.crate.collection", summary.collected, summary.total).getString();
        gui.text(font, count, left + 14, top + H - 22, 0xFFB8B0C8, false);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        PurpleUi.skinButtons(gui, Minecraft.getInstance().font, children(), null, mouseX, mouseY);
    }

    /** One gain: card with icon in the rarity colour, name and count, rarity and class, what it brought. */
    private void drawRow(GuiGraphicsExtractor gui, Font font, CrateSummary.Entry e, int x, int y, int w) {
        CrateRarity rarity = CrateRarity.byName(e.rarity);
        int color = 0xFF000000 | rarity.color();
        gui.fill(x, y, x + 20, y + 20, color);
        gui.fill(x + 1, y + 1, x + 19, y + 19, 0xFF1B1624);
        gui.item(new ItemStack(item(e.icon)), x + 2, y + 2);
        int tx = x + 26;
        String name = Component.translatable("nightsiege.gain." + e.gain).getString();
        gui.text(font, name, tx, y + 1, 0xFFFFFFFF, true);
        if (e.count > 1) {
            gui.text(font, "×" + e.count, tx + font.width(name) + 4, y + 1, 0xFFFFD86B, true);
        }
        SkillClass clazz = SkillClass.byName(e.clazz);
        String sub = Component.translatable(rarity.translationKey()).getString()
                + (clazz == null ? "" : " · " + Component.translatable(clazz.translationKey()).getString());
        gui.text(font, sub, tx, y + 12, color, false);
        // What it brought: the stage reached, and the points of the duplicates.
        String state = "";
        int stateColor = 0xFF8FE39A;
        if (e.fresh > 0) {
            if (e.twoPulls && e.stage % 2 == 1) {
                state = Component.translatable("nightsiege.crate.half").getString();
                stateColor = 0xFFFFC46B;
            } else if (e.topStage > 1 && !e.twoPulls) {
                state = Component.translatable("nightsiege.crate.stage", e.stage, e.topStage).getString();
            } else {
                state = Component.translatable("nightsiege.crate.new").getString();
            }
        }
        if (e.duplicates > 0) {
            String dup = Component.translatable("nightsiege.crate.all_points", e.points).getString();
            state = state.isEmpty() ? dup : state + " · " + dup;
            if (e.fresh == 0) {
                stateColor = 0xFFFFD86B;
            }
        }
        int room = Math.max(40, w - (tx - x) - font.width(name) - 40);
        state = font.plainSubstrByWidth(state, room);
        gui.text(font, state, x + w - font.width(state) - 4, y + 6, stateColor, false);
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id == null ? "" : id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("chest")) : item;
    }

    /** The overview shown (for the client test). */
    public CrateSummary summary() {
        return summary;
    }
}
