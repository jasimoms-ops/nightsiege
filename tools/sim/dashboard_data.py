#!/usr/bin/env python3
"""Macht aus einem zusammengefuehrten Team-Sim-Lauf die Dateien fuers Balance-Dashboard.

Aufruf: dashboard_data.py <merged-ordner> <ausgabe-ordner> --lauf <name> [--burg standard|kopie]
Liest summary.json (aus merge.py) und schreibt:
  zellen.json       Siegquote je Variante, Koennen, Stufe und Spielerzahl
  mischungen.json   Siegquote je Gruppenart und Spielerzahl
  unterklassen.json je Unterklasse: Laeufe, Siegquote der Gruppe, je Lauf Schaden/erhalten/Heilung/Tode
  gegner.json       Schaden an Spielern je Gegnertyp (Anteil je Variante)
  lauf.json         eine Zeile ueber den Lauf (Name, Datum, Testnaechte, Boden)
"""
import argparse
import datetime
import json
import os
import re

SUBS = {
    "blademaster": ("Klingenmeister", "Krieger"), "berserker": ("Berserker", "Krieger"),
    "axe": ("Axtkämpfer", "Krieger"), "paladin": ("Paladin", "Krieger"),
    "shieldfighter": ("Schildkämpfer", "Wächter"), "bulwark": ("Bollwerk", "Wächter"),
    "medic": ("Sanitäter", "Wächter"), "warden": ("Bannwächter", "Wächter"),
    "swift": ("Schnellschütze", "Bogenschütze"), "heavy": ("Wuchtschütze", "Bogenschütze"),
    "bolter": ("Bolzenschütze", "Bogenschütze"), "trapper": ("Fallensteller", "Bogenschütze"),
    "gunner": ("Schütze", "Techniker"), "electrician": ("Elektriker", "Techniker"),
    "demolition": ("Sprengmeister", "Techniker"), "alchemist": ("Alchemist", "Techniker"),
    "tamer": ("Tierbändiger", "Beschwörer"), "golemsmith": ("Golemschmied", "Beschwörer"),
    "necromancer": ("Totenbeschwörer", "Beschwörer"), "soulcatcher": ("Seelenfänger", "Beschwörer"),
}
MIXES = {
    "zufall": "Zufällig gewählt", "tank_heiler": "Tank + Heiler + Schaden", "nur_schaden": "Nur Schaden",
    "tanks_technik": "Tanks + Techniker", "beschwoerer": "Nur Beschwörer",
}
FOES = {
    "skeleton": "Skelett", "pillager": "Plünderer", "zombie": "Zombie", "archer": "Bogen-Plünderer",
    "skirmisher": "Plänkler", "legioner": "Legionär", "creeper": "Creeper", "evoker": "Magier",
    "standard_bearer": "Standartenträger", "revenant": "Wiedergänger", "vindicator": "Diener",
    "stray": "Eiswanderer", "illusioner": "Illusionist", "husk": "Wüstenzombie", "drowned": "Ertrunkener",
    "spider": "Spinne", "cave_spider": "Höhlenspinne", "tunneler": "Tunnelgräber",
    "grappler": "Enterhaken-Plünderer", "iron_knight": "Eisenritter", "treasure_raider": "Schatz-Plünderer",
    "witch": "Hexe", "vex": "Plagegeist", "phantom": "Phantom", "bogged": "Sumpfskelett",
    "iceologer": "Eis-Illager", "wildfire": "Wildfire", "camel_husk": "Kamel-Wüstenzombie",
    "parched": "Ausgedörrter", "ravager": "Verwüster", "wither_skeleton": "Witherskelett",
    "breeze": "Breeze", "blaze": "Lohe", "slime": "Schleim", "magma_cube": "Magmawürfel",
}


def koennen_of(variant, given):
    if variant in given:
        return given[variant]
    m = re.fullmatch(r"k0?(\d)(\d?)", variant)
    if m:
        return float("0.%s%s" % (m.group(1), m.group(2) or ""))
    return 1.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("merged")
    ap.add_argument("out")
    ap.add_argument("--lauf", required=True)
    ap.add_argument("--burg", default="standard")
    ap.add_argument("--koennen", default="", help="Variante=Wert,… wenn der Name es nicht sagt")
    a = ap.parse_args()
    given = {}
    for kv in filter(None, a.koennen.split(",")):
        k, v = kv.split("=")
        given[k] = float(v)
    s = json.load(open(os.path.join(a.merged, "summary.json"), encoding="utf-8"))
    os.makedirs(a.out, exist_ok=True)

    def write(name, rows):
        with open(os.path.join(a.out, name), "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))

    zellen = []
    for c in s["zellen"]:
        n = max(1, c["laeufe"])
        zellen.append({
            "variante": c["variante"], "koennen": koennen_of(c["variante"], given), "stufe": c["stufe"],
            "spieler": c["gruppe"], "laeufe": c["laeufe"], "siege": c["siege"],
            "siegquote": round(c["siege"] / n, 4), "tode_je_lauf": round(c["tode"] / n, 2),
            "sekunden_je_lauf": round(c["sekunden"] / n),
        })
    write("zellen.json", zellen)

    write("mischungen.json", [{
        "variante": m["variante"], "spieler": m["gruppe"], "mischung": m["mischung"],
        "gruppenart": MIXES.get(m["mischung"], m["mischung"]), "laeufe": m["laeufe"], "siege": m["siege"],
        "siegquote": round(m["siege"] / max(1, m["laeufe"]), 4),
    } for m in s["mischungen"]])

    rows = []
    for u in s["unterklassen"]:
        if u["unterklasse"] not in SUBS:
            continue   # aeltere Laeufe: ein Bot ohne gelernte Unterklasse stand unter seinem Namen ("Bot03")
        name, klasse = SUBS[u["unterklasse"]]
        n = max(1, u["laeufe"])
        rows.append({
            "variante": u["variante"], "unterklasse": u["unterklasse"], "name": name, "klasse": klasse,
            "laeufe": u["laeufe"], "siegquote": round(u["siege"] / n, 4),
            "schaden_je_lauf": round(u["schaden"] / n), "erhalten_je_lauf": round(u["erhalten"] / n),
            "heilung_je_lauf": round(u["heilung"] / n), "tode_je_lauf": round(u["tode"] / n, 2),
        })
    write("unterklassen.json", rows)

    total = {}
    for g in s.get("gegner", []):
        total[g["variante"]] = total.get(g["variante"], 0.0) + g["schaden"]
    foes = []
    for g in s.get("gegner", []):
        if g["schaden"] <= 0:
            continue
        short = g["typ"].split(":")[-1]
        foes.append({
            "variante": g["variante"], "typ": g["typ"], "name": FOES.get(short, short),
            "schaden": round(g["schaden"]), "anteil": round(g["schaden"] / max(1e-9, total[g["variante"]]), 4),
            "gespawnt": g["gespawnt"],
        })
    write("gegner.json", foes)

    write("lauf.json", [{
        "lauf": a.lauf, "datum": datetime.date.today().isoformat(), "testnaechte": s["laeufe"],
        "boden": "Kopie deiner Burg" if a.burg == "kopie" else "Standard-Burg",
        "varianten": len({z["variante"] for z in zellen}),
    }])
    print("Dashboard-Dateien in %s: %d Zellen, %d Unterklassen-Zeilen, %d Gegner" % (a.out, len(zellen), len(rows), len(foes)))


if __name__ == "__main__":
    main()
