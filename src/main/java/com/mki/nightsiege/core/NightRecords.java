package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The records of the statistics window (content update, Simon 28/29 October 2026): the best night
 * of each class and subclass in damage, kills, healing and damage taken, every player's own bests
 * and the longest win streaks. Kept in the save outside the players' progress, so a progress reset
 * leaves them standing (a hall of fame). Test and playtest nights do not count.
 */
public class NightRecords {

    /** The four columns, in the order the window shows them. */
    public static final String[] STATS = {"dealt", "kills", "healed", "taken"};

    /** One record: who, with what, how much, in which night. */
    public static class Entry {
        public String player = "";
        public String uuid = "";
        public String clazz = "";
        public String subclass = "";
        public double value;
        public int night;
        public String date = "";
    }

    /** "WARRIOR|dealt" (class), "WARRIOR|berserker|dealt" (subclass). */
    public Map<String, Entry> best = new LinkedHashMap<>();
    /** Per player (uuid): "WARRIOR|dealt" and the like. */
    public Map<String, Map<String, Entry>> own = new LinkedHashMap<>();
    /** Per player: the running streak of nights held in a row with them there, and their best. */
    public Map<String, Integer> streak = new LinkedHashMap<>();
    public Map<String, Integer> bestStreak = new LinkedHashMap<>();
    /** Per player: the number of the last real night they defended (to see whether they missed one). */
    public Map<String, Integer> lastNight = new LinkedHashMap<>();
    public Map<String, String> names = new LinkedHashMap<>();
    /** The server's run of nights held in a row, and its best. */
    public int serverStreak;
    public int serverBest;

    /** One defender's night, as the logger counted it. */
    public record Line(String uuid, String name, String clazz, String subclass, double dealt, int kills,
                       double healed, double taken) {
        double value(String stat) {
            return switch (stat) {
                case "dealt" -> dealt;
                case "kills" -> kills;
                case "healed" -> healed;
                default -> taken;
            };
        }
    }

    /** A record broken tonight, for the message after the night. */
    public record Broken(String uuid, String kind, String clazz, String subclass, String stat, double value) {
    }

    /**
     * Takes in a real night. Returns the records broken, the server's first (class, then subclass,
     * then the players' own and their streaks).
     */
    public List<Broken> offer(List<Line> lines, boolean held, int night, String date) {
        List<Broken> out = new ArrayList<>();
        for (Line line : lines) {
            if (line.clazz() == null || line.clazz().isBlank()) {
                continue;
            }
            names.put(line.uuid(), line.name());
            for (String stat : STATS) {
                double v = line.value(stat);
                if (v <= 0) {
                    continue;
                }
                if (beats(best, line.clazz() + "|" + stat, v)) {
                    best.put(line.clazz() + "|" + stat, entry(line, v, night, date));
                    out.add(new Broken(line.uuid(), "class", line.clazz(), line.subclass(), stat, v));
                }
                if (line.subclass() != null && !line.subclass().isBlank()) {
                    String key = line.clazz() + "|" + line.subclass() + "|" + stat;
                    if (beats(best, key, v)) {
                        best.put(key, entry(line, v, night, date));
                        out.add(new Broken(line.uuid(), "subclass", line.clazz(), line.subclass(), stat, v));
                    }
                }
                Map<String, Entry> mine = own.computeIfAbsent(line.uuid(), k -> new LinkedHashMap<>());
                String key = line.clazz() + "|" + stat;
                boolean first = !mine.containsKey(key);
                if (beats(mine, key, v)) {
                    mine.put(key, entry(line, v, night, date));
                    if (!first) {
                        out.add(new Broken(line.uuid(), "own", line.clazz(), line.subclass(), stat, v));
                    }
                }
            }
            // The streak: held nights in a row with this player there; a missed or a lost night ends it.
            int last = lastNight.getOrDefault(line.uuid(), Integer.MIN_VALUE);
            int run = held ? (last == night - 1 ? streak.getOrDefault(line.uuid(), 0) + 1 : 1) : 0;
            streak.put(line.uuid(), run);
            lastNight.put(line.uuid(), night);
            if (run > bestStreak.getOrDefault(line.uuid(), 0)) {
                bestStreak.put(line.uuid(), run);
                if (run >= 2) {
                    out.add(new Broken(line.uuid(), "streak", line.clazz(), line.subclass(), "streak", run));
                }
            }
        }
        serverStreak = held ? serverStreak + 1 : 0;
        serverBest = Math.max(serverBest, serverStreak);
        out.sort((a, b) -> Integer.compare(rank(a.kind()), rank(b.kind())));
        return out;
    }

    private static int rank(String kind) {
        return switch (kind) {
            case "class" -> 0;
            case "subclass" -> 1;
            case "streak" -> 2;
            default -> 3;
        };
    }

    private static boolean beats(Map<String, Entry> map, String key, double v) {
        Entry e = map.get(key);
        return e == null || v > e.value + 1e-9;
    }

    private static Entry entry(Line line, double v, int night, String date) {
        Entry e = new Entry();
        e.player = line.name();
        e.uuid = line.uuid();
        e.clazz = line.clazz();
        e.subclass = line.subclass() == null ? "" : line.subclass();
        e.value = v;
        e.night = night;
        e.date = date;
        return e;
    }

    /** The player with the longest streak, or null. */
    public String streakHolder() {
        String who = null;
        int bestRun = 0;
        for (Map.Entry<String, Integer> e : bestStreak.entrySet()) {
            if (e.getValue() > bestRun) {
                bestRun = e.getValue();
                who = e.getKey();
            }
        }
        return who;
    }

    /** What the window shows of the records, for one viewer. */
    public static class View {
        public List<Entry> classes = new ArrayList<>();
        public List<Entry> subclasses = new ArrayList<>();
        public List<Entry> mine = new ArrayList<>();
        /** Which stat each entry above stands for (same order). */
        public List<String> classStats = new ArrayList<>();
        public List<String> subStats = new ArrayList<>();
        public List<String> mineStats = new ArrayList<>();
        public int myStreak;
        public int myBest;
        public String streakName = "";
        public int streakBest;
        public int serverStreak;
        public int serverBest;
    }

    public View view(String viewerUuid) {
        View v = new View();
        for (Map.Entry<String, Entry> e : best.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length == 2) {
                v.classes.add(e.getValue());
                v.classStats.add(parts[1]);
            } else if (parts.length == 3) {
                v.subclasses.add(e.getValue());
                v.subStats.add(parts[2]);
            }
        }
        Map<String, Entry> mine = viewerUuid == null ? null : own.get(viewerUuid);
        if (mine != null) {
            for (Map.Entry<String, Entry> e : mine.entrySet()) {
                v.mine.add(e.getValue());
                v.mineStats.add(e.getKey().split("\\|")[1]);
            }
        }
        if (viewerUuid != null) {
            v.myStreak = streak.getOrDefault(viewerUuid, 0);
            v.myBest = bestStreak.getOrDefault(viewerUuid, 0);
        }
        String holder = streakHolder();
        if (holder != null) {
            v.streakName = names.getOrDefault(holder, "?");
            v.streakBest = bestStreak.getOrDefault(holder, 0);
        }
        v.serverStreak = serverStreak;
        v.serverBest = serverBest;
        return v;
    }
}
