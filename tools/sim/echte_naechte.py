#!/usr/bin/env python3
"""Echte Abende fuers Balance-Dashboard: wertet die Logger-Dateien des Servers aus.

Aufruf: echte_naechte.py <stats-ordner> <ausgabe-ordner> [--ab 2026-12-01] [--build 3ebce29]
<stats-ordner> ist playtest/nightsiege/stats vom Server (nicht ins Repo legen: Spielernamen).

Schreibt (ohne Spielernamen, nur Unterklassen und Zahlen):
  echte_naechte.json      eine Zeile je Nacht: Datum, Build, Stufe, Spieler, Ergebnis, Dauer, Boss, Bewertungen
  echte_zellen.json       Siegquote je Stufenbereich und Spielerzahl
  echte_unterklassen.json je Unterklasse: Naechte, Siege, Schaden/eingesteckt je Minute, Tode je Nacht
  echte_gegner.json       Schaden an Spielern je Gegnertyp (Anteil)
  echte_dungeons.json     Versuche und Siege je Dungeon und fuer das Heerlager
Naechte der Team-Sim (Feld "sim") und abgebrochene Naechte zaehlen nicht.
"""
import argparse
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dashboard_data import FOES, SUBS  # noqa: E402

DUNGEONS = {
    "stone_crypt": "Steingruft", "desert_crypt": "Wüstengruft", "illager_keep": "Illager-Feste",
    "frost_keep": "Frostfeste", "poison_swamp": "Giftsumpf", "deep_cellar": "Tiefenkerker",
    "nether_forge": "Nether-Schmiede", "basalt_forge": "Basaltschmiede", "heerlager": "Heerlager",
}
CLASSES = {"warrior": "Krieger", "guardian": "Wächter", "archer": "Bogenschütze", "engineer": "Techniker", "summoner": "Beschwörer"}


