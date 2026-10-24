package com.mki.nightsiege.core;

/**
 * The engineer's traps and towers, in the order the skill chain unlocks them.
 *
 * <p>The id is shared by the block, its item and the skill node, so one name
 * ties all three together. All numbers live here, away from Minecraft, so they
 * can be tested and tuned in one place. Tiers run from 1 to {@link #MAX_TIER}.</p>
 */
public enum TrapKind {
    /** Floor plate: hurts and slows every monster standing on it. Wears out. */
    SPIKE("spike_trap"),
    /** Floor plate: roots every monster around it for a few seconds, then recharges. */
    SNARE("snare_trap"),
    /** Tower (demolition expert): lobs a shell over walls onto the thickest group. Breaks no blocks. */
    MORTAR("mortar_tower"),
    /** Block: shoots arrows at the nearest monster it can see. Reloaded with arrows. */
    TURRET("arrow_turret"),
    /** Floor plate: sets every monster around it alight. Starts no fires in the world. */
    FLAME("flame_trap"),
    /** Tower (alchemist): throws a potion onto a group; the poison eats away their life. */
    BREWER("potion_tower"),
    /** Block: lightning that jumps from monster to monster. Needs no ammunition. */
    TESLA("tesla_coil");

    /** Levels of a trap's own skill node. */
    public static final int NODE_LEVELS = 3;
    /** Highest tier: the node plus up to two levels of a booster node. */
    public static final int MAX_TIER = 5;

    private final String id;

    TrapKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TrapKind byId(String id) {
        for (TrapKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    private static int clamp(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    /** Damage of one hit: a spike, the blast at its centre, or one turret arrow. */
    public double damage(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 3.0 + 1.5 * (t - 1);   // 3 / 4.5 / 6
            case SNARE -> 0.0;
            case MORTAR, BREWER -> 0.0;          // half a zombie of the night, see TrapService
            case TURRET -> 3.0 + (t - 1);        // 3 / 4 / 5
            case FLAME -> 2.0 + (t - 1);         // contact burn 2 / 3 / 4
            case TESLA -> 4.0 + (t - 1);         // 4 / 5 / 6 per jump
        };
    }

    /** How often the trap can fire before it is used up. The turret counts arrows instead. */
    public int uses(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 24 + 12 * (t - 1);     // 24 / 36 / 48 hits
            case SNARE -> 8 + 4 * (t - 1);       // 8 / 12 / 16 catches
            case TURRET, TESLA, MORTAR, BREWER -> Integer.MAX_VALUE;
            case FLAME -> 10 + 5 * (t - 1);      // 10 / 15 / 20
        };
    }

    /** Blocks around the trap it reaches (for the towers: how far they shoot). */
    public double radius(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 0.0;                   // only what stands on it
            case SNARE -> 2.0 + 0.5 * t;         // 2.5 / 3 / 3.5
            case MORTAR -> 16.0 + 4.0 * t;       // 20 / 24 / 28
            case TURRET -> 10.0 + 2.0 * t;       // 12 / 14 / 16
            case FLAME -> 1.0 + 0.5 * t;         // 1.5 / 2 / 2.5
            case BREWER -> 12.0 + 2.0 * t;       // 14 / 16 / 18
            case TESLA -> 4.0 + 2.0 * t;         // 6 / 8 / 10
        };
    }

    /** Ticks between two activations. */
    public int cooldownTicks(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 10;
            case SNARE -> 240 - 40 * t;          // 10 s / 8 s / 6 s
            case MORTAR -> Math.max(30, 70 - 10 * t);   // 3 s / 2.5 s / 2 s
            case TURRET -> Math.max(12, 36 - 6 * t);   // 1.5 s / 1.2 s / 0.9 s / 0.6 s
            case FLAME -> 120 - 20 * t;          // 5 s / 4 s / 3 s
            case BREWER -> Math.max(40, 90 - 10 * t);   // 4 s / 3.5 s / 3 s
            case TESLA -> Math.max(20, 60 - 10 * t);   // 2.5 s / 2 s / 1.5 s / 1 s
        };
    }

    /** Ticks a monster stays slowed (spike) or rooted (snare). */
    public int holdTicks(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 30;
            case SNARE -> 40 + 20 * t;           // 3 s / 4 s / 5 s
            case FLAME -> 60 + 40 * t;           // burns 5 s / 7 s / 9 s
            case BREWER -> 80;                   // the poison eats for 4 s
            default -> 0;
        };
    }

    /** Arrows a freshly placed turret starts with. */
    public int startAmmo(int tier) {
        return this == TURRET ? 16 + 16 * clamp(tier) : 0;   // 32 / 48 / 64
    }

    /** Most arrows a turret holds. */
    public int maxAmmo(int tier) {
        return this == TURRET ? 32 + 32 * clamp(tier) : 0;   // 64 / 96 / 128
    }

    /** Traps of this kind handed out at the start of every siege. Tier 0 means not unlocked. */
    public int freePerSiege(int tier) {
        if (tier <= 0) {
            return 0;
        }
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 1 + t;                 // 2 / 3 / 4
            case SNARE -> t;                     // 1 / 2 / 3
            case MORTAR -> t >= 3 ? 2 : 1;       // 1 / 1 / 2
            case TURRET -> t >= 3 ? 2 : 1;       // 1 / 1 / 2
            case FLAME -> t;                     // 1 / 2 / 3
            case BREWER -> t >= 3 ? 2 : 1;       // 1 / 1 / 2
            case TESLA -> t >= 3 ? 1 : 0;        // only at the top tier
        };
    }

    /** Most traps of this kind one engineer may have set at once. Grows with the tier. */
    public int maxPlaced(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 8 + 2 * t;             // 10 ... 18
            case SNARE -> 3 + t;                 // 4 ... 8
            case MORTAR, BREWER -> (t + 3) / 2;  // 2, 2, 3, 3, 4
            case TURRET -> 1 + t;                // 2 ... 6
            case FLAME -> 2 + t;                 // 3 ... 7
            case TESLA -> (t + 3) / 2;           // 2, 2, 3, 3, 4
        };
    }

    /** Monsters one tesla discharge jumps between. */
    public int chainTargets(int tier) {
        return this == TESLA ? 1 + clamp(tier) : 0;   // 2 / 3 / 4
    }

    /** Strength of the brewer's slowness, 0 being level I. */
    public int amplifier(int tier) {
        if (this != BREWER) {
            return 0;
        }
        int t = clamp(tier);
        return t >= 4 ? 2 : t >= 2 ? 1 : 0;
    }

    /** Blocks around the impact a tower's shell or potion reaches. */
    public double splash(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case MORTAR -> 2.0 + 0.25 * t;       // 2.25 / 2.5 / 2.75
            case BREWER -> 2.5 + 0.25 * t;       // 2.75 / 3 / 3.25
            default -> 0.0;
        };
    }

    /** Towers that shoot (and need no floor contact). */
    public boolean isTower() {
        return this == TURRET || this == TESLA || this == MORTAR || this == BREWER;
    }

    /** True for traps that are set into the floor and spring when stepped on. */
    public boolean isFloorPlate() {
        return !isTower();
    }
}
