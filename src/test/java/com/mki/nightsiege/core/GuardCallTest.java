package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hüterruf (Paket 11.11. Z1, Chef 12.11. 15:00): push, guard, recall, the boss exception, the pause, the key. */
class GuardCallTest {

    @Test
    void pushThrowsAPlainMonsterFourBlocksAway() {
        double[] v = GuardCall.push(1.0, 1.0, 0.0, 1.0, false);
        double speed = Math.hypot(v[0], v[2]);
        double flown = GuardCall.flight(speed, v[1]);
        assertEquals(4.0, flown, 0.05, "flies 4 blocks");
        assertTrue(v[0] > 0 && v[2] > 0, "away from the summoner");
        assertEquals(v[0], v[2], 1e-9, "straight away (45°)");
        // a monster right on him goes the way he looks
        double[] on = GuardCall.push(0.0, 0.0, 0.0, -1.0, false);
        assertTrue(on[2] < 0 && Math.abs(on[0]) < 1e-9);
    }

    @Test
    void bossesAndGuardsAreOnlySlowedBriefly() {
        double[] v = GuardCall.push(1.0, 0.0, 0.0, 1.0, true);
        assertEquals(0.0, v[0]);
        assertEquals(0.0, v[1]);
        assertEquals(0.0, v[2]);
        assertEquals(20, GuardCall.slowTicks(true), "1 s");
        assertEquals(40, GuardCall.slowTicks(false), "2 s");
        assertEquals(1, GuardCall.SLOW_AMPLIFIER, "Slowness II");
    }

    @Test
    void waveReachesFourBlocks() {
        assertTrue(GuardCall.reaches(4.0));
        assertFalse(GuardCall.reaches(4.01));
    }

    @Test
    void guardCutsFortyPercentForFourSeconds() {
        long until = 1000 + GuardCall.guardTicks();
        assertEquals(80, GuardCall.guardTicks());
        assertEquals(0.6f, GuardCall.takenFactor(until, 1000), 1e-6);
        assertEquals(0.6f, GuardCall.takenFactor(until, 1079), 1e-6);
        assertEquals(1f, GuardCall.takenFactor(until, 1080), 1e-6);
    }

    @Test
    void recallOnlyBeyondTwelveBlocksToThree() {
        assertFalse(GuardCall.recalls(12.0));
        assertTrue(GuardCall.recalls(12.5));
        assertEquals(3.0, GuardCall.nearBlocks());
        assertTrue(GuardCall.freshHit(1000, 1100), "5 s ago");
        assertFalse(GuardCall.freshHit(1000, 1101), "older than 5 s");
        assertFalse(GuardCall.freshHit(-1, 1000), "never hit");
    }

    @Test
    void pauseIsEighteenSeconds() {
        assertEquals(360, GuardCall.cooldownTicks());
        assertEquals(360, GuardCall.cooldownLeft(1360, 1000));
        assertEquals(0, GuardCall.cooldownLeft(1360, 1360));
    }

    @Test
    void tamerAndGolemsmithHaveItOnAFreeKey() {
        for (String sub : new String[]{SkillTree.SUB_TAMER, SkillTree.SUB_GOLEMSMITH}) {
            assertEquals(AbilityKeys.Key.ABILITY_4, AbilityKeys.keyOf(sub, GuardCall.ID), sub);
        }
        assertEquals(null, AbilityKeys.keyOf(SkillTree.SUB_NECROMANCER, GuardCall.ID));
        PlayerSkills own = new PlayerSkills();
        own.chosenClass = SkillClass.SUMMONER.name();
        AbilityTip tip = AbilityTips.barTip(new SkillTree(), own, GuardCall.ID);
        assertNotNull(tip);
        assertEquals(360, tip.cooldownTicks);
        assertEquals(4.0, tip.main.values.get("radius"));
    }
}
