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

    /** The night's size as before update 30.10. (settings 17): one formula, one cap for every group. */
    private SiegeConfig cfgOneCap() {
        SiegeConfig c = cfg();
        c.soloScaling = 1.0;
        c.liveCapByPlayers.clear();
        c.damageTakenByPlayers.clear();
        c.mobHealthByPlayers.clear();
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
        SiegeConfig c = cfgOneCap();
        assertEquals(240, SiegeMath.durationSeconds(1, c));
        assertEquals(600, SiegeMath.durationSeconds(99, c));
        assertEquals(4, SiegeMath.mobsPerBatch(1, 1, c));
        assertEquals(10, SiegeMath.mobsPerBatch(7, 1, c));   // night 7 alone: 10, not 18
        assertEquals(22, SiegeMath.mobsPerBatch(7, 3, c));   // +60 % per extra player
        assertTrue(SiegeMath.mobsPerBatch(20, 10, c) <= c.maxLiveMobs);
    }

    @Test
    void perPlayerScaling() {
        SiegeConfig c = cfgOneCap();
        // The defaults keep everything as it was: cap 40 for any group, no overflow life.
        assertEquals(40, SiegeMath.liveCap(1, c));
        assertEquals(40, SiegeMath.liveCap(10, c));
        assertEquals(1.0, SiegeMath.overflowHealthFactor(SiegeMath.mobsWanted(20, 10, c, c.playerScaling), 40, c), 1e-9);
        assertEquals(SiegeMath.mobsPerBatch(7, 3, c), (int) Math.round(SiegeMath.mobsWanted(7, 3, c, c.playerScaling)));
        // Lighter alone, more per further player, a cap that grows with the group up to maxLiveMobsCap.
        c.soloScaling = 0.5;
        c.playerScaling = 1.0;
        c.livePerPlayer = 0.5;
        c.overflowHealth = 1.0;
        assertEquals(5, SiegeMath.mobsPerBatch(7, 1, c));                 // half of 10
        assertEquals(25, SiegeMath.mobsPerBatch(7, 3, c));                // 10 × (0.5 + 2)
        assertEquals(100, SiegeMath.liveCap(4, c));                       // 40 × (1 + 0.5 × 3)
        assertEquals(120, SiegeMath.liveCap(10, c));                      // at most maxLiveMobsCap
        double wanted = SiegeMath.mobsWanted(20, 10, c, c.playerScaling); // 23 × 9.5 ≈ 218
        assertEquals(120, SiegeMath.mobsPerBatch(20, 10, c, c.playerScaling, SiegeMath.liveCap(10, c)));
        assertEquals(wanted / 120.0, SiegeMath.overflowHealthFactor(wanted, 120, c), 1e-9);
        // A table by group size, past its end liveCapStep per further player, at most maxLiveMobsCap.
        c.liveCapByPlayers = new java.util.ArrayList<>(java.util.List.of(8, 12, 20, 30));
        c.liveCapStep = 9;
        assertEquals(8, SiegeMath.liveCap(1, c));
        assertEquals(30, SiegeMath.liveCap(4, c));
        assertEquals(57, SiegeMath.liveCap(7, c));
        assertEquals(84, SiegeMath.liveCap(10, c));
        assertEquals(120, SiegeMath.liveCap(30, c));
        assertEquals(c.soloDamageTaken, SiegeMath.damageTakenForGroup(1, c), 1e-9);
        assertEquals(1.0, SiegeMath.damageTakenForGroup(3, c), 1e-9);
        c.damageTakenByPlayers = new java.util.ArrayList<>(java.util.List.of(0.6, 0.8, 1.0));
        assertEquals(0.6, SiegeMath.damageTakenForGroup(1, c), 1e-9);
        assertEquals(0.8, SiegeMath.damageTakenForGroup(2, c), 1e-9);
        assertEquals(1.0, SiegeMath.damageTakenForGroup(9, c), 1e-9);
        assertEquals(1.0, SiegeMath.healthForGroup(1, c), 1e-9);
        c.mobHealthByPlayers = new java.util.ArrayList<>(java.util.List.of(0.7, 0.85));
        assertEquals(0.7, SiegeMath.healthForGroup(1, c), 1e-9);
        assertEquals(0.85, SiegeMath.healthForGroup(6, c), 1e-9);
    }

    @Test
    void update17ScalesByGroupAndBalancesTheClasses() {
        // A server at settings 16 (the config of 29.10.) gets the tables, the switch and the class values.
        SiegeConfig server = new SiegeConfig();
        server.settingsVersion = 16;
        server.subclassDamage.put("alchemist", 1.15);
        server.subclassDamageTaken.put("soulcatcher", 1.1666);
        server.sanitize();
        assertEquals(17, server.settingsVersion);
        assertEquals(0.6, server.soloScaling, 1e-9);
        assertEquals(9, SiegeMath.liveCap(1, server));
        assertEquals(33, SiegeMath.liveCap(4, server));
        assertEquals(114, SiegeMath.liveCap(10, server));
        assertEquals(120, SiegeMath.liveCap(12, server));   // past the table +8 per player, at most 120
        assertEquals(0.7, SiegeMath.damageTakenForGroup(1, server), 1e-9);
        assertEquals(0.95, SiegeMath.healthForGroup(3, server), 1e-9);
        assertEquals(1.0, SiegeMath.healthForGroup(8, server), 1e-9);
        assertTrue(server.quotaSkipsVanished);
        assertEquals(0.9, server.subclassDamage.get("alchemist"), 1e-9);
        assertEquals(0.85, server.subclassDamageTaken.get("soulcatcher"), 1e-9);
        assertEquals(80.0, server.skillTweaks.get("d_soulcatcher").values.get("swarmcd"), 1e-9);
        assertEquals(8.0, server.skillTweaks.get("holy_light").values.get("heal"), 1e-9);
        // The tank guard is on by default.
        assertEquals(0.15, server.bulwarkGuardDamage, 1e-9);
        assertEquals(0.3, server.shieldfighterGuardKnockback, 1e-9);
        // Once done, a server's own later changes stay.
        server.liveCapByPlayers.set(9, 90);
        server.sanitize();
        assertEquals(90, SiegeMath.liveCap(10, server));
    }

    @Test
    void growthSlowsAboveTwenty() {
        SiegeConfig c = cfgOneCap();
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

    // --- extra siege called at the core ------------------------------

    private SiegeRhythm.ExtraBlock extra(long day, int time, int level, long siegeDay, long voted,
                                         boolean held, int calls, boolean busy, SiegeConfig c) {
        return SiegeRhythm.extraSiegeBlock(day, time, level, siegeDay, voted, held, calls, busy, c, c.warningDayTime);
    }

    @Test
    void extraSiegeOnlyFromLevelTwentyAndOncePerDay() {
        SiegeConfig c = cfg();
        assertEquals(20, c.extraSiegeFromLevel);
        assertEquals(1, c.extraSiegePerDay);
        assertEquals(SiegeRhythm.ExtraBlock.LEVEL, extra(9, 3000, 19, 11, 8, false, 0, false, c));
        assertEquals(SiegeRhythm.ExtraBlock.NONE, extra(9, 3000, 20, 11, 8, false, 0, false, c));
        assertEquals(SiegeRhythm.ExtraBlock.NONE, extra(9, 500, 25, 11, 8, false, 0, false, c));   // early morning too
        assertEquals(SiegeRhythm.ExtraBlock.CALLED_TODAY, extra(9, 3000, 20, 11, 8, false, 1, false, c));
        c.extraSiegePerDay = 0;
        assertEquals(SiegeRhythm.ExtraBlock.OFF, extra(9, 3000, 30, 11, 8, false, 0, false, c));
        c.extraSiegePerDay = 5;
        c.sanitize();
        assertEquals(1, c.extraSiegePerDay);   // more than one vote a day cannot fit
    }

    @Test
    void extraSiegeNotOnASiegeDayNorInTheNightNorTooLate() {
        SiegeConfig c = cfg();
        // The regular siege day is today: invited, or the invitation is still to come.
        assertEquals(SiegeRhythm.ExtraBlock.SIEGE_TODAY, extra(11, 3000, 20, 11, 11, false, 0, false, c));
        assertEquals(SiegeRhythm.ExtraBlock.SIEGE_TODAY, extra(11, 500, 20, 11, -1, false, 0, false, c));
        // Today's night was already decided (called off or played).
        assertEquals(SiegeRhythm.ExtraBlock.SIEGE_TODAY, extra(9, 3000, 20, 12, 8, true, 0, false, c));
        // A siege runs or the horn has sounded.
        assertEquals(SiegeRhythm.ExtraBlock.NIGHT, extra(9, 16000, 20, 11, 8, false, 0, true, c));
        // The vote would be over at once.
        assertEquals(SiegeRhythm.ExtraBlock.TOO_LATE, extra(9, c.warningDayTime, 20, 11, 8, false, 0, false, c));
        assertEquals(SiegeRhythm.ExtraBlock.NONE, extra(9, c.warningDayTime - 1, 20, 11, 8, false, 0, false, c));
    }

    @Test
    void extraSiegeVotesLikeARegularDayAndKeepsTheRhythm() {
        SiegeConfig c = cfg();
        // Called on day 9 (regular day 11): today becomes the siege day with the vote running.
        assertEquals(SiegeRhythm.Step.NONE, rhythm(9, 6000, 9, 9, -1, -1, 1, c));
        assertEquals(SiegeRhythm.Step.CANCEL, rhythm(9, c.warningDayTime, 9, 9, -1, -1, 2, c));
        assertEquals(SiegeRhythm.Step.WARN, rhythm(9, c.warningDayTime, 9, 9, -1, -1, 3, c));
        // Afterwards the regular day stays where it was ...
        assertEquals(11, SiegeRhythm.afterExtra(11, 9, 16000, c));
        assertEquals(11, SiegeRhythm.afterExtra(11, 10, 2000, c));   // the night ran past midnight
        // ... unless it went by meanwhile (server off): then as after a missed day.
        assertEquals(12, SiegeRhythm.afterExtra(11, 12, 500, c));
        assertEquals(15, SiegeRhythm.afterExtra(11, 12, 9000, c));
        assertEquals(12, SiegeRhythm.afterExtra(-1, 9, 16000, c));
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
    void theShieldFighterGotStronger() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        // 1.25 since 25.10., -30 % since the DPS bench of 27.10.
        // Update 30.10. (settings 17) sets the class balancing of the team sim (klassen3).
        assertEquals(0.45, fresh.subclassDamage.get("shieldfighter"), 1e-9);
        // Simon 27.10. (DPS bench, level 10): tamer +20 %, bolt archer -40 %, bulwark -15 %, medic heals 20 % more; the paladin stays.
        assertEquals(1.2, fresh.subclassDamage.get("tamer"), 1e-9);
        assertEquals(0.6, fresh.subclassDamage.get("bolter"), 1e-9);
        assertEquals(0.63, fresh.subclassDamage.get("bulwark"), 1e-9);
        assertEquals(1.1, fresh.subclassDamage.get("paladin"), 1e-9);
        assertEquals(1.8, fresh.healingFactor("medic"), 1e-9);
        // Simon 27.10., 23:00: more life steal for the melee classes again.
        assertEquals(0.12, fresh.meleeLifesteal, 1e-9);
        // Simon 28.10., 02:13: the berserker steals 12 % again; an old server tweak of 0.16 goes back too.
        SkillValues.apply(new java.util.HashMap<>());   // the defaults alone
        assertEquals(0.12, SkillValues.get("bloodlust", "steal", -1.0), 1e-9);
        SiegeConfig tuned16 = new SiegeConfig();
        tuned16.settingsVersion = 14;
        SiegeConfig.SkillTweak bl = new SiegeConfig.SkillTweak();
        bl.values = new java.util.HashMap<>(java.util.Map.of("below", 1.01, "steal", 0.16));
        tuned16.skillTweaks.put("bloodlust", bl);
        tuned16.sanitize();
        assertEquals(0.12, tuned16.skillTweaks.get("bloodlust").values.get("steal"), 1e-9);
        assertEquals(1.01, tuned16.skillTweaks.get("bloodlust").values.get("below"), 1e-9);
        assertEquals(1.0, fresh.healingFactor("warden"), 1e-9);
        // Simon 26.10.: the shield fighter takes as much as anyone, the bulwark 35 % less, the alchemist hits 15 % harder.
        assertEquals(1.0, fresh.subclassDamageTaken.get("shieldfighter"), 1e-9);
        assertEquals(0.65, fresh.subclassDamageTaken.get("bulwark"), 1e-9);
        assertEquals(0.9, fresh.subclassDamage.get("alchemist"), 1e-9);
        assertTrue(fresh.damageTakenFactor("guardian", "shieldfighter") >= fresh.damageTakenFactor("guardian", "bulwark") / 0.7 - 1e-9);
        // A server that tuned the shield fighter itself kept its own value (only the -30 % of 27.10. on top);
        // update 30.10. then sets the tested value.
        SiegeConfig tuned = new SiegeConfig();
        tuned.settingsVersion = 5;
        tuned.subclassDamage.put("shieldfighter", 1.5);
        tuned.sanitize();
        assertEquals(0.45, tuned.subclassDamage.get("shieldfighter"), 1e-9);
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
        assertEquals(List.of("WARRIOR", "GUARDIAN"), p.profiles());
        assertEquals(SkillClass.WARRIOR, p.clazz(), "unlocking does not switch the played class");
        p.award(100);
        // Buying for the guardian while playing the warrior: its subclass node first (the root of its
        // tree, level 1 free), then the root of its trunk.
        String sub = tree.ofClass(SkillClass.GUARDIAN).stream().filter(n -> n.needs().isEmpty())
                .findFirst().orElseThrow().id();
        assertTrue(SkillTree.isSubclassNode(sub), sub);
        String first = "vitality";
        assertTrue(p.as(SkillClass.GUARDIAN, () -> tree.unlock(sub, p) && tree.unlock(first, p)));
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

    // --- profiles (Simon, 26 October 2026) ------------------------------

    /** A warrior with the trunk and the blade way's shared node, ready to raise the subclass to level 2. */
    private static void warriorTrunk(SkillTree tree, PlayerSkills p) {
        assertTrue(tree.unlock("warrior_drill", p));
        assertTrue(tree.unlock("w_endurance", p));
        assertTrue(tree.unlock("champion_bane", p));
    }

    @Test
    void aSecondProfileOfTheSameClassHasItsOwnTreeAndSubclass() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.award(5000);
        assertTrue(tree.unlock("bloodlust", p));   // the subclass first (the root of the tree, free)
        warriorTrunk(tree, p);
        assertEquals(SkillTree.SUB_BERSERKER, tree.subclassOf(p));
        // A further subclass: a new profile "WARRIOR#2" that starts at 0, with its subclass node at the free level 1.
        String key = p.addProfile(SkillClass.WARRIOR, SkillTree.SUB_BLADEMASTER);
        assertEquals("WARRIOR#2", key);
        assertEquals(SkillClass.WARRIOR, PlayerSkills.classOfKey(key));
        assertEquals(List.of("WARRIOR", "WARRIOR#2"), p.profiles());
        assertEquals(0, p.levelInProfile(key, "warrior_drill"));
        assertEquals(1, p.levelInProfile(key, "w_combo"), "level 1 comes with the subclass");
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOfProfile(p, key), "opened for the blademaster");
        assertEquals(SkillClass.WARRIOR, p.clazz(), "opening a profile does not switch the played one");
        // Points are one account: spent in the new profile while the first one is played.
        int before = p.points();
        p.asProfile(key, () -> {
            warriorTrunk(tree, p);
            assertEquals("choice", tree.blockedReason("bloodlust", p), "the profile holds the blademaster");
            assertTrue(tree.unlock("w_combo", p));
            return null;
        });
        assertTrue(p.points() < before);
        assertEquals(2, p.levelInProfile(key, "w_combo"));
        assertEquals(0, p.levelInProfile("WARRIOR", "w_combo"), "the first profile keeps its own tree");
        assertEquals(1, p.levelInProfile("WARRIOR", "bloodlust"));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOfProfile(p, key));
        assertEquals(SkillTree.SUB_BERSERKER, tree.subclassOfProfile(p, "WARRIOR"));
        // Playing the second profile: its levels are the played ones now.
        assertTrue(p.switchToProfile(key));
        assertEquals(2, p.levelOf("w_combo"));
        assertEquals(0, p.levelOf("bloodlust"));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOf(p));
        // The blademaster profile keeps to the blade way.
        assertEquals("path", tree.blockedReason("heavy_blow", p));
        assertTrue(p.switchToProfile("WARRIOR"));
        assertEquals(1, p.levelOf("bloodlust"));
        // The free subclasses are the two of the way of might.
        assertEquals(List.of(SkillTree.SUB_AXE, SkillTree.SUB_PALADIN).stream().sorted().toList(),
                tree.freeSubclasses(p, SkillClass.WARRIOR).stream().sorted().toList());
    }

    @Test
    void aSubclassCannotBeOwnedTwice() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.award(5000);
        assertTrue(tree.unlock("w_combo", p));   // the blademaster: its node first, level 1 free
        warriorTrunk(tree, p);
        // A profile opened for the paladin keeps it for itself.
        String paladin = p.addProfile(SkillClass.WARRIOR, SkillTree.SUB_PALADIN);
        assertNotNull(paladin);
        assertEquals(1, p.levelInProfile(paladin, "smite"));
        assertTrue(tree.unlock("w_combo", p));
        // A third profile without a planned subclass may take neither the paladin nor the blademaster.
        String third = p.addProfile(SkillClass.WARRIOR, "");
        assertEquals("WARRIOR#3", third);
        p.asProfile(third, () -> {
            assertEquals("locked", tree.blockedReason("warrior_drill", p), "the trunk hangs from the subclass");
            assertEquals("subclass_owned", tree.blockedReason("smite", p), "the paladin belongs to WARRIOR#2");
            assertEquals("subclass_owned", tree.blockedReason("w_combo", p));
            assertEquals(null, tree.blockedReason("bloodlust", p));
            int points = p.points();
            assertTrue(tree.unlock("bloodlust", p));   // picked in the tree: free, and the subclass is fixed with it
            assertEquals(points, p.points());
            assertEquals(SkillTree.SUB_BERSERKER, p.plannedSubclass(third));
            assertTrue(tree.unlock("warrior_drill", p));
            assertTrue(tree.unlock("w_endurance", p));
            assertTrue(tree.unlock("champion_bane", p));
            return null;
        });
        assertFalse(tree.freeSubclasses(p, SkillClass.WARRIOR).contains(SkillTree.SUB_BLADEMASTER));
        assertFalse(tree.freeSubclasses(p, SkillClass.WARRIOR).contains(SkillTree.SUB_PALADIN));
        // At most four profiles of a class.
        assertNotNull(p.addProfile(SkillClass.WARRIOR, SkillTree.SUB_AXE));
        assertEquals(4, p.profilesOf(SkillClass.WARRIOR).size());
        assertNull(p.addProfile(SkillClass.WARRIOR, ""));
        assertNull(p.nextProfileKey(SkillClass.WARRIOR));
    }

    @Test
    void anUltimateAtLevelThreeEarnsOneUnlock() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        assertFalse(p.earnedUnlock());
        learnWay(tree, p, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        SkillNode ult = tree.ultimateOfProfile(p, "WARRIOR");
        assertNotNull(ult);
        assertEquals(SkillEffect.Target.ULTIMATE, ult.effect().target());
        assertEquals(3, ult.maxLevel());
        // One level below the top: no unlock yet.
        p.setLevel(ult.id(), ult.maxLevel() - 1);
        assertEquals(0, tree.grantUltUnlocks(p));
        p.setLevel(ult.id(), ult.maxLevel());
        assertTrue(tree.ultMaxed(p, "WARRIOR"));
        assertEquals(1, tree.grantUltUnlocks(p));
        assertEquals(0, tree.grantUltUnlocks(p), "only once per profile");
        assertEquals(1, p.unlocksFree);
        assertTrue(p.earnedUnlock());
        assertEquals(List.of("WARRIOR"), p.ultRewarded);
        // A profile of another class earns its own unlock.
        p.addProfile(SkillClass.GUARDIAN, "");
        p.asProfile("GUARDIAN", () -> {
            learnWay(tree, p, SkillClass.GUARDIAN, SkillTree.WAY_SHIELD);
            return null;
        });
        assertEquals(1, tree.grantUltUnlocks(p));
        assertEquals(2, p.unlocksFree);
        // The class change gives everything up, the unlocks with it.
        p.changeClass();
        assertEquals(0, p.unlocksFree);
        assertFalse(p.earnedUnlock());
        assertTrue(p.profiles().isEmpty());
    }

    @Test
    void oldSavesGetTheirUnlocksOnce() {
        SkillTree tree = new SkillTree();
        // Only one class, its ult at level 3: one unlock comes on loading.
        PlayerSkills one = new PlayerSkills();
        one.chosenClass = SkillClass.WARRIOR.name();
        learnWay(tree, one, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        tree.migrateProfiles(one);
        assertEquals(1, one.unlocksFree);
        assertEquals(PlayerSkills.PROFILE_VERSION, one.profileVersion);
        tree.migrateProfiles(one);
        assertEquals(1, one.unlocksFree, "the migration runs once");
        // A second class already opened the old way (first class full): that used up the unlock.
        PlayerSkills two = new PlayerSkills();
        two.chosenClass = SkillClass.WARRIOR.name();
        learnWay(tree, two, SkillClass.WARRIOR, SkillTree.WAY_BLADE);
        two.unlockClass(SkillClass.GUARDIAN);
        tree.migrateProfiles(two);
        assertEquals(0, two.unlocksFree);
        assertEquals(List.of("WARRIOR", "GUARDIAN"), two.profiles(), "opened classes stay");
        assertEquals(List.of("WARRIOR"), two.ultRewarded);
        // Old keys are plain class names: the first profile of a class stays readable.
        assertEquals(SkillClass.GUARDIAN, PlayerSkills.classOfKey("GUARDIAN"));
        assertEquals(SkillClass.GUARDIAN, PlayerSkills.classOfKey("GUARDIAN#3"));
        assertNull(PlayerSkills.classOfKey("NOPE#2"));
    }

    @Test
    void pointsCanBeSpentInEveryProfile() {
        SkillTree tree = new SkillTree();
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.WARRIOR.name();
        p.award(200);
        p.unlockClass(SkillClass.GUARDIAN);
        p.addProfile(SkillClass.WARRIOR, SkillTree.SUB_AXE);
        // Bought in the oldest and in the newest profile, while the first is played (the subclass
        // nodes first: free in the tree, or given with the profile of the axe fighter).
        assertTrue(tree.unlock("bloodlust", p));
        assertTrue(tree.unlock("warrior_drill", p));
        assertTrue(p.asProfile("GUARDIAN", () -> tree.unlock("riposte", p) && tree.unlock("vitality", p)));
        assertTrue(p.asProfile("WARRIOR#2", () -> tree.unlock("warrior_drill", p)));
        assertEquals(1, p.levelInProfile("WARRIOR#2", "heavy_blow"));
        assertEquals(1, p.levelInProfile("WARRIOR", "warrior_drill"));
        assertEquals(1, p.levelInProfile("WARRIOR#2", "warrior_drill"));
        assertEquals(1, p.levelInProfile("GUARDIAN", "vitality"));
        assertEquals(SkillClass.WARRIOR, p.clazz());
        assertEquals("WARRIOR", p.chosenClass, "buying elsewhere does not switch the played profile");
        int spent = tree.pointsSpentProfile(p, "WARRIOR") + tree.pointsSpentProfile(p, "WARRIOR#2")
                + tree.pointsSpentProfile(p, "GUARDIAN");
        assertEquals(200 - spent, p.points());
        // A lost night takes from the played profile only; each profile keeps its own history.
        assertTrue(p.switchToProfile("WARRIOR#2"));
        assertEquals(List.of("warrior_drill"), tree.loseLast(p, 1));
        assertEquals(1, p.levelInProfile("WARRIOR", "warrior_drill"));
        // The free level of the subclass node is never lost.
        assertEquals(List.of(), tree.loseLast(p, 1));
        assertEquals(1, p.levelOf("heavy_blow"));
    }

    @Test
    void everySubclassHasAKitWeaponWithAllItsPictures() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        int n = 0;
        for (KitWeapons.Look look : KitWeapons.all()) {
            n++;
            assertTrue(keys.add(look.key()), look.key());
            assertNotNull(getClass().getResource("/assets/nightsiege/items/kit/" + look.key() + ".json"), look.key());
            assertNotNull(getClass().getResource("/assets/nightsiege/models/item/kit/" + look.key() + ".json"), look.key());
            for (String texture : KitWeapons.textures(look)) {
                assertNotNull(getClass().getResource("/assets/nightsiege/textures/item/kit/" + texture + ".png"), texture);
            }
        }
        assertEquals(20, n, "one kit weapon per subclass");
        assertNotNull(KitWeapons.of(SkillTree.SUB_SOULCATCHER));
        assertNull(KitWeapons.of(null));
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
            // The archer's spare weapon left the crates (Simon 26.10.), the warrior's too (27.10.):
            // 13 own gains each; the engineer got the turret shield (27.10.): 15.
            // 27.10. evening: the crate review – warrior 13 (Spaltaxt for Plünderung), archer 14 (Teernetz),
            // guardian 15 (Wundsalbe), engineer 18 (Munitionskiste, Sappeur-Alarm, Langes Gebräu), summoner 15 (Seelenzins).
            // 29.10. (balance update 2): Köcher and Blendgranaten left (archer 13, engineer 17), Dritte Hand came (17 general).
            int own = switch (c) {
                case WARRIOR, ARCHER -> 13;
                case GUARDIAN, SUMMONER -> 15;
                case ENGINEER -> 17;
                default -> 0;
            };
            assertEquals(own + 17, CrateGains.forCollection(c).size(), c + " has " + own + " gains and 17 general ones");
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
        assertNull(CrateGains.get("wegtausch_s"), "way swap is gone (balance update)");
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
        // The subclass node is the first node of the tree; the trunk hangs from it.
        assertNull(tree.blockedReason("swift_draw", skills));
        assertEquals("locked", tree.blockedReason("archer_focus", skills));
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

        // The subclass picks the way (Simon, 29 October 2026): its node first, level 1 for free.
        assertEquals(0, tree.get("swift_draw").costFor(0));
        assertTrue(tree.unlock("swift_draw", skills));
        assertEquals(500, skills.points());
        assertEquals(SkillTree.SUB_SWIFT, tree.subclassOf(skills));
        assertEquals(SkillTree.WAY_BOW, skills.pathTaken(tree));
        assertEquals("choice", tree.blockedReason("heavy_draw", skills));
        assertEquals("path", tree.blockedReason("heavy_bolts", skills));
        assertEquals("upgrade", tree.blockedReason("swift_draw", skills), "level 2 needs the way's first node");
        assertTrue(tree.unlock("archer_focus", skills));
        assertNull(tree.blockedReason("a_pierce", skills));
        assertTrue(tree.unlock("a_pierce", skills));
        int before = skills.points();
        assertTrue(tree.unlock("swift_draw", skills));
        assertEquals(before - 6, skills.points(), "level 2 costs as before");
    }

    @Test
    void theArcherReachesTheHuntInBothWays() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.ARCHER.name();
        skills.award(2000);
        assertTrue(tree.unlock("a_netbolt", skills));   // the trapper's node first, level 1 free
        tree.unlock("archer_focus", skills);
        tree.unlock("heavy_bolts", skills);
        assertEquals("locked", tree.blockedReason("a_explosive", skills));
        assertTrue(tree.unlock("a_netbolt", skills));
        assertTrue(tree.unlock("a_shackle", skills));
        assertTrue(tree.unlock("x_mark", skills));
        assertTrue(tree.unlock("a_hunternet", skills));
        assertTrue(tree.unlock("x_cross_double", skills));
        // The cross double shot locks nothing any more (Simon, 29 October 2026); the barrage stays the bolter's.
        assertEquals("locked", tree.blockedReason("a_barrage", skills));
        assertTrue(tree.unlock("x_hunt", skills));
        assertEquals("choice", tree.blockedReason("bolt_volley", skills));
        assertTrue(tree.unlock("x_pack", skills));
        assertEquals("x_hunt", tree.ultimateOf(skills).id());
    }

    @Test
    void everyArcherBuildReachesRingFiveAndAMastery() {
        // Balance update, 26 October 2026: heavy shot with fire arrows and quick shot with the power shot were dead ends.
        SkillTree tree = new SkillTree();
        PlayerSkills heavy = new PlayerSkills();
        heavy.chosenClass = SkillClass.ARCHER.name();
        heavy.award(5000);
        for (String id : new String[]{"heavy_draw", "archer_focus", "a_pierce", "heavy_draw", "piercing", "a_fire"}) {
            assertTrue(tree.unlock(id, heavy), id);
        }
        assertNull(tree.blockedReason("a_firevolley", heavy));

        PlayerSkills swift = new PlayerSkills();
        swift.chosenClass = SkillClass.ARCHER.name();
        swift.award(5000);
        for (String id : new String[]{"swift_draw", "archer_focus", "a_pierce", "swift_draw", "double_shot", "b_mark",
                "a_deathmark", "a_eagle", "power_shot"}) {
            assertTrue(tree.unlock(id, swift), id);
        }
        assertNull(tree.blockedReason("a_sniper", swift));

        PlayerSkills trapper = new PlayerSkills();
        trapper.chosenClass = SkillClass.ARCHER.name();
        trapper.award(5000);
        for (String id : new String[]{"a_netbolt", "archer_focus", "heavy_bolts", "a_netbolt", "a_snarefield", "a_punch"}) {
            assertTrue(tree.unlock(id, trapper), id);
        }
        assertNull(tree.blockedReason("a_hunternet", trapper));
        for (String id : new String[]{"a_hunternet", "a_reloadrush", "bolt_volley"}) {
            assertTrue(tree.unlock(id, trapper), id);
        }
        assertNull(tree.blockedReason("a_widenet", trapper));
    }

    @Test
    void everyPlayerKeepsTheirOwnProgress() {
        SkillTree tree = new SkillTree();
        PlayerSkills one = new PlayerSkills();
        PlayerSkills two = new PlayerSkills();
        one.chosenClass = SkillClass.ARCHER.name();
        two.chosenClass = SkillClass.WARRIOR.name();
        one.award(50);

        assertTrue(tree.unlock("swift_draw", one));
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

        assertTrue(tree.unlock("heavy_draw", skills));
        assertEquals(1000, skills.points(), "the subclass's level 1 costs nothing");
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
        assertEquals("locked", tree.blockedReason(TrapKind.SPIKE.id(), engineer), "the trunk hangs from the subclass");
        // The gunner: the arrow turret is the first node of the tree, at tier 1 for free.
        assertTrue(tree.unlock(TrapKind.TURRET.id(), engineer));
        assertEquals(500, engineer.points());
        assertEquals(1, tree.trapTier(TrapKind.TURRET, engineer));
        assertTrue(tree.unlock(TrapKind.SPIKE.id(), engineer));
        assertEquals(1, tree.trapTier(TrapKind.SPIKE, engineer));
        assertEquals("locked", tree.blockedReason(TrapKind.SNARE.id(), engineer));
        assertTrue(tree.unlock(TrapKind.SPIKE.id(), engineer));
        assertTrue(tree.unlock(TrapKind.SNARE.id(), engineer));
        assertEquals("upgrade", tree.blockedReason(TrapKind.TURRET.id(), engineer), "tier 2 needs the circuit");
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
        java.util.Set<KeyHint> ultKeys = java.util.EnumSet.of(KeyHint.ULT, KeyHint.ULT_LOOK, KeyHint.ULT_SHOT);
        for (SkillNode node : tree.all()) {
            if (node.effect().target() == SkillEffect.Target.ULTIMATE) {
                assertTrue(ultKeys.contains(KeyHint.forNode(tree, node)), node.id());
                ults++;
            }
        }
        assertTrue(ults >= 16, "ults: " + ults);
        assertEquals(KeyHint.SHIELD_ACTION, KeyHint.forNode(tree, "g_shieldthrow"));
        assertEquals(KeyHint.NONE, KeyHint.forNode(tree, "archer_focus"));
        // Since 26 October 2026 the key is written next to every skill.
        assertEquals(KeyHint.ULT_LOOK, KeyHint.forNode(tree, "execute"));
        assertEquals(KeyHint.ULT_SHOT, KeyHint.forNode(tree, "power_shot"));
        assertEquals(KeyHint.ULT_UPGRADE, KeyHint.forNode(tree, "w_chain_execute"));
        assertEquals(KeyHint.ULT_UPGRADE, KeyHint.forNode(tree, "s_titanfist"));
        assertEquals(KeyHint.PLACE_TRAP, KeyHint.forNode(tree, TrapKind.SPIKE.id()));
        assertEquals(KeyHint.PLACE_TRAP, KeyHint.forNode(tree, "m_cross_mine"));
        assertEquals(KeyHint.PERFECT_BLOCK, KeyHint.forNode(tree, "g_shieldbash"));
        assertEquals(KeyHint.SNEAK_BLOCK, KeyHint.forNode(tree, "g_hedgehog"));
        assertEquals(KeyHint.JUMP_ATTACK, KeyHint.forNode(tree, "w_earthshock"));
        assertEquals(KeyHint.SNEAK_SHOOT, KeyHint.forNode(tree, "a_bracing"));
        assertEquals(KeyHint.REMOTE_DETONATE, KeyHint.forNode(tree, "e_detonator"));
        // The summoner's spells: each on its key of the bar, the right click with the staff casts it again.
        assertEquals(KeyHint.STAFF_SPELL, KeyHint.forNode(tree, "d_corpse"));
        assertEquals(KeyHint.STAFF_SPELL, KeyHint.forNode(tree, "d_haste"));
        assertEquals(KeyHint.STAFF_SPELL, KeyHint.forNode(tree, "d_host"));
        assertEquals(KeyHint.ULT2_REMOTE, KeyHint.forGain("magnetfeld"));
        assertEquals(KeyHint.ULT2_STAFF, KeyHint.forGain("seelentausch"));
        assertEquals(KeyHint.QUIVER_SHOT, KeyHint.forGain("sturmpfeile"));
        assertEquals(KeyHint.NONE, KeyHint.forGain("totem"));
        assertEquals(KeyHint.ULT2, KeyHint.forGain("spiegelschild"));
        assertEquals(KeyHint.GAIN_KEY, KeyHint.forGain("kletterhaken"));
        assertEquals(KeyHint.USE, KeyHint.forGadget("wurfbeil"));
        // Every gain with a key exists.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CrateGains.Gain g : CrateGains.all()) {
            ids.add(g.id());
        }
        for (String id : new String[]{"dritte_hand", "sprungstiefel", "zielfernrohr", "wurfspeere", "wurfbeile",
                "oelfass", "kletterhaken", "kampfhorn", "tarnumhang", "signalhorn", "mauerkelle", "ersatzbatterie",
                "kriegsbanner", "barrikaden", "leibwaechter", "schlachtentrance", "lockpfeil", "magnetfeld", "spiegelschild",
                "rueckrufpfeife", "geisterross", "futternapf", "totenglocke", "seelentausch"}) {
            assertTrue(ids.contains(id), id);
            assertNotEquals(KeyHint.NONE, KeyHint.forGain(id), id);
        }
    }

    @Test
    void cratesSkipGainsASubclassHasNoUseFor() {
        // Balance update 2 (Simon 29.10.: "schau über die Beutekisten im Bezug auf Sinn für alle Unterklassen").
        CrateGains.Gain net = CrateGains.get("teernetz");
        assertTrue(CrateGains.drawable(net, SkillClass.ARCHER, SkillTree.WAY_CROSSBOW, SkillTree.SUB_TRAPPER));
        assertFalse(CrateGains.drawable(net, SkillClass.ARCHER, SkillTree.WAY_CROSSBOW, SkillTree.SUB_BOLTER));
        assertFalse(CrateGains.drawable(net, SkillClass.ARCHER, SkillTree.WAY_BOW, ""));
        assertTrue(CrateGains.drawable(net, SkillClass.ARCHER, SkillTree.WAY_CROSSBOW, ""));
        assertFalse(CrateGains.drawable(CrateGains.get("golemplatten"), SkillClass.SUMMONER, SkillTree.WAY_WILD, SkillTree.SUB_TAMER));
        assertTrue(CrateGains.drawable(CrateGains.get("golemplatten"), SkillClass.SUMMONER, SkillTree.WAY_WILD, SkillTree.SUB_GOLEMSMITH));
        assertFalse(CrateGains.drawable(CrateGains.get("notfallmine"), SkillClass.ENGINEER, SkillTree.WAY_CHEMISTRY, SkillTree.SUB_ALCHEMIST));
        assertFalse(CrateGains.drawable(CrateGains.get("doppelbrau"), SkillClass.ENGINEER, SkillTree.WAY_CHEMISTRY, SkillTree.SUB_DEMOLITION));
        CrateGains.Gain hand = CrateGains.get("dritte_hand");
        assertFalse(CrateGains.drawable(hand, SkillClass.WARRIOR, SkillTree.WAY_BLADE, SkillTree.SUB_BERSERKER));
        assertFalse(CrateGains.drawable(hand, SkillClass.ARCHER, SkillTree.WAY_BOW, ""));
        assertFalse(CrateGains.drawable(hand, SkillClass.ENGINEER, SkillTree.WAY_MECHANICS, SkillTree.SUB_GUNNER));
        assertTrue(CrateGains.drawable(hand, SkillClass.ENGINEER, SkillTree.WAY_CHEMISTRY, SkillTree.SUB_DEMOLITION));
        assertTrue(CrateGains.drawable(hand, SkillClass.GUARDIAN, "", ""));
        assertNull(CrateGains.get("blendgranaten"));
        assertNull(CrateGains.get("koecher"));
        Random random = new Random(7);
        int[] odds = {20, 20, 20, 20, 20};
        for (int i = 0; i < 3000; i++) {
            CrateGains.Gain g = CrateGains.roll(SkillClass.ARCHER, SkillTree.WAY_CROSSBOW, SkillTree.SUB_BOLTER, odds, 0.7, random);
            assertNotEquals("teernetz", g.id());
            assertNotEquals("dritte_hand", g.id());
        }
        // Every key ability is a gain that can be drawn.
        for (String id : CrateGains.KEY_ABILITIES) {
            assertNotNull(CrateGains.get(id), id);
        }
    }

    @Test
    void retiredGainsPayTheirPointsBackOnce() {
        com.mki.nightsiege.game.SiegeSave save = new com.mki.nightsiege.game.SiegeSave();
        PlayerSkills own = save.skillsOf("p1");
        own.setGainStage(SkillClass.ENGINEER, "blendgranaten", 3);
        own.setGainStage(SkillClass.ARCHER, "koecher", 1);
        own.setGainStage(SkillClass.ARCHER, "lichtpfeile", 1);
        int before = own.points();
        int paid = save.payRetiredGains();
        assertEquals(3 * CrateRarity.COMMON.duplicatePoints() + CrateRarity.EPIC.duplicatePoints(), paid);
        assertEquals(before + paid, own.points());
        assertEquals(0, own.gainStage(SkillClass.ENGINEER, "blendgranaten"));
        assertEquals(0, own.gainStage(SkillClass.ARCHER, "koecher"));
        assertEquals(1, own.gainStage(SkillClass.ARCHER, "lichtpfeile"));
        assertEquals(0, save.payRetiredGains());
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
        // Subclass first: it picks the way, the way of might closes.
        assertTrue(tree.unlock("w_combo", warrior));
        assertEquals("choice", tree.blockedReason("bloodlust", warrior));
        assertEquals(SkillTree.SUB_BLADEMASTER, tree.subclassOf(warrior));
        assertEquals(SkillTree.WAY_BLADE, warrior.pathTaken(tree));
        assertEquals("path", tree.blockedReason("sunder", warrior));
        assertTrue(tree.unlock("warrior_drill", warrior));
        // Trunk choice: endurance closes footwork.
        assertTrue(tree.unlock("w_endurance", warrior));
        assertEquals("choice", tree.blockedReason("w_footwork", warrior));
        // The shared node of the blade way opens the subclass's level 2.
        assertEquals("upgrade", tree.blockedReason("w_combo", warrior));
        assertTrue(tree.unlock("champion_bane", warrior));
        assertEquals("locked", tree.blockedReason("keen_edge", warrior));
        assertTrue(tree.unlock("w_combo", warrior));
        assertTrue(tree.unlock("keen_edge", warrior));
        assertEquals("choice", tree.blockedReason("blood_drinker", warrior));
        // Each tier needs the one below (Simon, 29 October 2026): the hybrid follows ring III, the
        // talents follow the hybrid, ring VI the talents, the keystone a mastery.
        assertEquals("locked", tree.blockedReason("e_cross_heavy", warrior));
        assertTrue(tree.unlock("w_bladefrenzy", warrior));
        // A cross talent takes the place of an own one.
        assertTrue(tree.unlock("e_cross_heavy", warrior));
        assertEquals("choice", tree.blockedReason("w_frenzy", warrior));
        assertTrue(tree.unlock("w_evasion", warrior));
        assertTrue(tree.unlock("whirlwind", warrior));
        assertEquals("choice", tree.blockedReason("execute", warrior));
        assertEquals("locked", tree.blockedReason("w_glass_cannon", warrior));
        assertTrue(tree.unlock("w_bladestorm", warrior));
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
        assertTrue(tree.unlock("g_bastion", guardian));
        assertEquals(SkillTree.SUB_BULWARK, tree.subclassOf(guardian));
        assertEquals(SkillTree.WAY_SHIELD, guardian.pathTaken(tree));
        assertEquals("choice", tree.blockedReason("riposte", guardian));
        assertEquals("path", tree.blockedReason("field_dressing", guardian));
        assertTrue(tree.unlock("vitality", guardian));
        assertTrue(tree.unlock("g_challenger", guardian));
        assertEquals("choice", tree.blockedReason("toughness", guardian));
        assertTrue(tree.unlock("g_shieldwall", guardian));
        assertEquals("locked", tree.blockedReason("g_ironhide", guardian));
        assertTrue(tree.unlock("g_bastion", guardian));
        assertTrue(tree.unlock("g_anchor", guardian));
        assertTrue(tree.unlock("g_hedgehog", guardian));
        assertTrue(tree.unlock("g_thorns", guardian));
        // The ward from the other way no longer closes the shield charge (Simon, 29 October 2026:
        // a choice closes at most the next tier, and the charge's masteries lie two tiers on).
        assertTrue(tree.unlock("s_cross_ward", guardian));
        assertNull(tree.blockedReason("shield_charge", guardian));
        assertTrue(tree.unlock("taunt", guardian));
        assertEquals("choice", tree.blockedReason("shield_charge", guardian));
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
                {"g_shieldwall", "riposte", "riposte", "g_shieldthrow", "g_boomerang", "g_thorns"},
                {"g_shieldwall", "g_bastion", "g_bastion", "g_ironhide", "g_vengeance", "g_counterstrike"},
                {"g_shieldwall", "riposte", "riposte", "g_shieldbash", "g_vengeance", "g_counterstrike"},
                {"field_dressing", "g_ward", "g_ward", "g_dome", "g_lifebond", "g_selfless"},
                {"field_dressing", "g_ward", "g_ward", "g_dome", "g_wellspring", "g_lightbringer"},
                {"field_dressing", "g_healing_hand", "g_healing_hand", "g_triage", "g_miracle", "g_selfless"},
                {"field_dressing", "g_ward", "g_ward", "g_cleanse", "g_lifebond", "b_cross_riposte", "g_overflow",
                        "g_sanctuary", "g_eternal_watch", "g_patron"},
                // Balance update 26 October 2026: every subclass lane reaches a hybrid node and a mastery.
                {"g_shieldwall", "riposte", "riposte", "g_shieldbash", "g_vengeance", "g_thorns"},
                {"g_shieldwall", "riposte", "riposte", "g_shieldthrow", "g_boomerang", "s_cross_dressing"},
                {"g_shieldwall", "g_bastion", "g_bastion", "g_ironhide", "g_vengeance", "g_thorns"},
                {"g_shieldwall", "g_bastion", "g_bastion", "g_anchor", "g_hedgehog", "g_counterstrike"},
                {"field_dressing", "g_ward", "g_ward", "g_cleanse", "g_lifebond", "g_lightbringer"},
                {"field_dressing", "g_healing_hand", "g_healing_hand", "g_triage", "g_miracle", "b_cross_riposte", "g_grace",
                        "mass_heal", "g_rebirth"},
        };
        for (String[] route : routes) {
            PlayerSkills guardian = new PlayerSkills();
            guardian.chosenClass = SkillClass.GUARDIAN.name();
            guardian.award(5000);
            learnRoute(tree, guardian, List.of("vitality"), route);
        }
        // The other way's riposte and the healing spring shut each other (neighbour tiers since
        // 29 October 2026); the life spring, two tiers further, stays open.
        PlayerSkills cross = new PlayerSkills();
        cross.chosenClass = SkillClass.GUARDIAN.name();
        cross.award(5000);
        for (String id : new String[]{"g_ward", "vitality", "field_dressing", "g_ward", "g_dome", "g_wellspring"}) {
            assertTrue(tree.unlock(id, cross), id);
        }
        assertEquals("lockedby", tree.blockedReason("b_cross_riposte", cross));
        assertEquals("locked", tree.blockedReason("g_lifespring", cross));
        assertEquals(SkillTree.SUB_WARDEN, tree.subclassOf(cross));
    }

    /**
     * Learns a route the way it goes since the subclass comes first (Simon, 29 October 2026): the
     * route's subclass node at its free level 1, then the trunk, then the route; the route's first step
     * on the subclass node (written as before, after the way's first node) is its level 2 now.
     */
    private static void learnRoute(SkillTree tree, PlayerSkills p, List<String> trunk, String... route) {
        String sub = java.util.Arrays.stream(route).filter(SkillTree::isSubclassNode).findFirst().orElse(null);
        if (sub != null) {
            assertTrue(tree.unlock(sub, p), sub + " " + tree.blockedReason(sub, p));
        }
        for (String id : trunk) {
            assertTrue(tree.unlock(id, p), id + " " + tree.blockedReason(id, p));
        }
        boolean skipped = false;
        for (String id : route) {
            if (!skipped && id.equals(sub)) {
                skipped = true;   // that was the free level 1
                continue;
            }
            assertTrue(tree.unlock(id, p), id + " " + tree.blockedReason(id, p));
        }
    }

    @Test
    void aChoiceClosesAtMostTheNextTier() {
        // Simon, 29 October 2026: "Sachen aus 3 dürfen nur Sachen aus 4 ausschließen". In every
        // subclass tree a pick (its choice group, its locks, and what can then no longer be reached)
        // closes nothing two or more tiers above it, and every run of picks reaches the keystone.
        SkillTree tree = new SkillTree();
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            java.util.Map<String, SkillNode> in = subclassTree(tree, info);
            assertTrue(in.size() > 20, info.key());
            for (SkillNode pick : in.values()) {
                for (String id : closedBy(in, List.of(pick))) {
                    assertTrue(in.get(id).row() <= pick.row() + 1, info.key() + ": " + pick.id() + " closes " + id);
                }
            }
            assertEquals(0, deadEnds(in, new java.util.ArrayList<>(), 0), info.key());
        }
    }

    /** The trunk and the subclass's way without the other subclasses' nodes (as the skill window shows it). */
    private static java.util.Map<String, SkillNode> subclassTree(SkillTree tree, SkillTree.SubclassInfo info) {
        Set<String> others = new HashSet<>();
        Set<String> otherLanes = new HashSet<>();
        for (SkillTree.SubclassInfo o : SkillTree.subclassInfosOf(info.clazz())) {
            if (!o.key().equals(info.key())) {
                others.add(o.node());
                otherLanes.add(o.key());
            }
        }
        List<SkillNode> all = tree.ofClass(info.clazz());
        Set<String> ids = new HashSet<>();
        all.forEach(n -> ids.add(n.id()));
        java.util.Map<String, SkillNode> in = new java.util.LinkedHashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNode n : all) {
                if (in.containsKey(n.id()) || (!n.onTrunk() && (!n.path().equals(info.way()) || others.contains(n.id())
                        || otherLanes.contains(tree.laneOf(n.id()))))) {
                    continue;
                }
                boolean ok = true;
                for (List<SkillNode.Need> any : n.needs()) {
                    ok &= any.stream().anyMatch(need -> in.containsKey(need.id()) || !ids.contains(need.id()));
                }
                if (ok) {
                    in.put(n.id(), n);
                    changed = true;
                }
            }
        }
        return in;
    }

    /** What these picks shut in a subclass tree: their choice groups, their locks, and what then hangs only on those. */
    private static Set<String> closedBy(java.util.Map<String, SkillNode> in, List<SkillNode> picks) {
        Set<String> picked = new HashSet<>();
        picks.forEach(n -> picked.add(n.id()));
        Set<String> closed = new HashSet<>();
        for (SkillNode a : picks) {
            for (SkillNode n : in.values()) {
                if (!picked.contains(n.id()) && ((a.inGroup() && a.group().equals(n.group()))
                        || a.locksList().contains(n.id()) || n.locksList().contains(a.id()))) {
                    closed.add(n.id());
                }
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillNode n : in.values()) {
                if (picked.contains(n.id()) || closed.contains(n.id())) {
                    continue;
                }
                for (List<SkillNode.Need> any : n.needs()) {
                    if (any.stream().noneMatch(need -> picked.contains(need.id())
                            || (in.containsKey(need.id()) && !closed.contains(need.id())))) {
                        closed.add(n.id());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return closed;
    }

    /** Every run of one pick per choice tier from this row on; counts the runs that lose a later tier. */
    private static int deadEnds(java.util.Map<String, SkillNode> in, List<SkillNode> picks, int fromRow) {
        Set<String> closed = closedBy(in, picks);
        for (int row = fromRow; row <= 10; row++) {
            List<SkillNode> open = new java.util.ArrayList<>();
            boolean grouped = false;
            for (SkillNode n : in.values()) {
                if (n.row() == row && n.inGroup()) {
                    grouped = true;
                    if (!closed.contains(n.id()) && !picks.contains(n)) {
                        open.add(n);
                    }
                }
            }
            if (!grouped) {
                continue;
            }
            if (open.isEmpty()) {
                return 1;
            }
            int bad = 0;
            for (SkillNode n : open) {
                List<SkillNode> next = new java.util.ArrayList<>(picks);
                next.add(n);
                bad += deadEnds(in, next, row + 1);
            }
            return bad;
        }
        return 0;
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
                    // The trunk hangs from the subclass node, whichever way it is on (29 October 2026).
                    assertTrue(other.onTrunk() || other.path().equals(node.path())
                            || (node.onTrunk() && SkillTree.isSubclassNode(other.id())), node.id() + " needs " + need.id());
                }
            }
            for (List<SkillNode.Need> any : node.upgradeNeeds()) {
                for (SkillNode.Need need : any) {
                    SkillNode other = tree.get(need.id());
                    assertTrue(other != null, node.id() + " needs unknown " + need.id());
                    assertEquals(node.path(), other.path(), node.id() + " needs " + need.id() + " from level 2");
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
        // The subclass traps are the roots of their trees; from tier 2 on they need the way's first node.
        assertNull(tree.get(TrapKind.TESLA.id()).requires());
        assertEquals("e_circuit", tree.get(TrapKind.TESLA.id()).upgradeRequires());
        assertEquals("e_detonator", tree.get(TrapKind.MORTAR.id()).upgradeRequires());
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
        // The electrician: the tesla coil first (the root of the tree, tier 1 for free), then the trunk.
        for (String id : new String[]{"tesla_coil", "spike_trap", "spike_trap", "e_battery", "snare_trap", "e_circuit"}) {
            assertTrue(tree.unlock(id, engineer), id);
        }
        assertEquals("choice", tree.blockedReason("e_finemech", engineer));
        assertEquals(SkillTree.WAY_MECHANICS, engineer.pathTaken(tree));
        assertEquals("path", tree.blockedReason("e_detonator", engineer));
        assertEquals(SkillTree.SUB_ELECTRICIAN, tree.subclassOf(engineer));
        assertEquals("choice", tree.blockedReason("arrow_turret", engineer));
        assertEquals(0, tree.trapTier(TrapKind.MORTAR, engineer));
        assertTrue(tree.unlock("tesla_coil", engineer));
        assertTrue(tree.unlock("capacitor", engineer));
        assertEquals(4, tree.trapTier(TrapKind.TESLA, engineer));
        // The mortar from the other way, at tier 2, closes the network on the next tier (and since
        // Simon's rule of 29 October 2026 nothing further up: the ball lightning stays open).
        assertTrue(tree.unlock("m_cross_mine", engineer));
        assertEquals(2, tree.trapTier(TrapKind.MORTAR, engineer));
        assertEquals("lockedby", tree.blockedReason("e_network", engineer));
        assertTrue(tree.unlock("e_beacon", engineer));
        assertTrue(tree.unlock("e_watchtower", engineer));
        assertTrue(tree.unlock("thunderstorm", engineer));
        assertNull(tree.blockedReason("e_ball_lightning", engineer));
        // The superconductor needs only the thunderstorm, so the electrician keeps a mastery (balance update).
        assertNull(tree.blockedReason("e_superconductor", engineer));
        assertTrue(tree.unlock("e_superconductor", engineer));
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
                {"e_detonator", "mortar_tower", "mortar_tower", "e_shrapnel_mine", "e_chain_reaction", "e_napalm"},
                {"e_detonator", "potion_tower", "potion_tower", "e_acid", "e_accelerant", "e_plague_mine"},
                {"e_circuit", "tesla_coil", "tesla_coil", "capacitor", "m_cross_mine", "e_beacon"},
        };
        for (String[] route : routes) {
            PlayerSkills engineer = new PlayerSkills();
            engineer.chosenClass = SkillClass.ENGINEER.name();
            engineer.award(5000);
            learnRoute(tree, engineer, List.of("spike_trap", "spike_trap", "snare_trap"), route);
        }
    }

    @Test
    void everyEngineerRouteKeepsARingFiveNodeAndAMastery() {
        // Balance update (26 October 2026): no choice of the engineer runs into a dead end; since
        // 29 October 2026 every tier needs the one below, up to the keystone.
        SkillTree tree = new SkillTree();
        String[][][] ways = {
                {{"arrow_turret", "tesla_coil"}, {"turret_tuning", "e_ammobelt", "capacitor", "e_chainbolt"},
                        {"e_seeker", "e_overvolt", "m_cross_mine"}, {"e_backup", "e_watchtower", "m_cross_poison"},
                        {"overcharge", "thunderstorm"}},
                {{"mortar_tower", "potion_tower"}, {"flame_trap", "e_shrapnel_mine", "toxic_brew", "e_acid"},
                        {"e_chain_reaction", "e_accelerant", "c_cross_turret"}, {"e_blastpower", "e_slowpoison", "c_cross_tesla"},
                        {"bombardment", "toxic_cloud"}}};
        String[][] firstOf = {{"e_circuit"}, {"e_detonator"}};
        String[][] ringFive = {{"e_charged", "e_beacon", "e_network"}, {"e_napalm", "e_plague_mine", "e_targetblast"}};
        String[][] masteries = {{"e_sustained", "e_piercer", "e_ball_lightning", "e_superconductor"},
                {"e_carpet", "e_firebombs", "e_plague", "e_acid_fog"}};
        String[][] keys = {{"e_fortress", "e_fieldtech"}, {"e_pyro", "e_plague_doctor"}};
        int routes = 0;
        for (int w = 0; w < ways.length; w++) {
            routes += walkRoutes(tree, SkillClass.ENGINEER, List.of("spike_trap", "spike_trap", "snare_trap", firstOf[w][0]),
                    ways[w], ringFive[w], masteries[w], keys[w]);
        }
        assertTrue(routes > 40, "routes " + routes);
    }

    /**
     * Walks every pick of subclass, ring III, ring IV, ring VI and ult (Simon, 29 October 2026: each
     * tier needs the one below): ring V (the first open one), a mastery and the keystone must always
     * stay open. Returns the routes that could be walked (a talent of the other subclass ends one).
     */
    private static int walkRoutes(SkillTree tree, SkillClass clazz, List<String> trunk, String[][] rows,
                                  String[] ringFive, String[] masteries, String[] keys) {
        int routes = 0;
        int[] pick = new int[rows.length];
        while (true) {
            PlayerSkills p = new PlayerSkills();
            p.chosenClass = clazz.name();
            p.unlockClass(clazz);
            p.award(100_000);
            List<String> chosen = new java.util.ArrayList<>(trunk);
            // The subclass first (29 October 2026): its node is the root of the tree, level 1 free.
            boolean possible = tree.unlock(rows[0][pick[0]], p);
            for (String id : trunk) {
                possible &= tree.unlock(id, p) || p.levelOf(id) > 0;
            }
            for (int r = 0; r < rows.length && possible; r++) {
                String id = rows[r][pick[r]];
                chosen.add(id);
                if (!tree.unlock(id, p) && p.levelOf(id) == 0) {
                    possible = false;   // a talent of the other subclass
                    break;
                }
                // (r == 0: that was the subclass's level 2, which the talents need)
                if (r == 2) {
                    final PlayerSkills skills = p;
                    String five = java.util.Arrays.stream(ringFive).filter(x -> tree.canUnlock(x, skills)).findFirst().orElse(null);
                    assertNotNull(five, "ring V: " + chosen);
                    assertTrue(tree.unlock(five, p), five);
                    chosen.add(five);
                }
            }
            if (possible) {
                routes++;
                final PlayerSkills skills = p;
                String mastery = java.util.Arrays.stream(masteries).filter(x -> tree.canUnlock(x, skills)).findFirst().orElse(null);
                assertNotNull(mastery, "mastery: " + chosen);
                assertTrue(tree.unlock(mastery, p), mastery);
                assertTrue(java.util.Arrays.stream(keys).anyMatch(x -> tree.canUnlock(x, skills)), "keystone: " + chosen);
            }
            int k = rows.length - 1;
            while (k >= 0 && ++pick[k] >= rows[k].length) {
                pick[k] = 0;
                k--;
            }
            if (k < 0) {
                return routes;
            }
        }
    }

    @Test
    void theTeslaTablesKeepTheirNumbers() {
        // The skill values hold the numbers now; the game uses the same ones as before.
        assertEquals(250, TrapKind.TESLA.cooldownTicks(1));
        assertEquals(100, TrapKind.TESLA.cooldownTicks(4));
        assertEquals(4, TrapKind.TESLA.chainTargets(3));
        assertEquals(2, TrapKind.MORTAR.maxPlaced(1));
        assertEquals(2.75, TrapKind.MORTAR.splash(3), 1e-9);
        assertEquals(80, TrapKind.BREWER.holdTicks(1));
        assertEquals(2.0, SkillValues.get("m_cross_mine", "tier", 0), 1e-9);
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
    void updateOneSwapsTheLookalikesForTheNewAttackersAndKeepsTunedWeights() {
        SiegeConfig old = new SiegeConfig();
        old.settingsVersion = 13;
        old.mobListVersion = 2;
        old.mobs = new java.util.ArrayList<>(List.of(
                new MobEntry("minecraft:zombie", 1, 20, -1.0),
                new MobEntry("takesapillage:skirmisher", 4, 12, 0.36),
                new MobEntry("variantsandventures:thicket", 2, 6, 0.0),
                new MobEntry("variantsandventures:verdant", 2, 6, 0.0),
                new MobEntry("variantsandventures:murk", 3, 6, 0.1),
                new MobEntry("variantsandventures:gelid", 3, 6, 0.1),
                new MobEntry("minecraft:parched", 5, 5, 0.2),
                new MobEntry("minecraft:camel_husk+minecraft:husk", 5, 3, 0.2)));
        old.effectMobReplacements.put("variantsandventures:thicket", "minecraft:zombie");
        old.sanitize();
        assertEquals(SiegeConfig.CURRENT_MOB_LIST, old.mobListVersion);
        for (SiegeConfig cfg : List.of(old, freshConfig())) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (MobEntry e : cfg.mobs) {
                ids.add(e.id);
            }
            for (String gone : SiegeConfig.RETIRED_MOBS) {
                assertFalse(ids.contains(gone), gone);
            }
            for (String added : List.of("nightsiege:revenant", "nightsiege:tunneler", "nightsiege:standard_bearer",
                    "nightsiege:grappler", "nightsiege:iron_knight", SiegeConfig.CAMEL_TROOP)) {
                assertTrue(ids.contains(added), added);
            }
            assertFalse(ids.contains("nightsiege:treasure_raider"));   // it comes on its own, not with the waves
            assertFalse(cfg.effectMobReplacements.containsKey("variantsandventures:thicket"));
        }
        // The axe illagers tuned on the server stayed as they were (Simon, 27 October 2026); the balancing
        // update of 28 October halves the skirmisher and lets him come from level 6 (Simon 23:10).
        MobEntry skirmisher = old.mobs.stream().filter(e -> e.id.equals("takesapillage:skirmisher")).findFirst().orElseThrow();
        assertEquals(6, skirmisher.weight);
        assertEquals(0.18, skirmisher.weightPerLevel, 1e-9);
        assertEquals(6, skirmisher.minLevel);
        // The camel troop at low levels: plain zombie and skeleton on the camel husk.
        assertEquals("minecraft:camel_husk+minecraft:zombie+minecraft:skeleton", old.withoutEffects(SiegeConfig.CAMEL_TROOP));
    }

    private static SiegeConfig freshConfig() {
        SiegeConfig fresh = new SiegeConfig();
        fresh.sanitize();
        return fresh;
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
        assertTrue(tree.unlock("g_ward", p));
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
        assertEquals(List.of("toughness"), old);
        // ... down to the root of the trunk, but never the subclass's free level 1.
        int lost = p.lost;
        assertEquals(List.of("vitality"), tree.loseLast(p, 3));
        assertEquals(1, p.levelOf("g_ward"));
        assertEquals(lost + tree.get("vitality").costFor(0), p.lost);
        // The class change no longer gives lost points back.
        p.changeClass();
        assertEquals(p.earned - p.donated - p.lost, p.points());
    }

    @Test
    void anOldConfigTurnsTheDungeonsBackOnOnce() {
        SiegeConfig old = new SiegeConfig();
        old.dungeonsEnabled = false;
        old.settingsVersion = 2;
        old.sanitize();
        assertTrue(old.dungeonsEnabled);
        assertFalse(old.bloodMoonGlow);
        assertEquals(SiegeConfig.SETTINGS_VERSION, old.settingsVersion);
        // Switched off again by hand, it stays off.
        old.dungeonsEnabled = false;
        old.sanitize();
        assertFalse(old.dungeonsEnabled);
    }

    @Test
    void everyFragmentHasAnEvent() {
        for (NightFragment fragment : NightFragment.values()) {
            assertNotEquals(SiegeEvent.NONE, fragment.event());
        }
    }

    @Test
    void theClassChangeGivesEveryPointBackOnce() {
        SkillTree tree = new SkillTree();
        PlayerSkills skills = new PlayerSkills();
        skills.chosenClass = SkillClass.GUARDIAN.name();
        skills.award(40);
        skills.donated = 5;
        skills.spend(5);
        assertTrue(tree.unlock("riposte", skills));
        assertTrue(tree.unlock("vitality", skills));
        skills.changeClass();
        assertFalse(skills.hasClass());
        assertEquals(35, skills.points(), "the free level 1 of the subclass pays out nothing");
        assertEquals(0, skills.levelOf("vitality"));
        assertEquals(0, skills.levelOf("riposte"));
        assertEquals("", skills.plannedSubclass(SkillClass.GUARDIAN.name()));
        assertTrue(skills.classResetUsed);
    }

    // --- the subclass first, level 1 free (Simon, 29 October 2026) -----------------

    @Test
    void theSubclassNodeIsTheRootOfItsTree() {
        SkillTree tree = new SkillTree();
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            SkillNode sub = tree.get(info.node());
            assertEquals(0, sub.row(), info.node());
            assertTrue(sub.needs().isEmpty(), info.node() + " needs nothing");
            assertEquals(1, sub.freeLevels(), info.node());
            assertEquals(0, sub.costFor(0), info.node() + ": level 1 is free");
            assertEquals(sub.baseCost() + sub.costStep(), sub.costFor(1), info.node() + ": level 2 costs as before");
            // From level 2 on it needs the first node of its way (the one it needed before).
            SkillNode gate = tree.get(sub.upgradeRequires());
            assertNotNull(gate, info.node());
            assertEquals(info.way(), gate.path(), info.node());
            assertEquals(3, gate.row(), info.node());
            // The trunk's root hangs from it (and from every other subclass node of the class).
            SkillNode root = tree.ofClass(info.clazz()).stream().filter(n -> n.onTrunk() && n.row() == 1)
                    .filter(n -> n.requires() != null && n.requires().contains("|")).findFirst().orElseThrow();
            assertTrue(root.needs().get(0).stream().anyMatch(n -> n.id().equals(info.node())), root.id());
        }
        // The deeper tiers keep their needs (ring IV wants the subclass at level 2).
        assertEquals("swift_draw:2", tree.get("double_shot").requires());
        assertEquals("d_soulcatcher:2", tree.get("d_swarm").requires());
    }

    @Test
    void levelOneComesFreeWithTheSubclass() {
        SkillTree tree = new SkillTree();
        // The first class, picked with its subclass (onChooseClass): the profile holds the node at level 1.
        PlayerSkills p = new PlayerSkills();
        p.award(50);
        String key = p.addProfile(SkillClass.ARCHER, SkillTree.SUB_HEAVY);
        p.playOpened(key);
        assertEquals("ARCHER", p.chosenClass);
        assertEquals(1, p.levelOf("heavy_draw"));
        assertTrue(p.classLevels.get("ARCHER") == null || p.classLevels.get("ARCHER").isEmpty());
        assertEquals(50, p.points());
        assertTrue(p.history.isEmpty());
        assertEquals(0, tree.pointsSpent(p, SkillClass.ARCHER), "the free level is no points spent");
        assertEquals(SkillTree.SUB_HEAVY, tree.subclassOf(p));
        // The trunk opens at once; level 2 costs as before once the way's first node is there.
        assertNull(tree.blockedReason("archer_focus", p));
        assertEquals("upgrade", tree.blockedReason("heavy_draw", p));
        assertTrue(tree.unlock("archer_focus", p));
        assertTrue(tree.unlock("a_pierce", p));
        int before = p.points();
        assertTrue(tree.unlock("heavy_draw", p));
        assertEquals(before - tree.get("heavy_draw").costFor(1), p.points());
        assertEquals(6, before - p.points());
        assertEquals(List.of("archer_focus", "a_pierce", "heavy_draw"), p.history);
        assertEquals(p.earned - p.points(), tree.pointsSpent(p, SkillClass.ARCHER));
        // Fixing the subclass of a class picked without one gives it too, once.
        PlayerSkills q = new PlayerSkills();
        q.chosenClass = SkillClass.GUARDIAN.name();
        q.fixSub(SkillClass.GUARDIAN, "g_ward");
        assertEquals(1, q.levelOf("g_ward"));
        assertFalse(q.grantFreeSubclass(q.chosenClass), "never twice");
        q.fixSub(SkillClass.GUARDIAN, "g_ward");
        assertEquals(1, q.levelOf("g_ward"));
        // A further profile gets its own free level, the played one stays as it is.
        String second = q.addProfile(SkillClass.GUARDIAN, SkillTree.SUB_MEDIC);
        assertEquals(1, q.levelInProfile(second, "g_healing_hand"));
        assertEquals(0, q.levelOf("g_healing_hand"));
        // Switching profiles keeps every free level where it is.
        assertTrue(q.switchToProfile(second));
        assertEquals(1, q.levelOf("g_healing_hand"));
        assertEquals(1, q.levelInProfile("GUARDIAN", "g_ward"));
        assertTrue(q.switchToProfile("GUARDIAN"));
        assertEquals(0, q.grantFreeSubclasses());
        // The class change pays back what was earned; the free levels were never paid.
        q.award(30);
        assertTrue(tree.unlock("vitality", q));
        q.changeClass();
        assertEquals(30, q.points());
        assertEquals(0, q.levelOf("g_ward"));
        assertTrue(q.profiles().isEmpty());
    }

    @Test
    void aProfileCannotLearnTheOtherWay() {
        SkillTree tree = new SkillTree();
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            PlayerSkills p = new PlayerSkills();
            p.award(100_000);
            p.playOpened(p.addProfile(info.clazz(), info.key()));
            for (int round = 0; round < 60; round++) {
                for (SkillNode n : tree.ofClass(info.clazz())) {
                    if (tree.blockedReason(n.id(), p) == null) {
                        tree.unlock(n.id(), p);
                    }
                }
            }
            for (SkillNode n : tree.ofClass(info.clazz())) {
                if (!n.onTrunk() && !n.path().equals(info.way())) {
                    assertEquals(0, p.levelOf(n.id()), info.key() + " learned " + n.id() + " of the other way");
                    assertNotNull(tree.blockedReason(n.id(), p), n.id());
                }
                if (SkillTree.isSubclassNode(n.id()) && !n.id().equals(info.node())) {
                    assertEquals(0, p.levelOf(n.id()), info.key() + " also took " + n.id());
                }
            }
            assertEquals(tree.get(info.node()).maxLevel(), p.levelOf(info.node()), info.key());
            assertTrue(tree.ultimateMaxed(p, info.clazz()), info.key());
            assertEquals(p.earned - p.points(), tree.pointsSpent(p, info.clazz()), info.key());
        }
    }

    @Test
    void oldSavesGetTheirLevelOnePaidBackOnce() {
        SkillTree tree = new SkillTree();
        PlayerSkills old = new PlayerSkills();
        old.chosenClass = SkillClass.ARCHER.name();
        old.award(100);
        // Bought the old way: trunk, way, then the subclass twice (2 + 3 + 4 + 6 points).
        for (String id : new String[]{"archer_focus", "a_pierce", "swift_draw", "swift_draw"}) {
            old.setLevel(id, old.levelOf(id) + 1);
            old.remember(id);
        }
        old.spend(15);
        // A second profile opened for the bolt archer that never learned it, a guardian without
        // subclass whose trunk was learned first (vitality twice: 2 + 3 points).
        old.unlocked.addAll(List.of("ARCHER", "ARCHER#2", "GUARDIAN"));
        old.planned.put("ARCHER#2", SkillTree.SUB_BOLTER);
        old.classLevels.put("ARCHER#2", new java.util.HashMap<>());
        old.classLevels.put("GUARDIAN", new java.util.HashMap<>(java.util.Map.of("vitality", 2)));
        old.spend(5);
        assertEquals(80, old.points);
        assertEquals(0, old.freeSubclassVersion);

        assertEquals(4, tree.migrateFreeSubclass(old), "level 1 of the swift shot cost 4");
        assertEquals(84, old.points);
        assertEquals(2, old.levelOf("swift_draw"), "the levels stay");
        assertEquals(List.of("archer_focus", "a_pierce", "swift_draw"), old.history, "the paid-back purchase is forgotten");
        assertEquals(SkillTree.SUB_SWIFT, old.plannedSubclass("ARCHER"));
        assertEquals(1, old.levelInProfile("ARCHER#2", "shrapnel"), "planned, never learned: level 1 for free");
        assertEquals(2, old.levelInProfile("GUARDIAN", "vitality"), "nothing learned is lost");
        assertEquals(PlayerSkills.FREE_SUBCLASS_VERSION, old.freeSubclassVersion);
        // Points and paid levels add up: nothing given away, nothing taken.
        assertEquals(old.earned - old.points, tree.pointsSpentProfile(old, "ARCHER") + tree.pointsSpentProfile(old, "ARCHER#2")
                + tree.pointsSpentProfile(old, "GUARDIAN"));
        // Once only.
        assertEquals(0, tree.migrateFreeSubclass(old));
        assertEquals(84, old.points);
        assertEquals(2, old.levelOf("swift_draw"));
        // A lost night takes the paid level 2, never the free level 1.
        assertEquals(List.of("swift_draw", "a_pierce", "archer_focus"), tree.loseLast(old, 3));
        assertEquals(1, old.levelOf("swift_draw"));
        assertEquals(List.of(), tree.loseLast(old, 1));
        // A player of the new rules (free levels given) is never paid back.
        PlayerSkills fresh = new PlayerSkills();
        fresh.playOpened(fresh.addProfile(SkillClass.WARRIOR, SkillTree.SUB_AXE));
        assertEquals(PlayerSkills.FREE_SUBCLASS_VERSION, fresh.freeSubclassVersion);
        assertEquals(0, tree.migrateFreeSubclass(fresh));
        assertEquals(0, fresh.points);
        assertEquals(1, fresh.levelOf("heavy_blow"));
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
        cfg.skillTweaks.clear();   // only this tweak (update 30.10. brings skill values of its own)
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
        // Fixing the subclass gives its node's level 1 (the root of the tree) for free.
        assertEquals(1, p.levelOf("w_combo"));
        assertEquals(100_000, p.points());
        assertTrue(p.history.isEmpty(), "a free level is no purchase");
        // The fixed subclass is the profile's planned subclass: the other way is shut for it.
        assertEquals("path", tree.blockedReason("heavy_blow", p), "the way of might is shut");
        assertEquals("path", tree.blockedReason("sunder", p));
        // Without the learned node the planned subclass still shuts the other way and subclass.
        PlayerSkills planOnly = new PlayerSkills();
        planOnly.chosenClass = SkillClass.WARRIOR.name();
        planOnly.planned.put(SkillClass.WARRIOR.name(), SkillTree.SUB_BLADEMASTER);
        planOnly.award(100);
        assertEquals("subclass_planned", tree.blockedReason("heavy_blow", planOnly));
        assertEquals("subclass_planned", tree.blockedReason("sunder", planOnly));
        assertEquals("subclass_planned", tree.blockedReason("bloodlust", planOnly));
        assertNull(tree.blockedReason("w_combo", planOnly));
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
        assertEquals(4, SkillTree.subclassInfosOf(SkillClass.SUMMONER).size());
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
        assertEquals(SiegeConfig.SETTINGS_VERSION, old.settingsVersion);
        // Both subclasses of the way of the wild can be learned to the end.
        java.util.Map<String, String> subs = java.util.Map.of("s_tamer", SkillTree.SUB_TAMER, "s_golemsmith", SkillTree.SUB_GOLEMSMITH,
                "d_necro", SkillTree.SUB_NECROMANCER, "d_soulcatcher", SkillTree.SUB_SOULCATCHER);
        for (String sub : subs.keySet()) {
            boolean dead = sub.startsWith("d_");
            PlayerSkills p = new PlayerSkills();
            p.chosenClass = SkillClass.SUMMONER.name();
            p.unlockClass(SkillClass.SUMMONER);
            p.award(100_000);
            assertTrue(tree.unlock(sub, p));
            assertTrue(tree.unlock("s_soulbond", p));
            assertTrue(tree.unlock(dead ? "d_raise" : "s_packcall", p));
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
        assertEquals(KeyHint.STAFF_CALL, KeyHint.forNode(tree, "s_packcall"));
        assertEquals(KeyHint.STAFF_ATTACK, KeyHint.forNode(tree, "d_raise"));
        assertEquals(KeyHint.STAFF, KeyHint.forNode(tree, "s_callrange"));
        assertEquals(KeyHint.STAFF_SPELL, KeyHint.forNode(tree, "d_necro"));
        assertEquals(KeyHint.NONE, KeyHint.forNode(tree, "d_cross_wolf"));
        assertEquals(SkillTree.SUB_NECROMANCER, tree.laneOf("d_ghoul"));
        assertEquals(SkillTree.SUB_SOULCATCHER, tree.laneOf("d_swarm"));
        assertTrue(tree.pointsToComplete(new PlayerSkills(), SkillClass.SUMMONER) > 250);
    }

    @Test
    void everySummonerRouteKeepsARingFiveNodeAndAMastery() {
        // Simon 26.10.: the summoner rebuild keeps rings, groups, ids and ults; every choice of
        // both ways still reaches ring V, a mastery and (29 October 2026) the keystone.
        SkillTree tree = new SkillTree();
        String[][][] ways = {
                {{"s_tamer", "s_golemsmith"}, {"s_alpha", "s_packhunt", "s_snowguard", "s_hardgolem"},
                        {"s_howl", "s_wardbond", "l_cross_bones"}, {"s_wildness", "s_care", "l_cross_harvest"},
                        {"s_wild_hunt", "s_titan"}},
                {{"d_necro", "d_soulcatcher"}, {"d_archers", "d_ghoul", "d_swarm", "d_haste"},
                        {"d_rot", "d_corpse", "d_cross_wolf"}, {"d_unholy", "d_pact", "d_cross_loyalty"},
                        {"d_army", "d_soulstorm"}}};
        String[] stem = {"s_packcall", "d_raise"};
        String[][] ringFive = {{"s_packleader", "s_golemwork", "s_mixedpack"}, {"d_legion", "d_soulburn", "d_dance"}};
        String[][] masteries = {{"s_endless_hunt", "s_moonpack", "s_titanfist", "s_orecore"},
                {"d_eternal", "d_champion", "d_soulflood", "d_echo"}};
        String[][] keys = {{"s_beastlord", "s_shepherd"}, {"d_lich", "d_soulkeeper"}};
        int routes = 0;
        for (int w = 0; w < ways.length; w++) {
            routes += walkRoutes(tree, SkillClass.SUMMONER, List.of("s_soulbond", stem[w]), ways[w], ringFive[w],
                    masteries[w], keys[w]);
        }
        assertTrue(routes > 40, "routes " + routes);
    }

    @Test
    void theSoulcatcherLostTheShieldAndTheChainButKeepsItsPoints() {
        // Simon's playtest, 28 October 2026: "the soulcatcher must lose his G ability (the soul
        // shield on G) and the chains". Their talents gave way to Geisterhast and Geisterschar:
        // same rings, same cost, the way costs as many points as before.
        SkillTree tree = new SkillTree();
        assertNull(tree.get("d_soulshield"));
        assertNull(tree.get("d_fetter"));
        SkillNode haste = tree.get("d_haste");
        SkillNode host = tree.get("d_host");
        assertNotNull(haste);
        assertNotNull(host);
        assertEquals(10, haste.costFor(0));
        assertEquals(1, haste.maxLevel());
        assertEquals(15, host.costFor(0));
        assertEquals(1, host.maxLevel());
        assertEquals(SkillTree.SUB_SOULCATCHER, tree.laneOf("d_haste"));
        assertEquals(SkillTree.SUB_SOULCATCHER, tree.laneOf("d_host"));
        for (SkillNode node : tree.ofClass(SkillClass.SUMMONER)) {
            String needs = node.requires() == null ? "" : node.requires();
            assertFalse(needs.contains("d_soulshield") || needs.contains("d_fetter"), node.id() + " needs " + needs);
        }
        // The whole soulcatcher route: Geisterschar and Grauen come one after the other again.
        PlayerSkills p = new PlayerSkills();
        p.chosenClass = SkillClass.SUMMONER.name();
        p.unlockClass(SkillClass.SUMMONER);
        p.award(100_000);
        for (String id : new String[]{"d_soulcatcher", "s_soulbond", "d_raise", "d_soulcatcher", "d_haste", "d_shards",
                "d_soulcatcher", "d_host", "d_soulcatcher", "d_dread"}) {
            assertTrue(tree.unlock(id, p), "unlock " + id);
        }
        assertEquals(1, p.levelOf("d_dread"));
        // An old save: the learned talents move to their successors (levels, history, other profiles), once.
        com.mki.nightsiege.game.SiegeSave save = new com.mki.nightsiege.game.SiegeSave();
        PlayerSkills old = save.skillsOf("a");
        old.chosenClass = SkillClass.SUMMONER.name();
        old.setLevel("d_soulshield", 1);
        old.setLevel("d_fetter", 1);
        old.history.add("d_soulshield");
        old.history.add("d_fetter");
        old.classLevels.put("ARCHER", new java.util.HashMap<>(java.util.Map.of("d_fetter", 1)));
        save.skillsOf("b").setLevel("d_swarm", 1);
        assertEquals(1, save.moveRetiredNodes());
        assertEquals(1, old.levelOf("d_haste"));
        assertEquals(1, old.levelOf("d_host"));
        assertEquals(0, old.levelOf("d_soulshield"));
        assertEquals(0, old.levelOf("d_fetter"));
        assertEquals(List.of("d_haste", "d_host"), old.history);
        assertEquals(1, old.classLevels.get("ARCHER").get("d_host"));
        assertEquals(0, save.moveRetiredNodes(), "nothing left to move");
    }

    @Test
    void theSummonersCompanionsDrawLittleAnger() {
        // Simon 26.10.: monsters turn on a companion only within 3 blocks and for 3 s.
        SiegeConfig fresh = new SiegeConfig();
        assertEquals(3.0, fresh.companionTauntRadius, 1e-9);
        assertEquals(3.0, fresh.companionTauntSeconds, 1e-9);
        SiegeConfig old = new SiegeConfig();
        old.companionTauntRadius = 6.0;
        old.companionSettingsVersion = 0;
        old.sanitize();
        assertEquals(3.0, old.companionTauntRadius, 1e-9, "the old default goes to 3 once");
        assertEquals(1, old.companionSettingsVersion);
        old.companionTauntRadius = 6.0;
        old.sanitize();
        assertEquals(6.0, old.companionTauntRadius, 1e-9, "a server that sets 6 again keeps it");
        // The spells and the call strike take their numbers from the skill values.
        assertEquals(4.0, SkillValues.get("s_tamer", "max", 0), 1e-9);
        assertEquals(2.0, SkillValues.get("d_necro", "raisecost", 0), 1e-9);
        assertEquals(4.0, SkillValues.get("d_soulcatcher", "swarmcost", 0), 1e-9);
        assertEquals(4.0, SkillValues.get("d_army", "max", 0), 1e-9);
        assertEquals(160.0, SkillValues.get("d_army", "duration", 0), 1e-9);
        // The tamer reaches four wolves with every level, not more: 2 + 4 × 0.5.
        SkillTree tree = new SkillTree();
        PlayerSkills tamer = new PlayerSkills();
        tamer.chosenClass = SkillClass.SUMMONER.name();
        tamer.setLevel("s_tamer", 4);
        assertEquals(2.0, tree.totalBonus(SkillEffect.PACK_SLOTS, tamer), 1e-9);
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
        assertEquals(SiegeConfig.SETTINGS_VERSION, old.settingsVersion);
        assertEquals(1.25, old.subclassDamage.get("heavy"), 1e-9);
        assertEquals("minecraft:skeleton", old.effectMobReplacements.get("minecraft:wither_skeleton"));
        assertEquals("minecraft:pillager", old.effectMobReplacements.get("friendsandfoes:iceologer"));
        for (MobEntry e : old.mobs) {
            if (e.id.equals("minecraft:zombie")) {
                assertEquals(20, e.weight);
            } else {
                assertEquals(1, e.weight);
                // the skirmisher's growth halved again on 28 October
                assertEquals(e.id.endsWith("skirmisher") ? 0.025 : 0.05, e.weightPerLevel, 1e-9);
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
    void bossRhythmFourGroupsOfFourThenTwoEveryOtherNight() {
        SiegeConfig c = new SiegeConfig();
        c.sanitize();
        assertEquals(4, c.bossEvery);
        // Simon 29.10.: boss nights 4, 8, 12, 16, the Vierer-Nacht on 20; again 24-36 and 40;
        // from night 41 every 2nd night two bosses.
        int boss = 0;
        for (int night = 1; night <= 50; night++) {
            BossPlan.NightKind kind = BossPlan.nightKind(night, c);
            BossPlan.NightKind want = night == 20 || night == 40 ? BossPlan.NightKind.GROUP
                    : night < 40 && night % 4 == 0 ? BossPlan.NightKind.BOSS
                    : night > 40 && night % 2 == 0 ? BossPlan.NightKind.LATE : BossPlan.NightKind.NORMAL;
            assertEquals(want, kind, "night " + night);
            if (kind == BossPlan.NightKind.BOSS) {
                boss++;
            }
        }
        assertEquals(8, boss, "every one of the eight bosses has its own boss night before night 40");
        assertEquals(8, BossKind.values().length);
        // A lost boss night or Vierer-Nacht comes again; a late night goes on.
        assertEquals(3, BossPlan.countNight(3, BossPlan.NightKind.BOSS, false));
        assertEquals(4, BossPlan.countNight(3, BossPlan.NightKind.BOSS, true));
        assertEquals(19, BossPlan.countNight(19, BossPlan.NightKind.GROUP, false));
        assertEquals(42, BossPlan.countNight(41, BossPlan.NightKind.LATE, false));
        assertEquals(6, BossPlan.countNight(5, BossPlan.NightKind.NORMAL, false));
        c.bossesEnabled = false;
        assertEquals(BossPlan.NightKind.NORMAL, BossPlan.nightKind(4, c));
        c.bossesEnabled = true;
        // The Vierer-Nacht takes the four that came, in their order; fewer are filled up, none twice.
        List<String> came = List.of("frost_jarl", "grave_king", "storm_witch", "ram_titan");
        List<BossKind> four = BossPlan.groupBosses(came, 4, new Random(1));
        assertEquals(List.of(BossKind.FROST_JARL, BossKind.GRAVE_KING, BossKind.STORM_WITCH, BossKind.RAM_TITAN), four);
        List<BossKind> filled = BossPlan.groupBosses(List.of("grave_king"), 4, new Random(2));
        assertEquals(4, filled.size());
        assertEquals(BossKind.GRAVE_KING, filled.get(0));
        assertEquals(4, new HashSet<>(filled).size());
        List<BossKind> late = BossPlan.lateBosses(2, new Random(3));
        assertEquals(2, new HashSet<>(late).size());
        // A server that kept the old 6 goes to 4 once; one that sets 6 again afterwards keeps it.
        SiegeConfig old = new SiegeConfig();
        old.bossEvery = 6;
        old.contentVersion = 0;
        old.sanitize();
        assertEquals(4, old.bossEvery);
        old.bossEvery = 6;
        old.sanitize();
        assertEquals(6, old.bossEvery);
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

    @Test
    void recordsKeepTheBestAndCountTheStreak() {
        NightRecords r = new NightRecords();
        List<NightRecords.Line> one = List.of(
                new NightRecords.Line("a", "Simon", "WARRIOR", "berserker", 300, 20, 0, 80),
                new NightRecords.Line("b", "Quxo", "GUARDIAN", "medic", 50, 3, 120, 200));
        List<NightRecords.Broken> first = r.offer(one, true, 1, "2026-10-29");
        assertTrue(first.stream().anyMatch(b -> b.kind().equals("class") && b.stat().equals("dealt")));
        assertEquals(300, r.best.get("WARRIOR|dealt").value, 1e-9);
        // A weaker night breaks nothing; a stronger one does, and the own best moves along.
        List<NightRecords.Broken> second = r.offer(List.of(
                new NightRecords.Line("a", "Simon", "WARRIOR", "berserker", 200, 25, 0, 50)), true, 2, "2026-10-30");
        assertEquals(300, r.best.get("WARRIOR|dealt").value, 1e-9);
        assertTrue(second.stream().anyMatch(b -> b.stat().equals("kills")));
        assertEquals(2, (int) r.streak.get("a"));
        assertEquals(2, r.serverStreak);
        // A missed night ends the player's streak, a lost one ends everybody's.
        r.offer(List.of(new NightRecords.Line("b", "Quxo", "GUARDIAN", "medic", 10, 1, 10, 10)), true, 3, "x");
        assertEquals(1, (int) r.streak.get("b"));
        r.offer(List.of(new NightRecords.Line("a", "Simon", "WARRIOR", "berserker", 10, 1, 0, 10)), false, 4, "x");
        assertEquals(0, (int) r.streak.get("a"));
        assertEquals(0, r.serverStreak);
        assertEquals(3, r.serverBest);
        NightRecords.View v = r.view("a");
        assertEquals(2, v.myBest);
        assertFalse(v.classes.isEmpty());
    }
}
