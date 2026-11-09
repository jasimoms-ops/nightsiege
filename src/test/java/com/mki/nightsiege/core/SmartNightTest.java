package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Schlaue Nacht (Simon 05.11. 20:26): the steps up and down and what they do. */
class SmartNightTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        return c;
    }

    /** Feeds a defender's life and some kills for {@code seconds}, one look per tick() when due. */
    private static int run(SmartNight n, SiegeConfig c, long[] now, int seconds, double avg, double min, int killsPerSecond,
                           int defenders) {
        int changes = 0;
        for (int i = 0; i < seconds; i++) {
            now[0] += 20;
            n.sample(now[0], avg, min);
            for (int k = 0; k < killsPerSecond; k++) {
                n.kill(now[0]);
            }
            SmartNight.Look l = n.tick(now[0], defenders, c);
            if (l != null && l.change() != 0) {
                changes += l.change();
            }
        }
        return changes;
    }

    @Test
    void stressMeasure() {
        SiegeConfig c = cfg();
        assertEquals(0.0, SmartNight.stress(1, 1, 0, c), 1e-9);
        assertEquals(0.09, SmartNight.stress(0.95, 0.85, 0, c), 1e-9);   // dominant: below 0.2
        assertTrue(SmartNight.stress(0.85, 0.6, 0, c) > c.smartNightUpStress);   // fighting: holds
        assertTrue(SmartNight.stress(0.85, 0.6, 0, c) < c.smartNightDownStress);
        assertTrue(SmartNight.stress(0.55, 0.25, 0, c) > c.smartNightDownStress);   // tight: down
        assertEquals(SmartNight.stress(1, 1, 0, c) + 0.35, SmartNight.stress(1, 1, 1, c), 1e-9);
        assertEquals(1.0, SmartNight.stress(1, 1, 9, c), 1e-9);   // deaths capped at 1
    }

    @Test
    void tooGoodStepsUpSlowlyToFive() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {1000};
        n.begin(now[0], true, c);
        assertEquals(0, n.step());
        // First look after 30 s, the second "too easy" one 15 s later: step 1 after 45 s.
        assertEquals(0, run(n, c, now, 44, 0.97, 0.9, 1, 2));
        assertEquals(1, run(n, c, now, 1, 0.97, 0.9, 1, 2));
        assertEquals(1, n.step());
        // Then one step every 45 s at most (sim schlau1: 30 s overshot for the pairs), up to +5.
        assertEquals(45, c.smartNightUpSeconds);
        run(n, c, now, 44, 0.97, 0.9, 1, 2);
        assertEquals(1, n.step());
        run(n, c, now, 1, 0.97, 0.9, 1, 2);
        assertEquals(2, n.step());
        run(n, c, now, 600, 0.97, 0.9, 1, 2);
        assertEquals(5, n.step());
        assertEquals(5, n.highest());
        assertEquals("ganz oben", n.last().why());
        assertFalse(n.paused(now[0]));   // up never pauses
    }

    @Test
    void noKillsNoStepUp() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        // Full life but nobody fights (2 kills in 30 s for two defenders): it holds.
        for (int i = 0; i < 120; i++) {
            now[0] += 20;
            n.sample(now[0], 1.0, 1.0);
            if (i % 15 == 0) {
                n.kill(now[0]);
            }
            n.tick(now[0], 2, c);
        }
        assertEquals(0, n.step());
        assertTrue(n.last().why().startsWith("zu wenig Kills"), n.last().why());
    }

    @Test
    void fightingHolds() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        assertEquals(0, run(n, c, now, 300, 0.8, 0.55, 2, 2));
        assertEquals(0, n.step());
        assertEquals("passt", n.last().why());
    }

    @Test
    void tightStepsDownWithPauseToMinusFive() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        assertEquals(-1, run(n, c, now, 30, 0.5, 0.2, 1, 2));
        assertEquals(-1, n.step());
        assertTrue(n.paused(now[0]));
        assertTrue(n.paused(now[0] + 159));
        assertFalse(n.paused(now[0] + 161));   // 8 s
        // The next only 15 s later.
        run(n, c, now, 14, 0.5, 0.2, 1, 2);
        assertEquals(-1, n.step());
        run(n, c, now, 1, 0.5, 0.2, 1, 2);
        assertEquals(-2, n.step());
        run(n, c, now, 600, 0.5, 0.2, 1, 2);
        assertEquals(-5, n.step());
        assertEquals(-5, n.lowest());
        assertEquals(5, n.pauses());
    }

    @Test
    void deathStepsDownAtOnceButNotTwiceInFiveSeconds() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        run(n, c, now, 100, 0.97, 0.9, 1, 2);
        int before = n.step();
        assertTrue(before >= 2, "up first: " + before);
        now[0] += 200;   // 10 s after
        SmartNight.Look l = n.death(now[0], 2, c);
        assertEquals(-1, l.change());
        assertEquals("Tod", l.why());
        assertEquals(before - 1, n.step());
        assertTrue(n.paused(now[0]));
        SmartNight.Look again = n.death(now[0] + 40, 2, c);   // 2 s later
        assertEquals(0, again.change());
        assertEquals(before - 1, n.step());
    }

    @Test
    void deathsWeighByTheGroup() {
        SiegeConfig c = cfg();
        assertEquals(1.0, SmartNight.deathWeight(1), 1e-9);
        assertEquals(1.0, SmartNight.deathWeight(3), 1e-9);
        assertEquals(0.5, SmartNight.deathWeight(6), 1e-9);
        assertEquals(0.3, SmartNight.deathWeight(10), 1e-9);
        assertEquals(1, SmartNight.lowestCount(1));
        assertEquals(1, SmartNight.lowestCount(3));
        assertEquals(2, SmartNight.lowestCount(4));
        assertEquals(4, SmartNight.lowestCount(10));
        // Ten defenders, all well: one death does not step down at once, four in the window do.
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        run(n, c, now, 60, 0.9, 0.8, 3, 10);
        int before = n.step();
        now[0] += 200;
        SmartNight.Look one = n.death(now[0], 10, c);
        assertEquals(0, one.change(), one.why());
        n.death(now[0] + 20, 10, c);
        n.death(now[0] + 40, 10, c);
        n.death(now[0] + 60, 10, c);
        assertEquals(before - 1, n.step());   // the third or the fourth steps it down (once: 15 s apart)
    }

    @Test
    void comesBackFromBelowWhenCalm() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        run(n, c, now, 90, 0.5, 0.2, 1, 1);
        assertTrue(n.step() <= -2);
        // Calm (stress 0.25: above the bar of 0.2, below 0.3 for a night under 0): back up to 0, then it holds.
        run(n, c, now, 400, 0.85, 0.6, 2, 1);
        assertEquals(0, n.step());
    }

    @Test
    void offStaysAtZero() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], false, c);
        run(n, c, now, 300, 1.0, 1.0, 3, 1);
        assertEquals(0, n.step());
        assertNull(n.death(now[0], 1, c));
        assertFalse(n.paused(now[0]));
    }

    @Test
    void beginResets() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        long[] now = {0};
        n.begin(now[0], true, c);
        run(n, c, now, 300, 0.97, 0.9, 2, 1);
        assertTrue(n.step() > 0);
        n.begin(now[0], true, c);
        assertEquals(0, n.step());
        assertEquals(0, n.highest());
        assertTrue(n.steps().isEmpty());
    }

    @Test
    void force() {
        SiegeConfig c = cfg();
        SmartNight n = new SmartNight();
        n.begin(0, true, c);
        n.force(100, 9, c);
        assertEquals(5, n.step());
        n.force(200, -9, c);
        assertEquals(-5, n.step());
        assertTrue(n.paused(200));
        assertEquals(2, n.steps().size());
    }

    @Test
    void levers() {
        SiegeConfig c = cfg();
        // Elites: from level 5, 5 % per step, none at or below 0.
        assertEquals(0.0, SmartNight.eliteChance(c, 0, 20));
        assertEquals(0.0, SmartNight.eliteChance(c, 3, 4));
        assertEquals(0.05, SmartNight.eliteChance(c, 1, 5), 1e-9);
        assertEquals(0.25, SmartNight.eliteChance(c, 5, 5), 1e-9);
        // Special monsters: two levels earlier per step, extras from step 2, every class from step 4.
        assertEquals(0, SmartNight.levelBonus(c, -3));
        assertEquals(6, SmartNight.levelBonus(c, 3));
        assertEquals(0, SmartNight.extraPerWave(1));
        assertEquals(1, SmartNight.extraPerWave(2));
        assertEquals(1, SmartNight.extraPerWave(3));
        assertEquals(2, SmartNight.extraPerWave(5));
        assertEquals(5, SmartNight.extraAlive(5));
        assertFalse(SmartNight.anyClass(c, 3));
        assertTrue(SmartNight.anyClass(c, 4));
        // Down: 5 % fewer at once per step (−25 % at −5), no specials and no elites from −2.
        assertEquals(1.0, SmartNight.room(c, 3));
        assertEquals(0.97, SmartNight.room(c, -1), 1e-9);
        assertEquals(0.85, SmartNight.room(c, -5), 1e-9);
        assertTrue(SmartNight.specials(c, -1));
        assertFalse(SmartNight.specials(c, -2));
        assertTrue(SmartNight.specials(c, 5));
    }

    @Test
    void defaults() {
        SiegeConfig c = cfg();
        assertTrue(c.smartNight);
        assertFalse(c.eliteMobs);   // Simon 05.11. 19:51
        assertFalse(c.lifestealCoupled);   // Simon 05.11. 19:48
        assertEquals(5, c.smartNightUpSteps);
        assertEquals(5, c.smartNightDownSteps);
        assertEquals(5, c.smartNightEliteFromLevel);
    }
}
