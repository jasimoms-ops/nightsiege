package com.mki.nightsiege.core;

/**
 * One entry of the configurable mob pool.
 *
 * <p>This is the extension point for mobs from other mods: add an entry with the
 * entity id of the foreign mob, for example {@code friendsandfoes:iceologer}.
 * If that mod is not installed, the entry is skipped with a log line instead of
 * crashing.</p>
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
