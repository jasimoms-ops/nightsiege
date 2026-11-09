package com.mki.nightsiege.game;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tor 7.3: playtest nights get their own file name and field; real nights and test nights keep theirs. */
class StatsSpieltestTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 9, 12, 30, 5);

    @Test
    void fileNames() {
        assertEquals("night_2026-11-09_12-30-05.json", StatsRecorder.nightFileName(false, false, T));
        assertEquals("testnacht_2026-11-09_12-30-05.json", StatsRecorder.nightFileName(true, false, T));
        assertEquals("spieltest_2026-11-09_12-30-05.json", StatsRecorder.nightFileName(true, true, T));
    }

    @Test
    void fieldsInTheFile() {
        StatsRecorder.NightFile n = new StatsRecorder.NightFile();
        n.test = true;
        n.spieltest = true;
        StatsRecorder.Spieler s = new StatsRecorder.Spieler();
        s.name = "Spieler1";
        s.unterklasse = "platzhalter";
        s.knoten.put("knoten_a", 2);
        n.spieler.put(s.name, s);
        String json = Json.toJson(n);
        assertTrue(json.contains("\"spieltest\": true") || json.contains("\"spieltest\":true"), json);
        assertTrue(json.contains("\"ersterTodSekunde\""), json);
        assertTrue(json.contains("\"knoten_a\""), json);
        StatsRecorder.AttemptFile a = new StatsRecorder.AttemptFile();
        a.spieltest = true;
        String aj = Json.toJson(a);
        assertTrue(aj.contains("\"spieltest\": true") || aj.contains("\"spieltest\":true"), aj);
        assertTrue(!Json.toJson(new StatsRecorder.NightFile()).contains("\"spieltest\": true"));
    }
}
