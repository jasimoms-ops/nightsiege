package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.CoreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tor 11 Teil 2, "Belagerung starten" (Simon 13.11.): which character plays the night – the current one (a night of
 * its own level, all of the experience into the pool) or a veteran (night 20 as a normal night, 50 % + 10 % per curse
 * or fragment). Without effect on the world. Shows the pool: "Vorrat: X Erfahrung, nutzbar bis Stufe Y".
 */
public class SiegeRoundScreen extends Screen {

    private static final int ROW = 20;

    private final CoreStats stats;
    private final Screen back;
    private int W = 340;
    private int H = 200;
    private int left;
    private int top;

    public SiegeRoundScreen(CoreStats stats, Screen back) {
        super(Component.translatable("nightsiege.round.title"));
        this.stats = stats;
        this.back = back;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(back);
    }

    @Override
    protected void init() {
        int rows = 1 + stats.veterans.size();
        W = Math.max(260, Math.min(360, this.width - 10));
        H = Math.max(140, Math.min(70 + rows * ROW + 34, this.height - 8));
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        int y = top + 52;
        Button current = Button.builder(Component.translatable("nightsiege.round.current", stats.roundLevel), b -> start(-1))
                .bounds(left + 12, y, W - 24, 18)
                .tooltip(Tooltip.create(stats.roundBlock.isEmpty() ? Component.translatable("nightsiege.round.current_hint")
                        : Component.translatable(stats.roundBlock, 48)))
                .build();
        current.active = stats.roundBlock.isEmpty();
        addRenderableWidget(current);
        for (int i = 0; i < stats.veterans.size() && y + 2 * ROW < top + H - 30; i++) {
            y += ROW;
            CoreStats.VeteranChoice v = stats.veterans.get(i);
            SkillClass c = SkillClass.byName(v.clazz);
            Component name = v.sub != null && !v.sub.isEmpty() ? Component.translatable("nightsiege.subclass." + v.sub)
                    : Component.translatable(c == null ? "nightsiege.round.unknown" : c.translationKey());
            Component label = Component.translatable("nightsiege.round.veteran", name, v.chapter, v.level);
            final int index = i;
            Component tip = stats.roundVeteranBlock.isEmpty()
                    ? Component.translatable("nightsiege.round.veteran_hint", 50 + 10 * stats.roundExtras, stats.roundExtras)
                    : Component.translatable(stats.roundVeteranBlock, 48);
            Button vb = Button.builder(label, b -> start(index)).bounds(left + 12, y, W - 24, 18)
                    .tooltip(Tooltip.create(tip)).build();
            vb.active = stats.roundVeteranBlock.isEmpty();
            addRenderableWidget(vb);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + W - 76, top + H - 26, 64, 18).build());
    }

    private void start(int pick) {
        ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("round:start:" + pick));
        super.onClose();   // the window shuts, the build-up begins
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        gui.fill(0, 0, this.width, this.height, 0xA0000000);
        gui.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF3A2E4E);
        gui.fill(left, top, left + W, top + H, 0xF0141019);
        gui.text(font, Component.translatable("nightsiege.round.title").getString(), left + 12, top + 8, 0xFFFFD86B, true);
        gui.text(font, font.plainSubstrByWidth(Component.translatable("nightsiege.round.intro").getString(), W - 24),
                left + 12, top + 22, 0xFFB8B0C8, false);
        gui.text(font, Component.translatable("nightsiege.round.pool", stats.xpPool, stats.poolLevel).getString(),
                left + 12, top + 36, 0xFF7FD8FF, true);
        if (stats.veterans.isEmpty()) {
            gui.text(font, Component.translatable("nightsiege.round.no_veterans").getString(), left + 12, top + 52 + ROW + 4,
                    0xFF8A8494, false);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
