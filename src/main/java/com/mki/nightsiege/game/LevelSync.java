package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;

import java.util.HashMap;
import java.util.Map;

/**
 * Teil-Angleich (Tor 12 I, Simon 18.11.: "Teilweise angleichen"): whoever stands below world level − levelSyncGap
 * in a real night (normal, boss, finale, extra, event), a dungeon or the camp is lifted to that level for its
 * length. The points missing (a steady player's on that level minus the own earned ones) are lent, learned on a
 * copy of the own tree along the "Empfohlener Aufbau" of the subclass (Tor 10, {@link FreeClassPick#plan}), and
 * only the levels beyond the own ones are kept as {@link PlayerSkills#borrowed}. They are never saved (transient),
 * so a crash or a restart gives them back by themselves; the own levels and points never change.
 * Not in "Belagerung starten" (own level), the war leader's round, the bastion and the playtest (the caller's).
 */
public final class LevelSync {

    private LevelSync() {
    }

    /** The level a player is lifted to in a world of this level: world level − levelSyncGap, at least 1. */
    public static int target(SiegeConfig cfg, int worldLevel) {
        return Math.max(1, worldLevel - Math.max(0, cfg.levelSyncGap));
    }

    /**
     * The points to lend: a steady player's on the target level minus the own earned ones. 0 when the switch is off
     * or the player is not below the target level (by points, as the character's level counts).
     */
    public static int pointsToLend(SiegeConfig cfg, int worldLevel, int earned) {
        if (cfg == null || !cfg.levelSync) {
            return 0;
        }
        return Math.max(0, TestKit.pointsBefore(target(cfg, worldLevel), cfg) - Math.max(0, earned));
    }

    /**
     * Lends the levels for a world of this level. Returns the points lent (0: nothing to lend, not synced; a
     * player already synced keeps his).
     */
    public static int begin(SkillTree tree, SiegeConfig cfg, PlayerSkills own, int worldLevel) {
        if (own == null || tree == null || cfg == null) {
            return 0;
        }
        if (own.synced()) {
            return own.borrowedPoints;
        }
        SkillClass clazz = own.clazz();
        int points = pointsToLend(cfg, worldLevel, own.earned);
        if (points <= 0 || clazz == null || clazz.isShared()) {
            return 0;
        }
        SkillTree.SubclassInfo info = SkillTree.subclassByKey(clazz, tree.subclassOfProfile(own, own.chosenClass));
        if (info == null) {
            return 0;   // no subclass yet: no recommended build to follow
        }
        PlayerSkills copy = Json.fromJson(Json.toJson(own), PlayerSkills.class);   // the own tree, nothing lent
        if (copy == null) {
            return 0;
        }
        copy.points = points;
        Playtest.learnLikeAPlayer(tree, copy, FreeClassPick.plan(tree, clazz, info.node()));
        Map<String, Integer> lent = new HashMap<>();
        for (SkillNode n : tree.ofClass(clazz)) {
            int more = copy.levelOf(n.id()) - own.levelOf(n.id());
            if (more > 0) {
                lent.put(n.id(), more);
            }
        }
        if (lent.isEmpty()) {
            return 0;
        }
        own.lend(target(cfg, worldLevel), points, lent);
        return points;
    }

    /** The lent levels go back. True when there were any. */
    public static boolean end(PlayerSkills own) {
        if (own == null || !own.synced()) {
            return false;
        }
        own.endLend();
        return true;
    }
}
