package com.mki.nightsiege.core;

import com.mki.nightsiege.game.FreeClassPick;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.SiegeSave;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tor 10 / K3: the free class pick on level 20 (war leader's round and Letzte Bastion). The recommended build of
 * every subclass, the level 20 points, lent and back (end, cancel, crash), migration 29 and the round's level 25.
 */
class FreeClassPickTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
    }

    private static boolean ultLearned(SkillTree tree, PlayerSkills p, SkillClass clazz) {
        for (SkillNode n : tree.ofClass(clazz)) {
            if (n.effect().target() == SkillEffect.Target.ULTIMATE && p.levelOf(n.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theRecommendedBuildIsValidForEverySubclass() {
        SkillTree tree = new SkillTree();
        SiegeConfig cfg = cfg();
        int budget = FreeClassPick.budget(cfg);
        assertTrue(budget > 0, "level 20 has points (" + budget + ")");
        PlayerSkills lent = FunProfile.build(tree, new PlayerSkills(), c -> true, budget);
        assertEquals(budget, lent.points(), "the first profile starts with the points of level 20");
        int subclasses = 0;
        List<String> table = new ArrayList<>();
        for (String key : new ArrayList<>(lent.profiles())) {
            SkillClass clazz = PlayerSkills.classOfKey(key);
            String sub = lent.plannedSubclass(key);
            assertTrue(FunProfile.play(tree, lent, key, budget), key);
            assertEquals(budget, lent.points(), key + ": all points of level 20, nothing spent yet");
            int n = FreeClassPick.recommend(tree, lent, budget);
            int spent = tree.pointsSpentProfile(lent, key);
            assertTrue(n > 0, key + " learns something");
            assertTrue(spent <= budget, key + " spends at most the budget (" + spent + " of " + budget + ")");
            assertEquals(budget - spent, lent.points(), key + ": the rest stays to spend");
            assertTrue(lent.points() < 10, key + ": the build uses the points (" + lent.points() + " left)");
            assertEquals(sub, tree.subclassOfProfile(lent, key), key + " keeps its subclass");
            assertTrue(ultLearned(tree, lent, clazz), key + " (" + sub + "): the ultimate is in the build");
            for (Map.Entry<String, Integer> e : lent.levels.entrySet()) {
                SkillNode node = tree.get(e.getKey());
                assertNotNull(node, e.getKey());
                assertEquals(clazz, node.clazz(), key + ": " + e.getKey() + " of its class");
                assertTrue(e.getValue() <= node.maxLevel(), key + ": " + e.getKey() + " within its levels");
            }
            // The same build again (fixed, not by chance), also after own spending.
            String first = Json.toJson(lent.levels);
            FreeClassPick.recommend(tree, lent, budget);
            assertEquals(first, Json.toJson(lent.levels), key + ": the build is fixed");
            table.add(String.format("%s %s: %d Stufen, %d/%d Punkte, %s", clazz, sub, n, spent, budget,
                    new java.util.TreeMap<>(lent.levels)));
            subclasses++;
        }
        assertTrue(subclasses >= 16, "every subclass (" + subclasses + ")");
        table.forEach(line -> System.out.println("[K3 Aufbau] " + line));
    }

    @Test
    void lentAndBackAfterEndCancelAndCrash() {
        SkillTree tree = new SkillTree();
        SiegeConfig cfg = cfg();
        SiegeSave save = new SiegeSave();
        String id = "00000000-0000-0000-0000-00000000000a";
        PlayerSkills own = save.realSkillsOf(id);
        own.award(7);
        own.addProfile(SkillClass.ARCHER, SkillTree.SUB_TRAPPER);
        own.playOpened(own.profiles().get(0));
        own.setGainStage(SkillClass.ARCHER, "glueck", 2);
        save.skills.put(id, own);
        String before = Json.toJson(own);
        int budget = FreeClassPick.budget(cfg);

        for (String how : List.of("end", "cancel", "crash")) {
            PlayerSkills lent = FunProfile.build(tree, save.realSkillsOf(id), c -> true, budget);
            save.lend(id, lent);
            assertTrue(save.hasLoan(id), how);
            PlayerSkills played = save.skillsOf(id);
            assertTrue(played == lent, how + ": the round plays the lent profile");
            // Another class, its build, a crate's gain: all on the lent profile.
            String key = lent.profileFor(SkillClass.WARRIOR);
            assertTrue(FunProfile.play(tree, played, key, budget));
            FreeClassPick.recommend(tree, played, budget);
            played.setGainStage(SkillClass.WARRIOR, "glueck", 3);
            played.award(5);
            assertEquals(before, Json.toJson(save.realSkillsOf(id)), how + ": the own profile untouched while lent");
            if (how.equals("crash")) {
                // A crash: the save on disk never holds the loan; loaded again, the own profile is there.
                SiegeSave loaded = Json.fromJson(Json.toJson(save), SiegeSave.class);
                assertNotNull(loaded);
                assertFalse(loaded.hasLoan(id), "no loan in the written save");
                assertEquals(before, Json.toJson(loaded.skillsOf(id)), "crash: the own profile comes back");
                save.endLoan(id);
            } else {
                assertTrue(save.endLoan(id), how);   // the round's end and a cancel both end the loan (SiegeManager.endFun)
            }
            assertFalse(save.hasLoan(id), how);
            assertEquals(before, Json.toJson(save.skillsOf(id)), how + ": the own profile is back as it was");
        }
    }

    @Test
    void theCratesArePerClassAndOnlyAsManyAsSet() {
        SiegeConfig cfg = cfg();
        assertEquals(20, cfg.funCrates);
        assertEquals(20, FreeClassPick.due(cfg, 0));
        assertEquals(0, FreeClassPick.due(cfg, 20));
        assertEquals(0, FreeClassPick.due(cfg, 25));
        assertEquals(5, FreeClassPick.due(cfg, 15));
    }

    @Test
    void migration29BringsWarLevel25() {
        SiegeConfig fresh = cfg();
        assertTrue(SiegeConfig.SETTINGS_VERSION >= 29);   // 30 since Tor 11 (chapters), the 29 step still runs
        assertEquals(25, fresh.warLevel);
        assertEquals(20, fresh.funKitLevel);
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 28;
        server.warLevel = 20;
        server.funCrates = 20;
        server.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(25, server.warLevel, "the old default becomes 25");
        SiegeConfig own = new SiegeConfig();
        own.settingsVersion = 28;
        own.warLevel = 30;
        own.sanitize();
        assertEquals(30, own.warLevel, "an own value stays");
        own.sanitize();
        assertEquals(30, own.warLevel, "no second step");
        SiegeConfig wild = cfg();
        wild.funCrates = 500;
        wild.sanitize();
        assertEquals(100, wild.funCrates);
    }

    @Test
    void theRoundFightsAtWarLevelNotTheWorldsLevel() {
        SiegeConfig cfg = cfg();
        assertEquals(25, FreeClassPick.roundLevel(cfg, 0, 3));
        assertEquals(25, FreeClassPick.roundLevel(cfg, 0, 40));
        assertEquals(12, FreeClassPick.roundLevel(cfg, 12, 3), "the sim's / sandbox's forced level");
        cfg.funModeLoans = false;
        assertEquals(7, FreeClassPick.roundLevel(cfg, 0, 7), "without lent profiles: the world's level");
        cfg.funModeLoans = true;
        // War gold of 29.10. for level 25: more than for 20, and with more players.
        int g20 = WarShop.roundGold(20, 4, cfg, cfg.perPlayer(false, cfg.playerScaling), cfg.maxLiveMobsCap);
        int g25 = WarShop.roundGold(25, 4, cfg, cfg.perPlayer(false, cfg.playerScaling), cfg.maxLiveMobsCap);
        int g25six = WarShop.roundGold(25, 6, cfg, cfg.perPlayer(false, cfg.playerScaling), cfg.maxLiveMobsCap);
        assertTrue(g25 > g20, "level 25 gold " + g25 + " > level 20 " + g20);
        assertTrue(g25six > g25, "6 players " + g25six + " > 4 players " + g25);
        // Every "from level" of the shop is open at 25.
        for (WarShop.Squad s : WarShop.SQUADS) {
            assertTrue(s.fromLevel() <= cfg.warLevel, s.id() + " from " + s.fromLevel());
        }
        assertTrue(cfg.sapperFromLevel <= cfg.warLevel, "sapper from " + cfg.sapperFromLevel);
    }
}
