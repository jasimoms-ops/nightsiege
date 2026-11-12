package com.mki.nightsiege.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bossnacht neu (Simon 11.11.): the boss after 3 minutes, the others withdraw, the boss's fall wins. */
class BossNightTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
    }

    @Test
    void bossComesThreeMinutesIntoTheNight() {
        SiegeConfig c = cfg();
        assertEquals(180, c.bossFinaleSeconds);
        int total = 20 * 60 * 12;   // a 12-minute night
        int arrival = BossNight.arrivalTicks(total, c);
        assertEquals(3600, arrival);   // 3:00 after the start, not before dawn
        assertFalse(BossNight.due(0, arrival));   // 1: it starts as a normal night
        assertFalse(BossNight.due(3599, arrival));
        assertTrue(BossNight.due(3600, arrival));   // 2: at 3:00 the boss comes
        assertEquals(2000, BossNight.arrivalTicks(4000, c));   // a short night: at most half of it
        assertEquals(BossNight.Phase.BEFORE_BOSS, BossNight.phase(false));
        assertEquals("vor_boss", BossNight.phase(false).label());
        assertEquals("boss", BossNight.phase(true).label());
    }

    @Test
    void othersWithdrawTheBossAndHisGuardStay() {
        assertTrue(BossNight.withdraws(false, false));   // a monster of the waves
        assertFalse(BossNight.withdraws(true, false));   // the boss or a part of him
        assertFalse(BossNight.withdraws(false, true));   // his guard
        // gone after 10 s at the latest, at once when stuck
        assertFalse(BossNight.vanishes(19, false, Double.NaN));
        assertFalse(BossNight.vanishes(20, true, 3.0));   // it runs: it may go on
        assertTrue(BossNight.vanishes(20, true, 0.2));   // stuck: gone now
        assertFalse(BossNight.vanishes(199, false, Double.NaN));
        assertTrue(BossNight.vanishes(200, false, Double.NaN));
        assertTrue(BossNight.vanishes(BossNight.WITHDRAW_MAX_TICKS, true, 5.0));
    }

    @Test
    void noReinforcementsOnceTheBossCame() {
        assertTrue(BossNight.reinforcements(true, false));   // before the boss: waves as in any night
        assertFalse(BossNight.reinforcements(true, true));   // 3: no waves, machines, camp reinforcements
        assertTrue(BossNight.reinforcements(false, false));
    }

    @Test
    void theKillGoalDoesNotCountInABossNight() {
        assertFalse(BossNight.killGoalCounts(true));   // 4: the boss's fall alone wins
        assertTrue(BossNight.killGoalCounts(false));
    }

    @Test
    void withdrawingMonstersHarmNobody() {
        assertFalse(BossNight.hitAllowed(true));   // a slime's touch, a ravager's blow, its arrow in flight
        assertTrue(BossNight.hitAllowed(false));
    }

    @Test
    void aDefendersHitOnTheWitchsPhantomGoesToHer() {
        assertTrue(BossNight.mountHitGoesToRider(true, true));   // an arrow into the phantom: the witch takes it
        assertFalse(BossNight.mountHitGoesToRider(false, true));   // her own lightning, a monster, the world
        assertFalse(BossNight.mountHitGoesToRider(true, false));   // she landed and got off: the phantom takes nothing
    }

    @Test
    void monstersLoadedAfterTheBossCameGo() {
        assertTrue(BossNight.lateStray(true, true, false, false, false));   // unloaded at the withdrawal, loads now
        assertFalse(BossNight.lateStray(false, true, false, false, false));   // before the boss: nothing
        assertFalse(BossNight.lateStray(true, true, true, false, false));   // a monster of the night now (a boss add)
        assertFalse(BossNight.lateStray(true, true, false, true, false));   // the boss, a part, a guard or their summons
        assertFalse(BossNight.lateStray(true, true, false, false, true));   // a companion
        assertFalse(BossNight.lateStray(true, false, false, false, false));   // no monster of the night
    }

    @Test
    void noEventInABossNight() {
        assertEquals(SiegeEvent.NONE, BossNight.eventFor(true, SiegeEvent.ECLIPSE));
        assertEquals(SiegeEvent.NONE, BossNight.eventFor(true, SiegeEvent.BLOOD_MOON));
        assertEquals(SiegeEvent.ECLIPSE, BossNight.eventFor(false, SiegeEvent.ECLIPSE));
        assertEquals(SiegeEvent.BLOOD_MOON, BossNight.eventFor(false, SiegeEvent.BLOOD_MOON));
    }

    @Test
    void bossLifeInPercent() {
        assertEquals(100.0, BossNight.lifePercent(false, false, 0, 0), 1e-9);
        assertEquals(62.5, BossNight.lifePercent(true, false, 625, 1000), 1e-9);
        assertEquals(0.0, BossNight.lifePercent(true, true, 400, 1000), 1e-9);
    }

    @Test
    void bossClockHoldsTheDawnOnlyWhenSwitchedOn() {
        SiegeConfig c = cfg();
        assertTrue(c.bossPhaseHoldsDawn);   // on by default (Chef 12.11. 07:50)
        assertEquals(600, c.bossPhaseMaxSeconds);
        assertFalse(BossNight.holdsDawn(false, true, false));   // off: dawn ends the night as before
        assertFalse(BossNight.holdsDawn(true, false, false));   // on, before the boss: the normal clock
        assertTrue(BossNight.holdsDawn(true, true, false));     // on, boss stands: no dawn
        assertFalse(BossNight.holdsDawn(true, true, true));     // boss fell: the night is won (no hold)
        int max = c.bossPhaseMaxSeconds;
        int limit = BossNight.phaseLimitTicks(420 * 20, max);   // boss at 3:00 of a 10-minute night: 7:00 left
        assertEquals(max * 20, limit);                           // the clock gives 10:00
        assertEquals(800 * 20, BossNight.phaseLimitTicks(800 * 20, max));   // never shorter than the normal night
        assertFalse(BossNight.phaseOver(limit - 1, limit));    // the boss phase still runs
        assertTrue(BossNight.phaseOver(limit, limit));         // after the limit: lost
        assertEquals(limit, BossNight.phaseTicksLeft(0, limit));
        assertEquals(20, BossNight.phaseTicksLeft(limit - 20, limit));
        assertEquals(0, BossNight.phaseTicksLeft(limit + 5, limit));
        assertEquals("10:00", BossNight.clock(BossNight.phaseTicksLeft(0, limit)));   // the bar: m:ss
        assertEquals("1:00", BossNight.clock(1200));
        assertEquals("0:09", BossNight.clock(199));
        assertFalse(BossNight.warnNow(1220));   // the HUD warning at 1:00, for three seconds
        assertTrue(BossNight.warnNow(1200));
        assertFalse(BossNight.warnNow(1199));
        assertTrue(BossNight.warnNow(1160));
        assertFalse(BossNight.warnNow(1140));
        c.bossPhaseMaxSeconds = 10;
        c.sanitize();
        assertEquals(60, c.bossPhaseMaxSeconds);
        c.bossPhaseMaxSeconds = 5000;
        c.sanitize();
        assertEquals(1800, c.bossPhaseMaxSeconds);
        c.bossPhaseMaxSeconds = 0;
        c.sanitize();
        assertEquals(600, c.bossPhaseMaxSeconds);
    }

    @Test
    void lichSwitchesKeepTheOldLichByDefault() {
        SiegeConfig c = new SiegeConfig();
        assertEquals(30.0, c.lichCrystalHealth, 1e-9);   // boss sim 12.11.: the switches exist, nothing moves yet
        assertEquals(SiegeConfig.BOSS_LICH_SHIELD_SECONDS, c.lichShieldMaxSeconds);   // Paket 11.11., Kv5
        c.lichCrystalHealth = -1;
        c.lichShieldMaxSeconds = 5000;
        c.sanitize();
        assertEquals(30.0, c.lichCrystalHealth, 1e-9);
        assertEquals(1800, c.lichShieldMaxSeconds);
        c.lichCrystalHealth = 12.5;
        c.lichShieldMaxSeconds = -3;
        c.sanitize();
        assertEquals(12.5, c.lichCrystalHealth, 1e-9);
        assertEquals(0, c.lichShieldMaxSeconds);
    }

    @Test
    void preWavesWeakerOnlyInABossNightBeforeTheBoss() {
        // Vorwelle (Simon 12.11.: "ja dürfen sie"): the waves of a boss night may be weaker until the boss comes.
        SiegeConfig c = cfg();
        assertEquals(SiegeConfig.BOSS_PRE_WAVE, c.bossPreWaveFactor, 1e-9);   // standard: Paket 11.11., Kv5
        assertEquals(SiegeConfig.BOSS_PRE_WAVE, BossNight.preWaveFactor(true, false, c.bossPreWaveFactor), 1e-9);
        c.bossPreWaveFactor = 0.7;
        c.sanitize();
        assertEquals(0.7, c.bossPreWaveFactor, 1e-9);
        assertEquals(0.7, BossNight.preWaveFactor(true, false, c.bossPreWaveFactor), 1e-9);   // boss night, before the boss
        assertEquals(1.0, BossNight.preWaveFactor(true, true, c.bossPreWaveFactor), 1e-9);    // the boss came
        assertEquals(1.0, BossNight.preWaveFactor(false, false, c.bossPreWaveFactor), 1e-9);  // normal, Vierer, late, bastion, war
        assertEquals(1.0, BossNight.preWaveFactor(true, false, Double.NaN), 1e-9);
        assertEquals(0.2, BossNight.preWaveFactor(true, false, 0.05), 1e-9);   // an unsanitized sim value
        c.bossPreWaveFactor = 0.05;
        c.sanitize();
        assertEquals(0.2, c.bossPreWaveFactor, 1e-9);
        c.bossPreWaveFactor = 3.0;
        c.sanitize();
        assertEquals(1.0, c.bossPreWaveFactor, 1e-9);
        c.bossPreWaveFactor = Double.NaN;
        c.sanitize();
        assertEquals(1.0, c.bossPreWaveFactor, 1e-9);
    }

    @Test
    void fewerMachinesNeverCarryOver() {
        // Vorwelle: a camp's 6 machines, 0.5 tonight = 3; broken off, the next night gets all 6 again.
        int tonight = (int) Math.round(6 * BossNight.preWaveFactor(true, false, 0.5));
        assertEquals(3, tonight);
        assertEquals(6, BossNight.machinesCarried(tonight, 6));
        assertEquals(6, BossNight.machinesCarried(6, 6));   // factor 1: as before
        assertEquals(4, BossNight.machinesCarried(4, 0));   // random machines (no camp): as before
        assertEquals(0, BossNight.machinesCarried(0, 6));   // gone with the boss's arrival: 0 as before
    }
}
