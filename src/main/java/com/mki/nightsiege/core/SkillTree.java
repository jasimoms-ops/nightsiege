package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill catalogue.
 *
 * <p>Four player classes and one shared core tree. Each class has a trunk and
 * four paths behind it, each ending in an ultimate; once a player puts a point
 * into one path, the other three are closed for good.</p>
 */
public final class SkillTree {

    // Archer
    public static final String PATH_SWIFT = "swift";
    public static final String PATH_HEAVY = "heavy";
    public static final String PATH_CROSSBOW = "crossbow";
    public static final String PATH_HUNTER = "hunter";
    // Warrior: two ways, each with two subclasses
    public static final String WAY_BLADE = "edge";
    public static final String WAY_MIGHT = "might";
    public static final String SUB_BLADEMASTER = "blademaster";
    public static final String SUB_BERSERKER = "berserker";
    public static final String SUB_AXE = "axe";
    public static final String SUB_PALADIN = "paladin";
    // Guardian
    public static final String PATH_BULWARK = "bulwark";
    public static final String PATH_MEDIC = "medic";
    public static final String PATH_SHIELD = "shield";
    // Engineer
    public static final String PATH_DEMOLITION = "demolition";
    public static final String PATH_GUNNER = "gunner";
    public static final String PATH_ALCHEMIST = "alchemist";
    public static final String PATH_ELECTRICIAN = "electrician";

    /** Nodes that push one trap above its own three tiers. */
    private static final Map<TrapKind, String> TRAP_BOOSTS = Map.of(
            TrapKind.TURRET, "turret_tuning",
            TrapKind.POISON, "toxic_brew",
            TrapKind.TESLA, "capacitor");

    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillTree() {
        this(defaultNodes());
    }

    public SkillTree(List<SkillNode> list) {
        for (SkillNode node : list) {
            if (node != null && node.id() != null && !node.id().isBlank()) {
                nodes.put(node.id(), node);
            }
        }
    }

