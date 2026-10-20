package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks which mob spawns next. Entries come from the config, so mobs from other
 * mods can be added without touching the code.
 */
public final class MobPool {

    private final List<MobEntry> entries;

    public MobPool(List<MobEntry> entries) {
        this.entries = new ArrayList<>();
        if (entries != null) {
            for (MobEntry entry : entries) {
                if (entry != null && entry.isValid()) {
                    this.entries.add(entry);
                }
            }
        }
    }

    /** Every entry that may appear at this level. */
    public List<MobEntry> eligible(int level) {
        List<MobEntry> out = new ArrayList<>();
        for (MobEntry entry : entries) {
            if (entry.appliesTo(level)) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Picks one entity id by weight, or null if nothing is eligible.
     *
     * @param unavailable ids whose mod is not installed; they are skipped
     */
    public String pick(int level, Random random, java.util.Set<String> unavailable) {
        List<MobEntry> pool = eligible(level);
        if (unavailable != null && !unavailable.isEmpty()) {
            pool.removeIf(entry -> unavailable.contains(entry.id));
        }
        if (pool.isEmpty()) {
            // Nothing matched: fall back to the lowest-level entries so a siege
            // never silently spawns an empty wave.
            pool = eligible(1);
            if (unavailable != null) {
                pool.removeIf(entry -> unavailable.contains(entry.id));
            }
            if (pool.isEmpty()) {
                return null;
            }
        }
        int total = 0;
        for (MobEntry entry : pool) {
            total += entry.weightAt(level);
        }
        int roll = random.nextInt(total);
        for (MobEntry entry : pool) {
            roll -= entry.weightAt(level);
            if (roll < 0) {
                return entry.id;
            }
        }
        return pool.get(pool.size() - 1).id;
    }
}
