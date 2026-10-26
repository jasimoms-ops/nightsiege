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
    void levelRisesWithWinsOnly() {
        SiegeConfig c = cfg();
        assertEquals(1, SiegeMath.level(0, 0, c));
        assertEquals(4, SiegeMath.level(3, 0, c));
        assertEquals(1, SiegeMath.level(0, 16, c));   // time alone no longer raises it
        assertEquals(c.maxLevel, SiegeMath.level(999, 9999, c));
        c.daysPerLevel = 8;   // still possible by config
        assertEquals(3, SiegeMath.level(0, 16, c));
    }

    @Test
    void durationAndBatchScale() {
        SiegeConfig c = cfg();
        assertEquals(240, SiegeMath.durationSeconds(1, c));
        assertEquals(600, SiegeMath.durationSeconds(99, c));
        assertEquals(4, SiegeMath.mobsPerBatch(1, 1, c));
        assertEquals(10, SiegeMath.mobsPerBatch(7, 1, c));   // night 7 alone: 10, not 18
        assertEquals(22, SiegeMath.mobsPerBatch(7, 3, c));   // +60 % per extra player
        assertTrue(SiegeMath.mobsPerBatch(20, 10, c) <= c.maxLiveMobs);
    }

    @Test
    void growthSlowsAboveTwenty() {
        SiegeConfig c = cfg();
        assertEquals(1.0, SiegeMath.healthMultiplier(1, c), 1e-9);
        assertEquals(2.14, SiegeMath.healthMultiplier(20, c), 1e-9);
        assertEquals(1.76, SiegeMath.damageMultiplier(20, c), 1e-9);
        assertEquals(23, SiegeMath.mobsPerBatch(20, 1, c));
        // Four levels above 20 count like one level below it.
        assertEquals(2.20, SiegeMath.healthMultiplier(24, c), 1e-9);
        assertEquals(1.80, SiegeMath.damageMultiplier(24, c), 1e-9);
        assertEquals(24, SiegeMath.mobsPerBatch(24, 1, c));
        // No end: it keeps rising, but slowly.
        assertTrue(SiegeMath.healthMultiplier(60, c) > SiegeMath.healthMultiplier(40, c));
        assertTrue(SiegeMath.healthMultiplier(60, c) < 1.0 + c.healthPerLevel * 59);
    }

    // --- rhythm with invitations --------------------------------------

    private SiegeRhythm.Step rhythm(long day, int time, long siegeDay, long voted, long warned, long started,
                                    int accepts, SiegeConfig c) {
        return SiegeRhythm.decide(day, time, siegeDay, voted, warned, started, accepts, c,
                c.warningDayTime, c.startDayTime);
    }

    @Test
    void rhythmInvitesInTheMorningOfEveryThirdDay() {
        SiegeConfig c = cfg();
        assertEquals(3, c.daysBetweenSieges);
        assertEquals(SiegeRhythm.Step.SCHEDULE, rhythm(5, 100, -1, -1, -1, -1, 0, c));
        assertEquals(8, SiegeRhythm.nextAfter(5, c));
        assertEquals(SiegeRhythm.Step.NONE, rhythm(6, 3000, 8, -1, -1, -1, 0, c));
        assertEquals(SiegeRhythm.Step.NONE, rhythm(8, 500, 8, -1, -1, -1, 0, c));     // before the morning
        assertEquals(SiegeRhythm.Step.INVITE, rhythm(8, c.inviteDayTime, 8, -1, -1, -1, 0, c));
        assertEquals(SiegeRhythm.Step.NONE, rhythm(8, 6000, 8, 8, -1, -1, 1, c));       // vote still open
    }

    @Test
    void rhythmPlaysWithEnoughAcceptsAndSkipsOtherwise() {
        SiegeConfig c = cfg();
        assertEquals(SiegeRhythm.Step.CANCEL, rhythm(8, c.warningDayTime, 8, 8, -1, -1, 2, c));
        assertEquals(SiegeRhythm.Step.WARN, rhythm(8, c.warningDayTime, 8, 8, -1, -1, 3, c));
        assertEquals(SiegeRhythm.Step.NONE, rhythm(8, c.warningDayTime + 100, 8, 8, 8, -1, 3, c));
        assertEquals(SiegeRhythm.Step.START, rhythm(8, c.startDayTime, 8, 8, 8, -1, 3, c));
        assertEquals(SiegeRhythm.Step.NONE, rhythm(8, c.startDayTime + 100, 8, 8, 8, 8, 3, c));
        // A restart in the night: the horn was missed, the night still starts.
        assertEquals(SiegeRhythm.Step.START, rhythm(8, c.startDayTime + 500, 8, 8, -1, -1, 4, c));
        // Server was off in the morning and only came back after sunset: the day is lost.
        assertEquals(SiegeRhythm.Step.CANCEL, rhythm(8, c.warningDayTime + 10, 8, -1, -1, -1, 0, c));
    }

    @Test
    void rhythmFindsANewDayAfterAMissedOne() {
        SiegeConfig c = cfg();
        assertEquals(SiegeRhythm.Step.MISSED, rhythm(11, 200, 8, -1, -1, -1, 0, c));
        assertEquals(11, SiegeRhythm.afterMissed(11, 200, c));    // morning still to come: today
        assertEquals(14, SiegeRhythm.afterMissed(11, 9000, c));   // too late today: three days on
        assertEquals(0.0, SiegeRhythm.progress(5, c.warningDayTime, 8, c), 1e-9);
        assertEquals(1.0, SiegeRhythm.progress(8, c.warningDayTime, 8, c), 1e-9);
        assertEquals(24000L, SiegeRhythm.ticksUntilWarning(7, c.warningDayTime, 8, c));
    }

    @Test
    void eclipseVoteClosesBeforeNoon() {
        SiegeConfig c = cfg();
        int close = c.eclipseWarningDayTime;
        assertEquals(SiegeRhythm.Step.INVITE, SiegeRhythm.decide(8, c.inviteDayTime, 8, -1, -1, -1, 0, c,
                close, c.eclipseStartDayTime));
        assertEquals(SiegeRhythm.Step.WARN, SiegeRhythm.decide(8, close, 8, 8, -1, -1, 3, c,
                close, c.eclipseStartDayTime));
    }

    // --- effect monsters ----------------------------------------------

    @Test
    void effectMonstersComeBackSlowlyFromLevelTen() {
        SiegeConfig c = cfg();
        assertEquals(0.0, c.effectMobShare(9), 1e-9);
        assertEquals(0.1, c.effectMobShare(10), 1e-9);
        assertEquals(0.5, c.effectMobShare(14), 1e-9);
        assertEquals(1.0, c.effectMobShare(19), 1e-9);
        assertEquals(1.0, c.effectMobShare(40), 1e-9);
        Random random = new Random(7);
        assertEquals("minecraft:zombie", c.withoutEffects("minecraft:husk", 5, random));
        assertEquals("minecraft:husk", c.withoutEffects("minecraft:husk", 19, random));
        int kept = 0;
        for (int i = 0; i < 2000; i++) {
            if (c.withoutEffects("minecraft:stray", 12, random).equals("minecraft:stray")) {
                kept++;
            }
        }
        assertTrue(kept > 450 && kept < 750, "about 30 % stay real at level 12, got " + kept);
    }

    // --- Version B: several classes, night kit, reward crates ----------

    /** Learns a whole way of a class to the top (first fitting node of every choice), like the test profiles. */
    private static void learnWay(SkillTree tree, PlayerSkills p, SkillClass clazz, String way) {
        java.util.Set<String> groups = new java.util.HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNode node : tree.ofClass(clazz)) {
                if (!(node.onTrunk() || way.equals(node.path())) || p.levelOf(node.id()) >= node.maxLevel()) {
                    continue;
                }
                if ((node.inGroup() && groups.contains(node.group()) && p.levelOf(node.id()) == 0)
                        || !tree.needsMet(node, p) || tree.lockedOut(node, p)) {
                    continue;
                }
                if (node.inGroup()) {
                    groups.add(node.group());
                }
                p.setLevel(node.id(), node.maxLevel());
                changed = true;
            }
        }
    }

    @Test
    void aClassIsCompleteOnlyWhenNothingIsLeftToLearn() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        assertFalse(tree.isComplete(p, SkillClass.WARRIOR));
        learnWay(tree, p, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        assertTrue(tree.isComplete(p, SkillClass.WARRIOR), "a full blade way is a complete warrior");
        assertTrue(tree.pointsSpent(p, SkillClass.WARRIOR) > 0);
        // One level less somewhere and it is not complete any more.
        for (SkillNode node : tree.ofClass(SkillClass.WARRIOR)) {
            if (p.levelOf(node.id()) > 1) {
                p.setLevel(node.id(), p.levelOf(node.id()) - 1);
                break;
            }
        }
        assertFalse(tree.isComplete(p, SkillClass.WARRIOR));
    }

    @Test
    void waySwapOpensTheOtherSubclassButNotASecondUlt() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        learnWay(tree, p, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        p.award(500);
        String other = p.levelOf("w_combo") > 0 ? "bloodlust" : "w_combo";
        String why = tree.blockedReason(other, p);
        assertTrue("choice".equals(why) || "lockedby".equals(why), other + " is shut: " + why);
        assertTrue(tree.isComplete(p, SkillClass.WARRIOR));
        p.setGainStage(SkillClass.WARRIOR, "wegtausch_w", 1);
        assertNull(tree.blockedReason(other, p), "way swap opens " + other);
        assertFalse(tree.isComplete(p, SkillClass.WARRIOR), "with way swap there is more to learn");
        for (SkillNode node : tree.ofClass(SkillClass.WARRIOR)) {
            if (node.effect().target() == SkillEffect.Target.ULTIMATE && p.levelOf(node.id()) == 0
                    && SkillTree.WAY_BLADE.equals(node.path())) {
                assertNotNull(tree.blockedReason(node.id(), p), "the ult stays one: " + node.id());
            }
        }
        // The other way stays closed.
        for (SkillNode node : tree.ofClass(SkillClass.WARRIOR)) {
            if (SkillTree.WAY_MIGHT.equals(node.path())) {
                assertNotNull(tree.blockedReason(node.id(), p), node.id());
            }
        }
    }

    @Test
    void theShieldFighterGotStronger() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(1.25, fresh.subclassDamage.get("shieldfighter"), 1e-9);
        assertEquals(0.8, fresh.subclassDamageTaken.get("shieldfighter"), 1e-9);
        // A server that tuned the shield fighter itself keeps its own value.
        SiegeConfig tuned = new SiegeConfig();
        tuned.settingsVersion = 5;
        tuned.subclassDamage.put("shieldfighter", 1.5);
        tuned.sanitize();
        assertEquals(1.5, tuned.subclassDamage.get("shieldfighter"), 1e-9);
        assertEquals(2.5, new SkillTree().get("riposte").amountPerLevel(), 1e-9);
    }

    @Test
    void monstersHitHarderButPillagersDoNot() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertEquals(1.2, fresh.monsterDamageFactor("minecraft:zombie"), 1e-9);
        assertEquals(1.0, fresh.monsterDamageFactor("minecraft:pillager"), 1e-9);
        fresh.monsterDamageByType.put("vindicator", 0.9);
        assertEquals(0.9, fresh.monsterDamageFactor("minecraft:vindicator"), 1e-9);
        fresh.monsterDamage = 99;
        fresh.sanitize();
        assertEquals(5.0, fresh.monsterDamageFactor("minecraft:husk"), 1e-9);
    }

    @Test
    void everyGainIsBuiltInVersionB2() {
        for (CrateGains.Gain gain : CrateGains.all()) {
            assertTrue(gain.ready(), gain.id());
        }
        // A legendary pull now really is legendary.
        Random random = new Random(5);
        boolean legendary = false;
        for (int i = 0; i < 3000 && !legendary; i++) {
            CrateGains.Gain g = CrateGains.roll(SkillClass.GUARDIAN, "", new int[]{0, 0, 0, 0, 1}, 0.7, random);
            legendary = g.rarity() == CrateRarity.LEGENDARY;
        }
        assertTrue(legendary);
    }

    @Test
    void severalClassesKeepTheirOwnLevels() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        learnWay(tree, p, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        assertEquals(List.of("WARRIOR"), p.unlockedClasses());
        p.unlockClass(SkillClass.GUARDIAN);
        assertEquals(SkillClass.GUARDIAN, p.levelingClass());
        assertEquals(SkillClass.WARRIOR, p.clazz(), "unlocking does not switch the played class");
        p.award(100);
        // Buying for the guardian while playing the warrior.
        String first = tree.ofClass(SkillClass.GUARDIAN).stream().filter(SkillNode::onTrunk)
                .filter(n -> n.needs().isEmpty()).findFirst().orElseThrow().id();
        assertTrue(p.as(SkillClass.GUARDIAN, () -> tree.unlock(first, p)));
        assertEquals(1, p.levelIn(SkillClass.GUARDIAN, first));
        assertEquals(0, p.levelOf(first), "the played warrior does not see guardian nodes");
        // Switching: the guardian's levels are the played ones now, the warrior's are kept apart.
        String warriorNode = tree.ofClass(SkillClass.WARRIOR).get(0).id();
        int warriorLevel = p.levelOf(warriorNode);
        assertTrue(p.switchTo(SkillClass.GUARDIAN));
        assertEquals(1, p.levelOf(first));
        assertEquals(0, p.levelOf(warriorNode));
        assertEquals(warriorLevel, p.levelIn(SkillClass.WARRIOR, warriorNode));
        assertTrue(p.switchTo(SkillClass.WARRIOR));
        assertEquals(warriorLevel, p.levelOf(warriorNode));
        assertFalse(p.switchTo(SkillClass.ARCHER), "a locked class cannot be played");
    }

    @Test
    void everyCrateGainHasAnIconAndOnlyReadyOnesAreDrawn() {
        Random random = new Random(3);
        for (CrateGains.Gain gain : CrateGains.all()) {
            assertNotEquals("minecraft:chest", CrateGains.icon(gain.id()), gain.id());
        }
        int[] weights = new SiegeConfig().crateOddsFragment;
        for (SkillClass c : new SkillClass[]{SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN, SkillClass.ENGINEER,
                SkillClass.SUMMONER}) {
            assertEquals(31, CrateGains.forCollection(c).size(), c + " has 15 gains and 16 general ones");
            for (int i = 0; i < 400; i++) {
                CrateGains.Gain g = CrateGains.roll(c, "", weights, 0.7, random);
                assertNotNull(g);
                assertTrue(g.ready(), g.id());
                assertTrue(g.clazz() == null || g.clazz() == c, g.id());
            }
        }
        // A way-bound gain never comes to the other way.
        for (int i = 0; i < 400; i++) {
            CrateGains.Gain g = CrateGains.roll(SkillClass.ARCHER, SkillTree.WAY_CROSSBOW, weights, 1.0, random);
            assertNotEquals("staerke", g.id());
            CrateGains.Gain dead = CrateGains.roll(SkillClass.SUMMONER, SkillTree.WAY_DEAD, weights, 1.0, random);
            assertNotEquals(SkillTree.WAY_WILD, dead.way(), dead.id());
            CrateGains.Gain wild = CrateGains.roll(SkillClass.SUMMONER, SkillTree.WAY_WILD, weights, 1.0, random);
            assertNotEquals(SkillTree.WAY_DEAD, wild.way(), wild.id());
        }
        assertEquals("wegtausch_s", SkillTree.waySwapGain(SkillClass.SUMMONER));
        assertNotNull(CrateGains.get("wegtausch_s"));
    }

    @Test
    void crateOddsFollowTheTable() {
        Random random = new Random(9);
        int[] counts = new int[5];
        int[] weights = {60, 25, 10, 4, 1};
        for (int i = 0; i < 20000; i++) {
            counts[CrateGains.rollRarity(weights, random).ordinal()]++;
        }
        assertTrue(counts[0] > 11500 && counts[0] < 12500, "common about 60 %: " + counts[0]);
        assertTrue(counts[4] > 120 && counts[4] < 290, "legendary about 1 %: " + counts[4]);
        assertEquals(CrateKind.FRAGMENT, CrateKind.EVENT.better());
        assertEquals(CrateKind.FRAGMENT, CrateKind.FRAGMENT.better());
    }

    @Test
    void twoPullGainsWorkOnTheSecondDraw() {
        CrateGains.Gain sharp = CrateGains.get("schaerfe");
        assertTrue(sharp.twoPulls());
        assertEquals(2, sharp.topStage());
        assertEquals(0, sharp.level(1));
        assertEquals(1, sharp.level(2));
        CrateGains.Gain quiver = CrateGains.get("koecherfuellung");
        assertEquals(4, quiver.topStage());
        assertEquals(3, quiver.level(3));
    }

    @Test
    void nightKitClimbsWithTheLevel() {
        assertEquals(NightKit.Material.IRON, NightKit.armor(1, 0));
        assertEquals(NightKit.Material.DIAMOND, NightKit.armor(3, 0));
        assertEquals(NightKit.Material.IRON, NightKit.armor(3, 3));
        assertEquals(NightKit.Material.DIAMOND, NightKit.armor(10, 3));
        assertEquals(NightKit.Material.NETHERITE, NightKit.armor(11, 2));
        assertEquals(NightKit.Material.NETHERITE, NightKit.armor(19, 2));
        assertEquals(NightKit.Material.INFERNITE, NightKit.armor(20, 0));
        assertEquals(4, NightKit.protection(11));
        assertEquals(5, NightKit.plusOne(NightKit.protection(11), 1, 4));
        assertEquals(5, NightKit.plusOne(NightKit.protection(11), 3, 4), "at most one above vanilla");
        assertEquals(4, NightKit.sharpness(10));
        assertEquals(5, NightKit.sharpness(14));
        assertEquals(2, NightKit.foodTier(11, 0));
        assertEquals(3, NightKit.foodTier(11, 4));
        assertEquals(0, NightKit.provisionApples(11, 0));
        assertTrue(NightKit.provisionApples(11, 4) > 2);
        assertEquals(64, NightKit.archerArrows(1));
        assertEquals(128, NightKit.archerArrows(20));
    }

    @Test
    void defeatHasNoPenaltyByDefault() {
        SiegeConfig c = cfg();
        assertFalse(c.failureBlast);
        assertEquals(0, c.defeatSkillLoss);
        assertTrue(c.maxLevel > 20);
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
        assertEquals(1.0, SiegeEvent.THEME_WAVE.healthMultiplier(c), 1e-9);
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
        assertNull(tree.blockedReason("masonry", pot));
        assertTrue(tree.unlock("masonry", pot));
        // Battlements and rebuilding follow the masonry, the beacon fire the fortified ground.
        assertNull(tree.blockedReason("battlements", pot));
        assertNotNull(tree.blockedReason("rebuild", pot));
        assertNotNull(tree.blockedReason("beacon_fire", pot));
        assertTrue(tree.unlock("fortified", pot));
        assertTrue(tree.unlock("beacon_fire", pot));
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
                SkillClass.GUARDIAN, 2, SkillClass.ENGINEER, 2, SkillClass.SUMMONER, 2);
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
        assertEquals(SkillEffect.Target.SIEGE, SkillEffect.MASONRY.target());
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
    void theClassWindowKnowsWhatAClassCostsInAll() {
        SkillTree tree = new SkillTree();
        PlayerSkills fresh = new PlayerSkills();
        fresh.chosenClass = SkillClass.GUARDIAN.name();
        int total = tree.pointsToComplete(fresh, SkillClass.GUARDIAN);
        assertTrue(total > 50, "total " + total);
        // Filling the tree for real costs exactly that.
        fresh.award(total);
        for (int guard = 0; guard < 2000 && !tree.isComplete(fresh, SkillClass.GUARDIAN); guard++) {
            boolean any = false;
            for (SkillNode node : tree.ofClass(SkillClass.GUARDIAN)) {
                if (fresh.levelOf(node.id()) < node.maxLevel() && tree.unlock(node.id(), fresh)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                break;
            }
        }
        assertTrue(tree.isComplete(fresh, SkillClass.GUARDIAN));
        assertEquals(total, tree.pointsSpent(fresh, SkillClass.GUARDIAN));
        assertEquals(total, tree.pointsToComplete(fresh, SkillClass.GUARDIAN));
    }

    @Test
    void everythingWithAKeySaysWhichOne() {
        SkillTree tree = new SkillTree();
        int ults = 0;
        for (SkillNode node : tree.all()) {
            if (node.effect().target() == SkillEffect.Target.ULTIMATE) {
                assertEquals(KeyHint.ULT, KeyHint.forNode(node.id(), true));
                ults++;
            }
        }
        assertTrue(ults >= 16, "ults: " + ults);
        assertEquals(KeyHint.SHIELD_ACTION, KeyHint.forNode("g_shieldthrow", false));
        assertEquals(KeyHint.NONE, KeyHint.forNode("archer_focus", false));
        assertEquals(KeyHint.ULT2, KeyHint.forGain("spiegelschild"));
        assertEquals(KeyHint.QUIVER, KeyHint.forGain("koecher"));
        assertEquals(KeyHint.USE, KeyHint.forGadget("wurfbeil"));
        // Every gain with a key exists.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CrateGains.Gain g : CrateGains.all()) {
            ids.add(g.id());
        }
        for (String id : new String[]{"koecher", "sprungstiefel", "zielfernrohr", "wurfspeere", "wurfbeile", "blendgranaten",
                "oelfass", "kletterhaken", "kampfhorn", "tarnumhang", "signalhorn", "mauerkelle", "ersatzbatterie",
                "kriegsbanner", "barrikaden", "leibwaechter", "schlachtentrance", "lockpfeil", "magnetfeld", "spiegelschild",
                "rueckrufpfeife", "geisterross", "futternapf", "totenglocke", "seelentausch"}) {
            assertTrue(ids.contains(id), id);
            assertNotEquals(KeyHint.NONE, KeyHint.forGain(id), id);
        }
    }

    @Test
    void kitBowsGetMoreEnchantmentsWithTheLevel() {
        assertEquals(0, NightKit.bowPunch(4));
        assertEquals(1, NightKit.bowPunch(5));
        assertEquals(0, NightKit.bowFlame(9));
        assertEquals(1, NightKit.bowFlame(10));
        assertEquals(0, NightKit.crossbowPiercing(4));
        assertEquals(1, NightKit.crossbowPiercing(5));
        assertEquals(2, NightKit.crossbowPiercing(12));
        assertEquals(3, NightKit.crossbowPiercing(30));
    }

    @Test
    void theChemistrySubclassesBuildTowersNow() {
        SkillTree tree = new SkillTree();
        PlayerSkills demolition = new PlayerSkills();
        demolition.chosenClass = SkillClass.ENGINEER.name();
        demolition.levels.put(TrapKind.MORTAR.id(), 1);
        assertEquals("demolition", tree.subclassOf(demolition));
        assertEquals(1, tree.trapTier(TrapKind.MORTAR, demolition));
        assertEquals(0, tree.trapTier(TrapKind.TURRET, demolition));
        PlayerSkills alchemist = new PlayerSkills();
        alchemist.chosenClass = SkillClass.ENGINEER.name();
        alchemist.levels.put(TrapKind.BREWER.id(), 1);
        assertEquals("alchemist", tree.subclassOf(alchemist));
        alchemist.levels.put("toxic_brew", 1);
        assertEquals(3, tree.trapTier(TrapKind.BREWER, alchemist));
        // Towers, no floor plates; they shoot further and faster with the tier, never weaker.
        for (TrapKind kind : new TrapKind[]{TrapKind.MORTAR, TrapKind.BREWER}) {
            assertTrue(kind.isTower());
            assertFalse(kind.isFloorPlate());
            assertTrue(kind.splash(3) > kind.splash(1));
            assertEquals(1, kind.freePerSiege(1));
        }
        assertTrue(TrapKind.MORTAR.radius(1) >= 20);
        // Old saves: learned levels move over, the blademaster's two nodes swap.
        PlayerSkills old = new PlayerSkills();
        old.chosenClass = SkillClass.ENGINEER.name();
        old.levels.put("blast_mine", 2);
        old.classLevels.put("WARRIOR", new java.util.HashMap<>(java.util.Map.of("keen_edge", 3, "w_combo", 1)));
        old.history.add("blast_mine");
        old.renameNodes(java.util.Map.of("blast_mine", "mortar_tower", "poison_trap", "potion_tower",
                "keen_edge", "w_combo", "w_combo", "keen_edge"));
        assertEquals(2, old.levelOf("mortar_tower"));
        assertEquals(0, old.levelOf("blast_mine"));
        assertEquals(3, old.classLevels.get("WARRIOR").get("w_combo"));
        assertEquals(1, old.classLevels.get("WARRIOR").get("keen_edge"));
        assertEquals("mortar_tower", old.history.get(0));
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
        assertEquals(Integer.MAX_VALUE, TrapKind.MORTAR.uses(3));
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
        assertTrue(tree.unlock("w_combo", warrior));
        assertEquals("choice", tree.blockedReason("bloodlust", warrior));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOf(warrior));
        assertEquals("locked", tree.blockedReason("keen_edge", warrior));
        assertTrue(tree.unlock("w_combo", warrior));
        assertTrue(tree.unlock("keen_edge", warrior));
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
        assertEquals(10, ultimates.size());
        java.util.Map<String, Integer> expected = java.util.Map.of(SkillTree.WAY_BLADE, 2, SkillTree.WAY_MIGHT, 2,
                SkillTree.WAY_BOW, 3, SkillTree.WAY_CROSSBOW, 3, SkillTree.WAY_SHIELD, 2, SkillTree.WAY_BLESSING, 2,
                SkillTree.WAY_MECHANICS, 2, SkillTree.WAY_CHEMISTRY, 2, SkillTree.WAY_WILD, 2, SkillTree.WAY_DEAD, 2);
        ultimates.forEach((path, count) -> assertEquals(expected.getOrDefault(path, 1), count, path));
    }

    @Test
    void engineerWaysShareTheTrunkTraps() {
        SkillTree tree = new SkillTree();
        assertEquals("e_circuit", tree.get(TrapKind.TESLA.id()).requires());
        assertEquals("e_detonator", tree.get(TrapKind.MORTAR.id()).requires());
        assertTrue(tree.get(TrapKind.SPIKE.id()).onTrunk());
        assertTrue(tree.get(TrapKind.SNARE.id()).onTrunk());
        assertEquals(SkillTree.WAY_MECHANICS, tree.get(TrapKind.TURRET.id()).path());
        assertEquals(SkillTree.WAY_CHEMISTRY, tree.get(TrapKind.BREWER.id()).path());
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
        assertEquals(0, tree.trapTier(TrapKind.MORTAR, engineer));
        assertTrue(tree.unlock("tesla_coil", engineer));
        assertTrue(tree.unlock("capacitor", engineer));
        assertEquals(4, tree.trapTier(TrapKind.TESLA, engineer));
        // The mortar from the other way, at tier 2, closes the network and the ball lightning.
        assertTrue(tree.unlock("m_cross_mine", engineer));
        assertEquals(2, tree.trapTier(TrapKind.MORTAR, engineer));
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
                {"e_detonator", "mortar_tower", "mortar_tower", "flame_trap", "e_accelerant", "e_napalm"},
                {"e_detonator", "potion_tower", "potion_tower", "e_acid", "e_chain_reaction", "e_plague_mine"},
                {"e_detonator", "mortar_tower", "mortar_tower", "e_shrapnel_mine", "c_cross_turret", "e_targetblast",
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
    void twoSurvivedWavesMakeAPoint() {
        PlayerSkills p = new PlayerSkills();
        assertEquals(0, WaveXp.survive(p, 1, 2));
        assertEquals(1, p.waveCredit);
        assertEquals(1, WaveXp.survive(p, 1, 2));
        assertEquals(1, p.points());
        assertEquals(0, p.waveCredit);
        // A catch-up night: three waves' worth at once.
        assertEquals(1, WaveXp.survive(p, 3, 2));
        assertEquals(1, p.waveCredit);
    }

    @Test
    void pointFactorKeepsTheFraction() {
        PlayerSkills p = new PlayerSkills();
        // 1.5 points per two waves: 1, then 2 (the half points add up).
        assertEquals(1, WaveXp.survive(p, 2, 2, 1.5));
        assertEquals(2, WaveXp.survive(p, 2, 2, 1.5));
        assertEquals(3, p.points());
        assertEquals(0.0, p.pointFraction, 1e-9);
        // The kit counts the same way: before night 16 that is half as much again.
        SiegeConfig cfg = new SiegeConfig();
        cfg.pointGainMultiplier = 1.0;
        int plain = com.mki.nightsiege.game.TestKit.pointsBefore(16, cfg);
        cfg.pointGainMultiplier = 1.5;
        assertEquals(Math.floor(plain * 1.5), com.mki.nightsiege.game.TestKit.pointsBefore(16, cfg), 1.0);
    }

    @Test
    void effectMonstersComeAsPlainOnes() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals("minecraft:zombie", cfg.withoutEffects("minecraft:husk"));
        assertEquals("minecraft:camel_husk+minecraft:zombie", cfg.withoutEffects("minecraft:camel_husk+minecraft:husk"));
        assertEquals("minecraft:skeleton", cfg.withoutEffects("minecraft:stray"));
        assertEquals("minecraft:creeper", cfg.withoutEffects("minecraft:creeper"));
        // Every replacement is a monster of the normal list, none gives an effect itself.
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (MobEntry e : SiegeConfig.defaultMobs()) {
            for (String part : e.id.split("\\+")) {
                listed.add(part);
            }
        }
        for (String instead : cfg.effectMobReplacements.values()) {
            assertTrue(listed.contains(instead), instead);
            assertFalse(cfg.effectMobReplacements.containsKey(instead), instead);
        }
        cfg.replaceEffectMobs = false;
        assertEquals("minecraft:husk", cfg.withoutEffects("minecraft:husk"));
    }

    @Test
    void missedNightsAreMadeUp() {
        assertEquals(1, WaveXp.catchUp(5, 4, 4));    // was there last night
        assertEquals(3, WaveXp.catchUp(5, 2, 4));    // missed two
        assertEquals(4, WaveXp.catchUp(20, 2, 4));   // capped
        assertEquals(1, WaveXp.catchUp(0, -1, 4));   // the very first night
        assertEquals(4, WaveXp.catchUp(9, -1, 4));   // new on the server
    }

    @Test
    void aLostNightTakesTheNewestSkills() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.GUARDIAN.name();
        p.award(100);
        assertTrue(tree.unlock("vitality", p));
        assertTrue(tree.unlock("vitality", p));
        assertTrue(tree.unlock("g_challenger", p));
        int before = p.points();
        List<String> gone = tree.loseLast(p, 2);
        assertEquals(List.of("g_challenger", "vitality"), gone);
        assertEquals(0, p.levelOf("g_challenger"));
        assertEquals(1, p.levelOf("vitality"));
        assertEquals(before, p.points());            // the points do not come back
        assertTrue(p.lost > 0);
        // Without a history (old saves) the deepest free level goes.
        p.history.clear();
        assertTrue(tree.unlock("toughness", p) || p.levelOf("toughness") == 0);
        p.history.clear();
        List<String> old = tree.loseLast(p, 1);
        assertEquals(1, old.size());
        // The class change no longer gives lost points back.
        p.changeClass();
        assertEquals(p.earned - p.donated - p.lost, p.points());
    }

    @Test
    void anOldConfigTurnsTheDungeonsBackOnOnce() {
        SiegeConfig old = new SiegeConfig();
        old.dungeonsEnabled = false;
        old.settingsVersion = 2;
        old.dungeonListVersion = 1;
        old.sanitize();
        assertTrue(old.dungeonsEnabled);
        assertFalse(old.bloodMoonGlow);
        assertEquals(9, old.settingsVersion);
        assertEquals(SiegeConfig.CURRENT_DUNGEON_LIST, old.dungeonListVersion);
        assertTrue(old.dungeons.stream().allMatch(t -> t.structure.startsWith("nova_structures:")));
        // Switched off again by hand, it stays off.
        old.dungeonsEnabled = false;
        old.sanitize();
        assertFalse(old.dungeonsEnabled);
    }

    @Test
    void everyDungeonHasAFragmentAndEveryFragmentADungeon() {
        java.util.Set<NightFragment> seen = java.util.EnumSet.noneOf(NightFragment.class);
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (DungeonType type : DungeonType.defaults()) {
            assertTrue(keys.add(type.key), "twice: " + type.key);
            assertNotNull(NightFragment.byName(type.fragment), type.key);
            assertNotNull(DungeonTheme.byName(type.theme), type.key);
            assertEquals(1.0, type.chance);
            seen.add(type.fragment());
        }
        assertEquals(NightFragment.values().length, seen.size());
        for (NightFragment fragment : NightFragment.values()) {
            assertNotEquals(SiegeEvent.NONE, fragment.event());
        }
        assertTrue(DungeonMath.isDungeon(42L, "nova_structures:undead_crypt", 3, 4, 1.0));
        assertFalse(DungeonMath.isDungeon(42L, "nova_structures:undead_crypt", 3, 4, 0.0));
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
        assertTrue(DungeonMath.guards(DungeonTier.EASY, 1, 200) > DungeonMath.guards(DungeonTier.EASY, 1, 2));
        assertEquals(DungeonTier.EASY.mobsPerWave() + 2 + 20, DungeonMath.guards(DungeonTier.EASY, 1, 10000));
        assertEquals(1, DungeonTier.EASY.waves());
        assertEquals(3, DungeonTier.NETHER.waves());
    }

    @Test
    void dungeonKindsMatchTheirStructures() {
        List<DungeonType> types = DungeonType.defaults();
        assertEquals("undead_crypt", DungeonType.find(types, "nova_structures:undead_crypt").key);
        assertEquals("combat_shrine_6", DungeonType.find(types, "nova_structures:shrine_combat_tier_6").key);
        assertEquals("trident_monument", DungeonType.find(types, "nova_structures:trident_trial_monument").key);
        assertNull(DungeonType.find(types, "nova_structures:tavern_oak"));
        assertNull(DungeonType.find(types, "minecraft:village_plains"));
        Set<String> keys = new HashSet<>();
        for (DungeonType type : types) {
            assertTrue(keys.add(type.key), "duplicate " + type.key);
        }
        assertEquals(30, types.size());
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
        assertEquals("", SiegeEvent.THEME_WAVE.atmosphere());
        assertEquals("ECLIPSE", SiegeEvent.ECLIPSE.atmosphere());
        assertEquals("FROST", SiegeEvent.FROST.atmosphere());
    }

    @Test
    void theKillGoalIsSixtyPercentOfWhatComes() {
        // 8 waves of 10: 48 kills.
        assertEquals(48, SiegeMath.killQuota(0.6, 8, 10));
        // Halfway, all waves full so far: still 48.
        assertEquals(48, SiegeMath.killsRequired(0.6, 48, 40, 4, 10));
        // A crowded field kept the waves small: 60 % of what really came.
        assertEquals(30, SiegeMath.killsRequired(0.6, 48, 50, 0, 10));
        // Turned off.
        assertEquals(0, SiegeMath.killsRequired(0.0, 48, 50, 0, 10));
        assertEquals(0.6, new SiegeConfig().killQuotaShare, 1e-9);
    }

    @Test
    void testKitPointsGrowWithTheLevel() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals(0, com.mki.nightsiege.game.TestKit.pointsBefore(1, cfg));
        int five = com.mki.nightsiege.game.TestKit.pointsBefore(5, cfg);
        int ten = com.mki.nightsiege.game.TestKit.pointsBefore(10, cfg);
        assertTrue(five > 0);
        assertTrue(ten > five);
    }

    @Test
    void serverTweaksChangeSkillsAndDamage() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.sanitize();
        assertEquals(1.0, cfg.damageFactor("warrior", "berserker"), 1e-9);
        cfg.classDamage.put("warrior", 1.2);
        cfg.subclassDamage.put("berserker", 0.5);
        assertEquals(0.6, cfg.damageFactor("WARRIOR", "berserker"), 1e-9);
        SkillTree tree = new SkillTree();
        int before = tree.get("masonry").baseCost();
        SiegeConfig.SkillTweak t = new SiegeConfig.SkillTweak();
        t.cost = before + 5;
        cfg.skillTweaks.put("masonry", t);
        assertEquals(1, tree.applyTweaks(cfg.skillTweaks));
        assertEquals(before + 5, tree.get("masonry").baseCost());
        tree.applyTweaks(new java.util.HashMap<>());
        assertEquals(before, tree.get("masonry").baseCost());
    }

    @Test
    void aFixedSubclassShutsTheOtherWayAndTheOtherSubclass() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.unlockClass(SkillClass.WARRIOR);
        p.fixSub(SkillClass.WARRIOR, "w_combo");
        p.award(100_000);
        assertEquals("blademaster", tree.chosenSubclass(p, SkillClass.WARRIOR));
        assertEquals("path", tree.blockedReason("heavy_blow", p), "the way of might is shut");
        for (int round = 0; round < 40; round++) {
            for (SkillNode n : tree.ofClass(SkillClass.WARRIOR)) {
                if (tree.blockedReason(n.id(), p) == null) {
                    tree.unlock(n.id(), p);
                }
            }
        }
        assertTrue(p.levelOf("w_combo") > 0, "the fixed subclass is learnable");
        assertEquals(0, p.levelOf("bloodlust"), "the other subclass of the way stays shut");
        assertEquals(0, p.levelOf("heavy_blow"));
        assertTrue(tree.ultimateMaxed(p, SkillClass.WARRIOR), "an ultimate reaches level 3");
        assertEquals(4, SkillTree.subclassesOf(SkillClass.SUMMONER).size());
        assertEquals(20, SkillTree.SUBCLASSES.size());
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            SkillNode node = tree.get(info.node());
            assertNotNull(node, info.node());
            assertEquals(info.clazz(), node.clazz(), info.node());
            assertEquals(info.way(), node.path(), info.node());
        }
    }

    @Test
    void theSummonerCallsTheWildAndIsForAdminsFirst() {
        SkillTree tree = new SkillTree();
        assertTrue(SkillClass.SUMMONER.adminOnly());
        assertFalse(SkillClass.WARRIOR.adminOnly());
        assertTrue(new SiegeConfig().summonerForEveryone, "open for everyone since 25 October 2026");
        SiegeConfig old = new SiegeConfig();
        old.summonerForEveryone = false;
        old.settingsVersion = 7;
        old.sanitize();
        assertTrue(old.summonerForEveryone, "older configs open it once");
        assertEquals(9, old.settingsVersion);
        // Both subclasses of the way of the wild can be learned to the end.
        java.util.Map<String, String> subs = java.util.Map.of("s_tamer", SkillTree.SUB_TAMER, "s_golemsmith", SkillTree.SUB_GOLEMSMITH,
                "d_necro", SkillTree.SUB_NECROMANCER, "d_soulcatcher", SkillTree.SUB_SOULCATCHER);
        for (String sub : subs.keySet()) {
            boolean dead = sub.startsWith("d_");
            PlayerSkills p = new PlayerSkills();
            p.chosenClass = SkillClass.SUMMONER.name();
            p.unlockClass(SkillClass.SUMMONER);
            p.award(100_000);
            assertTrue(tree.unlock("s_soulbond", p));
            assertTrue(tree.unlock(dead ? "d_raise" : "s_packcall", p));
            assertTrue(tree.unlock(sub, p));
            assertTrue(tree.unlock(sub, p));
            assertEquals(dead ? SkillTree.WAY_DEAD : SkillTree.WAY_WILD, p.pathTaken(tree));
            assertEquals(subs.get(sub), tree.subclassOf(p));
            for (int guard = 0; guard < 500 && !tree.isComplete(p, SkillClass.SUMMONER); guard++) {
                boolean any = false;
                for (SkillNode node : tree.ofClass(SkillClass.SUMMONER)) {
                    if (p.levelOf(node.id()) < node.maxLevel() && tree.unlock(node.id(), p)) {
                        any = true;
                        break;
                    }
                }
                assertTrue(any, "stuck in " + sub);
            }
            assertTrue(tree.isComplete(p, SkillClass.SUMMONER), sub);
            assertNotNull(tree.ultimateOf(p), sub);
            // Only one subclass, one ultimate, one mastery, one keystone.
            for (String other : subs.keySet()) {
                if (!other.equals(sub)) {
                    assertEquals(0, p.levelOf(other), sub + " also learned " + other);
                }
            }
            int ults = 0;
            for (SkillNode node : tree.ofClass(SkillClass.SUMMONER)) {
                if (node.effect().target() == SkillEffect.Target.ULTIMATE && p.levelOf(node.id()) > 0) {
                    ults++;
                }
            }
            assertEquals(1, ults, sub);
        }
        assertTrue(tree.pointsToComplete(new PlayerSkills(), SkillClass.SUMMONER) > 250, "about as much as the other classes");
        // The tamer's wolves and the golemsmith's golems are lanes of their own.
        assertEquals(SkillTree.SUB_TAMER, tree.laneOf("s_alpha"));
        assertEquals(SkillTree.SUB_GOLEMSMITH, tree.laneOf("s_hardgolem"));
        assertEquals(KeyHint.STAFF, KeyHint.forNode("s_packcall", false));
        assertEquals(KeyHint.STAFF, KeyHint.forNode("d_raise", false));
        assertEquals(SkillTree.SUB_NECROMANCER, tree.laneOf("d_ghoul"));
        assertEquals(SkillTree.SUB_SOULCATCHER, tree.laneOf("d_swarm"));
        assertTrue(tree.pointsToComplete(new PlayerSkills(), SkillClass.SUMMONER) > 250);
    }

    @Test
    void thePlaytestOfOctoberTheTwentyFifthMakesTheAxeIllagersRare() {
        SiegeConfig old = new SiegeConfig();
        old.settingsVersion = 8;
        old.mobListVersion = SiegeConfig.CURRENT_MOB_LIST;
        old.mobs = new java.util.ArrayList<>(java.util.List.of(new MobEntry("minecraft:vindicator", 6, 6, 0.5),
                new MobEntry("takesapillage:skirmisher", 4, 6, 0.3), new MobEntry("minecraft:zombie", 1, 20, -1.0)));
        old.effectMobReplacements.put("minecraft:wither_skeleton", "minecraft:vindicator");
        old.effectMobReplacements.put("friendsandfoes:iceologer", "minecraft:vindicator");
        old.sanitize();
        assertEquals(9, old.settingsVersion);
        assertEquals(1.25, old.subclassDamage.get("heavy"), 1e-9);
        assertEquals("minecraft:skeleton", old.effectMobReplacements.get("minecraft:wither_skeleton"));
        assertEquals("minecraft:pillager", old.effectMobReplacements.get("friendsandfoes:iceologer"));
        for (MobEntry e : old.mobs) {
            if (e.id.equals("minecraft:zombie")) {
                assertEquals(20, e.weight);
            } else {
                assertEquals(1, e.weight);
                assertEquals(0.05, e.weightPerLevel, 1e-9);
            }
        }
        // A fresh config has the same.
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        assertTrue(fresh.mobs.stream().filter(e -> e.id.endsWith("vindicator") || e.id.endsWith("skirmisher"))
                .allMatch(e -> e.weight == 1));
    }

    @Test
    void theNightsMvpIsTheOneWhoDidMost() {
        java.util.List<NightStatsData.Row> rows = new java.util.ArrayList<>();
        NightStatsData.Row a = new NightStatsData.Row();
        a.dealt = 120;
        a.kills = 8;
        NightStatsData.Row b = new NightStatsData.Row();
        b.healed = 260;
        b.kills = 1;
        NightStatsData.Row c = new NightStatsData.Row();
        rows.add(a);
        rows.add(b);
        rows.add(c);
        assertEquals(1, NightStatsData.mvpOf(rows));
        b.deaths = 4;
        assertEquals(0, NightStatsData.mvpOf(rows));
        assertEquals(-1, NightStatsData.mvpOf(java.util.List.of(c)));
    }

    @Test
    void bossNightEverySixthSiege() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        assertEquals(6, c.bossEvery);
        // 5 sieges since the last boss: the 6th is a boss night
        assertFalse(BossPlan.isBossNight(4, c));
        assertTrue(BossPlan.isBossNight(5, c));
        // a lost boss night: the counter stays, the boss comes again
        assertTrue(BossPlan.isBossNight(7, c));
        c.bossesEnabled = false;
        assertFalse(BossPlan.isBossNight(5, c));
        c.bossesEnabled = true;
        // an old save counts the sieges played so far
        assertEquals(2, BossPlan.startCount(10, 4, c));
        assertEquals(0, BossPlan.startCount(0, 0, c));
    }

    @Test
    void bossBagGivesEveryBossOnceBeforeRepeating() {
        List<String> bag = new java.util.ArrayList<>();
        Random random = new Random(5);
        Set<BossKind> seen = new HashSet<>();
        BossKind last = null;
        for (int i = 0; i < BossKind.values().length; i++) {
            BossKind k = BossPlan.draw(bag, last == null ? null : last.id(), random);
            assertNotNull(k);
            assertTrue(seen.add(k), "no boss twice in one round");
            last = k;
        }
        assertEquals(BossKind.values().length, seen.size());
        // the new round does not start with the boss that just came
        for (int round = 0; round < 20; round++) {
            BossKind first = BossPlan.draw(bag, last.id(), random);
            assertNotEquals(last, first);
            for (int i = 1; i < BossKind.values().length; i++) {
                last = BossPlan.draw(bag, null, random);
            }
        }
        assertEquals(BossKind.GRAVE_KING, BossKind.byId("grave_king"));
        assertEquals(BossKind.WARLORD, BossKind.byId("WARLORD"));
        assertNull(BossKind.byId("champion"));
    }

    @Test
    void bossHealthGrowsWithLevelAndPlayers() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        double one = BossPlan.health(300, 6, 1, c);
        double four = BossPlan.health(300, 6, 4, c);
        assertEquals(one * 2.5, four, 1e-6);   // +50 % for each further defender
        assertTrue(BossPlan.health(300, 12, 1, c) > one);
        assertEquals(300 * SiegeMath.healthMultiplier(6, c), one, 1e-6);
        // the finale: 3 minutes before dawn, at most half the night
        assertEquals(3600, BossPlan.finaleTicks(20000, c));
        assertEquals(2000, BossPlan.finaleTicks(4000, c));
    }

    @Test
    void championNightIsGone() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        c.eventChance = 1.0;
        c.eventMinLevel = 1;
        Random random = new Random(3);
        for (int i = 0; i < 400; i++) {
            SiegeEvent e = EventPicker.pick(10, random, c);
            assertNotEquals("ELITE", e.name());
        }
        for (SiegeEvent e : SiegeEvent.values()) {
            assertNotEquals("ELITE", e.name());
        }
    }
}
