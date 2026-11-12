package com.mki.nightsiege.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paket 11.11. (Chef 15:45): class balancing, level curve per subclass, migration 27 -> 28. */
class Paket1111KlassenTest {

    private static SiegeConfig server() throws Exception {
        try (InputStream in = Paket1111KlassenTest.class.getResourceAsStream("/server-config-v27.json")) {
            return new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), SiegeConfig.class);
        }
    }

    @Test
    void curveIsLinearBetweenAndFlatBeyond() {
        double[] lv = {5, 10, 20};
        double[] f = {0.9, 0.9, 0.65};
        assertEquals(0.9, SiegeConfig.interpolate(lv, f, 1), 1e-9);
        assertEquals(0.9, SiegeConfig.interpolate(lv, f, 7), 1e-9);
        assertEquals(0.775, SiegeConfig.interpolate(lv, f, 15), 1e-9);
        assertEquals(0.65, SiegeConfig.interpolate(lv, f, 20), 1e-9);
        assertEquals(0.65, SiegeConfig.interpolate(lv, f, 40), 1e-9);
        assertEquals(1.0, SiegeConfig.interpolate(new double[0], new double[0], 7), 1e-9);
    }

    @Test
    void defaultCurvesElectricianAndTrapper() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        assertEquals(0.9, c.damageLevelFactor("electrician", 5), 1e-9);
        assertEquals(0.9, c.damageLevelFactor("electrician", 10), 1e-9);
        assertEquals(0.65, c.damageLevelFactor("electrician", 20), 1e-9);
        assertEquals(0.8, c.damageLevelFactor("trapper", 1), 1e-9);
        assertEquals(0.8, c.damageLevelFactor("trapper", 5), 1e-9);
        assertEquals(0.8, c.damageLevelFactor("trapper", 10), 1e-9);
        assertEquals(0.9, c.damageLevelFactor("trapper", 15), 1e-9);
        assertEquals(1.0, c.damageLevelFactor("trapper", 20), 1e-9);
        assertEquals(1.0, c.damageLevelFactor("axe", 20), 1e-9);
        assertTrue(c.damageLevelTrapsOnly("electrician"));
        assertFalse(c.damageLevelTrapsOnly("trapper"));   // his traps are bolts, tripwire, net rain: all his damage
        assertFalse(c.damageLevelTrapsOnly("gunner"));
        assertEquals(1.4, c.alchemistDirectHit, 1e-9);
    }

    @Test
    void sanitizeDropsBadPointsAndSorts() {
        SiegeConfig c = new SiegeConfig();
        Map<String, Double> points = new LinkedHashMap<>();
        points.put("20", 0.5);
        points.put("x", 3.0);
        points.put("10", Double.NaN);
        points.put("2", 9.0);
        c.subclassDamageLevel = new LinkedHashMap<>(Map.of("Gunner", points));
        c.sanitize();
        assertEquals(java.util.List.of("2", "20"), new java.util.ArrayList<>(c.subclassDamageLevel.get("gunner").keySet()));
        assertEquals(5.0, c.damageLevelFactor("gunner", 1), 1e-9);
        assertEquals(0.5, c.damageLevelFactor("gunner", 30), 1e-9);
    }

    @Test
    void serverV27GetsThePackageValues() throws Exception {
        SiegeConfig before = server();
        assertEquals(27, before.settingsVersion);
        Map<String, Double> old = new LinkedHashMap<>(before.subclassDamage);
        SiegeConfig c = server();
        c.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, c.settingsVersion);
        assertEquals(0.62, c.subclassDamage.get("gunner"), 1e-9);
        assertEquals(1.725, c.subclassDamage.get("axe"), 1e-9);
        assertEquals(1.08, c.subclassDamage.get("shieldfighter"), 1e-9);
        assertEquals(0.7, c.subclassDamageTaken.get("shieldfighter"), 1e-9);
        assertEquals(0.8, c.subclassDamage.get("alchemist"), 1e-9);
        assertEquals(1.5, c.subclassDamage.get("electrician"), 1e-9);
        assertEquals(1.4, c.alchemistDirectHit, 1e-9);
        assertEquals(0.65, c.damageLevelFactor("electrician", 20), 1e-9);
        assertEquals(0.8, c.damageLevelFactor("trapper", 10), 1e-9);
        assertEquals(6.4, c.skillTweaks.get("potion_tower").values.get("hit"), 1e-9);   // area stays
        int same = 0;
        for (String sub : SiegeConfig.SUBCLASS_KEYS) {
            if (sub.equals("gunner") || sub.equals("axe") || sub.equals("shieldfighter")) {
                continue;
            }
            assertEquals(old.getOrDefault(sub, 1.0), c.subclassDamage.get(sub), 1e-9, sub);
            if (!sub.equals("electrician") && !sub.equals("trapper")) {
                assertEquals(1.0, c.damageLevelFactor(sub, 5) * c.damageLevelFactor(sub, 20), 1e-9, sub);
                same++;
            }
        }
        assertEquals(15, same);   // the 14 others and the alchemist (his change is alchemistDirectHit)
        c.sanitize();   // no second step
        assertEquals(1.725, c.subclassDamage.get("axe"), 1e-9);
    }

    @Test
    void ownServerValueTakesTheSameStep() throws Exception {
        SiegeConfig c = server();
        c.subclassDamage.put("axe", 2.0);
        c.subclassDamage.put("gunner", 1.0);
        c.sanitize();
        assertEquals(2.3, c.subclassDamage.get("axe"), 1e-9);
        assertEquals(0.795, c.subclassDamage.get("gunner"), 1e-9);
    }
}
