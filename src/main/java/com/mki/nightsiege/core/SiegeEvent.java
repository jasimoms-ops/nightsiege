package com.mki.nightsiege.core;

/** The special shape a siege night can take. */
public enum SiegeEvent {
    /** An ordinary siege. */
    NONE,
    /** More mobs, tougher and harder hitting. */
    BLOOD_MOON,
    /** One named champion leads the attack. */
    ELITE,
    /** The whole night is made of a single mob type. */
    THEME_WAVE;

    public double mobCountMultiplier(SiegeConfig cfg) {
        return this == BLOOD_MOON ? cfg.bloodMoonMobMultiplier : 1.0;
    }

    public double healthMultiplier(SiegeConfig cfg) {
        return this == BLOOD_MOON ? cfg.bloodMoonHealthMultiplier : 1.0;
    }

    public double damageMultiplier(SiegeConfig cfg) {
        return this == BLOOD_MOON ? cfg.bloodMoonDamageMultiplier : 1.0;
    }
}
