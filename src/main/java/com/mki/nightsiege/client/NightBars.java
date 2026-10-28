package com.mki.nightsiege.client;

import com.mki.nightsiege.core.NightBarIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bars at the top in the kit bar's look (Simon, 29 October 2026): dark iron with a gold rim,
 * the fill in the bar's colour with a light top line, notches, small gold studs at the ends. Bosses,
 * champions, the nemesis and the camp's workbench draw a light trail of what they just lost; the
 * camp's bar is cut into ten wooden segments.
 */
public final class NightBars {

    private static final Map<UUID, float[]> TRAILS = new HashMap<>();

    private NightBars() {
    }

    /** False for bars that are not the mod's: those stay vanilla. */
    public static boolean draw(GuiGraphicsExtractor gui, int x, int y, BossEvent event) {
        NightBarIds.Kind kind = NightBarIds.kindOf(event.getId());
        if (kind == null) {
            return false;
        }
        int w = 182;
        float progress = Math.max(0f, Math.min(1f, event.getProgress()));
        // frame: outline, gold rim, dark inside (7 high, the vanilla bar is 5)
        gui.fill(x - 2, y - 1, x + w + 2, y + 6, 0xFF0A0807);
        gui.fill(x - 1, y, x + w + 1, y + 5, 0xFFC9A55A);
        gui.fill(x - 1, y + 4, x + w + 1, y + 5, 0xFF6B5222);
        gui.fill(x, y + 1, x + w, y + 4, 0xFF15110E);
        int body = color(event.getColor(), kind);
        int light = lighter(body);
        int fill = Math.round(w * progress);
        boolean trail = kind == NightBarIds.Kind.BOSS || kind == NightBarIds.Kind.NEMESIS
                || kind == NightBarIds.Kind.DUNGEON || kind == NightBarIds.Kind.CAMP || kind == NightBarIds.Kind.MACHINE;
        if (trail) {
            long now = System.currentTimeMillis();
            float[] t = TRAILS.computeIfAbsent(event.getId(), k -> new float[] {progress, now, now});
            if (progress < t[0] - 0.0001f && now > (long) t[2]) {
                float dt = Math.min(0.1f, (now - (long) t[1]) / 1000f);
                t[0] = Math.max(progress, t[0] - dt * 0.35f);
            } else if (progress >= t[0]) {
                t[0] = progress;
                t[2] = now + 400;
            }
            t[1] = now;
            int tw = Math.round(w * t[0]);
            if (tw > fill) {
                gui.fill(x + fill, y + 1, x + tw, y + 4, 0xFFE8C9A0);
            }
            if (TRAILS.size() > 64) {
                TRAILS.clear();
            }
        }
        if (fill > 0) {
            gui.fill(x, y + 1, x + fill, y + 4, body);
            gui.fill(x, y + 1, x + fill, y + 2, light);
        }
        int notches = switch (event.getOverlay()) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> kind == NightBarIds.Kind.CAMP ? 10 : 0;
        };
        for (int i = 1; i < notches; i++) {
            int nx = x + Math.round(w * i / (float) notches);
            gui.fill(nx, y + 1, nx + 1, y + 4, kind == NightBarIds.Kind.CAMP ? 0xFF3A2410 : 0x80000000);
        }
        // gold studs at the ends
        for (int sx : new int[] {x - 4, x + w + 2}) {
            gui.fill(sx, y + 1, sx + 2, y + 4, 0xFF0A0807);
            gui.fill(sx, y + 2, sx + 2, y + 3, 0xFFE8C46A);
        }
        return true;
    }

    private static int color(BossEvent.BossBarColor c, NightBarIds.Kind kind) {
        if (kind == NightBarIds.Kind.CAMP) {
            return 0xFFB8722E;   // wood orange
        }
        return switch (c) {
            case PINK -> 0xFFC05A9A;
            case BLUE -> 0xFF3F7FC8;
            case RED -> 0xFFA8261F;
            case GREEN -> 0xFF4E9A3A;
            case YELLOW -> 0xFFD0A030;
            case PURPLE -> 0xFF7A4AC0;
            default -> 0xFFB8B0A0;
        };
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 60);
        int b = Math.min(255, (c & 0xFF) + 60);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
