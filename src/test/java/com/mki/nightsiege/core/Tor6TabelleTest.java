package com.mki.nightsiege.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 6.2 (Chef 09.11.): the Grundtabelle is only built in; with its standard factors every wave stays as today. */
class Tor6TabelleTest {

    /** Today's batch before the Grundtabelle (SiegeMath.mobsWanted up to 0a3fad39f). */
    private static double oldWanted(int level, int n, SiegeConfig c, double perPlayer) {
        double raw = c.baseMobsPerBatch + c.mobsPerLevel * SiegeMath.growthSteps(level, c);
        double players = Math.max(0.0, c.soloScaling) + Math.max(0.0, perPlayer) * Math.max(0, n - 1);
        return raw * players;
    }

    private static void sameAsToday(SiegeConfig c, String name) {
        for (boolean playtest : new boolean[]{false, true}) {
            double perPlayer = c.perPlayer(playtest, c.playerScaling);
            for (int level = 1; level <= 25; level++) {
                for (int n = 1; n <= 10; n++) {
                    String at = name + " L" + level + " n" + n + (playtest ? " playtest" : "");
                    double old = oldWanted(level, n, c, perPlayer);
                    assertEquals(old, SiegeMath.mobsWanted(level, n, c, perPlayer), 0.0, at);
                    int cap = SiegeMath.liveCap(n, c);
                    int oldBatch = Math.max(1, Math.min(Math.max(1, cap), (int) Math.round(old)));
                    assertEquals(oldBatch, SiegeMath.mobsPerBatch(level, n, c, perPlayer, cap), at);
                }
            }
        }
    }

    @Test
    void freshConfigKeepsTodaysWaves() {
        SiegeConfig c = new Gson().fromJson("{}", SiegeConfig.class);
        c.sanitize();
        assertEquals(List.of(6, 11, 20), c.budgetJumpLevels);
        assertEquals(List.of(1.0, 1.0, 1.0), c.budgetJumpFactors);
        assertEquals(List.of(1, 2, 3, 5, 7), c.budgetGroupFrom);
        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0), c.budgetGroupFactors);
        sameAsToday(c, "frisch");
    }

    @Test
    void serverConfigKeepsTodaysWaves() throws Exception {
        String json = Files.readString(Path.of("tools/sim/server-config.json"), StandardCharsets.UTF_8);
        SiegeConfig c = new Gson().fromJson(json, SiegeConfig.class);
        c.sanitize();
        assertEquals(1.0, SiegeMath.budgetFactor(25, 10, c), 0.0);
        sameAsToday(c, "server");
    }

    @Test
    void jumpsAndBucketsActWhereTheyShould() {
        SiegeConfig c = new SiegeConfig();
        c.budgetJumpFactors = new ArrayList<>(List.of(1.2, 1.0, 0.5));
        c.budgetGroupFactors = new ArrayList<>(List.of(1.0, 1.0, 1.1, 1.0, 0.8));
        c.sanitize();
        assertEquals(1.0, SiegeMath.budgetFactor(5, 1, c), 1e-12);
        assertEquals(1.2, SiegeMath.budgetFactor(6, 1, c), 1e-12);
        assertEquals(0.6, SiegeMath.budgetFactor(20, 2, c), 1e-12);
        assertEquals(1.2 * 1.1, SiegeMath.budgetFactor(10, 4, c), 1e-12);
        assertEquals(0.8, SiegeMath.budgetFactor(1, 7, c), 1e-12);
        assertEquals(0.8, SiegeMath.budgetFactor(1, 10, c), 1e-12);
        assertEquals(oldWanted(6, 1, c, c.playerScaling) * 1.2, SiegeMath.mobsWanted(6, 1, c, c.playerScaling), 1e-9);
    }

    @Test
    void brokenListsFallBackToNeutral() {
        SiegeConfig c = new SiegeConfig();
        c.budgetJumpLevels = null;
        c.budgetJumpFactors = new ArrayList<>(List.of(Double.NaN));
        c.budgetGroupFactors = null;
        c.sanitize();
        assertEquals(3, c.budgetJumpFactors.size());
        assertEquals(5, c.budgetGroupFactors.size());
        assertTrue(c.budgetJumpFactors.stream().allMatch(v -> v == 1.0));
        sameAsToday(c, "kaputt");
    }
}
