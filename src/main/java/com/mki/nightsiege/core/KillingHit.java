package com.mki.nightsiege.core;

/**
 * What a killing hit took (GOLEMDIAG 3, 09.11.): Fabric's AFTER_DAMAGE leaves killing hits out,
 * so the counters (statistics, bench, companions) never saw the blow that finished a monster.
 */
public final class KillingHit {

    private KillingHit() {
    }

    /** The life and absorption a hit took; 0 when AFTER_DAMAGE reported it already (no double count). */
    public static float lost(boolean reported, float hpBefore, float absBefore, float hpAfter, float absAfter) {
        if (reported) {
            return 0f;
        }
        float before = Math.max(0f, hpBefore) + Math.max(0f, absBefore);
        float after = Math.max(0f, hpAfter) + Math.max(0f, absAfter);
        return Math.max(0f, before - after);
    }
}
