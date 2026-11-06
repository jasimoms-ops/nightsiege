package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Update 7: the Letzte Bastion's numbers, its record list and the fun mode's lent profile. */
class BastionTest {

    @Test
    void wavesClimbTheFullCurve() {
        SiegeConfig cfg = new SiegeConfig();
        // 20 + n of the full curve: with the quarter curve above 20 a wave takes four levels.
        assertEquals(24, BastionMath.levelForWave(1, cfg));
        assertEquals(28, BastionMath.levelForWave(2, cfg));
        assertEquals(60, BastionMath.levelForWave(10, cfg));
        assertEquals(SiegeMath.growthSteps(20, cfg) + 10, SiegeMath.growthSteps(BastionMath.levelForWave(10, cfg), cfg), 1e-9);
        assertEquals(30, BastionMath.quota(10, cfg));
        assertEquals(5, BastionMath.quota(1, cfg));
    }

    @Test
    void bossesMachinesElitesNemeses() {
        SiegeConfig cfg = new SiegeConfig();
        assertFalse(BastionMath.bossWave(1, cfg));
        assertTrue(BastionMath.bossWave(5, cfg));
        assertTrue(BastionMath.bossWave(15, cfg));
        assertEquals(0, BastionMath.machines(5, cfg));
        assertEquals(1, BastionMath.machines(10, cfg));
        assertEquals(0, BastionMath.machines(12, cfg));
        assertEquals(2, BastionMath.machines(15, cfg));
        assertEquals(3, BastionMath.machines(20, cfg));
        assertEquals(0.0, BastionMath.eliteChance(4, cfg), 1e-9);
        assertEquals(0.05, BastionMath.eliteChance(5, cfg), 1e-9);
        assertEquals(0.15, BastionMath.eliteChance(10, cfg), 1e-9);
        assertEquals(0.4, BastionMath.eliteChance(100, cfg), 1e-9);
        assertFalse(BastionMath.nemesisWave(14, cfg));
        assertTrue(BastionMath.nemesisWave(15, cfg));
        assertEquals(1, BastionMath.needed(1, cfg));
        assertEquals(3, BastionMath.needed(10, cfg));
    }

    private static BastionMath.Run run(int wave, int kills, boolean playtest) {
        BastionMath.Run r = new BastionMath.Run();
        r.welle = wave;
        r.kills = kills;
        r.playtest = playtest;
        return r;
    }

    @Test
    void theBestRunsStay() {
        List<BastionMath.Run> runs = new ArrayList<>();
        assertEquals(1, BastionMath.add(runs, run(5, 100, false)));
        assertEquals(1, BastionMath.add(runs, run(8, 50, false)));
        assertEquals(2, BastionMath.add(runs, run(5, 120, false)));
        assertEquals(1, BastionMath.add(runs, run(2, 10, true)));   // the playtest counts on its own list
        for (int i = 0; i < 12; i++) {
            BastionMath.add(runs, run(1, i, false));
        }
        assertEquals(BastionMath.KEPT, BastionMath.best(runs, false, 100).size());
        assertEquals(1, BastionMath.best(runs, true, 100).size());
        assertEquals(8, BastionMath.best(runs, false, 1).get(0).welle);
        assertEquals(0, BastionMath.add(runs, run(1, 0, false)));   // falls off the list
    }

    @Test
    void theLentProfileHasEverySubclassAndItsWholeTree() {
        SkillTree tree = new SkillTree();
        PlayerSkills own = new PlayerSkills();
        PlayerSkills lent = FunProfile.build(tree, own, c -> true);
        int expected = 0;
        for (SkillClass c : SkillClass.values()) {
            if (!c.isShared()) {
                expected += SkillTree.subclassInfosOf(c).size();
            }
        }
        assertEquals(expected, lent.profiles().size());
        assertTrue(lent.points() > 0);
        assertEquals(FunProfile.remaining(tree, lent, lent.chosenClass), lent.points());
        // Every profile: pick it, fill it, it is complete.
        for (String key : new ArrayList<>(lent.profiles())) {
            assertTrue(FunProfile.play(tree, lent, key));
            int points = lent.points();
            assertTrue(points > 0, key);
            FunProfile.fill(tree, lent);
            assertTrue(tree.isCompleteProfile(lent, key), key);
            assertEquals(0, lent.points());
        }
        assertTrue(lent.gains == null || lent.gains.isEmpty());
        assertEquals(0, lent.unlocksFree);
        // The own (empty) profile is untouched.
        assertFalse(own.hasClass());
    }

    @Test
    void theOwnSubclassIsPlayedFirst() {
        SkillTree tree = new SkillTree();
        PlayerSkills own = new PlayerSkills();
        String key = own.addProfile(SkillClass.ARCHER, SkillTree.SUB_TRAPPER);
        own.playOpened(key);
        PlayerSkills lent = FunProfile.build(tree, own, c -> true);
        assertNotNull(lent.clazz());
        assertEquals(SkillClass.ARCHER, lent.clazz());
        assertEquals(SkillTree.SUB_TRAPPER, tree.subclassOfProfile(lent, lent.chosenClass));
    }
}
