package com.mki.nightsiege.core;

/**
 * The numbers of the night kit by siege level, kept free of Minecraft so they can
 * be tested. Armour follows the old test kits: iron and diamond mixed up to level
 * 5, diamond to 10, netherite to 19, infernite from 20 on.
 */
public final class NightKit {

    /** Armour materials, in the order the kit climbs through them. */
    public enum Material {
        IRON, DIAMOND, NETHERITE, INFERNITE
    }

    private NightKit() {
    }

    /** The material of one armour piece (0 helmet, 1 chest, 2 legs, 3 boots). */
    public static Material armor(int level, int piece) {
        if (level >= 20) {
            return Material.INFERNITE;
        }
        if (level >= 11) {
            return Material.NETHERITE;
        }
        if (level >= 6) {
            return Material.DIAMOND;
        }
        // Low levels: one more diamond piece per level, the rest iron.
        int diamonds = Math.min(4, Math.max(0, level - 1));
        return piece < diamonds ? Material.DIAMOND : Material.IRON;
    }

    /** The weapon material (iron to level 5). */
    public static Material weapon(int level) {
        if (level >= 20) {
            return Material.INFERNITE;
        }
        if (level >= 11) {
            return Material.NETHERITE;
        }
        return level >= 6 ? Material.DIAMOND : Material.IRON;
    }

    /** Protection on every armour piece: I up to 2, II up to 5, III up to 10, IV from 11. */
    public static int protection(int level) {
        return level >= 11 ? 4 : level >= 6 ? 3 : level >= 3 ? 2 : 1;
    }

    /** Sharpness of sword and axe (and power of the bow): I .. V. */
    public static int sharpness(int level) {
        if (level >= 14) {
            return 5;
        }
        if (level >= 10) {
            return 4;
        }
        if (level >= 7) {
            return 3;
        }
        return level >= 4 ? 2 : 1;
    }

    /** Punch on the archer's kit bow from level 5. */
    public static int bowPunch(int level) {
        return level >= 5 ? 1 : 0;
    }

    /** Flame on the archer's kit bow from level 10. */
    public static int bowFlame(int level) {
        return level >= 10 ? 1 : 0;
    }

    /** Piercing on the kit crossbow: I from level 5, II from 10, III from 15. */
    public static int crossbowPiercing(int level) {
        return level >= 15 ? 3 : level >= 10 ? 2 : level >= 5 ? 1 : 0;
    }

    /** Quick charge of the kit crossbow (Simon 30.10.: needed earlier): I from the start, II from 7, III from 13. */
    public static int quickCharge(int level) {
        return level >= 13 ? 3 : level >= 7 ? 2 : 1;
    }

    /** A "+1" gain on top of a base enchantment, at most one above Minecraft's own top. */
    public static int plusOne(int base, int bonus, int vanillaMax) {
        return Math.min(vanillaMax + 1, base + Math.max(0, bonus));
    }

    /** Food: 0 bread, 1 cooked beef, 2 golden carrots, 3 golden carrots and golden apples. */
    public static int foodTier(int level, int provisionStage) {
        int base = level >= 11 ? 2 : level >= 6 ? 1 : 0;
        return Math.min(3, base + Math.max(0, provisionStage));
    }

    /** Golden apples from provisions beyond the top food tier (two per extra stage). */
    public static int provisionApples(int level, int provisionStage) {
        int base = level >= 11 ? 2 : level >= 6 ? 1 : 0;
        int over = base + Math.max(0, provisionStage) - 3;
        return over >= 0 ? 2 + 2 * over : 0;
    }

    /** Arrows of the archer: 64, and 16 more every five levels. */
    public static int archerArrows(int level) {
        return 64 + 16 * (Math.max(1, level) / 5);
    }

    /** Arrows of the engineer for the bow; the mechanics way gets turret arrows on top. */
    public static int engineerArrows(int level, boolean mechanics) {
        return 32 + (mechanics ? 64 + 16 * (Math.max(1, level) / 5) : 0);
    }
}
