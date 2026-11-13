package com.mki.nightsiege.core;

import java.util.List;
import java.util.function.Predicate;

/**
 * The fun mode's lent profile (Update 7, Simon 06.11.: "in dem Modus kann man jede Klasse wählen die man
 * will und die sind immer komplett durchgelevelt, also man kann sich den Skillbaum dann schnell
 * durchskillen und dann geht's los"): every subclass of every class as its own profile with its free
 * level 1, and as many points as the whole tree of the profile played costs. No crate gains, no unlocks.
 * Kept free of Minecraft so it can be tested.
 */
public final class FunProfile {

    private FunProfile() {
    }

    /**
     * A full lent profile: one profile per subclass of every class allowed, the own played subclass (if
     * the player has one) played first, its whole tree's points to spend.
     */
    public static PlayerSkills build(SkillTree tree, PlayerSkills own, Predicate<SkillClass> allowed) {
        return build(tree, own, allowed, -1);
    }

    /**
     * Tor 10 / K3 (Chef 13.11. 02:20: level 20 is the top): as {@link #build(SkillTree, PlayerSkills, Predicate)},
     * but with the points of that level ({@code budget}) to spend freely; a budget below 0 = the whole tree.
     */
    public static PlayerSkills build(SkillTree tree, PlayerSkills own, Predicate<SkillClass> allowed, int budget) {
        PlayerSkills lent = new PlayerSkills();
        lent.profileVersion = PlayerSkills.PROFILE_VERSION;
        lent.freeSubclassVersion = PlayerSkills.FREE_SUBCLASS_VERSION;
        lent.templateGiven = true;
        lent.classResetUsed = true;
        String first = null;
        String wanted = null;
        SkillClass ownClass = own == null ? null : own.clazz();
        String ownSub = own == null || ownClass == null ? "" : tree.subclassOfProfile(own, own.chosenClass);
        for (SkillClass c : SkillClass.values()) {
            if (c.isShared() || (allowed != null && !allowed.test(c))) {
                continue;
            }
            for (SkillTree.SubclassInfo sub : SkillTree.subclassInfosOf(c)) {
                String key = lent.addProfile(c, sub.key());
                if (key == null) {
                    continue;
                }
                lent.ultRewardedList().add(key);   // maxing an ultimate here gives no unlock
                if (first == null) {
                    first = key;
                }
                if (c == ownClass && sub.key().equals(ownSub)) {
                    wanted = key;
                }
            }
        }
        String start = wanted != null ? wanted : first;
        if (start != null) {
            lent.playOpened(start);
            lent.points = budget < 0 ? remaining(tree, lent, start) : left(tree, lent, start, budget);
        }
        return lent;
    }

    /** Points the rest of a profile's tree costs (on the choices taken so far). */
    public static int remaining(SkillTree tree, PlayerSkills lent, String key) {
        return Math.max(0, tree.pointsToCompleteProfile(lent, key) - tree.pointsSpentProfile(lent, key));
    }

    /** Plays another lent profile: its whole rest to spend (the points are one account, so they are set anew). */
    public static boolean play(SkillTree tree, PlayerSkills lent, String key) {
        return play(tree, lent, key, -1);
    }

    /** {@link #play(SkillTree, PlayerSkills, String)} with the points of one level (K3); below 0 = the whole tree. */
    public static boolean play(SkillTree tree, PlayerSkills lent, String key, int budget) {
        if (lent == null || key == null || !lent.hasProfile(key)) {
            return false;
        }
        lent.switchToProfile(key);
        lent.points = budget < 0 ? remaining(tree, lent, key) : left(tree, lent, key, budget);
        return true;
    }

    /** The points of a budget a profile has not spent yet (K3: the level's points, one account per profile). */
    public static int left(SkillTree tree, PlayerSkills lent, String key, int budget) {
        return Math.max(0, budget - tree.pointsSpentProfile(lent, key));
    }

    /**
     * "Alles lernen": the played profile is learned up to the top, on the choices the player took; where
     * a choice is still open the first node of it is taken. Returns the levels learned now.
     */
    public static int fill(SkillTree tree, PlayerSkills lent) {
        if (lent == null || lent.clazz() == null) {
            return 0;
        }
        String key = lent.chosenClass;
        SkillClass clazz = lent.clazz();
        List<SkillNode> nodes = tree.ofClass(clazz);
        lent.points = Math.max(lent.points, 0) + 1_000_000;
        int learned = 0;
        for (int guard = 0; guard < 4000 && !tree.isCompleteProfile(lent, key); guard++) {
            boolean any = false;
            for (SkillNode node : nodes) {
                if (lent.levelOf(node.id()) < node.maxLevel() && tree.unlock(node.id(), lent)) {
                    any = true;
                    learned++;
                    break;
                }
            }
            if (!any) {
                break;
            }
        }
        lent.points = 0;
        return learned;
    }
}
