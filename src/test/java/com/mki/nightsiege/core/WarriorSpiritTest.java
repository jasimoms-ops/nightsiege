package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The warrior rework (Simon 05.11.): Kampfgeist, the combo chain and its finishers, the F abilities. */
class WarriorSpiritTest {

    private static final String[] SUBS = {SkillTree.SUB_BLADEMASTER, SkillTree.SUB_BERSERKER, SkillTree.SUB_AXE, SkillTree.SUB_PALADIN};

    @Test
    void meterValuesAreSkillValues() {
        assertEquals(100, SkillValues.get("w_spirit", "max", -1), 1e-9);
        assertEquals(4, SkillValues.get("w_spirit", "hit", -1), 1e-9);
        assertEquals(10, SkillValues.get("w_spirit", "kill", -1), 1e-9);
        assertEquals(6, SkillValues.get("w_spirit", "hurt", -1), 1e-9);
        assertEquals(100, SkillValues.get("w_spirit", "idle", -1), 1e-9);   // 5 s
        assertEquals(5, SkillValues.get("w_spirit", "decay", -1), 1e-9);
        assertEquals(50, SkillValues.get("w_spirit", "glow", -1), 1e-9);
        assertEquals(50, SkillValues.get("w_spirit", "cost", -1), 1e-9);
        assertEquals(4, SkillValues.get("w_spirit", "chain", -1), 1e-9);
        assertEquals(40, SkillValues.get("w_spirit", "window", -1), 1e-9);   // 2 s
        assertEquals(30, SkillValues.get("w_spirit", "empcost", -1), 1e-9);
        assertEquals(2.0, SkillValues.get("w_spirit", "empfactor", -1), 1e-9);
        // The helpers read the same numbers.
        assertEquals(100, WarriorSpiritMath.max(), 1e-9);
        assertEquals(4, WarriorSpiritMath.chainHits());
        assertEquals(40, WarriorSpiritMath.chainWindow());
        assertEquals(50, WarriorSpiritMath.abilityCost(), 1e-9);
    }

    /** Simon 05.11.: the blademaster's fast clicks (his combo, no cooldown) must fill the chain too. */
    @Test
    void blademasterSwingsCountByHisComboRhythm() {
        assertEquals(5, WarriorSpiritMath.comboGap());   // 4 full hits a second
        // Others: only a charged swing counts.
        assertTrue(WarriorSpiritMath.swingCounts(0.8, false, 100, 99));
        assertFalse(WarriorSpiritMath.swingCounts(0.5, false, 100, 0));
        // Blademaster: a charged swing counts, an uncharged one 5 ticks after his last counted swing too.
        assertTrue(WarriorSpiritMath.swingCounts(0.9, true, 100, 98));
        assertTrue(WarriorSpiritMath.swingCounts(0.3, true, 105, 100));
        assertFalse(WarriorSpiritMath.swingCounts(0.3, true, 104, 100));
        // Clicking every 2 ticks for a second counts at most 4 swings.
        long last = Long.MIN_VALUE / 4;
        int counted = 0;
        for (long tick = 0; tick < 20; tick += 2) {
            if (WarriorSpiritMath.swingCounts(0.4, true, tick, last)) {
                counted++;
                last = tick;
            }
        }
        assertTrue(counted >= 3 && counted <= 4, "counted " + counted);
    }

