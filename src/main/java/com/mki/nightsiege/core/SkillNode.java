package com.mki.nightsiege.core;

/**
 * One entry of the skill tree.
 *
 * @param id             stable key, also used in the save file
 * @param branch         which of the four branches it sits in
 * @param effect         what it changes
 * @param maxLevel       how often it can be taken
 * @param baseCost       points for the first level
 * @param costStep       extra points each further level costs
 * @param amountPerLevel how much the effect grows per level
 * @param requires       id of the node that must be learned first, or null
 * @param requiresLevel  level that node needs
 */
public record SkillNode(String id, SkillBranch branch, SkillEffect effect, int maxLevel,
                        int baseCost, int costStep, double amountPerLevel,
                        String requires, int requiresLevel) {

    /** Points needed to go from the given level to the next one. */
    public int costFor(int currentLevel) {
        return baseCost + costStep * Math.max(0, currentLevel);
    }

    public double bonusAt(int level) {
        return amountPerLevel * Math.max(0, Math.min(maxLevel, level));
    }

    public String translationKey() {
        return "nightsiege.skill." + id;
    }
}
