package com.mki.nightsiege.core;

/**
 * Bossnacht neu (Simon 11.11.2026: "nach 3 minuten spawnt der boss und seine wächter und alle
 * anderen monster ziehen sich zurück bzw verschwinden"). A boss night starts with normal waves (no event);
 * after bossFinaleSeconds the boss and his guard come, every other monster withdraws from the core
 * and is gone within ten seconds (at once when it is stuck). Then no more waves, machines or camp
 * reinforcements; the boss's fall wins the night, the kill goal does not count.
 */
public final class BossNight {

    /** A withdrawing monster is gone after this many ticks at the latest (10 s). */
    public static final int WITHDRAW_MAX_TICKS = 200;
    /** Every this many ticks a withdrawing monster is checked for being stuck. */
    public static final int STUCK_CHECK_TICKS = 20;
    /** Less than this many blocks (sideways) in a check: the monster is stuck and goes at once. */
    public static final double STUCK_BLOCKS = 0.5;
    /** The boss night's log line comes every this many ticks (10 s). */
    public static final int LOG_TICKS = 200;

    private BossNight() {
    }

    /** The phases of a boss night, with their names in the night file. */
    public enum Phase {
        BEFORE_BOSS("vor_boss"),
        BOSS("boss");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static Phase phase(boolean bossCame) {
        return bossCame ? Phase.BOSS : Phase.BEFORE_BOSS;
    }

    /** Ticks after the start of the night at which the boss comes: bossFinaleSeconds, at most half the night. */
    public static int arrivalTicks(int totalTicks, SiegeConfig cfg) {
        return Math.max(20, Math.min(cfg.bossFinaleSeconds * 20, totalTicks / 2));
    }

    /** True once the boss is due ({@code elapsed} ticks of the night have run). */
    public static boolean due(int elapsed, int arrival) {
        return elapsed >= arrival;
    }

    /**
     * Bossuhr (switch bossPhaseHoldsDawn, on since Chef 12.11. 07:50): once the boss of a boss night came,
     * the dawn waits while he stands. His fall (won) and the wipe-out (lost) end the night as before.
     */
    public static boolean holdsDawn(boolean on, boolean bossCame, boolean bossFell) {
        return on && bossCame && !bossFell;
    }

    /**
     * Bossuhr: how long the boss phase may run, from the boss's arrival: the night's normal time left then or
     * bossPhaseMaxSeconds, whichever is longer, so the clock never makes the night shorter (Prüfer 12.11.).
     */
    public static int phaseLimitTicks(int nightTicksLeftAtArrival, int maxSeconds) {
        return Math.max(nightTicksLeftAtArrival, maxSeconds * 20);
    }

    /** Bossuhr: the boss phase ran out ({@code phaseTicks} since the boss came): the night is lost, as by the wipe-out. */
    public static boolean phaseOver(int phaseTicks, int limitTicks) {
        return phaseTicks >= limitTicks;
    }

    /** Bossuhr: ticks of the boss phase left, for the bar and the clock (never below 0). */
    public static int phaseTicksLeft(int phaseTicks, int limitTicks) {
        return Math.max(0, limitTicks - phaseTicks);
    }

    /** Bossuhr: the boss phase's time left as m:ss for the bar ("9:59", "1:00"). */
    public static String clock(int ticksLeft) {
        int s = Math.max(0, ticksLeft) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    /** Bossuhr (Chef 12.11.): the HUD warns at 1:00 left, shown for three seconds (60, 59, 58). */
    public static boolean warnNow(int ticksLeft) {
        return ticksLeft % 20 == 0 && ticksLeft <= 60 * 20 && ticksLeft > 57 * 20;
    }

    /** False once the boss of a boss night has come: no more waves, machines or camp reinforcements. */
    public static boolean reinforcements(boolean bossNight, boolean bossCame) {
        return !(bossNight && bossCame);
    }

    /** Which monster withdraws when the boss comes: all but the boss with his parts and his guard. */
    public static boolean withdraws(boolean bossOrPart, boolean guard) {
        return !bossOrPart && !guard;
    }

    /**
     * True when a withdrawing monster goes now: after {@link #WITHDRAW_MAX_TICKS}, or at a check
     * ({@code checked}) when it moved less than {@link #STUCK_BLOCKS} since the last one.
     */
    public static boolean vanishes(long ticksAway, boolean checked, double movedBlocks) {
        return ticksAway >= WITHDRAW_MAX_TICKS || checked && ticksAway > 0 && movedBlocks < STUCK_BLOCKS;
    }

    /** A withdrawing monster (or a shot of it) harms nobody any more: no contact, no blow, no arrow. */
    public static boolean hitAllowed(boolean attackerWithdrawing) {
        return !attackerWithdrawing;
    }

    /**
     * The storm witch's phantom takes no damage itself (12.11.); a defender's hit on it (blow, arrow, a
     * companion's) goes to the boss in its saddle. Monsters, the witch's own lightning and the world pass nothing on.
     */
    public static boolean mountHitGoesToRider(boolean byDefender, boolean riderIsBoss) {
        return byDefender && riderIsBoss;
    }

    /**
     * True for a monster of the night that loads (or turns up again) once the boss came and is
     * neither of the night's monsters, the boss's side (boss, part, guard, what they call up) nor a
     * companion: it was in an unloaded chunk at the withdrawal and goes at once.
     */
    public static boolean lateStray(boolean bossCame, boolean nightMob, boolean live, boolean bossSide, boolean companion) {
        return bossCame && nightMob && !live && !bossSide && !companion;
    }

    /** A boss night has no event (Chef 11.11. 23:35, as before), whichever way the event was picked. */
    public static SiegeEvent eventFor(boolean bossNight, SiegeEvent picked) {
        return bossNight ? SiegeEvent.NONE : picked;
    }

    /** The kill goal counts in no boss night: the boss is the goal. */
    public static boolean killGoalCounts(boolean bossNight) {
        return !bossNight;
    }

    /** The boss's life left in percent (100 before he comes, 0 once he fell). */
    public static double lifePercent(boolean came, boolean fallen, double health, double maxHealth) {
        if (!came) {
            return 100.0;
        }
        if (fallen || maxHealth <= 0) {
            return 0.0;
        }
        return Math.round(Math.max(0.0, Math.min(1.0, health / maxHealth)) * 1000.0) / 10.0;
    }
}
