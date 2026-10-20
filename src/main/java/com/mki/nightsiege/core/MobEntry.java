package com.mki.nightsiege.core;

/**
 * One entry of the configurable mob pool.
 *
 * <p>This is the extension point for mobs from other mods: add an entry with the
 * entity id of the foreign mob, for example {@code friendsandfoes:iceologer}.
 * If that mod is not installed, the entry is skipped with a log line instead of
 * crashing.</p>
 *
 * <p>A rider is written with a plus: {@code minecraft:spider+minecraft:skeleton}
 * spawns a skeleton riding a spider.</p>
 */
public class MobEntry {
    /** Entity id, for example {@code minecraft:zombie}. */
    public String id = "minecraft:zombie";
    /** First siege level at which this mob can appear. */
    public int minLevel = 1;
    /** Last siege level at which it appears; 0 means "no upper limit". */
    public int maxLevel = 0;
    /** Relative chance against the other eligible entries. Must be positive. */
    public int weight = 10;
    /** Change of the weight for every level above {@link #minLevel}; negative lets easy mobs fade out. */
    public double weightPerLevel = 0.0;

    public MobEntry() {
    }

    public MobEntry(String id, int minLevel, int weight) {
        this.id = id;
        this.minLevel = minLevel;
        this.weight = weight;
    }

    public MobEntry(String id, int minLevel, int maxLevel, int weight) {
        this(id, minLevel, weight);
        this.maxLevel = maxLevel;
    }

    public MobEntry(String id, int minLevel, int weight, double weightPerLevel) {
        this(id, minLevel, weight);
        this.weightPerLevel = weightPerLevel;
    }

    /** The weight at this level, never below 1. */
    public int weightAt(int level) {
        double w = weight + weightPerLevel * Math.max(0, level - minLevel);
        return Math.max(1, (int) Math.round(w));
    }

    /** The mob itself, without a rider. */
    public String baseId() {
        int plus = id.indexOf('+');
        return plus < 0 ? id : id.substring(0, plus);
    }

    public boolean appliesTo(int level) {
        if (level < minLevel) {
            return false;
        }
        return maxLevel <= 0 || level <= maxLevel;
    }

    public boolean isValid() {
        return id != null && !id.isBlank() && weight > 0;
    }
}
