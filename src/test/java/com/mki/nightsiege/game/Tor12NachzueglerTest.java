package com.mki.nightsiege.game;

import com.google.gson.Gson;
import com.mki.nightsiege.core.Chapters;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 12 Teil H (Simon 18.11.): the open character and the catch-up bonus ×2. */
class Tor12NachzueglerTest {

    private static SiegeConfig cfg() {
        SiegeConfig c = new SiegeConfig();
        c.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        c.sanitize();
        return c;
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
    void chapterChangeAtLevelTenLeavesTheCharacterOpen() {
        Chapters.Progress p = new Chapters.Progress();
        Map<String, PlayerSkills> skills = new HashMap<>();
        PlayerSkills late = character(SkillClass.GUARDIAN, 120);
        PlayerSkills steady = character(SkillClass.WARRIOR, 500);
        skills.put("late", late);
        skills.put("steady", steady);
        List<String> changed = Chapters.advance(p, 20, 19, skills, List.of("late", "steady"), Set.of(),
                s -> "", (uuid, s) -> uuid.equals("late") ? 10 : 20, "18.11.2026");
        assertEquals(List.of("late", "steady"), changed);           // both start a new character at level 1
        assertFalse(skills.get("late").hasClass());
        assertTrue(p.veteransOf("late").isEmpty());
        Chapters.Veteran open = p.openOf("late").get(0);
        assertEquals("GUARDIAN", open.clazz);
        assertEquals(10, open.level);
        assertTrue(open.skills == late);                             // class, tree, gains, level kept
        assertEquals(2, open.skills.gains.get("GUARDIAN").get("gain_a"));
        assertEquals(1, p.veteransOf("steady").size());              // level 20: a veteran
        assertTrue(p.openOf("steady").isEmpty());
        // A second chapter missed: several open characters
        skills.put("late", character(SkillClass.ARCHER, 30));
        Chapters.advance(p, 40, 25, skills, List.of("late"), Set.of(), s -> "", (u, s) -> 4, "x");
        assertEquals(2, p.openOf("late").size());
    }

    @Test
    void classPickLocksVeteranAndOpenClassesUntilAllAreTaken() {
        Chapters.Veteran vet = Chapters.freeze(character(SkillClass.WARRIOR, 500), "", 20, 1, "", true);
        Chapters.Veteran open = Chapters.freeze(character(SkillClass.ARCHER, 100), "", 10, 1, "", false);
        List<SkillClass> all = Chapters.playable();
        assertEquals(Set.of(SkillClass.WARRIOR, SkillClass.ARCHER), Chapters.lockedClasses(List.of(vet), List.of(open), all));
        List<Chapters.Veteran> opens = new ArrayList<>(List.of(open));
        for (SkillClass c : List.of(SkillClass.GUARDIAN, SkillClass.ENGINEER, SkillClass.SUMMONER)) {
            opens.add(Chapters.freeze(character(c, 10), "", 2, 1, "", false));
        }
        assertTrue(Chapters.lockedClasses(List.of(vet), opens, all).isEmpty());   // every class taken: free pick
    }

    @Test
    void openCharacterAtTwentyBecomesAVeteran() {
        Chapters.Progress p = new Chapters.Progress();
        Chapters.Veteran open = Chapters.freeze(character(SkillClass.GUARDIAN, 100), "", 18, 1, "", false);
        p.open.computeIfAbsent("a", k -> new ArrayList<>()).add(open);
        // all of the experience into the open character (not the pool), times the catch-up factor
        assertEquals(40, Chapters.creditOpen(open, 20, 2.0));
        assertEquals(140, open.skills.earned);
        assertEquals(0, open.skills.xpPool);
        assertFalse(Chapters.promote(p, "a", open));                  // level 18: stays open
        open.level = 20;
        assertTrue(Chapters.promote(p, "a", open));
        assertTrue(p.openOf("a").isEmpty());
        assertEquals(1, p.veteransOf("a").size());
        assertFalse(p.veteransOf("a").get(0).finale);                 // no star: that needs a won finale
        assertEquals(0, Chapters.stars(p.veteransOf("a")));
    }

    @Test
    void roundPicksForOpenCharacters() {
        Chapters.Round r = new Chapters.Round();
        r.starter = "a";
        assertEquals(-2, Chapters.openPick(0));
        assertEquals(1, Chapters.openIndex(-3));
        assertEquals(-1, Chapters.openIndex(Chapters.CURRENT));
        assertEquals(-1, Chapters.openIndex(0));
        assertEquals("nightsiege.round.no_open", Chapters.join(r, "a", Chapters.openPick(1), false, 0, 1, false));
        assertNull(Chapters.join(r, "a", Chapters.openPick(0), false, 0, 1, false));   // no finale, no class needed
        assertFalse(Chapters.veteranLoop(r));                         // an open starter: a night of its own level
        r.picks.put("a", 0);
        assertTrue(Chapters.veteranLoop(r));
    }

    @Test
    void catchUpBonusFromThreeLevelsUnderTheGoal() {
        SiegeConfig c = cfg();
        assertEquals(2.0, c.catchUpXpFactor, 1e-9);
        assertEquals(3, c.catchUpLevelGap);
        assertEquals(2.0, Chapters.catchUpFactor(7, 10, c), 1e-9);   // 3 under the world level: ×2
        assertEquals(1.0, Chapters.catchUpFactor(8, 10, c), 1e-9);   // 2 under: none
        assertEquals(1.0, Chapters.catchUpFactor(10, 10, c), 1e-9);
        assertEquals(2.0, Chapters.catchUpFactor(17, Chapters.LENGTH, c), 1e-9);   // open character: goal 20
        assertEquals(1.0, Chapters.catchUpFactor(18, Chapters.LENGTH, c), 1e-9);
        assertEquals("2", Chapters.factorText(2.0));
        assertEquals("1.5", Chapters.factorText(1.5));
        c.catchUpLevelGap = 0;
        assertEquals(1.0, Chapters.catchUpFactor(1, 20, c), 1e-9);   // 0 = off
    }

    @Test
    void migrationV32AddsTheBonusAndReopensUnfinishedVeterans() {
        SiegeConfig old = new Gson().fromJson("{\"settingsVersion\":31,\"catchUpMax\":4}", SiegeConfig.class);
        old.catchUpXpFactor = 0;   // as if missing
        old.catchUpLevelGap = 0;
        assertTrue(old.settingsVersion < 32);
        old.sanitize();
        assertTrue(old.settingsVersion >= 32);
        assertEquals(2.0, old.catchUpXpFactor, 1e-9);
        assertEquals(3, old.catchUpLevelGap);
        // The save: veterans under 20 (not of a finale) become open; done ones stay; a second load finds none.
        String json = "{\"chapter\":2,\"veterans\":{\"a\":[{\"clazz\":\"WARRIOR\",\"level\":20,\"finale\":true},"
                + "{\"clazz\":\"ARCHER\",\"level\":9}],\"b\":[{\"clazz\":\"GUARDIAN\",\"level\":12}]}}";
        Chapters.Progress p = new Gson().fromJson(json, Chapters.Progress.class);
        assertEquals(2, Chapters.reopen(p));
        assertEquals(1, p.veteransOf("a").size());
        assertEquals("ARCHER", p.openOf("a").get(0).clazz);
        assertEquals(9, p.openOf("a").get(0).level);
        assertTrue(p.veteransOf("b").isEmpty());
        assertEquals("GUARDIAN", p.openOf("b").get(0).clazz);
        assertEquals(0, Chapters.reopen(p));
        Chapters.Progress older = new Gson().fromJson("{\"chapter\":1}", Chapters.Progress.class);
        assertTrue(older.openOf("x").isEmpty());                      // a save without open characters loads
    }
}
