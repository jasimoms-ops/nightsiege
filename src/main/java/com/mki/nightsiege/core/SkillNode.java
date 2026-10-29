package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of a skill tree.
 *
 * @param id             stable key, also used in the save file
 * @param clazz          which tree it belongs to
 * @param path           branch inside the class (a path or, for the warrior, a way),
 *                       or empty when it sits on the trunk. Two different paths of one
 *                       class lock each other out.
 * @param effect         what it changes
 * @param maxLevel       how often it can be taken
 * @param baseCost       points for the first level
 * @param costStep       extra points each further level costs
 * @param amountPerLevel how much the effect grows per level
 * @param requires       what must be learned first, or null. A small expression:
 *                       {@code a|b:2,c} means (a or b at level 2) and c. A name without
 *                       a level needs {@code requiresLevel}.
 * @param requiresLevel  level a named node needs when the expression gives none
 * @param icon           item id drawn in the node's slot
 * @param row            column from the left (the tier)
 * @param group          choice group: only one node of a group can be taken; empty for none
 * @param locks          nodes that can no longer be taken once this one is (and the other way round)
 * @param col            place from the top inside its column; -1 lets the window decide
 * @param freeLevels     the first levels that cost nothing (the subclass node: level 1 comes with
 *                       the subclass, Simon 29 October 2026); 0 for every other node
 * @param upgradeRequires what the levels after the free ones need besides {@code requires}, or null
 *                       (the subclass node: the first node of its way), same form as {@code requires}
 */
public record SkillNode(String id, SkillClass clazz, String path, SkillEffect effect, int maxLevel,
                        int baseCost, int costStep, double amountPerLevel,
                        String requires, int requiresLevel, String icon, int row,
                        String group, List<String> locks, int col, int freeLevels, String upgradeRequires) {

    /** A node whose every level is paid and has the same requirement. */
    public SkillNode(String id, SkillClass clazz, String path, SkillEffect effect, int maxLevel,
                     int baseCost, int costStep, double amountPerLevel,
                     String requires, int requiresLevel, String icon, int row,
                     String group, List<String> locks, int col) {
        this(id, clazz, path, effect, maxLevel, baseCost, costStep, amountPerLevel,
                requires, requiresLevel, icon, row, group, locks, col, 0, null);
    }

    /** A node without choice group, locks or fixed place (all classes but the warrior). */
    public SkillNode(String id, SkillClass clazz, String path, SkillEffect effect, int maxLevel,
                     int baseCost, int costStep, double amountPerLevel,
                     String requires, int requiresLevel, String icon, int row) {
        this(id, clazz, path, effect, maxLevel, baseCost, costStep, amountPerLevel,
                requires, requiresLevel, icon, row, "", List.of(), -1);
    }

    /** One named node and the level it needs. */
    public record Need(String id, int level) {
    }

    /** Points needed to go from the given level to the next one (a free level costs nothing). */
    public int costFor(int currentLevel) {
        if (isFreeAt(currentLevel)) {
            return 0;
        }
        return baseCost + costStep * Math.max(0, currentLevel);
    }

    /** True when the step from this level to the next one is a free one. */
    public boolean isFreeAt(int currentLevel) {
        return currentLevel >= 0 && currentLevel < freeLevels;
    }

    /** What the paid levels after the free ones need, as alternatives that must all be met (empty: nothing). */
    public List<List<Need>> upgradeNeeds() {
        return parse(upgradeRequires, requiresLevel);
    }

    public double bonusAt(int level) {
        return amountPerLevel * Math.max(0, Math.min(maxLevel, level));
    }

    public boolean onTrunk() {
        return path == null || path.isBlank();
    }

    public boolean inGroup() {
        return group != null && !group.isBlank();
    }

    public List<String> locksList() {
        return locks == null ? List.of() : locks;
    }

    /** The requirement as a list of alternatives that must all be met. */
    public List<List<Need>> needs() {
        return parse(requires, requiresLevel);
    }

    /** Reads a requirement expression ({@code a|b:2,c}); a name without a level needs {@code requiresLevel}. */
    private static List<List<Need>> parse(String requires, int requiresLevel) {
        List<List<Need>> all = new ArrayList<>();
        if (requires == null || requires.isBlank()) {
            return all;
        }
        for (String term : requires.split(",")) {
            List<Need> any = new ArrayList<>();
            for (String option : term.split("\\|")) {
                String s = option.trim();
                if (s.isEmpty()) {
                    continue;
                }
                int colon = s.indexOf(':');
                if (colon > 0) {
                    int level;
                    try {
                        level = Integer.parseInt(s.substring(colon + 1).trim());
                    } catch (NumberFormatException e) {
                        level = Math.max(1, requiresLevel);
                    }
                    any.add(new Need(s.substring(0, colon).trim(), level));
                } else {
                    any.add(new Need(s, Math.max(1, requiresLevel)));
                }
            }
            if (!any.isEmpty()) {
                all.add(any);
            }
        }
        return all;
    }

    public String translationKey() {
        return "nightsiege.skill." + id;
    }
}
