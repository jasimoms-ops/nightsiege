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
    TURRET("arrow_turret");

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
        };
    }

    /** How often the trap can fire before it is used up. The turret counts arrows instead. */
    public int uses(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 24 + 12 * (t - 1);     // 24 / 36 / 48 hits
            case SNARE -> 8 + 4 * (t - 1);       // 8 / 12 / 16 catches
            case MINE -> 1;
            case TURRET -> Integer.MAX_VALUE;
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
        };
    }

    /** Ticks a monster stays slowed (spike) or rooted (snare). */
    public int holdTicks(int tier) {
        int t = clamp(tier);
        return switch (this) {
            case SPIKE -> 30;
            case SNARE -> 40 + 20 * t;           // 3 s / 4 s / 5 s
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
        };
    }
}
