package com.mki.nightsiege.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tor 9b (P1, Spielerzettel 09.11.: "der jump paladin ist unspielbar, er benötigt irgendeine art sustain"):
 * every monster a paladin's jump hit strikes gives him absorption (smite.jump_absorb, one golden heart),
 * at most smite.jump_cap from this source, gone smite.jump_time ticks after the last jump hit.
 *
 * <p>Only the share this source gave is tracked and taken away again, so other absorption (iron hide,
 * barriers, a golden apple) stays. Damage eats this share first: whenever the absorption is seen lower
 * than after the last look, the share shrinks by the difference.</p>
 */
public final class JumpWard {

    private static final class State {
        float own;    // absorption this source gave that is still there
        float seen;   // the player's absorption after the last look
        long last;    // tick of the last jump hit
    }

    private final Map<UUID, State> states = new HashMap<>();

    public static float perTarget() {
        return (float) SkillValues.get("smite", "jump_absorb", 2.0);
    }

    public static float cap() {
        return (float) SkillValues.get("smite", "jump_cap", 8.0);
    }

    public static long time() {
        return (long) SkillValues.get("smite", "jump_time", 200);
    }

    /**
     * A jump hit that struck {@code targets} monsters; {@code current} is the player's absorption now.
     * Returns the absorption to add (never past the cap of this source).
     */
    public float onJumpHit(UUID id, int targets, float current, long now) {
        State s = states.computeIfAbsent(id, k -> new State());
        look(s, current);
        float add = Math.max(0f, Math.min(perTarget() * Math.max(0, targets), cap() - s.own));
        s.own += add;
        s.seen = Math.max(0f, current) + add;
        s.last = now;
        return add;
    }

    /**
     * A regular look (every half second): follows the damage taken and returns the absorption to take
     * away when the time since the last jump hit has run out (0 otherwise).
     */
    public float tick(UUID id, float current, long now) {
        State s = states.get(id);
        if (s == null) {
            return 0f;
        }
        look(s, current);
        if (now - s.last < time()) {
            return 0f;
        }
        states.remove(id);
        return s.own;
    }

    /** The absorption of this source the player still carries. */
    public float own(UUID id) {
        State s = states.get(id);
        return s == null ? 0f : s.own;
    }

    public void forget(UUID id) {
        states.remove(id);
    }

    private static void look(State s, float current) {
        float now = Math.max(0f, current);
        if (now < s.seen) {
            s.own = Math.max(0f, s.own - (s.seen - now));
        }
        s.own = Math.min(s.own, now);
        s.seen = now;
    }
}
