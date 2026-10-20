package com.mki.nightsiege.core;

import java.util.Locale;

/**
 * The five trees. A player commits to exactly one of the four player classes;
 * the core tree is shared and paid for by donations from everyone.
 */
public enum SkillClass {
    ARCHER,
    WARRIOR,
    GUARDIAN,
    ENGINEER,
    CORE;

    /** True for the shared tree that strengthens the base itself. */
    public boolean isShared() {
        return this == CORE;
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
