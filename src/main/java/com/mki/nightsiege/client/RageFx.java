package com.mki.nightsiege.client;

import com.mki.nightsiege.core.WarriorSpiritMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tor 4.10: the berserker's Blutrausch on his own screen, a red vignette at the edges that grows darker
 * with the life he is missing and beats slowly. The server sends the ticks left ("rage"; 0 = over).
 */
public final class RageFx {

    private static final int BANDS = 12;
    private static long until;

    private RageFx() {
    }

    /** A state from the server: the ticks of Blutrausch left. */
    public static void set(String ticks) {
        long t;
        try {
            t = Long.parseLong(ticks.trim());
        } catch (NumberFormatException e) {
            t = 0L;
        }
        until = t <= 0 ? 0L : System.currentTimeMillis() + t * 50L;
    }

    public static void clear() {
        until = 0L;
    }

    /** True while the vignette shows (for the tests too). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return System.currentTimeMillis() < until && mc.player != null && mc.player.isAlive();
    }

    public static void draw(GuiGraphicsExtractor gui) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double missing = WarriorSpiritMath.missing(mc.player.getHealth(), mc.player.getMaxHealth());
        double beat = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0);
        double strength = 0.35 + 0.35 * missing + 0.08 * beat;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int step = Math.max(2, Math.min(w, h) / (BANDS * 3));
        if (FxSettings.full() && mc.options.getCameraType().isFirstPerson()) {
            // Z4 (Chef 12.11. 18:10/20:10): with full effects nothing closes the own view - the vignette two thirds as deep
            // (CI 6a2f3ae4d: 16 % of the own view)
            step = Math.max(1, step * 2 / 3);
        }
        for (int i = 0; i < BANDS; i++) {
            double fade = 1.0 - (double) i / BANDS;
            int alpha = (int) Math.round(255 * strength * fade * fade);
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | 0x9A0A0A;
            int in = i * step;
            gui.fill(in, in, w - in, in + step, color);                       // top
            gui.fill(in, h - in - step, w - in, h - in, color);               // bottom
            gui.fill(in, in + step, in + step, h - in - step, color);         // left
            gui.fill(w - in - step, in + step, w - in, h - in - step, color); // right
        }
    }
}
