package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tor 4.2 (Simon 08.11.): the tanks hit harder (0.7 -> 0.9, 0.63 -> 0.8), heal only themselves on a key instead
 * of potions, and their mace is strong against one foe, not against groups.
 */
class TankTest {

    private static final double EPS = 1e-9;

    @Test
    void theServerValuesGoUpAsSimonSaid() {
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 19;
        server.subclassDamage.put("shieldfighter", 0.7);
        server.subclassDamage.put("bulwark", 0.63);
        server.subclassDamage.put("medic", 0.95);
        server.sanitize();
        assertEquals(1.08, server.subclassDamage.get("shieldfighter"), EPS);   // 0.9, +20 % (Paket 11.11.)
        assertEquals(0.8, server.subclassDamage.get("bulwark"), EPS);
        assertEquals(0.95, server.subclassDamage.get("medic"), EPS);   // only the two tanks
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        // A second load changes nothing more.
        server.sanitize();
        assertEquals(1.08, server.subclassDamage.get("shieldfighter"), EPS);
    }

    @Test
    void freshOldDefaultsAndOwnValues() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(1.08, fresh.subclassDamage.get("shieldfighter"), EPS);
        assertEquals(0.8, fresh.subclassDamage.get("bulwark"), EPS);
        SiegeConfig oldDefault = new SiegeConfig();   // the old default of a fresh config (0.875) and none at all
        oldDefault.settingsVersion = 20;
        oldDefault.subclassDamage.put("shieldfighter", 0.875);
        oldDefault.sanitize();
        assertEquals(1.08, oldDefault.subclassDamage.get("shieldfighter"), EPS);
        assertEquals(0.8, oldDefault.subclassDamage.get("bulwark"), EPS);
        SiegeConfig own = new SiegeConfig();   // a server's own values: the same step
        own.settingsVersion = 20;
        own.subclassDamage.put("shieldfighter", 1.4);
        own.subclassDamage.put("bulwark", 0.315);
        own.sanitize();
        assertEquals(2.16, own.subclassDamage.get("shieldfighter"), EPS);
        assertEquals(0.4, own.subclassDamage.get("bulwark"), EPS);
    }

    @Test
    void theSelfHealIsOnYForBothTanksOnly() {
        for (String sub : new String[]{SkillTree.SUB_SHIELDFIGHTER, SkillTree.SUB_BULWARK}) {
            assertTrue(TankHeal.isTank(sub), sub);
            assertEquals(AbilityKeys.Key.ABILITY_3, AbilityKeys.keyOf(sub, TankHeal.ID), sub);
            assertEquals(AbilityKeys.Key.ABILITY_4, AbilityKeys.keyOf(sub, AbilityKeys.QUIVER), sub);
        }
        for (String sub : new String[]{SkillTree.SUB_MEDIC, SkillTree.SUB_WARDEN, SkillTree.SUB_AXE, SkillTree.SUB_SWIFT}) {
            assertFalse(TankHeal.isTank(sub), sub);
            assertNull(AbilityKeys.keyOf(sub, TankHeal.ID), sub);
        }
    }

    @Test
    void theHealIsAShareOfTheOwnMaxHealthAndNeverMoreThanMissing() {
        SkillValues.apply(new java.util.HashMap<>());
        assertEquals(0.20, TankHeal.share(), EPS);
        assertEquals(1800, TankHeal.cooldownTicks());
        assertEquals(4.0f, TankHeal.amount(20f, 5f), 1e-6f);     // 20 % of 20
        assertEquals(6.0f, TankHeal.amount(30f, 10f), 1e-6f);    // grows with the hearts
        assertEquals(1.5f, TankHeal.amount(20f, 18.5f), 1e-6f);  // only what is missing
        assertEquals(0f, TankHeal.amount(20f, 20f), 1e-6f);
        assertEquals(0f, TankHeal.amount(20f, 0f), 1e-6f);       // the dead do not heal
        // Heal a minute against the healers' two Strong Healing potions (8 health each) in a night of about 6 min.
        double tankPerMinute = 20.0 * TankHeal.share() * 1200.0 / TankHeal.cooldownTicks();
        double potionsPerMinute = 2 * 8.0 / 6.0;
        assertEquals(potionsPerMinute, tankPerMinute, 0.05);
    }

    @Test
    void theMaceIsStrongAgainstOneFoeNotAgainstGroups() {
        assertEquals(1.0, NightKit.MACE_SPEED, EPS);
        assertEquals(1.25, NightKit.maceTargetFactor(true), EPS);
        assertEquals(1.0, NightKit.maceTargetFactor(false), EPS);
        for (int level : new int[]{1, 6, 10, 20}) {
            int sharp = NightKit.sharpness(level);
            double sword = NightKit.swordHit(level, sharp) * NightKit.SWORD_SPEED;
            double before = NightKit.maceHit(level, sharp) * 0.8;          // 0.8 swings a second until Tor 4.2
            double single = NightKit.maceHit(level, sharp) * NightKit.MACE_SPEED;
            assertEquals(1.25, single / before, EPS, "level " + level);     // +25 % on one foe
            assertTrue(single > 1.5 * sword, "level " + level);
        }
        // One target a hit (no sweep, no smash): the scene "tanks" hits one of three husks and checks the other two.
    }
}
