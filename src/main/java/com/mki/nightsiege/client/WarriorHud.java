package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.core.WarriorHudData;
import com.mki.nightsiege.game.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The warrior's combo chain under the crosshair and his Kampfgeist without delay (Simon 05.11.): small
 * dots fill with each hit of the chain (four, the axe fighter's three); the finisher flashes them (gold
 * when empowered); with a full meter the last dot has a gold rim, as the next finisher will be empowered.
 * The server sends the state on every hit ("warrior"); the kit HUD's bar and F field take the new value at
 * once instead of waiting for the state that comes once a second.
 *
 * <p>Kombo 2 (Simon 06.11.: "Animation sollte es da auch geben"): a dot pops up when it fills; with only the
 * finisher left its dot pulses (the finisher waits a few seconds then); a thin bar under the dots runs out
 * until the chain breaks; the finisher bursts the dots outwards.</p>
 */
public final class WarriorHud {

    private static final int WARRIOR = 0xFFFF6B4A;
    private static final int GOLD = 0xFFFFD84A;
    private static final long POP_MS = 160L;
    private static final long FLASH_MS = 380L;

    private static WarriorHudData data;
    private static long receivedAt;
    private static int lastFin;
    private static long flashUntil;
    private static boolean flashGold;
    private static int shownChain;
    private static final long[] POPPED = new long[8];

    private WarriorHud() {
    }

    /** A state from the server (ScreenPayload "warrior"). */
    public static void set(String json) {
        WarriorHudData d = Json.fromJson(json, WarriorHudData.class);
        if (d == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (data != null && d.fin != lastFin) {
            flashUntil = now + FLASH_MS;   // a finisher: the dots burst
            flashGold = d.finEmp;
        }
        int before = chain();
        if (d.chain < before) {
            before = 0;   // a new chain began
        }
        if (d.chain > before) {
            for (int i = Math.max(0, before); i < Math.min(POPPED.length, d.chain); i++) {
                POPPED[i] = now;   // these dots just filled: they pop
            }
        }
        lastFin = d.fin;
        data = d;
        receivedAt = now;
        shownChain = d.chain;
        UltState s = UltHud.state();
        if (s != null && "nightsiege.kithud.spirit".equals(s.gaugeName)) {
            s.gaugeValue = d.v;
            s.gaugeGlow = d.v + 1.0e-4f >= d.glow;
            s.ab2Dim = d.dim;
        }
    }

    /** A new connection: nothing left from before. */
    public static void clear() {
        data = null;
        lastFin = 0;
        flashUntil = 0L;
        shownChain = 0;
        java.util.Arrays.fill(POPPED, 0L);
    }

    /** For the tests: the meter and the chain as the client knows them. */
    public static float value() {
        return data == null ? 0f : data.v;
    }

    /** Milliseconds the running chain still holds (as the server sent it, counted down here). */
    private static long leftMs(WarriorHudData d, long now) {
        int left = d.left > 0 ? d.left : d.window;
        return left * 50L - (now - receivedAt);
    }

    public static int chain() {
        WarriorHudData d = data;
        if (d == null || shownChain <= 0 || leftMs(d, System.currentTimeMillis()) < 0L) {
            return 0;
        }
        return shownChain;
    }

    /** For the tests: the finisher is ready (only it is left of the chain). */
    public static boolean ready() {
        WarriorHudData d = data;
        int c = chain();
        return d != null && d.hits > 1 && c == d.hits - 1;
    }

    public static int finishers() {
        return lastFin;
    }

    /** The dots under the crosshair. */
    public static void draw(GuiGraphicsExtractor gui) {
        WarriorHudData d = data;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.player == null || mc.player.isSpectator() || DroneClient.localDrone()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean flash = now < flashUntil;
        int chain = chain();
        if (chain <= 0 && !flash) {
            return;
        }
        int n = Math.max(1, Math.min(8, d.hits));
        boolean ready = !flash && n > 1 && chain == n - 1;
        int size = 3;
        int gap = 2;
        int total = n * size + (n - 1) * gap;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int x0 = cx - total / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 9;
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 90.0));
        int x = x0;
        for (int i = 0; i < n; i++) {
            boolean lit = flash || i < chain;
            boolean last = i == n - 1;
            int grow = 0;
            int color;
            if (flash) {
                color = flashGold ? GOLD : 0xFFFFF2E0;
            } else if (lit) {
                long age = now - POPPED[Math.min(i, POPPED.length - 1)];
                if (age >= 0 && age < POP_MS) {
                    grow = 1;   // just filled: a short pop, white at first
                    color = mix(0xFFFFFFFF, WARRIOR, age / (float) POP_MS);
                } else {
                    color = WARRIOR;
                }
            } else if (ready && last) {
                color = mix(0x60FFFFFF, d.emp ? GOLD : WARRIOR, pulse);   // the waiting finisher pulses
                grow = pulse > 0.6f ? 1 : 0;
            } else {
                color = 0x50FFFFFF;
            }
            int frame = last && d.emp && !flash ? GOLD : ready && last ? mix(0xA0000000, 0xFFFFFFFF, pulse * 0.5f) : 0xA0000000;
            gui.fill(x - 1 - grow, y - 1 - grow, x + size + 1 + grow, y + size + 1 + grow, frame);
            gui.fill(x - grow, y - grow, x + size + grow, y + size + grow, color);
            x += size + gap;
        }
        if (flash) {
            // The finisher: eight sparks fly out of the dots and fade.
            float t = 1f - (flashUntil - now) / (float) FLASH_MS;
            int dist = Math.round(3 + t * 11);
            int col = withAlpha(flashGold ? GOLD : WARRIOR, 1f - t);
            int cy = y + size / 2;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                int px = cx + (int) Math.round(Math.cos(a) * (dist + total / 2.0));
                int py = cy + (int) Math.round(Math.sin(a) * dist * 0.7);
                gui.fill(px - 1, py - 1, px + 1, py + 1, col);
            }
        } else {
            // The chain runs out: a thin bar under the dots.
            long full = (ready ? Math.max(d.window, d.ready) : d.window) * 50L;
            float share = full <= 0 ? 0f : Math.max(0f, Math.min(1f, leftMs(d, now) / (float) full));
            int w = Math.round(total * share);
            if (w > 0) {
                gui.fill(x0, y + size + 2, x0 + w, y + size + 3, withAlpha(ready ? GOLD : WARRIOR, 0.75f));
            }
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** From {@code a} to {@code b} by {@code t} (0–1), every channel. */
    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
