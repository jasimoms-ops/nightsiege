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
        /**
         * Teil 2: a "Belagerung starten" round that is running (saved, so a crash finds it and puts everyone back;
         * null when none runs).
         */
        public Round round;
        /**
         * Pruefer 13.11.: a won finale whose chapter change has not run yet (the finale's team, null when none).
         * The night's end saves before the change; a server that went down in between finishes it at the start.
         */
        public List<String> closing;
        public int closingLevel;

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
        /** The save's skill tree version when it was frozen (the load migrations start there when it comes back). */
        public int treeVersion;
    }

    /**
     * A "Belagerung starten" round (Teil 2). The rule (Chef 13.11.): only who stands at most 48 blocks from the core
     * and presses ready takes part (alone too); everyone picks their own character when pressing ready (the current
     * one or one of their veterans, mixed is fine). The starter's pick decides the night: a veteran → night 20 as a
     * normal night at the veteran's level; the current character → a night of its own level.
     */
    public static class Round {
        /** Who pressed ready: player uuid → the veteran played (index into their list), -1 for the current character. */
        public Map<String, Integer> picks = new LinkedHashMap<>();
        public String starter = "";
        /** Curse stones and the night fragment used up by it (each +10 % for veterans), counted when the night starts. */
        public int extras;
        public int curses;
        /** The night fragment used (given back when the round is broken off, as in a real night), or null. */
        public String fragment;
        public int level = 1;
        public String started = "";
        /** True once the night runs (before: the ready round). */
        public boolean night;
    }

    /** The night of the round follows the starter: the veterans' loop (night 20) when they picked a veteran. */
    public static boolean veteranLoop(Round r) {
        return r != null && r.picks.getOrDefault(r.starter, CURRENT) != CURRENT;
    }

    /**
     * Someone presses ready with a character: null when it counts, else why not. A veteran needs the first finale
     * (or the playtest/an operator) and must exist.
     */
    public static String join(Round r, String uuid, int pick, boolean veteransOpen, int veteranCount, boolean hasClass) {
        if (r == null || r.night) {
            return "nightsiege.round.block_busy";
        }
        if (pick != CURRENT) {
            if (!veteransOpen) {
                return "nightsiege.chapter.lock_finale";
            }
            if (pick < 0 || pick >= veteranCount) {
                return "nightsiege.round.no_veteran";
            }
        } else if (!hasClass) {
            return "nightsiege.round.no_class";
        }
        r.picks.put(uuid, pick);
        return null;
    }

    /**
     * Who may play a lent profile now (Pruefer 13.11.), the one place for every round that lends one: the ready
     * rounds of the bastion and the war leader, the night of such a round, and the "Belagerung starten" round (its
     * ready round and its night). sweepLoans takes back every other loan, so a new round with lent profiles goes here.
     */
    public static Set<String> loanHolders(Round siegeRound, Collection<java.util.UUID> bastionRound,
                                          Collection<java.util.UUID> warRound, Collection<java.util.UUID> funNight) {
        Set<String> out = new java.util.HashSet<>();
        for (Collection<java.util.UUID> ids : List.of(nn(bastionRound), nn(warRound), nn(funNight))) {
            for (java.util.UUID id : ids) {
                out.add(id.toString());
            }
        }
        if (siegeRound != null && siegeRound.picks != null) {
            out.addAll(siegeRound.picks.keySet());
        }
        return out;
    }

    private static Collection<java.util.UUID> nn(Collection<java.util.UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    /** Who plays when the night starts: those who pressed ready and still stand near the core (nobody else). */
    public static List<String> roster(Round r, Collection<String> nearCore) {
        List<String> out = new ArrayList<>();
        if (r != null && nearCore != null) {
            for (String uuid : r.picks.keySet()) {
                if (nearCore.contains(uuid)) {
                    out.add(uuid);
                }
            }
        }
        return out;
    }

    /**
     * The night starts (Simon 13.11.: the rules of a real night): the curse stones on the altar and the night fragment
     * set into the core are used by it. Counts them for the veterans' share; returns the fragment taken (or null).
     */
    public static String useExtras(Round r, List<String> altar, String fragment) {
        r.curses = altar == null ? 0 : altar.size();
        r.fragment = fragment;
        r.extras = r.curses + (fragment != null ? 1 : 0);
        r.night = true;
        return fragment;
    }

    /**
     * The round is over: played to the end, the stones on the altar are gone (as after a real night); broken off,
     * they stay and the fragment comes back. Returns the fragment to set into the core again, or null.
     */
    public static String afterExtras(Round r, List<String> altar, boolean playedOut) {
        if (r == null) {
            return null;
        }
        if (playedOut) {
            if (altar != null && r.curses > 0) {
                altar.clear();
            }
            return null;
        }
        return r.fragment;
    }

    /**
     * The experience of the round's night per player, counted the way a real night pays it (WaveXp per wave held,
     * the night's points when it is won, the curse points), on a scratch character: the share and the cap come after.
     */
    public static final class NightXp {
        private final PlayerSkills scratch = new PlayerSkills();

        /** A wave held: {@link WaveXp#survive} as in SiegeManager.waveSurvived. */
        public int wave(int weight, int wavesPerPoint, double multiplier) {
            return WaveXp.survive(scratch, weight, wavesPerPoint, multiplier);
        }

        /** The night was won: its points (as awardSkillPoints and awardCursePoints). */
        public void won(int points) {
            scratch.award(points);
        }

        public int points() {
            return scratch.earned;
        }
    }

    /** Index for "the current character" in {@link Round#picks}. */
    public static final int CURRENT = -1;

    /**
     * Share of a round's experience that goes into the pool (Simon 13.11.): a veteran 50 %, +10 % for every curse or
     * fragment (added), the current character 100 %.
     */
    public static double xpShare(boolean veteran, int extras) {
        return veteran ? 0.5 + 0.1 * Math.max(0, extras) : 1.0;
    }

    /** Adds a round's points (times the share) to the character's pool; returns the whole points added. */
    public static int addToPool(PlayerSkills own, double points, double share) {
        if (own == null || points <= 0 || share <= 0) {
            return 0;
        }
        double gained = points * share + Math.max(0.0, own.xpPoolFraction);
        int whole = (int) Math.floor(gained + 1e-9);
        own.xpPoolFraction = Math.max(0.0, gained - whole);
        own.xpPool = Math.max(0, own.xpPool) + whole;
        return whole;
    }

    /**
     * The pool flows into the character up to {@code cap} earned points (the points of the chapter's world level);
     * the rest waits. Returns the points that flowed.
     */
    public static int flowPool(PlayerSkills own, int cap) {
        if (own == null || own.xpPool <= 0) {
            return 0;
        }
        int room = Math.max(0, cap - Math.max(0, own.earned));
        int move = Math.min(room, own.xpPool);
        if (move > 0) {
            own.award(move);
            own.xpPool -= move;
        }
        return move;
    }

    /**
     * Why the buttons that wait for the first finale (war leader, bastion, the veterans' loop) are grey, or null
     * when open: open from the first won finale, always for the playtest and operators.
     */
    public static String finaleLock(Progress p, boolean playtest, boolean operator) {
        if (playtest || operator || (p != null && p.firstFinaleWon)) {
            return null;
        }
        return "nightsiege.chapter.lock_finale";
    }

    /**
     * After a crash in the middle of a round: the round is gone (no experience, nothing of the world touched), the
     * lent veterans were never saved. Returns the players who were in it (to tell them), and clears it.
     */
    public static List<String> recover(Progress p) {
        if (p == null || p.round == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(p.round.picks.keySet());
        p.round = null;
        return out;
    }

    /** The fragment a crashed round had used (it comes back into the core, as after a broken-off night), or null. */
    public static String recoverFragment(Progress p) {
        return p == null || p.round == null ? null : p.round.fragment;
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
                // Rule 7: the pool belongs to the current character, so it goes along (Pruefer 13.11.); the veteran keeps none.
                fresh.xpPool = Math.max(0, own.xpPool);
                fresh.xpPoolFraction = Math.max(0.0, own.xpPoolFraction);
                own.xpPool = 0;
                own.xpPoolFraction = 0.0;
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