    public static List<SkillNode> defaultNodes() {
        List<SkillNode> list = new ArrayList<>();
        // Every class: a trunk, then four paths of three nodes, the last an ultimate.
        // A point in one path closes the other three for good.

        // ---------------- Archer ----------------
        add(list, "archer_focus", SkillClass.ARCHER, "", SkillEffect.ARROW_DAMAGE, 3, 2, 1, 0.5, null, 0, "minecraft:bow", 0);
        // Swift: short draws hit like full ones, a second arrow, then a storm.
        add(list, "swift_draw", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DRAW_ASSIST, 4, 3, 2, 0.2, "archer_focus", 1, "minecraft:feather", 1);
        add(list, "double_shot", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DOUBLE_SHOT, 1, 15, 0, 1.0, "swift_draw", 4, "minecraft:arrow", 2);
        add(list, "arrow_storm", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.ARROW_STORM, 3, 25, 10, 1.0, "double_shot", 1, "minecraft:blaze_rod", 3);
        // Heavy: slow bow, hard arrows, a crushing shot.
        add(list, "heavy_draw", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE, 5, 3, 2, 1.0, "archer_focus", 1, "minecraft:iron_ingot", 1);
        add(list, "piercing", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE, 4, 5, 3, 1.5, "heavy_draw", 4, "minecraft:spectral_arrow", 2);
        add(list, "power_shot", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.POWER_SHOT, 3, 25, 10, 1.0, "piercing", 4, "minecraft:dragon_breath", 3);
        // Crossbow: heavy bolts that burst on impact, then a fan of bolts.
        add(list, "heavy_bolts", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.CROSSBOW_DAMAGE, 4, 3, 2, 2.0, "archer_focus", 1, "minecraft:crossbow", 1);
        add(list, "shrapnel", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.SHRAPNEL, 4, 5, 3, 0.2, "heavy_bolts", 4, "minecraft:firework_star", 2);
        add(list, "bolt_volley", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.BOLT_VOLLEY, 3, 25, 10, 1.0, "shrapnel", 4, "minecraft:firework_rocket", 3);
        // Hunter: marks prey, moves fast, marks a whole pack.
        add(list, "tracker", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.MARK, 4, 3, 2, 0.15, "archer_focus", 1, "minecraft:spyglass", 1);
        add(list, "fleet_foot", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.MOVEMENT_SPEED, 4, 5, 3, 0.008, "tracker", 4, "minecraft:rabbit_foot", 2);
        add(list, "hunt", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.HUNTERS_MARK, 3, 25, 10, 1.0, "fleet_foot", 4, "minecraft:ender_eye", 3);

        // ---------------- Warrior ----------------
        // Trunk, then one of two ways (blade or might). Every tier of a way is a
        // choice group: one node per tier. Cross nodes (from the other way) take
        // the place of an own node and close some later ones.
        warrior(list);

        // ---------------- Guardian ----------------
        add(list, "vitality", SkillClass.GUARDIAN, "", SkillEffect.MAX_HEALTH, 4, 2, 1, 2.0, null, 0, "minecraft:golden_apple", 0);
        add(list, "toughness", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.ARMOR, 4, 3, 2, 2.0, "vitality", 1, "minecraft:iron_chestplate", 1);
        add(list, "steadfast", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.KNOCKBACK_RESIST, 4, 5, 3, 0.15, "toughness", 4, "minecraft:netherite_ingot", 2);
        add(list, "taunt", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.TAUNT, 3, 25, 10, 1.0, "steadfast", 4, "minecraft:goat_horn", 3);
        add(list, "field_dressing", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.HEAL_AURA, 4, 3, 2, 0.75, "vitality", 1, "minecraft:glistering_melon_slice", 1);
        add(list, "guardian_angel", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.GUARDIAN_ANGEL, 3, 5, 3, 1.0, "field_dressing", 4, "minecraft:totem_of_undying", 2);
        add(list, "mass_heal", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.MASS_HEAL, 3, 25, 10, 1.0, "guardian_angel", 1, "minecraft:enchanted_golden_apple", 3);
        add(list, "iron_guard", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.ARMOR, 4, 3, 2, 1.5, "vitality", 1, "minecraft:shield", 1);
        add(list, "riposte", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.COUNTER, 4, 5, 3, 2.5, "iron_guard", 4, "minecraft:iron_ingot", 2);
        add(list, "shield_charge", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.SHIELD_CHARGE, 3, 25, 10, 1.0, "riposte", 4, "minecraft:anvil", 3);

        // ---------------- Engineer ----------------
        // Trunk: the two basic traps everyone builds; then one speciality.
        int t = TrapKind.NODE_LEVELS;
        add(list, TrapKind.SPIKE.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP, t, 3, 2, 1.0, null, 0, "nightsiege:spike_trap", 0);
        add(list, TrapKind.SNARE.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP, t, 5, 3, 1.0, TrapKind.SPIKE.id(), 2, "nightsiege:snare_trap", 1);
        // Demolition: mine, flames, bombardment.
        add(list, TrapKind.MINE.id(), SkillClass.ENGINEER, PATH_DEMOLITION, SkillEffect.TRAP, t, 8, 4, 1.0, TrapKind.SNARE.id(), 2, "nightsiege:blast_mine", 2);
        add(list, TrapKind.FLAME.id(), SkillClass.ENGINEER, PATH_DEMOLITION, SkillEffect.TRAP, t, 12, 6, 1.0, TrapKind.MINE.id(), 2, "nightsiege:flame_trap", 3);
        add(list, "bombardment", SkillClass.ENGINEER, PATH_DEMOLITION, SkillEffect.BOMBARDMENT, 3, 25, 10, 1.0, TrapKind.FLAME.id(), 2, "minecraft:tnt", 4);
        // Gunner: the arrow turret, tuned, then overcharged.
        add(list, TrapKind.TURRET.id(), SkillClass.ENGINEER, PATH_GUNNER, SkillEffect.TRAP, t, 8, 4, 1.0, TrapKind.SNARE.id(), 2, "nightsiege:arrow_turret", 2);
        add(list, "turret_tuning", SkillClass.ENGINEER, PATH_GUNNER, SkillEffect.TRAP_BOOST, 2, 15, 10, 1.0, TrapKind.TURRET.id(), 3, "minecraft:redstone", 3);
        add(list, "overcharge", SkillClass.ENGINEER, PATH_GUNNER, SkillEffect.OVERCHARGE, 3, 25, 10, 1.0, "turret_tuning", 1, "minecraft:redstone_block", 4);
        // Alchemist: poison trap, stronger brew, a cloud at will.
        add(list, TrapKind.POISON.id(), SkillClass.ENGINEER, PATH_ALCHEMIST, SkillEffect.TRAP, t, 8, 4, 1.0, TrapKind.SNARE.id(), 2, "nightsiege:poison_trap", 2);
        add(list, "toxic_brew", SkillClass.ENGINEER, PATH_ALCHEMIST, SkillEffect.TRAP_BOOST, 2, 15, 10, 1.0, TrapKind.POISON.id(), 3, "minecraft:fermented_spider_eye", 3);
        add(list, "toxic_cloud", SkillClass.ENGINEER, PATH_ALCHEMIST, SkillEffect.TOXIC_CLOUD, 3, 25, 10, 1.0, "toxic_brew", 1, "minecraft:lingering_potion", 4);
        // Electrician: tesla coil, capacitors, a storm.
        add(list, TrapKind.TESLA.id(), SkillClass.ENGINEER, PATH_ELECTRICIAN, SkillEffect.TRAP, t, 10, 5, 1.0, TrapKind.SNARE.id(), 2, "nightsiege:tesla_coil", 2);
        add(list, "capacitor", SkillClass.ENGINEER, PATH_ELECTRICIAN, SkillEffect.TRAP_BOOST, 2, 15, 10, 1.0, TrapKind.TESLA.id(), 3, "minecraft:copper_block", 3);
        add(list, "thunderstorm", SkillClass.ENGINEER, PATH_ELECTRICIAN, SkillEffect.THUNDERSTORM, 3, 25, 10, 1.0, "capacitor", 1, "minecraft:lightning_rod", 4);

        // ---------------- Core: shared, paid by donations ----------------
        list.add(new SkillNode("watchtower", SkillClass.CORE, "", SkillEffect.SPAWN_DISTANCE,
                8, 3, 1, 1.0, null, 0, "minecraft:spyglass", 0));
        list.add(new SkillNode("fortified", SkillClass.CORE, "", SkillEffect.WAVE_DELAY,
                8, 4, 2, 0.5, "watchtower", 3, "minecraft:obsidian", 1));
        return list;
    }

