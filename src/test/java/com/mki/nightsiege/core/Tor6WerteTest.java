package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tor 6.10-6.12 (Chef 09.11.): curve, dungeons, war camp and their migration 24. */
class Tor6WerteTest {

    /** A server config as it stood before (version 23) with the old server standard. */
    private static SiegeConfig oldServer() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = 23;
        c.damagePerLevel = 0.1;
        c.maxDamageMultiplier = 3.0;
        c.bloodMoonMobMultiplier = 1.5;
        c.campWorkshopHealth = 300;
        c.campReinforceSeconds = 60;
        return c;
    }

    @Test
    void freshConfigHasTheTargets() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(25, SiegeConfig.SETTINGS_VERSION);
        assertEquals(SiegeConfig.SETTINGS_VERSION, fresh.settingsVersion);
        assertEquals(0.04, fresh.damagePerLevel, 1e-9);   // the code default is not the server's 0.1 and stays
        assertEquals(1.3, fresh.bloodMoonMobMultiplier, 1e-9);
        assertEquals(450, fresh.campWorkshopHealth, 1e-9);
        assertEquals(40, fresh.campReinforceSeconds);
        assertEquals(3, fresh.dungeonGroupFrom);
        assertEquals(0.75, fresh.dungeonGroupMobs, 1e-9);
    }

    @Test
    void curveOldServerValueBecomesTarget() {
        SiegeConfig server = oldServer();
        server.sanitize();
        assertEquals(0.08, server.damagePerLevel, 1e-9);
        assertEquals(1.3, server.bloodMoonMobMultiplier, 1e-9);
        assertEquals(SiegeConfig.SETTINGS_VERSION, server.settingsVersion);
        // level 5 / 10 / 15 / 20 with the server's cap 3.0: 1.32 / 1.72 / 2.12 / 2.52 (was 1.4 / 1.9 / 2.4 / 2.9)
        assertEquals(1.32, SiegeMath.damageMultiplier(5, server), 1e-9);
        assertEquals(1.72, SiegeMath.damageMultiplier(10, server), 1e-9);
        assertEquals(2.12, SiegeMath.damageMultiplier(15, server), 1e-9);
        assertEquals(2.52, SiegeMath.damageMultiplier(20, server), 1e-9);
        assertEquals(1.3, SiegeEvent.BLOOD_MOON.mobCountMultiplier(server), 1e-9);
        server.sanitize();   // no second step
        assertEquals(0.08, server.damagePerLevel, 1e-9);
    }

    @Test
    void campOldServerValueBecomesTarget() {
        SiegeConfig server = oldServer();
        server.sanitize();
        assertEquals(450, server.campWorkshopHealth, 1e-9);
        assertEquals(40, server.campReinforceSeconds);
    }

    @Test
    void serverAlreadyOnTheTargetStays() {
        SiegeConfig server = oldServer();
        server.damagePerLevel = 0.08;
        server.bloodMoonMobMultiplier = 1.3;
        server.campWorkshopHealth = 450;
        server.campReinforceSeconds = 40;
        server.sanitize();
        assertEquals(0.08, server.damagePerLevel, 1e-9);
        assertEquals(1.3, server.bloodMoonMobMultiplier, 1e-9);
        assertEquals(450, server.campWorkshopHealth, 1e-9);
        assertEquals(40, server.campReinforceSeconds);
    }

    @Test
    void ownValuesStayUnscaled() {
        SiegeConfig server = oldServer();
        server.damagePerLevel = 0.12;
        server.bloodMoonMobMultiplier = 2.0;
        server.campWorkshopHealth = 600;
        server.campReinforceSeconds = 90;
        server.sanitize();
        assertEquals(0.12, server.damagePerLevel, 1e-9);
        assertEquals(2.0, server.bloodMoonMobMultiplier, 1e-9);
        assertEquals(600, server.campWorkshopHealth, 1e-9);
        assertEquals(90, server.campReinforceSeconds);
    }

    @Test
    void dungeonGroupDiscountFromThreeFighters() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.sanitize();
        assertEquals(1.0, ExpeditionKind.groupShare(2, cfg), 1e-9);
        assertEquals(0.75, ExpeditionKind.groupShare(3, cfg), 1e-9);
        // real dungeon, level 5 (guards 6 alone): 2 fighters 9, 3 fighters 12 x 0.75 = 9
        assertEquals(9, ExpeditionKind.guards(5, 2, 0.5, 14, ExpeditionKind.groupShare(2, cfg)));
        assertEquals(9, ExpeditionKind.guards(5, 3, 0.5, 14, ExpeditionKind.groupShare(3, cfg)));
        assertEquals(12, ExpeditionKind.guards(5, 3));   // the old count without the discount
        // arena wave, level 4 (5 alone): 3 fighters 10 x 0.75 = 7.5 -> 8
        assertEquals(8, ExpeditionKind.wave(4, 3, 0.5, 16, ExpeditionKind.groupShare(3, cfg)));
    }

    @Test
    void playtestDungeonCapsLikeARealDungeon() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.sanitize();
        // playtest: +100 % per player; 6 at level 20 wanted 54 / 54 before the caps, now 14 / 16 (was 24 / 32)
        assertEquals(14, ExpeditionKind.guards(20, 6, cfg.playtestPlayerScaling, 14, ExpeditionKind.groupShare(6, cfg)));
        assertEquals(16, ExpeditionKind.wave(20, 6, cfg.playtestPlayerScaling, 16, ExpeditionKind.groupShare(6, cfg)));
        // alone the playtest and a real dungeon agree
        assertEquals(ExpeditionKind.guards(10, 1), ExpeditionKind.guards(10, 1, cfg.playtestPlayerScaling, 14,
                ExpeditionKind.groupShare(1, cfg)));
    }
}
