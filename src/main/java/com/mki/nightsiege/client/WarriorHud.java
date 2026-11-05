package com.mki.nightsiege.client;

import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.core.WarriorHudData;
import com.mki.nightsiege.game.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The warrior's combo chain under the crosshair and his Kampfgeist without delay (Simon 05.11.): four
 * small dots fill with each hit of the chain; the finisher flashes them (gold when empowered); with a
 * full meter the last dot has a gold rim, as the next finisher will be empowered. The server sends the
 * state on every hit ("warrior"); the kit HUD's bar and F field take the new value at once instead of
 * waiting for the state that comes once a second.
 */
public final class WarriorHud {

    private static final int WARRIOR = 0xFFFF6B4A;
    private static final int GOLD = 0xFFFFD84A;

    private static WarriorHudData data;
    private static long receivedAt;
    private static int lastFin;
    private static long flashUntil;
    private static boolean flashGold;

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
            flashUntil = now + 350L;   // a finisher: the dots flash
            flashGold = d.finEmp;
        }
        lastFin = d.fin;
        data = d;
        receivedAt = now;
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
    }

    /** For the tests: the meter and the chain as the client knows them. */
    public static float value() {
        return data == null ? 0f : data.v;
    }

    public static int chain() {
        WarriorHudData d = data;
        if (d == null || System.currentTimeMillis() - receivedAt > d.window * 50L) {
            return 0;
        }
        return d.chain;
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
        int size = 3;
        int gap = 2;
        int total = n * size + (n - 1) * gap;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - total / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 9;
        for (int i = 0; i < n; i++) {
            boolean lit = flash || i < chain;
            boolean last = i == n - 1;
            int frame = last && d.emp && !flash ? GOLD : 0xA0000000;
            int color = flash ? (flashGold ? GOLD : 0xFFFFF2E0) : lit ? WARRIOR : 0x50FFFFFF;
            gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, frame);
            gui.fill(x, y, x + size, y + size, color);
            x += size + gap;
        }
    }
}
