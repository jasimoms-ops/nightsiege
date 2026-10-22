package com.mki.nightsiege.core;

import java.util.HashMap;
import java.util.Map;

/** One player's own class, experience and learned levels. */
public class PlayerSkills {

    /** Empty until the player commits to a class. */
    public String chosenClass = "";
    public int points;
    /** True once maxing the ultimate has paid out the infernite upgrade template. */
    public boolean templateGiven;
    public int earned;
    /** Points this player has put into the shared core tree. */
    public int donated;
    public Map<String, Integer> levels = new HashMap<>();
    /** True once the player has used their one free class change at the core. */
    public boolean classResetUsed;

    public SkillClass clazz() {
        return SkillClass.byName(chosenClass);
    }

    public boolean hasClass() {
        return clazz() != null;
    }

    public int points() {
        return points;
    }

    public int levelOf(String nodeId) {
        if (levels == null || nodeId == null) {
            return 0;
        }
        Integer value = levels.get(nodeId);
        return value == null ? 0 : Math.max(0, value);
    }

    public void setLevel(String nodeId, int level) {
        if (levels == null) {
            levels = new HashMap<>();
        }
        levels.put(nodeId, Math.max(0, level));
    }

    public void award(int amount) {
        if (amount <= 0) {
            return;
        }
        points += amount;
        earned += amount;
    }

    public void spend(int amount) {
        points = Math.max(0, points - amount);
    }

    /** Which path of its class this player has committed to, or empty. */
    public String pathTaken(SkillTree tree) {
        if (levels == null) {
            return "";
        }
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            SkillNode node = tree.get(entry.getKey());
            if (node != null && !node.onTrunk()) {
                return node.path();
            }
        }
        return "";
    }

    /**
     * The one free class change: the class is open again and every point earned
     * comes back (minus what went into the core). Learned levels are gone.
     */
    public void changeClass() {
        points = Math.max(0, earned - donated);
        chosenClass = "";
        classResetUsed = true;
        if (levels != null) {
            levels.clear();
        }
    }

    public void reset() {
        templateGiven = false;
        points = 0;
        earned = 0;
        donated = 0;
        chosenClass = "";
        if (levels != null) {
            levels.clear();
        }
    }
}
