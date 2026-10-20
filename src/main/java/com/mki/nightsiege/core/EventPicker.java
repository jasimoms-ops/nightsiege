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
        int blood = Math.max(0, cfg.bloodMoonWeight);
        int elite = Math.max(0, cfg.eliteWeight);
        int theme = Math.max(0, cfg.themeWaveWeight);
        int total = blood + elite + theme;
        if (total <= 0) {
            return SiegeEvent.NONE;
        }
        int roll = random.nextInt(total);
        if (roll < blood) {
            return SiegeEvent.BLOOD_MOON;
        }
        if (roll < blood + elite) {
            return SiegeEvent.ELITE;
        }
        return SiegeEvent.THEME_WAVE;
    }
}
