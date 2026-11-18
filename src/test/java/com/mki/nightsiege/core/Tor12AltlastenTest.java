package com.mki.nightsiege.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 J (v32): the dead switches fall out of an old config file, and the live values stay as they were. */
class Tor12AltlastenTest {

    static final List<String> GONE = List.of("minDaysBetweenSieges", "maxDaysBetweenSieges", "requiredPlayers",
            "daysPerLevel", "playtestLivePerPlayer", "failureBlast", "failureBlastRadius", "failureBlastProtectsContainers",
            "bloodMoonGlow", "skillPointsPerSiege", "skillPointsPerKill", "championPointMultiplier", "skillPointsLevelStep",
            "tankDamagePerPoint", "defeatSkillLoss", "comboKillLifesteal", "berserkerLifesteal", "legacyDungeons",
            "lifestealCoupled", "lifestealTakenShare", "eliteMobs", "eliteMobFromLevel", "eliteMobChance",
            "eliteMobChanceMax", "eliteMobMaxLevel", "killQuotaShare", "quotaSkipsVanished", "targetGoalRate", "perksEnabled");

    @Test
    void oldServerFileLosesTheDeadKeys() throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/tor12/standard-v30.json"), StandardCharsets.UTF_8);
        JsonObject before = new Gson().fromJson(raw, JsonObject.class);
        for (String k : GONE) {
            assertTrue(before.has(k), "the old file has " + k);
        }
        SiegeConfig c = new Gson().fromJson(raw, SiegeConfig.class);
        assertTrue(c.settingsVersion < 32);
        c.sanitize();
        assertTrue(c.settingsVersion >= 32);
        JsonObject after = new Gson().toJsonTree(c).getAsJsonObject();
        for (String k : GONE) {
            assertFalse(after.has(k), k + " is gone from the written file");
        }
        // The live neighbours stay, with the file's values.
        assertEquals(before.get("lifestealTakenSeconds").getAsDouble(), c.lifestealTakenSeconds, 1e-9);
        assertEquals(before.get("eliteMobLife").getAsDouble(), c.eliteMobLife, 1e-9);
        assertEquals(before.get("eliteMobTwoChance").getAsDouble(), c.eliteMobTwoChance, 1e-9);
        assertEquals(before.get("perkRadius").getAsDouble(), c.perkRadius, 1e-9);
        assertEquals(before.get("playtestPlayerScaling").getAsDouble(), c.playtestPlayerScaling, 1e-9);
    }

    @Test
    void theLevelCountsWonNightsOnly() {
        SiegeConfig c = new SiegeConfig();
        assertEquals(1, SiegeMath.level(0, 400, c));   // days no longer count (daysPerLevel gone)
        assertEquals(6, SiegeMath.level(5, 400, c));
    }
}
