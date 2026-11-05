package com.mki.nightsiege.core;

/**
 * The numbers and rules of the warrior rework (Simon 05.11.: "ja alles super bau das so"): the
 * Kampfgeist meter, the combo chain with its finisher and the F ability of each subclass. Pure logic
 * without Minecraft, so the tests can check it; the game side lives in {@code game.WarriorSpirit}
 * and {@code game.WarriorArts}.
 *
 * <p>Every number is a skill value ({@link SkillValues}, declared in {@code SkillValuesWarrior}): the
 * meter and the chain under {@code w_spirit}, each subclass's finisher ({@code fin_*}) and F ability
 * ({@code f_*}) under its subclass node (w_combo, bloodlust, heavy_blow, smite), so the skill text of
 * the subclass can show them and {@code skillTweaks} can change them.</p>
 */
public final class WarriorSpiritMath {

    /** The node of the meter and the chain. */
    public static final String NODE = "w_spirit";

    private WarriorSpiritMath() {
    }

    // ------------------------------------------------------------------ the subclasses

    /** True for the four warrior subclasses (each has a finisher and an F ability). */
    public static boolean isWarriorSub(String sub) {
        return SkillTree.SUB_BLADEMASTER.equals(sub) || SkillTree.SUB_BERSERKER.equals(sub)
                || SkillTree.SUB_AXE.equals(sub) || SkillTree.SUB_PALADIN.equals(sub);
    }

    /** The subclass node that holds the subclass's finisher and F numbers. */
    public static String nodeOf(String sub) {
        if (SkillTree.SUB_BERSERKER.equals(sub)) {
            return "bloodlust";
        }
        if (SkillTree.SUB_AXE.equals(sub)) {
            return "heavy_blow";
        }
        if (SkillTree.SUB_PALADIN.equals(sub)) {
            return "smite";
        }
        return "w_combo";
    }

    /** True for one of the four subclass nodes (their skill texts get the meter's numbers too). */
    public static boolean isSubNode(String node) {
        return "w_combo".equals(node) || "bloodlust".equals(node) || "heavy_blow".equals(node) || "smite".equals(node);
    }

    /** The finisher's id (statistics "finisher:…", texts "nightsiege.finisher.…"). */
    public static String finisherId(String sub) {
        if (SkillTree.SUB_BERSERKER.equals(sub)) {
            return "blutiger_hieb";
        }
        if (SkillTree.SUB_AXE.equals(sub)) {
            return "spalter";
        }
        if (SkillTree.SUB_PALADIN.equals(sub)) {
            return "lichtschlag";
        }
        return "klingenwirbel";
    }

    /** The F ability's id (statistics "faehigkeit:…", texts "nightsiege.warrior_f.…"). */
    public static String abilityId(String sub) {
        if (SkillTree.SUB_BERSERKER.equals(sub)) {
            return "raserei";
        }
        if (SkillTree.SUB_AXE.equals(sub)) {
            return "axtwurf";
        }
        if (SkillTree.SUB_PALADIN.equals(sub)) {
            return "weihegrund";
        }
        return "klingentanz";
    }

    /** The F field's symbol. */
    public static String abilityIcon(String sub) {
        if (SkillTree.SUB_BERSERKER.equals(sub)) {
            return "minecraft:redstone";
        }
        if (SkillTree.SUB_AXE.equals(sub)) {
            return "minecraft:iron_axe";
        }
        if (SkillTree.SUB_PALADIN.equals(sub)) {
            return "minecraft:glowstone_dust";
        }
        return "minecraft:golden_sword";
    }

    /** Ticks between two uses of the subclass's F ability (it starts when F is used). */
    public static int abilityCooldown(String sub) {
        double fallback = SkillTree.SUB_BERSERKER.equals(sub) ? 200 : SkillTree.SUB_PALADIN.equals(sub) ? 240 : 160;
        return (int) Math.max(0, Math.round(SkillValues.get(nodeOf(sub), "f_cd", fallback)));
    }

    // ------------------------------------------------------------------ the meter

    public static double max() {
        return Math.max(1.0, SkillValues.get(NODE, "max", 100.0));
    }

    public static double gainHit() {
        return SkillValues.get(NODE, "hit", 4.0);
    }

    public static double gainKill() {
        return SkillValues.get(NODE, "kill", 10.0);
    }

