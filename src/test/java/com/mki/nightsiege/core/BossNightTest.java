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
}
