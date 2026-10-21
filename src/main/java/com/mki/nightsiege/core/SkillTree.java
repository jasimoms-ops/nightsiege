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

    // Archer: two ways, each with two subclasses
    public static final String WAY_BOW = "bow";
    public static final String WAY_CROSSBOW = "xbow";
    public static final String SUB_SWIFT = "swift";
    public static final String SUB_HEAVY = "heavy";
    public static final String SUB_BOLTER = "bolter";
    public static final String SUB_TRAPPER = "trapper";
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
        // Trunk, then bow or crossbow. Marking and the hunt (the old hunter)
        // come up as extra choices in both ways.
        archer(list);

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
            "swift_draw", SUB_SWIFT,
            "heavy_draw", SUB_HEAVY,
            "shrapnel", SUB_BOLTER,
            "a_netbolt", SUB_TRAPPER,
            "keen_edge", SUB_BLADEMASTER,
            "bloodlust", SUB_BERSERKER,
            "heavy_blow", SUB_AXE,
            "smite", SUB_PALADIN);

    private static void archer(List<SkillNode> list) {
        SkillClass c = SkillClass.ARCHER;
        node(list, "archer_focus", c, "", SkillEffect.ARROW_DAMAGE, 3, 2, 1, 0.5, null, "", "minecraft:bow", 0, 3);
        node(list, "a_lightfoot", c, "", SkillEffect.MOVEMENT_SPEED, 3, 3, 2, 0.005, "archer_focus", "a_stem", "minecraft:leather_boots", 1, 2);
        node(list, "a_quiver", c, "", SkillEffect.QUIVER, 3, 3, 2, 0.15, "archer_focus", "a_stem", "minecraft:bundle", 1, 4);

        // ----- Way of the bow
        String b = WAY_BOW;
        node(list, "a_steady", c, b, SkillEffect.DRAW_ASSIST, 3, 3, 2, 0.1, "archer_focus", "b1", "minecraft:string", 2, 1);
        node(list, "a_pierce", c, b, SkillEffect.PIERCE, 2, 3, 2, 1.0, "archer_focus", "", "minecraft:spectral_arrow", 2, 3);
        node(list, "a_longshot", c, b, SkillEffect.LONGSHOT, 3, 3, 2, 0.1, "archer_focus", "b1", "minecraft:spyglass", 2, 5);
        node(list, "swift_draw", c, b, SkillEffect.DRAW_ASSIST, 4, 4, 2, 0.2, "a_pierce", "b_sub", "minecraft:feather", 3, 1);
        node(list, "heavy_draw", c, b, SkillEffect.ARROW_DAMAGE, 4, 4, 2, 1.0, "a_pierce", "b_sub", "minecraft:iron_ingot", 3, 5);
        node(list, "double_shot", c, b, SkillEffect.DOUBLE_SHOT, 1, 10, 0, 1.0, "swift_draw:2", "b3", "minecraft:arrow", 4, 0);
        node(list, "a_volley", c, b, SkillEffect.VOLLEY, 1, 10, 0, 1.0, "swift_draw:2", "b3", "minecraft:tipped_arrow", 4, 2);
        node(list, "piercing", c, b, SkillEffect.PIERCE, 1, 10, 0, 2.0, "heavy_draw:2", "b3", "minecraft:trident", 4, 4);
        node(list, "a_knockarrow", c, b, SkillEffect.ARROW_KNOCKBACK, 1, 10, 0, 1.0, "heavy_draw:2", "b3", "minecraft:piston", 4, 6);
        String b3 = "double_shot|a_volley|piercing|a_knockarrow";
        node(list, "a_fire", c, b, SkillEffect.FIRE_ARROWS, 1, 12, 0, 1.0, b3, "b4", "minecraft:blaze_powder", 5, 1);
        node(list, "a_poison", c, b, SkillEffect.POISON_ARROWS, 1, 12, 0, 1.0, b3, "b4", "minecraft:spider_eye", 5, 3);
        node(list, "b_mark", c, b, SkillEffect.MARK, 1, 12, 0, 0.25, b3, "b4", "minecraft:ender_eye", 5, 5);
        node(list, "a_firevolley", c, b, SkillEffect.FIRE_VOLLEY, 1, 15, 0, 1.0, "double_shot|a_volley,a_fire", "b5", "minecraft:fire_charge", 6, 1);
        node(list, "a_deathmark", c, b, SkillEffect.DEATH_MARK, 1, 15, 0, 0.3, "heavy_draw,b_mark", "b5", "minecraft:wither_skeleton_skull", 6, 3);
        node(list, "a_venomburst", c, b, SkillEffect.VENOM_BURST, 1, 15, 0, 1.0, "piercing|a_knockarrow,a_poison", "b5", "minecraft:fermented_spider_eye", 6, 5);
        String b4 = "a_fire|a_poison|b_mark";
        node(list, "a_eagle", c, b, SkillEffect.ARROW_CRIT, 1, 18, 0, 0.2, b4, "b6", "minecraft:feather", 7, 1);
        node(list, "a_retreat", c, b, SkillEffect.RETREAT, 1, 18, 0, 1.0, b4, "b6", "minecraft:rabbit_foot", 7, 3);
        node(list, "b_cross_shrapnel", c, b, SkillEffect.SHRAPNEL, 1, 18, 0, 0.3, b4, "b6", "minecraft:firework_star", 7, 5,
                "a_firevolley", "a_firestorm");
        String b6 = "a_eagle|a_retreat|b_cross_shrapnel";
        node(list, "arrow_storm", c, b, SkillEffect.ARROW_STORM, 3, 20, 10, 1.0, b6, "b_ult", "minecraft:blaze_rod", 8, 1);
        node(list, "power_shot", c, b, SkillEffect.POWER_SHOT, 3, 20, 10, 1.0, b6, "b_ult", "minecraft:dragon_breath", 8, 3);
        node(list, "b_hunt", c, b, SkillEffect.HUNTERS_MARK, 3, 20, 10, 1.0, b6, "b_ult", "minecraft:compass", 8, 5);
        node(list, "a_tempest", c, b, SkillEffect.TEMPEST, 1, 25, 0, 1.0, "arrow_storm,swift_draw", "b_mastery", "minecraft:wind_charge", 9, 0);
        node(list, "a_firestorm", c, b, SkillEffect.FIRESTORM, 1, 25, 0, 1.0, "arrow_storm,a_fire|a_firevolley", "b_mastery", "minecraft:magma_cream", 9, 2);
        node(list, "a_sniper", c, b, SkillEffect.SNIPER, 1, 25, 0, 1.0, "power_shot,heavy_draw", "b_mastery", "minecraft:crossbow", 9, 4);
        node(list, "b_pack", c, b, SkillEffect.PACK, 1, 25, 0, 1.0, "b_hunt,b_mark", "b_mastery", "minecraft:bone", 9, 6);
        node(list, "a_deadeye", c, b, SkillEffect.DEADEYE, 1, 30, 0, 1.0, "arrow_storm|power_shot|b_hunt", "b_key", "minecraft:target", 10, 2);
        node(list, "a_skirmisher", c, b, SkillEffect.SKIRMISHER, 1, 30, 0, 1.0, "arrow_storm|power_shot|b_hunt", "b_key", "minecraft:golden_boots", 10, 4);

        // ----- Way of the crossbow
        String x = WAY_CROSSBOW;
        node(list, "a_lightxbow", c, x, SkillEffect.XBOW_SPEED, 3, 3, 2, 0.006, "archer_focus", "x1", "minecraft:leather", 2, 1);
        node(list, "heavy_bolts", c, x, SkillEffect.CROSSBOW_DAMAGE, 3, 3, 2, 1.5, "archer_focus", "", "minecraft:crossbow", 2, 3);
        node(list, "a_steelbolts", c, x, SkillEffect.BOLT_SUNDER, 3, 3, 2, 0.06, "archer_focus", "x1", "minecraft:iron_nugget", 2, 5);
        node(list, "shrapnel", c, x, SkillEffect.SHRAPNEL, 4, 4, 2, 0.12, "heavy_bolts", "x_sub", "minecraft:firework_star", 3, 1);
        node(list, "a_netbolt", c, x, SkillEffect.NET_BOLT, 4, 4, 2, 1.0, "heavy_bolts", "x_sub", "minecraft:cobweb", 3, 5);
        node(list, "a_explosive", c, x, SkillEffect.EXPLOSIVE, 1, 10, 0, 4.0, "shrapnel:2", "x3", "minecraft:tnt", 4, 0);
        node(list, "a_multiload", c, x, SkillEffect.MULTI_BOLT, 1, 10, 0, 1.0, "shrapnel:2", "x3", "minecraft:arrow", 4, 2);
        node(list, "a_snarefield", c, x, SkillEffect.NET_FIELD, 1, 10, 0, 1.0, "a_netbolt:2", "x3", "minecraft:string", 4, 4);
        node(list, "a_shackle", c, x, SkillEffect.SHACKLE, 1, 10, 0, 1.0, "a_netbolt:2", "x3", "minecraft:slime_ball", 4, 6);
        String x3 = "a_explosive|a_multiload|a_snarefield|a_shackle";
        node(list, "a_punch", c, x, SkillEffect.PIERCE, 1, 12, 0, 2.0, x3, "x4", "minecraft:spectral_arrow", 5, 1);
        node(list, "a_tripwire", c, x, SkillEffect.TRIPWIRE, 1, 12, 0, 1.0, x3, "x4", "minecraft:tripwire_hook", 5, 3);
        node(list, "x_mark", c, x, SkillEffect.MARK, 1, 12, 0, 0.25, x3, "x4", "minecraft:ender_eye", 5, 5);
        node(list, "a_clusterbolt", c, x, SkillEffect.CLUSTER, 1, 15, 0, 1.0, "a_explosive|a_multiload,a_punch", "x5", "minecraft:firework_rocket", 6, 1);
        node(list, "a_hunternet", c, x, SkillEffect.HUNTER_NET, 1, 15, 0, 0.25, "a_snarefield|a_shackle,x_mark", "x5", "minecraft:lead", 6, 3);
        node(list, "a_trapmaster", c, x, SkillEffect.TRAPMASTER, 1, 15, 0, 4.0, "a_netbolt,a_tripwire", "x5", "minecraft:iron_bars", 6, 5);
        String x4 = "a_punch|a_tripwire|x_mark";
        node(list, "a_bracing", c, x, SkillEffect.BRACE, 1, 18, 0, 0.4, x4, "x6", "minecraft:anvil", 7, 1);
        node(list, "a_reloadrush", c, x, SkillEffect.RELOAD_RUSH, 1, 18, 0, 1.0, x4, "x6", "minecraft:sugar", 7, 3);
        node(list, "x_cross_double", c, x, SkillEffect.DOUBLE_SHOT, 1, 18, 0, 1.0, x4, "x6", "minecraft:bow", 7, 5,
                "a_multiload", "a_barrage");
        String x6 = "a_bracing|a_reloadrush|x_cross_double";
        node(list, "bolt_volley", c, x, SkillEffect.BOLT_VOLLEY, 3, 20, 10, 1.0, x6, "x_ult", "minecraft:firework_rocket", 8, 1);
        node(list, "a_netrain", c, x, SkillEffect.NET_RAIN, 3, 20, 10, 1.0, x6, "x_ult", "minecraft:cobweb", 8, 3);
        node(list, "x_hunt", c, x, SkillEffect.HUNTERS_MARK, 3, 20, 10, 1.0, x6, "x_ult", "minecraft:compass", 8, 5);
        node(list, "a_barrage", c, x, SkillEffect.BARRAGE, 1, 25, 0, 1.0, "bolt_volley,shrapnel", "x_mastery", "minecraft:tnt_minecart", 9, 0);
        node(list, "a_widenet", c, x, SkillEffect.WIDE_NET, 1, 25, 0, 1.0, "bolt_volley,a_netbolt", "x_mastery", "minecraft:string", 9, 2);
        node(list, "a_deathtrap", c, x, SkillEffect.DEATHTRAP, 1, 25, 0, 0.5, "a_netrain,a_netbolt|a_hunternet", "x_mastery", "minecraft:wither_rose", 9, 4);
        node(list, "x_pack", c, x, SkillEffect.PACK, 1, 25, 0, 1.0, "x_hunt,x_mark", "x_mastery", "minecraft:bone", 9, 6);
        node(list, "a_siegeengine", c, x, SkillEffect.SIEGE_ENGINE, 1, 30, 0, 1.0, "bolt_volley|a_netrain|x_hunt", "x_key", "minecraft:dispenser", 10, 2);
        node(list, "a_watchful", c, x, SkillEffect.WATCHFUL, 1, 30, 0, 0.15, "bolt_volley|a_netrain|x_hunt", "x_key", "minecraft:observer", 10, 4);
    }

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
