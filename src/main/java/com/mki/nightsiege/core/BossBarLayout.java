package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact boss bars (Tor 2.3, Simon 08.11.: "wenn mehrere boss bars da sind wird der bildschirm viel zu voll").
 * From two boss bars of the mod at once, one boss gets the big bar and the others small stacked rows
 * (name and a thin bar, half the height). With one boss everything stays as before. Pure layout: the client
 * draws the slots.
 *
 * <p>Which boss gets the big bar: the one hit last (the one the players fight right now), held for
 * {@link #HOLD_MS} so the bar does not jump between bosses when several are hit at once. The client only
 * knows each bar's share of life, not the life itself, so "most life" could not be compared fairly anyway.
 */
public final class BossBarLayout {

    /** Vanilla: the first bar sits 12 down, every bar takes 19 (10 bar + 9 name). */
    public static final int TOP = 12;
    public static final int BIG = 19;
    /** A small row: name and thin bar side by side, a little over half a big bar. */
    public static final int MINI = 10;
    /** Small rows start this far above the cursor of the next big bar (just below the big bar above). */
    public static final int MINI_LIFT = 11;
    public static final long HOLD_MS = 3000;

    /** One drawn bar: index into the bars as the client lists them, y of the bar (big) or the row's top (mini). */
    public record Slot(int index, int y, boolean mini) {
    }

    private BossBarLayout() {
    }

    /** How many bars take part in the compact group. */
    public static int bossCount(boolean[] boss) {
        int n = 0;
        for (boolean b : boss) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /**
     * The slots, top to bottom. {@code boss[i]} marks the bars that may be compacted; {@code main} is the
     * index of the boss with the big bar (a wrong one falls back to the first boss). The other bars keep
     * their order, the boss group comes after them; like vanilla, drawing stops once a third of the screen is reached.
     */
    public static List<Slot> layout(boolean[] boss, int main, int guiHeight) {
        List<Slot> out = new ArrayList<>();
        boolean compact = bossCount(boss) >= 2;
        if (compact && (main < 0 || main >= boss.length || !boss[main])) {
            main = -1;
            for (int i = 0; i < boss.length && main < 0; i++) {
                if (boss[i]) {
                    main = i;
                }
            }
        }
        int limit = guiHeight / 3;
        int y = TOP;
        // the other bars keep their order; the boss group comes last (vanilla draws the big boss, the rows hang under it)
        for (int i = 0; i < boss.length; i++) {
            if (compact && boss[i]) {
                continue;
            }
            out.add(new Slot(i, y, false));
            y += BIG;
            if (y >= limit) {
                return out;
            }
        }
        if (!compact) {
            return out;
        }
        out.add(new Slot(main, y, false));
        y += BIG;
        for (int j = 0; j < boss.length; j++) {
            if (boss[j] && j != main) {
                if (y >= limit) {
                    return out;
                }
                out.add(new Slot(j, y - MINI_LIFT, true));
                y += MINI;
            }
        }
        return out;
    }

    /** Lowest pixel (GUI scale 1) the slots cover: big bars end 6 below their y (the mod's frame), rows 8 below their top. */
    public static int bottom(List<Slot> slots) {
        int b = 0;
        for (Slot s : slots) {
            b = Math.max(b, s.mini() ? s.y() + 8 : s.y() + 6);
        }
        return b;
    }

    /** Remembers which boss was hit last, to pick the big bar. */
    public static final class Focus<K> {
        private final Map<K, Float> last = new HashMap<>();
        private final Map<K, Long> hit = new HashMap<>();
        private K main;

        /** The key of the boss with the big bar, from the bosses now shown and their share of life. */
        public K pick(List<K> ids, List<Float> progress, long now) {
            if (ids.isEmpty()) {
                last.clear();
                hit.clear();
                main = null;
                return null;
            }
            last.keySet().retainAll(ids);
            hit.keySet().retainAll(ids);
            K latest = ids.get(0);
            long latestAt = Long.MIN_VALUE;
            for (int i = 0; i < ids.size(); i++) {
                K id = ids.get(i);
                float p = progress.get(i);
                Float before = last.put(id, p);
                if (before != null && p < before - 0.0001f) {
                    hit.put(id, now);
                }
                long at = hit.getOrDefault(id, Long.MIN_VALUE);
                if (at > latestAt) {
                    latestAt = at;
                    latest = id;
                }
            }
            if (main == null || !ids.contains(main)) {
                main = latest;
            } else if (!latest.equals(main) && now - hit.getOrDefault(main, Long.MIN_VALUE / 2) >= HOLD_MS) {
                main = latest;
            }
            return main;
        }
    }
}
