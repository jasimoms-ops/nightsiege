package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skill catalogue: four branches, each with a root that can be taken
 * straight away and deeper nodes behind it.
 *
 * <p>This is the frame, not the balance. The numbers are deliberately small so
 * the tree pays off over many sieges instead of deciding a single night.</p>
 */
public final class SkillTree {

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
        // --- Nahkampf
        list.add(new SkillNode("strength", SkillBranch.MELEE, SkillEffect.ATTACK_DAMAGE,
                10, 3, 1, 0.15, null, 0));
        list.add(new SkillNode("swift_blade", SkillBranch.MELEE, SkillEffect.ATTACK_SPEED,
                10, 4, 2, 0.04, "strength", 3));

        // --- Fernkampf. Beide Knoten sind vorerst nur beschrieben, nicht aktiv.
        list.add(new SkillNode("steady_aim", SkillBranch.RANGED, SkillEffect.ARROW_SPEED,
                8, 3, 1, 0.05, null, 0));
        list.add(new SkillNode("double_shot", SkillBranch.RANGED, SkillEffect.ARROW_DOUBLE,
                5, 8, 4, 0.10, "steady_aim", 4));

        // --- Zaehigkeit
        list.add(new SkillNode("vitality", SkillBranch.TOUGHNESS, SkillEffect.MAX_HEALTH,
                10, 2, 1, 0.5, null, 0));
        list.add(new SkillNode("toughness", SkillBranch.TOUGHNESS, SkillEffect.ARMOR,
                10, 3, 1, 0.25, "vitality", 3));
        list.add(new SkillNode("agility", SkillBranch.TOUGHNESS, SkillEffect.MOVEMENT_SPEED,
                8, 4, 2, 0.002, "vitality", 5));

        // --- Basis. Wirkt auf die Belagerung selbst, nicht auf Spielerwerte.
        list.add(new SkillNode("watchtower", SkillBranch.BASE, SkillEffect.SPAWN_DISTANCE,
                8, 3, 1, 1.0, null, 0));
        list.add(new SkillNode("fortified", SkillBranch.BASE, SkillEffect.WAVE_DELAY,
                8, 4, 2, 0.5, "watchtower", 3));
        return list;
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }

    public List<SkillNode> all() {
        return new ArrayList<>(nodes.values());
    }

    /** Every node of one branch, in display order. */
    public List<SkillNode> ofBranch(SkillBranch branch) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.branch() == branch) {
                out.add(node);
            }
        }
        return out;
    }

    /** Why a node cannot be taken right now, or null when it can. */
    public String blockedReason(String nodeId, SkillState skills) {
        SkillNode node = get(nodeId);
        if (node == null) {
            return "unknown";
        }
        int level = skills.levelOf(nodeId);
        if (level >= node.maxLevel()) {
            return "maxed";
        }
        if (node.requires() != null && skills.levelOf(node.requires()) < node.requiresLevel()) {
            return "locked";
        }
        if (skills.points() < node.costFor(level)) {
            return "points";
        }
        return null;
    }

    public boolean canUnlock(String nodeId, SkillState skills) {
        return blockedReason(nodeId, skills) == null;
    }

    /** Spends the points and raises the level. Returns false when not allowed. */
    public boolean unlock(String nodeId, SkillState skills) {
        if (!canUnlock(nodeId, skills)) {
            return false;
        }
        SkillNode node = get(nodeId);
        int level = skills.levelOf(nodeId);
        skills.spend(node.costFor(level));
        skills.setLevel(nodeId, level + 1);
        return true;
    }

    /** Total bonus across all nodes for one effect. */
    public double totalBonus(SkillEffect effect, SkillState skills) {
        double sum = 0.0;
        for (SkillNode node : nodes.values()) {
            if (node.effect() == effect) {
                sum += node.bonusAt(skills.levelOf(node.id()));
            }
        }
        return sum;
    }
}
