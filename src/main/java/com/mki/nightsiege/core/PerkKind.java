package com.mki.nightsiege.core;

/**
 * The perks core fragments level up in the main core. Stage n costs n fragments,
 * so stage V needs 1 + 2 + 3 + 4 + 5 = 15 fragments of that kind.
 */
public enum PerkKind {
    /** +2 hearts per stage. From easy ruins. */
    VITALITY,
    /** +1 armour and 5 % less damage per stage. From outposts and castles. */
    BULWARK,
    /** Regeneration I, from stage III Regeneration II. From the sea. */
    TIDES,
    /** +10 % melee damage per stage, from stage III fire resistance. From the Nether. */
    FLAME,
    /** -10 % ult cooldown and +10 % experience per stage. From every guardian boss. */
    VIGILANCE;

    public static final int MAX_STAGE = 5;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Fragments needed in total to reach a stage. */
    public static int fragmentsFor(int stage) {
        int s = Math.max(0, Math.min(MAX_STAGE, stage));
        return s * (s + 1) / 2;
    }

    /** The stage a number of inserted fragments has reached. */
    public static int stageFor(int fragments) {
        int stage = 0;
        while (stage < MAX_STAGE && fragmentsFor(stage + 1) <= fragments) {
            stage++;
        }
        return stage;
    }

    public static PerkKind byId(String id) {
        for (PerkKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