def band(level):
    return "1–5" if level <= 5 else "6–10" if level <= 10 else "11–15" if level <= 15 else "16–20" if level <= 20 else "über 20"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("stats")
    ap.add_argument("out")
    ap.add_argument("--ab", default="", help="nur Naechte ab diesem Datum (JJJJ-MM-TT)")
    ap.add_argument("--build", default="", help="nur Naechte dieses Builds")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    nights, dungeons = [], []
    for f in sorted(os.listdir(a.stats)):
        if not f.endswith(".json"):
            continue
        try:
            n = json.load(open(os.path.join(a.stats, f), encoding="utf-8"))
        except (OSError, ValueError):
            continue
        if n.get("sim"):
            continue
        day = (n.get("start") or f.split("_", 1)[-1])[:10]
        if a.ab and day < a.ab or a.build and n.get("build") != a.build:
            continue
        if f.startswith(("night_", "testnacht_")):
            nights.append((f, n))
        elif f.startswith(("dungeon_", "heerlager_")):
            dungeons.append(n)

    rows, cells = [], collections.defaultdict(lambda: {"naechte": 0, "siege": 0})
    subs = collections.defaultdict(lambda: {"naechte": 0, "siege": 0, "schaden": 0.0, "erhalten": 0.0, "minuten": 0.0, "tode": 0})
    foes = collections.defaultdict(float)
    for f, n in nights:
        outcome = n.get("ergebnis") or "?"
        if outcome == "abgebrochen" or not n.get("abgeschlossen", True):
            continue
        won = outcome in ("gewonnen", "test_gehalten") and (not n.get("killZiel") or n.get("kills", 0) >= n.get("killZiel", 0))
        players = n.get("spieler") or {}
        votes = collections.Counter((n.get("bewertung") or {}).values())
        level = int(n.get("stufe") or 0)
        secs = max(1, int(n.get("dauerSekunden") or 0))
        sub_names = []
        per = collections.defaultdict(lambda: [0.0, 0.0, 0])
        for w in n.get("wellen") or []:
            for name, pw in (w.get("spieler") or {}).items():
                p = per[name]
                p[0] += pw.get("schaden", 0.0)
                p[1] += pw.get("erhalten", 0.0)
                p[2] += pw.get("tode", 0)
                for src, v in (pw.get("erhaltenNachGegner") or {}).items():
                    foes[src] += v
        for name, info in players.items():
            sub = info.get("unterklasse") or ""
            label = SUBS[sub][0] if sub in SUBS else CLASSES.get(info.get("klasse"), info.get("klasse") or "?") + " (ohne Unterklasse)"
            sub_names.append(label)
            s = subs[label]
            s["klasse"] = CLASSES.get(info.get("klasse"), "–")
            s["naechte"] += 1
            s["siege"] += int(won)
            d, e, t = per.get(name, [0.0, 0.0, 0])
            s["schaden"] += d
            s["erhalten"] += e
            s["tode"] += t
            s["minuten"] += secs / 60.0
        c = cells[(band(level), len(players))]
        c["naechte"] += 1
        c["siege"] += int(won)
        rows.append({
            "nacht": f.split(".")[0], "datum": (n.get("start") or "")[:16].replace("T", " "), "build": n.get("build") or "",
            "stufe": level, "spieler": len(players), "unterklassen": ", ".join(sorted(sub_names)),
            "ergebnis": "gewonnen" if won else "verloren", "sekunden": secs, "kills": n.get("kills", 0),
            "killziel": n.get("killZiel", 0), "boss": n.get("boss") or "", "tode": n.get("tode", 0),
            "zu_leicht": votes.get("leicht", 0), "passt": votes.get("passt", 0), "zu_schwer": votes.get("schwer", 0),
        })

    def write(name, data):
        with open(os.path.join(a.out, name), "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))

    write("echte_naechte.json", rows)
    order = ["1–5", "6–10", "11–15", "16–20", "über 20"]
    write("echte_zellen.json", [{"stufen": k[0], "ordnung": order.index(k[0]), "spieler": k[1], "naechte": v["naechte"],
                                 "siege": v["siege"], "siegquote": round(v["siege"] / v["naechte"], 4)}
                                for k, v in sorted(cells.items(), key=lambda kv: (order.index(kv[0][0]), kv[0][1]))])
    write("echte_unterklassen.json", [{"name": k, "klasse": v.get("klasse", "–"), "naechte": v["naechte"], "siege": v["siege"],
                                       "siegquote": round(v["siege"] / v["naechte"], 4),
                                       "schaden_je_min": round(v["schaden"] / max(0.1, v["minuten"])),
                                       "erhalten_je_min": round(v["erhalten"] / max(0.1, v["minuten"])),
                                       "tode_je_nacht": round(v["tode"] / v["naechte"], 2)}
                                      for k, v in sorted(subs.items())])
    total = sum(foes.values()) or 1.0
    write("echte_gegner.json", [{"typ": k, "name": FOES.get(k.split(":")[-1], k.split(":")[-1]), "schaden": round(v),
                                 "anteil": round(v / total, 4)} for k, v in sorted(foes.items(), key=lambda kv: -kv[1]) if v > 0])
    dg = collections.defaultdict(lambda: {"versuche": 0, "siege": 0})
    for n in dungeons:
        if n.get("ergebnis") in ("abgebrochen", "verlassen"):
            continue
        t = n.get("typ") or n.get("art") or "?"
        dg[t]["versuche"] += 1
        dg[t]["siege"] += int(n.get("ergebnis") in ("gewonnen", "zerstoert"))
    write("echte_dungeons.json", [{"typ": k, "name": DUNGEONS.get(k, k), "versuche": v["versuche"], "siege": v["siege"],
                                   "siegquote": round(v["siege"] / v["versuche"], 4)} for k, v in sorted(dg.items(), key=lambda kv: -kv[1]["versuche"])])
    print("Echte Abende: %d Nächte, %d Unterklassen, %d Dungeon-/Heerlager-Arten" % (len(rows), len(subs), len(dg)))


if __name__ == "__main__":
    main()
