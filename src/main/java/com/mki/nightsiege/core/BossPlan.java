package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * When a boss comes and which one (Simon, 25 October 2026; rhythm of 29 October 2026, see
 * {@link #nightKind}): a lost boss night comes again until the boss falls. The bosses come from
 * a shuffled bag, none twice before all have been there.
 */
public final class BossPlan {

    private BossPlan() {
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

    /** What a night of the boss rhythm brings. */
    public enum NightKind {
        /** Waves only. */
        NORMAL,
        /** One boss at the end of the night (every bossEvery-th night). */
        BOSS,
        /** The "Vierer-Nacht": the bosses of the boss nights before, two at a time, no waves, no clock. */
        GROUP,
        /** After the last Vierer-Nacht: two bosses at once, five minutes into the night. */
        LATE
    }

    /**
     * The boss rhythm (Simon 29 October 2026): boss nights 4, 8, 12, 16; night 20 the four bosses
     * that came; the same for 24–36 and night 40 with the other four; from night 41 every 2nd night
     * two bosses at once after five minutes. {@code night} counts from 1.
     */
    public static NightKind nightKind(int night, SiegeConfig cfg) {
        if (!cfg.bossesEnabled || night <= 0) {
            return NightKind.NORMAL;
        }
        if (cfg.bossLateFrom > 0 && night > cfg.bossLateFrom) {
            return cfg.bossLateEvery > 0 && (night - cfg.bossLateFrom) % cfg.bossLateEvery == 0 ? NightKind.LATE : NightKind.NORMAL;
        }
        if (cfg.bossGroupEvery > 0 && night % cfg.bossGroupEvery == 0) {
            return NightKind.GROUP;
        }
        if (cfg.bossEvery > 0 && night % cfg.bossEvery == 0) {
            return NightKind.BOSS;
        }
        return NightKind.NORMAL;
    }

    /** The rhythm's count after a real night: a lost boss night or Vierer-Nacht comes again. */
    public static int countNight(int nights, NightKind kind, boolean bossesDown) {
        int n = Math.max(0, nights);
        if ((kind == NightKind.BOSS || kind == NightKind.GROUP) && !bossesDown) {
            return n;
        }
        return n + 1;
    }

    /**
     * The bosses of a Vierer-Nacht: those that came in the boss nights before it (in their order),
     * filled up with others when fewer came (an old save, a test).
     */
    public static List<BossKind> groupBosses(List<String> came, int count, Random random) {
        List<BossKind> out = new ArrayList<>();
        if (came != null) {
            for (String id : came) {
                BossKind kind = BossKind.byId(id);
                if (kind != null && !out.contains(kind) && out.size() < count) {
                    out.add(kind);
                }
            }
        }
        List<BossKind> rest = new ArrayList<>(List.of(BossKind.values()));
        rest.removeAll(out);
        Collections.shuffle(rest, random);
        while (out.size() < count && !rest.isEmpty()) {
            out.add(rest.remove(0));
        }
        return out;
    }

    /** The bosses of a late boss night: so many different ones, at random. */
    public static List<BossKind> lateBosses(int count, Random random) {
        List<BossKind> all = new ArrayList<>(List.of(BossKind.values()));
        Collections.shuffle(all, random);
        return new ArrayList<>(all.subList(0, Math.max(0, Math.min(count, all.size()))));
    }

    /** Ticks before the end of the night at which the boss comes: bossFinaleSeconds, at most half the night. */
    public static int finaleTicks(int totalTicks, SiegeConfig cfg) {
        return Math.max(20, Math.min(cfg.bossFinaleSeconds * 20, totalTicks / 2));
    }
}
