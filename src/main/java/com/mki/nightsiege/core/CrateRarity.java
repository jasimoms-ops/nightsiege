package com.mki.nightsiege.core;

import java.util.Locale;

/** How rare a gain from a reward crate is, and what a duplicate pays in skill points. */
public enum CrateRarity {
    COMMON(0xA3A3AE, 1),
    UNCOMMON(0x5DC160, 2),
    RARE(0x4C8DFF, 4),
    EPIC(0xB565FF, 8),
    LEGENDARY(0xF5B83D, 15);

    private final int color;
    private final int duplicatePoints;

    CrateRarity(int color, int duplicatePoints) {
        this.color = color;
        this.duplicatePoints = duplicatePoints;
    }

    public int color() {
        return color;
    }

    /** Skill points a gain of this rarity pays when it is drawn again at its top stage. */
    public int duplicatePoints() {
        return duplicatePoints;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "nightsiege.rarity." + key();
    }

    /** One step down (common stays common). */
    public CrateRarity lower() {
        return this == COMMON ? COMMON : values()[ordinal() - 1];
    }

    /** One step up (legendary stays legendary). */
    public CrateRarity higher() {
        return this == LEGENDARY ? LEGENDARY : values()[ordinal() + 1];
    }

    public static CrateRarity byName(String name) {
        if (name == null) {
            return COMMON;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
