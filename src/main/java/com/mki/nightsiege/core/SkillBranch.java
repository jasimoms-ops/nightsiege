package com.mki.nightsiege.core;

/** The four branches of the tree. */
public enum SkillBranch {
    MELEE,
    RANGED,
    TOUGHNESS,
    BASE;

    public String translationKey() {
        return "nightsiege.branch." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
