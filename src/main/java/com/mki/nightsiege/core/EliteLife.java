package com.mki.nightsiege.core;

import java.util.Map;

/**
 * The elite's extra life by monster type (Tor 9b, Simon 09.11.: "breeze elites haben zu viel hp").
 * Monsters that are hard to hit get half the bonus: the breeze jumps around and turns arrows back,
 * the vex is small, flies fast and through walls. The blaze stays at the full bonus (big, slow,
 * every arrow hits it), the phantom never becomes an elite (Elites.eligible).
 */
public final class EliteLife {

    /** Share of the bonus (eliteMobLife − 1) these types get; all others get all of it. */
    static final Map<String, Double> SHARE = Map.of(
            "minecraft:breeze", 0.5,
            "minecraft:vex", 0.5);

    private EliteLife() {
    }

    /** The elite's extra life as a multiplier on its total life (ADD_MULTIPLIED_TOTAL), 0 for none. */
    public static double bonus(String typeId, double eliteMobLife) {
        if (eliteMobLife <= 1.0) {
            return 0.0;
        }
        return (eliteMobLife - 1.0) * SHARE.getOrDefault(typeId, 1.0);
    }
}
