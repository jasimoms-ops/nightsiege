package com.mki.nightsiege.core;

/**
 * Tor 12 F (Chef 17.11., Simon: "das mit dem kill Ziel muss geändert werden"): the core's life instead of the
 * kill goal. The core has coreHealth life in every night, no healing; monsters within coreHitRange hit it
 * every coreHitSeconds. A normal monster costs coreDamageNormal, an elite, champion or nemesis
 * coreDamageStrong, a boss coreDamageBoss. Lost: the core at 0 or every defender fallen. Won: the night is
 * over and the core lives (a boss night: the boss fell). coreHealth 0 turns the core off (the scenes).
 */
public final class CoreLife {

    public enum Rank { NORMAL, STRONG, BOSS }

    public enum End { GOES_ON, WON, LOST }

    private CoreLife() {
    }

    public static boolean on(SiegeConfig cfg) {
        return cfg.coreHealth > 0;
    }

    /** The rank of a monster at the core. */
    public static Rank rank(boolean boss, boolean elite, boolean champion, boolean nemesis) {
        return boss ? Rank.BOSS : elite || champion || nemesis ? Rank.STRONG : Rank.NORMAL;
    }

    /** Life one hit of a monster of this rank costs. */
    public static int damage(Rank rank, SiegeConfig cfg) {
        return switch (rank) {
            case BOSS -> cfg.coreDamageBoss;
            case STRONG -> cfg.coreDamageStrong;
            case NORMAL -> cfg.coreDamageNormal;
        };
    }

    /** The core's life after a hit, never below 0. */
    public static int afterHit(int life, int damage) {
        return Math.max(0, life - Math.max(0, damage));
    }

    /** Whether a monster this far from the core's block (feet against the block's centre) reaches it. */
    public static boolean inReach(double dx, double dy, double dz, double range) {
        return dx * dx + dz * dz <= range * range && Math.abs(dy) <= range;
    }

    /** The warning a fall of the life crosses: 50 or 25 (per cent of the life), 0 for none. */
    public static int warning(int before, int after, int max) {
        if (max <= 0) {
            return 0;
        }
        if (before * 4 > max && after * 4 <= max) {
            return 25;
        }
        if (before * 2 > max && after * 2 <= max) {
            return 50;
        }
        return 0;
    }

    /**
     * The night's end by the rules: lost with the core at 0 (when on) or every defender fallen; at dawn won
     * when the core lives and no boss of the night stands; otherwise it goes on.
     */
    public static End outcome(boolean coreOn, int life, int standing, int fallen, boolean dawn, boolean bossStands) {
        if (coreOn && life <= 0) {
            return End.LOST;
        }
        if (standing == 0 && fallen > 0) {
            return End.LOST;
        }
        if (dawn) {
            return bossStands ? End.LOST : End.WON;
        }
        return End.GOES_ON;
    }
}
