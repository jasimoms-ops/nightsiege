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
    // Warrior
    public static final String PATH_BERSERKER = "berserker";
    public static final String PATH_BLADE = "blade";
    public static final String PATH_SPEAR = "spear";
    public static final String PATH_AXE = "axe";
    // Guardian
    public static final String PATH_BULWARK = "bulwark";
    public static final String PATH_MEDIC = "medic";
    public static final String PATH_SHIELD = "shield";
    public static final String PATH_PALADIN = "paladin";
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
        add(list, "swift_draw", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DRAW_ASSIST, 4, 3, 2, 0.25, "archer_focus", 1, "minecraft:feather", 1);
        add(list, "double_shot", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DOUBLE_SHOT, 1, 15, 0, 1.0, "swift_draw", 4, "minecraft:arrow", 2);
        add(list, "arrow_storm", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.ARROW_STORM, 3, 25, 10, 1.0, "double_shot", 1, "minecraft:blaze_rod", 3);
        // Heavy: slow bow, hard arrows, a crushing shot.
        add(list, "heavy_draw", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE, 5, 3, 2, 1.2, "archer_focus", 1, "minecraft:iron_ingot", 1);
        add(list, "piercing", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE, 4, 5, 3, 2.0, "heavy_draw", 4, "minecraft:spectral_arrow", 2);
        add(list, "power_shot", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.POWER_SHOT, 3, 25, 10, 1.0, "piercing", 4, "minecraft:dragon_breath", 3);
        // Crossbow: heavy bolts that burst on impact, then a fan of bolts.
        add(list, "heavy_bolts", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.CROSSBOW_DAMAGE, 4, 3, 2, 1.5, "archer_focus", 1, "minecraft:crossbow", 1);
        add(list, "shrapnel", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.SHRAPNEL, 4, 5, 3, 0.15, "heavy_bolts", 4, "minecraft:firework_star", 2);
        add(list, "bolt_volley", SkillClass.ARCHER, PATH_CROSSBOW, SkillEffect.BOLT_VOLLEY, 3, 25, 10, 1.0, "shrapnel", 4, "minecraft:firework_rocket", 3);
        // Hunter: marks prey, moves fast, marks a whole pack.
        add(list, "tracker", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.MARK, 4, 3, 2, 0.08, "archer_focus", 1, "minecraft:spyglass", 1);
        add(list, "fleet_foot", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.MOVEMENT_SPEED, 4, 5, 3, 0.006, "tracker", 4, "minecraft:rabbit_foot", 2);
        add(list, "hunt", SkillClass.ARCHER, PATH_HUNTER, SkillEffect.HUNTERS_MARK, 3, 25, 10, 1.0, "fleet_foot", 4, "minecraft:ender_eye", 3);

        // ---------------- Warrior ----------------
        add(list, "warrior_drill", SkillClass.WARRIOR, "", SkillEffect.ATTACK_DAMAGE, 3, 2, 1, 0.5, null, 0, "minecraft:iron_sword", 0);
        add(list, "bloodlust", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.RAGE, 4, 3, 2, 0.15, "warrior_drill", 1, "minecraft:redstone", 1);
        add(list, "blood_drinker", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.LIFESTEAL, 4, 5, 3, 0.05, "bloodlust", 4, "minecraft:ghast_tear", 2);
        add(list, "whirlwind", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.WHIRLWIND, 3, 25, 10, 1.0, "blood_drinker", 4, "minecraft:breeze_rod", 3);
        add(list, "keen_edge", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.CRIT_CHANCE, 4, 3, 2, 0.08, "warrior_drill", 1, "minecraft:flint", 1);
        add(list, "champion_bane", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.CHAMPION_BANE, 4, 5, 3, 0.25, "keen_edge", 4, "minecraft:golden_sword", 2);
        add(list, "execute", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.EXECUTE, 3, 25, 10, 1.0, "champion_bane", 4, "minecraft:diamond_sword", 3);
        // Spear: longer reach, hits pass through, a thrust down a whole line.
        add(list, "long_reach", SkillClass.WARRIOR, PATH_SPEAR, SkillEffect.REACH, 4, 3, 2, 0.4, "warrior_drill", 1, "minecraft:iron_spear", 1);
        add(list, "impale", SkillClass.WARRIOR, PATH_SPEAR, SkillEffect.IMPALE, 4, 5, 3, 0.2, "long_reach", 4, "minecraft:diamond_spear", 2);
        add(list, "lance_thrust", SkillClass.WARRIOR, PATH_SPEAR, SkillEffect.LANCE_THRUST, 3, 25, 10, 1.0, "impale", 4, "minecraft:netherite_spear", 3);
        // Axe: cracks armour, heavy blows, smashes the ground.
        add(list, "sunder", SkillClass.WARRIOR, PATH_AXE, SkillEffect.SUNDER, 4, 3, 2, 0.08, "warrior_drill", 1, "minecraft:iron_axe", 1);
        add(list, "heavy_blow", SkillClass.WARRIOR, PATH_AXE, SkillEffect.HEAVY_BLOW, 4, 5, 3, 1.0, "sunder", 4, "minecraft:diamond_axe", 2);
        add(list, "ground_slam", SkillClass.WARRIOR, PATH_AXE, SkillEffect.GROUND_SLAM, 3, 25, 10, 1.0, "heavy_blow", 4, "minecraft:mace", 3);

        // ---------------- Guardian ----------------
        add(list, "vitality", SkillClass.GUARDIAN, "", SkillEffect.MAX_HEALTH, 4, 2, 1, 2.0, null, 0, "minecraft:golden_apple", 0);
        add(list, "toughness", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.ARMOR, 4, 3, 2, 1.5, "vitality", 1, "minecraft:iron_chestplate", 1);
        add(list, "steadfast", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.KNOCKBACK_RESIST, 4, 5, 3, 0.15, "toughness", 4, "minecraft:netherite_ingot", 2);
        add(list, "taunt", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.TAUNT, 3, 25, 10, 1.0, "steadfast", 4, "minecraft:goat_horn", 3);
        add(list, "field_dressing", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.HEAL_AURA, 4, 3, 2, 0.5, "vitality", 1, "minecraft:glistering_melon_slice", 1);
        add(list, "guardian_angel", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.GUARDIAN_ANGEL, 3, 5, 3, 1.0, "field_dressing", 4, "minecraft:totem_of_undying", 2);
        add(list, "mass_heal", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.MASS_HEAL, 3, 25, 10, 1.0, "guardian_angel", 1, "minecraft:enchanted_golden_apple", 3);
        add(list, "iron_guard", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.ARMOR, 4, 3, 2, 1.5, "vitality", 1, "minecraft:shield", 1);
        add(list, "riposte", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.COUNTER, 4, 5, 3, 1.5, "iron_guard", 4, "minecraft:iron_ingot", 2);
        add(list, "shield_charge", SkillClass.GUARDIAN, PATH_SHIELD, SkillEffect.SHIELD_CHARGE, 3, 25, 10, 1.0, "riposte", 4, "minecraft:anvil", 3);
        add(list, "smite", SkillClass.GUARDIAN, PATH_PALADIN, SkillEffect.SMITE, 4, 3, 2, 0.15, "vitality", 1, "minecraft:golden_sword", 1);
        add(list, "holy_vigor", SkillClass.GUARDIAN, PATH_PALADIN, SkillEffect.LIFESTEAL, 4, 5, 3, 0.04, "smite", 4, "minecraft:glow_berries", 2);
        add(list, "holy_light", SkillClass.GUARDIAN, PATH_PALADIN, SkillEffect.HOLY_LIGHT, 3, 25, 10, 1.0, "holy_vigor", 4, "minecraft:beacon", 3);

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
        if (node.requires() != null && skills.levelOf(node.requires()) < node.requiresLevel()) {
            return "locked";
        }
        if (skills.points() < node.costFor(level)) {
            return "points";
        }
        return null;
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
