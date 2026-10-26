package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The night's protocol for balancing. After every siege one line goes to
 * config/nightsiege-stats.csv (the night) and one line per defender to
 * config/nightsiege-stats-spieler.csv. Semicolons, so a German spreadsheet
 * opens it straight away.
 */
public final class NightLog {

    public static final String NIGHT_FILE = "nightsiege-stats.csv";
    public static final String PLAYER_FILE = "nightsiege-stats-spieler.csv";
    private static final String NIGHT_HEADER = "datum;nacht;stufe;test;ereignis;besondere_nacht;spieler;klassen;wellen;"
            + "monster_gespawnt;kills_gesamt;kills_spieler;kill_ziel;ziel_erreicht;tode;ergebnis;dauer_s;"
            + "naechster_monster_abstand;bloecke_gegraben;bloecke_explosion";
    private static final String PLAYER_HEADER = "datum;nacht;stufe;test;spieler;klasse;unterklasse;schaden_ausgeteilt;"
            + "schaden_eingesteckt;geheilt;kills;tode;ergebnis;schaden_begleiter";

    /** One defender's night. */
    public static final class Line {
        public String name = "";
        public String clazz = "";
        public String subclass = "";
        public double dealt;
        /** Of {@link #dealt}: what the summoner's companions and spells dealt (Simon 26.10.). */
        public double dealtCompanions;
        public double taken;
        public double healed;
        public int kills;
        public int deaths;
    }

    private final Map<UUID, Line> players = new LinkedHashMap<>();
    private long startedAt;
    public int playerKills;
    public int dug;
    public double closest = Double.MAX_VALUE;

    public void begin(long gameTime) {
        players.clear();
        startedAt = gameTime;
        playerKills = 0;
        dug = 0;
        closest = Double.MAX_VALUE;
    }

    public Line of(UUID id, String name) {
        Line line = players.computeIfAbsent(id, key -> new Line());
        if (name != null && !name.isBlank()) {
            line.name = name;
        }
        return line;
    }

    public Line peek(UUID id) {
        return players.get(id);
    }

    public void near(double distance) {
        closest = Math.min(closest, distance);
    }

    /** Everything about the night itself; the defenders' lines come from {@link #of}. */
    public record Night(int number, int level, boolean test, String event, boolean special, int waves, int spawned,
                        int kills, int required, int deaths, String result, long endTime, int blasted) {
    }

    public void write(Night night) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        List<String> classes = new ArrayList<>();
        for (Line line : players.values()) {
            classes.add(line.name + ":" + (line.subclass.isBlank() ? line.clazz : line.subclass));
        }
        String nightLine = String.join(";", date, String.valueOf(night.number()), String.valueOf(night.level()),
                night.test() ? "ja" : "nein", night.event(), night.special() ? "ja" : "nein",
                String.valueOf(players.size()), String.join(" ", classes), String.valueOf(night.waves()),
                String.valueOf(night.spawned()), String.valueOf(night.kills()), String.valueOf(playerKills),
                String.valueOf(night.required()), night.kills() >= night.required() ? "ja" : "nein",
                String.valueOf(night.deaths()), night.result(),
                String.valueOf(Math.max(0, (night.endTime() - startedAt) / 20)),
                closest == Double.MAX_VALUE ? "" : num(closest), String.valueOf(dug), String.valueOf(night.blasted()));
        List<String> playerLines = new ArrayList<>();
        for (Line line : players.values()) {
            playerLines.add(String.join(";", date, String.valueOf(night.number()), String.valueOf(night.level()),
                    night.test() ? "ja" : "nein", line.name, line.clazz, line.subclass, num(line.dealt), num(line.taken),
                    num(line.healed), String.valueOf(line.kills), String.valueOf(line.deaths), night.result(),
                    num(line.dealtCompanions)));
        }
        append(Json.configFile(NIGHT_FILE), NIGHT_HEADER, List.of(nightLine));
        append(Json.configFile(PLAYER_FILE), PLAYER_HEADER, playerLines);
    }

    /** The night for the statistics window: how it went and one row per defender. */
    public com.mki.nightsiege.core.NightStatsData summary(Night night) {
        com.mki.nightsiege.core.NightStatsData data = new com.mki.nightsiege.core.NightStatsData();
        data.level = night.level();
        data.event = night.event();
        data.test = night.test();
        data.result = night.result();
        data.kills = night.kills();
        data.required = night.required();
        data.waves = night.waves();
        data.seconds = (int) Math.max(0, (night.endTime() - startedAt) / 20);
        for (Line line : players.values()) {
            com.mki.nightsiege.core.NightStatsData.Row row = new com.mki.nightsiege.core.NightStatsData.Row();
            row.name = line.name;
            row.clazz = line.clazz;
            row.subclass = line.subclass;
            row.dealt = line.dealt;
            row.taken = line.taken;
            row.healed = line.healed;
            row.kills = line.kills;
            row.deaths = line.deaths;
            data.rows.add(row);
        }
        data.mvp = com.mki.nightsiege.core.NightStatsData.mvpOf(data.rows);
        return data;
    }

    /** The defenders of this night. */
    public java.util.Set<UUID> ids() {
        return players.keySet();
    }

    private static String num(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    private static void append(Path path, String header, List<String> lines) {
        try {
            List<String> out = new ArrayList<>();
            if (!Files.exists(path)) {
                out.add(header);
            }
            out.addAll(lines);
            Files.write(path, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Nacht-Protokoll nicht geschrieben: {}", e.toString());
        }
    }
}
