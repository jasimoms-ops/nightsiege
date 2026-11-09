package com.mki.nightsiege.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tor 4b (Prüfer-Fund): the shared hit lock of one player's Statikfelder. However many fields of the
 * same player cover a monster (Netzwerk lays one at every own trap near the spot), it is stung at most
 * once per pulse; each field alone works as before.
 */
public final class StingLock {

    /** Player → monster (entity id) → game time of its last sting. */
    private final Map<UUID, Map<Integer, Long>> last = new HashMap<>();

    /** True (and noted) when this player may sting this monster now; false within {@code period} ticks of the last sting. */
    public boolean tryHit(UUID player, int monster, long now, long period) {
        Map<Integer, Long> own = last.computeIfAbsent(player, k -> new HashMap<>());
        Long at = own.get(monster);
        if (at != null && now >= at && now - at < period) {
            return false;
        }
        own.put(monster, now);
        if (own.size() > 512) {
            own.values().removeIf(t -> now - t >= period);   // keep it small over a long night
        }
        return true;
    }

    /** End of a night, or the player's fields ended. */
    public void clear() {
        last.clear();
    }

    public void forget(UUID player) {
        last.remove(player);
    }
}
