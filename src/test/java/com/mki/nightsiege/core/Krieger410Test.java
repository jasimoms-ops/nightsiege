package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 4.10 (Krieger): Blutrausch grows with the life missing, Axtwurf hits 3 at most, Klingensturm and Kombo knockback. */
class Krieger410Test {

    @Test
    void valuesAsReleased() {
        assertEquals(160, SkillValues.get("bloodlust", "f_time", -1), 1e-9);    // 8 s
        assertEquals(400, SkillValues.get("bloodlust", "f_cd", -1), 1e-9);      // 20 s
        assertEquals(400, WarriorSpiritMath.abilityCooldown(SkillTree.SUB_BERSERKER));
        assertEquals(0.15, SkillValues.get("bloodlust", "f_taken", -1), 1e-9);
        assertEquals(7.5, SkillValues.get("heavy_blow", "f_dmg", -1), 1e-9);
        assertEquals(2.5, SkillValues.get("heavy_blow", "f_speed", -1), 1e-9);
        assertEquals(12, SkillValues.get("heavy_blow", "f_range", -1), 1e-9);
        assertEquals(2, SkillValues.get("heavy_blow", "f_pierce", -1), 1e-9);
        assertEquals(1.2, WarriorSpiritMath.whirlFactor(true), 1e-9);
        assertEquals(1.0, WarriorSpiritMath.whirlFactor(false), 1e-9);
        assertEquals(15.6, SkillValues.get("whirlwind", "dmg3", -1) * WarriorSpiritMath.whirlFactor(true), 1e-9);
        assertEquals(0.25, WarriorSpiritMath.whirlPush(true), 1e-9);
        assertEquals(0.8, WarriorSpiritMath.whirlPush(false), 1e-9);
    }

    @Test
    void rageGrowsWithLifeMissing() {
        assertEquals(0.0, WarriorSpiritMath.missing(20, 20), 1e-9);
        assertEquals(0.6, WarriorSpiritMath.missing(8, 20), 1e-9);
        assertEquals(1.0, WarriorSpiritMath.missing(-1, 20), 1e-9);
        // full life: +50 % speed, +25 % damage, x2 life steal; no life: +100 %, +60 %, x3
        assertEquals(0.5, WarriorSpiritMath.rageSpeed(0), 1e-9);
        assertEquals(1.0, WarriorSpiritMath.rageSpeed(1), 1e-9);
        assertEquals(0.25, WarriorSpiritMath.rageDamage(0), 1e-9);
        assertEquals(0.60, WarriorSpiritMath.rageDamage(1), 1e-9);
        assertEquals(2.0, WarriorSpiritMath.rageSteal(0), 1e-9);
        assertEquals(3.0, WarriorSpiritMath.rageSteal(1), 1e-9);
        assertEquals(0.39, WarriorSpiritMath.rageDamage(0.4), 1e-9);
        assertEquals(1.0, WarriorSpiritMath.rageSpeed(5), 1e-9);   // clamped
        double before = -1;
        for (int i = 0; i <= 10; i++) {
            double now = WarriorSpiritMath.rageDamage(i / 10.0) + WarriorSpiritMath.rageSpeed(i / 10.0) + WarriorSpiritMath.rageSteal(i / 10.0);
            assertTrue(now > before, "grows at " + i);
            before = now;
        }
    }

    @Test
    void axeHitsThreeAtMost() {
        assertEquals(3, WarriorSpiritMath.throwHitsMax());
    }

    @Test
    void comboKnockbackIsSoft() {
        assertEquals(0.3, WarriorSpiritMath.comboKnockback(), 1e-9);
        assertEquals(0.12, 0.4 * WarriorSpiritMath.comboKnockback(), 1e-9);
    }
}
