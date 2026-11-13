package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * Kapitel statt Stufe 21+ (Tor 11, Simon 13.11.): a chapter is 20 nights of the boss rhythm, night 20 the finale
 * with the four bosses of the chapter. Only a won finale closes it: every registered character is frozen as a
 * veteran, the world level goes back to 1 and everyone picks a new character. Pure arithmetic on the save's
 * {@link Progress}, so it can be unit tested.
 */
public final class Chapters {

    /** Nights of a chapter; night 20 is the finale. */
    public static final int LENGTH = 20;
    /** Chapter 1, 3, 5 …: the bosses of the first chapter (Simon 13.11.). */
    public static final List<BossKind> GROUP_ONE = List.of(BossKind.SPIDER_QUEEN, BossKind.RAM_TITAN, BossKind.WARLORD, BossKind.STORM_WITCH);
    /** Chapter 2, 4, 6 …. */
    public static final List<BossKind> GROUP_TWO = List.of(BossKind.FROST_JARL, BossKind.EMBER_COLOSSUS, BossKind.GRAVE_KING, BossKind.BONE_LICH);

    private Chapters() {
    }

    /** The chapter state of a world (in the save). An old save gets the defaults: chapter 1, its nights counted on. */
    public static class Progress {
        /** The chapter now played, from 1. */
        public int chapter = 1;
        /** Nights of the boss rhythm before this chapter (the coming night is rhythm night - nightBase). */
        public int nightBase;
        /** Won nights before this chapter: the world level counts from here (level 1 again). */
        public int wonBase;
        /** Welt-Merker (Teil 2: war leader, bastion and the veterans' loop open from here). */
        public boolean firstFinaleWon;
        public String firstFinaleDate = "";
        /** Player uuid → their veterans, oldest first. */
        public Map<String, List<Veteran>> veterans = new LinkedHashMap<>();

        public List<Veteran> veteransOf(String uuid) {
            if (veterans == null) {
                veterans = new LinkedHashMap<>();
            }
            List<Veteran> list = veterans.get(uuid);
            return list == null ? List.of() : list;
        }
    }

    /** A frozen character: class, subclass, tree, crate gains and level as they were when the chapter closed. */
    public static class Veteran {
        public String clazz = "";
        public String subclass = "";
        public int level = 1;
        public int chapter = 1;
        public String date = "";
        /** Stood in the won finale (the chapter star). */
        public boolean finale;
        /** The character itself (tree, gains, profiles); a copy is lent out to play it ({@link #thaw}). */
        public PlayerSkills skills;
    }

    /** The chapter of a night counted from the first (a world without a won finale in between): 1–20 → 1, 21–40 → 2. */
    public static int chapterOf(int night) {
        return Math.max(0, night - 1) / LENGTH + 1;
    }

    /** The night within its chapter for {@link #chapterOf}: 20 → 20, 21 → 1. */
    public static int nightOf(int night) {
        return Math.max(0, night - 1) % LENGTH + 1;
    }

    /** The night within the chapter of a rhythm night (an old save past night 20 stays in chapter 1: 27, 41 …). */
    public static int nightIn(int rhythmNight, Progress p) {
        return Math.max(1, rhythmNight - (p == null ? 0 : Math.max(0, p.nightBase)));
    }

    /**
     * What a night of the chapter brings: every 20th the finale (GROUP), every bossEvery-th a boss night. The late
     * rhythm from night 40 on is gone for chapters (Simon 13.11.).
     */
    public static BossPlan.NightKind nightKind(int nightInChapter, SiegeConfig cfg) {
        if (!cfg.bossesEnabled || nightInChapter <= 0) {
            return BossPlan.NightKind.NORMAL;
        }
        if (nightInChapter % LENGTH == 0) {
            return BossPlan.NightKind.GROUP;
        }
        if (cfg.bossEvery > 0 && nightInChapter % cfg.bossEvery == 0) {
            return BossPlan.NightKind.BOSS;
        }
        return BossPlan.NightKind.NORMAL;
    }

    /** The four bosses of a chapter: the first group in odd chapters, the second in even ones. */
    public static List<BossKind> bossGroup(int chapter) {
        return Math.max(1, chapter) % 2 == 1 ? GROUP_ONE : GROUP_TWO;
    }

    /** The finale's four: the chapter's group, those that came in its boss nights first (in their order). */
    public static List<BossKind> finaleBosses(int chapter, List<String> came) {
        List<BossKind> group = bossGroup(chapter);
        List<BossKind> out = new ArrayList<>();
        if (came != null) {
            for (String id : came) {
                BossKind kind = BossKind.byId(id);
                if (kind != null && group.contains(kind) && !out.contains(kind)) {
                    out.add(kind);
                }
            }
        }
        for (BossKind kind : group) {
            if (!out.contains(kind)) {
                out.add(kind);
            }
        }
        return out;
    }

    /** The boss of a boss night: from the bag, which only holds the chapter's group (refilled with it when empty). */
    public static BossKind drawBoss(List<String> bag, String last, int chapter, Random random) {
        List<BossKind> group = bossGroup(chapter);
        bag.removeIf(id -> !group.contains(BossKind.byId(id)));
        if (bag.isEmpty()) {
            List<String> fresh = new ArrayList<>();
            for (BossKind kind : group) {
                fresh.add(kind.id());
            }
            Collections.shuffle(fresh, random);
            if (fresh.size() > 1 && fresh.get(0).equals(last)) {
                Collections.swap(fresh, 0, fresh.size() - 1);
            }
            bag.addAll(fresh);
        }
        return BossKind.byId(bag.remove(0));
    }

