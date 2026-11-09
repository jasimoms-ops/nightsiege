package com.mki.nightsiege.core;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Tor 5.7 (Simon 09.11. 08:50: "alles, was man bekommt, ist ein Gewinn für die Tasten 1-3, auch
 * Verbrauchsgüter"): the healing potions, golden apples, ender pearls and barricades are no items any
 * more but charges on a gain field. The key fires at once (no drinking, no eating), a short lock stops
 * a double press, the same things stack in one field. Pure logic, so it can be tested on its own.
 */
public final class GainCharges {

    /** The gain fields with charges, in the order they fill the keys. */
    public static final String POTION = "heiltrank";
    public static final String APPLE = "notration";
    public static final String PEARL = "wurfperlen";
    public static final String BARRICADE = "barrikaden";
    public static final List<String> FIELDS = List.of(POTION, APPLE, PEARL, BARRICADE);

    /** The guardians' first-aid kit adds its potions to the field "Heiltrank". */
    public static final String FIRST_AID = "verbandskasten";

    /** The lock after a charge, in ticks (1 s): one press, one charge. */
    public static final int LOCK_TICKS = 20;

    private GainCharges() {
    }

    public static boolean isField(String id) {
        return id != null && FIELDS.contains(id);
    }

    /**
     * The charges a field brings per kit, as many as the items were (inventar.md, section 1).
     *
     * @param level    the working level of a gain for the class
     * @param blessing true on the way of the blessing (its two potions go into the field too)
     */
    public static int perKit(String field, ToIntFunction<String> level, boolean blessing) {
        if (field == null) {
            return 0;
        }
        return switch (field) {
            case POTION -> {
                int heal = level.applyAsInt(POTION);
                yield (heal >= 2 ? 3 : heal >= 1 ? 2 : 0) + Math.max(0, level.applyAsInt(FIRST_AID)) + (blessing ? 2 : 0);
            }
            case APPLE -> Math.max(0, level.applyAsInt(APPLE));
            case PEARL -> level.applyAsInt(PEARL) > 0 ? 2 : 0;
            case BARRICADE -> level.applyAsInt(BARRICADE) > 0 ? 3 : 0;
            default -> 0;
        };
    }

    /** The charges left: those of the kit less the ones used, never below 0. */
    public static int left(int perKit, int used) {
        return Math.max(0, perKit - Math.max(0, used));
    }

    /** True when a press may take a charge: some left, and the lock of the last one is over. */
    public static boolean mayFire(int left, long now, long lockedUntil) {
        return left > 0 && now >= lockedUntil;
    }
}
