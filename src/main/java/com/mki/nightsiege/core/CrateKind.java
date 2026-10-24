package com.mki.nightsiege.core;

import java.util.Locale;

/** What kind of night a crate comes from: event and fragment nights roll better odds. */
public enum CrateKind {
    NORMAL,
    EVENT,
    FRAGMENT;

    /** The next better kind (a luck crate rolls one step up). */
    public CrateKind better() {
        return this == FRAGMENT ? FRAGMENT : values()[ordinal() + 1];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CrateKind byName(String name) {
        if (name == null) {
            return NORMAL;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
