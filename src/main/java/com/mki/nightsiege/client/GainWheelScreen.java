package com.mki.nightsiege.client;

import com.mki.nightsiege.core.GainWheelLogic;
import com.mki.nightsiege.core.UltState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The gain wheel (Tor 3.2): opens while the gain key is held. The mouse points at a place (or the
 * mouse wheel turns), letting go of the key uses it; a left click does too, Escape closes without.
 * Every place shows its gain with the cooldown ring of {@link GainCooldownHud#gain}.
 */
public final class GainWheelScreen extends Screen {

    private static final int RADIUS = 40;

    private int selected;
    private boolean done;

    public GainWheelScreen(int start) {
        super(Component.translatable("nightsiege.gainwheel.title"));
        this.selected = start;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static List<UltState.Slot> gains() {
        UltState s = UltHud.state();
        if (s == null || s.gains == null) {
            return List.of();
        }
        return s.gains.size() > GainClient.GAIN_KEYS ? s.gains.subList(0, GainClient.GAIN_KEYS) : s.gains;
    }

    private void use() {
        if (done) {
            return;
        }
        done = true;
        int n = gains().size();
        if (selected >= 0 && selected < n) {
            GainClient.useGain(selected);
        }
        onClose();
    }

    @Override
    public void tick() {
        // The key let go between the hold check and the opening (no release event reaches us then).
        KeyMapping key = GainClient.wheelKey();
        InputConstants.Key bound = key == null ? null : net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key);
        if (bound != null && bound.getType() == InputConstants.Type.KEYSYM
                && !InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue())) {
            use();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyMapping key = GainClient.wheelKey();
        if (key != null && key.matches(event)) {
            use();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        KeyMapping key = GainClient.wheelKey();
        if (key != null && key.matchesMouse(event)) {
            use();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            pick(event.x(), event.y());
            use();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        pick(mouseX, mouseY);
    }

    private void pick(double mouseX, double mouseY) {
        int at = GainWheelLogic.sector(mouseX - width / 2.0, mouseY - height / 2.0, gains().size());
        if (at >= 0) {
            selected = at;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            selected = GainWheelLogic.scroll(selected, scrollY > 0 ? -1 : 1, gains().size());
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // No blur, no grey veil: the fight stays visible around the wheel.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        List<UltState.Slot> gains = gains();
        int n = gains.size();
        int cx = width / 2;
        int cy = height / 2;
        UltState s = UltHud.state();
        boolean live = s != null && s.live;
        // The iron ring with gold studs at the four quarters, under the places.
        for (int k = 0; k < 64; k++) {
            double a = Math.PI * 2 * k / 64;
            int px = (int) Math.round(cx + Math.sin(a) * RADIUS) - 1;
            int py = (int) Math.round(cy - Math.cos(a) * RADIUS) - 1;
            boolean stud = k % 16 == 8;
            gui.fill(px, py, px + 2, py + 2, stud ? PurpleUi.GOLD_SHADE : PurpleUi.FRAME_HI);
        }
        // The middle: a dark plate with the name of the chosen gain.
        gui.fill(cx - 3, cy - 3, cx + 3, cy + 3, PurpleUi.GOLD_EDGE);
        gui.fill(cx - 2, cy - 2, cx + 2, cy + 2, PurpleUi.PANEL_SOLID);
        long elapsed = UltHud.elapsedTicks();
        for (int i = 0; i < n; i++) {
            double a = GainWheelLogic.angleOf(i, n);
            int px = (int) Math.round(cx + Math.sin(a) * RADIUS) - GainCooldownHud.SIZE / 2;
            int py = (int) Math.round(cy - Math.cos(a) * RADIUS) - GainCooldownHud.SIZE / 2;
            GainCooldownHud.gain(gui, font, px, py, gains.get(i), elapsed, live, i == selected);
        }
        int textY = cy + RADIUS + GainCooldownHud.SIZE / 2 + 6;
        if (n == 0) {
            String none = Component.translatable("nightsiege.kitpage.keys_none").getString();
            gui.text(font, none, cx - font.width(none) / 2, textY, PurpleUi.LIGHT, true);
            return;
        }
        if (selected >= 0 && selected < n) {
            UltState.Slot g = gains.get(selected);
            String name = g.name == null || g.name.isEmpty() ? g.id : Component.translatable(g.name).getString();
            int w = font.width(name);
            PurpleUi.window(gui, cx - w / 2 - 4, textY - 3, cx + w / 2 + 4, textY + 10);
            gui.text(font, name, cx - w / 2, textY, PurpleUi.GOLD, true);
        }
        String hint = Component.translatable("nightsiege.gainwheel.hint").getString();
        gui.text(font, hint, cx - font.width(hint) / 2, textY + 15, PurpleUi.GREY, true);
    }
}
