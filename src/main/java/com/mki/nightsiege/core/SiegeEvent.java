package com.mki.nightsiege.core;

/** The special shape a siege night can take. */
public enum SiegeEvent {
    /** An ordinary siege. */
    NONE,
    /** More mobs, tougher and harder hitting. */
    BLOOD_MOON,
    /** The whole night is made of a single mob type. */
    THEME_WAVE,
    /** At noon the sun goes dark and the siege comes by day; the undead do not burn. */
    ECLIPSE,
    /** Thick fog, silent monsters, more spiders and creepers. */
    FOG,
    /** A storm: lightning hits the attackers and charges creepers, Tesla coils hit harder. */
    THUNDER,
    /** Rifts open around the base and Nether monsters pour through. */
    NETHER_RIFT,
    /** A blizzard: strays instead of skeletons, the cold slows anyone out in the open. */
    FROST;

    /** Name of the event's look in the sky, or an empty string if it has none. */
    public String atmosphere() {
        return switch (this) {
            case BLOOD_MOON, ECLIPSE, FOG, THUNDER, NETHER_RIFT, FROST -> name();
            default -> "";
        };
    }

    public double mobCountMultiplier(SiegeConfig cfg) {
        if (this == ECLIPSE) {
            return cfg.eclipseMobMultiplier;
        }
        return this == BLOOD_MOON ? cfg.bloodMoonMobMultiplier : 1.0;
    }

    public double healthMultiplier(SiegeConfig cfg) {
        return this == BLOOD_MOON ? cfg.bloodMoonHealthMultiplier : 1.0;
    }

    public double damageMultiplier(SiegeConfig cfg) {
        return this == BLOOD_MOON ? cfg.bloodMoonDamageMultiplier : 1.0;
    }
}
