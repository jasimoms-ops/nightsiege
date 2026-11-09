package com.mki.nightsiege.core;

import java.util.Set;

/**
 * Tor 9b (Simon 09.11.: "slow und blind Effekte durch illager zauberer viel zu stark gegen fernkampf klassen"):
 * what an illager caster (evoker, illusioner, iceologer and their spells) may still do to a player.
 * Blindness at most 2 s, slowness at most level I and 3 s, the same effect from a caster only 6 s after
 * the last one began. Effects of players and effects on monsters are not touched (see game/CasterCurbs).
 */
public final class CasterCurb {

    public enum Kind { BLIND, SLOW }

    public static final int BLIND_MAX_TICKS = 40;
    public static final int SLOW_MAX_TICKS = 60;
    public static final int SLOW_MAX_AMPLIFIER = 0;   // level I
    public static final int LOCK_TICKS = 120;

    /** Casters and their spell entities that are not a SpellcasterIllager themselves. */
    static final Set<String> CASTER_IDS = Set.of("minecraft:evoker", "minecraft:illusioner",
            "friendsandfoes:iceologer", "friendsandfoes:illusioner", "friendsandfoes:ice_chunk");

    private CasterCurb() {
    }

    public static boolean casterId(String typeId) {
        return typeId != null && CASTER_IDS.contains(typeId);
    }

    /**
     * The effect a caster may give: {duration, amplifier}, or null while the lock of the last one
     * (begun at {@code last}, below 0 for none) still runs. A negative duration means endless.
     */
    public static int[] cap(Kind kind, int duration, int amplifier, long now, long last) {
        if (last >= 0 && now - last < LOCK_TICKS) {
            return null;
        }
        int max = kind == Kind.BLIND ? BLIND_MAX_TICKS : SLOW_MAX_TICKS;
        int ticks = duration < 0 ? max : Math.min(duration, max);
        int amp = kind == Kind.SLOW ? Math.min(amplifier, SLOW_MAX_AMPLIFIER) : amplifier;
        return new int[]{ticks, amp};
    }
}
