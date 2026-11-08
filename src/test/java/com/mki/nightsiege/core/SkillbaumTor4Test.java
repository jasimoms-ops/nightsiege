package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 4 skill trees: the 17 proposals, the shield skills growing with the world level, the dead core tree. */
class SkillbaumTor4Test {

    @Test
    void shieldSkillDamageGrowsLikeTheMonstersLife() {
        SiegeConfig c = new SiegeConfig();   // healthPerLevel 0.06
        assertEquals(4.0, SiegeMath.skillDamage(4.0, 1, c), 1e-9);
        assertEquals(4.0 * 1.54, SiegeMath.skillDamage(4.0, 10, c), 1e-9);
        assertEquals(4.0 * 2.14, SiegeMath.skillDamage(4.0, 20, c), 1e-9);
        c.healthPerLevel = 0.1;               // the server's value
        assertEquals(8.0, SiegeMath.skillDamage(8.0, 1, c), 1e-9);
        assertEquals(8.0 * 1.9, SiegeMath.skillDamage(8.0, 10, c), 1e-9);
        assertEquals(8.0 * SiegeMath.healthMultiplier(20, c), SiegeMath.skillDamage(8.0, 20, c), 1e-9);
        assertTrue(SiegeMath.skillDamage(8.0, 20, c) > SiegeMath.skillDamage(8.0, 10, c));
    }

    @Test
    void coreTreeCanNoLongerBeBought() {
        SkillTree tree = new SkillTree();
        PlayerSkills pot = new PlayerSkills();
        pot.award(100);
        for (String id : new String[]{"masonry", "battlements", "rebuild", "fortified", "beacon_fire"}) {
            assertEquals("retired", tree.blockedReason(id, pot), id);
            assertFalse(tree.unlock(id, pot), id);
            assertEquals(0, pot.levelOf(id), id);
        }
        assertEquals(100, pot.points());
    }

    @Test
    void theSeventeenProposalsAreInTheValues() {
        SkillTree tree = new SkillTree();
        assertEquals(0.5, SkillValues.get("double_shot", "share", 0), 1e-9);
        assertEquals(0.2, SkillValues.get("a_knockarrow", "bonus", 0), 1e-9);
        assertEquals(120, SkillValues.get("a_poison", "dur", 0), 1e-9);
        assertEquals(1.0, SkillValues.get("a_eagle", "crit", 0), 1e-9);
        assertEquals(0.2, SkillValues.get("b_cross_shrapnel", "swift", 0), 1e-9);
        assertEquals(8.0, SkillValues.get("power_shot", "mult3", 0), 1e-9);
        assertEquals(3, SkillValues.get("power_shot", "pierce", 0), 1e-9);
        assertEquals(1.0, SkillValues.get("a_skirmisher", "mult", 0), 1e-9);
        assertEquals(1.25, SkillValues.get("a_deadeye", "swift", 0), 1e-9);
        assertEquals(0.6, SkillValues.get("x_cross_double", "share", 0), 1e-9);
        assertEquals(0.6, tree.get("a_bracing").amountPerLevel(), 1e-9);
        assertEquals(12.0, tree.get("blood_drinker").amountPerLevel(), 1e-9);
        assertEquals(20, SkillValues.get("w_bloodprice", "every", 0), 1e-9);
        assertEquals(0.8, tree.get("w_headhunter").amountPerLevel(), 1e-9);
        assertEquals(12.0, tree.get("holy_vigor").amountPerLevel(), 1e-9);
        assertEquals(0.15, tree.get("g_prayer").amountPerLevel(), 1e-9);
        // The engineer proposals (ammo belt, chain bolt, guiding beam) moved to Tor 4b.4.
        assertEquals(0.2, SkillValues.get("e_ammobelt", "speed", 0), 1e-9);
        assertEquals(2.0, tree.get("e_chainbolt").amountPerLevel(), 1e-9);
        assertEquals(200, SkillValues.get("s_howl", "every", 0), 1e-9);
        assertEquals(1, SkillValues.get("d_haste", "cheaper", 0), 1e-9);
    }
}
