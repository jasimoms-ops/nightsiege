package com.mki.nightsiege.core;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The bars at the top (design update, Simon 29 October 2026): every boss bar of the mod gets an id
 * the client can tell apart, so it draws it in the mod's own iron and gold look. The kind sits in
 * the id's high bits; no extra message travels.
 */
public final class NightBarIds {

    public enum Kind { NIGHT, PREP, BOSS, NEMESIS, CAMP, MACHINE, DUNGEON }

    private static final long MARK = 0x4E5342L;   // "NSB"

    private NightBarIds() {
    }

    public static UUID make(Kind kind) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long high = (MARK << 40) | ((long) kind.ordinal() << 32) | (r.nextLong() & 0xFFFF0FFFL) | 0x4000L;
        long low = (r.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    /** The kind of a bar of the mod, or null for every other bar. */
    public static Kind kindOf(UUID id) {
        if (id == null || (id.getMostSignificantBits() >>> 40) != MARK) {
            return null;
        }
        int k = (int) ((id.getMostSignificantBits() >>> 32) & 0xFF);
        return k < Kind.values().length ? Kind.values()[k] : null;
    }
}
