package com.mki.nightsiege.core;

import java.util.Locale;

/**
 * The trees. A player commits to one of the player classes at a time; the core
 * tree is shared and paid for by donations from everyone. The summoner is only
 * for admins for now (config {@code summonerForEveryone}).
 */
public enum SkillClass {
    ARCHER,
    WARRIOR,
    GUARDIAN,
    ENGINEER,
    SUMMONER,
    CORE;

    /** True for the shared tree that strengthens the base itself. */
    public boolean isShared() {
        return this == CORE;
    }

    /** Classes not everyone may choose yet (the summoner: admins only, unless the config opens it). */
    public boolean adminOnly() {
        return this == SUMMONER;
    }

    public static SkillClass byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String translationKey() {
        return "nightsiege.class." + name().toLowerCase(Locale.ROOT);
    }
}
