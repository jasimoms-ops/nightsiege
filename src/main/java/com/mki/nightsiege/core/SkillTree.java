package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill catalogue.
 *
 * <p>Four player classes and one shared core tree. A class usually has a short
 * trunk and two paths behind it; once a player puts a point into one path, the
 * other is closed for good.</p>
 */
public final class SkillTree {

    public static final String PATH_SWIFT = "swift";
    public static final String PATH_HEAVY = "heavy";
    public static final String PATH_BERSERKER = "berserker";
    public static final String PATH_BLADE = "blade";
    public static final String PATH_SHIELD = "shield";
    public static final String PATH_BULWARK = "bulwark";
    public static final String PATH_MEDIC = "medic";

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

        // ---------------- Archer ----------------
        // Trunk. Both paths grow out of this one.
        list.add(new SkillNode("archer_focus", SkillClass.ARCHER, "", SkillEffect.ARROW_DAMAGE,
                3, 2, 1, 0.5, null, 0, "minecraft:bow", 0));

        // Path one: loose arrows faster. A half drawn bow still hits full force.
        list.add(new SkillNode("swift_draw", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DRAW_ASSIST,
                4, 3, 2, 0.22, "archer_focus", 1, "minecraft:feather", 1));
        list.add(new SkillNode("quick_hands", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DRAW_ASSIST,
                4, 5, 3, 0.07, "swift_draw", 4, "minecraft:sugar", 2));
        list.add(new SkillNode("double_shot", SkillClass.ARCHER, PATH_SWIFT, SkillEffect.DOUBLE_SHOT,
                1, 25, 0, 1.0, "quick_hands", 4, "minecraft:arrow", 3));

        // Path two: slower, but every arrow lands like a hammer.
        list.add(new SkillNode("heavy_draw", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE,
                5, 3, 2, 1.2, "archer_focus", 1, "minecraft:iron_ingot", 1));
        list.add(new SkillNode("piercing", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.ARROW_DAMAGE,
                4, 5, 3, 2.0, "heavy_draw", 5, "minecraft:spectral_arrow", 2));
        list.add(new SkillNode("power_shot", SkillClass.ARCHER, PATH_HEAVY, SkillEffect.POWER_SHOT,
                3, 25, 10, 1.0, "piercing", 4, "minecraft:dragon_breath", 3));

        // ---------------- Warrior: one trunk, three paths ----------------
        list.add(new SkillNode("warrior_drill", SkillClass.WARRIOR, "", SkillEffect.ATTACK_DAMAGE,
                3, 2, 1, 0.5, null, 0, "minecraft:iron_sword", 0));
        // Berserker: hits harder the closer to death, drinks blood, spins.
        list.add(new SkillNode("bloodlust", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.RAGE,
                4, 3, 2, 0.15, "warrior_drill", 1, "minecraft:redstone", 1));
        list.add(new SkillNode("blood_drinker", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.LIFESTEAL,
                4, 5, 3, 0.05, "bloodlust", 4, "minecraft:ghast_tear", 2));
        list.add(new SkillNode("whirlwind", SkillClass.WARRIOR, PATH_BERSERKER, SkillEffect.WHIRLWIND,
                3, 25, 10, 1.0, "blood_drinker", 4, "minecraft:iron_axe", 3));
        // Blademaster: precise strikes, champion hunter, finisher.
        list.add(new SkillNode("keen_edge", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.CRIT_CHANCE,
                4, 3, 2, 0.08, "warrior_drill", 1, "minecraft:flint", 1));
        list.add(new SkillNode("champion_bane", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.CHAMPION_BANE,
                4, 5, 3, 0.25, "keen_edge", 4, "minecraft:golden_sword", 2));
        list.add(new SkillNode("execute", SkillClass.WARRIOR, PATH_BLADE, SkillEffect.EXECUTE,
                3, 25, 10, 1.0, "champion_bane", 4, "minecraft:diamond_sword", 3));
        // Shieldbearer: armour, punishes blocked hits, rams.
        list.add(new SkillNode("iron_guard", SkillClass.WARRIOR, PATH_SHIELD, SkillEffect.ARMOR,
                4, 3, 2, 1.5, "warrior_drill", 1, "minecraft:iron_chestplate", 1));
        list.add(new SkillNode("riposte", SkillClass.WARRIOR, PATH_SHIELD, SkillEffect.COUNTER,
                4, 5, 3, 1.5, "iron_guard", 4, "minecraft:shield", 2));
        list.add(new SkillNode("shield_charge", SkillClass.WARRIOR, PATH_SHIELD, SkillEffect.SHIELD_CHARGE,
                3, 25, 10, 1.0, "riposte", 4, "minecraft:anvil", 3));

