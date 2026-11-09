package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Several crates opened with one click (Tor 9.1 h, Simon 09.11.): what came out, one line per gain and
 * class, as the overview window shows it. Built on the server from the very rolls of the crates; travels
 * as JSON.
 */
public class CrateSummary {

    /** One gain of the overview: how often it came, how often as new stage, how many points the rest gave. */
    public static class Entry {
        public String clazz = "";
        public String gain = "";
        public String rarity = "";
        public String icon = "";
        public int count;
        /** Draws that raised the stage (the rest were duplicates). */
        public int fresh;
        public int duplicates;
        public int points;
        /** Stage after the last draw, and the top. */
        public int stage;
        public int topStage;
        public boolean twoPulls;
    }

    public int crates;
    public int points;
    public int fresh;
    public int duplicates;
    public int lucky;
    /** Gains the kit of the (last) class holds now, and all there are. */
    public int collected;
    public int total;
    public List<Entry> entries = new ArrayList<>();

    /** The overview of these rolls: grouped by class and gain, rarest first, then in the order drawn. */
    public static CrateSummary of(List<CrateRoll> rolls) {
        CrateSummary s = new CrateSummary();
        Map<String, Entry> byGain = new LinkedHashMap<>();
        for (CrateRoll r : rolls) {
            if (r == null || r.gain == null || r.gain.isEmpty()) {
                continue;
            }
            s.crates++;
            s.points += r.points;
            s.lucky += r.lucky ? 1 : 0;
            s.collected = r.collected;
            s.total = r.total;
            Entry e = byGain.computeIfAbsent(r.clazz + "|" + r.gain, k -> new Entry());
            e.clazz = r.clazz;
            e.gain = r.gain;
            e.rarity = r.rarity;
            e.icon = r.icon;
            e.count++;
            e.points += r.points;
            e.stage = r.stage;
            e.topStage = r.topStage;
            e.twoPulls = r.twoPulls;
            if (r.duplicate) {
                e.duplicates++;
                s.duplicates++;
            } else {
                e.fresh++;
                s.fresh++;
            }
        }
        s.entries.addAll(byGain.values());
        s.entries.sort((a, b) -> Integer.compare(CrateRarity.byName(b.rarity).ordinal(), CrateRarity.byName(a.rarity).ordinal()));
        return s;
    }

    /** The rarest gain of the overview (for the light above the player), or null. */
    public Entry best() {
        return entries.isEmpty() ? null : entries.get(0);
    }
}
