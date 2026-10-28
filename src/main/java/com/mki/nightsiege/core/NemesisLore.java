package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The plain rules of a nemesis (update 2 "Fluch & Rache", Simon 28 October 2026: "wer einen
 * Verteidiger tötet"): its name, its title, its traits, how strong it comes back and when.
 */
public final class NemesisLore {

    /** What a nemesis has learned: one at the start, one more with every promotion. */
    public enum Trait {
        /** More life. */
        TOUGH("zaeh"),
        /** Hits harder. */
        WILD("wild"),
        /** Runs faster. */
        SWIFT("flink"),
        /** Arrows and bolts do half. */
        ARMORED("gepanzert"),
        /** Fire does nothing. */
        FIREPROOF("feuerfest"),
        /** Its first victim's class does less to it. */
        CLASS_FOE("klassenfeind"),
        /** Heals from the damage it deals. */
        LEECH("blutsauger");

        private final String id;

        Trait(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "nightsiege.nemesis.trait." + id;
        }

        public static Trait byId(String id) {
            if (id == null) {
                return null;
            }
            String want = id.trim().toLowerCase(Locale.ROOT);
            for (Trait t : values()) {
                if (t.id.equals(want) || t.name().toLowerCase(Locale.ROOT).equals(want)) {
                    return t;
                }
            }
            return null;
        }
    }

    /** Life, damage and speed of the traits. */
    public static final double TOUGH_HEALTH = 1.3;
    public static final double WILD_DAMAGE = 1.2;
    public static final double SWIFT_SPEED = 1.2;
    public static final double ARMORED_SHOTS = 0.5;
    public static final double CLASS_FOE_SHARE = 0.6;
    public static final double LEECH_SHARE = 0.25;

    private static final String[] FRONT = {
            "Moder", "Grab", "Knochen", "Blut", "Aschen", "Nacht", "Schatten", "Eisen",
            "Gift", "Dorn", "Krähen", "Sumpf", "Frost", "Rost", "Schädel", "Asche", "Galle", "Wurm"
    };
    private static final String[] BACK = {
            "klaue", "zahn", "fresser", "brecher", "reißer", "schlund", "kralle", "faust",
            "biss", "fang", "hauer", "beißer", "schlitzer", "würger", "schinder", "auge"
    };
    /** The title by level (index = level - 1); keys nightsiege.nemesis.title.N. */
    public static final int TITLES = 5;

    private NemesisLore() {
    }

    /** A name of its own ("Moderklaue") none of the others carries. */
    public static String name(Random random, Collection<String> taken) {
        for (int attempt = 0; attempt < 40; attempt++) {
            String front = FRONT[random.nextInt(FRONT.length)];
            String back = BACK[random.nextInt(BACK.length)];
            String name = front + back;
            if (taken == null || !taken.contains(name)) {
                return name;
            }
        }
        return FRONT[random.nextInt(FRONT.length)] + BACK[random.nextInt(BACK.length)] + " " + (taken == null ? 2 : taken.size() + 1);
    }

    /** The key of its title at this level ("der Schlächter"). */
    public static String titleKey(int level) {
        return "nightsiege.nemesis.title." + Math.max(1, Math.min(TITLES, level));
    }

    /** A trait it has not got yet (null when it has them all). */
    public static Trait newTrait(Collection<String> owned, Random random) {
        List<Trait> open = new ArrayList<>();
        for (Trait t : Trait.values()) {
            if (owned == null || !owned.contains(t.id())) {
                open.add(t);
            }
        }
        return open.isEmpty() ? null : open.get(random.nextInt(open.size()));
    }

    public static boolean has(Collection<String> traits, Trait trait) {
        return traits != null && traits.contains(trait.id());
    }

    /** Its life against the plain monster: times the base, plus per level, times "zäh". */
    public static double healthFactor(SiegeConfig cfg, int level, Collection<String> traits) {
        double f = cfg.nemesisHealth + Math.max(0, level - 1) * cfg.nemesisHealthPerLevel;
        return has(traits, Trait.TOUGH) ? f * TOUGH_HEALTH : f;
    }

    /** Its damage against the plain monster: times the base, plus per level, times "wild". */
    public static double damageFactor(SiegeConfig cfg, int level, Collection<String> traits) {
        double f = cfg.nemesisDamage + Math.max(0, level - 1) * cfg.nemesisDamagePerLevel;
        return has(traits, Trait.WILD) ? f * WILD_DAMAGE : f;
    }

    public static double speedFactor(Collection<String> traits) {
        return has(traits, Trait.SWIFT) ? SWIFT_SPEED : 1.0;
    }

    /**
     * Whether a lurking nemesis comes back tonight: never in the night it fled, always after two
     * nights away, otherwise with the chance from the config.
     */
    public static boolean returns(int nightsAway, double chance, double roll) {
        if (nightsAway <= 0) {
            return false;
        }
        return nightsAway >= 3 || roll < chance;
    }
}
