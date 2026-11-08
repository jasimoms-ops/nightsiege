package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The numbers of the Letzte Bastion (Update 7, Simon 29.10. and 06.11.), kept free of Minecraft so
 * they can be tested: endless waves from level 20 on, a boss every 5th wave, from wave 10 one more
 * siege machine every 5 waves, fixed elites from wave 5, nemeses from wave 15, until all have fallen.
 * The only reward is fame: the best runs on the record board at the core.
 */
public final class BastionMath {

    /** Runs kept in the save; the board and the records tab show the best {@link #SHOWN}. */
    public static final int KEPT = 10;
    public static final int SHOWN = 5;

    private BastionMath() {
    }

    /**
     * The siege level wave n fights at: 20 + n of the full curve (Simon 29.10.: "nicht der Viertel-Kurve
     * über 20"). Above the soft cap a level brings only growthAboveSoftCap of a step, so the wave takes
     * as many levels as give its steps on the quarter curve.
     */
    public static int levelForWave(int wave, SiegeConfig cfg) {
        int soft = Math.max(1, cfg.softCapLevel);
        double steps = Math.max(1, wave) * Math.max(0.1, cfg.bastionStepsPerWave);
        double perLevel = cfg.growthAboveSoftCap > 0 ? cfg.growthAboveSoftCap : 1.0;
        int level = soft + (int) Math.ceil(steps / perLevel - 1e-9);
        return Math.max(1, Math.min(cfg.maxLevel, level));
    }

    /** Kills a wave asks for: bastionKillsPerField times the field, at least 5. */
    public static int quota(int field, SiegeConfig cfg) {
        return Math.max(5, (int) Math.round(Math.max(1, field) * cfg.bastionKillsPerField));
    }

    /** True for a boss wave (every bastionBossEvery waves, none when 0). */
    public static boolean bossWave(int wave, SiegeConfig cfg) {
        return cfg.bastionBossEvery > 0 && wave > 0 && wave % cfg.bastionBossEvery == 0;
    }

    /** Siege machines a wave brings: from bastionMachinesFromWave every bastionMachinesEvery waves one more (10: 1, 15: 2). */
    public static int machines(int wave, SiegeConfig cfg) {
        int from = Math.max(1, cfg.bastionMachinesFromWave);
        int every = Math.max(1, cfg.bastionMachinesEvery);
        if (wave < from || (wave - from) % every != 0) {
            return 0;
        }
        return Math.min(Math.max(1, cfg.machinesMax), 1 + (wave - from) / every);
    }

    /** The chance of a monster to be an elite in this wave (fixed, the smart night is off). */
    public static double eliteChance(int wave, SiegeConfig cfg) {
        if (wave < Math.max(1, cfg.bastionEliteFromWave)) {
            return 0.0;
        }
        double c = cfg.bastionEliteChance + cfg.bastionEliteChancePerWave * (wave - cfg.bastionEliteFromWave);
        return Math.max(0.0, Math.min(cfg.bastionEliteChanceMax, c));
    }

    /** True when a wave fighting since {@code fightSince} (ticks, below 0 = not yet: a boss wave before its boss) is over its time. */
    public static boolean waveTimeUp(long now, long fightSince, SiegeConfig cfg) {
        return cfg.bastionWaveMaxSeconds > 0 && fightSince >= 0 && now - fightSince >= cfg.bastionWaveMaxSeconds * 20L;
    }

    /** Seconds the wave has left, -1 without a running limit. */
    public static int waveSecondsLeft(long now, long fightSince, SiegeConfig cfg) {
        if (cfg.bastionWaveMaxSeconds <= 0 || fightSince < 0) {
            return -1;
        }
        return (int) Math.max(0, (fightSince + cfg.bastionWaveMaxSeconds * 20L - now + 19) / 20);
    }

    /** True from the wave on which nemeses may come back. */
    public static boolean nemesisWave(int wave, SiegeConfig cfg) {
        return wave >= Math.max(1, cfg.bastionNemesisFromWave);
    }

    /** Ready players a round needs: bastionAccepts, or every signed-up player online when fewer are on. */
    public static int needed(int signedUpOnline, SiegeConfig cfg) {
        return Math.max(1, Math.min(Math.max(1, cfg.bastionAccepts), Math.max(1, signedUpOnline)));
    }

    /** One run of the Letzte Bastion: the last wave reached, its team and when. */
    public static class Run {
        /** The wave the last defender fell in. */
        public int welle;
        /** Waves fully held (the one they fell in not counted). */
        public int gehalten;
        public int kills;
        public int sekunden;
        /** The team: "Name (subclass key)", in the order they signed up. */
        public List<String> team = new ArrayList<>();
        public List<String> namen = new ArrayList<>();
        public List<String> unterklassen = new ArrayList<>();
        public String datum = "";
        /** A run of a playtest (marked, it counts on its own list). */
        public boolean playtest;
    }

    /** Best first: the higher wave, then more kills, then the faster run. */
    public static final Comparator<Run> BEST = Comparator.<Run>comparingInt(r -> -r.welle)
            .thenComparingInt(r -> -r.kills)
            .thenComparingInt(r -> r.sekunden);

    /** Puts a run into the list (best first, at most {@link #KEPT}); returns its place (1 = best) or 0 when it fell off. */
    public static int add(List<Run> runs, Run run) {
        runs.add(run);
        runs.sort(BEST);
        // Each kind (real runs, playtest runs) keeps its best KEPT; the place counts within the kind.
        int place = 0;
        int real = 0;
        int test = 0;
        for (java.util.Iterator<Run> it = runs.iterator(); it.hasNext(); ) {
            Run r = it.next();
            int n = r.playtest ? ++test : ++real;
            if (n > KEPT) {
                it.remove();
            } else if (r == run) {
                place = n;
            }
        }
        return place;
    }

    /** The runs of one kind (real or playtest), best first. */
    public static List<Run> best(List<Run> runs, boolean playtest, int count) {
        List<Run> out = new ArrayList<>();
        if (runs != null) {
            for (Run r : runs) {
                if (r != null && r.playtest == playtest) {
                    out.add(r);
                }
            }
        }
        out.sort(BEST);
        return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;
    }
}
