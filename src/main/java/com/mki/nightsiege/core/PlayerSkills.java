package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's profiles, experience and learned levels. A profile is one tree of a
 * class with its own levels, ultimate and subclass; a player may own up to four
 * profiles of a class (one per subclass). Points are one account for all profiles.
 */
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
    /** Every level bought in the played profile, oldest first (one entry per level): a lost night takes the newest. */
    public List<String> history = new ArrayList<>();
    /** The bought-level history of the other profiles, by profile key (see {@link #history}). */
    public Map<String, List<String>> classHistory = new HashMap<>();
    /** Points gone with skills lost in lost nights; the class change does not give them back. */
    public int lost;
    /** Survived waves not yet turned into a point (see {@link WaveXp}). */
    public int waveCredit;
    /** Part of a point left over when the point factor is not a whole number. */
    public double pointFraction;
    /** Number of the last siege night this player stood in, or -1 for none. */
    public int lastNight = -1;
    /**
     * Learned levels of the player's other profiles, by profile key. The profile
     * being played keeps its levels in {@link #levels}, so every skill effect only
     * ever sees the profile of the night.
     */
    public Map<String, Map<String, Integer>> classLevels = new HashMap<>();
    /**
     * The player's profiles in the order they were opened, by profile key. The first
     * profile of a class is keyed by the class name (as in older saves), more profiles
     * of the same class by "CLASS#2", "CLASS#3", "CLASS#4". Each profile has its own
     * tree and its own subclass.
     */
    public List<String> unlocked = new ArrayList<>();
    /** Crate gains per class: gain id to the stage reached (see the reward crates). */
    public Map<String, Map<String, Integer>> gains = new HashMap<>();
    /** The gain abilities on the gain keys per class, key by key (balance update 2, 29 October 2026). */
    public Map<String, java.util.List<String>> gainKeyChoice = new HashMap<>();
    /** Souls the summoner's soul vessel (crate gain) carries into the next night. */
    public int keptSouls;
    /**
     * Unlocks earned and not used yet: one per profile whose ultimate reached its top
     * level. One unlock opens a new class or a further subclass of an own class.
     */
    public int unlocksFree;
    /** Profiles whose ultimate has already paid out its unlock (each only once). */
    public List<String> ultRewarded = new ArrayList<>();
    /** The subclass a profile was opened for (profile key to subclass), until and after it is learned. */
    public Map<String, String> planned = new HashMap<>();

    /** Version of the profile rules this player was moved to (0 = before the profiles). */
    public int profileVersion;
    public static final int PROFILE_VERSION = 1;

    /**
     * 1 once level 1 of the subclass node is the free one for this player (the subclass first, Simon
     * 29 October 2026): set by {@link SkillTree#migrateFreeSubclass} for older saves, and by every free
     * level given since. A player still at 0 who holds a subclass node bought its level 1.
     */
    public int freeSubclassVersion;
    public static final int FREE_SUBCLASS_VERSION = 1;

    /** At most this many profiles per class: one per subclass. */
    public static final int MAX_PROFILES = 4;

    /** The played profile's class. */
    public SkillClass clazz() {
        return classOfKey(chosenClass);
    }

    /** The played profile's class name (e.g. "WARRIOR"), or empty: for config factors and logs. */
    public String className() {
        SkillClass c = clazz();
        return c == null ? "" : c.name();
    }

    /** The class of a profile key ("WARRIOR" or "WARRIOR#2"). */
    public static SkillClass classOfKey(String key) {
        if (key == null) {
            return null;
        }
        int hash = key.indexOf('#');
        return SkillClass.byName(hash < 0 ? key : key.substring(0, hash));
    }

    /** The profile keys, oldest first (older saves: just the chosen class). */
    public List<String> unlockedClasses() {
        if (unlocked == null) {
            unlocked = new ArrayList<>();
        }
        if (unlocked.isEmpty() && hasClass()) {
            unlocked.add(chosenClass);
        }
        return unlocked;
    }

    /** The profile keys, oldest first. */
    public List<String> profiles() {
        return unlockedClasses();
    }

    public boolean hasProfile(String key) {
        return key != null && unlockedClasses().contains(key);
    }

    /** The profile keys of one class, oldest first. */
    public List<String> profilesOf(SkillClass c) {
        List<String> out = new ArrayList<>();
        if (c != null) {
            for (String key : unlockedClasses()) {
                if (classOfKey(key) == c) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    /** The played profile if it is of this class, else the oldest profile of the class, or null. */
    public String profileFor(SkillClass c) {
        if (c == null) {
            return null;
        }
        if (c == clazz() && hasProfile(chosenClass)) {
            return chosenClass;
        }
        List<String> own = profilesOf(c);
        return own.isEmpty() ? null : own.get(0);
    }

    /** True when the player has at least one profile of this class. */
    public boolean hasUnlocked(SkillClass c) {
        return c != null && !profilesOf(c).isEmpty();
    }

    /** True once a first unlock was earned (an ultimate at its top level). */
    public boolean earnedUnlock() {
        return !ultRewardedList().isEmpty();
    }

    public List<String> ultRewardedList() {
        if (ultRewarded == null) {
            ultRewarded = new ArrayList<>();
        }
        return ultRewarded;
    }

    /** The subclass a profile was opened for, or empty. */
    public String plannedSubclass(String key) {
        String sub = planned == null || key == null ? null : planned.get(key);
        return sub == null ? "" : sub;
    }

    /**
     * The subclass node fixed for a class with the armour stand window (26 October 2026): the
     * planned subclass of the class's profile (see {@link #profileFor}), as its node id, or empty.
     */
    public String fixedSubOf(SkillClass c) {
        String key = profileFor(c);
        SkillTree.SubclassInfo info = SkillTree.subclassByKey(c, plannedSubclass(key));
        return info == null ? "" : info.node();
    }

    /** Fixes the subclass of a class's profile by its node id (the armour stand window); level 1 of it comes free. */
    public void fixSub(SkillClass c, String nodeId) {
        SkillTree.SubclassInfo info = SkillTree.subclassByNode(nodeId);
        String key = profileFor(c);
        if (info == null || info.clazz() != c || key == null) {
            return;
        }
        planSubclass(key, info.key());
    }

    /** Fixes the subclass of one profile and gives it level 1 of the subclass node (see {@link #grantFreeSubclass}). */
    public void planSubclass(String key, String subclass) {
        if (key == null || subclass == null || subclass.isBlank()) {
            return;
        }
        if (planned == null) {
            planned = new HashMap<>();
        }
        planned.put(key, subclass);
        grantFreeSubclass(key);
    }

    /**
     * Level 1 of the subclass node comes with the subclass (Simon, 29 October 2026: "stufe 1
     * geschenkt"): a profile with a fixed subclass that has not learned its node gets level 1, at no
     * cost and outside the history (a lost night never takes it). Nothing happens while the profile
     * holds a subclass node already. Returns true when the level was given now.
     */
    public boolean grantFreeSubclass(String key) {
        SkillClass c = classOfKey(key);
        SkillTree.SubclassInfo info = c == null ? null : SkillTree.subclassByKey(c, plannedSubclass(key));
        if (info == null || !hasProfile(key)) {
            return false;
        }
        for (SkillTree.SubclassInfo other : SkillTree.subclassInfosOf(c)) {
            if (levelInProfile(key, other.node()) > 0) {
                return false;
            }
        }
        if (key.equals(chosenClass)) {
            setLevel(info.node(), 1);
        } else {
            if (classLevels == null) {
                classLevels = new HashMap<>();
            }
            classLevels.computeIfAbsent(key, k -> new HashMap<>()).put(info.node(), 1);
        }
        freeSubclassVersion = FREE_SUBCLASS_VERSION;
        return true;
    }

    /** {@link #grantFreeSubclass} for every profile. Returns how many got their level 1 now. */
    public int grantFreeSubclasses() {
        int given = 0;
        for (String key : new ArrayList<>(unlockedClasses())) {
            if (grantFreeSubclass(key)) {
                given++;
            }
        }
        return given;
    }

    /** Takes the oldest purchase of a node out of the played profile's history (it was paid back). */
    public void forgetPurchase(String nodeId) {
        if (history != null) {
            history.remove(nodeId);
        }
    }

    /** A learned level in the played profile, or in the class's profile (see {@link #profileFor}). */
    public int levelIn(SkillClass c, String nodeId) {
        if (c == null || c == clazz()) {
            return levelOf(nodeId);
        }
        return levelInProfile(profileFor(c), nodeId);
    }

    /** A learned level in one profile, played or not. */
    public int levelInProfile(String key, String nodeId) {
        if (key == null) {
            return 0;
        }
        if (key.equals(chosenClass)) {
            return levelOf(nodeId);
        }
        Map<String, Integer> other = classLevels == null ? null : classLevels.get(key);
        Integer value = other == null || nodeId == null ? null : other.get(nodeId);
        return value == null ? 0 : Math.max(0, value);
    }

    /** Plays a profile of this class: the one already played, else the oldest one. */
    public boolean switchTo(SkillClass c) {
        if (c == null || c.isShared()) {
            return false;
        }
        return switchToProfile(profileFor(c));
    }

    /** Makes a profile the one played; its levels move into {@link #levels}. */
    public boolean switchToProfile(String key) {
        SkillClass c = classOfKey(key);
        if (c == null || c.isShared() || !hasProfile(key)) {
            return false;
        }
        if (key.equals(chosenClass)) {
            return true;
        }
        if (classLevels == null) {
            classLevels = new HashMap<>();
        }
        if (classHistory == null) {
            classHistory = new HashMap<>();
        }
        if (clazz() != null && chosenClass != null && !chosenClass.isBlank()) {
            classLevels.put(chosenClass, levels == null ? new HashMap<>() : levels);
            classHistory.put(chosenClass, history == null ? new ArrayList<>() : history);
        }
        Map<String, Integer> next = classLevels.remove(key);
        levels = next == null ? new HashMap<>() : next;
        List<String> nextHistory = classHistory.remove(key);
        history = nextHistory == null ? new ArrayList<>() : nextHistory;
        chosenClass = key;
        return true;
    }

    /** Runs something with a profile of this class in place (see {@link #profileFor}), then switches back. */
    public <T> T as(SkillClass c, java.util.function.Supplier<T> action) {
        if (c == null || c == clazz()) {
            return action.get();
        }
        return asProfile(profileFor(c), action);
    }

    /** Runs something with another profile in place (to buy or show its nodes), then switches back. */
    public <T> T asProfile(String key, java.util.function.Supplier<T> action) {
        String before = chosenClass;
        if (key == null || key.equals(before) || !switchToProfile(key)) {
            return action.get();
        }
        try {
            return action.get();
        } finally {
            if (before != null && hasProfile(before)) {
                switchToProfile(before);
            } else {
                // What was played before is no profile (none yet, or a test set-up): park the
                // borrowed profile again and bring back what was there.
                classLevels.put(chosenClass, levels == null ? new HashMap<>() : levels);
                classHistory.put(chosenClass, history == null ? new ArrayList<>() : history);
                String back = before == null ? "" : before;
                Map<String, Integer> oldLevels = back.isEmpty() ? null : classLevels.remove(back);
                List<String> oldHistory = back.isEmpty() ? null : classHistory.remove(back);
                levels = oldLevels == null ? new HashMap<>() : oldLevels;
                history = oldHistory == null ? new ArrayList<>() : oldHistory;
                chosenClass = back;
            }
        }
    }

    /** The key a new profile of this class gets, or null when the class has all its profiles. */
    public String nextProfileKey(SkillClass c) {
        if (c == null || c.isShared()) {
            return null;
        }
        List<String> own = profilesOf(c);
        if (own.size() >= MAX_PROFILES) {
            return null;
        }
        if (!own.contains(c.name())) {
            return c.name();
        }
        for (int n = 2; n <= MAX_PROFILES; n++) {
            if (!own.contains(c.name() + "#" + n)) {
                return c.name() + "#" + n;
            }
        }
        return null;
    }

    /** Opens a new, empty profile of a class (for a subclass, or none). Returns its key, or null. */
    public String addProfile(SkillClass c, String subclass) {
        String key = nextProfileKey(c);
        if (key == null) {
            return null;
        }
        unlockedClasses().add(key);
        if (classLevels == null) {
            classLevels = new HashMap<>();
        }
        classLevels.putIfAbsent(key, new HashMap<>());
        // The subclass comes with its node at level 1 (the root of the profile's tree).
        planSubclass(key, subclass);
        return key;
    }

    /**
     * Plays a profile just opened while no class was played yet (the first class): what it holds
     * already (level 1 of its subclass node) becomes the played levels.
     */
    public void playOpened(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Map<String, Integer> parked = classLevels == null ? null : classLevels.remove(key);
        List<String> parkedHistory = classHistory == null ? null : classHistory.remove(key);
        chosenClass = key;
        if (levels == null) {
            levels = new HashMap<>();
        }
        if (parked != null) {
            for (Map.Entry<String, Integer> e : parked.entrySet()) {
                levels.merge(e.getKey(), e.getValue(), Math::max);
            }
        }
        if (history == null) {
            history = new ArrayList<>();
        }
        if (parkedHistory != null) {
            history.addAll(0, parkedHistory);
        }
    }

    /** Opens the first profile of a class (nothing happens when the class has one already). */
    public void unlockClass(SkillClass c) {
        if (c == null || c.isShared() || hasUnlocked(c)) {
            return;
        }
        addProfile(c, "");
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
        if (classHistory != null) {
            for (List<String> list : classHistory.values()) {
                if (list != null) {
                    list.replaceAll(id -> renames.getOrDefault(id, id));
                }
            }
        }
    }

    /** True when any of these node ids is learned or in the history, in any class. */
    public boolean hasAnyNode(java.util.Collection<String> ids) {
        if (levels != null && levels.keySet().stream().anyMatch(ids::contains)) {
            return true;
        }
        if (classLevels != null && classLevels.values().stream()
                .anyMatch(m -> m != null && m.keySet().stream().anyMatch(ids::contains))) {
            return true;
        }
        if (history != null && history.stream().anyMatch(ids::contains)) {
            return true;
        }
        return classHistory != null && classHistory.values().stream()
                .anyMatch(l -> l != null && l.stream().anyMatch(ids::contains));
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

    /** Forgets every profile but the played class (its key back to the plain class name) and every unlock. */
    private void clearProfiles(boolean keepClass) {
        chosenClass = keepClass ? className() : "";
        if (unlocked != null) {
            unlocked.clear();
        }
        if (classLevels != null) {
            classLevels.clear();
        }
        if (classHistory != null) {
            classHistory.clear();
        }
        if (levels != null) {
            levels.clear();
        }
        if (history != null) {
            history.clear();
        }
        if (ultRewarded != null) {
            ultRewarded.clear();
        }
        if (planned != null) {
            planned.clear();
        }
        unlocksFree = 0;
    }

    /**
     * The one free class change: the class is open again and every point earned
     * comes back (minus what went into the core). Learned levels are gone.
     */
    public void changeClass() {
        points = Math.max(0, earned - donated - lost);
        classResetUsed = true;
        clearProfiles(false);
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
        clearProfiles(true);
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
        clearProfiles(false);
    }
}