        // ---------------- Guardian: one trunk, two paths ----------------
        list.add(new SkillNode("vitality", SkillClass.GUARDIAN, "", SkillEffect.MAX_HEALTH,
                4, 2, 1, 2.0, null, 0, "minecraft:golden_apple", 0));
        // Bulwark: stands like a wall and draws the attack.
        list.add(new SkillNode("toughness", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.ARMOR,
                4, 3, 2, 1.5, "vitality", 1, "minecraft:iron_chestplate", 1));
        list.add(new SkillNode("steadfast", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.KNOCKBACK_RESIST,
                4, 5, 3, 0.15, "toughness", 4, "minecraft:netherite_ingot", 2));
        list.add(new SkillNode("taunt", SkillClass.GUARDIAN, PATH_BULWARK, SkillEffect.TAUNT,
                3, 25, 10, 1.0, "steadfast", 4, "minecraft:goat_horn", 3));
        // Medic: keeps everyone standing.
        list.add(new SkillNode("field_dressing", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.HEAL_AURA,
                4, 3, 2, 0.5, "vitality", 1, "minecraft:glistering_melon_slice", 1));
        list.add(new SkillNode("triage", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.HEAL_AURA,
                4, 5, 3, 0.5, "field_dressing", 4, "minecraft:enchanted_golden_apple", 2));
        list.add(new SkillNode("guardian_angel", SkillClass.GUARDIAN, PATH_MEDIC, SkillEffect.GUARDIAN_ANGEL,
                3, 25, 10, 1.0, "triage", 4, "minecraft:totem_of_undying", 3));

        // ---------------- Engineer: one chain of four traps ----------------
        // Each node unlocks its trap; the next trap needs the one before at tier 2.
        list.add(new SkillNode(TrapKind.SPIKE.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 3, 2, 1.0, null, 0, "nightsiege:spike_trap", 0));
        list.add(new SkillNode(TrapKind.SNARE.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 5, 3, 1.0, TrapKind.SPIKE.id(), 2, "nightsiege:snare_trap", 1));
        list.add(new SkillNode(TrapKind.MINE.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 8, 4, 1.0, TrapKind.SNARE.id(), 2, "nightsiege:blast_mine", 2));
        list.add(new SkillNode(TrapKind.TURRET.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 15, 8, 1.0, TrapKind.MINE.id(), 2, "nightsiege:arrow_turret", 3));
        list.add(new SkillNode(TrapKind.FLAME.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 18, 8, 1.0, TrapKind.TURRET.id(), 2, "nightsiege:flame_trap", 4));
        list.add(new SkillNode(TrapKind.POISON.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 22, 10, 1.0, TrapKind.FLAME.id(), 2, "nightsiege:poison_trap", 5));
        list.add(new SkillNode(TrapKind.TESLA.id(), SkillClass.ENGINEER, "", SkillEffect.TRAP,
                TrapKind.MAX_TIER, 30, 12, 1.0, TrapKind.POISON.id(), 2, "nightsiege:tesla_coil", 6));

        // ---------------- Core: shared, paid by donations ----------------
        list.add(new SkillNode("watchtower", SkillClass.CORE, "", SkillEffect.SPAWN_DISTANCE,
                8, 3, 1, 1.0, null, 0, "minecraft:spyglass", 0));
        list.add(new SkillNode("fortified", SkillClass.CORE, "", SkillEffect.WAVE_DELAY,
                8, 4, 2, 0.5, "watchtower", 3, "minecraft:obsidian", 1));
        return list;
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
        return Math.min(TrapKind.MAX_TIER, skills.levelOf(kind.id()));
    }
}
