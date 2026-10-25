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
    // Guardian: two ways, each with two subclasses
    public static final String WAY_SHIELD = "shield";
    public static final String WAY_BLESSING = "bless";
    public static final String SUB_SHIELDFIGHTER = "shieldfighter";
    public static final String SUB_BULWARK = "bulwark";
    public static final String SUB_MEDIC = "medic";
    public static final String SUB_WARDEN = "warden";
    // Engineer: two ways, each with two subclasses
    public static final String WAY_MECHANICS = "mech";
    public static final String WAY_CHEMISTRY = "chem";
    public static final String SUB_GUNNER = "gunner";
    public static final String SUB_ELECTRICIAN = "electrician";
    public static final String SUB_DEMOLITION = "demolition";
    public static final String SUB_ALCHEMIST = "alchemist";
    // Summoner (admins only for now): the call of the wild (wolves, golems), later the call of the dead
    public static final String WAY_WILD = "wild";
    public static final String WAY_DEAD = "dead";
    public static final String SUB_TAMER = "tamer";
    public static final String SUB_GOLEMSMITH = "golemsmith";
    public static final String SUB_NECROMANCER = "necromancer";
    public static final String SUB_SOULCATCHER = "soulcatcher";

    /** Nodes that push one trap above its own three tiers. */
    private static final Map<TrapKind, String> TRAP_BOOSTS = Map.of(
            TrapKind.TURRET, "turret_tuning",
            TrapKind.BREWER, "toxic_brew",
            TrapKind.TESLA, "capacitor");
    /** Cross talents that lend a trap of the other way at tier 2. */
    private static final Map<TrapKind, String> CROSS_TRAPS = Map.of(
            TrapKind.MORTAR, "m_cross_mine",
            TrapKind.BREWER, "m_cross_poison",
            TrapKind.TURRET, "c_cross_turret",
            TrapKind.TESLA, "c_cross_tesla");

    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillTree() {
        this(defaultNodes());
    }

    /** The built-in nodes, so server tweaks can always be laid over them anew. */
    private final List<SkillNode> builtIn = new ArrayList<>();

    public SkillTree(List<SkillNode> list) {
        for (SkillNode node : list) {
            if (node != null && node.id() != null && !node.id().isBlank()) {
                nodes.put(node.id(), node);
                builtIn.add(node);
            }
        }
    }

    /**
     * Lays the server's skill tweaks over the built-in values (amount per level,
     * costs, highest level). Returns how many skills were changed.
     */
    public int applyTweaks(Map<String, SiegeConfig.SkillTweak> tweaks) {
        nodes.clear();
        int changed = 0;
        for (SkillNode n : builtIn) {
            SiegeConfig.SkillTweak t = tweaks == null ? null : tweaks.get(n.id());
            if (t == null) {
                nodes.put(n.id(), n);
                continue;
            }
            changed++;
            nodes.put(n.id(), new SkillNode(n.id(), n.clazz(), n.path(), n.effect(),
                    t.maxLevel != null ? Math.max(1, t.maxLevel) : n.maxLevel(),
                    t.cost != null ? Math.max(0, t.cost) : n.baseCost(),
                    t.costStep != null ? Math.max(0, t.costStep) : n.costStep(),
                    t.amount != null ? t.amount : n.amountPerLevel(),
                    n.requires(), n.requiresLevel(), n.icon(), n.row(), n.group(), n.locks(), n.col()));
        }
        return changed;
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
        // Trunk, then shield or blessing; every guardian draws the monsters' anger.
        guardian(list);

        // ---------------- Engineer ----------------
        // Trunk traps, then mechanics or chemistry; the remote steers the traps.
        engineer(list);

        // ---------------- Summoner ----------------
        // Trunk, then the call of the wild (wolves and golems) or the call of the dead (raised monsters, ghosts).
        summoner(list);

        // ---------------- Core: shared, paid by donations ----------------
        // Masonry first, then the wall (battlements, rebuilding) or the watch (time, beacon fire).
        // Fixed places give the core its own star map: masonry in the middle, the wall
        // to the left, the watch to the right, each way two rings out.
        list.add(new SkillNode("masonry", SkillClass.CORE, "", SkillEffect.MASONRY,
                3, 3, 2, 0.3, null, 0, "minecraft:stone_bricks", 0, "", List.of(), 3));
        list.add(new SkillNode("battlements", SkillClass.CORE, "wall", SkillEffect.BATTLEMENTS,
                3, 4, 2, 0.1, "masonry", 1, "minecraft:cobblestone_wall", 2, "", List.of(), 2));
        list.add(new SkillNode("rebuild", SkillClass.CORE, "wall", SkillEffect.REBUILD,
                3, 6, 3, 1.0, "battlements", 1, "minecraft:brick", 3, "", List.of(), 4));
        list.add(new SkillNode("fortified", SkillClass.CORE, "watch", SkillEffect.WAVE_DELAY,
                8, 4, 2, 0.5, "masonry", 1, "minecraft:obsidian", 2, "", List.of(), 2));
        list.add(new SkillNode("beacon_fire", SkillClass.CORE, "watch", SkillEffect.BEACON_FIRE,
                1, 6, 0, 1.0, "fortified", 1, "minecraft:campfire", 3, "", List.of(), 4));
        return list;
    }

    // ------------------------------------------------------------------
    // warrior
    // ------------------------------------------------------------------

    /** The subclass node of each way and the subclass it stands for. */
    private static final Map<String, String> SUBCLASS_NODES = Map.ofEntries(
            Map.entry("swift_draw", SUB_SWIFT),
            Map.entry("heavy_draw", SUB_HEAVY),
            Map.entry("shrapnel", SUB_BOLTER),
            Map.entry("a_netbolt", SUB_TRAPPER),
            Map.entry("w_combo", SUB_BLADEMASTER),
            Map.entry("bloodlust", SUB_BERSERKER),
            Map.entry("heavy_blow", SUB_AXE),
            Map.entry("smite", SUB_PALADIN),
            Map.entry("riposte", SUB_SHIELDFIGHTER),
            Map.entry("g_bastion", SUB_BULWARK),
            Map.entry("g_healing_hand", SUB_MEDIC),
            Map.entry("g_ward", SUB_WARDEN),
            Map.entry(TrapKind.TURRET.id(), SUB_GUNNER),
            Map.entry(TrapKind.TESLA.id(), SUB_ELECTRICIAN),
            Map.entry(TrapKind.MORTAR.id(), SUB_DEMOLITION),
            Map.entry(TrapKind.BREWER.id(), SUB_ALCHEMIST),
            Map.entry("s_tamer", SUB_TAMER),
            Map.entry("s_golemsmith", SUB_GOLEMSMITH),
            Map.entry("d_necro", SUB_NECROMANCER),
            Map.entry("d_soulcatcher", SUB_SOULCATCHER));

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
        // Combo is the blademaster's subclass node (Simon, 24 Oct 2026): no sword cooldown, plus the streak bonus.
        node(list, "w_combo", c, e, SkillEffect.COMBO, 4, 4, 2, 0.02, "champion_bane", "e_sub", "minecraft:sugar", 3, 1);
        node(list, "bloodlust", c, e, SkillEffect.RAGE, 4, 4, 2, 0.15, "champion_bane", "e_sub", "minecraft:redstone", 3, 5);
        node(list, "keen_edge", c, e, SkillEffect.CRIT_CHANCE, 1, 10, 0, 0.2, "w_combo:2", "e3", "minecraft:flint", 4, 0);
        node(list, "w_precision", c, e, SkillEffect.CRIT_POWER, 1, 10, 0, 0.5, "w_combo:2", "e3", "minecraft:target", 4, 2);
        node(list, "blood_drinker", c, e, SkillEffect.MAX_HEALTH, 1, 10, 0, 4.0, "bloodlust:2", "e3", "minecraft:ghast_tear", 4, 4);
        node(list, "w_warcry", c, e, SkillEffect.WARCRY, 1, 10, 0, 1.0, "bloodlust:2", "e3", "minecraft:goat_horn", 4, 6);
        String e3 = "keen_edge|w_precision|blood_drinker|w_warcry";
        node(list, "w_parry", c, e, SkillEffect.PARRY, 1, 12, 0, 0.15, e3, "e4", "minecraft:iron_sword", 5, 1);
        node(list, "w_frenzy", c, e, SkillEffect.FRENZY, 1, 12, 0, 1.0, e3, "e4", "minecraft:blaze_powder", 5, 3);
        node(list, "e_cross_heavy", c, e, SkillEffect.HEAVY_BLOW, 1, 12, 0, 3.0, e3, "e4", "minecraft:iron_axe", 5, 5,
                "w_bladefrenzy", "w_bladestorm");
        node(list, "w_bladefrenzy", c, e, SkillEffect.CRIT_RAGE, 1, 15, 0, 1.0, "keen_edge|w_precision,w_frenzy", "e5", "minecraft:netherite_sword", 6, 2);
        node(list, "w_bloodblade", c, e, SkillEffect.PARRY_HEAL, 1, 15, 0, 4.0, "blood_drinker|w_warcry,w_parry", "e5", "minecraft:red_dye", 6, 4);
        String e4 = "w_parry|w_frenzy|e_cross_heavy";
        node(list, "w_evasion", c, e, SkillEffect.DODGE, 1, 18, 0, 0.10, e4, "e6", "minecraft:phantom_membrane", 7, 1);
        node(list, "w_bloodprice", c, e, SkillEffect.BLOOD_PRICE, 1, 18, 0, 0.3, e4, "e6", "minecraft:redstone_block", 7, 3);
        node(list, "e_cross_smite", c, e, SkillEffect.SMITE, 1, 18, 0, 0.35, e4, "e6", "minecraft:glowstone_dust", 7, 5, "execute");
        String e6 = "w_evasion|w_bloodprice|e_cross_smite";
        node(list, "execute", c, e, SkillEffect.EXECUTE, 3, 20, 10, 1.0, e6, "e_ult", "minecraft:diamond_sword", 8, 2);
        node(list, "whirlwind", c, e, SkillEffect.WHIRLWIND, 3, 20, 10, 1.0, e6, "e_ult", "minecraft:breeze_rod", 8, 4);
        node(list, "w_chain_execute", c, e, SkillEffect.CHAIN_EXECUTE, 1, 25, 0, 1.0, "execute,w_combo", "e_mastery", "minecraft:wither_skeleton_skull", 9, 0);
        node(list, "w_bloody_execute", c, e, SkillEffect.BLOODY_EXECUTE, 1, 25, 0, 1.0, "execute,bloodlust", "e_mastery", "minecraft:nether_wart", 9, 2);
        node(list, "w_bladestorm", c, e, SkillEffect.BLADESTORM, 1, 25, 0, 1.0, "whirlwind,w_combo", "e_mastery", "minecraft:wind_charge", 9, 4);
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
        node(list, "holy_vigor", c, m, SkillEffect.MAX_HEALTH, 1, 10, 0, 4.0, "smite:2", "m3", "minecraft:glow_berries", 4, 4);
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

    // ------------------------------------------------------------------
    // engineer
    // ------------------------------------------------------------------

    private static void engineer(List<SkillNode> list) {
        SkillClass c = SkillClass.ENGINEER;
        String spike = TrapKind.SPIKE.id();
        String snare = TrapKind.SNARE.id();
        node(list, spike, c, "", SkillEffect.TRAP, 3, 3, 2, 1.0, null, "", "nightsiege:spike_trap", 0, 3);
        node(list, "e_battery", c, "", SkillEffect.REMOTE_ENERGY, 3, 3, 2, 10.0, spike, "e_stem", "minecraft:copper_ingot", 1, 1);
        node(list, snare, c, "", SkillEffect.TRAP, 3, 3, 2, 1.0, spike + ":2", "", "nightsiege:snare_trap", 1, 3);
        node(list, "e_finemech", c, "", SkillEffect.TRAP_DURABILITY, 3, 3, 2, 0.17, spike, "e_stem", "minecraft:clock", 1, 5);

        // ----- Way of mechanics: turrets and current
        String m = WAY_MECHANICS;
        String turret = TrapKind.TURRET.id();
        String tesla = TrapKind.TESLA.id();
        node(list, "e_longrange", c, m, SkillEffect.REMOTE_RANGE, 3, 3, 2, 5.0, snare, "em1", "minecraft:spyglass", 2, 1);
        node(list, "e_circuit", c, m, SkillEffect.OVERCHARGE_TIME, 3, 3, 2, 20.0, snare, "", "minecraft:repeater", 2, 3);
        node(list, "e_fastcharge", c, m, SkillEffect.RECHARGE, 3, 3, 2, 0.17, snare, "em1", "minecraft:redstone_torch", 2, 5);
        node(list, turret, c, m, SkillEffect.TRAP, 3, 4, 3, 1.0, "e_circuit", "em_sub", "nightsiege:arrow_turret", 3, 1);
        node(list, tesla, c, m, SkillEffect.TRAP, 3, 4, 3, 1.0, "e_circuit", "em_sub", "nightsiege:tesla_coil", 3, 5);
        node(list, "turret_tuning", c, m, SkillEffect.TRAP_BOOST, 1, 10, 0, 2.0, turret + ":2", "em3", "minecraft:redstone", 4, 0);
        node(list, "e_ammobelt", c, m, SkillEffect.AMMO_BELT, 1, 10, 0, 1.0, turret + ":2", "em3", "minecraft:arrow", 4, 2);
        node(list, "capacitor", c, m, SkillEffect.TRAP_BOOST, 1, 10, 0, 2.0, tesla + ":2", "em3", "minecraft:copper_block", 4, 4);
        node(list, "e_chainbolt", c, m, SkillEffect.CHAIN_LIGHTNING, 1, 10, 0, 2.0, tesla + ":2", "em3", "minecraft:lightning_rod", 4, 6);
        String m3 = "turret_tuning|e_ammobelt|capacitor|e_chainbolt";
        node(list, "e_seeker", c, m, SkillEffect.SEEKER, 1, 12, 0, 0.3, m3, "em4", "minecraft:target", 5, 1);
        node(list, "e_overvolt", c, m, SkillEffect.OVERVOLT, 1, 12, 0, 1.0, m3, "em4", "minecraft:glowstone_dust", 5, 3);
        node(list, "m_cross_mine", c, m, SkillEffect.TRAP, 1, 12, 0, 1.0, m3, "em4", "nightsiege:mortar_tower", 5, 5,
                "e_network", "e_ball_lightning");
        node(list, "e_charged", c, m, SkillEffect.CHARGED_ARROWS, 1, 15, 0, 0.5, turret + ",e_overvolt|e_seeker", "em5", "minecraft:spectral_arrow", 6, 1);
        node(list, "e_beacon", c, m, SkillEffect.GUIDING_BEAM, 1, 15, 0, 1.0, tesla + ",e_seeker", "em5", "minecraft:end_rod", 6, 3);
        node(list, "e_network", c, m, SkillEffect.NETWORK, 1, 15, 0, 1.0, m3 + ",e_overvolt", "em5", "minecraft:comparator", 6, 5);
        String m4 = "e_seeker|e_overvolt|m_cross_mine";
        node(list, "e_backup", c, m, SkillEffect.BACKUP_POWER, 1, 18, 0, 1.0, m4, "em6", "minecraft:redstone_lamp", 7, 1);
        node(list, "e_watchtower", c, m, SkillEffect.TOWER_RANGE, 1, 18, 0, 4.0, m4, "em6", "minecraft:observer", 7, 3);
        node(list, "m_cross_poison", c, m, SkillEffect.TRAP, 1, 18, 0, 1.0, m4, "em6", "nightsiege:potion_tower", 7, 5,
                "thunderstorm");
        String m6 = "e_backup|e_watchtower|m_cross_poison";
        node(list, "overcharge", c, m, SkillEffect.OVERCHARGE, 3, 20, 10, 1.0, m6, "em_ult", "minecraft:redstone_block", 8, 2);
        node(list, "thunderstorm", c, m, SkillEffect.THUNDERSTORM, 3, 20, 10, 1.0, m6, "em_ult", "minecraft:trident", 8, 4);
        node(list, "e_sustained", c, m, SkillEffect.SUSTAINED_FIRE, 1, 25, 0, 1.0, "overcharge," + turret, "em_mastery", "minecraft:dispenser", 9, 0);
        node(list, "e_piercer", c, m, SkillEffect.ARMOR_PIERCER, 1, 25, 0, 0.5, "overcharge," + tesla, "em_mastery", "minecraft:netherite_scrap", 9, 2);
        node(list, "e_ball_lightning", c, m, SkillEffect.BALL_LIGHTNING, 1, 25, 0, 1.0, "thunderstorm," + tesla, "em_mastery", "minecraft:fire_charge", 9, 4);
        node(list, "e_superconductor", c, m, SkillEffect.SUPERCONDUCTOR, 1, 25, 0, 1.0, "thunderstorm," + turret, "em_mastery", "minecraft:copper_bulb", 9, 6);
        node(list, "e_fortress", c, m, SkillEffect.FORTRESS, 1, 30, 0, 1.0, "overcharge|thunderstorm", "em_key", "minecraft:iron_bars", 10, 2);
        node(list, "e_fieldtech", c, m, SkillEffect.FIELD_TECH, 1, 30, 0, 1.0, "overcharge|thunderstorm", "em_key", "minecraft:crafter", 10, 4);

        // ----- Way of chemistry: mortar, flames and poison (the mine and the poison trap became towers, 24 Oct 2026)
        String ch = WAY_CHEMISTRY;
        String mine = TrapKind.MORTAR.id();
        String poison = TrapKind.BREWER.id();
        node(list, "e_stockpile", c, ch, SkillEffect.FREE_TRAPS, 3, 3, 2, 1.0, snare, "ec1", "minecraft:chest", 2, 1);
        node(list, "e_detonator", c, ch, SkillEffect.DETONATOR, 3, 3, 2, 1.0, snare, "", "minecraft:lever", 2, 3);
        node(list, "e_durable", c, ch, SkillEffect.TRAP_DURABILITY, 3, 3, 2, 0.17, snare, "ec1", "minecraft:iron_ingot", 2, 5);
        node(list, mine, c, ch, SkillEffect.TRAP, 3, 4, 3, 1.0, "e_detonator", "ec_sub", "nightsiege:mortar_tower", 3, 1);
        node(list, poison, c, ch, SkillEffect.TRAP, 3, 4, 3, 1.0, "e_detonator", "ec_sub", "nightsiege:potion_tower", 3, 5);
        node(list, TrapKind.FLAME.id(), c, ch, SkillEffect.TRAP, 3, 10, 5, 1.0, mine + ":2", "ec3", "nightsiege:flame_trap", 4, 0);
        node(list, "e_shrapnel_mine", c, ch, SkillEffect.SHRAPNEL_MINE, 1, 10, 0, 0.5, mine + ":2", "ec3", "minecraft:flint", 4, 2);
        node(list, "toxic_brew", c, ch, SkillEffect.TRAP_BOOST, 1, 10, 0, 2.0, poison + ":2", "ec3", "minecraft:fermented_spider_eye", 4, 4);
        node(list, "e_acid", c, ch, SkillEffect.ACID, 1, 10, 0, 0.2, poison + ":2", "ec3", "minecraft:slime_ball", 4, 6);
        String c3 = TrapKind.FLAME.id() + "|e_shrapnel_mine|toxic_brew|e_acid";
        node(list, "e_chain_reaction", c, ch, SkillEffect.CHAIN_REACTION, 1, 12, 0, 1.0, c3, "ec4", "minecraft:tnt", 5, 1);
        node(list, "e_accelerant", c, ch, SkillEffect.ACCELERANT, 1, 12, 0, 0.5, c3, "ec4", "minecraft:blaze_powder", 5, 3);
        node(list, "c_cross_turret", c, ch, SkillEffect.TRAP, 1, 12, 0, 1.0, c3, "ec4", "nightsiege:arrow_turret", 5, 5,
                "e_napalm", "e_carpet");
        node(list, "e_napalm", c, ch, SkillEffect.NAPALM, 1, 15, 0, 1.0, TrapKind.FLAME.id() + ",e_accelerant", "ec5", "minecraft:magma_cream", 6, 1);
        node(list, "e_plague_mine", c, ch, SkillEffect.PLAGUE_MINE, 1, 15, 0, 8.0, poison + ",e_chain_reaction", "ec5", "minecraft:spider_eye", 6, 3);
        node(list, "e_targetblast", c, ch, SkillEffect.TARGET_BLAST, 1, 15, 0, 4.0, "c_cross_turret,e_detonator", "ec5", "minecraft:firework_star", 6, 5);
        String c4 = "e_chain_reaction|e_accelerant|c_cross_turret";
        node(list, "e_blastpower", c, ch, SkillEffect.BLAST_POWER, 1, 18, 0, 0.5, c4, "ec6", "minecraft:gunpowder", 7, 1);
        node(list, "e_slowpoison", c, ch, SkillEffect.SLOW_POISON, 1, 18, 0, 0.5, c4, "ec6", "minecraft:pufferfish", 7, 3);
        node(list, "c_cross_tesla", c, ch, SkillEffect.TRAP, 1, 18, 0, 1.0, c4, "ec6", "nightsiege:tesla_coil", 7, 5,
                "toxic_cloud");
        String c6 = "e_blastpower|e_slowpoison|c_cross_tesla";
        node(list, "bombardment", c, ch, SkillEffect.BOMBARDMENT, 3, 20, 10, 1.0, c6, "ec_ult", "minecraft:tnt_minecart", 8, 2);
        node(list, "toxic_cloud", c, ch, SkillEffect.TOXIC_CLOUD, 3, 20, 10, 1.0, c6, "ec_ult", "minecraft:lingering_potion", 8, 4);
        node(list, "e_carpet", c, ch, SkillEffect.CARPET_BOMB, 1, 25, 0, 4.0, "bombardment," + mine, "ec_mastery", "minecraft:tnt", 9, 0);
        node(list, "e_firebombs", c, ch, SkillEffect.FIRE_BOMBS, 1, 25, 0, 1.0, "bombardment," + poison, "ec_mastery", "minecraft:fire_charge", 9, 2);
        node(list, "e_plague", c, ch, SkillEffect.PLAGUE, 1, 25, 0, 1.0, "toxic_cloud," + poison, "ec_mastery", "minecraft:wither_rose", 9, 4);
        node(list, "e_acid_fog", c, ch, SkillEffect.ACID_FOG, 1, 25, 0, 0.25, "toxic_cloud," + mine, "ec_mastery", "minecraft:bone_meal", 9, 6);
        node(list, "e_pyro", c, ch, SkillEffect.PYROMANIAC, 1, 30, 0, 0.4, "bombardment|toxic_cloud", "ec_key", "minecraft:flint_and_steel", 10, 2);
        node(list, "e_plague_doctor", c, ch, SkillEffect.PLAGUE_DOCTOR, 1, 30, 0, 1.0, "bombardment|toxic_cloud", "ec_key", "minecraft:carved_pumpkin", 10, 4);
    }

    // ------------------------------------------------------------------
    // summoner
    // ------------------------------------------------------------------

    /**
     * The summoner (concept of 24 October 2026, built 25 October): companions that follow,
     * fight and vanish after the night. Way of the wild: wolves (tamer) or golems
     * (golemsmith). Way of the dead: fallen monsters rise again for souls (necromancer) or
     * ghosts come along (soulcatcher).
     */
    private static void summoner(List<SkillNode> list) {
        SkillClass c = SkillClass.SUMMONER;
        node(list, "s_soulbond", c, "", SkillEffect.SUMMON_HEALTH, 3, 2, 1, 0.1, null, "", "minecraft:lead", 0, 3);
        node(list, "s_callrange", c, "", SkillEffect.CALL_RANGE, 3, 3, 2, 3.0, "s_soulbond", "s_stem", "minecraft:compass", 1, 1);
        // Lebensband (Simon, 25 October 2026): the summoner had no hearts in the tree at all.
        node(list, "s_lifebond", c, "", SkillEffect.MAX_HEALTH, 3, 3, 2, 4.0, "s_soulbond", "", "minecraft:golden_apple", 1, 3);
        node(list, "s_command", c, "", SkillEffect.SUMMON_DAMAGE, 3, 3, 2, 0.1, "s_soulbond", "s_stem", "minecraft:name_tag", 1, 5);

        // ----- Way of the wild: wolves and golems answer the call
        String w = WAY_WILD;
        node(list, "s_loyalty", c, w, SkillEffect.SUMMON_TOUGHNESS, 3, 3, 2, 0.05, "s_soulbond", "l1", "minecraft:leather_chestplate", 2, 1);
        node(list, "s_packcall", c, w, SkillEffect.PACK_CALL, 3, 3, 2, 3.0, "s_soulbond", "", "minecraft:wolf_spawn_egg", 2, 3);
        node(list, "s_trail", c, w, SkillEffect.SUMMON_SPEED, 3, 3, 2, 0.07, "s_soulbond", "l1", "minecraft:rabbit_foot", 2, 5);
        node(list, "s_tamer", c, w, SkillEffect.PACK_SLOTS, 4, 4, 2, 1.0, "s_packcall", "l_sub", "minecraft:bone", 3, 1);
        node(list, "s_golemsmith", c, w, SkillEffect.GOLEM_CALL, 4, 4, 2, 0.15, "s_packcall", "l_sub", "minecraft:iron_golem_spawn_egg", 3, 5);
        node(list, "s_alpha", c, w, SkillEffect.ALPHA_WOLF, 1, 10, 0, 1.0, "s_tamer:2", "l3", "minecraft:wolf_armor", 4, 0);
        node(list, "s_packhunt", c, w, SkillEffect.PACK_HUNT, 1, 10, 0, 0.2, "s_tamer:2", "l3", "minecraft:beef", 4, 2);
        node(list, "s_snowguard", c, w, SkillEffect.SNOW_GUARD, 1, 10, 0, 2.0, "s_golemsmith:2", "l3", "minecraft:snowball", 4, 4);
        node(list, "s_hardgolem", c, w, SkillEffect.HARD_GOLEM, 1, 10, 0, 0.5, "s_golemsmith:2", "l3", "minecraft:anvil", 4, 6);
        String l3 = "s_alpha|s_packhunt|s_snowguard|s_hardgolem";
        node(list, "s_howl", c, w, SkillEffect.HOWL, 1, 12, 0, 0.3, l3, "l4", "minecraft:goat_horn", 5, 1);
        node(list, "s_wardbond", c, w, SkillEffect.WARD_BOND, 1, 12, 0, 1.0, l3, "l4", "minecraft:shield", 5, 3);
        node(list, "l_cross_bones", c, w, SkillEffect.BONE_GUARD, 1, 12, 0, 1.0, l3, "l4", "minecraft:skeleton_skull", 5, 5,
                "s_moonpack");
        node(list, "s_packleader", c, w, SkillEffect.PACK_LEADER, 1, 15, 0, 2.0, "s_alpha,s_howl", "l5", "minecraft:glistering_melon_slice", 6, 1);
        node(list, "s_golemwork", c, w, SkillEffect.GOLEM_WORK, 1, 15, 0, 2.0, "s_hardgolem,s_wardbond", "l5", "minecraft:piston", 6, 3);
        node(list, "s_mixedpack", c, w, SkillEffect.MIXED_PACK, 1, 15, 0, 0.15, "s_packhunt,l_cross_bones", "l5", "minecraft:bone_meal", 6, 5);
        String l4 = "s_howl|s_wardbond|l_cross_bones";
        node(list, "s_wildness", c, w, SkillEffect.WILDNESS, 1, 18, 0, 0.25, l4, "l6", "minecraft:redstone", 7, 1);
        node(list, "s_care", c, w, SkillEffect.CARE, 1, 18, 0, 2.0, l4, "l6", "minecraft:sweet_berries", 7, 3);
        node(list, "l_cross_harvest", c, w, SkillEffect.SOUL_HARVEST, 1, 18, 0, 3.0, l4, "l6", "minecraft:soul_lantern", 7, 5,
                "s_titan");
        String l6 = "s_wildness|s_care|l_cross_harvest";
        node(list, "s_wild_hunt", c, w, SkillEffect.WILD_HUNT, 3, 20, 10, 1.0, l6, "l_ult", "minecraft:echo_shard", 8, 2);
        node(list, "s_titan", c, w, SkillEffect.TITAN, 3, 20, 10, 1.0, l6, "l_ult", "minecraft:iron_block", 8, 4);
        node(list, "s_endless_hunt", c, w, SkillEffect.ENDLESS_HUNT, 1, 25, 0, 5.0, "s_wild_hunt,s_tamer", "l_mastery", "minecraft:clock", 9, 0);
        node(list, "s_moonpack", c, w, SkillEffect.MOON_PACK, 1, 25, 0, 1.0, "s_wild_hunt,s_golemsmith", "l_mastery", "minecraft:glow_berries", 9, 2);
        node(list, "s_titanfist", c, w, SkillEffect.TITAN_FIST, 1, 25, 0, 1.0, "s_titan,s_tamer", "l_mastery", "minecraft:mace", 9, 4);
        node(list, "s_orecore", c, w, SkillEffect.ORE_CORE, 1, 25, 0, 1.0, "s_titan,s_golemsmith", "l_mastery", "minecraft:raw_iron_block", 9, 6);
        node(list, "s_beastlord", c, w, SkillEffect.BEAST_LORD, 1, 30, 0, 1.0, "s_wild_hunt|s_titan", "l_key", "minecraft:dragon_head", 10, 2);
        node(list, "s_shepherd", c, w, SkillEffect.SHEPHERD, 1, 30, 0, 1.0, "s_wild_hunt|s_titan", "l_key", "minecraft:lantern", 10, 4);

        // ----- Way of the dead: fallen monsters rise again (weakened) for souls; ghosts
        String d = WAY_DEAD;
        node(list, "d_greed", c, d, SkillEffect.SOUL_GREED, 3, 3, 2, 0.1, "s_soulbond", "d1", "minecraft:soul_sand", 2, 1);
        node(list, "d_raise", c, d, SkillEffect.RAISE_DEAD, 3, 3, 2, 10.0, "s_soulbond", "", "minecraft:zombie_head", 2, 3);
        node(list, "d_bindrune", c, d, SkillEffect.BIND_RUNE, 3, 3, 2, 0.17, "s_soulbond", "d1", "minecraft:enchanted_book", 2, 5);
        node(list, "d_necro", c, d, SkillEffect.NECRO_POWER, 4, 4, 2, 0.15, "d_raise", "d_sub", "minecraft:wither_skeleton_skull", 3, 1);
        node(list, "d_soulcatcher", c, d, SkillEffect.SOUL_CATCHER, 4, 4, 2, 0.15, "d_raise", "d_sub", "minecraft:soul_lantern", 3, 5);
        node(list, "d_archers", c, d, SkillEffect.SKELETON_ARCHERS, 1, 10, 0, 0.5, "d_necro:2", "d3", "minecraft:bow", 4, 0);
        node(list, "d_ghoul", c, d, SkillEffect.GHOUL_HUNGER, 1, 10, 0, 0.3, "d_necro:2", "d3", "minecraft:rotten_flesh", 4, 2);
        node(list, "d_swarm", c, d, SkillEffect.GHOST_SWARM, 1, 10, 0, 1.0, "d_soulcatcher:2", "d3", "minecraft:phantom_membrane", 4, 4);
        node(list, "d_soulshield", c, d, SkillEffect.SOUL_SHIELD, 1, 10, 0, 8.0, "d_soulcatcher:2", "d3", "minecraft:totem_of_undying", 4, 6);
        String d3 = "d_archers|d_ghoul|d_swarm|d_soulshield";
        node(list, "d_rot", c, d, SkillEffect.ROT, 1, 12, 0, 1.0, d3, "d4", "minecraft:fermented_spider_eye", 5, 1);
        node(list, "d_corpse", c, d, SkillEffect.CORPSE_BLAST, 1, 12, 0, 6.0, d3, "d4", "minecraft:tnt", 5, 3);
        node(list, "d_cross_wolf", c, d, SkillEffect.PACK_CALL, 1, 12, 0, 0.0, d3, "d4", "minecraft:wolf_spawn_egg", 5, 5,
                "d_champion");
        node(list, "d_legion", c, d, SkillEffect.BONE_LEGION, 1, 15, 0, 1.0, "d_archers,d_rot", "d5", "minecraft:arrow", 6, 1);
        node(list, "d_soulburn", c, d, SkillEffect.SOUL_BURN, 1, 15, 0, 2.0, "d_swarm,d_corpse", "d5", "minecraft:soul_campfire", 6, 3);
        node(list, "d_dance", c, d, SkillEffect.DEATH_DANCE, 1, 15, 0, 2.0, "d_ghoul,d_cross_wolf", "d5", "minecraft:bone", 6, 5);
        String d4 = "d_rot|d_corpse|d_cross_wolf";
        node(list, "d_unholy", c, d, SkillEffect.UNHOLY_MIGHT, 1, 18, 0, 0.05, d4, "d6", "minecraft:wither_rose", 7, 1);
        node(list, "d_pact", c, d, SkillEffect.SOUL_PACT, 1, 18, 0, 2.0, d4, "d6", "minecraft:ghast_tear", 7, 3);
        node(list, "d_cross_loyalty", c, d, SkillEffect.SUMMON_TOUGHNESS, 1, 18, 0, 0.15, d4, "d6", "minecraft:leather_chestplate", 7, 5,
                "d_soulstorm");
        String d6 = "d_unholy|d_pact|d_cross_loyalty";
        node(list, "d_army", c, d, SkillEffect.ARMY_OF_THE_DEAD, 3, 20, 10, 1.0, d6, "d_ult", "minecraft:skeleton_skull", 8, 2);
        node(list, "d_soulstorm", c, d, SkillEffect.SOUL_STORM, 3, 20, 10, 1.0, d6, "d_ult", "minecraft:sculk_catalyst", 8, 4);
        node(list, "d_eternal", c, d, SkillEffect.ETERNAL_LEGION, 1, 25, 0, 5.0, "d_army,d_necro", "d_mastery", "minecraft:clock", 9, 0);
        node(list, "d_champion", c, d, SkillEffect.CHAMPION_OF_THE_DEAD, 1, 25, 0, 1.0, "d_army,d_soulcatcher", "d_mastery", "minecraft:golden_helmet", 9, 2);
        node(list, "d_soulflood", c, d, SkillEffect.SOUL_FLOOD, 1, 25, 0, 0.5, "d_soulstorm,d_soulcatcher", "d_mastery", "minecraft:soul_torch", 9, 4);
        node(list, "d_echo", c, d, SkillEffect.ECHO, 1, 25, 0, 0.5, "d_soulstorm,d_necro", "d_mastery", "minecraft:echo_shard", 9, 6);
        node(list, "d_lich", c, d, SkillEffect.LICH, 1, 30, 0, 0.5, "d_army|d_soulstorm", "d_key", "minecraft:dragon_breath", 10, 2);
        node(list, "d_soulkeeper", c, d, SkillEffect.SOUL_KEEPER, 1, 30, 0, 20.0, "d_army|d_soulstorm", "d_key", "minecraft:respawn_anchor", 10, 4);
    }

    // ------------------------------------------------------------------
    // guardian
    // ------------------------------------------------------------------

    private static void guardian(List<SkillNode> list) {
        SkillClass c = SkillClass.GUARDIAN;
        node(list, "vitality", c, "", SkillEffect.MAX_HEALTH, 4, 2, 1, 2.0, null, "", "minecraft:golden_apple", 0, 3);
        node(list, "g_challenger", c, "", SkillEffect.AGGRO, 3, 3, 2, 2.0, "vitality", "g_stem", "minecraft:bell", 1, 2);
        node(list, "toughness", c, "", SkillEffect.ARMOR, 3, 3, 2, 1.5, "vitality", "g_stem", "minecraft:iron_chestplate", 1, 4);

        // ----- Way of the shield (boni only with a shield at hand)
        String s = WAY_SHIELD;
        node(list, "g_ironward", c, s, SkillEffect.ARMOR, 3, 3, 2, 1.5, "vitality", "s1", "minecraft:iron_ingot", 2, 1);
        node(list, "g_shieldwall", c, s, SkillEffect.SHIELD_WALL, 3, 3, 2, 1.0, "vitality", "", "minecraft:shield", 2, 3);
        node(list, "steadfast", c, s, SkillEffect.KNOCKBACK_RESIST, 3, 3, 2, 0.25, "vitality", "s1", "minecraft:netherite_ingot", 2, 5);
        node(list, "riposte", c, s, SkillEffect.COUNTER, 4, 4, 2, 2.5, "g_shieldwall", "s_sub", "minecraft:iron_sword", 3, 1);
        node(list, "g_bastion", c, s, SkillEffect.BASTION, 4, 4, 2, 0.02, "g_shieldwall", "s_sub", "minecraft:cobblestone_wall", 3, 5);
        node(list, "g_shieldthrow", c, s, SkillEffect.SHIELD_THROW, 1, 10, 0, 10.0, "riposte:2", "s3", "minecraft:trident", 4, 0);
        node(list, "g_shieldbash", c, s, SkillEffect.SHIELD_BASH, 1, 10, 0, 8.0, "riposte:2", "s3", "minecraft:piston", 4, 2);
        node(list, "g_ironhide", c, s, SkillEffect.IRONHIDE, 1, 10, 0, 2.0, "g_bastion:2", "s3", "minecraft:chainmail_chestplate", 4, 4);
        node(list, "g_anchor", c, s, SkillEffect.ANCHOR, 1, 10, 0, 0.3, "g_bastion:2", "s3", "minecraft:anvil", 4, 6);
        String s3 = "g_shieldthrow|g_shieldbash|g_ironhide|g_anchor";
        node(list, "g_counterstrike", c, s, SkillEffect.COUNTERSTRIKE, 1, 12, 0, 1.0, s3, "s4", "minecraft:golden_sword", 5, 1);
        node(list, "g_thorns", c, s, SkillEffect.THORNS, 1, 12, 0, 4.0, s3, "s4", "minecraft:sweet_berries", 5, 3);
        node(list, "s_cross_dressing", c, s, SkillEffect.HEAL_AURA, 1, 12, 0, 0.75, s3, "s4", "minecraft:glistering_melon_slice", 5, 5,
                "g_hedgehog", "g_rampart");
        node(list, "g_boomerang", c, s, SkillEffect.BOOMERANG, 1, 15, 0, 1.0, "g_shieldthrow,g_counterstrike|g_thorns", "s5", "minecraft:fishing_rod", 6, 1);
        node(list, "g_vengeance", c, s, SkillEffect.VENGEANCE, 1, 15, 0, 9.0, "g_ironhide|g_shieldbash,g_counterstrike", "s5", "minecraft:fire_charge", 6, 3);
        node(list, "g_hedgehog", c, s, SkillEffect.HEDGEHOG, 1, 15, 0, 1.0, "g_anchor,g_thorns", "s5", "minecraft:cactus", 6, 5);
        String s4 = "g_counterstrike|g_thorns|s_cross_dressing";
        node(list, "g_laststand", c, s, SkillEffect.LAST_STAND, 1, 18, 0, 1.0, s4, "s6", "minecraft:shulker_shell", 7, 1);
        node(list, "g_revenge", c, s, SkillEffect.REVENGE, 1, 18, 0, 0.5, s4, "s6", "minecraft:redstone", 7, 3);
        node(list, "s_cross_ward", c, s, SkillEffect.WARD, 1, 18, 0, 2.0, s4, "s6", "minecraft:amethyst_shard", 7, 5,
                "shield_charge");
        String s6 = "g_laststand|g_revenge|s_cross_ward";
        node(list, "shield_charge", c, s, SkillEffect.SHIELD_CHARGE, 3, 20, 10, 1.0, s6, "s_ult", "minecraft:iron_horse_armor", 8, 2);
        node(list, "taunt", c, s, SkillEffect.TAUNT, 3, 20, 10, 1.0, s6, "s_ult", "minecraft:goat_horn", 8, 4);
        node(list, "g_debris", c, s, SkillEffect.DEBRIS, 1, 25, 0, 1.0, "shield_charge,riposte", "s_mastery", "minecraft:gravel", 9, 0);
        node(list, "g_rampart", c, s, SkillEffect.RAMPART, 1, 25, 0, 1.0, "shield_charge,g_bastion", "s_mastery", "minecraft:iron_block", 9, 2);
        node(list, "g_iron_will", c, s, SkillEffect.IRON_WILL, 1, 25, 0, 1.0, "taunt,g_bastion", "s_mastery", "minecraft:netherite_chestplate", 9, 4);
        node(list, "g_shared_burden", c, s, SkillEffect.SHARED_BURDEN, 1, 25, 0, 1.0, "taunt,riposte", "s_mastery", "minecraft:lead", 9, 6);
        node(list, "g_living_wall", c, s, SkillEffect.LIVING_WALL, 1, 30, 0, 1.0, "shield_charge|taunt", "s_key", "minecraft:obsidian", 10, 2);
        node(list, "g_bladeshield", c, s, SkillEffect.BLADESHIELD, 1, 30, 0, 1.0, "shield_charge|taunt", "s_key", "minecraft:netherite_sword", 10, 4);

        // ----- Way of blessing (healing and barriers)
        String b = WAY_BLESSING;
        node(list, "g_prayer", c, b, SkillEffect.HEAL_POWER, 3, 3, 2, 0.08, "vitality", "gb1", "minecraft:book", 2, 1);
        node(list, "field_dressing", c, b, SkillEffect.HEAL_AURA, 3, 3, 2, 0.5, "vitality", "", "minecraft:glistering_melon_slice", 2, 3);
        node(list, "g_wardsign", c, b, SkillEffect.WARD_SIGN, 3, 3, 2, 0.04, "vitality", "gb1", "minecraft:white_banner", 2, 5);
        node(list, "g_healing_hand", c, b, SkillEffect.HEALING_HAND, 4, 4, 2, 1.0, "field_dressing", "gb_sub", "minecraft:honey_bottle", 3, 1);
        node(list, "g_ward", c, b, SkillEffect.WARD, 4, 4, 2, 1.0, "field_dressing", "gb_sub", "minecraft:amethyst_shard", 3, 5);
        node(list, "guardian_angel", c, b, SkillEffect.GUARDIAN_ANGEL, 1, 10, 0, 2.0, "g_healing_hand:2", "gb3", "minecraft:totem_of_undying", 4, 0);
        node(list, "g_triage", c, b, SkillEffect.TRIAGE, 1, 10, 0, 1.0, "g_healing_hand:2", "gb3", "minecraft:apple", 4, 2);
        node(list, "g_dome", c, b, SkillEffect.DOME, 1, 10, 0, 1.0, "g_ward:2", "gb3", "minecraft:glass", 4, 4);
        node(list, "g_cleanse", c, b, SkillEffect.CLEANSE, 1, 10, 0, 1.0, "g_ward:2", "gb3", "minecraft:milk_bucket", 4, 6);
        String b3 = "guardian_angel|g_triage|g_dome|g_cleanse";
        node(list, "g_lightbringer", c, b, SkillEffect.AURA_RANGE, 1, 12, 0, 4.0, b3, "gb4", "minecraft:lantern", 5, 1);
        node(list, "g_selfless", c, b, SkillEffect.SELFLESS, 1, 12, 0, 0.3, b3, "gb4", "minecraft:poppy", 5, 3);
        node(list, "b_cross_riposte", c, b, SkillEffect.COUNTER, 1, 12, 0, 3.0, b3, "gb4", "minecraft:iron_sword", 5, 5,
                "g_wellspring", "g_lifespring");
        node(list, "g_lifebond", c, b, SkillEffect.LIFEBOND, 1, 15, 0, 3.0, "g_dome|g_cleanse,g_selfless", "gb5", "minecraft:heart_of_the_sea", 6, 1);
        node(list, "g_wellspring", c, b, SkillEffect.WELLSPRING, 1, 15, 0, 1.0, "g_dome,g_lightbringer", "gb5", "minecraft:water_bucket", 6, 3);
        node(list, "g_miracle", c, b, SkillEffect.MIRACLE, 1, 15, 0, 4.0, "guardian_angel|g_triage,g_lightbringer|g_selfless", "gb5", "minecraft:nether_star", 6, 5);
        String b4 = "g_lightbringer|g_selfless|b_cross_riposte";
        node(list, "g_overflow", c, b, SkillEffect.OVERFLOW, 1, 18, 0, 6.0, b4, "gb6", "minecraft:experience_bottle", 7, 1);
        node(list, "g_grace", c, b, SkillEffect.GRACE, 1, 18, 0, 1.0, b4, "gb6", "minecraft:feather", 7, 3);
        node(list, "b_cross_bastion", c, b, SkillEffect.BASTION, 1, 18, 0, 0.06, b4, "gb6", "minecraft:cobblestone_wall", 7, 5,
                "mass_heal");
        String b6 = "g_overflow|g_grace|b_cross_bastion";
        node(list, "mass_heal", c, b, SkillEffect.MASS_HEAL, 3, 20, 10, 1.0, b6, "gb_ult", "minecraft:enchanted_golden_apple", 8, 2);
        node(list, "g_sanctuary", c, b, SkillEffect.SANCTUARY, 3, 20, 10, 1.0, b6, "gb_ult", "minecraft:end_crystal", 8, 4);
        node(list, "g_rebirth", c, b, SkillEffect.REBIRTH, 1, 25, 0, 1.0, "mass_heal,g_healing_hand", "gb_mastery", "minecraft:blaze_powder", 9, 0);
        node(list, "g_lifespring", c, b, SkillEffect.LIFESPRING, 1, 25, 0, 1.0, "mass_heal,g_ward", "gb_mastery", "minecraft:glow_berries", 9, 2);
        node(list, "g_lightcircle", c, b, SkillEffect.LIGHT_CIRCLE, 1, 25, 0, 1.0, "g_sanctuary,g_healing_hand", "gb_mastery", "minecraft:sunflower", 9, 4);
        node(list, "g_eternal_watch", c, b, SkillEffect.ETERNAL_WATCH, 1, 25, 0, 1.0, "g_sanctuary,g_ward", "gb_mastery", "minecraft:clock", 9, 6);
        node(list, "g_saint", c, b, SkillEffect.SAINT, 1, 30, 0, 0.4, "mass_heal|g_sanctuary", "gb_key", "minecraft:enchanted_book", 10, 2);
        node(list, "g_patron", c, b, SkillEffect.PATRON, 1, 30, 0, 0.5, "mass_heal|g_sanctuary", "gb_key", "minecraft:diamond_chestplate", 10, 4);
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
        // The shared core tree has two branches, but both stay open.
        if (!node.onTrunk() && !node.clazz().isShared()) {
            String taken = skills.pathTaken(this);
            if (!taken.isEmpty() && !taken.equals(node.path())) {
                return "path";
            }
        }
        boolean swap = waySwap(node, skills);
        if (node.inGroup() && !swap) {
            for (SkillNode other : nodes.values()) {
                if (other != node && other.clazz() == node.clazz() && node.group().equals(other.group())
                        && skills.levelOf(other.id()) > 0) {
                    return "choice";
                }
            }
        }
        if (!swap && lockedOut(node, skills)) {
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

    /**
     * True when a class is fully learned: every node that can still be reached
     * (not shut by a choice, a lock or the other way) is at its top level.
     */
    public boolean isComplete(PlayerSkills skills, SkillClass clazz) {
        if (skills == null || clazz == null || clazz.isShared() || !skills.hasUnlocked(clazz)) {
            return false;
        }
        return skills.as(clazz, () -> {
            List<SkillNode> own = ofClass(clazz);
            if (own.isEmpty()) {
                return false;
            }
            java.util.Set<String> reachable = new java.util.HashSet<>();
            boolean changed = true;
            while (changed) {
                changed = false;
                for (SkillNode node : own) {
                    if (reachable.contains(node.id())) {
                        continue;
                    }
                    if (skills.levelOf(node.id()) > 0) {
                        reachable.add(node.id());
                        changed = true;
                        continue;
                    }
                    String why = blockedReason(node.id(), skills);
                    if ("choice".equals(why) || "lockedby".equals(why) || "path".equals(why) || "class".equals(why)) {
                        continue;   // shut for good
                    }
                    boolean open = true;
                    for (List<SkillNode.Need> any : node.needs()) {
                        boolean term = false;
                        for (SkillNode.Need need : any) {
                            SkillNode other = get(need.id());
                            if (skills.levelOf(need.id()) >= need.level()
                                    || (other != null && reachable.contains(need.id()) && other.maxLevel() >= need.level())) {
                                term = true;
                                break;
                            }
                        }
                        if (!term) {
                            open = false;
                            break;
                        }
                    }
                    if (open) {
                        reachable.add(node.id());
                        changed = true;
                    }
                }
            }
            for (String id : reachable) {
                SkillNode node = get(id);
                if (node != null && skills.levelOf(id) < node.maxLevel()) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Points a class costs in all, on the way the player has started (or the first one):
     * the tree is filled up on a copy of their levels and the cost added up.
     */
    public int pointsToComplete(PlayerSkills skills, SkillClass clazz) {
        if (skills == null || clazz == null || clazz.isShared()) {
            return 0;
        }
        PlayerSkills copy = new PlayerSkills();
        copy.chosenClass = clazz.name();
        for (SkillNode node : ofClass(clazz)) {
            int level = skills.levelIn(clazz, node.id());
            if (level > 0) {
                copy.levels.put(node.id(), level);
            }
        }
        copy.award(1_000_000);
        List<SkillNode> own = ofClass(clazz);
        for (int guard = 0; guard < 2000 && !isComplete(copy, clazz); guard++) {
            boolean any = false;
            for (SkillNode node : own) {
                if (copy.levelOf(node.id()) < node.maxLevel() && unlock(node.id(), copy)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                break;
            }
        }
        return pointsSpent(copy, clazz);
    }

    /** Points put into one class (its levels must be the played ones, see {@link PlayerSkills#as}). */
    public int pointsSpent(PlayerSkills skills, SkillClass clazz) {
        int sum = 0;
        for (SkillNode node : ofClass(clazz)) {
            int level = skills.levelOf(node.id());
            for (int l = 0; l < level; l++) {
                sum += node.costFor(l);
            }
        }
        return sum;
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

    /** The legendary crate gain "way swap" of each class. */
    public static String waySwapGain(SkillClass clazz) {
        if (clazz == null) {
            return null;
        }
        return switch (clazz) {
            case WARRIOR -> "wegtausch_w";
            case ARCHER -> "wegtausch_a";
            case GUARDIAN -> "wegtausch_g";
            case ENGINEER -> "wegtausch_e";
            case SUMMONER -> "wegtausch_s";
            default -> null;
        };
    }

    /**
     * Way swap (legendary crate gain): inside the way a player took, nodes shut by the
     * choice of the other subclass may be learned after all. Ultimates stay one per player.
     */
    public boolean waySwap(SkillNode node, PlayerSkills skills) {
        String gain = waySwapGain(node.clazz());
        if (gain == null || skills == null || skills.gainStage(node.clazz(), gain) <= 0
                || node.effect().target() == SkillEffect.Target.ULTIMATE || node.onTrunk()) {
            return false;
        }
        String taken = skills.pathTaken(this);
        return !taken.isEmpty() && taken.equals(node.path());
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

    /**
     * The subclass a node belongs to: a subclass node itself, or a node whose
     * requirement names only nodes of that subclass. Shared nodes and hybrids
     * that need both subclasses give an empty string.
     */
    public String laneOf(String id) {
        return laneOf(id, new java.util.HashMap<>());
    }

    private String laneOf(String id, Map<String, String> memo) {
        String known = memo.get(id);
        if (known != null) {
            return known;
        }
        memo.put(id, "");
        SkillNode node = nodes.get(id);
        String lane = "";
        if (node != null && SUBCLASS_NODES.containsKey(id) && node.clazz() != SkillClass.CORE) {
            lane = SUBCLASS_NODES.get(id);
        } else if (node != null) {
            boolean conflict = false;
            for (List<SkillNode.Need> any : node.needs()) {
                String term = null;
                boolean same = true;
                for (SkillNode.Need need : any) {
                    String l = laneOf(need.id(), memo);
                    if (term == null) {
                        term = l;
                    } else if (!term.equals(l)) {
                        same = false;
                    }
                }
                if (same && term != null && !term.isEmpty()) {
                    if (lane.isEmpty()) {
                        lane = term;
                    } else if (!lane.equals(term)) {
                        conflict = true;
                    }
                }
            }
            if (conflict) {
                lane = "";
            }
        }
        memo.put(id, lane);
        return lane;
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
        skills.remember(nodeId);
        return true;
    }

    /**
     * A lost night: the player's newest learned levels go, and the points spent on
     * them with them. Saves from before the history was kept lose the deepest
     * level that nothing else learned depends on. Returns one id per level lost.
     */
    public List<String> loseLast(PlayerSkills skills, int count) {
        List<String> taken = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = skills.takeLast();
            if (id == null) {
                id = deepestLeaf(skills);
            }
            if (id == null) {
                break;
            }
            int level = skills.levelOf(id);
            skills.setLevel(id, level - 1);
            SkillNode node = get(id);
            if (node != null) {
                skills.lost += node.costFor(level - 1);
            }
            taken.add(id);
        }
        return taken;
    }

    private String deepestLeaf(PlayerSkills skills) {
        String best = null;
        int bestRow = -1;
        for (SkillNode node : nodes.values()) {
            int level = skills.levelOf(node.id());
            if (level <= 0 || node.clazz() != skills.clazz()) {
                continue;
            }
            skills.setLevel(node.id(), level - 1);
            boolean free = true;
            for (SkillNode other : nodes.values()) {
                if (other != node && skills.levelOf(other.id()) > 0 && !needsMet(other, skills)) {
                    free = false;
                    break;
                }
            }
            skills.setLevel(node.id(), level);
            if (free && node.row() > bestRow) {
                bestRow = node.row();
                best = node.id();
            }
        }
        return best;
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
        String cross = CROSS_TRAPS.get(kind);
        if (cross != null && skills.levelOf(cross) > 0) {
            own = Math.max(own, 2);   // borrowed from the other way, at tier 2
        }
        if (own <= 0) {
            return 0;
        }
        String boost = TRAP_BOOSTS.get(kind);
        SkillNode boostNode = boost == null ? null : nodes.get(boost);
        int extra = boostNode == null ? 0 : (int) Math.round(boostNode.bonusAt(skills.levelOf(boost)));
        return Math.min(TrapKind.MAX_TIER, own + extra);
    }
}