    /** Life and damage of the normal monsters in this chapter: 1 + chapterScaling (the last entry for later chapters). */
    public static double scaling(int chapter, SiegeConfig cfg) {
        List<Double> steps = cfg == null ? null : cfg.chapterScaling;
        if (steps == null || steps.isEmpty() || chapter <= 0) {
            return 1.0;
        }
        Double step = steps.get(Math.min(chapter, steps.size()) - 1);
        return 1.0 + (step == null || step.isNaN() ? 0.0 : Math.max(0.0, step));
    }

    /** A veteran counts as done (its class locked for the new character) after the finale or at level 20. */
    public static boolean done(Veteran v) {
        return v != null && (v.finale || v.level >= LENGTH);
    }

    /** The classes this player has as done veterans. */
    public static Set<SkillClass> doneClasses(List<Veteran> veterans) {
        Set<SkillClass> out = EnumSet.noneOf(SkillClass.class);
        if (veterans != null) {
            for (Veteran v : veterans) {
                SkillClass c = SkillClass.byName(v == null ? null : v.clazz);
                if (c != null && done(v)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /**
     * The classes a new character may not take: those done as veterans. Who has every class they may choose done
     * picks freely again (Simon 13.11.: "wer alle 5 fertig hat, wählt frei").
     */
    public static Set<SkillClass> lockedClasses(List<Veteran> veterans, Collection<SkillClass> choosable) {
        Set<SkillClass> done = doneClasses(veterans);
        Set<SkillClass> out = EnumSet.noneOf(SkillClass.class);
        boolean all = true;
        for (SkillClass c : choosable) {
            if (done.contains(c)) {
                out.add(c);
            } else {
                all = false;
            }
        }
        return all ? EnumSet.noneOf(SkillClass.class) : out;
    }

    /** The playable classes (no core tree). */
    public static List<SkillClass> playable() {
        List<SkillClass> out = new ArrayList<>();
        for (SkillClass c : SkillClass.values()) {
            if (!c.isShared()) {
                out.add(c);
            }
        }
        return out;
    }

    /** Freezes a character as a veteran (the skills object itself goes into the veteran). */
    public static Veteran freeze(PlayerSkills skills, String subclass, int level, int chapter, String date, boolean finale) {
        Veteran v = new Veteran();
        v.clazz = skills == null ? "" : skills.className();
        v.subclass = subclass == null ? "" : subclass;
        v.level = Math.max(1, level);
        v.chapter = Math.max(1, chapter);
        v.date = date == null ? "" : date;
        v.finale = finale;
        v.skills = skills;
        return v;
    }

    /** A copy of a veteran's character to play it (Teil 2); the veteran itself stays frozen. */
    public static PlayerSkills thaw(Veteran v) {
        if (v == null || v.skills == null) {
            return null;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJson(v.skills), PlayerSkills.class);
    }

    /**
     * The chapter is won: every registered player with a class becomes a veteran (also those who were not there,
     * with the level they have), gets a fresh character at level 1, the world level goes back to 1 and the next
     * chapter starts at night 1. Returns the uuids frozen.
     *
     * @param rhythmNights the boss rhythm's count after the finale (the finale counted)
     * @param siegesWon    won nights after the finale (the finale counted)
     */
    public static List<String> advance(Progress p, int rhythmNights, int siegesWon, Map<String, PlayerSkills> skills,
                                       Collection<String> registered, Collection<String> finaleTeam,
                                       Function<PlayerSkills, String> subOf, ToIntBiFunction<String, PlayerSkills> levelOf,
                                       String date) {
        List<String> frozen = new ArrayList<>();
        if (p.veterans == null) {
            p.veterans = new LinkedHashMap<>();
        }
        if (registered != null && skills != null) {
            for (String uuid : registered) {
                PlayerSkills own = skills.get(uuid);
                if (own == null || !own.hasClass()) {
                    continue;
                }
                boolean there = finaleTeam != null && finaleTeam.contains(uuid);
                Veteran v = freeze(own, subOf == null ? "" : subOf.apply(own), levelOf == null ? 1 : levelOf.applyAsInt(uuid, own),
                        p.chapter, date, there);
                p.veterans.computeIfAbsent(uuid, k -> new ArrayList<>()).add(v);
                PlayerSkills fresh = new PlayerSkills();
                fresh.lastNight = own.lastNight;   // the latecomer count goes on
                fresh.templateGiven = own.templateGiven;
                skills.put(uuid, fresh);
                frozen.add(uuid);
            }
        }
        if (!p.firstFinaleWon) {
            p.firstFinaleWon = true;
            p.firstFinaleDate = date == null ? "" : date;
        }
        p.chapter = Math.max(1, p.chapter) + 1;
        p.nightBase = Math.max(0, rhythmNights);
        p.wonBase = Math.max(0, siegesWon);
        return frozen;
    }

    /** Chapter stars of a player: one for every won finale stood in. */
    public static int stars(List<Veteran> veterans) {
        int n = 0;
        if (veterans != null) {
            for (Veteran v : veterans) {
                if (v != null && v.finale) {
                    n++;
                }
            }
        }
        return n;
    }
}
