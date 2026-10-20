package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
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

    // --- difficulty ---------------------------------------------------

    @Test
    void levelRisesWithWinsAndDays() {
        SiegeConfig c = cfg();
        assertEquals(1, SiegeMath.level(0, 0, c));
        assertEquals(4, SiegeMath.level(3, 0, c));
        assertEquals(3, SiegeMath.level(0, 16, c));
        assertEquals(c.maxLevel, SiegeMath.level(999, 9999, c));
    }

    @Test
    void durationAndBatchScale() {
        SiegeConfig c = cfg();
        assertEquals(240, SiegeMath.durationSeconds(1, c));
        assertEquals(600, SiegeMath.durationSeconds(99, c));
        assertEquals(6, SiegeMath.mobsPerBatch(1, 1, c));
        assertTrue(SiegeMath.mobsPerBatch(20, 10, c) <= c.maxLiveMobs);
    }

    @Test
    void multipliersAreCapped() {
        SiegeConfig c = cfg();
        assertEquals(1.0, SiegeMath.healthMultiplier(1, c), 1e-9);
        assertEquals(c.maxHealthMultiplier, SiegeMath.healthMultiplier(999, c), 1e-9);
        assertEquals(c.maxDamageMultiplier, SiegeMath.damageMultiplier(999, c), 1e-9);
    }

    // --- mob pool -----------------------------------------------------

    @Test
    void mobPoolRespectsLevelGates() {
        MobPool pool = new MobPool(cfg().mobs);
        assertTrue(pool.eligible(1).stream().noneMatch(e -> e.id.equals("minecraft:ravager")));
        assertTrue(pool.eligible(16).stream().anyMatch(e -> e.id.equals("minecraft:ravager")));
    }

    @Test
    void mobPoolSkipsMissingMods() {
        MobPool pool = new MobPool(cfg().mobs);
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            assertNotEquals("minecraft:ravager",
                    pool.pick(16, random, Set.of("minecraft:ravager")));
        }
        assertNull(pool.pick(1, random, Set.of("minecraft:zombie", "minecraft:skeleton")));
    }

    // --- countdown ----------------------------------------------------

    @Test
    void intervalStaysWithinTheConfiguredRange() {
        SiegeConfig c = cfg();
        SiegeScheduler scheduler = new SiegeScheduler();
        Random random = new Random(11);
        Set<Long> days = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            scheduler.rollInterval(random, c);
            days.add(scheduler.requiredTicks() / 24000L);
        }
        assertTrue(days.stream().allMatch(d -> d >= c.minDaysBetweenSieges && d <= c.maxDaysBetweenSieges));
        assertTrue(days.size() > 1, "the gap should vary between sieges");
    }

    @Test
    void countdownOnlyFiresWhenFull() {
        SiegeConfig c = cfg();
        SiegeScheduler scheduler = new SiegeScheduler();
        scheduler.rollInterval(new Random(1), c);
        long needed = scheduler.requiredTicks();

        assertFalse(scheduler.armed());
        assertEquals(SiegeScheduler.Decision.NONE, scheduler.evaluate(24000L * 9 + 12000, false, c));

        scheduler.addQualifyingTicks(needed - 1);
        assertFalse(scheduler.armed());
        scheduler.addQualifyingTicks(1);
        assertTrue(scheduler.armed());

        long day = 24000L * 20;
        assertEquals(SiegeScheduler.Decision.WARN, scheduler.evaluate(day + 12000, false, c));
        assertEquals(SiegeScheduler.Decision.START, scheduler.evaluate(day + 15000, false, c));
        assertEquals(SiegeScheduler.Decision.NONE, scheduler.evaluate(day + 16000, false, c));
    }

    @Test
    void finishingASiegeRestartsTheCountdown() {
        SiegeConfig c = cfg();
        SiegeScheduler scheduler = new SiegeScheduler();
        scheduler.rollInterval(new Random(1), c);
        scheduler.addQualifyingTicks(scheduler.requiredTicks());
        assertTrue(scheduler.armed());

        scheduler.markSiegeFinished(new Random(2), c);
        assertFalse(scheduler.armed());
        assertEquals(0L, scheduler.readyTicks());
        assertEquals(0.0, scheduler.progress(), 1e-9);
    }

    // --- anchor -------------------------------------------------------

    @Test
    void anchorIsTheCoreWithMostPlayers() {
        AnchorPicker.Point coreA = new AnchorPicker.Point("overworld", 0, 64, 0);
        AnchorPicker.Point coreB = new AnchorPicker.Point("overworld", 500, 64, 0);
        AnchorPicker.Result result = AnchorPicker.pick(
                List.of(coreA, coreB),
                List.of(new AnchorPicker.Point("overworld", 10, 64, 0),
                        new AnchorPicker.Point("overworld", 505, 64, 0),
                        new AnchorPicker.Point("overworld", 510, 64, 0)),
                50);
        assertNotNull(result);
        assertEquals(coreB, result.anchor());
        assertEquals(2, result.playerCount());
    }

    @Test
    void anchorIgnoresOtherDimensionsAndFarPlayers() {
        AnchorPicker.Point core = new AnchorPicker.Point("overworld", 0, 64, 0);
        assertNull(AnchorPicker.pick(List.of(core),
                List.of(new AnchorPicker.Point("the_nether", 0, 64, 0)), 50));
        assertNull(AnchorPicker.pick(List.of(core),
                List.of(new AnchorPicker.Point("overworld", 300, 64, 0)), 50));
    }

    // --- digging ------------------------------------------------------

    @Test
    void containersAreNeverBroken() {
        SiegeConfig c = cfg();
        assertTrue(BlockGuard.isProtected("minecraft:bedrock", c));
        assertTrue(BlockGuard.isProtected("minecraft:chest", c));
        assertTrue(BlockGuard.isProtected("sophisticatedstorage:oak_chest", c));
        assertTrue(BlockGuard.isProtected(null, c));
        assertFalse(BlockGuard.isProtected("minecraft:stone", c));
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
    void shovelsAreNeverHandedOut() {
        SiegeConfig c = cfg();
        assertFalse(c.allowShovelDigging);
        assertEquals(0, c.toolShovelWeight);
        Random random = new Random(5);
        for (int i = 0; i < 5000; i++) {
            assertNotEquals(ToolKind.SHOVEL, ToolPicker.pick(random, c));
        }
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

    // --- events -------------------------------------------------------

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
        assertTrue(SiegeEvent.BLOOD_MOON.mobCountMultiplier(c) > 1.0);
    }

    // --- skills -------------------------------------------------------




    @Test
    void theArrowSkillIsStillOnlyAPreview() {
        assertFalse(SkillEffect.ARROW_DOUBLE.isLive());
        assertTrue(SkillEffect.MAX_HEALTH.isLive());
        assertEquals(20.0, SkillEffect.MAX_HEALTH.playerBase(), 1e-9);
    }

    // --- config -------------------------------------------------------

    @Test
    void sanitizeRepairsBrokenConfig() {
        SiegeConfig bad = new SiegeConfig();
        bad.baseMobsPerBatch = -5;
        bad.maxLevel = 0;
        bad.spawnMaxDistance = 1;
        bad.minDaysBetweenSieges = 9;
        bad.maxDaysBetweenSieges = 2;
        bad.requiredPlayers = 0;
        bad.mobs = new java.util.ArrayList<>();
        bad.protectedBlocks = null;
        bad.sanitize();

        assertTrue(bad.baseMobsPerBatch >= 1);
        assertTrue(bad.maxLevel >= 1);
        assertTrue(bad.spawnMaxDistance > bad.spawnMinDistance);
        assertTrue(bad.maxDaysBetweenSieges >= bad.minDaysBetweenSieges);
        assertTrue(bad.requiredPlayers >= 1);
        assertFalse(bad.mobs.isEmpty());
        assertTrue(bad.protectedBlocks.contains("minecraft:bedrock"));
    }

    @Test
    void countdownOnlyAdvancesWhileDefendersAreThere() {
        SiegeConfig c = cfg();
        SiegeScheduler s = new SiegeScheduler();
        s.rollInterval(new Random(1), c);
        assertTrue(s.requiredTicks() >= c.minDaysBetweenSieges * 24000L);
        assertTrue(s.requiredTicks() <= c.maxDaysBetweenSieges * 24000L);
        assertFalse(s.armed());

        // Time that does not qualify is simply never handed in.
        assertEquals(0.0, s.progress(), 1e-9);
        s.addQualifyingTicks(s.requiredTicks() / 2);
        assertEquals(0.5, s.progress(), 0.01);
        assertFalse(s.armed());

        s.addQualifyingTicks(s.requiredTicks());
        assertTrue(s.armed());
        assertEquals(0L, s.remainingTicks());
    }

    @Test
    void nothingStartsBeforeTheCountdownIsFull() {
        SiegeConfig c = cfg();
        SiegeScheduler s = new SiegeScheduler();
        s.rollInterval(new Random(1), c);
        long night = 24000L * 3;
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(night + c.warningDayTime, false, c));

        s.addQualifyingTicks(s.requiredTicks());
        assertEquals(SiegeScheduler.Decision.WARN, s.evaluate(night + c.warningDayTime, false, c));
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(night + c.warningDayTime + 100, false, c));
        assertEquals(SiegeScheduler.Decision.START, s.evaluate(night + c.startDayTime, false, c));
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(night + c.startDayTime + 100, false, c));
    }

    @Test
    void intervalStaysWithinTwoToFiveDays() {
        SiegeConfig c = cfg();
        SiegeScheduler s = new SiegeScheduler();
        Random random = new Random(11);
        for (int i = 0; i < 500; i++) {
            s.rollInterval(random, c);
            long days = s.requiredTicks() / 24000L;
            assertTrue(days >= c.minDaysBetweenSieges && days <= c.maxDaysBetweenSieges,
                    "interval out of range: " + days);
        }
    }




    @Test
    void onlyPlayerEffectsTouchAttributes() {
        assertEquals(SkillEffect.Target.PLAYER, SkillEffect.MAX_HEALTH.target());
        assertEquals(SkillEffect.Target.SIEGE, SkillEffect.SPAWN_DISTANCE.target());
        assertEquals(SkillEffect.Target.PREVIEW, SkillEffect.ARROW_DOUBLE.target());
        assertFalse(SkillEffect.ARROW_DOUBLE.isLive());
        assertTrue(SkillEffect.SPAWN_DISTANCE.isLive());
    }

    @Test
    void shovelsAreNotHandedOutByDefault() {
        SiegeConfig c = cfg();
        assertEquals(0, c.toolShovelWeight);
        assertFalse(c.allowShovelDigging);
        Random random = new Random(5);
        for (int i = 0; i < 5000; i++) {
            assertNotEquals(ToolKind.SHOVEL, ToolPicker.pick(random, c));
        }
    }

    @Test
    void aClassMustBeChosenBeforeItsNodesOpen() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.award(100);
        assertEquals("nochoice", tree.blockedReason("archer_focus", skills));

        skills.chosenClass = SkillClass.ARCHER.name();
        assertNull(tree.blockedReason("archer_focus", skills));
        // A node of a different class stays shut.
        assertEquals("class", tree.blockedReason("strength", skills));
    }

    @Test
    void theCoreTreeNeedsNoClass() {
        SkillTree tree = new SkillTree();
        PlayerSkills pot = new PlayerSkills();
        pot.award(100);
        assertNull(tree.blockedReason("watchtower", pot));
        assertTrue(tree.unlock("watchtower", pot));
    }

    @Test
    void thetwoArcherPathsLockEachOtherOut() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(500);

        assertTrue(tree.unlock("archer_focus", skills));
        assertNull(tree.blockedReason("swift_draw", skills));
        assertNull(tree.blockedReason("heavy_draw", skills));

        assertTrue(tree.unlock("swift_draw", skills));
        assertEquals(SkillTree.PATH_SWIFT, skills.pathTaken(tree));
        // Once one path is started the other is shut for good.
        assertEquals("path", tree.blockedReason("heavy_draw", skills));
        assertEquals("path", tree.blockedReason("power_shot", skills));
    }

    @Test
    void theFinalArcherNodesSitBehindTheirPath() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(5000);
        tree.unlock("archer_focus", skills);
        assertEquals("locked", tree.blockedReason("double_shot", skills));

        for (int i = 0; i < 4; i++) {
            assertTrue(tree.unlock("swift_draw", skills), "swift_draw " + i);
        }
        assertEquals("locked", tree.blockedReason("double_shot", skills));
        for (int i = 0; i < 4; i++) {
            assertTrue(tree.unlock("quick_hands", skills), "quick_hands " + i);
        }
        assertNull(tree.blockedReason("double_shot", skills));
        assertTrue(tree.unlock("double_shot", skills));
        assertEquals("maxed", tree.blockedReason("double_shot", skills));
    }

    @Test
    void everyPlayerKeepsTheirOwnProgress() {
        SkillTree tree = new SkillTree();
        PlayerSkills one = new PlayerSkills();
        PlayerSkills two = new PlayerSkills();
        one.chosenClass = SkillClass.ARCHER.name();
        two.chosenClass = SkillClass.WARRIOR.name();
        one.award(50);

        assertTrue(tree.unlock("archer_focus", one));
        assertEquals(0, two.points());
        assertEquals(0, two.levelOf("archer_focus"));
        assertEquals("nochoice", tree.blockedReason("archer_focus", two) == null ? "" : "class");
    }

    @Test
    void costsRiseAndResetWipesEverything() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(1000);

        int first = skills.points();
        tree.unlock("archer_focus", skills);
        int afterFirst = skills.points();
        tree.unlock("archer_focus", skills);
        int afterSecond = skills.points();
        assertTrue(first - afterFirst < afterFirst - afterSecond, "a second level must cost more");

        skills.reset();
        assertEquals(0, skills.points());
        assertEquals(0, skills.levelOf("archer_focus"));
        assertFalse(skills.hasClass());
    }

    @Test
    void onlyArrowEffectsBelongToTheArcher() {
        SkillTree tree = new SkillTree();
        for (SkillNode node : tree.ofClass(SkillClass.ARCHER)) {
            assertEquals(SkillEffect.Target.ARROW, node.effect().target(),
                    "archer node with a non-arrow effect: " + node.id());
        }
        for (SkillNode node : tree.ofClass(SkillClass.CORE)) {
            assertEquals(SkillEffect.Target.SIEGE, node.effect().target(),
                    "core node with a non-siege effect: " + node.id());
        }
    }
}
