package com.mki.nightsiege.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The team's shared skill progress: one pot of points and one set of learned
 * levels for the whole server. Also the shape stored in the save file.
 */
public class SkillState {

    /** Points not yet spent. */
    public int points;
    /** Points earned over the whole run, kept for the status display. */
    public int earned;
    /** Node id to learned level. */
    public Map<String, Integer> levels = new HashMap<>();

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

    /** Wipes everything, used when a lost siege resets the core. */
    public void reset() {
        points = 0;
        earned = 0;
        if (levels != null) {
            levels.clear();
        }
    }
}
