package com.mki.nightsiege.core;

/**
 * Who may start and stop a playtest (Tor 9.1 j, Simon 09.11. 09:26: "Playtest aber für alle möglich,
 * also ich kann auch alleine einen starten"). Every player may, without OP: start one when no night and
 * no other playtest runs, for themselves alone or with those who said "mitmachen" (nobody is pulled in
 * against their will); stop it, if they started it or take part. Between two starts by non-OPs there is
 * a cooldown, so nobody can start and stop over and over. OPs keep everything as before.
 */
public final class PlaytestAccess {

    /** Ticks between two playtest starts by non-OPs (10 minutes). */
    public static final long START_COOLDOWN_TICKS = 20L * 60 * 10;
    /** How long a "mitmachen" holds (5 minutes). */
    public static final long JOIN_WINDOW_TICKS = 20L * 60 * 5;
    /** At most this many players in a playtest started by a non-OP (the starter included). */
    public static final int MAX_PLAYERS = 10;

    private PlaytestAccess() {
    }

    /**
     * Null when this start may go on, else the translation key of why not.
     *
     * @param op        the source has OP (level 2) – the console too
     * @param byPlayer  a player typed it
     * @param running   a playtest runs already
     * @param nightBusy a night runs, is prepared or its horn has sounded
     * @param now       server tick now
     * @param lastStart server tick of the last start, or a negative number for none
     */
    public static String startProblem(boolean op, boolean byPlayer, boolean running, boolean nightBusy,
                                      long now, long lastStart) {
        if (running) {
            return "nightsiege.playtest.already";
        }
        if (nightBusy) {
            return "nightsiege.cmd.already_active";
        }
        if (op) {
            return null;
        }
        if (!byPlayer) {
            return "nightsiege.cmd.player_only";
        }
        if (cooldownSecondsLeft(now, lastStart) > 0) {
            return "nightsiege.playtest.cooldown";
        }
        return null;
    }

    /** Seconds until a non-OP may start again (0: now). */
    public static int cooldownSecondsLeft(long now, long lastStart) {
        if (lastStart < 0 || now < lastStart) {
            return 0;
        }
        long left = START_COOLDOWN_TICKS - (now - lastStart);
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    /** Highest target level a non-OP may choose (Chef 09.11.). */
    public static final int MAX_LEVEL_NON_OP = 25;

    /** Null when that target level is allowed, else the translation key (it takes the highest level). */
    public static String levelProblem(boolean op, int target) {
        return op || (target >= 1 && target <= MAX_LEVEL_NON_OP) ? null : "nightsiege.playtest.level_limit";
    }

    /** Whether this one may stop the running playtest. */
    public static boolean mayStop(boolean op, boolean starter, boolean participant) {
        return op || starter || participant;
    }

    /** Whether a "mitmachen" of that tick still holds. */
    public static boolean joinHolds(long now, long joinedAt) {
        return joinedAt >= 0 && now >= joinedAt && now - joinedAt <= JOIN_WINDOW_TICKS;
    }

    // Tor 9.1 j, Prüfer 09.11.: a non-OP must not hold the real night back with a playtest.

    /** A non-OP starts only with at least this much game time before the next real night's horn (30 min). */
    public static final long MIN_TICKS_BEFORE_NIGHT = 20L * 60 * 30;
    /** A playtest a non-OP started ends after this long in "plan"/"paused" without moving on (10 min). */
    public static final long IDLE_LIMIT_TICKS = 20L * 60 * 10;
    /** The warning comes this long before (1 min). */
    public static final long IDLE_WARN_BEFORE_TICKS = 20L * 60;

    /**
     * Null when the next real night leaves room, else the translation key. {@code ticksToHorn} &lt; 0:
     * no siege day planned yet (then nothing blocks).
     */
    public static String nightProblem(boolean op, long ticksToHorn) {
        return op || ticksToHorn < 0 || ticksToHorn >= MIN_TICKS_BEFORE_NIGHT ? null : "nightsiege.playtest.night_soon";
    }

    /** What the idle watch does now. */
    public enum Idle { NONE, WARN, END }

    /**
     * The idle watch of a playtest: only for a start by a non-OP ({@code limited}); {@code idleTicks} in
     * "plan"/"paused" since the phase began; {@code warned}: the warning was sent already.
     */
    public static Idle idleStep(boolean limited, long idleTicks, boolean warned) {
        if (!limited || idleTicks < IDLE_LIMIT_TICKS - IDLE_WARN_BEFORE_TICKS) {
            return Idle.NONE;
        }
        if (idleTicks >= IDLE_LIMIT_TICKS) {
            return Idle.END;
        }
        return warned ? Idle.NONE : Idle.WARN;
    }
}
