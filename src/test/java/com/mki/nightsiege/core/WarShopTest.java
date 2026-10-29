package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The war leader's shop and gold (Kriegsführer, 29 October 2026). */
class WarShopTest {

    @Test
    void squadsHaveUniqueIdsAndSensiblePrices() {
        Set<String> ids = new HashSet<>();
        for (WarShop.Squad s : WarShop.SQUADS) {
            assertTrue(ids.add(s.id()), "twice: " + s.id());
            assertTrue(s.price() > 0 && s.size() > 0 && s.fromLevel() >= 1, s.id());
            assertSame(s, WarShop.squad(s.id()));
        }
        assertNull(WarShop.squad("drachen"));
        for (WarShop.Card c : WarShop.Card.values()) {
            assertSame(c, WarShop.Card.byId(c.id()));
            assertNotEquals(SiegeEvent.NONE, c.event());
        }
    }

    @Test
    void wavesShareTheRoundsGoldAndTheLastBringsMore() {
        int round = 200;
        int sum = 0;
        for (int w = 1; w <= 5; w++) {
            sum += WarShop.waveGold(round, w, 5);
        }
        assertTrue(Math.abs(sum - round) <= 3, "sum " + sum);
        assertTrue(WarShop.waveGold(round, 5, 5) > WarShop.waveGold(round, 4, 5));
        assertEquals(WarShop.waveGold(round, 1, 5), WarShop.waveGold(round, 2, 5));
        assertEquals(0, WarShop.waveGold(round, 0, 5));
        assertEquals(0, WarShop.waveGold(round, 6, 5));
        assertEquals(5, WarShop.carry(11));
        assertEquals(0, WarShop.carry(-3));
    }

    @Test
    void roundGoldFollowsANormalNightAndIsLessThanIt() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.sanitize();
        double perPlayer = cfg.perPlayer(false, cfg.playerScaling);
        for (int level : new int[] {1, 5, 10, 15, 20}) {
            int prev = 0;
            for (int players : new int[] {1, 2, 4, 7}) {
                int gold = WarShop.roundGold(level, players, cfg, perPlayer, cfg.maxLiveMobs);
                assertTrue(gold >= 10, "level " + level + " players " + players);
                assertTrue(gold >= prev, "more players, more gold: level " + level + " players " + players);
                prev = gold;
                // A normal night of the same kind in gold, without the share.
                int perWave = SiegeMath.mobsPerBatch(level, players, cfg, perPlayer, cfg.maxLiveMobs);
                double night = WarShop.nightWaves(level, cfg) * perWave * cfg.warGoldPerMob
                        + WarShop.MACHINE_PRICE * SiegeMachineKind.countFor(level) * cfg.warMachineShare
                        + WarShop.BOSS_PRICE * cfg.warBossShare;
                assertTrue(gold <= Math.max(10, Math.round(night)), "share below one: level " + level);
                System.out.println("[WarShopTest] level " + level + " players " + players + ": waves " + WarShop.nightWaves(level, cfg)
                        + " x " + perWave + " monsters, round gold " + gold + " (night worth " + Math.round(night) + ")");
            }
        }
    }

    @Test
    void threeDifferentBossesAreOffered() {
        List<BossKind> offers = WarShop.bossOffers(new Random(3));
        assertEquals(WarShop.BOSS_OFFERS, offers.size());
        assertEquals(offers.size(), new HashSet<>(offers).size());
    }
}
