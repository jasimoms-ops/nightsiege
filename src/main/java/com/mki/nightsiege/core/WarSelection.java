package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The war leader's picking (Kriegsführer 3, K1): which squads are picked, and the groups on 1-9 like in a
 * strategy game. Click picks one (Shift adds or takes away), a frame picks many (Shift adds), Ctrl+A all;
 * Ctrl+number keeps the picked as a group, the number picks it again, pressed twice the camera goes there.
 * Pure logic for the client screen (and its tests).
 */
public final class WarSelection {

    /** Two presses of a group's number within this many milliseconds: the camera goes there. */
    public static final long DOUBLE_MS = 400;

    private final Set<Integer> picked = new LinkedHashSet<>();
    private final Map<Integer, List<Integer>> groups = new HashMap<>();
    private int lastGroup = -1;
    private long lastGroupAt;

    /** A click on a squad: only it (or with Shift: in or out of the picked). */
    public void click(int id, boolean add) {
        if (!add) {
            picked.clear();
            picked.add(id);
        } else if (!picked.remove(id)) {
            picked.add(id);
        }
    }

    /** A frame: the squads in it (with Shift: added to the picked). Nothing in it without Shift: none picked. */
    public void box(Collection<Integer> ids, boolean add) {
        if (!add) {
            picked.clear();
        }
        picked.addAll(ids);
    }

    /** Ctrl+A, or a double click on a squad (all of its kind on the screen). */
    public void all(Collection<Integer> ids) {
        picked.clear();
        picked.addAll(ids);
    }

    public void clear() {
        picked.clear();
    }

    /** Ctrl+number: the picked become group n (an empty pick clears it). */
    public void store(int n) {
        if (picked.isEmpty()) {
            groups.remove(n);
        } else {
            groups.put(n, new ArrayList<>(picked));
        }
    }

    /**
     * The number: group n is picked (with Shift: added). True when it was the second press in a row
     * within {@link #DOUBLE_MS} (the camera goes to the group).
     */
    public boolean recall(int n, long nowMs, boolean add) {
        List<Integer> g = groups.get(n);
        boolean twice = n == lastGroup && nowMs - lastGroupAt <= DOUBLE_MS;
        lastGroup = n;
        lastGroupAt = nowMs;
        if (g == null || g.isEmpty()) {
            return false;
        }
        if (!add) {
            picked.clear();
        }
        picked.addAll(g);
        return twice;
    }

    /** Squads gone (dead, refunded) leave the pick and the groups. */
    public void retain(Collection<Integer> alive) {
        picked.retainAll(alive);
        for (List<Integer> g : groups.values()) {
            g.retainAll(alive);
        }
        groups.values().removeIf(List::isEmpty);
    }

    public Set<Integer> picked() {
        return picked;
    }

    public boolean has(int id) {
        return picked.contains(id);
    }

    public boolean isEmpty() {
        return picked.isEmpty();
    }

    /** The first picked squad (the one the hint names), or -1. */
    public int first() {
        return picked.isEmpty() ? -1 : picked.iterator().next();
    }

    public List<Integer> group(int n) {
        List<Integer> g = groups.get(n);
        return g == null ? List.of() : List.copyOf(g);
    }

    /** The group a squad is in (the lowest number), or 0. */
    public int groupOf(int id) {
        int best = 0;
        for (Map.Entry<Integer, List<Integer>> e : groups.entrySet()) {
            if (e.getValue().contains(id) && (best == 0 || e.getKey() < best)) {
                best = e.getKey();
            }
        }
        return best;
    }

    /** The picked as "1,4,7" for the order to the server. */
    public String ids() {
        StringBuilder b = new StringBuilder();
        for (int id : picked) {
            if (!b.isEmpty()) {
                b.append(',');
            }
            b.append(id);
        }
        return b.toString();
    }
}
