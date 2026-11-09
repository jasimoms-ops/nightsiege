package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tor 4b.3: seat lock 20 -> 10 s, tower health 3x -> 5x; migration 22 (fresh, server value, own value). */
class Tor4bTuermeTest {

    private static SiegeConfig.SkillTweak tweak(String key, double value) {
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.values = new LinkedHashMap<>(Map.of(key, value));
        return t;
    }

    @Test
    void codeValuesAreTheTargets() {
        assertEquals(200, SkillValues.get("e_longrange", "seatlock", -1), 1e-9);
        assertEquals(0.5, SkillValues.get("e_ammobelt", "speed", -1), 1e-9);   // Tor 4b.4
        assertEquals(0.3, SkillValues.get("e_beacon", "bonus", -1), 1e-9);
        assertEquals(3.0, new SkillTree().get("e_chainbolt").amountPerLevel(), 1e-9);
        for (TrapKind tower : new TrapKind[] {TrapKind.TURRET, TrapKind.MORTAR, TrapKind.BREWER, TrapKind.TESLA}) {
            assertEquals(5.0, SkillValues.get(tower.id(), "seathealth", -1), 1e-9);
        }
    }

    @Test
    void freshConfigHasNoTowerTweaks() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertNull(fresh.skillTweaks.get("e_longrange"));
        assertNull(fresh.skillTweaks.get("arrow_turret"));
    }

    @Test
    void serverOldValuesBecomeTargetsOwnValuesMoveAlike() {
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 21;
        server.skillTweaks.put("e_longrange", tweak("seatlock", 400));   // old default written by the server
        server.skillTweaks.put("mortar_tower", tweak("seathealth", 3));
        server.skillTweaks.put("tesla_coil", tweak("seathealth", 2.4));    // own value
        server.skillTweaks.put("e_ammobelt", tweak("speed", 0.2));          // Tor 4b.4: old default
        server.sanitize();
        assertEquals(0.5, server.skillTweaks.get("e_ammobelt").values.get("speed"), 1e-9);
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        assertEquals(200, server.skillTweaks.get("e_longrange").values.get("seatlock"), 1e-9);
        assertEquals(5, server.skillTweaks.get("mortar_tower").values.get("seathealth"), 1e-3);
        assertEquals(4.0, server.skillTweaks.get("tesla_coil").values.get("seathealth"), 1e-3);
        server.sanitize();   // no second step
        assertEquals(200, server.skillTweaks.get("e_longrange").values.get("seatlock"), 1e-9);
        assertEquals(4.0, server.skillTweaks.get("tesla_coil").values.get("seathealth"), 1e-3);
    }
}
