package com.mki.nightsiege.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tor 12 Teil D: the night atlas. Writes a Markdown table of every night (level 1-40 x 1/2/4/6 defenders) from the
 * real planning code of the mod (SiegeMath, MobPool, Chapters, BossPlan, SiegeMachineKind, Elites ...) with the
 * standard config (= server values), and checks the rules of D.2. It never fails the build: findings go to the top
 * of the file. Output: env ATLAS_OUT, else build/atlas.md.
 * Command: see the head of production/qa/tor-12/atlas.md.
 */
class NachtAtlasTest {

    private static final int[] GROUPS = {1, 2, 4, 6};
    private static final int MAX_LEVEL = 40;

    private record Finding(String rule, String level, String players, String value) {
    }

    private final List<Finding> findings = new ArrayList<>();

    private void flag(String rule, int level, int players, String value) {
        findings.add(new Finding(rule, level < 0 ? "-" : String.valueOf(level), players < 0 ? "-" : String.valueOf(players), value));
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f %%", v * 100.0);
    }

    private static int maxLive(SiegeConfig c, int n) {
        int cap = SiegeMath.liveCap(n, c);
        return c.refillSpawns ? Math.min(cap, c.refillCap) : cap;
    }

    private static int base(SiegeConfig c, int level, int n) {
        return SiegeMath.waveSize(level, n, c, c.playerScaling, maxLive(c, n), 1.0, false, 1.0);
    }

    private static int phantoms(SiegeConfig c, int level) {
        return Math.min(12, Math.max(0, c.nightPhantoms) + (int) Math.floor(Math.max(0.0, c.nightPhantomsPerLevel) * level));
    }

