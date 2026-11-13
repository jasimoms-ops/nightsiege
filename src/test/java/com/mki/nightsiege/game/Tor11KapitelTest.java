package com.mki.nightsiege.game;

import com.google.gson.Gson;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.BossPlan;
import com.mki.nightsiege.core.Chapters;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 11, Teil 1: chapters instead of level 21+ (Simon 13.11.). */
class Tor11KapitelTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
    }

    @Test
    void chaptersCountTwentyNights() {
        assertEquals(1, Chapters.chapterOf(20));
        assertEquals(20, Chapters.nightOf(20));
        assertEquals(2, Chapters.chapterOf(21));
        assertEquals(1, Chapters.nightOf(21));
        assertEquals(2, Chapters.chapterOf(40));
        assertEquals(20, Chapters.nightOf(40));
        assertEquals(3, Chapters.chapterOf(41));
        assertEquals(1, Chapters.nightOf(41));
        assertEquals(1, Chapters.chapterOf(1));
    }

    @Test
    void bossNightsFourEightTwelveSixteenAndTheFinale() {
        SiegeConfig c = cfg();
        for (int n = 1; n <= 20; n++) {
            BossPlan.NightKind want = n == 20 ? BossPlan.NightKind.GROUP : n % 4 == 0 ? BossPlan.NightKind.BOSS : BossPlan.NightKind.NORMAL;
            assertEquals(want, Chapters.nightKind(n, c), "night " + n);
        }
        // An old save past night 40 stays in chapter 1: no late rhythm, a boss every 4th, the finale every 20th.
        assertEquals(BossPlan.NightKind.NORMAL, Chapters.nightKind(42, c));
        assertEquals(BossPlan.NightKind.BOSS, Chapters.nightKind(44, c));
        assertEquals(BossPlan.NightKind.GROUP, Chapters.nightKind(60, c));
        // The chapter 1 nights are the same as the old rhythm up to 40.
        for (int n = 1; n <= 40; n++) {
            assertEquals(BossPlan.nightKind(n, c), Chapters.nightKind(n, c), "night " + n);
        }
    }

    @Test
    void bossGroupsByChapter() {
        assertEquals(List.of(BossKind.SPIDER_QUEEN, BossKind.RAM_TITAN, BossKind.WARLORD, BossKind.STORM_WITCH), Chapters.bossGroup(1));
        assertEquals(List.of(BossKind.FROST_JARL, BossKind.EMBER_COLOSSUS, BossKind.GRAVE_KING, BossKind.BONE_LICH), Chapters.bossGroup(2));
        assertEquals(Chapters.bossGroup(1), Chapters.bossGroup(3));
        assertEquals(Chapters.bossGroup(2), Chapters.bossGroup(4));
        // A boss night draws only from the chapter's four; a bag of the old eight is cleaned.
        List<String> bag = new ArrayList<>(List.of("frost_jarl", "grave_king"));
        Random r = new Random(7);
        for (int i = 0; i < 12; i++) {
            assertTrue(Chapters.bossGroup(1).contains(Chapters.drawBoss(bag, null, 1, r)));
        }
        // The finale: the chapter's four, those that came first.
        assertEquals(List.of(BossKind.WARLORD, BossKind.SPIDER_QUEEN, BossKind.RAM_TITAN, BossKind.STORM_WITCH),
                Chapters.finaleBosses(1, List.of("warlord", "frost_jarl")));
        assertEquals(4, Chapters.finaleBosses(2, List.of()).size());
    }

    @Test
    void chapterScalingOnlyFromChapterThree() {
        SiegeConfig c = cfg();
        assertEquals(1.0, Chapters.scaling(1, c), 1e-9);
        assertEquals(1.0, Chapters.scaling(2, c), 1e-9);
        assertEquals(1.10, Chapters.scaling(3, c), 1e-9);
        assertEquals(1.20, Chapters.scaling(4, c), 1e-9);
        assertEquals(1.30, Chapters.scaling(5, c), 1e-9);
        assertEquals(1.30, Chapters.scaling(9, c), 1e-9);
    }

    @Test
    void configMigrationKeepsValuesAndAddsTheSteps() {
        SiegeConfig c = new Gson().fromJson("{\"settingsVersion\":28,\"bossEvery\":4,\"healthPerLevel\":0.123}", SiegeConfig.class);
        c.sanitize();
        assertEquals(SiegeConfig.SETTINGS_VERSION, c.settingsVersion);
        assertEquals(30, SiegeConfig.SETTINGS_VERSION);
        assertEquals(List.of(0.0, 0.0, 0.10, 0.20, 0.30), c.chapterScaling);
        assertEquals(0.123, c.healthPerLevel, 1e-9);
        SiegeConfig own = new Gson().fromJson("{\"settingsVersion\":28,\"chapterScaling\":[0,0,0.05]}", SiegeConfig.class);
        own.sanitize();
        assertEquals(List.of(0.0, 0.0, 0.05), own.chapterScaling);   // an own value stays
    }

    @Test
    void oldSaveIsChapterOneWithItsNightsAndLevel() {
        SiegeSave save = new Gson().fromJson("{\"siegesWon\":26,\"bossNights\":27,\"season\":2}", SiegeSave.class);
        Chapters.Progress p = save.chapters();
        assertEquals(1, p.chapter);
        assertEquals(28, Chapters.nightIn(save.bossNights + 1, p));   // the night count stays
        assertEquals(26, save.siegesWon - p.wonBase);                 // no step back in level
        assertFalse(p.firstFinaleWon);
        assertEquals(SiegeMath.level(26, 0, cfg()), SiegeMath.level(save.siegesWon - p.wonBase, 0, cfg()));
    }

    private static PlayerSkills character(SkillClass c, int earned) {
        PlayerSkills s = new PlayerSkills();
        s.chosenClass = c.name();
        s.unlocked.add(c.name());
        s.earned = earned;
        s.levels.put("w_test", 3);
        s.gains.put(c.name(), new HashMap<>(Map.of("gain_a", 2)));
        return s;
    }

    @Test
    void finaleWonFreezesEveryRegisteredCharacterAndStartsChapterTwo() {
        Chapters.Progress p = new Chapters.Progress();
        Map<String, PlayerSkills> skills = new HashMap<>();
        PlayerSkills a = character(SkillClass.WARRIOR, 400);
        PlayerSkills b = character(SkillClass.ARCHER, 90);
        b.lastNight = 17;
        skills.put("a", a);
        skills.put("b", b);
        skills.put("c", new PlayerSkills());   // registered, no class yet
        List<String> frozen = Chapters.advance(p, 20, 19, skills, List.of("a", "b", "c"), Set.of("a"),
                s -> "berserker", (uuid, s) -> uuid.equals("a") ? 20 : 9, "13.11.2026");
        assertEquals(List.of("a", "b"), frozen);
        assertEquals(2, p.chapter);
        assertEquals(1, Chapters.nightIn(20 + 1, p));        // the next night is "Nacht 1"
        assertEquals(0, 19 - p.wonBase);                      // world level 1 again
        assertTrue(p.firstFinaleWon);
        assertEquals("13.11.2026", p.firstFinaleDate);
        // New characters at level 1; the old ones as veterans, untouched.
        assertFalse(skills.get("a").hasClass());
        assertEquals(0, skills.get("a").earned);
        assertEquals(17, skills.get("b").lastNight);
        Chapters.Veteran va = p.veteransOf("a").get(0);
        assertEquals("WARRIOR", va.clazz);
        assertEquals("berserker", va.subclass);
        assertEquals(20, va.level);
        assertEquals(1, va.chapter);
        assertTrue(va.finale);
        assertTrue(Chapters.done(va));
        assertTrue(va.skills == a);
        Chapters.Veteran vb = p.veteransOf("b").get(0);
        assertEquals(9, vb.level);
        assertFalse(vb.finale);
        assertFalse(Chapters.done(vb));                       // not there, level 9: not done
        assertEquals(1, Chapters.stars(p.veteransOf("a")));
        assertEquals(0, Chapters.stars(p.veteransOf("b")));
        assertTrue(p.veteransOf("c").isEmpty());
    }

    @Test
    void veteranComesBackAsACopy() {
        Chapters.Progress p = new Chapters.Progress();
        Map<String, PlayerSkills> skills = new HashMap<>(Map.of("a", character(SkillClass.GUARDIAN, 300)));
        Chapters.advance(p, 20, 19, skills, List.of("a"), Set.of("a"), s -> "", (u, s) -> 20, "x");
        Chapters.Veteran v = p.veteransOf("a").get(0);
        // Through the save (as it is written to disk) and back.
        Chapters.Progress loaded = new Gson().fromJson(new Gson().toJson(p), Chapters.Progress.class);
        Chapters.Veteran lv = loaded.veteransOf("a").get(0);
        PlayerSkills back = Chapters.thaw(lv);
        assertNotSame(lv.skills, back);
        assertEquals("GUARDIAN", back.chosenClass);
        assertEquals(3, back.levels.get("w_test"));
        assertEquals(2, back.gains.get("GUARDIAN").get("gain_a"));
        assertEquals(300, back.earned);
        back.levels.put("w_test", 0);                          // playing the copy leaves the veteran alone
        assertEquals(3, lv.skills.levels.get("w_test"));
        assertEquals(v.level, lv.level);
    }

    @Test
    void classLockUntilEveryClassIsDone() {
        List<SkillClass> all = Chapters.playable();
        assertEquals(5, all.size());
        List<Chapters.Veteran> vets = new ArrayList<>();
        vets.add(Chapters.freeze(character(SkillClass.WARRIOR, 0), "", 20, 1, "", true));
        vets.add(Chapters.freeze(character(SkillClass.ARCHER, 0), "", 12, 1, "", false));   // not done
        assertEquals(Set.of(SkillClass.WARRIOR), Chapters.lockedClasses(vets, all));
        for (SkillClass c : List.of(SkillClass.ARCHER, SkillClass.GUARDIAN, SkillClass.ENGINEER)) {
            vets.add(Chapters.freeze(character(c, 0), "", 20, 2, "", false));   // level 20 counts as done
        }
        assertEquals(Set.of(SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN, SkillClass.ENGINEER),
                Chapters.lockedClasses(vets, all));
        // Without the summoner (admins only) every class this player may choose is done: free again.
        assertTrue(Chapters.lockedClasses(vets, List.of(SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN, SkillClass.ENGINEER)).isEmpty());
        vets.add(Chapters.freeze(character(SkillClass.SUMMONER, 0), "", 20, 3, "", true));
        assertTrue(Chapters.lockedClasses(vets, all).isEmpty());   // all 5 done: free
    }

    // ------------------------------------------------------------------ Teil 2

    @Test
    void poolSharesForVeteransCursesAndTheCurrentCharacter() {
        assertEquals(1.0, Chapters.xpShare(false, 3), 1e-9);    // the current character: 100 %, curses or not
        assertEquals(0.5, Chapters.xpShare(true, 0), 1e-9);     // a veteran: 50 %
        assertEquals(0.7, Chapters.xpShare(true, 2), 1e-9);     // +10 % per curse or fragment, added
        PlayerSkills own = new PlayerSkills();
        assertEquals(5, Chapters.addToPool(own, 10, Chapters.xpShare(true, 0)));
        assertEquals(3, Chapters.addToPool(own, 5, Chapters.xpShare(true, 1)));   // 3.0
        assertEquals(8, own.xpPool);
        assertEquals(0, Chapters.addToPool(own, 1.5, 0.5));    // 0.75 waits as a fraction
        assertEquals(1, Chapters.addToPool(own, 0.5, 0.5));    // 0.75 + 0.25
        assertEquals(9, own.xpPool);
        assertEquals(0, own.earned);                           // nothing flows by itself
    }

    @Test
    void poolFlowsUpToTheWorldLevelAndTheRestWaits() {
        PlayerSkills own = new PlayerSkills();
        own.award(40);
        own.xpPool = 30;
        assertEquals(10, Chapters.flowPool(own, 50));          // cap 50: 10 flow
        assertEquals(50, own.earned);
        assertEquals(20, own.xpPool);                          // the rest waits
        assertEquals(0, Chapters.flowPool(own, 50));           // same level: nothing more
        assertEquals(20, Chapters.flowPool(own, 75));          // the world level rose: the rest flows
        assertEquals(70, own.earned);
        assertEquals(0, own.xpPool);
    }

    @Test
    void finaleLockOpensWithTheFirstFinaleForPlaytestAndOperators() {
        Chapters.Progress p = new Chapters.Progress();
        assertEquals("nightsiege.chapter.lock_finale", Chapters.finaleLock(p, false, false));
        assertEquals(null, Chapters.finaleLock(p, true, false));    // the playtest
        assertEquals(null, Chapters.finaleLock(p, false, true));    // an operator
        p.firstFinaleWon = true;
        assertEquals(null, Chapters.finaleLock(p, false, false));
    }

    @Test
    void crashInARoundLeavesNothingBehind() {
        Chapters.Progress p = new Chapters.Progress();
        Map<String, PlayerSkills> skills = new HashMap<>(Map.of("a", character(SkillClass.WARRIOR, 300)));
        Chapters.advance(p, 20, 19, skills, List.of("a"), Set.of("a"), s -> "", (u, s) -> 20, "x");
        p.round = new Chapters.Round();
        p.round.picks.put("a", 0);
        p.round.extras = 2;
        // The save as written mid-round, read after the crash.
        Chapters.Progress loaded = new Gson().fromJson(new Gson().toJson(p), Chapters.Progress.class);
        assertEquals(List.of("a"), Chapters.recover(loaded));
        assertEquals(null, loaded.round);
        assertTrue(Chapters.recover(loaded).isEmpty());
        // The veteran is untouched (the lent copy was never saved), the new character got nothing.
        assertEquals(300, loaded.veteransOf("a").get(0).skills.earned);
        assertEquals(0, skills.get("a").xpPool);
    }
}
