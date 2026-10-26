package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    /** Every level bought, oldest first (one entry per level): a lost night takes the newest. */
    public List<String> history = new ArrayList<>();
    /** Points gone with skills lost in lost nights; the class change does not give them back. */
    public int lost;
    /** Survived waves not yet turned into a point (see {@link WaveXp}). */
    public int waveCredit;
    /** Part of a point left over when the point factor is not a whole number. */
    public double pointFraction;
    /** Number of the last siege night this player stood in, or -1 for none. */
    public int lastNight = -1;
    /**
     * Learned levels of the player's other unlocked classes. The class being
     * played keeps its levels in {@link #levels}, so every skill effect only
     * ever sees the class of the night.
     */
    public Map<String, Map<String, Integer>> classLevels = new HashMap<>();
    /** Unlocked classes in the order they were opened; new points go to the last one. */
    public List<String> unlocked = new ArrayList<>();
    /** Crate gains per class: gain id to the stage reached (see the reward crates). */
    public Map<String, Map<String, Integer>> gains = new HashMap<>();
    /** Souls the summoner's soul vessel (crate gain) carries into the next night. */
    public int keptSouls;
    /**
     * The subclass fixed when a class was chosen (class name to subclass node id): it settles
     * the way and the subclass at once, the other three are shut in the tree (Simon, 26 October 2026).
     */
    public Map<String, String> fixedSub = new HashMap<>();

    /** The fixed subclass node of a class, or empty. */
    public String fixedSubOf(SkillClass c) {
        if (c == null || fixedSub == null) {
            return "";
        }
        String id = fixedSub.get(c.name());
        return id == null ? "" : id;
    }

    public void fixSub(SkillClass c, String nodeId) {
        if (c == null || c.isShared() || nodeId == null || nodeId.isBlank()) {
            return;
        }
        if (fixedSub == null) {
            fixedSub = new HashMap<>();
        }
        fixedSub.put(c.name(), nodeId);
    }

    public SkillClass clazz() {
        return SkillClass.byName(chosenClass);
    }

    /** The unlocked classes, oldest first (older saves: just the chosen class). */
    public List<String> unlockedClasses() {
        if (unlocked == null) {
            unlocked = new ArrayList<>();
        }
        if (unlocked.isEmpty() && hasClass()) {
            unlocked.add(clazz().name());
        }
        return unlocked;
    }

    public boolean hasUnlocked(SkillClass c) {
        return c != null && unlockedClasses().contains(c.name());
    }

    /** The class new points go to: the newest unlocked one. */
    public SkillClass levelingClass() {
        List<String> list = unlockedClasses();
        return list.isEmpty() ? clazz() : SkillClass.byName(list.get(list.size() - 1));
    }

    /** A learned level in any class, played or not. */
    public int levelIn(SkillClass c, String nodeId) {
        if (c == null || c == clazz()) {
            return levelOf(nodeId);
        }
        Map<String, Integer> other = classLevels == null ? null : classLevels.get(c.name());
        Integer value = other == null ? null : other.get(nodeId);
        return value == null ? 0 : Math.max(0, value);
    }

    /** Makes an unlocked class the one played; its levels move into {@link #levels}. */
    public boolean switchTo(SkillClass c) {
        if (c == null || c.isShared() || !hasUnlocked(c)) {
            return false;
        }
        SkillClass current = clazz();
        if (current == c) {
            return true;
        }
        if (classLevels == null) {
            classLevels = new HashMap<>();
        }
        if (current != null) {
            classLevels.put(current.name(), levels == null ? new HashMap<>() : levels);
        }
        Map<String, Integer> next = classLevels.remove(c.name());
        levels = next == null ? new HashMap<>() : next;
        chosenClass = c.name();
        return true;
    }

    /** Runs something with another unlocked class in place (to buy or show its nodes), then switches back. */
    public <T> T as(SkillClass c, java.util.function.Supplier<T> action) {
        SkillClass before = clazz();
        if (c == null || c == before || !switchTo(c)) {
            return action.get();
        }
        try {
            return action.get();
        } finally {
            switchTo(before);
        }
    }

    /** Opens the next class: new points go there from now on. */
    public void unlockClass(SkillClass c) {
        if (c == null || c.isShared() || hasUnlocked(c)) {
            return;
        }
        unlockedClasses().add(c.name());
        if (classLevels == null) {
            classLevels = new HashMap<>();
        }
        classLevels.putIfAbsent(c.name(), new HashMap<>());
    }

    /** Moves learned levels to new node ids, in every class (all at once, so two nodes can swap). */
    public void renameNodes(Map<String, String> renames) {
        levels = renamed(levels, renames);
        if (classLevels != null) {
            for (Map.Entry<String, Map<String, Integer>> e : classLevels.entrySet()) {
                e.setValue(renamed(e.getValue(), renames));
            }
        }
        if (history != null) {
            history.replaceAll(id -> renames.getOrDefault(id, id));
        }
    }

    private static Map<String, Integer> renamed(Map<String, Integer> map, Map<String, String> renames) {
        Map<String, Integer> out = new HashMap<>();
        if (map != null) {
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                out.put(renames.getOrDefault(e.getKey(), e.getKey()), e.getValue());
            }
        }
        return out;
    }

    /** The stage of a crate gain in one class's kit (0 = not drawn yet). */
    public int gainStage(SkillClass c, String gainId) {
        if (c == null || gains == null) {
            return 0;
        }
        Map<String, Integer> map = gains.get(c.name());
        Integer value = map == null ? null : map.get(gainId);
        return value == null ? 0 : Math.max(0, value);
    }

    public void setGainStage(SkillClass c, String gainId, int stage) {
        if (c == null) {
            return;
        }
        if (gains == null) {
            gains = new HashMap<>();
        }
        gains.computeIfAbsent(c.name(), k -> new HashMap<>()).put(gainId, Math.max(0, stage));
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

    /** Notes a level just bought, for {@link SkillTree#loseLast}. */
    public void remember(String nodeId) {
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(nodeId);
    }

    /** The newest bought level that is still learned (taken off the history), or null. */
    public String takeLast() {
        while (history != null && !history.isEmpty()) {
            String id = history.remove(history.size() - 1);
            if (levelOf(id) > 0) {
                return id;
            }
        }
        return null;
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
            if (node != null && !node.onTrunk() && node.clazz() == clazz()) {
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
        points = Math.max(0, earned - donated - lost);
        chosenClass = "";
        classResetUsed = true;
        if (fixedSub != null) {
            fixedSub.clear();
        }
        if (unlocked != null) {
            unlocked.clear();
        }
        if (classLevels != null) {
            classLevels.clear();
        }
        if (levels != null) {
            levels.clear();
        }
        if (history != null) {
            history.clear();
        }
    }

    /**
     * The fresh start of the new balancing: class kept, every learned level and
     * every point gone, the class change still open if it was.
     */
    public void restartProgress() {
        points = 0;
        earned = 0;
        donated = 0;
        lost = 0;
        waveCredit = 0;
        pointFraction = 0;
        templateGiven = false;
        keptSouls = 0;
        if (levels != null) {
            levels.clear();
        }
        if (history != null) {
            history.clear();
        }
        if (classLevels != null) {
            classLevels.clear();
        }
        if (unlocked != null) {
            unlocked.clear();
        }
    }

    public void reset() {
        templateGiven = false;
        points = 0;
        earned = 0;
        donated = 0;
        lost = 0;
        waveCredit = 0;
        pointFraction = 0;
        keptSouls = 0;
        chosenClass = "";
        if (fixedSub != null) {
            fixedSub.clear();
        }
        if (levels != null) {
            levels.clear();
        }
        if (history != null) {
            history.clear();
        }
        if (unlocked != null) {
            unlocked.clear();
        }
        if (classLevels != null) {
            classLevels.clear();
        }
    }
}