    @Test
    void writeAtlas() throws Exception {
        SiegeConfig c = new Gson().fromJson("{}", SiegeConfig.class);
        c.sanitize();

        StringBuilder a = new StringBuilder();

        // ---------------------------------------------------------------- A: every night
        a.append("## A. Alle Nächte (Standardwerte, Spieler = Anwesende)\n\n");
        a.append("Stufe = Level der Nacht. Kapitel 2 spielt wieder Stufe 1-20 (Leben/Schaden mal 1+chapterScaling, Monsterzahl gleich); "
                + "Stufen 21-40 gibt es als Testnacht oder alter Spielstand. Art: N normal, B Boss, F Finale (Vierer-Nacht, keine Wellen).\n");
        a.append("Welle = Monster je Welle (normale Nacht, ohne Ereignis); Grenze = Lebend-Grenze; Spitze = Grenze + Phantome + "
                + "Sprengmeister/Betäuber je Welle (Sonderspawns zählen nicht gegen die Grenze); Überlauf = gewollte Zahl über der Grenze (kommt als Elite/Leben).\n\n");
        a.append("| Stufe | Art | Länge s | Wellen | Spieler | Welle | gesamt | Grenze | Spitze | Überlauf | Maschinen | Boss-Vorwelle |\n");
        a.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        Map<String, Integer> prevBase = new LinkedHashMap<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            BossPlan.NightKind kind = Chapters.nightKind(Chapters.nightOf(level), c);
            String art = kind == BossPlan.NightKind.NORMAL ? "N" : kind == BossPlan.NightKind.BOSS ? "B" : "F";
            int seconds = SiegeMath.durationSeconds(level, c);
            int waves = SiegeMath.plannedWaves(seconds * 20, c, 1.0);
            int lastCap = 0;
            for (int n : GROUPS) {
                int cap = SiegeMath.liveCap(n, c);
                int live = maxLive(c, n);
                int wave = base(c, level, n);
                double wanted = SiegeMath.mobsWanted(level, n, c, c.playerScaling);
                int ph = phantoms(c, level);
                int sappers = level >= c.sapperFromLevel ? Math.min((int) Math.ceil(SiegeMath.sappersWanted(level, c)), Math.max(1, c.sapperMaxPerWave)) : 0;
                int stun = level >= c.stunnerFromLevel ? Math.max(0, SiegeMath.stunnersWanted(n, c, 0)) : 0;
                int peak = live + ph + sappers + stun;
                int total = waves * wave + ph;
                double pre = BossNight.preWaveFactor(kind == BossPlan.NightKind.BOSS, false, c.bossPreWaveFactor);
                int preWave = (int) Math.round(wave * pre);
                String machines;
                if (level >= c.machinesRandomFromLevel) {
                    machines = pct(c.machinesRandomChanceAt(level)) + " x" + SiegeMachineKind.randomCount(level, c.machinesRandomFewer, c.machinesMax)
                            + " / Lager x" + SiegeMachineKind.campCount(level, c.machinesCampFactor, c.machinesMax);
                } else {
                    machines = "nur Lager x" + SiegeMachineKind.campCount(level, c.machinesCampFactor, c.machinesMax);
                }
                String kill = "";
                a.append("| ").append(level).append(" | ").append(art).append(" | ").append(seconds).append(" | ")
                        .append(kind == BossPlan.NightKind.GROUP ? "-" : String.valueOf(waves)).append(" | ").append(n).append(" | ")
                        .append(kind == BossPlan.NightKind.GROUP ? "-" : String.valueOf(wave)).append(" | ")
                        .append(kind == BossPlan.NightKind.GROUP ? "-" : String.valueOf(total)).append(" | ").append(live).append(" | ")
                        .append(peak).append(" | ").append(wanted > live ? f1(wanted) : "-").append(" | ").append(machines).append(" | ")
                        .append(kind == BossPlan.NightKind.BOSS ? String.valueOf(preWave) : "-").append(" |")
                        .append(kill).append("\n");

                // rule 1: no wave with 0 monsters (finale nights have no waves)
                if (kind != BossPlan.NightKind.GROUP && wave <= 0) {
                    flag("1 Welle mit 0 Monstern", level, n, "Welle " + wave);
                }
                if (kind == BossPlan.NightKind.BOSS && preWave <= 0) {
                    flag("1 Welle mit 0 Monstern", level, n, "Vorwelle der Bossnacht " + preWave);
                }
                // rule 2: growth between neighbour levels
                String key = n + "";
                Integer before = prevBase.get(key);
                BossPlan.NightKind prevKind = Chapters.nightKind(Chapters.nightOf(level - 1), c);
                if (before != null && before > 0 && kind == BossPlan.NightKind.NORMAL && prevKind == BossPlan.NightKind.NORMAL
                        && wave > before * 1.25 + 1e-9) {
                    flag("2 Sprung über 25 %", level, n, before + " -> " + wave + " (+" + Math.round((wave / (double) before - 1) * 100) + " %)");
                }
                prevBase.put(key, wave);
                // rule 4: the live cap
                if (cap > c.maxLiveMobsCap) {
                    flag("4 Lebend-Grenze über Deckel", level, n, cap + " > " + c.maxLiveMobsCap);
                }
                if (cap < lastCap) {
                    flag("4 Grenze sinkt mit Spielerzahl", level, n, cap + " < " + lastCap);
                }
                if (wave > live) {
                    flag("4 Welle über Grenze", level, n, wave + " > " + live);
                }
                if (peak > c.maxLiveMobsCap) {
                    flag("4 Spitze mit Sonderspawns über Deckel", level, n, peak + " > " + c.maxLiveMobsCap);
                }
                lastCap = cap;
            }
        }

