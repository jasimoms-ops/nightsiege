package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ruhmeshalle (Simon 30.10., 23:42: "eine größere und detailliertere Stat-Seite im Core",
 * concept claude/night-siege-ruhmeshalle-konzept.md): everything every defender has done in the
 * real nights, the monsters of the server, the curiosities' bests, the badges and the last night
 * for the podium. Kept in the save and left alone by the progress reset, like the records.
 *
 * <p>Test and playtest nights do not count. The nights before the Ruhmeshalle came in once from
 * the statistics logger's files ({@link #imported}).</p>
 */
public class HallOfFame {

    public int version = 1;
    /** True once the logger files of the nights before were read in. */
    public boolean imported;
    /** Every defender by UUID. */
    public Map<String, Hero> heroes = new LinkedHashMap<>();
    /** The monsters of the server by type (as the logger names them). */
    public Map<String, Beast> bestiary = new LinkedHashMap<>();
    /** The server's best of every curiosity. */
    public Map<String, Best> curioBests = new LinkedHashMap<>();
    /** The last real night, for the podium "letzte Nacht". */
    public LastNight lastNight;

    /** One defender. */
    public static class Hero {
        public String name = "";
        public int nights;
        public int held;
        public int lost;
        /** Nights without a death. */
        public int nightsNoDeath;
        /** Held nights in which the defender was the last one standing of a group. */
        public int lastStandingHeld;
        public int kills;
        public int assists;
        public int deaths;
        /** Bosses and wardens defeated (nights and dungeon champions). */
        public int bosses;
        public int towerKills;
        public int dungeons;
        public int dungeonsWon;
        public int camps;
        public int campsDestroyed;
        public int crates;
        public int rareCrates;
        /** Most curse stones on the altar in a held night. */
        public int maxCurses;
        public double dealt;
        public double taken;
        /** Hits caught as the monster dealt them (before armour and blocking). */
        public double intercepted;
        /** Healing given to others. */
        public double healed;
        public double droneDamage;
        public int droneSeconds;
        public int streak;
        public int bestStreak;
        /** Damage by source: nah, fern, faehigkeit, turm, begleiter, drohne. */
        public Map<String, Double> dealtBy = new LinkedHashMap<>();
        /** Per subclass (or class without one). */
        public Map<String, SubLine> subs = new LinkedHashMap<>();
        /** Monsters killed by type (since the Ruhmeshalle). */
        public Map<String, Integer> victims = new LinkedHashMap<>();
        /** Who killed this defender, by type (since the Ruhmeshalle). */
        public Map<String, Integer> killedBy = new LinkedHashMap<>();
        /** Damage taken by monster type. */
        public Map<String, Double> takenFrom = new LinkedHashMap<>();
        /** Curiosities: counts summed up, bests as the best night. */
        public Map<String, Double> curios = new LinkedHashMap<>();
        /** Badge → tier reached (1 bronze, 2 silver, 3 gold). */
        public Map<String, Integer> badges = new LinkedHashMap<>();
        /** The chosen title: a badge in gold, or empty. */
        public String title = "";
        /** The last nights, newest last. */
        public List<NightMini> recent = new ArrayList<>();
    }

    public static class SubLine {
        public String clazz = "";
        public int nights;
        public int held;
        public int kills;
        public int deaths;
        public double dealt;
    }

    public static class NightMini {
        public String date = "";
        public int night;
        public int level;
        public boolean held;
        public double dealt;
        public int kills;
        public int deaths;
        public String sub = "";
    }

    /** One monster type of the server. */
    public static class Beast {
        public int seen;
        public int killed;
        public int killedPlayers;
        public double damageToPlayers;
    }

    public static class Best {
        public String uuid = "";
        public String name = "";
        public double value;
        public String date = "";
    }

    public static class LastNight {
        public String date = "";
        public int night;
        public int level;
        public String result = "";
        public List<Entry> entries = new ArrayList<>();
    }

    /** One defender of the last night. */
    public static class Entry {
        public String uuid = "";
        public String name = "";
        public String clazz = "";
        public String sub = "";
        public double dealt;
        public double healed;
        public double intercepted;
        public int kills;
        public int deaths;
        public int bosses;
        public boolean held;
    }

    /** The curiosities that add up over the nights; all others keep the best night. */
    public static final java.util.Set<String> SUMMED = java.util.Set.of("creeper", "kletterer", "halbesHerz", "geschosse",
            "traenke", "retter", "nemesis", "bossLetzterTreffer", "sturmschritte");
    /** The curiosities in the order of the page. */
    public static final List<String> CURIOS = List.of("weitesterTreffer", "blutrausch", "mehrfach", "creeper", "kletterer",
            "halbesHerz", "ohneTreffer", "bossLetzterTreffer", "geschosse", "traenke", "sturmschritte", "retter", "nemesis");

    public Hero hero(String uuid, String name) {
        Hero h = heroes.computeIfAbsent(uuid, k -> new Hero());
        if (name != null && !name.isBlank()) {
            h.name = name;
        }
        return h;
    }

    /** Adds a curiosity of one night to a hero, and to the server's bests. */
    public void curio(String uuid, Hero h, String key, double value, String date) {
        if (value <= 0) {
            return;
        }
        double now = SUMMED.contains(key) ? h.curios.merge(key, value, Double::sum) : h.curios.merge(key, value, Math::max);
        Best b = curioBests.get(key);
        if (b == null || now > b.value) {
            b = new Best();
            b.uuid = uuid;
            b.name = h.name;
            b.value = now;
            b.date = date == null ? "" : date;
            curioBests.put(key, b);
        }
    }

    /** The subclass a hero played most (or "" before the first night). */
    public static String mainSub(Hero h) {
        String best = "";
        int most = -1;
        for (Map.Entry<String, SubLine> e : h.subs.entrySet()) {
            if (e.getValue().nights > most) {
                most = e.getValue().nights;
                best = e.getKey();
            }
        }
        return best;
    }

    /** The class of a hero's most played subclass. */
    public static String mainClass(Hero h) {
        SubLine s = h.subs.get(mainSub(h));
        return s == null ? "" : s.clazz;
    }
}
