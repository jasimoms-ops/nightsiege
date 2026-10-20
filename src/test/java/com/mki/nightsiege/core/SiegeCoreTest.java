package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the parts of the mod that do not touch Minecraft. */
class SiegeCoreTest {

    private SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        return c;
    }

    @Test
    void levelRisesWithWinsAndDays() {
        SiegeConfig c = cfg();
        assertEquals(1, SiegeMath.level(0, 0, c));
        assertEquals(4, SiegeMath.level(3, 0, c));
        assertEquals(3, SiegeMath.level(0, 16, c));
        assertEquals(6, SiegeMath.level(3, 16, c));
        assertEquals(c.maxLevel, SiegeMath.level(999, 9999, c));
    }

    @Test
    void durationAndBatchScale() {
        SiegeConfig c = cfg();
        assertEquals(240, SiegeMath.durationSeconds(1, c));
        assertEquals(420, SiegeMath.durationSeconds(10, c));
        assertEquals(600, SiegeMath.durationSeconds(99, c));

        assertEquals(6, SiegeMath.mobsPerBatch(1, 1, c));
        assertEquals(24, SiegeMath.mobsPerBatch(10, 1, c));
        assertEquals(40, SiegeMath.mobsPerBatch(10, 3, c));
        assertTrue(SiegeMath.mobsPerBatch(20, 10, c) <= c.maxLiveMobs);
    }

    @Test
    void multipliersAreCapped() {
        SiegeConfig c = cfg();
        assertEquals(1.0, SiegeMath.healthMultiplier(1, c), 1e-9);
        assertEquals(1.72, SiegeMath.healthMultiplier(10, c), 1e-9);
        assertEquals(c.maxHealthMultiplier, SiegeMath.healthMultiplier(999, c), 1e-9);
        assertEquals(c.maxDamageMultiplier, SiegeMath.damageMultiplier(999, c), 1e-9);
    }

    @Test
    void mobPoolRespectsLevelGates() {
        SiegeConfig c = cfg();
        MobPool pool = new MobPool(c.mobs);
        assertTrue(pool.eligible(1).stream()
                .noneMatch(entry -> entry.id.equals("minecraft:ravager")));
        assertTrue(pool.eligible(16).stream()
                .anyMatch(entry -> entry.id.equals("minecraft:ravager")));
    }

    @Test
    void mobPoolSkipsMissingMods() {
        SiegeConfig c = cfg();
        MobPool pool = new MobPool(c.mobs);
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            String picked = pool.pick(16, random, Set.of("minecraft:ravager", "minecraft:evoker"));
            assertNotEquals("minecraft:ravager", picked);
            assertNotEquals("minecraft:evoker", picked);
        }
        assertNull(pool.pick(1, random, Set.of("minecraft:zombie", "minecraft:skeleton")));
    }

    @Test
    void schedulerWarnsThenStartsOncePerNight() {
        SiegeConfig c = cfg();
        SiegeScheduler s = new SiegeScheduler();
        long day = 24000L * 3;
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(day + 6000, false, c));
        assertEquals(SiegeScheduler.Decision.WARN, s.evaluate(day + 12000, false, c));
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(day + 13000, false, c));
        assertEquals(SiegeScheduler.Decision.START, s.evaluate(day + 15000, false, c));
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(day + 16000, false, c));
    }

    @Test
    void schedulerHonoursCooldown() {
        SiegeConfig c = cfg();
        SiegeScheduler s = new SiegeScheduler();
        s.markSiegeFinished(24000L * 3 + 20000);
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(24000L * 4 + 12000, false, c));
        assertEquals(SiegeScheduler.Decision.WARN, s.evaluate(24000L * 6 + 12000, false, c));
    }

    @Test
    void anchorIsTheBedWithMostPlayers() {
        AnchorPicker.Point bedA = new AnchorPicker.Point("overworld", 0, 64, 0);
        AnchorPicker.Point bedB = new AnchorPicker.Point("overworld", 500, 64, 0);
        AnchorPicker.Result result = AnchorPicker.pick(
                List.of(bedA, bedB),
                List.of(new AnchorPicker.Point("overworld", 10, 64, 0),
                        new AnchorPicker.Point("overworld", 505, 64, 0),
                        new AnchorPicker.Point("overworld", 510, 64, 0)),
                50);
        assertNotNull(result);
        assertEquals(bedB, result.anchor());
        assertEquals(2, result.playerCount());
    }

    @Test
    void anchorIgnoresOtherDimensionsAndFarPlayers() {
        AnchorPicker.Point bed = new AnchorPicker.Point("overworld", 0, 64, 0);
        assertNull(AnchorPicker.pick(List.of(bed),
                List.of(new AnchorPicker.Point("the_nether", 0, 64, 0)), 50));
        assertNull(AnchorPicker.pick(List.of(bed),
                List.of(new AnchorPicker.Point("overworld", 300, 64, 0)), 50));
    }

    @Test
    void containersAreNeverBroken() {
        SiegeConfig c = cfg();
        assertTrue(BlockGuard.isProtected("minecraft:bedrock", c));
        assertTrue(BlockGuard.isProtected("minecraft:chest", c));
        assertTrue(BlockGuard.isProtected("minecraft:red_shulker_box", c));
        assertTrue(BlockGuard.isProtected("sophisticatedstorage:oak_chest", c));
        assertTrue(BlockGuard.isProtected(null, c));
        assertFalse(BlockGuard.isProtected("minecraft:stone", c));
        assertFalse(BlockGuard.isProtected("minecraft:obsidian", c));
    }

    @Test
    void sanitizeRepairsBrokenConfig() {
        SiegeConfig bad = new SiegeConfig();
        bad.baseMobsPerBatch = -5;
        bad.maxLevel = 0;
        bad.spawnMaxDistance = 1;
        bad.mobs = new java.util.ArrayList<>();
        bad.protectedBlocks = null;
        bad.sanitize();
        assertTrue(bad.baseMobsPerBatch >= 1);
        assertTrue(bad.maxLevel >= 1);
        assertTrue(bad.spawnMaxDistance > bad.spawnMinDistance);
        assertFalse(bad.mobs.isEmpty());
        assertTrue(bad.protectedBlocks.contains("minecraft:bedrock"));
    }

    @Test
    void toolKindIsReadFromItemId() {
        assertEquals(ToolKind.PICKAXE, ToolPicker.fromItemId("minecraft:iron_pickaxe"));
        assertEquals(ToolKind.PICKAXE, ToolPicker.fromItemId("tconstruct:steel_pickaxe"));
        // A pickaxe id also ends in "axe", so the order of the checks matters.
        assertEquals(ToolKind.AXE, ToolPicker.fromItemId("minecraft:netherite_axe"));
        assertEquals(ToolKind.SHOVEL, ToolPicker.fromItemId("minecraft:iron_shovel"));
        assertEquals(ToolKind.NONE, ToolPicker.fromItemId("minecraft:iron_sword"));
        assertEquals(ToolKind.NONE, ToolPicker.fromItemId(null));
    }

    @Test
    void toolsAreHandedOutAtTheConfiguredRate() {
        SiegeConfig c = cfg();
        Random random = new Random(7);
        int armed = 0;
        for (int i = 0; i < 20000; i++) {
            if (ToolPicker.pick(random, c).canDig()) {
                armed++;
            }
        }
        assertEquals(c.toolEquipChance, armed / 20000.0, 0.02);
    }

    @Test
    void disabledToolGatingHandsOutNothing() {
        SiegeConfig c = new SiegeConfig();
        c.toolGatedDigging = false;
        c.sanitize();
        assertEquals(ToolKind.NONE, ToolPicker.pick(new Random(1), c));
    }

    @Test
    void eventsRespectChanceAndMinimumLevel() {
        SiegeConfig c = cfg();
        assertEquals(SiegeEvent.NONE, EventPicker.pick(c.eventMinLevel - 1, new Random(1), c));
        Random random = new Random(3);
        int withEvent = 0;
        for (int i = 0; i < 20000; i++) {
            if (EventPicker.pick(10, random, c) != SiegeEvent.NONE) {
                withEvent++;
            }
        }
        assertEquals(c.eventChance, withEvent / 20000.0, 0.02);
    }

    @Test
    void onlyBloodMoonChangesWaveStrength() {
        SiegeConfig c = cfg();
        assertEquals(1.0, SiegeEvent.NONE.mobCountMultiplier(c), 1e-9);
        assertEquals(1.0, SiegeEvent.ELITE.healthMultiplier(c), 1e-9);
        assertEquals(1.0, SiegeEvent.THEME_WAVE.damageMultiplier(c), 1e-9);
        assertTrue(SiegeEvent.BLOOD_MOON.mobCountMultiplier(c) > 1.0);
        assertTrue(SiegeEvent.BLOOD_MOON.healthMultiplier(c) > 1.0);
    }
}
