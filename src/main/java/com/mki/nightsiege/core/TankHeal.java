package com.mki.nightsiege.core;

/**
 * Tor 4.2 (Simon 08.11.: "die zwei klassen könnten eine fähigkeit bekommen zum heilen statt tränken allerdings nur
 * für selbstheilung"): the shield fighter and the bulwark heal themselves on key Y, never anyone else.
 *
 * <p>The numbers replace the healers' two kit potions (Strong Healing, 8 health each = 16 a night): a night lasts
 * 4 to 10 minutes, about 6, so 16 / 6 = 2.7 health a minute. 20 % of 20 health every 90 s is 4 / 1.5 = 2.7 a
 * minute, the same; with more hearts it grows with them. The heal comes at once (a potion too), nothing breaks it
 * off: a tank is always hit, an interrupted heal would never land. It never heals more than is missing.
 * Server tweaks: {@code skillTweaks.tankheal.values.share / cd}.</p>
 */
public final class TankHeal {

    /** The ability id on the bar and in {@link AbilityKeys}. */
    public static final String ID = "tankheal";

    private TankHeal() {
    }

    /** The two tanks (way of the shield). */
    public static boolean isTank(String subclass) {
        return SkillTree.SUB_SHIELDFIGHTER.equals(subclass) || SkillTree.SUB_BULWARK.equals(subclass);
    }

    /** The share of the max health one heal gives (0.20). */
    public static double share() {
        return Math.max(0.0, Math.min(1.0, SkillValues.get(ID, "share", 0.20)));
    }

    /** The pause in ticks (1800 = 90 s). */
    public static int cooldownTicks() {
        return (int) Math.max(20, Math.round(SkillValues.get(ID, "cd", 1800.0)));
    }

    /** What one heal gives: the share of the max health, never more than is missing. */
    public static float amount(float maxHealth, float health) {
        if (maxHealth <= 0f || health <= 0f) {
            return 0f;
        }
        return (float) Math.min(maxHealth * share(), Math.max(0.0, maxHealth - health));
    }
}
