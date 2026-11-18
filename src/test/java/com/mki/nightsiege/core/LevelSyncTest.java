package com.mki.nightsiege.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.LevelSync;
import com.mki.nightsiege.game.MeleeSim;
import com.mki.nightsiege.game.TestKit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tor 12 I (Simon 18.11.): the Teil-Angleich. Lent only below world level − 3, along the recommended build, on top
 * of the own tree; back at the end, after a crash (never saved) and after a log-out; the own tree and points never
 * change. The old catch-up systems are gone, migration v32 drops their keys.
 */
class LevelSyncTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
    }

    /** A blademaster with the points of this level, learned like the bots. */
    private static PlayerSkills warrior(SkillTree tree, SiegeConfig c, int level) {
        return MeleeSim.buildBudget(tree, SkillClass.WARRIOR, "w_combo", TestKit.pointsBefore(level, c));
    }

    private static int spent(SkillTree tree, PlayerSkills p) {
        return tree.pointsSpentProfile(p, p.chosenClass);
    }

    @Test
    void defaultsAreOnWithGapThree() {
        SiegeConfig c = cfg();
        assertTrue(c.levelSync);
        assertEquals(3, c.levelSyncGap);
        assertEquals(12, LevelSync.target(c, 15));
        assertEquals(1, LevelSync.target(c, 2));
    }

    @Test
    void onlyBelowWorldLevelMinusThree() {
        SiegeConfig c = cfg();
        int at12 = TestKit.pointsBefore(12, c);
        assertEquals(0, LevelSync.pointsToLend(c, 15, at12), "on world level − 3: nothing lent");
        assertEquals(0, LevelSync.pointsToLend(c, 15, at12 + 30), "above it: nothing");
        assertEquals(at12 - TestKit.pointsBefore(5, c), LevelSync.pointsToLend(c, 15, TestKit.pointsBefore(5, c)),
                "level 5 in a world of 15: a steady player's points on 12 minus the own");
        c.levelSync = false;
        assertEquals(0, LevelSync.pointsToLend(c, 15, 0), "switched off: nothing");

        SkillTree tree = new SkillTree();
        SiegeConfig on = cfg();
        PlayerSkills near = warrior(tree, on, 12);
        assertEquals(0, LevelSync.begin(tree, on, near, 15), "level 12 in a world of 15 is not lifted");
        assertFalse(near.synced());
    }

    @Test
    void lendsAlongTheRecommendedBuildAndGivesBack() {
        SkillTree tree = new SkillTree();
        SiegeConfig c = cfg();
        PlayerSkills own = warrior(tree, c, 4);
        Map<String, Integer> before = new HashMap<>(own.levels);
        int points = own.points;
        int earned = own.earned;
        int spentBefore = spent(tree, own);

        int lent = LevelSync.begin(tree, c, own, 16);
        assertEquals(TestKit.pointsBefore(13, c) - earned, lent);
        assertTrue(own.synced());
        assertEquals(13, own.syncLevel);
        int spentSynced = spent(tree, own);
        assertTrue(spentSynced > spentBefore, "more learned (" + spentBefore + " -> " + spentSynced + ")");
        assertTrue(spentSynced - spentBefore <= lent, "at most the lent points spent");
        assertEquals(before, own.levels, "the own tree is untouched");
        assertEquals(points, own.points, "own points untouched");
        assertEquals(earned, own.earned);
        for (String id : before.keySet()) {
            assertTrue(own.levelOf(id) >= before.get(id), id + " keeps at least the own level");
        }
        assertEquals("w_combo", SkillTree.subclassByKey(SkillClass.WARRIOR, tree.subclassOf(own)).node(), "same subclass");

        assertTrue(LevelSync.end(own));
        assertFalse(own.synced());
        assertEquals(0, own.syncLevel);
        assertEquals(spentBefore, spent(tree, own), "back to the own tree");
        assertEquals(before, own.levels);
        assertFalse(LevelSync.end(own), "nothing to give back twice");
    }

    @Test
    void aCrashGivesTheLentLevelsBack() {
        SkillTree tree = new SkillTree();
        SiegeConfig c = cfg();
        PlayerSkills own = warrior(tree, c, 3);
        Map<String, Integer> before = new HashMap<>(own.levels);
        int spentBefore = spent(tree, own);
        assertTrue(LevelSync.begin(tree, c, own, 14) > 0);
        // The save is written while synced, then the server dies: the load has only the own tree.
        PlayerSkills loaded = Json.fromJson(Json.toJson(own), PlayerSkills.class);
        assertFalse(loaded.synced());
        assertFalse(Json.toJson(own).contains("borrowed"), "lent levels never reach the file");
        assertEquals(before, loaded.levels);
        assertEquals(own.points, loaded.points);
        assertEquals(own.earned, loaded.earned);
        assertEquals(spentBefore, spent(tree, loaded));
    }

    @Test
    void buyingAndLosingWhileSyncedTouchOnlyTheOwnTree() {
        SkillTree tree = new SkillTree();
        SiegeConfig c = cfg();
        PlayerSkills own = warrior(tree, c, 3);
        assertTrue(LevelSync.begin(tree, c, own, 14) > 0);
        String lentNode = own.borrowed.keySet().iterator().next();
        int ownLevel = own.levels.getOrDefault(lentNode, 0);
        // A loss on a lent level (as SkillTree.loseLast sets it): the own level never goes below its own.
        own.setLevel(lentNode, own.levelOf(lentNode) - 1);
        assertTrue(own.levels.getOrDefault(lentNode, 0) >= Math.max(0, ownLevel - 1));
        // A purchase is made on the own tree only (as onBuySkill does it).
        own.award(50);
        int pointsBefore = own.points;
        own.withoutBorrowed(() -> tree.unlock(lentNode, own));
        LevelSync.end(own);
        assertTrue(own.levelOf(lentNode) <= Math.max(ownLevel, own.levels.getOrDefault(lentNode, 0)));
        assertTrue(own.points <= pointsBefore, "paid from the own points");
    }

    @Test
    void aLogOutEndsTheLoanAndARejoinLendsAgain() {
        SkillTree tree = new SkillTree();
        SiegeConfig c = cfg();
        PlayerSkills own = warrior(tree, c, 2);
        int spentBefore = spent(tree, own);
        int lent = LevelSync.begin(tree, c, own, 10);
        assertTrue(lent > 0);
        assertEquals(lent, LevelSync.begin(tree, c, own, 10), "already synced: the same loan, nothing twice");
        assertTrue(LevelSync.end(own));   // the sweep on the log-out
        assertEquals(spentBefore, spent(tree, own));
        assertEquals(lent, LevelSync.begin(tree, c, own, 10), "back in the night: lent anew, the same amount");
    }

    @Test
    void theOldCatchUpSystemsAreGone() throws Exception {
        for (String gone : new String[]{"catchUpMax", "lateJoiners", "lateJoinerNights", "soloHelper", "soloHelperBelow"}) {
            assertThrows(NoSuchFieldException.class, () -> SiegeConfig.class.getField(gone), gone);
        }
        for (String gone : new String[]{"catchUpPool", "catchUpFraction"}) {
            assertThrows(NoSuchFieldException.class, () -> PlayerSkills.class.getField(gone), gone);
        }
        for (String gone : new String[]{"com.mki.nightsiege.core.CatchUp", "com.mki.nightsiege.core.SoloHelper"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(gone), gone);
        }
        assertThrows(NoSuchMethodException.class, () -> WaveXp.class.getMethod("catchUp", int.class, int.class, int.class));
        // An old save with an open pool: the pool is emptied, the points given stay.
        PlayerSkills old = Json.fromJson("{\"points\":12,\"earned\":40,\"lastNight\":7,\"catchUpPool\":9,\"catchUpFraction\":0.5}",
                PlayerSkills.class);
        assertEquals(12, old.points);
        assertEquals(40, old.earned);
        assertFalse(Json.toJson(old).contains("catchUp"));
    }

    @Test
    void migrationV32DropsTheOldKeys() throws Exception {
        SiegeConfig c = new Gson().fromJson(Files.readString(Path.of("src/test/resources/tor12/standard-v30.json"),
                StandardCharsets.UTF_8), SiegeConfig.class);
        assertTrue(c.settingsVersion < 32);
        c.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, c.settingsVersion);
        assertTrue(c.settingsVersion >= 32);
        JsonObject written = new Gson().toJsonTree(c).getAsJsonObject();
        for (String gone : new String[]{"catchUpMax", "lateJoiners", "lateJoinerNights", "soloHelper", "soloHelperBelow"}) {
            assertFalse(written.has(gone), gone);
        }
        assertTrue(c.levelSync);
        assertEquals(3, c.levelSyncGap);
    }
}
