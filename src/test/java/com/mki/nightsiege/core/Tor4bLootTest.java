package com.mki.nightsiege.core;

import com.mki.nightsiege.game.SiegeSave;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 4b.5: the engineer's loot – new gains, rarities, the remote's gains gone, drop test, save migration. */
class Tor4bLootTest {

    private static final int DRAWS = 10_000;

    @Test
    void theEngineerGainsAreAsDecided() {
        assertEquals(CrateRarity.COMMON, CrateGains.get("verstaerkt").rarity());
        assertEquals(CrateRarity.COMMON, CrateGains.get("kalibrierung").rarity());
        assertEquals(3, CrateGains.get("kalibrierung").stages());
        assertEquals(CrateRarity.UNCOMMON, CrateGains.get("saeurekanister").rarity());
        assertEquals(SkillTree.WAY_CHEMISTRY, CrateGains.get("saeurekanister").way());
        assertNotNull(CrateGains.get("ersatzbatterie"));
        assertNull(CrateGains.get("zweite_ladung"));
        assertNull(CrateGains.get("recycling"));
    }

    /**
     * 10 000 draws per engineer subclass with the default odds of a normal night: every gain of its pool
     * shows up, and the share of each rarity matches the odds (rarities with no gain step down).
     */
    @Test
    void dropTestPerEngineerSubclass() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        cfg.sanitize();
        int[] weights = cfg.crateOdds(CrateKind.values()[0]);
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            if (info.clazz() != SkillClass.ENGINEER) {
                continue;
            }
            Random random = new Random(4_2_0 + info.key().hashCode());
            Map<String, Integer> seen = new HashMap<>();
            Map<CrateRarity, Integer> byRarity = new EnumMap<>(CrateRarity.class);
            for (int i = 0; i < DRAWS; i++) {
                CrateGains.Gain g = CrateGains.roll(SkillClass.ENGINEER, info.way(), info.key(), weights, cfg.crateClassShare, random);
                assertNotNull(g, info.key());
                seen.merge(g.id(), 1, Integer::sum);
                byRarity.merge(g.rarity(), 1, Integer::sum);
            }
            Set<String> pool = new HashSet<>();
            for (CrateGains.Gain g : CrateGains.all()) {
                if (g.clazz() == SkillClass.ENGINEER && CrateGains.drawable(g, SkillClass.ENGINEER, info.way(), info.key())
                        && weights[g.rarity().ordinal()] > 0) {
                    pool.add(g.id());
                }
            }
            for (String id : pool) {
                assertTrue(seen.containsKey(id), info.key() + ": " + id + " never drawn in " + DRAWS);
            }
            assertFalse(seen.containsKey("zweite_ladung") || seen.containsKey("recycling"), info.key());
            // Common gains exist for every subclass, so the common share must match its weight closely.
            double want = weights[CrateRarity.COMMON.ordinal()] / (double) total;
            double got = byRarity.getOrDefault(CrateRarity.COMMON, 0) / (double) DRAWS;
            assertEquals(want, got, 0.03, info.key() + " common share");
            System.out.println("[drop] " + info.key() + " pool=" + pool.size() + " " + byRarity + " " + seen);
        }
    }

    private static SiegeSave saveWith(String gain, int stage, String other) {
        SiegeSave save = new SiegeSave();
        PlayerSkills own = new PlayerSkills();
        own.gains.computeIfAbsent(SkillClass.ENGINEER.name(), k -> new HashMap<>()).put(gain, stage);
        if (other != null) {
            own.gains.get(SkillClass.ENGINEER.name()).put(other, 1);
        }
        save.skills = new HashMap<>(Map.of("p", own));
        return save;
    }

    @Test
    void migrationMovesTheRemoteGainsWithoutLosingPoints() {
        // zweite_ladung -> Notschalter (ersatzbatterie)
        SiegeSave a = saveWith("zweite_ladung", 1, null);
        int before = a.skills.get("p").points;
        assertEquals(0, a.migrateRemoteGains());
        Map<String, Integer> ga = a.skills.get("p").gains.get(SkillClass.ENGINEER.name());
        assertEquals(1, ga.get("ersatzbatterie"));
        assertNull(ga.get("zweite_ladung"));
        assertEquals(before, a.skills.get("p").points);
        // already owning the Notschalter: the rare duplicate points instead
        SiegeSave b = saveWith("zweite_ladung", 1, "ersatzbatterie");
        assertEquals(CrateRarity.RARE.duplicatePoints(), b.migrateRemoteGains());
        // recycling: its rare duplicate points per stage back
        SiegeSave c = saveWith("recycling", 1, null);
        int pts = c.skills.get("p").points;
        assertEquals(CrateRarity.RARE.duplicatePoints(), c.migrateRemoteGains());
        assertEquals(pts + CrateRarity.RARE.duplicatePoints(), c.skills.get("p").points);
        assertNull(c.skills.get("p").gains.get(SkillClass.ENGINEER.name()).get("recycling"));
        // once only
        assertEquals(0, c.migrateRemoteGains());
        // a fresh save: nothing to do
        SiegeSave fresh = new SiegeSave();
        assertEquals(0, fresh.migrateRemoteGains());
    }
}
