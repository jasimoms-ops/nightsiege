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

    /** CI 4.10: turns 2 and 3 of Klingensturm missed, the monsters had slid out (vanilla knockback + lift on top). */
    @Test
    void bladestormKeepsMonstersInReach() {
        double reach = SkillValues.get("whirlwind", "radius1", -1);   // the smallest radius (rank 1)
        double start = 2.5;                                           // a monster 2.5 out (the scene: 2.5 and 2.9, radius 5)
        double before = slide(WarriorSpiritMath.whirlPush(true) + 0.4, 0.4);   // old: 0.4 knockback and hop on top
        assertTrue(start + 2 * before > reach, "the old way slid out: " + (start + 2 * before));
        double now = slide(WarriorSpiritMath.whirlPush(true), WarriorSpiritMath.whirlLift(true));
        assertTrue(start + 2 * now < reach, "turn 3 still reaches: " + (start + 2 * now) + " < " + reach);
        assertEquals(0.3, WarriorSpiritMath.whirlLift(false), 1e-9);
    }

    /** Blocks a monster slides after a push (Minecraft: air drag 0.91, gravity 0.08, ground friction 0.546). */
    private static double slide(double push, double lift) {
        double x = 0.0;
        double y = 0.0;
        double vx = push;
        double vy = lift;
        for (int t = 0; t < 100; t++) {
            x += vx;
            if (y > 0.0 || vy > 0.0) {
                y += vy;
                vy = (vy - 0.08) * 0.98;
                vx *= 0.91;
                if (y <= 0.0) {
                    y = 0.0;
                    vy = 0.0;
                }
            } else {
                vx *= 0.546;
            }
        }
        return x;
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
