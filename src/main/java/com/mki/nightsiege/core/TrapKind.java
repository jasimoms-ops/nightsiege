package com.mki.nightsiege.core;

/**
 * The engineer's four traps, in the order the skill chain unlocks them.
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
    /** Floor plate: goes off once when a monster steps on it. Breaks no blocks, spares players. */
    MINE("blast_mine"),
    /** Block: shoots arrows at the nearest monster it can see. Reloaded with arrows. */
    TURRET("arrow_turret"),
    /** Floor plate: sets every monster around it alight. Starts no fires in the world. */
    FLAME("flame_trap"),
    /** Floor plate: a cloud that poisons and weakens; the undead wither instead. */
    POISON("poison_trap"),
    /** Block: lightning that jumps from monster to monster. Needs no ammunition. */
    TESLA("tesla_coil");

    public static final int MAX_TIER = 3;

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
            case MINE -> 12.0 + 4.0 * (t - 1);   // 12 / 16 / 20
            case TURRET -> 3.0 + (t - 1);        // 3 / 4 / 5
            case FLAME -> 2.0 + (t - 1);         // contact burn 2 / 3 / 4
            case POISON -> 0.0;
            case TESLA -> 4.0 + (t - 1);         // 4 / 5 / 6 per jump
        };
    }

    /** How often the trap can fire before it is used up. The turret counts arrows instead. */
    public int uses(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 24 + 12 * (t - 1);     // 24 / 36 / 48 hits
            case SNARE -> 8 + 4 * (t - 1);       // 8 / 12 / 16 catches
            case MINE -> 1;
            case TURRET, TESLA -> Integer.MAX_VALUE;
            case FLAME -> 10 + 5 * (t - 1);      // 10 / 15 / 20
            case POISON -> 6 + 3 * (t - 1);      // 6 / 9 / 12
        };
    }

    /** Blocks around the trap it reaches. */
    public double radius(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 0.0;                   // only what stands on it
            case SNARE -> 2.0 + 0.5 * t;         // 2.5 / 3 / 3.5
            case MINE -> 2.5 + 0.5 * t;          // 3 / 3.5 / 4
            case TURRET -> 10.0 + 2.0 * t;       // 12 / 14 / 16
            case FLAME -> 1.0 + 0.5 * t;         // 1.5 / 2 / 2.5
            case POISON -> 2.0 + 0.5 * t;        // 2.5 / 3 / 3.5
            case TESLA -> 4.0 + 2.0 * t;         // 6 / 8 / 10
        };
    }

    /** Ticks between two activations. */
    public int cooldownTicks(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 10;
            case SNARE -> 240 - 40 * t;          // 10 s / 8 s / 6 s
            case MINE -> 0;
            case TURRET -> 36 - 6 * t;           // 1.5 s / 1.2 s / 0.9 s
            case FLAME -> 120 - 20 * t;          // 5 s / 4 s / 3 s
            case POISON -> 280 - 40 * t;         // 12 s / 10 s / 8 s
            case TESLA -> 60 - 10 * t;           // 2.5 s / 2 s / 1.5 s
        };
    }

    /** Ticks a monster stays slowed (spike) or rooted (snare). */
    public int holdTicks(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 30;
            case SNARE -> 40 + 20 * t;           // 3 s / 4 s / 5 s
            case FLAME -> 60 + 40 * t;           // burns 5 s / 7 s / 9 s
            case POISON -> 60 + 40 * t;          // 5 s / 7 s / 9 s
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
            case MINE -> 1 + t;                  // 2 / 3 / 4
            case TURRET -> t >= 3 ? 2 : 1;       // 1 / 1 / 2
            case FLAME -> t;                     // 1 / 2 / 3
            case POISON -> t >= 2 ? 2 : 1;       // 1 / 2 / 2
            case TESLA -> t >= 3 ? 1 : 0;        // only at the top tier
        };
    }

    /** Monsters one tesla discharge jumps between. */
    public int chainTargets(int tier) {
        return this == TESLA ? 1 + clamp(tier) : 0;   // 2 / 3 / 4
    }

    /** Strength of the poison or wither, 0 being level I. */
    public int amplifier(int tier) {
        return this == POISON && clamp(tier) >= 2 ? 1 : 0;
    }

    /** True for traps that are set into the floor and spring when stepped on. */
    public boolean isFloorPlate() {
        return this != TURRET && this != TESLA;
    }
}
