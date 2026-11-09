package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 9.1 h: the overview of "alle öffnen" holds every roll once, grouped by gain, rarest first. */
class CrateSummaryTest {

    private static CrateRoll roll(String clazz, String gain, String rarity, boolean dup, int points, int stage) {
        CrateRoll r = new CrateRoll();
        r.clazz = clazz;
        r.gain = gain;
        r.rarity = rarity;
        r.duplicate = dup;
        r.points = points;
        r.stage = stage;
        r.topStage = 3;
        r.collected = 4;
        r.total = 20;
        return r;
    }

    @Test
    void groupsAndCountsEveryRoll() {
        List<CrateRoll> rolls = new ArrayList<>();
        rolls.add(roll("WARRIOR", "proviant", "COMMON", false, 0, 1));
        rolls.add(roll("WARRIOR", "proviant", "COMMON", false, 0, 2));
        rolls.add(roll("WARRIOR", "proviant", "COMMON", true, 1, 3));
        rolls.add(roll("WARRIOR", "kriegshorn", "EPIC", false, 0, 1));
        rolls.add(roll("ARCHER", "proviant", "COMMON", true, 2, 3));
        rolls.add(null);
        CrateSummary s = CrateSummary.of(rolls);
        assertEquals(5, s.crates);
        assertEquals(3, s.points);
        assertEquals(3, s.fresh);
        assertEquals(2, s.duplicates);
        assertEquals(3, s.entries.size());
        assertEquals("kriegshorn", s.best().gain, "rarest first");
        CrateSummary.Entry prov = s.entries.get(1);
        assertEquals("WARRIOR", prov.clazz);
        assertEquals(3, prov.count);
        assertEquals(2, prov.fresh);
        assertEquals(1, prov.duplicates);
        assertEquals(3, prov.stage, "stage after the last draw");
        assertEquals("ARCHER", s.entries.get(2).clazz, "same gain of another class stays its own line");
        assertEquals(s.crates, s.entries.stream().mapToInt(e -> e.count).sum());
        assertEquals(s.points, s.entries.stream().mapToInt(e -> e.points).sum());
    }

    @Test
    void emptyHasNoBest() {
        CrateSummary s = CrateSummary.of(List.of());
        assertEquals(0, s.crates);
        assertNull(s.best());
    }
}
