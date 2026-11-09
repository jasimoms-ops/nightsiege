package com.mki.nightsiege.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schlaue Nacht (Simon 05.11. 20:26: "kann es eine Mechanik geben, die die Nacht schlau macht und
 * automatisch stärkere Mobs spawnt, wenn wir zu gut sind, oder andersrum"). The night watches the
 * defenders and turns itself up or down one step at a time; every night starts at 0.
 *
 * <p>Once a second the night notes the defenders' life (average share of their max life, and the
 * average of the weakest third), every death and every kill. Every {@code smartNightEverySeconds} it looks at the last
 * {@code smartNightWindowSeconds}: the stress is {@code 1 − (0.6 × average + 0.4 × lowest)} plus
 * {@code smartNightDeathStress} per death. Below {@code smartNightUpStress} with enough kills and twice
 * in a row it goes one step up (at most {@code smartNightUpSteps}, not sooner than
 * {@code smartNightUpSeconds} after the last step); above {@code smartNightDownStress}, or at a death,
 * one step down (at most {@code smartNightDownSteps}), with a pause without refills. Below 0 the bar to
 * come up again is lower by 0.1, so a calm night finds its way back.</p>
 *
 * <p>Simon's answers 05.11.: +50 % / −25 %, hidden, up only with elites (from level 5) and the special
 * monsters earlier, down also with fewer monsters, no reward. What a step does lies in the static
 * levers below (the night and the foes ask them).</p>
 *
 * <p>Tor 6.6 (Formel C): neutral on average (fewer 0.03 per step = at most −15 %, down bar 0.50). It also
 * measures with the setting off ({@link #watching()}, {@link #peakStress()}), so a night with and one without
 * can be compared; the outer loop between nights lies in {@link OuterLoop}.</p>
 */
public final class SmartNight {

    /** One look of the night at its defenders, and the step it took (change −1, 0 or +1). */
    public record Look(long tick, double life, double lowest, int deaths, int kills, double stress, int step,
                       int change, String why) {
    }

    private record Sample(long tick, double avg, double min) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final ArrayDeque<Long> deaths = new ArrayDeque<>();
    private final ArrayDeque<Long> kills = new ArrayDeque<>();
    private final List<Look> steps = new ArrayList<>();
    private boolean on;
    /** Measuring (Tor 6.6): from begin to end, also with the smart night off, for the pair comparison. */
    private boolean watching;
    /** The highest pressure of the night's looks (-1 before the first look). */
    private double peakStress = -1;
    private long nextLook;
    private long lastStep;
    private int step;
    private int easyInRow;
    private int highest;
    private int lowest;
    private int pauses;
    private long pauseUntil;
    private Look last;

    /** A new night: back to 0. {@code on} false (war leader, the setting off) keeps it at 0 all night. */
    public void begin(long now, boolean on, SiegeConfig cfg) {
        this.on = on;
        this.watching = true;
        this.peakStress = -1;
        samples.clear();
        deaths.clear();
        kills.clear();
        steps.clear();
        step = 0;
        easyInRow = 0;
        highest = 0;
        lowest = 0;
        pauses = 0;
        pauseUntil = 0;
        last = null;
        lastStep = now;
        nextLook = now + Math.max(1, cfg.smartNightWindowSeconds) * 20L;
    }

    /** The night is over (or none runs): nothing more happens until the next begins. */
    public void end() {
        on = false;
        watching = false;
        pauseUntil = 0;
    }

    public boolean on() {
        return on;
    }

    /** True from the night's begin to its end, also when the smart night does not steer (Tor 6.6). */
    public boolean watching() {
        return watching;
    }

    /** The highest pressure seen tonight, -1 if the night never looked (Tor 6.6, "früh entschieden"). */
    public double peakStress() {
        return peakStress;
    }

    /** The step now (0 while off). */
    public int step() {
        return on ? step : 0;
    }

    public int highest() {
        return highest;
    }

    public int lowest() {
        return lowest;
    }

    public int pauses() {
        return pauses;
    }

    /** The steps taken tonight, in order. */
    public List<Look> steps() {
        return Collections.unmodifiableList(steps);
    }

    /** The last look (for the command and the tests), null before the first. */
    public Look last() {
        return last;
    }

    /** True while a step down gives the defenders a breather (no refills). */
    public boolean paused(long now) {
        return on && now < pauseUntil;
    }

    /** Once a second: the defenders' average and lowest share of their max life (only those standing). */
    public void sample(long now, double avg, double min) {
        if (watching) {
            samples.addLast(new Sample(now, clamp01(avg), clamp01(min)));
        }
    }

    /** A monster of the night fell. */
    public void kill(long now) {
        if (watching) {
            kills.addLast(now);
        }
    }

    /** A defender fell: the night looks at once (and steps down unless it just did). */
    public Look death(long now, int defenders, SiegeConfig cfg) {
        if (!watching) {
            return null;
        }
        deaths.addLast(now);
        Look l = look(now, defenders, cfg, true);
        return on ? l : null;   // off: only measured, no effect (Tor 6.6)
    }

    /** Every tick of the night: a look when it is time (null otherwise). */
    public Look tick(long now, int defenders, SiegeConfig cfg) {
        if (!watching || now < nextLook) {
            return null;
        }
        nextLook = now + Math.max(1, cfg.smartNightEverySeconds) * 20L;
        return look(now, defenders, cfg, false);
    }

    /** For the tests and the command: the step set by hand (inside the bounds). */
    public void force(long now, int to, SiegeConfig cfg) {
        if (!on) {
            return;
        }
        int before = step;
        step = Math.max(-cfg.smartNightDownSteps, Math.min(cfg.smartNightUpSteps, to));
        if (step < before) {
            pause(now, cfg);
        }
        note(new Look(now, Double.NaN, Double.NaN, 0, 0, Double.NaN, step, Integer.signum(step - before), "gesetzt"));
        lastStep = now;
        easyInRow = 0;
        nextLook = Math.min(nextLook, now + Math.max(1, cfg.smartNightEverySeconds) * 20L);   // it looks again soon
    }

    private Look look(long now, int defenders, SiegeConfig cfg, boolean death) {
        prune(now, cfg);
        double life = 1.0;
        double low = 1.0;
        if (!samples.isEmpty()) {
            double sum = 0;
            for (Sample s : samples) {
                sum += s.avg();
                low = Math.min(low, s.min());
            }
            life = sum / samples.size();
        }
        int d = deaths.size();
        int k = kills.size();
        // A death weighs by the group (sim schlau1: ten bots lose one now and then, that is no sign of a hard night).
        double weighted = d * deathWeight(defenders);
        double stress = stress(life, low, weighted, cfg);
        if (!samples.isEmpty() || d > 0) {
            peakStress = Math.max(peakStress, stress);
        }
        if (!on) {
            Look l = new Look(now, life, low, d, k, stress, step, 0, "aus");   // only measures, never steps
            last = l;
            return l;
        }
        int change = 0;
        String why;
        double upBar = cfg.smartNightUpStress + (step < 0 ? 0.1 : 0.0);
        int killsNeeded = (int) Math.ceil(cfg.smartNightUpKillsPerPlayer * Math.max(1, defenders));
        death = death && weighted >= 1.0 - 1e-9;   // at once only for one of up to three, two of four to six …
        if (death || stress > cfg.smartNightDownStress) {
            easyInRow = 0;
            long wait = (death ? 5L : Math.max(0, cfg.smartNightDownSeconds)) * 20L;
            if (step <= -cfg.smartNightDownSteps) {
                why = "ganz unten";
            } else if (now - lastStep < wait) {
                why = "zu eng, gerade erst gestuft";
            } else {
                change = -1;
                why = death ? "Tod" : "zu eng";
            }
        } else if (samples.isEmpty()) {
            easyInRow = 0;
            why = "niemand steht";
        } else if (stress < upBar && d == 0 && k >= killsNeeded) {
            easyInRow++;
            if (step >= cfg.smartNightUpSteps) {
                why = "ganz oben";
            } else if (easyInRow < 2) {
                why = "zu leicht (1.)";
            } else if (now - lastStep < Math.max(0, cfg.smartNightUpSeconds) * 20L) {
                why = "zu leicht, gerade erst gestuft";
            } else {
                change = 1;
                why = "zu leicht";
            }
        } else {
            easyInRow = 0;
            why = stress < upBar ? "zu wenig Kills (" + k + " von " + killsNeeded + ")" : "passt";
        }
        if (change != 0) {
            step += change;
            lastStep = now;
            easyInRow = 0;
            if (change < 0) {
                pause(now, cfg);
            }
        }
        Look l = new Look(now, life, low, d, k, stress, step, change, why);
        last = l;
        if (change != 0) {
            note(l);
        }
        return l;
    }

    private void note(Look l) {
        steps.add(l);
        highest = Math.max(highest, step);
        lowest = Math.min(lowest, step);
        last = l;
    }

    private void pause(long now, SiegeConfig cfg) {
        if (cfg.smartNightPauseSeconds > 0) {
            pauseUntil = Math.max(pauseUntil, now + Math.round(cfg.smartNightPauseSeconds * 20));
            pauses++;
        }
    }

    private void prune(long now, SiegeConfig cfg) {
        long from = now - Math.max(1, cfg.smartNightWindowSeconds) * 20L;
        while (!samples.isEmpty() && samples.peekFirst().tick() < from) {
            samples.pollFirst();
        }
        while (!deaths.isEmpty() && deaths.peekFirst() < from) {
            deaths.pollFirst();
        }
        while (!kills.isEmpty() && kills.peekFirst() < from) {
            kills.pollFirst();
        }
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 1.0 : Math.max(0.0, Math.min(1.0, v));
    }

    // ------------------------------------------------------------------ the measure

    /**
     * 0 = nobody is ever hurt, about 0.5 = it gets tight; every death adds smartNightDeathStress (deaths
     * weighed by the group, see deathWeight).
     */
    public static double stress(double life, double lowest, double deaths, SiegeConfig cfg) {
        double s = 1.0 - (0.6 * clamp01(life) + 0.4 * clamp01(lowest));
        return s + Math.min(1.0, Math.max(0.0, deaths) * cfg.smartNightDeathStress);
    }

    /** What one death counts for in a group of this size: 1 for up to three, 3/n above (0.3 for ten). */
    public static double deathWeight(int defenders) {
        return Math.min(1.0, 3.0 / Math.max(1, defenders));
    }

    /** How many of the defenders standing count as "the lowest": the weakest third, at least one. */
    public static int lowestCount(int standing) {
        return Math.max(1, (int) Math.ceil(standing / 3.0));
    }

    // ------------------------------------------------------------------ what a step does

    /** The share of the night's monsters that the smart night makes elites (on top of eliteMobs). */
    public static double eliteChance(SiegeConfig cfg, int step, int level) {
        return step > 0 && level >= cfg.smartNightEliteFromLevel ? step * cfg.smartNightElitePerStep : 0.0;
    }

    /** False far down: no special monsters and no elites at all. */
    public static boolean specials(SiegeConfig cfg, int step) {
        return step > cfg.smartNightNoSpecialsAt;
    }

    /** How many levels earlier the stunners, breakers and executioners come. */
    public static int levelBonus(SiegeConfig cfg, int step) {
        return step > 0 ? step * cfg.smartNightSpecialLevelsPerStep : 0;
    }

    /** Special monsters the night adds with every wave (1 from step 2, 2 from step 4 …). */
    public static int extraPerWave(int step) {
        return step >= 2 ? step / 2 : 0;
    }

    /** The most of those extra special monsters alive at once. */
    public static int extraAlive(int step) {
        return Math.max(0, step);
    }

    /** From this step the special monsters come for every defender, not only the fighters and tanks. */
    public static boolean anyClass(SiegeConfig cfg, int step) {
        return step >= cfg.smartNightAnyClassFromStep;
    }

    /** The share of the field's room that may be filled (fewer monsters at once below 0). */
    public static double room(SiegeConfig cfg, int step) {
        return step < 0 ? Math.max(0.1, 1.0 + step * cfg.smartNightFewerPerStep) : 1.0;
    }
}
