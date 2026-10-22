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
        assertTrue(pool.eligible(1).stream().noneMatch(e -> e.baseId().equals("minecraft:ravager")));
        assertTrue(pool.eligible(16).stream().anyMatch(e -> e.baseId().equals("minecraft:ravager")));
    }

    @Test
    void mobPoolSkipsMissingMods() {
        MobPool pool = new MobPool(cfg().mobs);
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            assertNotEquals("minecraft:evoker",
                    pool.pick(16, random, Set.of("minecraft:evoker")));
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
        assertEquals("class", tree.blockedReason("warrior_drill", skills));
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
    void theArcherPicksBowOrCrossbow() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(500);

        assertTrue(tree.unlock("archer_focus", skills));
        assertNull(tree.blockedReason("a_pierce", skills));
        assertNull(tree.blockedReason("heavy_bolts", skills));
        assertTrue(tree.unlock("a_pierce", skills));
        assertEquals(SkillTree.WAY_BOW, skills.pathTaken(tree));
        assertEquals("path", tree.blockedReason("heavy_bolts", skills));
        assertTrue(tree.unlock("swift_draw", skills));
        assertEquals("choice", tree.blockedReason("heavy_draw", skills));
        assertEquals(SkillTree.SUB_SWIFT, tree.subclassOf(skills));
    }

    @Test
    void theArcherReachesTheHuntInBothWays() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(2000);
        tree.unlock("archer_focus", skills);
        tree.unlock("heavy_bolts", skills);
        assertEquals("locked", tree.blockedReason("a_explosive", skills));
        assertTrue(tree.unlock("a_netbolt", skills));
        assertTrue(tree.unlock("a_netbolt", skills));
        assertTrue(tree.unlock("a_shackle", skills));
        assertTrue(tree.unlock("x_mark", skills));
        assertTrue(tree.unlock("a_hunternet", skills));
        assertTrue(tree.unlock("x_cross_double", skills));
        assertEquals("lockedby", tree.blockedReason("a_barrage", skills));
        assertTrue(tree.unlock("x_hunt", skills));
        assertEquals("choice", tree.blockedReason("bolt_volley", skills));
        assertTrue(tree.unlock("x_pack", skills));
        assertEquals("x_hunt", tree.ultimateOf(skills).id());
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
        assertEquals("class", tree.blockedReason("archer_focus", two));
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
    void everyClassHasItsPaths() {
        SkillTree tree = new SkillTree();
        java.util.Map<SkillClass, Integer> expected = java.util.Map.of(SkillClass.ARCHER, 2, SkillClass.WARRIOR, 2,
                SkillClass.GUARDIAN, 2, SkillClass.ENGINEER, 2);
        for (java.util.Map.Entry<SkillClass, Integer> entry : expected.entrySet()) {
            Set<String> paths = new HashSet<>();
            for (SkillNode node : tree.ofClass(entry.getKey())) {
                if (!node.onTrunk()) {
                    paths.add(node.path());
                }
            }
            assertEquals(entry.getValue(), paths.size(), entry.getKey() + " paths " + paths);
        }
        for (SkillNode node : tree.ofClass(SkillClass.CORE)) {
            assertEquals(SkillEffect.Target.SIEGE, node.effect().target(),
                    "core node with a non-siege effect: " + node.id());
        }
    }

    @Test
    void everyEffectKnowsWhatItActsOn() {
        assertEquals(SkillEffect.Target.PLAYER, SkillEffect.MAX_HEALTH.target());
        assertEquals(SkillEffect.Target.SIEGE, SkillEffect.SPAWN_DISTANCE.target());
        assertEquals(SkillEffect.Target.ARROW, SkillEffect.DOUBLE_SHOT.target());
        assertEquals(SkillEffect.Target.ULTIMATE, SkillEffect.POWER_SHOT.target());
        assertEquals(20.0, SkillEffect.MAX_HEALTH.playerBase(), 1e-9);
    }

    // ------------------------------------------------------------------
    // engineer traps
    // ------------------------------------------------------------------

    @Test
    void everyTrapHasItsOwnEngineerNode() {
        SkillTree tree = new SkillTree();
        for (TrapKind kind : TrapKind.values()) {
            SkillNode node = tree.get(kind.id());
            assertNotNull(node, "no node for " + kind);
            assertEquals(SkillClass.ENGINEER, node.clazz());
            assertEquals(SkillEffect.TRAP, node.effect());
            assertEquals(TrapKind.NODE_LEVELS, node.maxLevel());
            assertEquals(kind, TrapKind.byId(kind.id()));
        }
    }

    @Test
    void trapsUnlockOneAfterTheOther() {
        SkillTree tree = new SkillTree();
        PlayerSkills engineer = new PlayerSkills();
        engineer.chosenClass = SkillClass.ENGINEER.name();
        engineer.award(500);

        assertEquals("locked", tree.blockedReason(TrapKind.SNARE.id(), engineer));
        assertTrue(tree.unlock(TrapKind.SPIKE.id(), engineer));
        assertEquals(1, tree.trapTier(TrapKind.SPIKE, engineer));
        assertEquals("locked", tree.blockedReason(TrapKind.SNARE.id(), engineer));
        assertTrue(tree.unlock(TrapKind.SPIKE.id(), engineer));
        assertTrue(tree.unlock(TrapKind.SNARE.id(), engineer));
        assertEquals("locked", tree.blockedReason(TrapKind.TURRET.id(), engineer));
    }

    @Test
    void onlyEngineersGetTrapTiers() {
        SkillTree tree = new SkillTree();
        PlayerSkills archer = new PlayerSkills();
        archer.chosenClass = SkillClass.ARCHER.name();
        archer.setLevel(TrapKind.SPIKE.id(), 3);
        assertEquals(0, tree.trapTier(TrapKind.SPIKE, archer));
        assertEquals("class", tree.blockedReason(TrapKind.SPIKE.id(), archer));
    }

    @Test
    void higherTiersAreNeverWeaker() {
        for (TrapKind kind : TrapKind.values()) {
            for (int tier = 1; tier < TrapKind.MAX_TIER; tier++) {
                assertTrue(kind.damage(tier + 1) >= kind.damage(tier), kind + " damage");
                assertTrue(kind.uses(tier + 1) >= kind.uses(tier), kind + " uses");
                assertTrue(kind.radius(tier + 1) >= kind.radius(tier), kind + " radius");
                assertTrue(kind.cooldownTicks(tier + 1) <= kind.cooldownTicks(tier), kind + " cooldown");
                assertTrue(kind.freePerSiege(tier + 1) >= kind.freePerSiege(tier), kind + " free");
            }
            assertEquals(0, kind.freePerSiege(0), kind + " is free before it is unlocked");
        }
        assertEquals(1, TrapKind.MINE.uses(3));
        assertTrue(TrapKind.TURRET.startAmmo(1) <= TrapKind.TURRET.maxAmmo(1));
        assertEquals(TrapKind.SPIKE.damage(TrapKind.MAX_TIER), TrapKind.SPIKE.damage(99), 1e-9);
    }

    // ------------------------------------------------------------------
    // warrior and guardian
    // ------------------------------------------------------------------

    @Test
    void theWarriorWalksOneWayWithChoices() {
        SkillTree tree = new SkillTree();
        PlayerSkills warrior = new PlayerSkills();
        warrior.chosenClass = SkillClass.WARRIOR.name();
        warrior.award(5000);
        assertTrue(tree.unlock("warrior_drill", warrior));
        // Trunk choice: endurance closes footwork.
        assertTrue(tree.unlock("w_endurance", warrior));
        assertEquals("choice", tree.blockedReason("w_footwork", warrior));
        // The shared node of the blade way; the way of might closes.
        assertTrue(tree.unlock("champion_bane", warrior));
        assertEquals(SkillTree.WAY_BLADE, warrior.pathTaken(tree));
        assertEquals("path", tree.blockedReason("sunder", warrior));
        // Subclass.
        assertTrue(tree.unlock("keen_edge", warrior));
        assertEquals("choice", tree.blockedReason("bloodlust", warrior));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOf(warrior));
        assertEquals("locked", tree.blockedReason("w_combo", warrior));
        assertTrue(tree.unlock("keen_edge", warrior));
        assertTrue(tree.unlock("w_combo", warrior));
        assertEquals("choice", tree.blockedReason("blood_drinker", warrior));
        // A cross talent closes a hybrid and a mastery.
        assertTrue(tree.unlock("e_cross_heavy", warrior));
        assertEquals("lockedby", tree.blockedReason("w_bladestorm", warrior));
        assertEquals("choice", tree.blockedReason("w_frenzy", warrior));
        assertTrue(tree.unlock("w_evasion", warrior));
        assertTrue(tree.unlock("whirlwind", warrior));
        assertEquals("choice", tree.blockedReason("execute", warrior));
        assertNull(tree.blockedReason("w_glass_cannon", warrior));
        assertEquals(tree.get("whirlwind"), tree.ultimateOf(warrior));
        assertEquals("class", tree.blockedReason("g_ironward", warrior));
    }

    @Test
    void oldWarriorsGetTheirPointsBack() {
        com.mki.nightsiege.game.SiegeSave save = new com.mki.nightsiege.game.SiegeSave();
        PlayerSkills warrior = save.skillsOf("a");
        warrior.chosenClass = SkillClass.WARRIOR.name();
        warrior.award(100);
        warrior.spend(40);
        warrior.setLevel("long_reach", 3);
        PlayerSkills archer = save.skillsOf("b");
        archer.chosenClass = SkillClass.ARCHER.name();
        archer.award(50);
        archer.spend(10);
        archer.setLevel("archer_focus", 2);
        PlayerSkills engineer = save.skillsOf("c");
        engineer.chosenClass = SkillClass.ENGINEER.name();
        engineer.award(30);
        engineer.spend(5);
        PlayerSkills guardian = save.skillsOf("d");
        guardian.chosenClass = SkillClass.GUARDIAN.name();
        guardian.award(40);
        guardian.spend(12);
        guardian.setLevel("iron_guard", 2);
        assertEquals(4, save.migrateSkills());
        assertEquals(40, guardian.points());
        assertEquals(0, guardian.levelOf("iron_guard"));
        assertEquals(100, warrior.points());
        assertEquals(0, warrior.levelOf("long_reach"));
        assertEquals(50, archer.points());
        assertEquals(0, archer.levelOf("archer_focus"));
        assertEquals(30, engineer.points());
        assertEquals(0, save.migrateSkills());
    }

    @Test
    void theGuardianPicksShieldOrBlessing() {
        SkillTree tree = new SkillTree();
        PlayerSkills guardian = new PlayerSkills();
        guardian.chosenClass = SkillClass.GUARDIAN.name();
        guardian.award(5000);
        assertTrue(tree.unlock("vitality", guardian));
        assertTrue(tree.unlock("g_challenger", guardian));
        assertEquals("choice", tree.blockedReason("toughness", guardian));
        assertTrue(tree.unlock("g_shieldwall", guardian));
        assertEquals(SkillTree.WAY_SHIELD, guardian.pathTaken(tree));
        assertEquals("path", tree.blockedReason("field_dressing", guardian));
        assertTrue(tree.unlock("g_bastion", guardian));
        assertEquals(SkillTree.SUB_BULWARK, tree.subclassOf(guardian));
        assertEquals("choice", tree.blockedReason("riposte", guardian));
        assertEquals("locked", tree.blockedReason("g_ironhide", guardian));
        assertTrue(tree.unlock("g_bastion", guardian));
        assertTrue(tree.unlock("g_anchor", guardian));
        assertTrue(tree.unlock("g_thorns", guardian));
        assertTrue(tree.unlock("g_hedgehog", guardian));
        // The ward from the other way closes the shield charge.
        assertTrue(tree.unlock("s_cross_ward", guardian));
        assertEquals("lockedby", tree.blockedReason("shield_charge", guardian));
        assertTrue(tree.unlock("taunt", guardian));
        assertTrue(tree.unlock("g_iron_will", guardian));
        assertEquals("choice", tree.blockedReason("g_shared_burden", guardian));
        assertTrue(tree.unlock("g_living_wall", guardian));
        assertEquals("taunt", tree.ultimateOf(guardian).id());
        assertEquals("class", tree.blockedReason("warrior_drill", guardian));
    }

    @Test
    void theGuardianReachesEveryHybridSlot() {
        SkillTree tree = new SkillTree();
        String[][] routes = {
                {"g_shieldwall", "riposte", "riposte", "g_shieldthrow", "g_thorns", "g_boomerang"},
                {"g_shieldwall", "g_bastion", "g_bastion", "g_ironhide", "g_counterstrike", "g_vengeance"},
                {"g_shieldwall", "riposte", "riposte", "g_shieldbash", "g_counterstrike", "g_vengeance"},
                {"field_dressing", "g_ward", "g_ward", "g_dome", "g_selfless", "g_lifebond"},
                {"field_dressing", "g_ward", "g_ward", "g_dome", "g_lightbringer", "g_wellspring"},
                {"field_dressing", "g_healing_hand", "g_healing_hand", "g_triage", "g_selfless", "g_miracle"},
                {"field_dressing", "g_ward", "g_ward", "g_cleanse", "b_cross_riposte", "g_overflow", "g_sanctuary",
                        "g_eternal_watch", "g_patron"},
        };
        for (String[] route : routes) {
            PlayerSkills guardian = new PlayerSkills();
            guardian.chosenClass = SkillClass.GUARDIAN.name();
            guardian.award(5000);
            assertTrue(tree.unlock("vitality", guardian));
            for (String id : route) {
                assertTrue(tree.unlock(id, guardian), id + " " + tree.blockedReason(id, guardian));
            }
        }
        // The other way's riposte closes the healing spring and the life spring.
        PlayerSkills cross = new PlayerSkills();
        cross.chosenClass = SkillClass.GUARDIAN.name();
        cross.award(5000);
        for (String id : new String[]{"vitality", "field_dressing", "g_ward", "g_ward", "g_dome", "b_cross_riposte"}) {
            assertTrue(tree.unlock(id, cross), id);
        }
        assertEquals("lockedby", tree.blockedReason("g_wellspring", cross));
        assertEquals("lockedby", tree.blockedReason("g_lifespring", cross));
        assertEquals(SkillTree.SUB_WARDEN, tree.subclassOf(cross));
    }

    @Test
    void everyRequirementAndLockPointsAtARealNode() {
        SkillTree tree = new SkillTree();
        Set<String> places = new HashSet<>();
        for (SkillNode node : tree.all()) {
            for (List<SkillNode.Need> any : node.needs()) {
                for (SkillNode.Need need : any) {
                    SkillNode other = tree.get(need.id());
                    assertTrue(other != null, node.id() + " needs unknown " + need.id());
                    assertEquals(node.clazz(), other.clazz(), node.id() + " needs " + need.id());
                    assertTrue(other.onTrunk() || other.path().equals(node.path()), node.id() + " needs " + need.id());
                }
            }
            for (String locked : node.locksList()) {
                assertTrue(tree.get(locked) != null, node.id() + " locks unknown " + locked);
            }
            if (node.col() >= 0) {
                String place = node.clazz() + "/" + (node.onTrunk() ? "" : node.path()) + "/" + node.row() + "/" + node.col();
                assertTrue(places.add(place), "two nodes on " + place);
            }
        }
    }

    @Test
    void everyPathEndsInExactlyOneUltimate() {
        SkillTree tree = new SkillTree();
        java.util.Map<String, Integer> ultimates = new java.util.HashMap<>();
        for (SkillNode node : tree.all()) {
            if (node.onTrunk() || node.clazz() == SkillClass.CORE) {
                continue;
            }
            ultimates.merge(node.path(), node.effect().target() == SkillEffect.Target.ULTIMATE ? 1 : 0, Integer::sum);
        }
        assertEquals(8, ultimates.size());
        java.util.Map<String, Integer> expected = java.util.Map.of(SkillTree.WAY_BLADE, 2, SkillTree.WAY_MIGHT, 2,
                SkillTree.WAY_BOW, 3, SkillTree.WAY_CROSSBOW, 3, SkillTree.WAY_SHIELD, 2, SkillTree.WAY_BLESSING, 2,
                SkillTree.WAY_MECHANICS, 2, SkillTree.WAY_CHEMISTRY, 2);
        ultimates.forEach((path, count) -> assertEquals(expected.getOrDefault(path, 1), count, path));
    }

    @Test
    void engineerWaysShareTheTrunkTraps() {
        SkillTree tree = new SkillTree();
        assertEquals("e_circuit", tree.get(TrapKind.TESLA.id()).requires());
        assertEquals("e_detonator", tree.get(TrapKind.MINE.id()).requires());
        assertTrue(tree.get(TrapKind.SPIKE.id()).onTrunk());
        assertTrue(tree.get(TrapKind.SNARE.id()).onTrunk());
        assertEquals(SkillTree.WAY_MECHANICS, tree.get(TrapKind.TURRET.id()).path());
        assertEquals(SkillTree.WAY_CHEMISTRY, tree.get(TrapKind.POISON.id()).path());
    }

    @Test
    void theEngineerPicksMechanicsOrChemistry() {
        SkillTree tree = new SkillTree();
        PlayerSkills engineer = new PlayerSkills();
        engineer.chosenClass = SkillClass.ENGINEER.name();
        engineer.award(5000);
        for (String id : new String[]{"spike_trap", "spike_trap", "e_battery", "snare_trap", "e_circuit"}) {
            assertTrue(tree.unlock(id, engineer), id);
        }
        assertEquals("choice", tree.blockedReason("e_finemech", engineer));
        assertEquals(SkillTree.WAY_MECHANICS, engineer.pathTaken(tree));
        assertEquals("path", tree.blockedReason("e_detonator", engineer));
        assertTrue(tree.unlock("tesla_coil", engineer));
        assertEquals(SkillTree.SUB_ELECTRICIAN, tree.subclassOf(engineer));
        assertEquals("choice", tree.blockedReason("arrow_turret", engineer));
        assertEquals(0, tree.trapTier(TrapKind.MINE, engineer));
        assertTrue(tree.unlock("tesla_coil", engineer));
        assertTrue(tree.unlock("capacitor", engineer));
        assertEquals(4, tree.trapTier(TrapKind.TESLA, engineer));
        // The mine from the other way, at tier 2, closes the network and the ball lightning.
        assertTrue(tree.unlock("m_cross_mine", engineer));
        assertEquals(2, tree.trapTier(TrapKind.MINE, engineer));
        assertEquals("lockedby", tree.blockedReason("e_network", engineer));
        assertTrue(tree.unlock("e_watchtower", engineer));
        assertTrue(tree.unlock("thunderstorm", engineer));
        assertEquals("lockedby", tree.blockedReason("e_ball_lightning", engineer));
        assertEquals("locked", tree.blockedReason("e_superconductor", engineer));
        assertTrue(tree.unlock("e_fieldtech", engineer));
        assertEquals("thunderstorm", tree.ultimateOf(engineer).id());
    }

    @Test
    void theEngineerReachesEveryHybridSlot() {
        SkillTree tree = new SkillTree();
        String[][] routes = {
                {"e_circuit", "arrow_turret", "arrow_turret", "e_ammobelt", "e_overvolt", "e_charged"},
                {"e_circuit", "tesla_coil", "tesla_coil", "e_chainbolt", "e_seeker", "e_beacon"},
                {"e_circuit", "arrow_turret", "arrow_turret", "turret_tuning", "e_overvolt", "e_network"},
                {"e_detonator", "blast_mine", "blast_mine", "flame_trap", "e_accelerant", "e_napalm"},
                {"e_detonator", "poison_trap", "poison_trap", "e_acid", "e_chain_reaction", "e_plague_mine"},
                {"e_detonator", "blast_mine", "blast_mine", "e_shrapnel_mine", "c_cross_turret", "e_targetblast",
                        "e_blastpower", "bombardment", "e_firebombs"},
        };
        for (String[] route : routes) {
            PlayerSkills engineer = new PlayerSkills();
            engineer.chosenClass = SkillClass.ENGINEER.name();
            engineer.award(5000);
            assertTrue(tree.unlock("spike_trap", engineer));
            assertTrue(tree.unlock("spike_trap", engineer));
            assertTrue(tree.unlock("snare_trap", engineer));
            for (String id : route) {
                boolean ok = tree.unlock(id, engineer);
                if (id.equals("e_firebombs")) {
                    // Fire bombs need the alchemist's poison trap: closed on the mine route.
                    assertFalse(ok);
                    continue;
                }
                assertTrue(ok, id + " " + tree.blockedReason(id, engineer));
            }
        }
    }

    @Test
    void boostersPushATrapPastItsNode() {
        SkillTree tree = new SkillTree();
        PlayerSkills engineer = new PlayerSkills();
        engineer.chosenClass = SkillClass.ENGINEER.name();
        engineer.setLevel(TrapKind.TURRET.id(), 3);
        assertEquals(3, tree.trapTier(TrapKind.TURRET, engineer));
        engineer.setLevel("turret_tuning", 2);
        assertEquals(5, tree.trapTier(TrapKind.TURRET, engineer));
        engineer.setLevel(TrapKind.TURRET.id(), 0);
        assertEquals(0, tree.trapTier(TrapKind.TURRET, engineer));
    }

    @Test
    void everySkillAndTrapHasANameInBothLanguages() throws Exception {
        SkillTree tree = new SkillTree();
        for (String lang : new String[]{"de_de", "en_us"}) {
            String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "src/main/resources/assets/nightsiege/lang/" + lang + ".json")), java.nio.charset.StandardCharsets.UTF_8);
            for (SkillNode node : tree.all()) {
                assertTrue(json.contains("\"nightsiege.skill." + node.id() + "\""), lang + " lacks " + node.id());
                if (!node.onTrunk()) {
                    assertTrue(json.contains("\"nightsiege.ui.path_" + node.path() + "\""), lang + " lacks path " + node.path());
                }
            }
            for (TrapKind kind : TrapKind.values()) {
                assertTrue(json.contains("\"block.nightsiege." + kind.id() + "\""), lang + " lacks block " + kind.id());
            }
        }
    }

    @Test
    void hardMobsGrowMoreCommonAndEasyOnesFade() {
        MobEntry zombie = new MobEntry("minecraft:zombie", 1, 20, -1.0);
        MobEntry evoker = new MobEntry("minecraft:evoker", 10, 2, 0.4);
        assertTrue(zombie.weightAt(20) < zombie.weightAt(1));
        assertEquals(1, zombie.weightAt(99));
        assertTrue(evoker.weightAt(20) > evoker.weightAt(10));
        assertEquals("minecraft:spider", new MobEntry("minecraft:spider+minecraft:skeleton", 6, 3).baseId());
    }

    @Test
    void anOldConfigGetsTheNewMobList() {
        SiegeConfig old = new SiegeConfig();
        old.mobListVersion = 1;
        old.mobs = new java.util.ArrayList<>(List.of(new MobEntry("minecraft:zombie", 1, 10)));
        old.sanitize();
        assertEquals(SiegeConfig.CURRENT_MOB_LIST, old.mobListVersion);
        assertTrue(old.mobs.size() > 20);
    }

    @Test
    void nodesKnowTheirSubclass() {
        SkillTree tree = new SkillTree();
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.laneOf("keen_edge"));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.laneOf("w_combo"));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.laneOf("w_chain_execute"));
        assertEquals("", tree.laneOf("w_parry"));
        assertEquals("", tree.laneOf("champion_bane"));
        assertEquals("", tree.laneOf("g_vengeance"));
        assertEquals(SkillTree.SUB_GUNNER, tree.laneOf("e_charged"));
        assertEquals(SkillTree.SUB_WARDEN, tree.laneOf("g_dome"));
        assertEquals("", tree.laneOf("vitality"));
    }

    @Test
    void anOldConfigSwitchesTheDungeonsOffOnce() {
        SiegeConfig old = new SiegeConfig();
        old.dungeonsEnabled = true;
        old.settingsVersion = 0;
        old.sanitize();
        assertFalse(old.dungeonsEnabled);
        assertFalse(old.bloodMoonGlow);
        assertEquals(2, old.settingsVersion);
        // Switched back on by hand, it stays on.
        old.dungeonsEnabled = true;
        old.sanitize();
        assertTrue(old.dungeonsEnabled);
    }

    @Test
    void theClassChangeGivesEveryPointBackOnce() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.GUARDIAN.name();
        skills.award(40);
        skills.donated = 5;
        skills.spend(5);
        assertTrue(tree.unlock("vitality", skills));
        skills.changeClass();
        assertFalse(skills.hasClass());
        assertEquals(35, skills.points());
        assertEquals(0, skills.levelOf("vitality"));
        assertTrue(skills.classResetUsed);
    }

    @Test
    void trapCapsGrowWithTheTierAndStayPositive() {
        for (TrapKind kind : TrapKind.values()) {
            for (int tier = 1; tier < TrapKind.MAX_TIER; tier++) {
                assertTrue(kind.maxPlaced(tier) >= 1, kind + " cap");
                assertTrue(kind.maxPlaced(tier + 1) >= kind.maxPlaced(tier), kind + " cap grows");
            }
        }
    }

    // ------------------------------------------------------------------
    // dungeons
    // ------------------------------------------------------------------

    @Test
    void perkStagesCostOneMoreFragmentEachTime() {
        assertEquals(0, PerkKind.stageFor(0));
        assertEquals(1, PerkKind.stageFor(1));
        assertEquals(1, PerkKind.stageFor(2));
        assertEquals(2, PerkKind.stageFor(3));
        assertEquals(4, PerkKind.stageFor(14));
        assertEquals(5, PerkKind.stageFor(15));
        assertEquals(5, PerkKind.stageFor(99));
        assertEquals(15, PerkKind.fragmentsFor(5));
    }

    @Test
    void roughlyTwoInFiveStructuresHideACore() {
        int hits = 0;
        for (int i = 0; i < 5000; i++) {
            if (DungeonMath.isDungeon(12345L, "mvs:castle_ruins", i, i * 7)) {
                hits++;
            }
        }
        assertTrue(hits > 1700 && hits < 2300, "hits " + hits);
        // Asking twice gives the same answer.
        assertEquals(DungeonMath.isDungeon(9L, "a:b", 3, 4), DungeonMath.isDungeon(9L, "a:b", 3, 4));
    }

    @Test
    void dungeonDifficultyFollowsTierAndProgress() {
        assertEquals(DungeonTier.EASY.baseLevel(), DungeonMath.level(DungeonTier.EASY, 0));
        assertEquals(DungeonTier.EASY.baseLevel() + 2, DungeonMath.level(DungeonTier.EASY, 50));
        assertEquals(DungeonTier.NETHER.baseLevel() + DungeonMath.MAX_PROGRESS_LEVELS,
                DungeonMath.level(DungeonTier.NETHER, 10000));
        assertTrue(DungeonMath.mobsPerWave(DungeonTier.HARD, 3) > DungeonMath.mobsPerWave(DungeonTier.HARD, 1));
        assertEquals(1, DungeonTier.EASY.waves());
        assertEquals(3, DungeonTier.NETHER.waves());
    }

    @Test
    void dungeonKindsMatchTheirStructures() {
        List<DungeonType> types = DungeonType.defaults();
        assertEquals("outpost", DungeonType.find(types, "towns_and_towers:pillager_outpost_savanna").key);
        assertEquals("nether_arena", DungeonType.find(types, "structures:nether_arena").key);
        assertNull(DungeonType.find(types, "minecraft:village_plains"));
        Set<String> keys = new HashSet<>();
        for (DungeonType type : types) {
            assertTrue(keys.add(type.key), "duplicate " + type.key);
        }
        assertEquals(20, types.size());
    }

    @Test
    void anOldConfigGetsTheDungeonList() {
        SiegeConfig old = new SiegeConfig();
        old.dungeons = null;
        old.sanitize();
        assertEquals(SiegeConfig.CURRENT_DUNGEON_LIST, old.dungeonListVersion);
        assertFalse(old.dungeons.isEmpty());
    }

    @Test
    void everyEventCanComeUp() {
        SiegeConfig c = new SiegeConfig();
        c.eventChance = 1.0;
        Set<SiegeEvent> seen = new HashSet<>();
        Random random = new Random(3);
        for (int i = 0; i < 4000; i++) {
            seen.add(EventPicker.pick(10, random, c));
        }
        for (SiegeEvent event : SiegeEvent.values()) {
            if (event != SiegeEvent.NONE) {
                assertTrue(seen.contains(event), "never picked: " + event);
            }
        }
        assertFalse(seen.contains(SiegeEvent.NONE));
    }

    @Test
    void anEventWithoutWeightNeverComes() {
        SiegeConfig c = new SiegeConfig();
        c.eventChance = 1.0;
        c.eclipseWeight = 0;
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            assertNotEquals(SiegeEvent.ECLIPSE, EventPicker.pick(10, random, c));
        }
    }

    @Test
    void anEclipseWarnsAndStartsAroundNoon() {
        SiegeConfig c = new SiegeConfig();
        SiegeScheduler s = new SiegeScheduler();
        s.setRequiredTicks(24000);
        s.setReadyTicks(24000);
        long day = 24000L * 5;
        int warn = c.eclipseWarningDayTime;
        int start = c.eclipseStartDayTime;
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(day + 3000, false, warn, start));
        assertEquals(SiegeScheduler.Decision.WARN, s.evaluate(day + warn, false, warn, start));
        assertEquals(SiegeScheduler.Decision.START, s.evaluate(day + start, false, warn, start));
        assertEquals(SiegeScheduler.Decision.NONE, s.evaluate(day + start + 100, false, warn, start));
    }

    @Test
    void onlyEventsWithASkyHaveAnAtmosphere() {
        assertEquals("", SiegeEvent.NONE.atmosphere());
        assertEquals("", SiegeEvent.ELITE.atmosphere());
        assertEquals("ECLIPSE", SiegeEvent.ECLIPSE.atmosphere());
        assertEquals("FROST", SiegeEvent.FROST.atmosphere());
    }
}
