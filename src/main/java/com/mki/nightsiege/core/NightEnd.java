package com.mki.nightsiege.core;

/**
 * Tor 12 F (Simon 17.11. 15:28 on the kill goal: "nein einfach nur raus nehmen"): a night is won when it is over
 * and at least one defender stands (a boss night: the boss fell); lost when every defender fell. Kills do not count.
 */
public final class NightEnd {

    public enum End { GOES_ON, WON, LOST }

    private NightEnd() {
    }

    public static End outcome(int standing, int fallen, boolean dawn, boolean bossStands) {
        if (standing == 0 && fallen > 0) {
            return End.LOST;
        }
        if (dawn) {
            return bossStands ? End.LOST : End.WON;
        }
        return End.GOES_ON;
    }

    /** The result in the statistics: "gewonnen" or "verloren" (no more "ziel_verfehlt"). */
    public static String result(boolean won) {
        return won ? "gewonnen" : "verloren";
    }
}
