package com.mki.nightsiege.core;

/**
 * When the next siege night comes: every few in-game days (three by default)
 * the registered defenders are invited in the morning. With enough accepts the
 * horn sounds at sunset and the night is played; otherwise it is skipped and
 * the next invitation comes a few days later. Only won nights raise the level,
 * so skipped days never move anything on.
 *
 * <p>Pure arithmetic on the saved state, so it can be unit tested.</p>
 */
public final class SiegeRhythm {

    public enum Step {
        /** Nothing to do now. */
        NONE,
        /** No siege day set yet: set it a few days ahead. */
        SCHEDULE,
        /** The siege day went by unplayed (server off, time skipped): set a new one. */
        MISSED,
        /** Morning of the siege day: send the invitations. */
        INVITE,
        /** Enough accepts at closing time: sound the horn. */
        WARN,
        /** Too few accepts at closing time: the night is skipped. */
        CANCEL,
        /** Start time after a warning: begin the siege. */
        START
    }

    private SiegeRhythm() {
    }

    /**
     * @param day          current in-game day
     * @param time         time of day, 0 to 23999
     * @param siegeDay     the day of the next siege, or a negative number if none is set
     * @param votedDay     the day the invitations last went out
     * @param warnedDay    the day the horn last sounded
     * @param startedDay   the day a siege last started
     * @param accepts      accepted invitations so far
     * @param closeTime    end of the vote: the warning time (sunset, or before noon for an eclipse)
     * @param startTime    start of the siege
     */
    public static Step decide(long day, int time, long siegeDay, long votedDay, long warnedDay, long startedDay,
                              int accepts, SiegeConfig cfg, int closeTime, int startTime) {
        return decide(day, time, siegeDay, votedDay, warnedDay, startedDay, accepts, Math.max(1, cfg.requiredAccepts),
                cfg, closeTime, startTime);
    }

    /**
     * As above with the accepts tonight needs ({@link #requiredAccepts}). A siege day further ahead
     * than any interval could put it (the clock was set back, /time set) is set anew from today.
     */
    public static Step decide(long day, int time, long siegeDay, long votedDay, long warnedDay, long startedDay,
                              int accepts, int required, SiegeConfig cfg, int closeTime, int startTime) {
        if (siegeDay < 0 || tooFarAhead(day, siegeDay, cfg)) {
            return Step.SCHEDULE;
        }
        if (day < siegeDay) {
            return Step.NONE;
        }
        if (day > siegeDay) {
            return Step.MISSED;
        }
        if (startedDay == day) {
            return Step.NONE;
        }
        int invite = Math.min(cfg.inviteDayTime, Math.max(0, closeTime - 200));
        if (votedDay != day) {
            // Not invited yet: in the morning, invite; once the vote would be over, the day is lost.
            return time >= invite && time < closeTime ? Step.INVITE
                    : time >= closeTime ? Step.CANCEL : Step.NONE;
        }
        boolean enough = accepts >= Math.max(1, required);
        if (warnedDay != day) {
            if (time < closeTime) {
                return Step.NONE;
            }
            if (!enough) {
                return Step.CANCEL;
            }
            // After a restart in the night the horn may have been missed: then start straight away.
            return time >= startTime ? Step.START : Step.WARN;
        }
        return time >= startTime ? Step.START : Step.NONE;
    }

    /**
     * Accepts a real night needs (Simon 31.10.: survival safety): the config's number at most,
     * otherwise everyone invited (two players invited: two accepts suffice), never less than one.
     */
    public static int requiredAccepts(SiegeConfig cfg, int invited) {
        return Math.max(1, Math.min(Math.max(1, cfg.requiredAccepts), invited));
    }

    /**
     * True when the siege day lies further ahead than the longest interval (+1 day): the clock went
     * back (/time set 1000 on day 50) and the rhythm would wait for weeks.
     */
    public static boolean tooFarAhead(long day, long siegeDay, SiegeConfig cfg) {
        return siegeDay >= 0 && siegeDay - day > Math.max(1, cfg.daysBetweenSieges) + 1L;
    }

