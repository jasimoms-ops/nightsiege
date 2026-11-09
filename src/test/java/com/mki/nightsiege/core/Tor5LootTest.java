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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 5.4: the loot list (qa/tor-5/loot.md, Chef 22:42) – gains, pool per subclass, drop test, save migration. */
class Tor5LootTest {

    private static final int DRAWS = 10_000;

    /** Own gains per subclass after Tor 5.4 (loot.md, section 2). */
    private static final Map<String, Integer> POOL = Map.ofEntries(
            Map.entry(SkillTree.SUB_BLADEMASTER, 12), Map.entry(SkillTree.SUB_BERSERKER, 12),
            Map.entry(SkillTree.SUB_AXE, 12), Map.entry(SkillTree.SUB_PALADIN, 12),
            Map.entry(SkillTree.SUB_SWIFT, 11), Map.entry(SkillTree.SUB_HEAVY, 11),
            Map.entry(SkillTree.SUB_BOLTER, 11), Map.entry(SkillTree.SUB_TRAPPER, 12),
            Map.entry(SkillTree.SUB_SHIELDFIGHTER, 12), Map.entry(SkillTree.SUB_BULWARK, 12),
            Map.entry(SkillTree.SUB_MEDIC, 13), Map.entry(SkillTree.SUB_WARDEN, 13),
            Map.entry(SkillTree.SUB_GUNNER, 13), Map.entry(SkillTree.SUB_ELECTRICIAN, 13),
            Map.entry(SkillTree.SUB_DEMOLITION, 13), Map.entry(SkillTree.SUB_ALCHEMIST, 13),
            Map.entry(SkillTree.SUB_TAMER, 11), Map.entry(SkillTree.SUB_GOLEMSMITH, 12),
            Map.entry(SkillTree.SUB_NECROMANCER, 12), Map.entry(SkillTree.SUB_SOULCATCHER, 12));

    @Test
    void theGainsAreAsDecided() {
        for (String gone : new String[]{"fackeln", "spektralpfeile", "fester_griff", "leuchtschild", "zweite_ladung", "recycling"}) {
            assertNull(CrateGains.get(gone), gone);
        }
        assertEquals(2, CrateGains.get("proviant").topStage());
        assertEquals(CrateRarity.RARE, CrateGains.get("federleicht").rarity());
        assertEquals(2, CrateGains.get("gestaehlt").topStage());
        assertEquals(2, CrateGains.get("schwung").stages());
        assertEquals(2, CrateGains.get("schwung").level(2), "schwung: one stage per draw now");
        assertEquals(2, CrateGains.get("verbandskasten").topStage());
        assertEquals(CrateRarity.UNCOMMON, CrateGains.get("brandpfeile").rarity());
        assertEquals(SkillClass.ARCHER, CrateGains.get("brandpfeile").clazz());
        assertEquals(CrateRarity.COMMON, CrateGains.get("rudelfuehrer").rarity());
        assertEquals(SkillTree.WAY_WILD, CrateGains.get("rudelfuehrer").way());
        assertEquals(CrateRarity.COMMON, CrateGains.get("grabesodem").rarity());
        assertEquals(SkillTree.WAY_DEAD, CrateGains.get("grabesodem").way());
        assertEquals(CrateRarity.RARE, CrateGains.get("treue").rarity());
        assertEquals("", CrateGains.get("treue").way());
        assertEquals(CrateRarity.RARE, CrateGains.get("rudelruf").rarity());
        assertEquals(SkillTree.WAY_WILD, CrateGains.get("rudelruf").way());
        for (CrateGains.Gain g : CrateGains.all()) {
            assertTrue(!CrateGains.icon(g.id()).equals("minecraft:chest"), g.id() + " has no icon");
        }
    }