        // ---------------------------------------------------------------- B: types from the pool
        a.append("\n## B. Typen aus der Mob-Liste (ab minLevel)\n\n");
        a.append("Erster Auftritt = erste Stufe, auf der MobPool den Eintrag zulässt (muss minLevel sein). Gewicht% = Anteil an der Liste auf dieser Stufe; "
                + "Je Nacht = Erwartung bei 1 Spieler auf minLevel (Wellen x Welle x Anteil); P(>=1) = Chance, dass er in der Nacht mindestens einmal kommt.\n\n");
        a.append("| Typ | minLevel | maxLevel | Gewicht | erster Auftritt | Anteil minLevel | Anteil Stufe 20 | Je Nacht (min) | P(>=1) | Grenze je Welle |\n|---|---|---|---|---|---|---|---|---|---|\n");
        MobPool pool = new MobPool(c.mobs);
        for (MobEntry e : c.mobs) {
            if (!e.isValid()) {
                flag("3 Typ ungültig", -1, -1, e.id);
                continue;
            }
            int first = -1;
            for (int l = 1; l <= MAX_LEVEL && first < 0; l++) {
                if (pool.eligible(l).contains(e)) {
                    first = l;
                }
            }
            if (first != e.minLevel) {
                flag("3 Typ kommt nicht ab minLevel vor", e.minLevel, -1, e.id + " erster Auftritt " + first);
            }
            int lvl = Math.max(1, e.minLevel);
            double shareMin = shareOf(pool, e, lvl);
            double share20 = e.appliesTo(20) ? shareOf(pool, e, 20) : 0.0;
            int waves = SiegeMath.plannedWaves(SiegeMath.durationSeconds(lvl, c) * 20, c, 1.0);
            double perNight = waves * base(c, lvl, 1) * shareMin;
            double p1 = 1.0 - Math.pow(1.0 - shareMin, waves * base(c, lvl, 1));
            String cap = capOf(e.baseId(), c);
            a.append("| ").append(e.id).append(" | ").append(e.minLevel).append(" | ").append(e.maxLevel == 0 ? "-" : String.valueOf(e.maxLevel))
                    .append(" | ").append(e.weight).append(" (").append(e.weightPerLevel).append("/Stufe) | ").append(first).append(" | ")
                    .append(pct(shareMin)).append(" | ").append(pct(share20)).append(" | ").append(f1(perNight)).append(" | ").append(pct(p1))
                    .append(" | ").append(cap).append(" |\n");
            if (perNight < 0.5) {
                flag("3 Typ ab minLevel praktisch nie (Erwartung < 0,5 je Nacht, 1 Spieler)", e.minLevel, 1, e.id + " " + f1(perNight) + " je Nacht, P(>=1) " + pct(p1));
            }
            if ("nightsiege:breaker".equals(e.id) || cap.equals("0 (nie aus Liste)")) {
                flag("3 Typ in der Liste, aber Grenze 0", e.minLevel, -1, e.id);
            }
        }

        // ---------------------------------------------------------------- C: special monsters
        a.append("\n## C. Sondermonster (je Stufe, ab ihrer Stufe)\n\n");
        a.append("Zahlen je Welle bei 1 / 4 Spielern (Betäuber: Nahkämpfer = Spielerzahl; Brecher/Scharfrichter brauchen Tanks, je 1 je Tank).\n\n");
        a.append("| Sondermonster | ab Stufe | Quelle | Stufe 5 | 10 | 15 | 20 | 30 | 40 |\n|---|---|---|---|---|---|---|---|---|\n");
        int[] at = {5, 10, 15, 20, 30, 40};
        special(a, "Wiedergänger", poolMin(c, "nightsiege:revenant"), "Liste", at, l -> shareTxt(pool, c, "nightsiege:revenant", l));
        special(a, "Tunnelgräber", poolMin(c, "nightsiege:tunneler"), "Liste, max " + c.tunnelersPerWave + "/Welle", at, l -> shareTxt(pool, c, "nightsiege:tunneler", l));
        special(a, "Bannerträger", poolMin(c, "nightsiege:standard_bearer"), "Liste, max " + c.standardBearersPerWave + "/Welle, " + c.standardBearersPerWave * 2 + " ab " + c.standardBearerTwoFromLevel,
                at, l -> shareTxt(pool, c, "nightsiege:standard_bearer", l));
        special(a, "Greifer", poolMin(c, "nightsiege:grappler"), "Liste, max " + c.grapplersAtOnce + " gleichzeitig", at, l -> shareTxt(pool, c, "nightsiege:grappler", l));
        special(a, "Eisenritter", poolMin(c, "nightsiege:iron_knight"), "Liste", at, l -> shareTxt(pool, c, "nightsiege:iron_knight", l));
        special(a, "Sprengmeister (Sapper)", c.sapperFromLevel, "Besiegers, je Welle, max " + c.sapperMaxPerWave, at,
                l -> l < c.sapperFromLevel ? "-" : f1(Math.min(SiegeMath.sappersWanted(l, c), c.sapperMaxPerWave)));
        special(a, "Betäuber", c.stunnerFromLevel, "Foes, max " + c.stunnersMax + " lebend", at,
                l -> l < c.stunnerFromLevel ? "-" : SiegeMath.stunnersWanted(1, c, 0) + " / " + SiegeMath.stunnersWanted(4, c, 0));
        special(a, "Brecher (Tank)", c.breakerFromLevel, "Foes, 1 je Tank/Welle", at, l -> l < c.breakerFromLevel ? "-" : "1 je Tank");
        special(a, "Scharfrichter (Tank)", c.executionerFromLevel, "Foes, 1 je Tank/Welle", at, l -> l < c.executionerFromLevel ? "-" : "1 je Tank");
        special(a, "Schatzräuber", c.treasureRaiderFromLevel, "Foes, Chance je Nacht (ab 3 Wellen)", at, l -> l < c.treasureRaiderFromLevel ? "-" : pct(c.treasureRaiderChance));
        special(a, "Phantome (Welle 2)", 1, "SiegeManager", at, l -> String.valueOf(phantoms(c, l)));
        for (Object[] r : new Object[][]{{"Wiedergänger", poolMin(c, "nightsiege:revenant")}, {"Tunnelgräber", poolMin(c, "nightsiege:tunneler")},
                {"Bannerträger", poolMin(c, "nightsiege:standard_bearer")}, {"Greifer", poolMin(c, "nightsiege:grappler")}, {"Eisenritter", poolMin(c, "nightsiege:iron_knight")}}) {
            if ((int) r[1] < 0) {
                flag("3 Sondermonster fehlt in der Liste", -1, -1, (String) r[0]);
            }
        }
        if (c.sapperFromLevel <= 0 || c.sapperMaxPerWave <= 0) {
            flag("3 Sondermonster aus", -1, -1, "Sprengmeister");
        }
        // Elite: the fixed eliteMobs are gone (Tor 12 J), only the smart night, the bastion and the wardens send them.
        if (c.stunnersMax <= 0 || c.treasureRaiderChance <= 0) {
            flag("3 Sondermonster aus", -1, -1, "Betäuber/Schatzräuber Wert 0");
        }