    // ------------------------------------------------------------------
    // warrior
    // ------------------------------------------------------------------

    /** The subclass node of each way and the subclass it stands for. */
    private static final Map<String, String> SUBCLASS_NODES = Map.of(
            "keen_edge", SUB_BLADEMASTER,
            "bloodlust", SUB_BERSERKER,
            "heavy_blow", SUB_AXE,
            "smite", SUB_PALADIN);

    private static void warrior(List<SkillNode> list) {
        SkillClass c = SkillClass.WARRIOR;
        // Trunk: drill, then stamina or footwork.
        node(list, "warrior_drill", c, "", SkillEffect.ATTACK_DAMAGE, 3, 2, 1, 0.5, null, "", "minecraft:iron_sword", 0, 3);
        node(list, "w_endurance", c, "", SkillEffect.MAX_HEALTH, 3, 3, 2, 2.0, "warrior_drill", "w_stem", "minecraft:cooked_beef", 1, 2);
        node(list, "w_footwork", c, "", SkillEffect.MOVEMENT_SPEED, 3, 3, 2, 0.005, "warrior_drill", "w_stem", "minecraft:leather_boots", 1, 4);

        // ----- Way of the blade (swords)
        String e = WAY_BLADE;
        node(list, "w_bladedance", c, e, SkillEffect.WEAPON_SPEED, 3, 3, 2, 0.15, "warrior_drill", "e1", "minecraft:feather", 2, 1);
        node(list, "champion_bane", c, e, SkillEffect.CHAMPION_BANE, 3, 3, 2, 0.25, "warrior_drill", "", "minecraft:golden_sword", 2, 3);
        node(list, "w_fencer", c, e, SkillEffect.KNOCKBACK_RESIST, 3, 3, 2, 0.15, "warrior_drill", "e1", "minecraft:chainmail_chestplate", 2, 5);
        node(list, "keen_edge", c, e, SkillEffect.CRIT_CHANCE, 4, 4, 2, 0.07, "champion_bane", "e_sub", "minecraft:flint", 3, 1);
        node(list, "bloodlust", c, e, SkillEffect.RAGE, 4, 4, 2, 0.15, "champion_bane", "e_sub", "minecraft:redstone", 3, 5);
        node(list, "w_combo", c, e, SkillEffect.COMBO, 1, 10, 0, 0.08, "keen_edge:2", "e3", "minecraft:sugar", 4, 0);
        node(list, "w_precision", c, e, SkillEffect.CRIT_POWER, 1, 10, 0, 0.5, "keen_edge:2", "e3", "minecraft:target", 4, 2);
        node(list, "blood_drinker", c, e, SkillEffect.LIFESTEAL, 1, 10, 0, 0.12, "bloodlust:2", "e3", "minecraft:ghast_tear", 4, 4);
        node(list, "w_warcry", c, e, SkillEffect.WARCRY, 1, 10, 0, 1.0, "bloodlust:2", "e3", "minecraft:goat_horn", 4, 6);
        String e3 = "w_combo|w_precision|blood_drinker|w_warcry";
        node(list, "w_parry", c, e, SkillEffect.PARRY, 1, 12, 0, 0.15, e3, "e4", "minecraft:iron_sword", 5, 1);
        node(list, "w_frenzy", c, e, SkillEffect.FRENZY, 1, 12, 0, 1.0, e3, "e4", "minecraft:blaze_powder", 5, 3);
        node(list, "e_cross_heavy", c, e, SkillEffect.HEAVY_BLOW, 1, 12, 0, 3.0, e3, "e4", "minecraft:iron_axe", 5, 5,
                "w_bladefrenzy", "w_bladestorm");
        node(list, "w_bladefrenzy", c, e, SkillEffect.CRIT_RAGE, 1, 15, 0, 1.0, "w_combo|w_precision,w_frenzy", "e5", "minecraft:netherite_sword", 6, 2);
        node(list, "w_bloodblade", c, e, SkillEffect.PARRY_HEAL, 1, 15, 0, 4.0, "blood_drinker|w_warcry,w_parry", "e5", "minecraft:red_dye", 6, 4);
        String e4 = "w_parry|w_frenzy|e_cross_heavy";
        node(list, "w_evasion", c, e, SkillEffect.DODGE, 1, 18, 0, 0.10, e4, "e6", "minecraft:phantom_membrane", 7, 1);
        node(list, "w_bloodprice", c, e, SkillEffect.BLOOD_PRICE, 1, 18, 0, 0.3, e4, "e6", "minecraft:redstone_block", 7, 3);
        node(list, "e_cross_smite", c, e, SkillEffect.SMITE, 1, 18, 0, 0.35, e4, "e6", "minecraft:glowstone_dust", 7, 5, "execute");
        String e6 = "w_evasion|w_bloodprice|e_cross_smite";
        node(list, "execute", c, e, SkillEffect.EXECUTE, 3, 20, 10, 1.0, e6, "e_ult", "minecraft:diamond_sword", 8, 2);
        node(list, "whirlwind", c, e, SkillEffect.WHIRLWIND, 3, 20, 10, 1.0, e6, "e_ult", "minecraft:breeze_rod", 8, 4);
        node(list, "w_chain_execute", c, e, SkillEffect.CHAIN_EXECUTE, 1, 25, 0, 1.0, "execute,keen_edge", "e_mastery", "minecraft:wither_skeleton_skull", 9, 0);
        node(list, "w_bloody_execute", c, e, SkillEffect.BLOODY_EXECUTE, 1, 25, 0, 1.0, "execute,bloodlust", "e_mastery", "minecraft:nether_wart", 9, 2);
        node(list, "w_bladestorm", c, e, SkillEffect.BLADESTORM, 1, 25, 0, 1.0, "whirlwind,keen_edge", "e_mastery", "minecraft:wind_charge", 9, 4);
        node(list, "w_bloodstorm", c, e, SkillEffect.BLOODSTORM, 1, 25, 0, 1.0, "whirlwind,bloodlust", "e_mastery", "minecraft:fermented_spider_eye", 9, 6);
        node(list, "w_glass_cannon", c, e, SkillEffect.GLASS_CANNON, 1, 30, 0, 0.3, "execute|whirlwind", "e_key", "minecraft:glass", 10, 2);
        node(list, "w_unstoppable", c, e, SkillEffect.UNSTOPPABLE, 1, 30, 0, 1.0, "execute|whirlwind", "e_key", "minecraft:obsidian", 10, 4);

        // ----- Way of might (axes and maces)
        String m = WAY_MIGHT;
        node(list, "w_ironskin", c, m, SkillEffect.ARMOR, 3, 3, 2, 1.5, "warrior_drill", "m1", "minecraft:iron_chestplate", 2, 1);
        node(list, "sunder", c, m, SkillEffect.SUNDER, 3, 3, 2, 0.08, "warrior_drill", "", "minecraft:iron_axe", 2, 3);
        node(list, "w_heavyswing", c, m, SkillEffect.WEAPON_SPEED, 3, 3, 2, 0.2, "warrior_drill", "m1", "minecraft:piston", 2, 5);
        node(list, "heavy_blow", c, m, SkillEffect.HEAVY_BLOW, 4, 4, 2, 1.0, "sunder", "m_sub", "minecraft:diamond_axe", 3, 1);
        node(list, "smite", c, m, SkillEffect.SMITE, 4, 4, 2, 0.12, "sunder", "m_sub", "minecraft:golden_sword", 3, 5);
        node(list, "w_cleave", c, m, SkillEffect.CLEAVE, 1, 10, 0, 0.4, "heavy_blow:2", "m3", "minecraft:stone_axe", 4, 0);
        node(list, "w_headhunter", c, m, SkillEffect.HEADHUNTER, 1, 10, 0, 0.4, "heavy_blow:2", "m3", "minecraft:skeleton_skull", 4, 2);
        node(list, "holy_vigor", c, m, SkillEffect.LIFESTEAL, 1, 10, 0, 0.10, "smite:2", "m3", "minecraft:glow_berries", 4, 4);
        node(list, "w_vow", c, m, SkillEffect.VOW, 1, 10, 0, 1.0, "smite:2", "m3", "minecraft:bell", 4, 6);
        String m3 = "w_cleave|w_headhunter|holy_vigor|w_vow";
        node(list, "w_momentum", c, m, SkillEffect.MOMENTUM, 1, 12, 0, 1.0, m3, "m4", "minecraft:rabbit_foot", 5, 1);
        node(list, "w_blessing", c, m, SkillEffect.HEAL_AURA, 1, 12, 0, 1.0, m3, "m4", "minecraft:glistering_melon_slice", 5, 3);
        node(list, "m_cross_keen", c, m, SkillEffect.CRIT_CHANCE, 1, 12, 0, 0.2, m3, "m4", "minecraft:flint", 5, 5, "w_aftershock");
        node(list, "w_crusader", c, m, SkillEffect.CRUSADER, 1, 15, 0, 1.0, "w_cleave,w_blessing", "m5", "minecraft:golden_axe", 6, 1);
        node(list, "w_headsman", c, m, SkillEffect.HEADSMAN, 1, 15, 0, 4.0, "w_headhunter,m_cross_keen", "m5", "minecraft:netherite_axe", 6, 3);
        node(list, "w_holy_strike", c, m, SkillEffect.HOLY_STRIKE, 1, 15, 0, 1.0, "smite,m_cross_keen", "m5", "minecraft:blaze_rod", 6, 5);
        String m4 = "w_momentum|w_blessing|m_cross_keen";
        node(list, "w_earthshock", c, m, SkillEffect.EARTHSHOCK, 1, 18, 0, 0.6, m4, "m6", "minecraft:mace", 7, 1);
        node(list, "w_divine_shield", c, m, SkillEffect.DIVINE_SHIELD, 1, 18, 0, 1.0, m4, "m6", "minecraft:shield", 7, 3);
        node(list, "m_cross_rage", c, m, SkillEffect.RAGE, 1, 18, 0, 0.6, m4, "m6", "minecraft:redstone", 7, 5, "holy_light");
        String m6 = "w_earthshock|w_divine_shield|m_cross_rage";
        node(list, "ground_slam", c, m, SkillEffect.GROUND_SLAM, 3, 20, 10, 1.0, m6, "m_ult", "minecraft:heavy_core", 8, 2);
        node(list, "holy_light", c, m, SkillEffect.HOLY_LIGHT, 3, 20, 10, 1.0, m6, "m_ult", "minecraft:beacon", 8, 4);
        node(list, "w_aftershock", c, m, SkillEffect.AFTERSHOCK, 1, 25, 0, 1.0, "ground_slam,heavy_blow", "m_mastery", "minecraft:tnt", 9, 0);
        node(list, "w_crater", c, m, SkillEffect.CRATER, 1, 25, 0, 1.0, "ground_slam,smite", "m_mastery", "minecraft:cobweb", 9, 2);
        node(list, "w_sunfire", c, m, SkillEffect.SUNFIRE, 1, 25, 0, 1.0, "holy_light,smite", "m_mastery", "minecraft:fire_charge", 9, 4);
        node(list, "w_redemption", c, m, SkillEffect.REDEMPTION, 1, 25, 0, 1.0, "holy_light,w_blessing|w_vow|holy_vigor", "m_mastery", "minecraft:enchanted_golden_apple", 9, 6);
        node(list, "w_colossus", c, m, SkillEffect.COLOSSUS, 1, 30, 0, 1.0, "ground_slam|holy_light", "m_key", "minecraft:iron_block", 10, 2);
        node(list, "w_martyr", c, m, SkillEffect.MARTYR, 1, 30, 0, 1.0, "ground_slam|holy_light", "m_key", "minecraft:totem_of_undying", 10, 4);
    }

