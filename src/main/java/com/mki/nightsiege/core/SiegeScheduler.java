package com.mki.nightsiege.core;

import java.util.Random;

/**
 * Decides when the next siege is due.
 *
 * <p>The countdown does not simply follow the world clock. It only advances
 * while enough players are gathered around the core, so a base nobody defends
 * is never attacked, and a group cannot run the clock down by logging off.</p>
 */
public final class SiegeScheduler {

    public enum Decision {
        NONE,
        WARN,
        START
    }

    private long readyTicks;
    private long requiredTicks;
    private long warnedForDay = Long.MIN_VALUE;
    private long startedForDay = Long.MIN_VALUE;

    public long readyTicks() {
        return readyTicks;
    }

    public void setReadyTicks(long ticks) {
        this.readyTicks = Math.max(0L, ticks);
    }

    public long requiredTicks() {
        return requiredTicks;
    }

    public void setRequiredTicks(long ticks) {
        this.requiredTicks = Math.max(0L, ticks);
    }

    /** Rolls the next interval, somewhere between the configured bounds. */
    public void rollInterval(Random random, SiegeConfig cfg) {
        int min = Math.max(1, cfg.minDaysBetweenSieges);
        int max = Math.max(min, cfg.maxDaysBetweenSieges);
        int days = min + (max > min ? random.nextInt(max - min + 1) : 0);
        requiredTicks = days * 24000L;
        readyTicks = 0L;
    }

    /** Adds time, but only when the defenders are actually present. */
    public void addQualifyingTicks(long ticks) {
        if (ticks > 0) {
            readyTicks += ticks;
        }
    }

    public boolean armed() {
        return requiredTicks > 0L && readyTicks >= requiredTicks;
    }

    /** 0 to 1, how far the countdown has come. */
    public double progress() {
        if (requiredTicks <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, (double) readyTicks / requiredTicks);
    }

    /** Qualifying ticks still to go. */
    public long remainingTicks() {
        return Math.max(0L, requiredTicks - readyTicks);
    }

    public void markSiegeFinished(Random random, SiegeConfig cfg) {
        rollInterval(random, cfg);
    }

    /**
     * @param gameTime    total world time in ticks
     * @param siegeActive whether a siege is already running
     */
    public Decision evaluate(long gameTime, boolean siegeActive, SiegeConfig cfg) {
        return evaluate(gameTime, siegeActive, cfg.warningDayTime, cfg.startDayTime);
    }

    /** Same, with the warning and start time given, e.g. noon for an eclipse. */
    public Decision evaluate(long gameTime, boolean siegeActive, int warningTime, int startTime) {
        if (siegeActive || !armed()) {
            return Decision.NONE;
        }
        long day = SiegeMath.dayOf(gameTime);
        int time = SiegeMath.timeOfDay(gameTime);

        if (warnedForDay != day && time >= warningTime && time < startTime) {
            warnedForDay = day;
            return Decision.WARN;
        }
        if (warnedForDay == day && startedForDay != day && time >= startTime) {
            startedForDay = day;
            return Decision.START;
        }
        return Decision.NONE;
    }
}