    @Test
    void finisherAndAbilityValuesOfEverySubclass() {
        assertEquals(2.5, SkillValues.get("w_combo", "fin_radius", -1), 1e-9);
        assertEquals(0.6, SkillValues.get("w_combo", "fin_share", -1), 1e-9);
        assertEquals(3, SkillValues.get("w_combo", "f_jumps", -1), 1e-9);
        assertEquals(6, SkillValues.get("w_combo", "f_radius", -1), 1e-9);
        assertEquals(0.5, SkillValues.get("bloodlust", "fin_bonus", -1), 1e-9);
        assertEquals(0.1, SkillValues.get("bloodlust", "fin_perlost", -1), 1e-9);
        assertEquals(60, SkillValues.get("bloodlust", "fin_bleedtime", -1), 1e-9);
        assertEquals(160, SkillValues.get("bloodlust", "f_time", -1), 1e-9);
        assertEquals(0.5, SkillValues.get("bloodlust", "f_speed", -1), 1e-9);
        assertEquals(2.0, SkillValues.get("bloodlust", "f_steal", -1), 1e-9);
        assertEquals(0.15, SkillValues.get("bloodlust", "f_taken", -1), 1e-9);
        assertEquals(20, SkillValues.get("bloodlust", "f_extend", -1), 1e-9);
        assertEquals(0.3, SkillValues.get("heavy_blow", "fin_armor", -1), 1e-9);
        assertEquals(80, SkillValues.get("heavy_blow", "fin_time", -1), 1e-9);
        assertEquals(20, SkillValues.get("heavy_blow", "fin_slow", -1), 1e-9);
        assertEquals(12, SkillValues.get("heavy_blow", "f_range", -1), 1e-9);
        assertEquals(2, SkillValues.get("smite", "fin_heal", -1), 1e-9);
        assertEquals(6, SkillValues.get("smite", "fin_radius", -1), 1e-9);
        assertEquals(60, SkillValues.get("smite", "fin_burn", -1), 1e-9);
        assertEquals(6, SkillValues.get("smite", "f_radius", -1), 1e-9);
        assertEquals(160, SkillValues.get("smite", "f_time", -1), 1e-9);
        for (String sub : SUBS) {
            assertTrue(WarriorSpiritMath.isWarriorSub(sub), sub);
            String node = WarriorSpiritMath.nodeOf(sub);
            assertTrue(WarriorSpiritMath.isSubNode(node), sub);
            assertEquals(sub, SkillTree.subclassByNode(node).key(), sub);
            assertTrue(SkillValues.get(node, "f_cd", -1) > 0, sub + " has a pause");
            assertEquals(SkillValues.get(node, "f_cd", -1), WarriorSpiritMath.abilityCooldown(sub), 1e-9, sub);
            assertFalse(WarriorSpiritMath.finisherId(sub).isEmpty());
            assertFalse(WarriorSpiritMath.abilityId(sub).isEmpty());
            assertTrue(WarriorSpiritMath.abilityIcon(sub).startsWith("minecraft:"));
        }
        assertFalse(WarriorSpiritMath.isWarriorSub(SkillTree.SUB_SWIFT));
        assertFalse(WarriorSpiritMath.isWarriorSub(""));
        assertFalse(WarriorSpiritMath.isWarriorSub(null));
    }

    @Test
    void meterFillsAndStaysBetweenZeroAndFull() {
        double v = 0.0;
        for (int i = 0; i < 24; i++) {
            v = WarriorSpiritMath.add(v, WarriorSpiritMath.gainHit());
        }
        assertEquals(96, v, 1e-9);   // 24 hits
        v = WarriorSpiritMath.add(v, WarriorSpiritMath.gainKill());
        assertEquals(100, v, 1e-9);   // never more than full
        assertEquals(0, WarriorSpiritMath.add(10, -30), 1e-9);
        assertEquals(6, WarriorSpiritMath.add(0, WarriorSpiritMath.gainHurt()), 1e-9);
        assertFalse(WarriorSpiritMath.glows(49.9));
        assertTrue(WarriorSpiritMath.glows(50));
        assertFalse(WarriorSpiritMath.canUseAbility(49));
        assertTrue(WarriorSpiritMath.canUseAbility(50));
    }