    public static double gainHurt() {
        return SkillValues.get(NODE, "hurt", 6.0);
    }

    /** Ticks without fighting before the meter starts to fall. */
    public static long idleTicks() {
        return Math.max(0L, Math.round(SkillValues.get(NODE, "idle", 100.0)));
    }

    /** What the meter loses per second once it falls. */
    public static double decayPerSecond() {
        return Math.max(0.0, SkillValues.get(NODE, "decay", 5.0));
    }

    /** From this on the bar glows and the F field lights up. */
    public static double glowAt() {
        return SkillValues.get(NODE, "glow", 50.0);
    }

    /** What F costs. */
    public static double abilityCost() {
        return Math.max(0.0, SkillValues.get(NODE, "cost", 50.0));
    }

    /** The meter after a gain, between 0 and the top. */
    public static double add(double value, double gain) {
        return Math.max(0.0, Math.min(max(), value + gain));
    }

    /** The meter one tick later: unchanged while the fight is recent, then decay/20 less a tick. */
    public static double decayTick(double value, long ticksSinceFight) {
        if (value <= 0.0 || ticksSinceFight <= idleTicks()) {
            return Math.max(0.0, value);
        }
        return Math.max(0.0, value - decayPerSecond() / 20.0);
    }

    /** True when F may be used with this much Kampfgeist. */
    public static boolean canUseAbility(double value) {
        return value + 1.0e-6 >= abilityCost();
    }

    /** True when the bar glows (and the F field lights up, when it is ready). */
    public static boolean glows(double value) {
        return value + 1.0e-6 >= glowAt();
    }

    // ------------------------------------------------------------------ the chain

    /** Hits of a chain: the last one is the finisher. */
    public static int chainHits() {
        return (int) Math.max(1, Math.round(SkillValues.get(NODE, "chain", 4.0)));
    }

    /** Ticks without a hit after which a chain breaks. */
    public static long chainWindow() {
        return Math.max(1L, Math.round(SkillValues.get(NODE, "window", 40.0)));
    }

    /**
     * The least attack charge (0–1, Minecraft's attack strength) a swing needs to count for the meter
     * and the chain, so clicking fast without waiting fills nothing (a guard against click spam).
     */
    public static double minCharge() {
        return SkillValues.get(NODE, "charge", 0.8);
    }

    /** The chain's count after a hit at {@code now}; the hit before it was at {@code lastHit}. */
    public static int chainAfterHit(int chain, long lastHit, long now) {
        if (chain <= 0 || now - lastHit > chainWindow() || chain >= chainHits()) {
            chain = 0;
        }
        return chain + 1;
    }

    /** The chain's count at {@code now} without a new hit (0 once it broke). */
    public static int chainNow(int chain, long lastHit, long now) {
        return chain > 0 && now - lastHit <= chainWindow() ? chain : 0;
    }

    /** True when this count of the chain is its finisher. */
    public static boolean isFinisher(int chain) {
        return chain >= chainHits();
    }

    /** A finisher at a full meter is empowered (double effect, costs {@link #empowerCost()}). */
    public static boolean empowers(double value) {
        return value + 1.0e-6 >= max();
    }

    public static double empowerCost() {
        return Math.max(0.0, SkillValues.get(NODE, "empcost", 30.0));
    }

    /** The factor of an empowered finisher (Simon: double effect). */
    public static double empowerFactor() {
        return SkillValues.get(NODE, "empfactor", 2.0);
    }

    // ------------------------------------------------------------------ the finishers

    /**
     * Blutiger Hieb: the extra damage share of the hit, +fin_bonus plus +fin_perlost for every full
     * tenth of the life that is missing ({@code life} is the share of life left, 0–1).
     */
    public static double bloodyBonus(double life) {
        double missing = 1.0 - Math.max(0.0, Math.min(1.0, life));
        int tenths = (int) Math.floor(missing * 10.0 + 1.0e-6);
        return SkillValues.get("bloodlust", "fin_bonus", 0.5) + SkillValues.get("bloodlust", "fin_perlost", 0.1) * tenths;
    }

    /** Spalter: the share of armour the target loses (at most 95 %). */
    public static double cleaveArmour(double factor) {
        return Math.max(0.0, Math.min(0.95, SkillValues.get("heavy_blow", "fin_armor", 0.3) * factor));
    }
}
