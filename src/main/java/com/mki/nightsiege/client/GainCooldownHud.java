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
 * ring that fills gold while the pause runs out, the seconds on top, its key (1, 2, 3; Tor 3.2b) beside
 * it. Its own element, hooked in by {@link GainClient#init}: it
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
                || !(s.keys || s.live) || (KitHud.active() && !KitHud.legacy)) {
            return;   // Tor 3.2c: with the kit the gains sit on places 1-3 of the action bar
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
            // Tor 3.2b: each gain's own key (1, 2, 3) left of its medallion.
            String text = AbilityKeys.shortLabel(GainClient.gainKey(i));
            gui.text(mc.font, text, x - 2 - mc.font.width(text), y + i * STEP + SIZE / 2 - 4, PurpleUi.GOLD, true);
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
        ItemStack icon = UltHud.icon(g.icon);
        int ix = x + (SIZE - 16) / 2;
        int iy = y + (SIZE - 16) / 2;
        gui.item(icon, ix, iy);
        if (dim) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0xA0000000);
        } else if (left > 0) {
            gui.fill(ix, iy, ix + 16, iy + 16, 0x80000000);
        }
        // The same ring as every other ability box (core/CooldownRing via KitHud.ring, Tor 3.3).
        KitHud.ring(gui, x, y, SIZE, dim ? 0 : left, g.totalTicks);
        if (!dim && left > 0) {
            String seconds = String.valueOf(GainWheelLogic.seconds(left));
            gui.text(font, seconds, x + SIZE / 2 - font.width(seconds) / 2, y + SIZE / 2 - 4, 0xFFFFFFFF, true);
        }
    }
}
