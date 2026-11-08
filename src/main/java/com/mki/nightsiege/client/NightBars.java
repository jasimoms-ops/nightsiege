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

    /** Compact boss bars (Tor 2.3): off only for the gametest's "before" picture. */
    public static boolean compact = true;

    private static final com.mki.nightsiege.core.BossBarLayout.Focus<UUID> FOCUS = new com.mki.nightsiege.core.BossBarLayout.Focus<>();

    /** The boss with the big bar this frame and the bosses drawn as small rows under it (Tor 2.3). */
    private static UUID mainId;
    private static java.util.List<BossEvent> minis = java.util.List.of();

    /**
     * The bars vanilla draws, in order (Tor 2.3). From two boss bars of the mod at once, only the boss with
     * the big bar stays in the list, as the last one; the others are drawn as small rows under it
     * ({@link #drawMinis}). Vanilla's loop, its other bars (night, camp, Wither, dragon) and other mods' hooks
     * run as always; with no or one boss the list is untouched.
     */
    public static <E extends BossEvent> java.util.Collection<E> order(java.util.Collection<E> all) {
        mainId = null;
        minis = java.util.List.of();
        if (!compact || all.size() < 2) {
            return all;
        }
        java.util.List<E> bars = new java.util.ArrayList<>(all);
        boolean[] boss = new boolean[bars.size()];
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        java.util.List<Float> progress = new java.util.ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            boss[i] = compactable(bars.get(i));
            if (boss[i]) {
                ids.add(bars.get(i).getId());
                progress.add(bars.get(i).getProgress());
            }
        }
        UUID pick = FOCUS.pick(ids, progress, System.currentTimeMillis());
        if (ids.size() < 2) {
            return all;
        }
        int main = -1;
        for (int i = 0; i < bars.size(); i++) {
            if (bars.get(i).getId().equals(pick)) {
                main = i;
            }
        }
        java.util.List<E> shown = new java.util.ArrayList<>();
        java.util.List<BossEvent> rows = new java.util.ArrayList<>();
        for (com.mki.nightsiege.core.BossBarLayout.Slot slot
                : com.mki.nightsiege.core.BossBarLayout.layout(boss, main, Integer.MAX_VALUE)) {
            if (slot.mini()) {
                rows.add(bars.get(slot.index()));
            } else {
                shown.add(bars.get(slot.index()));
                if (boss[slot.index()]) {
                    mainId = bars.get(slot.index()).getId();
                }
            }
        }
        minis = rows;
        return shown;
    }

    /** The small rows under the big boss bar drawn at {@code y}; like vanilla, nothing past a third of the screen. */
    public static void drawMinis(GuiGraphicsExtractor gui, int x, int y, BossEvent event) {
        if (mainId == null || !mainId.equals(event.getId())) {
            return;
        }
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int next = y + com.mki.nightsiege.core.BossBarLayout.BIG;
        for (BossEvent row : minis) {
            if (next >= gui.guiHeight() / 3) {
                return;
            }
            drawMini(gui, font, x, next - com.mki.nightsiege.core.BossBarLayout.MINI_LIFT, row);
            next += com.mki.nightsiege.core.BossBarLayout.MINI;
        }
    }

    /** True for the bars that share the compact group: bosses (the Wächter too) and the nemesis. */
    public static boolean compactable(BossEvent event) {
        NightBarIds.Kind kind = NightBarIds.kindOf(event.getId());
        return kind == NightBarIds.Kind.BOSS || kind == NightBarIds.Kind.NEMESIS;
    }

    /**
     * A small row under the big boss bar (Tor 2.3): the name on the left, a thin bar in iron and gold on the
     * right, 10 high instead of 19.
     */
    public static void drawMini(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font, int x, int top, BossEvent event) {
        int nameW = 74;
        int bx = x + 78;
        int w = 182 - 78;
        net.minecraft.network.chat.FormattedText name = event.getName();
        if (font.width(name) > nameW) {
            name = net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(name, nameW - font.width("…")),
                    net.minecraft.network.chat.FormattedText.of("…"));
        }
        gui.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(name), x, top + 1, 0xFFE0D2B4);
        int y = top + 2;
        float progress = Math.max(0f, Math.min(1f, event.getProgress()));
        gui.fill(bx - 1, y, bx + w + 1, y + 5, 0xFF0A0807);
        gui.fill(bx, y + 1, bx + w, y + 4, 0xFF6B5222);
        gui.fill(bx, y + 1, bx + w, y + 2, 0xFFC9A55A);
        gui.fill(bx, y + 2, bx + w, y + 4, 0xFF15110E);
        int fill = Math.round(w * progress);
        if (fill > 0) {
            int body = color(event.getColor(), NightBarIds.kindOf(event.getId()));
            gui.fill(bx, y + 2, bx + fill, y + 4, body);
            gui.fill(bx, y + 2, bx + fill, y + 3, lighter(body));
        }
        gui.fill(bx + w + 1, y + 1, bx + w + 3, y + 4, 0xFF0A0807);
        gui.fill(bx + w + 1, y + 2, bx + w + 3, y + 3, 0xFFE8C46A);
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