        // ---------------------------------------------------------------- D: special nights
        a.append("\n## D. Sondernächte (je Art)\n\n");
        a.append("**Bossnächte** (Nacht im Kapitel, jede ").append(c.bossEvery).append(".; Finale jede ").append(c.bossGroupEvery).append(".): Kapitel 1 ")
                .append(Chapters.bossGroup(1)).append(", Kapitel 2 ").append(Chapters.bossGroup(2)).append(".\n\n");
        a.append("| Stufe | Art | Kap./Nacht | Boss kommt nach s | Vorwelle (1 Sp.) | Vorwelle (4 Sp.) | Boss-Leben 1 Sp. | 4 Sp. |\n|---|---|---|---|---|---|---|---|\n");
        for (int level = 1; level <= MAX_LEVEL; level++) {
            BossPlan.NightKind kind = Chapters.nightKind(Chapters.nightOf(level), c);
            if (kind == BossPlan.NightKind.NORMAL) {
                continue;
            }
            int ticks = SiegeMath.durationSeconds(level, c) * 20;
            double pre = BossNight.preWaveFactor(kind == BossPlan.NightKind.BOSS, false, c.bossPreWaveFactor);
            a.append("| ").append(level).append(" | ").append(kind == BossPlan.NightKind.BOSS ? "Boss" : "Finale (4 Bosse)").append(" | K")
                    .append(Chapters.chapterOf(level)).append(" N").append(Chapters.nightOf(level)).append(" | ")
                    .append(BossNight.arrivalTicks(ticks, c) / 20).append(" | ").append(Math.round(base(c, level, 1) * pre)).append(" | ")
                    .append(Math.round(base(c, level, 4) * pre)).append(" | ").append(Math.round(BossPlan.health(100.0, level, 1, c))).append(" % von 100 | ")
                    .append(Math.round(BossPlan.health(100.0, level, 4, c))).append(" % |\n");
        }
        a.append("\n**Ereignisnächte** (ab Stufe ").append(c.eventMinLevel).append(", Chance ").append(pct(c.eventChance)).append(" je Nacht; auch Fragment-Nächte nehmen dieses Ereignis): "
                + "Welle mit Ereignis bei Stufe 5/10/20/40, Spieler 1/4/6; (c) = von der Grenze gekappt.\n\n");
        a.append("| Ereignis | Gewicht | Zahl-Faktor | Stufe 5 | 10 | 20 | 40 | Fragment |\n|---|---|---|---|---|---|---|---|\n");
        int[] evLevels = {5, 10, 20, 40};
        int[] weights = {c.bloodMoonWeight, c.eclipseWeight, c.fogWeight, c.thunderWeight, c.netherRiftWeight, c.frostWeight};
        SiegeEvent[] evs = {SiegeEvent.BLOOD_MOON, SiegeEvent.ECLIPSE, SiegeEvent.FOG, SiegeEvent.THUNDER, SiegeEvent.NETHER_RIFT, SiegeEvent.FROST};
        for (int i = 0; i < evs.length; i++) {
            a.append("| ").append(evs[i]).append(" | ").append(weights[i]).append(" | ").append(evs[i].mobCountMultiplier(c)).append(" |");
            for (int l : evLevels) {
                a.append(" ").append(waveTxt(c, l, evs[i].mobCountMultiplier(c), false, 1.0));
                if (evs[i].mobCountMultiplier(c) < 1.0 && base(c, l, 1) > 0 && waveOf(c, l, 1, evs[i].mobCountMultiplier(c), false, 1.0) <= 0) {
                    flag("1 Welle mit 0 Monstern", l, 1, "Ereignis " + evs[i]);
                }
                a.append(" |");
            }
            String frag = "-";
            for (NightFragment nf : NightFragment.values()) {
                if (nf.event() == evs[i]) {
                    frag = nf.id();
                }
            }
            a.append(" ").append(frag).append(" |\n");
        }
        a.append("\n**Wächter der Nacht**: ab Stufe ").append(c.wardenFromLevel).append(", Chance ").append(pct(c.wardenChance))
                .append(" je normaler Nacht (Boss-/Finale-Nächte ausgenommen), ein Wächter (Dungeon-Boss) kommt am Ende; Wellen wie normal.\n\n");
        a.append("**Fluch-Nächte** (bis ").append(c.curseMax).append(" Flüche; wirken auf die Welle): Übermacht x").append(c.curseMobs).append(" Monster, Sturmlauf x")
                .append(c.curseWaves).append(" Wellenabstand, Champions 1 je Welle. Welle / Wellen bei Stufe 5/10/20:\n\n");
        a.append("| Fluch | Spieler | Stufe 5 | 10 | 20 |\n|---|---|---|---|---|\n");
        for (int n : new int[]{1, 4, 6}) {
            curseRow(a, c, "keiner", n, 1.0, 1.0);
            curseRow(a, c, "Übermacht", n, c.curseMobs, 1.0);
            curseRow(a, c, "Sturmlauf", n, 1.0, c.curseWaves);
            curseRow(a, c, "Übermacht+Sturmlauf+Champions", n, c.curseMobs, c.curseWaves);
        }
        a.append("\n**Extra-Belagerung** (ab Stufe ").append(c.extraSiegeFromLevel).append(", Monsterzahl x").append(c.specialNightMobMultiplier).append(", XP x")
                .append(c.specialNightXpMultiplier).append("): Welle bei 1/4/6 Spielern, ");
        StringBuilder ex = new StringBuilder();
        for (int l : new int[]{20, 30, 40}) {
            ex.append("S").append(l).append(": ");
            for (int n : new int[]{1, 4, 6}) {
                int w = waveOf(c, l, n, 1.0, true, 1.0);
                ex.append(w).append(w > maxLive(c, n) ? "(c)" : "").append(n == 6 ? "" : "/");
            }
            ex.append("  ");
        }
        a.append(ex).append("\n");

