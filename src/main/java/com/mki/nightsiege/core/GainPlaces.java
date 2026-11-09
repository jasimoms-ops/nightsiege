package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The gain places 1-3 (Tor 3.2b, Simon 09.11.: "welche drei Gewinne auf 1-3 liegen, wählt der Spieler
 * im Inventar"): a click on a gain's field in the inventory puts the next gain the player owns, that
 * sits on no other place, onto this place. Pure logic, so it can be tested on its own.
 */
public final class GainPlaces {

    private GainPlaces() {
    }

    /**
     * The places after a click on place {@code place}, or null when nothing changes (no such place,
     * or every owned gain already has a place).
     *
     * @param order the gains on the places now, place 1 first
     * @param owned every gain ability the class owns, in its fixed order
     */
    public static List<String> next(List<String> order, List<String> owned, int place) {
        if (order == null || owned == null || place < 0 || place >= order.size()) {
            return null;
        }
        int from = owned.indexOf(order.get(place));
        for (int step = 1; step <= owned.size(); step++) {
            String candidate = owned.get(Math.floorMod(from + step, owned.size()));
            if (!order.contains(candidate)) {
                List<String> out = new ArrayList<>(order);
                out.set(place, candidate);
                return out;
            }
        }
        return null;
    }
}
