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

    // ------------------------------------------------------------------
    // the tanks' mace (Simon 05.11.: "die Tanks könnten … den Mace als Waffe haben, der viel Schaden
    // macht, aber nicht schnell angreifen kann")
    // ------------------------------------------------------------------

    /** A hit of the shield fighter's and the bulwark's mace against a kit sword hit of the same level. */
    public static final double MACE_DAMAGE_FACTOR = 2.5;
    /** Swings a second: the kit sword (vanilla) and the tanks' mace; 2.5 x 0.8 / 1.6 = 1.25 times the damage a second. */
    public static final double SWORD_SPEED = 1.6;
    public static final double MACE_SPEED = 0.8;
    /** A player's own attack damage and attack speed, before the weapon (vanilla attributes). */
    public static final double PLAYER_DAMAGE = 1.0;
    public static final double PLAYER_SPEED = 4.0;

    /** What Sharpness adds to a melee hit (vanilla: 1 at level I and half a point for every level above). */
    public static double sharpnessBonus(int level) {
        return level <= 0 ? 0.0 : 0.5 * level + 0.5;
    }

    /**
     * The attack damage modifier of the kit sword of a material: vanilla's 3 plus the material's bonus
     * (iron 2, diamond 3, netherite 4), from level 20 one point more. The kit reads the real sword's
     * numbers; these are for the table and the tests.
     */
    public static double swordDamageModifier(Material material) {
        return switch (material) {
            case IRON -> 5.0;
            case DIAMOND -> 6.0;
            case NETHERITE -> 7.0;
            case INFERNITE -> 8.0;
        };
    }

    /**
     * The attack damage modifier of the tanks' mace (it replaces the vanilla mace's own): a full hit
     * deals {@link #MACE_DAMAGE_FACTOR} times a full hit of the kit sword. {@code swordDamage} is the
     * sword's attack damage with the player's own point (6 for iron, 9 for the netherite of level 20);
     * both weapons carry the same Sharpness, which the mace adds on top as on any weapon.
     */
    public static double maceDamageModifier(double swordDamage, int sharpness) {
        double sharp = sharpnessBonus(sharpness);
        return MACE_DAMAGE_FACTOR * (swordDamage + sharp) - sharp - PLAYER_DAMAGE;
    }

    /** The attack speed modifier of the tanks' mace (it replaces the vanilla mace's -3.4): 0.8 swings a second. */
    public static double maceSpeedModifier() {
        return MACE_SPEED - PLAYER_SPEED;
    }

    /** A full hit of the kit sword at a level with this Sharpness (vanilla numbers, see {@link #swordDamageModifier}). */
    public static double swordHit(int level, int sharpness) {
        return PLAYER_DAMAGE + swordDamageModifier(weapon(level)) + sharpnessBonus(sharpness);
    }

    /** A full hit of the tanks' mace at a level with this Sharpness. */
    public static double maceHit(int level, int sharpness) {
        double sword = PLAYER_DAMAGE + swordDamageModifier(weapon(level));
        return PLAYER_DAMAGE + maceDamageModifier(sword, sharpness) + sharpnessBonus(sharpness);
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
