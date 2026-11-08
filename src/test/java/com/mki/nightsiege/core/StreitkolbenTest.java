package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tanks' mace (Simon 05.11.: "die Tanks könnten … den Mace als Waffe haben, der viel Schaden macht, aber
 * nicht schnell angreifen kann"): a hit 2.5 times the kit sword's of the same level, 0.8 swings a second
 * instead of 1.6, together 1.25 times the damage a second, growing with the level like the sword.
 */
class StreitkolbenTest {

    private static final double EPS = 1e-9;

    @Test
    void sharpnessAddsLikeVanilla() {
        assertEquals(0.0, NightKit.sharpnessBonus(0), EPS);
        assertEquals(1.0, NightKit.sharpnessBonus(1), EPS);
        assertEquals(1.5, NightKit.sharpnessBonus(2), EPS);
        assertEquals(3.0, NightKit.sharpnessBonus(5), EPS);
        assertEquals(3.5, NightKit.sharpnessBonus(6), EPS);   // the crate gain "Schärfe" goes one above V
    }

    @Test
    void maceSwingsHalfAsOftenAsTheSword() {
        assertEquals(0.8, NightKit.PLAYER_SPEED + NightKit.maceSpeedModifier(), EPS);
        assertEquals(-3.2, NightKit.maceSpeedModifier(), EPS);
        assertEquals(0.5, NightKit.MACE_SPEED / NightKit.SWORD_SPEED, EPS);
        // Minecraft's charge time: 20 / attack speed ticks.
        assertEquals(25.0, 20.0 / NightKit.MACE_SPEED, EPS);
        assertEquals(12.5, 20.0 / NightKit.SWORD_SPEED, EPS);
    }

    @Test
    void maceModifierFromTheSword() {
        // Iron sword (6 with the player's point) and Sharpness I: sword 7, mace 17.5 = 1 + 15.5 + 1.
        assertEquals(15.5, NightKit.maceDamageModifier(6.0, 1), EPS);
        // Netherite of level 20 (9) and Sharpness V: sword 12, mace 30 = 1 + 26 + 3.
        assertEquals(26.0, NightKit.maceDamageModifier(9.0, 5), EPS);
        // No Sharpness at all: plain 2.5 times.
        assertEquals(2.5 * 7.0 - 1.0, NightKit.maceDamageModifier(7.0, 0), EPS);
    }

    @Test
    void everyLevelHitsTwoAndAHalfTimesAndDealsAQuarterMoreASecond() {
        for (int level = 1; level <= 30; level++) {
            for (int gain = 0; gain <= 1; gain++) {   // with and without the crate gain "Schärfe"
                int sharp = NightKit.plusOne(NightKit.sharpness(level), gain, 5);
                double sword = NightKit.swordHit(level, sharp);
                double mace = NightKit.maceHit(level, sharp);
                assertEquals(2.5 * sword, mace, EPS, "level " + level + " sharpness " + sharp);
                double perSecond = mace * NightKit.MACE_SPEED / (sword * NightKit.SWORD_SPEED);
                assertEquals(1.25, perSecond, EPS, "level " + level);
                assertTrue(mace > sword, "level " + level);
            }
        }
    }

    @Test
    void theTableOfTheLevels() {
        // level: material, Sharpness -> sword hit, mace hit
        assertEquals(7.0, NightKit.swordHit(1, NightKit.sharpness(1)), EPS);       // iron, I
        assertEquals(17.5, NightKit.maceHit(1, NightKit.sharpness(1)), EPS);
        assertEquals(7.5, NightKit.swordHit(4, NightKit.sharpness(4)), EPS);       // iron, II
        assertEquals(18.75, NightKit.maceHit(4, NightKit.sharpness(4)), EPS);
        assertEquals(8.5, NightKit.swordHit(6, NightKit.sharpness(6)), EPS);       // diamond, II
        assertEquals(21.25, NightKit.maceHit(6, NightKit.sharpness(6)), EPS);
        assertEquals(9.0, NightKit.swordHit(7, NightKit.sharpness(7)), EPS);       // diamond, III
        assertEquals(22.5, NightKit.maceHit(7, NightKit.sharpness(7)), EPS);
        assertEquals(9.5, NightKit.swordHit(10, NightKit.sharpness(10)), EPS);     // diamond, IV
        assertEquals(23.75, NightKit.maceHit(10, NightKit.sharpness(10)), EPS);
        assertEquals(10.5, NightKit.swordHit(11, NightKit.sharpness(11)), EPS);    // netherite, IV
        assertEquals(26.25, NightKit.maceHit(11, NightKit.sharpness(11)), EPS);
        assertEquals(11.0, NightKit.swordHit(14, NightKit.sharpness(14)), EPS);    // netherite, V
        assertEquals(27.5, NightKit.maceHit(14, NightKit.sharpness(14)), EPS);
        assertEquals(12.0, NightKit.swordHit(20, NightKit.sharpness(20)), EPS);    // netherite +1, V
        assertEquals(30.0, NightKit.maceHit(20, NightKit.sharpness(20)), EPS);
    }

    @Test
    void theMaceGrowsWithTheLevel() {
        double last = 0.0;
        for (int level = 1; level <= 25; level++) {
            double mace = NightKit.maceHit(level, NightKit.sharpness(level));
            assertTrue(mace >= last, "level " + level);
            last = mace;
        }
        assertTrue(NightKit.maceHit(20, 5) > NightKit.maceHit(19, 5));   // the level-20 point counts 2.5 times
    }

    @Test
    void bothTanksCarryAMaceWithTheirOwnLook() {
        KitWeapons.Look shield = KitWeapons.of(SkillTree.SUB_SHIELDFIGHTER);
        KitWeapons.Look bulwark = KitWeapons.of(SkillTree.SUB_BULWARK);
        assertEquals(KitWeapons.Kind.MACE, shield.kind());
        assertEquals(KitWeapons.Kind.MACE, bulwark.kind());
        assertNotEquals(shield.key(), bulwark.key());
        assertEquals("nightsiege:kit/shieldfighter_crestmace", shield.itemModel());
        assertEquals("nightsiege.kitweapon.bulwark_wallmace", bulwark.nameKey());
        // The healers carry their staff since Tor 4.7 (HeilerstabTest).
        assertEquals(KitWeapons.Kind.HEALER_STAFF, KitWeapons.of(SkillTree.SUB_MEDIC).kind());
        assertEquals(KitWeapons.Kind.HEALER_STAFF, KitWeapons.of(SkillTree.SUB_WARDEN).kind());
    }
}
