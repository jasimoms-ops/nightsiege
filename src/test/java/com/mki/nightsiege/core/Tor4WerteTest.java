package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 4 (Simon 08.11.): quick shot, axe fighter, towers and soulcatcher. */
class Tor4WerteTest {

    private static SiegeConfig.SkillTweak tweak(String key, double value) {
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.values = new LinkedHashMap<>(Map.of(key, value));
        return t;
    }

    @Test
    void update20ScalesTheServersValuesAndKeepsTheRest() {
        // The server's config (settings 19): quick shot 1.3, a tuned tower lock and seat health, a heavy archer 1.75.
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 19;
        server.subclassDamage.put("swift", 1.3);
        server.subclassDamage.put("heavy", 1.75);
        server.skillTweaks.put("e_longrange", tweak("seatlock", 400));
        server.skillTweaks.put("arrow_turret", tweak("seathealth", 3));
        server.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(0.8, server.subclassDamage.get("swift"), 1e-3);
        assertEquals(1.75, server.subclassDamage.get("heavy"), 1e-9);
        // The towers moved to Tor 4b.3: the server's tower tweaks stay as they are.
        assertEquals(400, server.skillTweaks.get("e_longrange").values.get("seatlock"), 1e-9);
        assertEquals(3, server.skillTweaks.get("arrow_turret").values.get("seathealth"), 1e-3);
        assertNull(server.skillTweaks.get("tesla_coil"));
        // Run again: no second scaling.
        server.sanitize();
        assertEquals(0.8, server.subclassDamage.get("swift"), 1e-3);
    }

    @Test
    void update20GivesTheQuickShot08FreshAndFromTheServerAndScalesOwnValues() {
        SiegeConfig fresh = new SiegeConfig();   // settings 0: every migration runs
        fresh.sanitize();
        assertEquals(0.8, fresh.subclassDamage.get("swift"), 1e-9);
        SiegeConfig missing = new SiegeConfig();
        missing.settingsVersion = 19;
        missing.subclassDamage = new LinkedHashMap<>();   // a file without the key
        missing.sanitize();
        assertEquals(0.8, missing.subclassDamage.get("swift"), 1e-9);
        SiegeConfig oldDefault = new SiegeConfig();
        oldDefault.settingsVersion = 19;
        oldDefault.subclassDamage.put("swift", 1.0);   // the old default, written into every config by filled()
        oldDefault.sanitize();
        assertEquals(0.8, oldDefault.subclassDamage.get("swift"), 1e-9);
        SiegeConfig own = new SiegeConfig();
        own.settingsVersion = 19;
        own.subclassDamage.put("swift", 1.5);   // a server's own value: × 0.8 / 1.3
        own.sanitize();
        assertEquals(0.923, own.subclassDamage.get("swift"), 1e-3);
        own.sanitize();
        assertEquals(0.923, own.subclassDamage.get("swift"), 1e-3);
    }

    @Test
    void theAxeStealsLessFromLevel15() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.hitLifesteal.put("axe", 0.08);   // the server's value
        c.sanitize();
        assertEquals(0.08, c.hitLifestealAt("axe", 14), 1e-9);
        assertFalse(c.hitLifestealLateAt("axe", 14));
        assertEquals(0.06, c.hitLifestealAt("axe", 15), 1e-9);
        assertTrue(c.hitLifestealLateAt("axe", 20));
        assertEquals(0.08, c.hitLifestealAt("blademaster", 20), 1e-9);   // only the axe fighter
        assertFalse(c.hitLifestealLateAt("blademaster", 20));
        assertEquals(1.0, c.hitLifestealLateTakenShare, 1e-9);
    }

    @Test
    void theNewBuiltInValues() {
        // The towers stay at 20 s and 3x until Tor 4b.3.
        assertEquals(400, SkillValues.get("e_longrange", "seatlock", -1), 1e-9);
        for (TrapKind tower : new TrapKind[] {TrapKind.TURRET, TrapKind.MORTAR, TrapKind.BREWER, TrapKind.TESLA}) {
            assertEquals(3.0, SkillValues.get(tower.id(), "seathealth", -1), 1e-9);
        }
        assertEquals(-10, SkillValues.get("heavy_blow", "hp", 0), 1e-9);
        assertEquals(-6, SkillValues.get("heavy_blow", "hplow", 0), 1e-9);
        assertEquals(10, SkillValues.get("heavy_blow", "hplowuntil", 0), 1e-9);
        assertEquals(24, SkillValues.get("d_raise", "catcherrange", -1), 1e-9);
        assertEquals(16, SkillValues.get("d_raise", "range", -1), 1e-9);   // corpses and the necromancer stay
        assertEquals(4, SkillValues.get("d_soulcatcher", "swarmcost", -1), 1e-9);
        assertEquals(1, SkillValues.get("d_haste", "cheaper", -1), 1e-9);   // Geisterhast: 4 -> 3
    }
}
