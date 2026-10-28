package com.mki.nightsiege.core;

import java.util.Locale;

/**
 * The curse stones of the altar (update 2 "Fluch & Rache", Simon 28 October 2026): each one makes
 * the next night harder; a night won under curses pays better (crates, an extra crate, skill points).
 */
public enum CurseKind {
    /** Monsters have more life. */
    TOUGH("zaeh", "minecraft:rotten_flesh", 0xFF7A9A4A),
    /** Monsters hit harder. */
    BLOODTHIRST("blutdurst", "minecraft:redstone", 0xFFC0303A),
    /** More monsters in every wave. */
    HORDE("uebermacht", "minecraft:zombie_head", 0xFF6A8A5A),
    /** Monsters run faster. */
    HASTE("eile", "minecraft:sugar", 0xFFE0E0F0),
    /** The defenders heal less. */
    BLIGHT("siechtum", "minecraft:fermented_spider_eye", 0xFF8A3A6A),
    /** A champion comes with every wave. */
    CHAMPIONS("champions", "minecraft:golden_sword", 0xFFE8C040),
    /** The waves come faster. */
    ONSLAUGHT("sturmlauf", "minecraft:goat_horn", 0xFFB08050),
    /** Towers and traps hit softer. */
    BLUNT("stumpf", "minecraft:chipped_anvil", 0xFF8A8A9A);

    private final String id;
    private final String icon;
    private final int color;

    CurseKind(String id, String icon, int color) {
        this.id = id;
        this.icon = icon;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String icon() {
        return icon;
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "nightsiege.curse." + id;
    }

    public static CurseKind byId(String id) {
        if (id == null) {
            return null;
        }
        String want = id.trim().toLowerCase(Locale.ROOT);
        for (CurseKind k : values()) {
            if (k.id.equals(want) || k.name().toLowerCase(Locale.ROOT).equals(want)) {
                return k;
            }
        }
        return null;
    }

    /**
     * The odds of a crate from a cursed night: every curse moves the odds half a step along the
     * chain normal night → event night → fragment night → the cursed top table. {@code start} is
     * the step of the night itself (0 normal, 1 event, 2 fragment).
     */
    public static int[] shiftOdds(int[][] chain, int start, int curses) {
        if (chain == null || chain.length == 0) {
            return new int[5];
        }
        double at = Math.min(chain.length - 1.0, Math.max(0, start) + Math.max(0, curses) * 0.5);
        int low = (int) Math.floor(at);
        int high = Math.min(chain.length - 1, low + 1);
        double t = at - low;
        int[] a = chain[low];
        int[] b = chain[high];
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (int) Math.round(a[i] * (1.0 - t) + b[i] * t);
        }
        return out;
    }
}
