package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * When a boss comes and which one (Simon, 25 October 2026): every 6th siege is a boss
 * night; a lost boss night comes again until the boss falls. The four bosses come from a
 * shuffled bag, none twice before all have been there.
 */
public final class BossPlan {

    private BossPlan() {
    }

    /** True when the coming siege is a boss night, after this many sieges since the last boss fell. */
    public static boolean isBossNight(int siegesSinceBoss, SiegeConfig cfg) {
        return cfg.bossesEnabled && cfg.bossEvery > 0 && siegesSinceBoss >= cfg.bossEvery - 1;
    }

    /** The counter for a save that has none yet: the sieges played so far, as if counted all along. */
    public static int startCount(int siegesWon, int siegesLost, SiegeConfig cfg) {
        int every = Math.max(1, cfg.bossEvery);
        return Math.floorMod(Math.max(0, siegesWon) + Math.max(0, siegesLost), every);
    }

    /**
     * Draws the next boss from the bag (refilled and shuffled when empty, so that the last
     * boss does not come again at once). The bag holds the boss ids and is changed in place.
     */
    public static BossKind draw(List<String> bag, String last, Random random) {
        bag.removeIf(id -> BossKind.byId(id) == null);
        if (bag.isEmpty()) {
            List<String> fresh = new ArrayList<>();
            for (BossKind kind : BossKind.values()) {
                fresh.add(kind.id());
            }
            Collections.shuffle(fresh, random);
            if (fresh.size() > 1 && fresh.get(0).equals(last)) {
                Collections.swap(fresh, 0, fresh.size() - 1);
            }
            bag.addAll(fresh);
        }
        return BossKind.byId(bag.remove(0));
    }

    /** Health of the boss: its base, the night's level and +bossHealthPerExtraPlayer for every further defender. */
    public static double health(double base, int level, int defenders, SiegeConfig cfg) {
        return health(base, level, defenders, cfg, cfg.bossHealthPerExtraPlayer);
    }

    /** As above with the share per further defender given (the playtest scales per player). */
    public static double health(double base, int level, int defenders, SiegeConfig cfg, double perPlayer) {
        double players = 1.0 + Math.max(0.0, perPlayer) * Math.max(0, defenders - 1);
        return base * SiegeMath.healthMultiplier(level, cfg) * players * cfg.bossHealthMultiplier;
    }

    /**
     * The coming night is the season finale (content update, Simon 28 October 2026): the last
     * night of the season, and again the night after a lost finale.
     */
    public static boolean finaleDue(int seasonNights, SiegeConfig cfg) {
        return cfg.seasonLength > 0 && cfg.bossesEnabled && seasonNights + 1 >= cfg.seasonLength;
    }

    /** The season's count after a real night; it stops at the finale until the finale is won. */
    public static int countNight(int seasonNights, SiegeConfig cfg) {
        return cfg.seasonLength <= 0 ? seasonNights : Math.min(cfg.seasonLength, Math.max(0, seasonNights) + 1);
    }

    /** The count for a save that has none yet: the nights held before, as if counted all along. */
    public static int startSeason(int nightsHeldBefore, SiegeConfig cfg) {
        return Math.floorMod(Math.max(0, nightsHeldBefore), Math.max(1, cfg.seasonLength));
    }

    /** Ticks before the end of the night at which the boss comes: bossFinaleSeconds, at most half the night. */
    public static int finaleTicks(int totalTicks, SiegeConfig cfg) {
        return Math.max(20, Math.min(cfg.bossFinaleSeconds * 20, totalTicks / 2));
    }
}