    @Test
    void meterFallsOnlyAfterFiveSecondsWithoutFighting() {
        assertEquals(40, WarriorSpiritMath.decayTick(40, 0), 1e-9);
        assertEquals(40, WarriorSpiritMath.decayTick(40, 100), 1e-9);   // still within the 5 s
        assertEquals(39.75, WarriorSpiritMath.decayTick(40, 101), 1e-9);   // 5 a second = 0.25 a tick
        double v = 40;
        for (long t = 1; t <= 140; t++) {
            v = WarriorSpiritMath.decayTick(v, t);
        }
        assertEquals(30, v, 1e-9);   // 40 ticks of falling: 2 s, 10 less
        assertEquals(0, WarriorSpiritMath.decayTick(0.1, 500), 1e-9);
    }

    @Test
    void everyFourthHitInARowIsTheFinisher() {
        int chain = 0;
        long last = -1000;
        int finishers = 0;
        for (int hit = 1; hit <= 12; hit++) {
            long now = hit * 15L;   // a hit every 0.75 s
            chain = WarriorSpiritMath.chainAfterHit(chain, last, now);
            last = now;
            if (WarriorSpiritMath.isFinisher(chain)) {
                finishers++;
                assertEquals(0, hit % 4, "finisher on hit " + hit);
            }
        }
        assertEquals(3, finishers);
        // A pause longer than 2 s breaks the chain ...
        chain = WarriorSpiritMath.chainAfterHit(0, 0, 0);
        chain = WarriorSpiritMath.chainAfterHit(chain, 0, 20);
        assertEquals(2, chain);
        assertEquals(2, WarriorSpiritMath.chainNow(chain, 20, 60));
        assertEquals(0, WarriorSpiritMath.chainNow(chain, 20, 61));
        assertEquals(1, WarriorSpiritMath.chainAfterHit(chain, 20, 61));
        // ... but with only the finisher left it waits 6 s (Kombo 2, Simon 06.11.).
        chain = WarriorSpiritMath.chainAfterHit(chain, 0, 40);
        assertEquals(3, chain);
        assertTrue(WarriorSpiritMath.finisherReady(chain, 4));
        assertEquals(3, WarriorSpiritMath.chainNow(chain, 40, 160));
        assertEquals(0, WarriorSpiritMath.chainNow(chain, 40, 161));
        assertEquals(4, WarriorSpiritMath.chainAfterHit(chain, 40, 160));
        assertEquals(1, WarriorSpiritMath.chainAfterHit(chain, 40, 161));
        // After a finisher the next hit starts anew.
        assertEquals(1, WarriorSpiritMath.chainAfterHit(4, 100, 105));
    }

    /** Kombo 2 (Simon 06.11.): the axe fighter's chain is 3 hits, the finisher waits 6 s once it is next. */
    @Test
    void axeChainIsThreeAndTheFinisherWaits() {
        assertEquals(3, WarriorSpiritMath.chainHits(SkillTree.SUB_AXE));
        assertEquals(4, WarriorSpiritMath.chainHits(SkillTree.SUB_BERSERKER));
        assertEquals(4, WarriorSpiritMath.chainHits(SkillTree.SUB_PALADIN));
        assertEquals(4, WarriorSpiritMath.chainHits(SkillTree.SUB_BLADEMASTER));
        assertEquals(120, WarriorSpiritMath.readyWindow());
        assertEquals(40, WarriorSpiritMath.windowFor(1, 3));
        assertEquals(120, WarriorSpiritMath.windowFor(2, 3));
        assertFalse(WarriorSpiritMath.finisherReady(0, 1));
        // One-hit kills with a few steps in between: hit, 1.5 s, hit, 5 s walking, finisher.
        int chain = WarriorSpiritMath.chainAfterHit(0, -1000, 0, 3);
        chain = WarriorSpiritMath.chainAfterHit(chain, 0, 30, 3);
        assertEquals(2, chain);
        chain = WarriorSpiritMath.chainAfterHit(chain, 30, 130, 3);
        assertEquals(3, chain);
        assertTrue(WarriorSpiritMath.isFinisher(chain, 3));
        assertFalse(WarriorSpiritMath.isFinisher(3, 4));
    }

