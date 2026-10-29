#!/usr/bin/env python3
"""Fuehrt die Teile einer Team-Sim (CI-Jobs) zusammen.

Aufruf: merge.py <ordner-mit-teilen> <ausgabe>
Liest teamsim-*.csv, stats/*.json und sim-log-*.txt aus allen Unterordnern und schreibt:
  teamsim.csv      alle Zeilen (LAUF und BOT) in einer Datei
  teamsim.txt      Siegquote je Variante, Stufe und Gruppe, dazu je Unterklasse
  summary.json     dasselbe als Daten (fuer das Balance-Dashboard)
  stats/           jede Testnacht des Statistik-Loggers
  sim-log.txt      die Sim-Zeilen aller Jobs
"""
import json
import os
import shutil
import sys
from collections import defaultdict

TARGET = 0.70


def main(src, dst):
    os.makedirs(os.path.join(dst, "stats"), exist_ok=True)
    runs, bots, logs = [], [], []
    for root, _dirs, files in os.walk(src):
        for f in sorted(files):
            path = os.path.join(root, f)
            if f.startswith("teamsim-") and f.endswith(".csv"):
                for line in open(path, encoding="utf-8"):
                    parts = line.rstrip("\n").split(";")
                    if parts[0] == "LAUF" and len(parts) >= 14:
                        runs.append(parts)
                    elif parts[0] == "BOT" and len(parts) >= 12:
                        bots.append(parts)
            elif f.endswith(".json") and os.path.basename(root) == "stats":
                shutil.copy(path, os.path.join(dst, "stats", f))
            elif f.startswith("sim-log-"):
                logs.append(open(path, encoding="utf-8", errors="replace").read())
    with open(os.path.join(dst, "teamsim.csv"), "w", encoding="utf-8") as out:
        out.write("art;variante;stufe;gruppe;mischung;lauf;ergebnis;gehalten;sieg;kills;ziel;sekunden;tode;unterklassen\n")
        for r in runs:
            out.write(";".join(r) + "\n")
        out.write("art;variante;stufe;gruppe;mischung;lauf;unterklasse;schaden;erhalten;heilung;kills;tode\n")
        for b in bots:
            out.write(";".join(b) + "\n")
    with open(os.path.join(dst, "sim-log.txt"), "w", encoding="utf-8") as out:
        out.write("\n".join(logs))

    cells = defaultdict(lambda: {"laeufe": 0, "siege": 0, "gehalten": 0, "kills": 0, "ziel": 0, "sekunden": 0, "tode": 0})
    mixes = defaultdict(lambda: {"laeufe": 0, "siege": 0})
    problems = defaultdict(int)
    for r in runs:
        _, var, lvl, grp, mix, idx, outcome, held, won, kills, goal, secs, deaths, subs = r[:14]
        c = cells[(var, int(lvl), int(grp))]
        c["laeufe"] += 1
        c["siege"] += int(won)
        c["gehalten"] += int(held)
        c["kills"] += int(kills)
        c["ziel"] += int(goal)
        c["sekunden"] += int(secs)
        c["tode"] += int(deaths)
        mx = mixes[(var, int(grp), mix)]
        mx["laeufe"] += 1
        mx["siege"] += int(won)
        if outcome not in ("test_gehalten", "test_verloren"):
            problems[outcome] += 1

    subs = defaultdict(lambda: {"laeufe": 0, "siege": 0, "schaden": 0.0, "erhalten": 0.0, "heilung": 0.0, "kills": 0.0, "tode": 0.0})
    won_of = {}
    for r in runs:
        won_of[(r[1], r[2], r[3], r[4], r[5])] = int(r[8])
        for s in set(r[13].split("+")):
            if s:
                subs[(r[1], s)]["laeufe"] += 1
                subs[(r[1], s)]["siege"] += int(r[8])
    for b in bots:
        _, var, lvl, grp, mix, idx, sub, dmg, taken, heal, kills, deaths = b[:12]
        s = subs[(var, sub)]
        s["schaden"] += float(dmg)
        s["erhalten"] += float(taken)
        s["heilung"] += float(heal)
        s["kills"] += float(kills)
        s["tode"] += float(deaths)

    variants = sorted({k[0] for k in cells})
    levels = sorted({k[1] for k in cells})
    groups = sorted({k[2] for k in cells})
    lines = ["Team-Sim: %d Läufe, Ziel %.0f %% Siege (! unter 55 %%, + über 85 %%)" % (len(runs), TARGET * 100)]
    if problems:
        lines.append("Läufe ohne normales Ende: " + ", ".join("%s %d" % kv for kv in sorted(problems.items())))
    for var in variants:
        lines.append("")
        lines.append("Variante %s – Siegquote je Stufe (Zeilen) und Spielerzahl (Spalten)" % var)
        lines.append("Stufe | " + " | ".join("%2d Sp." % g for g in groups))
        for lvl in levels:
            row = []
            for g in groups:
                c = cells.get((var, lvl, g))
                if not c or not c["laeufe"]:
                    row.append("  -   ")
                else:
                    q = c["siege"] / c["laeufe"]
                    row.append("%3.0f%%%s" % (100 * q, "!" if q < 0.55 else "+" if q > 0.85 else " ") + " ")
            lines.append("%5d | %s" % (lvl, " | ".join(row)))
    lines.append("")
    lines.append("Je Gruppenart: Siegquote")
    for (var, grp, mix), m in sorted(mixes.items()):
        lines.append("  %s %2d Sp. %-14s %3.0f %% (%d Läufe)" % (var, grp, mix, 100 * m["siege"] / max(1, m["laeufe"]), m["laeufe"]))
    lines.append("")
    lines.append("Je Unterklasse (alle Stufen): Läufe, Siegquote der Gruppe, je Lauf Schaden / erhalten / Heilung / Tode")
    for (var, sub), s in sorted(subs.items(), key=lambda kv: (kv[0][0], -kv[1]["siege"] / max(1, kv[1]["laeufe"]))):
        n = max(1, s["laeufe"])
        lines.append("  %s %-14s %4d  %3.0f %%  %7.0f %7.0f %6.0f %5.2f" % (var, sub, s["laeufe"], 100 * s["siege"] / n,
                     s["schaden"] / n, s["erhalten"] / n, s["heilung"] / n, s["tode"] / n))
    with open(os.path.join(dst, "teamsim.txt"), "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    summary = {
        "ziel": TARGET,
        "laeufe": len(runs),
        "probleme": problems,
        "zellen": [dict(variante=k[0], stufe=k[1], gruppe=k[2], **v) for k, v in sorted(cells.items())],
        "mischungen": [dict(variante=k[0], gruppe=k[1], mischung=k[2], **v) for k, v in sorted(mixes.items())],
        "unterklassen": [dict(variante=k[0], unterklasse=k[1], **v) for k, v in sorted(subs.items())],
    }
    with open(os.path.join(dst, "summary.json"), "w", encoding="utf-8") as out:
        json.dump(summary, out, ensure_ascii=False, indent=1)
    print("\n".join(lines[:60]))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