    private static void node(List<SkillNode> list, String id, SkillClass clazz, String path, SkillEffect effect,
                             int maxLevel, int baseCost, int costStep, double amount, String requires,
                             String group, String icon, int row, int col, String... locks) {
        list.add(new SkillNode(id, clazz, path, effect, maxLevel, baseCost, costStep, amount,
                requires, requires == null ? 0 : 1, icon, row, group, List.of(locks), col));
    }

    private static void add(List<SkillNode> list, String id, SkillClass clazz, String path, SkillEffect effect,
                            int maxLevel, int baseCost, int costStep, double amount,
                            String requires, int requiresLevel, String icon, int row) {
        list.add(new SkillNode(id, clazz, path, effect, maxLevel, baseCost, costStep, amount,
                requires, requiresLevel, icon, row));
    }

    /** The ultimate this player can fire, or null. */
    public SkillNode ultimateOf(PlayerSkills skills) {
        for (SkillNode node : nodes.values()) {
            if (node.effect().target() == SkillEffect.Target.ULTIMATE && skills.levelOf(node.id()) > 0
                    && node.clazz() == skills.clazz()) {
                return node;
            }
        }
        return null;
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }

    public List<SkillNode> all() {
        return new ArrayList<>(nodes.values());
    }

    public List<SkillNode> ofClass(SkillClass clazz) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.clazz() == clazz) {
                out.add(node);
            }
        }
        return out;
    }

    /**
     * Why a node cannot be taken right now, or null when it can.
     *
     * <p>Reasons: {@code class} wrong or unchosen class, {@code path} the other
     * path of this class is already taken, {@code locked} prerequisite missing,
     * {@code maxed}, {@code points}.</p>
     */
    public String blockedReason(String nodeId, PlayerSkills skills) {
        SkillNode node = get(nodeId);
        if (node == null) {
            return "unknown";
        }
        if (!node.clazz().isShared() && node.clazz() != skills.clazz()) {
            return skills.hasClass() ? "class" : "nochoice";
        }
        int level = skills.levelOf(nodeId);
        if (level >= node.maxLevel()) {
            return "maxed";
        }
        if (!node.onTrunk()) {
            String taken = skills.pathTaken(this);
            if (!taken.isEmpty() && !taken.equals(node.path())) {
                return "path";
            }
        }
        if (node.inGroup()) {
            for (SkillNode other : nodes.values()) {
                if (other != node && other.clazz() == node.clazz() && node.group().equals(other.group())
                        && skills.levelOf(other.id()) > 0) {
                    return "choice";
                }
            }
        }
        if (lockedOut(node, skills)) {
            return "lockedby";
        }
        if (!needsMet(node, skills)) {
            return "locked";
        }
        if (skills.points() < node.costFor(level)) {
            return "points";
        }
        return null;
    }

    /** True when a taken node closes this one, or this one would close a taken node. */
    public boolean lockedOut(SkillNode node, PlayerSkills skills) {
        for (String locked : node.locksList()) {
            if (skills.levelOf(locked) > 0) {
                return true;
            }
        }
        for (SkillNode other : nodes.values()) {
            if (other.clazz() == node.clazz() && skills.levelOf(other.id()) > 0
                    && other.locksList().contains(node.id())) {
                return true;
            }
        }
        return false;
    }

    /** Whether every requirement of a node is met. */
    public boolean needsMet(SkillNode node, PlayerSkills skills) {
        for (List<SkillNode.Need> any : node.needs()) {
            boolean met = false;
            for (SkillNode.Need need : any) {
                if (skills.levelOf(need.id()) >= need.level()) {
                    met = true;
                    break;
                }
            }
            if (!met) {
                return false;
            }
        }
        return true;
    }

    /** The warrior subclass a player has chosen (blademaster, berserker, axe, paladin), or empty. */
    public String subclassOf(PlayerSkills skills) {
        if (skills == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : SUBCLASS_NODES.entrySet()) {
            SkillNode node = nodes.get(entry.getKey());
            if (node != null && node.clazz() == skills.clazz() && skills.levelOf(entry.getKey()) > 0) {
                return entry.getValue();
            }
        }
        return "";
    }

    /** True when the player has learned this node. */
    public static boolean has(PlayerSkills skills, String id) {
        return skills != null && skills.levelOf(id) > 0;
    }

    public boolean canUnlock(String nodeId, PlayerSkills skills) {
        return blockedReason(nodeId, skills) == null;
    }

    /** Spends the points and raises the level. Returns false when not allowed. */
    public boolean unlock(String nodeId, PlayerSkills skills) {
        if (!canUnlock(nodeId, skills)) {
            return false;
        }
        SkillNode node = get(nodeId);
        int level = skills.levelOf(nodeId);
        skills.spend(node.costFor(level));
        skills.setLevel(nodeId, level + 1);
        return true;
    }

    /** Total bonus across all nodes of one effect. */
    public double totalBonus(SkillEffect effect, PlayerSkills skills) {
        double sum = 0.0;
        for (SkillNode node : nodes.values()) {
            if (node.effect() == effect) {
                sum += node.bonusAt(skills.levelOf(node.id()));
            }
        }
        return sum;
    }

    /** The tier a player has a trap at, or 0 when it is not theirs to use. */
    public int trapTier(TrapKind kind, PlayerSkills skills) {
        if (kind == null || skills == null || skills.clazz() != SkillClass.ENGINEER) {
            return 0;
        }
        int own = skills.levelOf(kind.id());
        if (own <= 0) {
            return 0;
        }
        String boost = TRAP_BOOSTS.get(kind);
        int extra = boost == null ? 0 : skills.levelOf(boost);
        return Math.min(TrapKind.MAX_TIER, own + extra);
    }
}
