package com.mki.nightsiege.core;

import com.mki.nightsiege.game.ArcherEffects;
import com.mki.nightsiege.game.CombatEffects;
import com.mki.nightsiege.game.EngineerAbilities;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tor 4c.5 A1-A7 (Chef 09.11. 10:15): the level outliers; migration 23 (fresh, server value, own value). */
class Tor4cStufenTest {

    private static SiegeConfig.SkillTweak tweak(String key, double value) {
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.values = new LinkedHashMap<>(Map.of(key, value));
        return t;
    }

    @Test
    void a1FirestormArrowsHitHarder() {
        assertEquals(1.75, SkillValues.get("a_firestorm", "mult", -1), 1e-9);
        assertEquals(10.0, ArcherEffects.stormArrowDamage(10.0, false), 1e-9);
        assertEquals(17.5, ArcherEffects.stormArrowDamage(10.0, true), 1e-9);
    }

    @Test
    void a2GroundSlamDamageAndRadius() {
        double[] dmg = {11, 15, 20};
        double[] radius = {6, 7, 8};
        for (int rank = 1; rank <= 3; rank++) {
            assertEquals(dmg[rank - 1], SkillValues.get("ground_slam", "dmg" + rank, -1), 1e-9);
            assertEquals(radius[rank - 1], SkillValues.get("ground_slam", "radius" + rank, -1), 1e-9);
        }
    }

    @Test
    void a3MartyrHealsHimselfHalf() {
        assertEquals(1.5, CombatEffects.martyrHeal(false), 1e-9);
        assertEquals(0.75, CombatEffects.martyrHeal(true), 1e-9);
    }

    @Test
    void a4WatchfulEyeGives25Percent() {
        SkillTree tree = new SkillTree();
        assertEquals(0.25, tree.get("a_watchful").amountPerLevel(), 1e-9);
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.ARCHER.name();
        p.setLevel("a_watchful", 1);
        assertEquals(0.25, tree.totalBonus(SkillEffect.WATCHFUL, p), 1e-9);
    }

    @Test
    void a6ShackleHoldsNearMonstersHalfAsLong() {
        assertEquals(2.0, SkillValues.get("a_shackle", "radius", -1), 1e-9);
        assertEquals(60, CombatEffects.shackleTicks(false));
        assertEquals(30, CombatEffects.shackleTicks(true));
    }

    @Test
    void a6bCleanseHealsTwoPerPulse() {
        assertEquals(2.0, SkillValues.get("g_cleanse", "heal", -1), 1e-9);
    }

    @Test
    void a7BackupPowerFromSixtyPercent() {
        assertEquals(0.6, SkillValues.get("e_backup", "below", -1), 1e-9);
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.ENGINEER.name();
        p.setLevel("e_backup", 1);
        assertEquals(0.5, EngineerAbilities.pauseFactor(tree, p, 0.5), 1e-9);    // under 60 %: twice as fast
        assertEquals(1.0, EngineerAbilities.pauseFactor(tree, p, 0.7), 1e-9);
    }

    @Test
    void freshConfigHasNoTweaksForThem() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertNull(fresh.skillTweaks.get("ground_slam"));
        assertNull(fresh.skillTweaks.get("e_backup"));
        assertNull(fresh.skillTweaks.get("a_watchful"));
    }

    @Test
    void serverOldValuesBecomeTargetsOtherValuesStay() {
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 22;
        SiegeConfig.SkillTweak slam = tweak("dmg1", 8);       // old default written by the server
        slam.values.put("dmg3", 14.0);
        slam.values.put("radius2", 7.0);                       // already the target
        slam.values.put("radius3", 10.0);                      // own value
        server.skillTweaks.put("ground_slam", slam);
        server.skillTweaks.put("e_backup", tweak("below", 0.3));
        SiegeConfig.SkillTweak watchful = new SiegeConfig.SkillTweak();
        watchful.amount = 0.15;
        server.skillTweaks.put("a_watchful", watchful);
        server.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(11, server.skillTweaks.get("ground_slam").values.get("dmg1"), 1e-9);
        assertEquals(20, server.skillTweaks.get("ground_slam").values.get("dmg3"), 1e-9);
        assertEquals(7, server.skillTweaks.get("ground_slam").values.get("radius2"), 1e-9);
        assertEquals(10, server.skillTweaks.get("ground_slam").values.get("radius3"), 1e-9);
        assertEquals(0.6, server.skillTweaks.get("e_backup").values.get("below"), 1e-9);
        assertEquals(0.25, server.skillTweaks.get("a_watchful").amount, 1e-9);
        server.sanitize();   // no second step
        assertEquals(11, server.skillTweaks.get("ground_slam").values.get("dmg1"), 1e-9);
        assertEquals(0.6, server.skillTweaks.get("e_backup").values.get("below"), 1e-9);
    }

    @Test
    void serverValuesAlreadyOnTheTargetStay() {
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 22;
        server.skillTweaks.put("e_backup", tweak("below", 0.6));
        server.skillTweaks.put("ground_slam", tweak("dmg2", 15));
        SiegeConfig.SkillTweak watchful = new SiegeConfig.SkillTweak();
        watchful.amount = 0.25;
        server.skillTweaks.put("a_watchful", watchful);
        server.sanitize();
        assertEquals(0.6, server.skillTweaks.get("e_backup").values.get("below"), 1e-9);
        assertEquals(15, server.skillTweaks.get("ground_slam").values.get("dmg2"), 1e-9);
        assertEquals(0.25, server.skillTweaks.get("a_watchful").amount, 1e-9);
    }
}
