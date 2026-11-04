package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the stacks of a night kit go (update 6, Simon 4 November 2026: "ein Feature, um sein
 * Kit-Inventar plus ausgewählte Items für die Nacht zu personalisieren"; "alles, was nicht auf Item-
 * Slots kommt, darf man unbegrenzt mitnehmen, die Items, die auf den Slots liegen, müssen ausgewählt
 * werden"; "so viele, wie in die Hotbar passen").
 *
 * <p>The kit's own things (weapon, ammunition, food, stone, tools, traps) go where the player keeps
 * them, anywhere in the inventory. The crate items (things a crate gain puts into a slot of their
 * own: throwing spears, barricades, healing potions …) come only as far as they are chosen and only
 * into the hotbar: they share its nine places with the kit things the player keeps there. A crate
 * item without a free hotbar place stays at home tonight. Without a choice the crate items come on
 * their own, the rarest first; tools and stone then go up into the inventory.</p>
 *
 * <p>Pure logic, no game objects, so it can be tested on its own.</p>
 */
public final class KitArrange {

    public static final int HOTBAR = 9;
    public static final int SLOTS = 36;

    private KitArrange() {
    }

    /**
     * One stack of the kit: its layout key (item, gadget, potion, crate gain), the crate gain it comes
     * from ("" for the kit's own things), whether it belongs in the back (tools, stone) and the
     * rank of its gain (higher = rarer; for the automatic choice).
     */
    public record Thing(String key, String gain, boolean back, int rank) {
        public boolean crate() {
            return gain != null && !gain.isEmpty();
        }
    }

    /** The layout keys numbered for several stacks of one kind: "key", "key#2", "key#3" … */
    public static List<String> numbered(List<Thing> things) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (Thing t : things) {
            int n = seen.merge(t.key(), 1, Integer::sum);
            out.add(n == 1 ? t.key() : t.key() + "#" + n);
        }
        return out;
    }

    /**
     * The slot of every stack (0-35), or -1 when it stays at home tonight.
     *
     * @param layout where the player keeps things (numbered key → slot); may be null
     * @param choice the crate gains chosen for the hotbar, in order; null = automatic (all, rarest first)
     */
    public static int[] place(List<Thing> things, Map<String, Integer> layout, List<String> choice) {
        int n = things.size();
        int[] slot = new int[n];
        java.util.Arrays.fill(slot, -1);
        boolean[] used = new boolean[SLOTS];
        List<String> keys = numbered(things);
        Map<String, Integer> lay = layout == null ? Map.of() : layout;

        // The crate stacks that come along, in the order they get the free hotbar places.
        List<Integer> crate = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thing t = things.get(i);
            if (t.crate() && (choice == null || choice.contains(t.gain()))) {
                crate.add(i);
            }
        }
        if (choice == null) {
            crate.sort(Comparator.comparingInt((Integer i) -> -things.get(i).rank()));
        } else {
            crate.sort(Comparator.comparingInt((Integer i) -> choice.indexOf(things.get(i).gain())));
        }

        // 1. Where the player keeps them: the kit's own things anywhere, crate items in the hotbar only.
        for (int i = 0; i < n; i++) {
            if (things.get(i).crate()) {
                continue;
            }
            Integer s = lay.get(keys.get(i));
            if (s != null && s >= 0 && s < SLOTS && !used[s]) {
                slot[i] = s;
                used[s] = true;
            }
        }
        for (int i : crate) {
            Integer s = lay.get(keys.get(i));
            if (s != null && s >= 0 && s < HOTBAR && !used[s]) {
                slot[i] = s;
                used[s] = true;
            }
        }
        // 2. The kit's own things without a place: fighting things into the hotbar, tools and stone
        //    into the inventory (so the crate items find room), whatever is left anywhere.
        for (int i = 0; i < n; i++) {
            Thing t = things.get(i);
            if (t.crate() || slot[i] >= 0) {
                continue;
            }
            int s = t.back() ? free(used, HOTBAR, SLOTS) : free(used, 0, HOTBAR);
            if (s < 0) {
                s = t.back() ? free(used, 0, HOTBAR) : free(used, HOTBAR, SLOTS);
            }
            if (s >= 0) {
                slot[i] = s;
                used[s] = true;
            }
        }
        // 3. Crate items without a place: the free hotbar places, the rest stays at home.
        for (int i : crate) {
            if (slot[i] >= 0) {
                continue;
            }
            int s = free(used, 0, HOTBAR);
            if (s >= 0) {
                slot[i] = s;
                used[s] = true;
            }
        }
        return slot;
    }

    private static int free(boolean[] used, int from, int to) {
        for (int s = from; s < to; s++) {
            if (!used[s]) {
                return s;
            }
        }
        return -1;
    }

    /**
     * Cleans a layout sent from the kit page: crate items only in the hotbar, every slot once, only
     * keys the kit has.
     */
    public static Map<String, Integer> clean(List<Thing> things, Map<String, Integer> sent) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (sent == null) {
            return out;
        }
        List<String> keys = numbered(things);
        boolean[] used = new boolean[SLOTS];
        for (int i = 0; i < things.size(); i++) {
            Integer s = sent.get(keys.get(i));
            if (s == null || s < 0 || s >= SLOTS || used[s]) {
                continue;
            }
            if (things.get(i).crate() && s >= HOTBAR) {
                continue;
            }
            used[s] = true;
            out.put(keys.get(i), s);
        }
        return out;
    }
}
