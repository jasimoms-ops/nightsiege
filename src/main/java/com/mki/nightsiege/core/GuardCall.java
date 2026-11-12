package com.mki.nightsiege.core;

/**
 * Hüterruf (Paket 11.11., Simon 12.11. 14:56: "gib den Lichtbändigern eine Fähigkeit, mit der sie sich verteidigen
 * können"; Chef 15:00 Z1): the tamer and the golemsmith get it with the subclass, without a skill point, on place 4
 * (C; place 3 / F holds the arrow kinds). Pure defence, no damage:
 * <ul>
 *   <li>a shock wave of {@code radius} blocks: monsters fly {@code push} blocks back and get Slowness II for
 *       {@code slow} ticks; bosses and guards ({@code BossService.immovable}) only {@code bossslow} ticks, no push;</li>
 *   <li>the summoner takes {@code cut} less damage for {@code guard} ticks;</li>
 *   <li>every own companion further than {@code far} blocks comes back to him (within {@code near});</li>
 *   <li>then all companions attack the monster that hit him last, if that was at most {@code memory} ticks ago.</li>
 * </ul>
 * Pause {@code cd} ticks. Server tweaks: {@code skillTweaks.guardcall.values.<key>}.
 * The numbers live here (no Minecraft types) so the tests can check them; {@code game.Companions} fires it.
 */
public final class GuardCall {

    /** The ability id on the bar and in {@link AbilityKeys}. */
    public static final String ID = "guardcall";
    /** Lift of the push: the monster leaves the ground, so the ground's friction does not eat the push at once. */
    public static final double LIFT = 0.35;
    /** Amplifier of the slowness (1 = Slowness II). */
    public static final int SLOW_AMPLIFIER = 1;

    private GuardCall() {
    }

    private static double v(String key, double fallback) {
        return SkillValues.get(ID, key, fallback);
    }

    /** Blocks around the summoner the shock wave reaches (4). */
    public static double radius() {
        return Math.max(0.0, v("radius", 4.0));
    }

    /** Blocks a monster flies back (4). */
    public static double pushBlocks() {
        return Math.max(0.0, v("push", 4.0));
    }

    /** Ticks of Slowness II: a plain monster 40 (2 s), a boss or a guard 20 (1 s). */
    public static int slowTicks(boolean bossOrGuard) {
        return (int) Math.max(0, Math.round(bossOrGuard ? v("bossslow", 20) : v("slow", 40)));
    }

    /** Ticks the summoner takes less damage (80 = 4 s). */
    public static int guardTicks() {
        return (int) Math.max(0, Math.round(v("guard", 80)));
    }

    /** The share of the damage taken away while guarded (0.40). */
    public static double cut() {
        return Math.max(0.0, Math.min(1.0, v("cut", 0.40)));
    }

    /** Companions further away than this come back (12 blocks). */
    public static double farBlocks() {
        return Math.max(0.0, v("far", 12.0));
    }

    /** They come back to at most this many blocks from him (3). */
    public static double nearBlocks() {
        return Math.max(1.5, v("near", 3.0));
    }

    /** How long ago the last hit on him may be for the companions to go after its monster (100 = 5 s). */
    public static int memoryTicks() {
        return (int) Math.max(0, Math.round(v("memory", 100)));
    }

    /** The pause in ticks (360 = 18 s). */
    public static int cooldownTicks() {
        return (int) Math.max(20, Math.round(v("cd", 360)));
    }

    /** Ticks until it is ready again (0: ready). */
    public static int cooldownLeft(long readyAt, long now) {
        return readyAt <= now ? 0 : (int) Math.min(Integer.MAX_VALUE, readyAt - now);
    }

    /** The factor on a hit taken: {@code 1 - cut} while guarded (now before {@code guardedUntil}), else 1. */
    public static float takenFactor(long guardedUntil, long now) {
        return now < guardedUntil ? (float) (1.0 - cut()) : 1f;
    }

    /** True when the monster that hit him last may still be the companions' target. */
    public static boolean freshHit(long hitAt, long now) {
        return hitAt >= 0 && now >= hitAt && now - hitAt <= memoryTicks();
    }

    /** True when a companion this far away is called back. */
    public static boolean recalls(double distance) {
        return distance > farBlocks();
    }

    /** True when the shock wave reaches a monster this far away. */
    public static boolean reaches(double distance) {
        return distance <= radius();
    }

    /**
     * The push of a monster at offset (dx, dz) from the summoner: {x, y, z} of its new motion, away from him,
     * {@link #pushBlocks} far. A boss or a guard: none (all zeros). A monster right on him goes the way he looks
     * ({@code lookX, lookZ}).
     */
    public static double[] push(double dx, double dz, double lookX, double lookZ, boolean bossOrGuard) {
        if (bossOrGuard) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) {
            dx = lookX;
            dz = lookZ;
            len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-3) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
        }
        double speed = pushBlocks() / flight(1.0, LIFT);
        return new double[]{dx / len * speed, LIFT, dz / len * speed};
    }

    /**
     * How far a body flies with horizontal speed {@code speed} and lift {@code lift} from flat ground, by vanilla's
     * rules for a living entity: in the air gravity 0.08 and drag 0.98 up and down, 0.91 sideways; on the ground the
     * friction 0.6 × 0.91 until it stands. Its own walking is not counted.
     */
    public static double flight(double speed, double lift) {
        double y = 0.0;
        double vy = lift;
        double vx = speed;
        double x = 0.0;
        boolean ground = lift <= 0.0;
        for (int t = 0; t < 200 && Math.abs(vx) > 1e-4; t++) {
            x += vx;
            if (!ground) {
                y += vy;
                vy = (vy - 0.08) * 0.98;
                if (y <= 0.0) {
                    ground = true;
                }
            }
            vx *= ground ? 0.6 * 0.91 : 0.91;
        }
        return x;
    }
}
