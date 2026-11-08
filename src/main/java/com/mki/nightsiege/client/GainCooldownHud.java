package com.mki.nightsiege.client;

import com.mki.nightsiege.core.GainWheelLogic;
import com.mki.nightsiege.core.UltState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The cooldowns of the crate gains (Tor 3.2, Simon 08.11.: "man sieht aber irgendwo in der ui die
 * cooldowns"): a small column at the middle of the right screen edge, each gain as its symbol in a
 * ring that fills gold while the pause runs out, the seconds on top. The gain wheel draws its places
 * with the same {@link #gain} medallion. Its own element, hooked in by {@link GainClient#init}: it
 * does not touch the kit HUD. The right edge keeps clear of chat (bottom left), Jade (top middle),
 * Simple Voice Chat (top left) and the effects (top right).
 */
public final class GainCooldownHud {

    /** Size of one medallion and the gap to the next. */
    static final int SIZE = 22;
    private static final int STEP = 25;
    private static final int RING_SEGMENTS = 24;

    private GainCooldownHud() {
    }

    /** Hooked in after the hotbar by {@link GainClient#init}. */
    public static void draw(GuiGraphicsExtractor gui) {
        Minecraft mc = Minecraft.getInstance();
        UltState s = UltHud.state();
        if (s == null || s.gains == null || s.gains.isEmpty() || mc.player == null
                || mc.gui.screen() instanceof GainWheelScreen || !(s.keys || s.live)) {
            return;
        }
        List<UltState.Slot> gains = s.gains;
        int n = Math.min(gains.size(), GainClient.GAIN_KEYS);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw - SIZE - 4;
        int height = n * STEP - (STEP - SIZE);
        int y = sh / 2 - height / 2;
        // A thin iron band with gold studs above and below the column, like the trim of the kit bar.
        gui.fill(x + 2, y - 4, x + SIZE - 2, y - 3, PurpleUi.FRAME_HI);
        gui.fill(x + SIZE / 2 - 1, y - 5, x + SIZE / 2 + 1, y - 2, PurpleUi.GOLD_SHADE);
        gui.fill(x + 2, y + height + 3, x + SIZE - 2, y + height + 4, PurpleUi.FRAME_HI);
        gui.fill(x + SIZE / 2 - 1, y + height + 2, x + SIZE / 2 + 1, y + height + 5, PurpleUi.GOLD_SHADE);
        long elapsed = UltHud.elapsedTicks();
        for (int i = 0; i < n; i++) {
            gain(gui, mc.font, x, y + i * STEP, gains.get(i), elapsed, s.live, false);
        }
        // The wheel key once, small, at the top left of the column.
        net.minecraft.client.KeyMapping key = GainClient.wheelKey();
        if (key != null && !key.isUnbound()) {
            String text = key.getTranslatedKeyMessage().getString();
            if (text.length() > 3) {
                text = text.substring(0, 3);
            }
            gui.text(mc.font, text, x + SIZE / 2 - mc.font.width(text) / 2, y - 14, PurpleUi.GOLD, true);
        }
    }

    /**
     * One gain as a medallion at (x, y), {@link #SIZE} wide: dark plate, iron frame with gold corner
     * studs, the symbol, the cooldown ring and the seconds. {@code picked}: the place chosen in the wheel.
     */
    static void gain(GuiGraphicsExtractor gui, Font font, int x, int y, UltState.Slot g, long elapsed,
                     boolean live, boolean picked) {
        int frame = picked ? PurpleUi.GOLD : PurpleUi.FRAME_HI;
        gui.fill(x + 1, y, x + SIZE - 1, y + SIZE, frame);
        gui.fill(x, y + 1, x + SIZE, y + SIZE - 1, frame);
        gui.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xE0121019);
        int stud = picked ? PurpleUi.GOLD_HOVER : PurpleUi.GOLD_EDGE;
        gui.fill(x, y, x + 2, y + 2, stud);
        gui.fill(x + SIZE - 2, y, x + SIZE, y + 2, stud);
        gui.fill(x, y + SIZE - 2, x + 2, y + SIZE, stud);
        gui.fill(x + SIZE - 2, y + SIZE - 2, x + SIZE, y + SIZE, stud);

        long left = GainWheelLogic.leftTicks(g.cooldownTicks, elapsed);
        boolean spent = g.current;   // used up tonight or in this kit
        boolean dim = spent || !live;
        double share = GainWheelLogic.readyShare(left, g.totalTicks);
        int lit = dim ? 0 : GainWheelLogic.litSegments(share, RING_SEGMENTS);
        ItemStack icon = UltHud.icon(g.icon);
        int ix = x + (SIZE - 16) / 2;
        int iy = y + (SIZE - 16) / 2;
        gui.item(icon, ix, iy);
        if (dim) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0xA0000000);
        } else if (left > 0) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0x80000000);
        }
        // The ring over the symbol's edge, so the shade does not hide it.
        double cx = x + SIZE / 2.0;
        double cy = y + SIZE / 2.0;
        double r = SIZE / 2.0 - 2.0;
        for (int k = 0; k < RING_SEGMENTS; k++) {
            double a = Math.PI * 2 * (k + 0.5) / RING_SEGMENTS;
            int px = (int) Math.round(cx + Math.sin(a) * r) - 1;
            int py = (int) Math.round(cy - Math.cos(a) * r) - 1;
            int color = k < lit ? (left > 0 ? PurpleUi.GOLD_SHADE : PurpleUi.GOLD) : PurpleUi.DIM;
            gui.fill(px, py, px + 2, py + 2, color);
        }
        if (!dim && left > 0) {
            String seconds = String.valueOf(GainWheelLogic.seconds(left));
            gui.text(font, seconds, x + SIZE / 2 - font.width(seconds) / 2, y + SIZE / 2 - 4, 0xFFFFFFFF, true);
        }
    }
}
