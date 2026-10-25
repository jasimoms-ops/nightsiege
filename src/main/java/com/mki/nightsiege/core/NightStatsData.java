package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The statistics window after a night (Simon's playtest, 25 October 2026): how the night
 * went and one row per defender. Sent by the server as JSON, shown by NightStatsScreen.
 */
public class NightStatsData {

    public int level;
    /** The event of the night (SiegeEvent name in lower case), "none" for a normal night. */
    public String event = "";
    public boolean test;
    /** gewonnen, ziel_verfehlt, verloren, test_gehalten, test_verloren. */
    public String result = "";
    public int kills;
    public int required;
    public int waves;
    public int seconds;
    /** Index of the night's best defender in {@link #rows}, or -1. */
    public int mvp = -1;
    public List<Row> rows = new ArrayList<>();

    /** One defender's night. */
    public static class Row {
        public String name = "";
        public String clazz = "";
        public String subclass = "";
        public double dealt;
        public double taken;
        public double healed;
        public int kills;
        public int deaths;
    }

    /** True for a night the defenders held. */
    public boolean held() {
        return "gewonnen".equals(result) || "ziel_verfehlt".equals(result) || "test_gehalten".equals(result);
    }

    /**
     * How much a defender did for the night: damage, healing (it counts as much as damage)
     * and kills; a fall costs. The MVP is the one with the most.
     */
    public static double score(Row row) {
        return row.dealt + row.healed + 10.0 * row.kills - 25.0 * row.deaths;
    }

    /** The best defender, or -1 when nobody did anything. */
    public static int mvpOf(List<Row> rows) {
        int best = -1;
        double bestScore = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            double s = score(rows.get(i));
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best;
    }
}