        // ---------------------------------------------------------------- head
        StringBuilder out = new StringBuilder();
        out.append("# Nacht-Atlas (Tor 12 Teil D)\n\n");
        out.append("Erzeugt vom Test `NachtAtlasTest` aus der echten Planungslogik der Mod mit den Standardwerten (= Server v31). Neu erzeugen "
                + "(im Ordner `wt/tor12-fruehe`, ein Befehl):\n\n```\n"
                + "ATLAS_OUT=C:/Users/simon/NightSiege-Mod/production/qa/tor-12/atlas.md JAVA_HOME=C:/Users/simon/NightSiege-Mod/tools/jdk-25 "
                + "PATH=\"$JAVA_HOME/bin:C:/Users/simon/NightSiege-Mod/tools/gradle/bin:$PATH\" gradle test --no-daemon "
                + "-Pfabric_version=0.161.0+26.2 --tests com.mki.nightsiege.core.NachtAtlasTest\n```\n\n");
        out.append("Kill-Ziel: gibt es nicht mehr (Tor 12 F/J).\n\n");
        out.append("## Verstöße gegen die Prüfregeln (D.2) – nur gemeldet, nicht behoben\n\n");
        Map<String, List<Finding>> byRule = new TreeMap<>();
        for (Finding f : findings) {
            byRule.computeIfAbsent(f.rule, k -> new ArrayList<>()).add(f);
        }
        if (findings.isEmpty()) {
            out.append("Keine Verstöße.\n");
        } else {
            out.append("| Regel | Anzahl |\n|---|---|\n");
            for (Map.Entry<String, List<Finding>> e : byRule.entrySet()) {
                out.append("| ").append(e.getKey()).append(" | ").append(e.getValue().size()).append(" |\n");
            }
            out.append("\n| Regel | Stufe | Spieler | Wert |\n|---|---|---|---|\n");
            for (Map.Entry<String, List<Finding>> e : byRule.entrySet()) {
                for (Finding f : e.getValue()) {
                    out.append("| ").append(f.rule).append(" | ").append(f.level).append(" | ").append(f.players).append(" | ").append(f.value).append(" |\n");
                }
            }
        }
        out.append("\n").append(a);