    @Test
    void fullMeterEmpowersForThirty() {
        assertFalse(WarriorSpiritMath.empowers(99));
        assertTrue(WarriorSpiritMath.empowers(100));
        assertEquals(30, WarriorSpiritMath.empowerCost(), 1e-9);
        assertEquals(2.0, WarriorSpiritMath.empowerFactor(), 1e-9);
    }

    @Test
    void bloodyStrikeGrowsWithTheMissingLife() {
        assertEquals(0.5, WarriorSpiritMath.bloodyBonus(1.0), 1e-9);
        assertEquals(0.5, WarriorSpiritMath.bloodyBonus(0.95), 1e-9);   // not a full tenth missing
        assertEquals(0.6, WarriorSpiritMath.bloodyBonus(0.9), 1e-9);
        assertEquals(0.9, WarriorSpiritMath.bloodyBonus(0.55), 1e-9);
        assertEquals(1.5, WarriorSpiritMath.bloodyBonus(0.0), 1e-9);
        assertEquals(0.3, WarriorSpiritMath.cleaveArmour(1.0), 1e-9);
        assertEquals(0.6, WarriorSpiritMath.cleaveArmour(2.0), 1e-9);   // empowered: double
        assertEquals(0.95, WarriorSpiritMath.cleaveArmour(10.0), 1e-9);
    }

    @Test
    void textsInBothLanguages() throws Exception {
        String[] keys = {"nightsiege.kithud.spirit", "nightsiege.msg.spirit_fight_only", "nightsiege.msg.spirit_wait",
                "nightsiege.msg.spirit_low", "nightsiege.msg.spirit_no_target", "nightsiege.msg.spirit_used",
                "nightsiege.msg.finisher_empowered"};
        for (String lang : new String[]{"de_de", "en_us"}) {
            String json = Files.readString(Path.of("src/main/resources/assets/nightsiege/lang/" + lang + ".json"), StandardCharsets.UTF_8);
            for (String key : keys) {
                assertTrue(json.contains("\"" + key + "\""), lang + " lacks " + key);
            }
            for (String sub : SUBS) {
                assertTrue(json.contains("\"nightsiege.finisher." + WarriorSpiritMath.finisherId(sub) + "\""), lang + " finisher " + sub);
                assertTrue(json.contains("\"nightsiege.warrior_f." + WarriorSpiritMath.abilityId(sub) + "\""), lang + " F " + sub);
            }
        }
    }

    /** Every number in the subclass texts comes from a skill value (its node's, or the meter's as sp_…). */
    @Test
    void subclassTextsShowOnlyRealNumbers() throws Exception {
        Pattern token = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)(\\*)?(?:\\|([%hsx+]+))?}");
        Map<String, Double> spirit = SkillValues.of(WarriorSpiritMath.NODE);
        for (String lang : new String[]{"de_de", "en_us"}) {
            String json = Files.readString(Path.of("src/main/resources/assets/nightsiege/lang/" + lang + ".json"), StandardCharsets.UTF_8);
            for (String sub : SUBS) {
                String node = WarriorSpiritMath.nodeOf(sub);
                String key = "\"nightsiege.skill." + node + ".desc\": \"";
                int at = json.indexOf(key);
                assertTrue(at >= 0, lang + " " + node);
                String text = json.substring(at + key.length(), json.indexOf("\",\n", at));
                assertTrue(text.contains("{f_cd|s}") && text.contains("{sp_chain}"), lang + " " + node + " tells of F and the finisher");
                Map<String, Double> values = SkillValues.of(node);
                Matcher m = token.matcher(text);
                while (m.find()) {
                    String name = m.group(1);
                    boolean ok = name.equals("a") || name.equals("m")
                            || (m.group(2) != null ? values.containsKey(name + "1") : values.containsKey(name))
                            || name.startsWith("sp_") && spirit.containsKey(name.substring(3));
                    assertTrue(ok, lang + " " + node + ": {" + name + "} has no value");
                }
            }
        }
    }
}
