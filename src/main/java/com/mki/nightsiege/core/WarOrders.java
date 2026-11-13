package com.mki.nightsiege.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The war leader's orders (Kriegsführer 3, K1, Simon 13.11.2026: "mehr Gewalt über die Armee"): what a
 * squad does with an order and whom it may fight then. Pure logic; the server ({@code WarLeader}) carries
 * it out, the client shows it.
 *
 * <ul>
 *   <li>Move: there, on the way only fight back. Attack: a defender until he falls, a building (the raid's
 *   way in), on open ground an attack march (everything on the way).</li>
 *   <li>Hold: stay, fight only within {@link #HOLD_RADIUS}. Retreat: back to the gathering point, no fight,
 *   {@link #RETREAT_SPEED}. Storm: at the base. Special: the unit's own job at a spot.</li>
 *   <li>Shift queues orders (way points); the stance (aggressive, defensive, passive) sets what a squad
 *   does on its own between and after orders.</li>
 *   <li>A squad without any order acts as before (gathering, the auto-storm).</li>
 * </ul>
 */
public final class WarOrders {

    private WarOrders() {
    }

    /** Hold: fight only within this many blocks of the spot. */
    public static final double HOLD_RADIUS = 6.0;
    /** Retreat: +20 % speed. */
    public static final double RETREAT_SPEED = 1.2;
    /** A squad within this many blocks of its spot has arrived. */
    public static final double ARRIVE = 3.0;
    /** An aggressive squad standing still fights who comes this close (the old gathering leash). */
    public static final double LEASH = 14.0;
    /** Orders take hold within this many ticks (0.5 s): the server steers commanded squads this often. */
    public static final int STEER_TICKS = 10;
    /** At most this many orders in a row (way points). */
    public static final int MAX_QUEUE = 8;
    /** The boss's ability on E: once in this many seconds. */
    public static final int BOSS_COOLDOWN_SECONDS = 25;

    /** What an order does. */
    public enum Kind {
        MOVE, ATTACK, ATTACK_MOVE, HOLD, RETREAT, STORM, SPECIAL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind byId(String id) {
            for (Kind k : values()) {
                if (k.id().equals(id)) {
                    return k;
                }
            }
            return null;
        }

        /** Green for moving, red for attacking, yellow for a special order (the marker, the way line). */
        public int color() {
            return switch (this) {
                case MOVE, HOLD, RETREAT -> 0xFF5BE36B;
                case ATTACK, ATTACK_MOVE, STORM -> 0xFFFF4040;
                case SPECIAL -> 0xFFFFE040;
            };
        }

        /** True for orders that need a spot. */
        public boolean needsSpot() {
            return this == MOVE || this == ATTACK || this == ATTACK_MOVE || this == SPECIAL;
        }

        /** After this order the squad stays with it (no next way point is taken). */
        public boolean last() {
            return this == HOLD || this == STORM;
        }
    }

    /** What a squad does on its own: chase, stay near, or not fight at all unless told. */
    public enum Stance {
        AGGRESSIVE, DEFENSIVE, PASSIVE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Stance next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Stance byId(String id) {
            for (Stance s : values()) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return AGGRESSIVE;
        }
    }

    /** The unit's own job on E (with its own spot). */
    public enum Special {
        NONE, LADDER, BLAST, TUNNEL, GRAPPLE, STANDARD, AIM, BOSS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The boss's ability goes at once; everything else wants a spot. */
        public boolean needsSpot() {
            return this != BOSS && this != NONE;
        }

        /** E twice: now, without a spot (the sapper blows where it stands). */
        public boolean hasNow() {
            return this == BLAST || this == BOSS;
        }

        public static Special byId(String id) {
            for (Special s : values()) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return NONE;
        }
    }

    /** The special order of a squad of the shop (by its id). */
    public static Special specialOf(String item) {
        if (item == null) {
            return Special.NONE;
        }
        if (item.startsWith("machine.")) {
            return Special.AIM;
        }
        if (item.startsWith("boss.")) {
            return Special.BOSS;
        }
        return switch (item) {
            case "leitertraeger" -> Special.LADDER;
            case WarShop.SAPPER -> Special.BLAST;
            case "tunnelgraeber" -> Special.TUNNEL;
            case "enterhaken" -> Special.GRAPPLE;
            case "standarte" -> Special.STANDARD;
            default -> Special.NONE;
        };
    }

    /** One order: what, where, at whom ("" for a spot). */
    public record Order(Kind kind, double x, double z, String target) {
        public Order {
            target = target == null ? "" : target;
        }

        public static Order at(Kind kind, double x, double z) {
            return new Order(kind, x, z, "");
        }

        public boolean hasTarget() {
            return !target.isEmpty();
        }
    }

    /** A squad's orders in a row: the first is carried out, Shift adds to the end. */
    public static final class Queue {
        private final Deque<Order> orders = new ArrayDeque<>();

        /** A new order: the old ones go (without Shift) or it waits behind them (Shift). */
        public void give(Order order, boolean queued) {
            if (order == null) {
                return;
            }
            if (!queued || (!orders.isEmpty() && orders.peekLast().kind().last())) {
                orders.clear();
            }
            if (orders.size() >= MAX_QUEUE) {
                return;
            }
            orders.addLast(order);
        }

        public Order current() {
            return orders.peekFirst();
        }

        /** The current order is done: the next one, or null (the squad stands). */
        public Order next() {
            orders.pollFirst();
            return orders.peekFirst();
        }

        public boolean isEmpty() {
            return orders.isEmpty();
        }

        public int size() {
            return orders.size();
        }

        public List<Order> all() {
            return new ArrayList<>(orders);
        }

        public void clear() {
            orders.clear();
        }
    }

    /**
     * May a monster of a commanded squad fight this target?
     *
     * @param kind          the current order, null when it stands without one (between or after orders)
     * @param stance        the squad's stance
     * @param orderTarget   the target is the one the order names (attack a defender), or the attack names
     *                      nobody (a building: whoever stands in the way)
     * @param attacker      the target hit this monster (it fights back)
     * @param fromSpot      the target's distance from where the squad stands or holds
     */
    public static boolean mayEngage(Kind kind, Stance stance, boolean orderTarget, boolean attacker, double fromSpot) {
        if (kind == Kind.RETREAT || kind == Kind.SPECIAL) {
            return false;
        }
        if (kind == Kind.ATTACK) {
            return orderTarget;
        }
        if (kind == Kind.ATTACK_MOVE || kind == Kind.STORM) {
            return true;
        }
        if (stance == Stance.PASSIVE) {
            return false;
        }
        if (kind == Kind.MOVE) {
            return attacker;
        }
        if (kind == Kind.HOLD) {
            return fromSpot <= HOLD_RADIUS;
        }
        // Standing without an order.
        return stance == Stance.AGGRESSIVE ? fromSpot <= LEASH : attacker || fromSpot <= HOLD_RADIUS;
    }

    /**
     * What a click gives (a right click, or a left click after M, A or E): null for nothing (nothing
     * picked, no spot under the pointer). Right click: on a defender or the base attack, else move.
     * A: on a defender or the base attack, on open ground the attack march.
     */
    public static Kind intent(boolean anyPicked, Kind armed, boolean spot, boolean onDefender, boolean onBase) {
        if (!anyPicked || (!spot && !onDefender)) {
            return null;
        }
        if (armed == Kind.SPECIAL) {
            return spot ? Kind.SPECIAL : null;
        }
        if (armed == Kind.ATTACK || armed == Kind.ATTACK_MOVE) {
            return onDefender || onBase ? Kind.ATTACK : Kind.ATTACK_MOVE;
        }
        if (armed == Kind.MOVE) {
            return Kind.MOVE;
        }
        return onDefender || onBase ? Kind.ATTACK : Kind.MOVE;
    }

    /**
     * Is the spot on the base (inside its edge or on the wall, within the margin)? {@code edge} is the base's
     * edge per slice around the core (slice i from angle i/n of the full turn, counted from east to south).
     */
    public static boolean onBase(List<Double> edge, double cx, double cz, double x, double z, double margin) {
        if (edge == null || edge.isEmpty()) {
            return false;
        }
        double a = Math.atan2(z - cz, x - cx);
        if (a < 0) {
            a += 2 * Math.PI;
        }
        int n = edge.size();
        int i = Math.min(n - 1, (int) (a / (2 * Math.PI) * n));
        double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
        return d <= edge.get(i) + margin;
    }

    /** Has the squad's middle arrived at the spot? */
    public static boolean arrived(double x, double z, double tx, double tz) {
        double dx = x - tx;
        double dz = z - tz;
        return dx * dx + dz * dz <= ARRIVE * ARRIVE;
    }

    /** Ticks until the boss's ability is ready again (0: ready). */
    public static long cooldownLeft(long lastUse, long now) {
        if (lastUse < 0) {
            return 0;
        }
        return Math.max(0, lastUse + BOSS_COOLDOWN_SECONDS * 20L - now);
    }
}