        String target = System.getenv("ATLAS_OUT");
        Path path = Path.of(target != null && !target.isBlank() ? target : "build/atlas.md");
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        System.out.println("ATLAS " + path.toAbsolutePath() + " Verstoesse=" + findings.size());
        for (Map.Entry<String, List<Finding>> e : byRule.entrySet()) {
            System.out.println("ATLAS-REGEL " + e.getKey() + " = " + e.getValue().size());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int waveOf(SiegeConfig c, int level, int n, double ev, boolean special, double curse) {
        return SiegeMath.waveSize(level, n, c, c.playerScaling, maxLive(c, n), ev, special, curse);
    }

    private static String waveTxt(SiegeConfig c, int level, double ev, boolean special, double curse) {
        StringBuilder s = new StringBuilder();
        for (int n : new int[]{1, 4, 6}) {
            int w = waveOf(c, level, n, ev, special, curse);
            s.append(w).append(w > maxLive(c, n) ? "(c)" : "").append(n == 6 ? "" : "/");
        }
        return s.toString();
    }

    private static void curseRow(StringBuilder a, SiegeConfig c, String name, int n, double mobs, double waves) {
        a.append("| ").append(name).append(" | ").append(n).append(" |");
        for (int l : new int[]{5, 10, 20}) {
            int w = waveOf(c, l, n, 1.0, false, mobs);
            int cnt = SiegeMath.plannedWaves(SiegeMath.durationSeconds(l, c) * 20, c, waves);
            a.append(" ").append(w).append(w > maxLive(c, n) ? "(c)" : "").append(" x ").append(cnt).append(" |");
        }
        a.append("\n");
    }

    private static double shareOf(MobPool pool, MobEntry e, int level) {
        int total = 0;
        for (MobEntry x : pool.eligible(level)) {
            total += x.weightAt(level);
        }
        return total <= 0 ? 0.0 : e.weightAt(level) / (double) total;
    }

    private static int poolMin(SiegeConfig c, String id) {
        for (MobEntry e : c.mobs) {
            if (id.equals(e.id)) {
                return e.minLevel;
            }
        }
        return -1;
    }

    private static String shareTxt(MobPool pool, SiegeConfig c, String id, int level) {
        for (MobEntry e : c.mobs) {
            if (id.equals(e.id)) {
                if (!pool.eligible(level).contains(e)) {
                    return "-";
                }
                int waves = SiegeMath.plannedWaves(SiegeMath.durationSeconds(level, c) * 20, c, 1.0);
                return pct(shareOf(pool, e, level)) + " (~" + f1(waves * base(c, level, 1) * shareOf(pool, e, level)) + "/Nacht)";
            }
        }
        return "fehlt";
    }

    private static String capOf(String baseId, SiegeConfig c) {
        return switch (baseId) {
            case "nightsiege:tunneler" -> String.valueOf(c.tunnelersPerWave);
            case "nightsiege:standard_bearer" -> c.standardBearersPerWave + " (" + c.standardBearersPerWave * 2 + " ab " + c.standardBearerTwoFromLevel + ")";
            case "nightsiege:grappler" -> c.grapplersAtOnce + " gleichzeitig";
            case "nightsiege:treasure_raider", "nightsiege:breaker", "nightsiege:executioner" -> "0 (nie aus Liste)";
            default -> "-";
        };
    }

    private interface At {
        String at(int level);
    }

    private static void special(StringBuilder a, String name, int from, String source, int[] levels, At at) {
        a.append("| ").append(name).append(" | ").append(from < 0 ? "fehlt" : String.valueOf(from)).append(" | ").append(source).append(" |");
        for (int l : levels) {
            a.append(" ").append(at.at(l)).append(" |");
        }
        a.append("\n");
    }
}
