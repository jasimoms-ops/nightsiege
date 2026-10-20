package com.mki.nightsiege.core;

/**
 * Decides when to warn and when to start, purely from the world clock.
 * Holds no Minecraft types so the timing can be tested directly.
 */
public final class SiegeScheduler {

    public enum Decision {
        /** Nothing to do this tick. */
        NONE,
        /** Send the sunset warning. */
        WARN,
        /** Start the siege now. */
        START
    }

    private long lastSiegeDay = Long.MIN_VALUE;
    private long warnedForDay = Long.MIN_VALUE;
    private long startedForDay = Long.MIN_VALUE;

    public long lastSiegeDay() {
        return lastSiegeDay;
    }

    public void setLastSiegeDay(long day) {
        this.lastSiegeDay = day;
    }

    public long warnedForDay() {
        return warnedForDay;
    }

    public void setWarnedForDay(long day) {
        this.warnedForDay = day;
    }

    /** Called when a siege ends, so the cooldown starts from here. */
    public void markSiegeFinished(long gameTime) {
        this.lastSiegeDay = SiegeMath.dayOf(gameTime);
    }

    /**
     * @param gameTime    total world time in ticks
     * @param siegeActive whether a siege is already running
     */
    public Decision evaluate(long gameTime, boolean siegeActive, SiegeConfig cfg) {
        if (siegeActive) {
            return Decision.NONE;
        }
        long day = SiegeMath.dayOf(gameTime);
        int time = SiegeMath.timeOfDay(gameTime);

        if (!cooldownOver(day, cfg)) {
            return Decision.NONE;
        }
        // The warning comes first and arms the start for the same night.
        if (warnedForDay != day && time >= cfg.warningDayTime && time < cfg.startDayTime) {
            warnedForDay = day;
            return Decision.WARN;
        }
        if (warnedForDay == day && startedForDay != day && time >= cfg.startDayTime) {
            startedForDay = day;
            return Decision.START;
        }
        return Decision.NONE;
    }

    private boolean cooldownOver(long day, SiegeConfig cfg) {
        if (lastSiegeDay == Long.MIN_VALUE) {
            return true;
        }
        return day - lastSiegeDay >= cfg.daysBetweenSieges;
    }
}
