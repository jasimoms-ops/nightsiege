package com.mki.nightsiege.core;

import java.util.Random;

/** Chooses the event for one siege night. */
public final class EventPicker {

    private EventPicker() {
    }

    public static SiegeEvent pick(int level, Random random, SiegeConfig cfg) {
        if (!cfg.eventsEnabled || level < cfg.eventMinLevel) {
            return SiegeEvent.NONE;
        }
        if (random.nextDouble() >= Math.min(1.0, Math.max(0.0, cfg.eventChance))) {
            return SiegeEvent.NONE;
        }
        SiegeEvent[] events = {SiegeEvent.BLOOD_MOON, SiegeEvent.THEME_WAVE, SiegeEvent.ECLIPSE,
                SiegeEvent.FOG, SiegeEvent.THUNDER, SiegeEvent.NETHER_RIFT, SiegeEvent.FROST};
        int[] weights = {cfg.bloodMoonWeight, cfg.themeWaveWeight, cfg.eclipseWeight,
                cfg.fogWeight, cfg.thunderWeight, cfg.netherRiftWeight, cfg.frostWeight};
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return SiegeEvent.NONE;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < events.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return events[i];
            }
        }
        return SiegeEvent.NONE;
    }
}