    /**
     * 10 000 draws for each of the 20 subclasses with the odds of a normal night: the own pool has the size
     * of the list, every gain of it shows up, and every rarity comes as often as its weight says.
     */
    @Test
    void dropTestForEverySubclass() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        cfg.sanitize();
        int[] weights = cfg.crateOdds(CrateKind.values()[0]);
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        assertEquals(20, SkillTree.SUBCLASSES.size());
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            SkillClass c = info.clazz();
            Set<String> pool = new HashSet<>();
            int commons = 0;
            for (CrateGains.Gain g : CrateGains.all()) {
                if (g.clazz() == c && CrateGains.drawable(g, c, info.way(), info.key())) {
                    pool.add(g.id());
                    commons += g.rarity() == CrateRarity.COMMON ? 1 : 0;
                }
            }
            assertEquals(POOL.get(info.key()), pool.size(), info.key() + " pool " + pool);
            if (c == SkillClass.WARRIOR || c == SkillClass.ENGINEER || c == SkillClass.SUMMONER) {
                assertTrue(commons >= 3, info.key() + " has " + commons + " common own gains");
            }
            Random random = new Random(5_4_0L * 31 + info.key().hashCode());
            Map<String, Integer> seen = new HashMap<>();
            Map<CrateRarity, Integer> byRarity = new EnumMap<>(CrateRarity.class);
            for (int i = 0; i < DRAWS; i++) {
                CrateGains.Gain g = CrateGains.roll(c, info.way(), info.key(), weights, cfg.crateClassShare, random);
                assertNotNull(g, info.key());
                assertTrue(g.general() || pool.contains(g.id()), info.key() + " drew " + g.id());
                seen.merge(g.id(), 1, Integer::sum);
                byRarity.merge(g.rarity(), 1, Integer::sum);
            }
            for (String id : pool) {
                if (weights[CrateGains.get(id).rarity().ordinal()] > 0) {
                    assertTrue(seen.containsKey(id), info.key() + ": " + id + " never drawn in " + DRAWS);
                }
            }
            // General gains exist in every rarity, so no rarity steps down: each share matches its weight.
            for (CrateRarity r : CrateRarity.values()) {
                double want = weights[r.ordinal()] / (double) total;
                double got = byRarity.getOrDefault(r, 0) / (double) DRAWS;
                assertEquals(want, got, 0.02, info.key() + " share " + r);
            }
            System.out.println("[drop5] " + info.key() + " pool=" + pool.size() + " commons=" + commons + " " + byRarity);
        }
    }

    @Test
    void migrationMovesTheRetiredGainsToTheirSuccessors() {
        SiegeSave save = new SiegeSave();
        PlayerSkills own = new PlayerSkills();
        own.setGainStage(SkillClass.WARRIOR, "fackeln", 1);
        own.setGainStage(SkillClass.ARCHER, "fackeln", 1);
        own.setGainStage(SkillClass.ARCHER, "baukiste", 1);         // has the successor: points
        own.setGainStage(SkillClass.ARCHER, "spektralpfeile", 1);
        own.setGainStage(SkillClass.GUARDIAN, "fester_griff", 1);
        own.setGainStage(SkillClass.GUARDIAN, "leuchtschild", 1);
        own.setGainStage(SkillClass.GUARDIAN, "schildlaterne", 1);  // has the successor: points
        own.setGainStage(SkillClass.SUMMONER, "proviant", 4);       // two stages over the new top
        own.setGainStage(SkillClass.SUMMONER, "schutz", 2);         // at its top: untouched
        save.skills = new HashMap<>(Map.of("p", own));
        int before = own.points;
        int want = CrateRarity.COMMON.duplicatePoints() + CrateRarity.RARE.duplicatePoints() + 2 * CrateRarity.COMMON.duplicatePoints();
        assertEquals(want, save.migrateLoot5());
        assertEquals(before + want, own.points);
        assertEquals(1, own.gainStage(SkillClass.WARRIOR, "baukiste"));
        assertEquals(0, own.gainStage(SkillClass.WARRIOR, "fackeln"));
        assertEquals(1, own.gainStage(SkillClass.ARCHER, "baukiste"));
        assertEquals(1, own.gainStage(SkillClass.ARCHER, "lichtpfeile"));
        assertEquals(1, own.gainStage(SkillClass.GUARDIAN, "turmschild"));
        assertEquals(1, own.gainStage(SkillClass.GUARDIAN, "schildlaterne"));
        assertNull(own.gains.get(SkillClass.GUARDIAN.name()).get("leuchtschild"));
        assertEquals(2, own.gainStage(SkillClass.SUMMONER, "proviant"));
        assertEquals(2, own.gainStage(SkillClass.SUMMONER, "schutz"));
        assertEquals(0, save.migrateLoot5(), "once only");
        assertEquals(0, new SiegeSave().migrateLoot5(), "a fresh save");
    }
}
