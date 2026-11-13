package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The bot war leader's choices (Tor 10, plan-kriegsfuehrer-3 Prüfen 4): what a good human leader does with
 * what he sees in his window (the defenders' places and life bars, the base's edge and gates), nothing more.
 * Pure logic for the team simulation's bot ({@code WarLeader.tickBot}) and its tests.
 */
public final class WarBotPlan {

    private WarBotPlan() {
    }

    /** A defender as the leader's window shows him. */
    public record Defender(String name, double x, double y, double z, float health, float max) {
        public float share() {
            return max <= 0 ? 1f : health / max;
        }
    }

    /** A defender with no mate this close counts as alone (blocks). */
    public static final double ALONE = 9.0;
    /** A wall spot counts the defenders this close (blocks). */
    public static final double WATCHED = 16.0;
    /** Below this share of its life a squad pulls back. */
    public static final double RETREAT_SHARE = 0.3;

    /**
     * The defender to focus: the weakest of those standing alone, else the weakest of all (by life share, then
     * life). -1 for none.
     */
    public static int focus(List<Defender> defenders) {
        int best = -1;
        boolean bestAlone = false;
        for (int i = 0; i < defenders.size(); i++) {
            Defender d = defenders.get(i);
            boolean alone = nearestMate(defenders, i) > ALONE;
            if (best < 0 || (alone && !bestAlone) || (alone == bestAlone && weaker(d, defenders.get(best)))) {
                best = i;
                bestAlone = alone;
            }
        }
        return best;
    }

    private static boolean weaker(Defender a, Defender b) {
        if (Math.abs(a.share() - b.share()) > 0.02f) {
            return a.share() < b.share();
        }
        return a.health() < b.health();
    }

    /** The distance to the nearest other defender (infinite when he is the only one). */
    public static double nearestMate(List<Defender> defenders, int i) {
        Defender d = defenders.get(i);
        double best = Double.POSITIVE_INFINITY;
        for (int j = 0; j < defenders.size(); j++) {
            if (j != i) {
                Defender o = defenders.get(j);
                best = Math.min(best, Math.hypot(o.x() - d.x(), o.z() - d.z()));
            }
        }
        return best;
    }

    /**
     * The weakest spot of the wall: the one the fewest defenders watch (within {@link #WATCHED}), on a tie the
     * one farthest from the nearest defender, on a tie a gate before the wall. Spots as {x, z, gate 1/0}. -1 for none.
     */
    public static int quietest(List<double[]> spots, List<Defender> defenders) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        double bestFar = -1;
        boolean bestGate = false;
        for (int i = 0; i < spots.size(); i++) {
            double[] s = spots.get(i);
            int count = 0;
            double far = Double.POSITIVE_INFINITY;
            for (Defender d : defenders) {
                double dist = Math.hypot(d.x() - s[0], d.z() - s[1]);
                far = Math.min(far, dist);
                if (dist <= WATCHED) {
                    count++;
                }
            }
            boolean gate = s.length > 2 && s[2] > 0;
            boolean better = count < bestCount
                    || (count == bestCount && (Math.abs(far - bestFar) > 1.0 ? far > bestFar : gate && !bestGate));
            if (best < 0 || better) {
                best = i;
                bestCount = count;
                bestFar = far;
                bestGate = gate;
            }
        }
        return best;
    }

    /**
     * Where a machine shoots: the defender with the most mates within 5 blocks (a group), one standing high
     * (a tower, the wall) counts one more. -1 for none.
     */
    public static int machineTarget(List<Defender> defenders, double groundY) {
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < defenders.size(); i++) {
            Defender d = defenders.get(i);
            int score = d.y() >= groundY + 3 ? 1 : 0;
            for (int j = 0; j < defenders.size(); j++) {
                Defender o = defenders.get(j);
                if (j != i && Math.hypot(o.x() - d.x(), o.z() - d.z()) <= 5.0) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /** A squad this hurt pulls back (to be spared for the next assault). */
    public static boolean retreat(float health, float max) {
        return max > 0 && health / max < RETREAT_SHARE;
    }

    /**
     * Tor 10 bot fix: a sent squad pulls back only when it lost life in this assault, measured on its life when it was
     * sent (not on its full size with the fallen: a thinned squad would be pulled back two seconds after every storm).
     */
    public static boolean retreatSinceSent(float health, float sentHealth) {
        return retreat(health, sentHealth);
    }

    /**
     * Tor 10 bot fix: a wall to climb only when the scan found a closed base; then ladders, sappers and the wall's
     * weakest spot. An open base (no scan, not closed) is stormed at its defenders by the core.
     */
    public static boolean walled(boolean scanned, boolean closed) {
        return scanned && closed;
    }

    /** So many monsters wait at the points before an assault: a bigger group, a bigger wave. */
    public static int assaultSize(int defenders) {
        return 6 + 3 * Math.max(1, defenders);
    }

    /**
     * The shopping list by the group (ids of the shop, drawn at random): against few defenders many cheap ones,
     * against many the strong and the ranged ones.
     */
    public static List<String> shopping(int defenders) {
        List<String> out = new ArrayList<>(List.of("zombies", "zombies", "skelette", "skelette", "spinnen", "creeper",
                "wiedergaenger", "wiedergaenger", "axtillager", "enterhaken", "tunnelgraeber", "standarte", "kameltrupp"));
        if (defenders <= 2) {
            out.addAll(List.of("zombies", "spinnen", "spinnen", "creeper"));
        } else {
            out.addAll(List.of("eisenritter", "axtillager", "skelette", "wiedergaenger"));
            if (defenders >= 5) {
                out.addAll(List.of("eisenritter", "axtillager", "standarte"));
            }
        }
        return out;
    }

    /**
     * The shopping list by the situation: as {@link #shopping(int)}, and for every defender standing high (a tower,
     * the wall; at most three) two more archer squads, the ranged ones against them.
     */
    public static List<String> shopping(int defenders, int high) {
        List<String> out = shopping(defenders);
        for (int i = 0; i < Math.min(3, Math.max(0, high)); i++) {
            out.addAll(List.of("skelette", "skelette"));
        }
        return out;
    }

    /** A squad of the shop as the bot weighs it: its id, price and monsters. */
    public record Offer(String id, int price, int size) {
    }

    /**
     * The next buy as a good human leader makes it: drawn from the list (the duplicates are its weights) among the
     * squads that fit both the purse and the room left under the live cap, so a full field does not stop the
     * small ones. Null when none fits.
     */
    public static Offer pick(List<Offer> wanted, int gold, int room, java.util.random.RandomGenerator random) {
        List<Offer> fit = new ArrayList<>();
        for (Offer o : wanted) {
            if (o != null && o.price() <= gold && o.size() <= room) {
                fit.add(o);
            }
        }
        return fit.isEmpty() ? null : fit.get(random.nextInt(fit.size()));
    }

    /** Why nothing of the list fits: "voll" when the gold is there but the live cap leaves no room, else "arm". */
    public static String stop(List<Offer> wanted, int gold, int room) {
        for (Offer o : wanted) {
            if (o != null && o.price() <= gold) {
                return "voll";
            }
        }
        return "arm";
    }

    /** Defenders standing high (on a tower, the wall): three blocks above the ground or more. */
    public static int high(List<Defender> defenders, double groundY) {
        int n = 0;
        for (Defender d : defenders) {
            if (d.y() >= groundY + 3) {
                n++;
            }
        }
        return n;
    }

    /** Ladder carriers per wave: one, and one more for every three defenders. */
    public static int ladders(int defenders) {
        return 1 + Math.max(0, defenders) / 3;
    }
}