    /** The next siege day after this one: a few days on. */
    public static long nextAfter(long day, SiegeConfig cfg) {
        return day + Math.max(1, cfg.daysBetweenSieges);
    }

    /** A new siege day after a missed one: still today if the morning invitation is yet to come. */
    public static long afterMissed(long day, int time, SiegeConfig cfg) {
        return time < cfg.inviteDayTime ? day : nextAfter(day, cfg);
    }

    /** Why no extra siege can be called right now (NONE: it can). */
    public enum ExtraBlock {
        NONE,
        /** Switched off in the config. */
        OFF,
        /** The world has not reached the level yet. */
        LEVEL,
        /** A siege runs, a test is being prepared, or the horn has already sounded. */
        NIGHT,
        /** Already called today (as often as allowed). */
        CALLED_TODAY,
        /** A regular siege is invited, planned or already held today. */
        SIEGE_TODAY,
        /** Too late for a vote: the vote would end at sunset (or now). */
        TOO_LATE
    }

    /**
     * Whether a registered player may call an extra siege for tonight with the button at the core
     * (Simon, 26 October 2026): from a world level on, at most once a day, never on a siege day,
     * never during a night, and only while a vote is still possible.
     *
     * @param level        the siege level of the world
     * @param siegeDay     the day of the next regular siege (negative: none set yet)
     * @param votedDay     the day the invitations last went out
     * @param heldToday    a siege night was already warned of, started or called off today
     * @param callsToday   extra sieges called today so far
     * @param busy         a siege runs, a test is prepared or tonight's horn has sounded
     * @param closeTime    end of the vote (sunset)
     */
    public static ExtraBlock extraSiegeBlock(long day, int time, int level, long siegeDay, long votedDay,
                                             boolean heldToday, int callsToday, boolean busy,
                                             SiegeConfig cfg, int closeTime) {
        if (cfg.extraSiegePerDay <= 0) {
            return ExtraBlock.OFF;
        }
        if (level < cfg.extraSiegeFromLevel) {
            return ExtraBlock.LEVEL;
        }
        if (busy) {
            return ExtraBlock.NIGHT;
        }
        if (callsToday >= cfg.extraSiegePerDay) {
            return ExtraBlock.CALLED_TODAY;
        }
        if ((siegeDay >= 0 && siegeDay <= day) || votedDay == day || heldToday) {
            return ExtraBlock.SIEGE_TODAY;
        }
        if (time >= closeTime) {
            return ExtraBlock.TOO_LATE;
        }
        return ExtraBlock.NONE;
    }

    /**
     * After an extra night (played, called off or missed) the regular plan goes on: back to the
     * siege day it had, or, if that went by meanwhile (server off), a new one as after a missed day.
     */
    public static long afterExtra(long regularDay, long day, int time, SiegeConfig cfg) {
        if (regularDay < 0) {
            return nextAfter(day, cfg);
        }
        return regularDay >= day ? regularDay : afterMissed(day, time, cfg);
    }

    /** 0 to 1: how far the days towards the next siege day have run. */
    public static double progress(long day, int time, long siegeDay, SiegeConfig cfg) {
        if (siegeDay < 0) {
            return 0.0;
        }
        double total = Math.max(1, cfg.daysBetweenSieges) * 24000.0;
        double left = (siegeDay - day) * 24000.0 + cfg.warningDayTime - time;
        return Math.max(0.0, Math.min(1.0, 1.0 - left / total));
    }

    /** Ticks until the horn of the next siege day. */
    public static long ticksUntilWarning(long day, int time, long siegeDay, SiegeConfig cfg) {
        if (siegeDay < 0) {
            return 0L;
        }
        return Math.max(0L, (siegeDay - day) * 24000L + cfg.warningDayTime - time);
    }
}
