package com.mki.nightsiege.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chef 12.11.: one health factor per boss (bossHealthPer), empty = every boss on 1.0. */
class BossHealthPerTest {

    @Test
    void freshConfigHasThePackageFactors() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        assertEquals(SiegeConfig.packageBossHealth(), c.bossHealthPer);   // Paket 11.11., Kv5
        for (BossKind k : BossKind.values()) {
            assertEquals(SiegeConfig.packageBossHealth().getOrDefault(k.id(), 1.0), c.bossHealthFor(k.id()), 1e-9);
        }
        assertEquals(1.0, c.bossHealthFor("grave_king"), 1e-9);
    }

    @Test
    void serverValuesAreReadAndKeptInBounds() {
        SiegeConfig c = new Gson().fromJson("{\"settingsVersion\":28,\"bossHealthPer\":{\"frost_jarl\":0.8,"
                + "\"storm_witch\":0,\"ram_titan\":50,\"bone_lich\":null}}", SiegeConfig.class);
        c.sanitize();
        assertEquals(0.8, c.bossHealthFor("frost_jarl"), 1e-9);
        assertEquals(1.0, c.bossHealthFor("storm_witch"), 1e-9);   // 0 or less = no factor
        assertEquals(10.0, c.bossHealthFor("ram_titan"), 1e-9);     // capped
        assertFalse(c.bossHealthPer.containsKey("bone_lich"));      // null dropped
        assertEquals(1.0, c.bossHealthFor("grave_king"), 1e-9);     // not listed
    }

    @Test
    void oldConfigWithoutTheFieldGetsThePackageFactors() {
        SiegeConfig c = new Gson().fromJson("{\"settingsVersion\":27,\"bossHealthPer\":null}", SiegeConfig.class);
        c.sanitize();
        assertEquals(SiegeConfig.packageBossHealth(), c.bossHealthPer);   // migration 27 -> 28
        c.bossHealthPer.put("warlord", 0.9);   // the sim's variant writes into it (TeamSim applyConfig)
        assertEquals(0.9, c.bossHealthFor("warlord"), 1e-9);
    }
}
