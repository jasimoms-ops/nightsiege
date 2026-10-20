package com.mki.nightsiege.core;

/**
 * One entry of a skill tree.
 *
 * @param id             stable key, also used in the save file
 * @param clazz          which tree it belongs to
 * @param path           branch inside the class, or empty when it sits on the trunk.
 *                       Two different paths of one class lock each other out.
 * @param effect         what it changes
 * @param maxLevel       how often it can be taken
 * @param baseCost       points for the first level
 * @param costStep       extra points each further level costs
 * @param amountPerLevel how much the effect grows per level
 * @param requires       id of the node that must be learned first, or null
 * @param requiresLevel  level that node needs
 * @param icon           item id drawn in the node's slot
 * @param row            position inside its column, starting at 0
 */
public record SkillNode(String id, SkillClass clazz, String path, SkillEffect effect, int maxLevel,
                        int baseCost, int costStep, double amountPerLevel,
                        String requires, int requiresLevel, String icon, int row) {

    /** Points needed to go from the given level to the next one. */
    public int costFor(int currentLevel) {
        return baseCost + costStep * Math.max(0, currentLevel);
    }

    public double bonusAt(int level) {
        return amountPerLevel * Math.max(0, Math.min(maxLevel, level));
    }

    public boolean onTrunk() {
        return path == null || path.isBlank();
    }

    public String translationKey() {
        return "nightsiege.skill." + id;
    }
}
