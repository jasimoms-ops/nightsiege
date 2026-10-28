#!/usr/bin/env python3
"""Auswertung des Night-Siege-Statistik-Loggers (28.10.2026).

Liest alle Dateien aus <welt>/nightsiege/stats/ (night_*.json, dungeon_*.json,
heerlager_*.json, kisten.jsonl) und schreibt Tabellen als CSV (Semikolon, deutsches
Excel) in einen Ausgabeordner:

  siege_je_stufe.csv     Nächte, Siege, Siegquote je Weltstufe
  klassen.csv            Schaden, Heilung, Kills, Tode je Unterklasse (je Spieler-Welle gemittelt)
  schadensquellen.csv    Schaden nach Quelle (nah:…, fern:…, turm:…, begleiter …)
  gegner.csv             gespawnt, getötet, Überlebenszeit, Schaden an Spielern je Gegnertyp
  tuerme.csv             Schaden und Kills bemannt/unbemannt je Turmart
  dungeons.csv           ein Versuch je Zeile
  heerlager.csv          ein Versuch je Zeile
  kisten.csv             Seltenheiten je Kistenart (gezählt und in Prozent)

Aufruf: python3 auswertung.py <stats-ordner> [ausgabe-ordner] [--mit-tests] [--version <build>]
Testnächte (testnacht_*.json) bleiben ohne --mit-tests draußen.
"""
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path


def lesen(ordner, mit_tests, nur_build):
    naechte, dungeons, lager, kisten = [], [], [], []
    for f in sorted(Path(ordner).glob("*.json")):
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        if nur_build and d.get("build") != nur_build:
            continue
        art = d.get("art")
        if art == "nacht":
            if d.get("test") and not mit_tests:
                continue
            naechte.append(d)
        elif art == "dungeon":
            dungeons.append(d)
        elif art == "heerlager":
            lager.append(d)
    k = Path(ordner) / "kisten.jsonl"
    if k.exists():
        for zeile in k.read_text(encoding="utf-8").splitlines():
            try:
                d = json.loads(zeile)
            except ValueError:
                continue
            if not nur_build or d.get("build") == nur_build:
                kisten.append(d)
    return naechte, dungeons, lager, kisten


def schreiben(pfad, kopf, zeilen):
    with open(pfad, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(kopf)
        for z in zeilen:
            w.writerow([str(x).replace(".", ",") if isinstance(x, float) else x for x in z])


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        print(__doc__)
        return 1
    mit_tests = "--mit-tests" in sys.argv
    nur_build = None
    if "--version" in sys.argv:
        nur_build = sys.argv[sys.argv.index("--version") + 1]
        args = [a for a in args if a != nur_build]
    ordner = Path(args[0])
    aus = Path(args[1]) if len(args) > 1 else ordner / "auswertung"
    aus.mkdir(parents=True, exist_ok=True)
    naechte, dungeons, lager, kisten = lesen(ordner, mit_tests, nur_build)

    # Siege je Stufe
    stufen = defaultdict(lambda: [0, 0])
    for n in naechte:
        if not n.get("abgeschlossen"):
            continue
        s = stufen[n.get("stufe", 0)]
        s[0] += 1
        if n.get("ergebnis") in ("gewonnen", "test_gehalten"):
            s[1] += 1
    schreiben(aus / "siege_je_stufe.csv", ["stufe", "naechte", "siege", "siegquote"],
              [[k, v[0], v[1], round(v[1] / v[0], 3) if v[0] else 0.0] for k, v in sorted(stufen.items())])

    # Klassen und Schadensquellen
    klassen = defaultdict(lambda: defaultdict(float))
    quellen = defaultdict(float)
    for n in naechte:
        wer = {name: (s.get("unterklasse") or s.get("klasse") or "?") for name, s in n.get("spieler", {}).items()}
        for welle in n.get("wellen", []):
            for name, w in welle.get("spieler", {}).items():
                k = klassen[wer.get(name, "?")]
                k["spieler_wellen"] += 1
                for feld in ("schaden", "erhalten", "heilungGegeben", "heilungErhalten", "kills", "assists", "tode",
                             "drohneSekunden", "turmSekunden"):
                    k[feld] += w.get(feld, 0)
                for q, v in w.get("schadenNachQuelle", {}).items():
                    quellen[q] += v
    zeilen = []
    for name, k in sorted(klassen.items()):
        n = max(1.0, k["spieler_wellen"])
        zeilen.append([name, int(k["spieler_wellen"])] + [round(k[f] / n, 2) for f in
                      ("schaden", "erhalten", "heilungGegeben", "heilungErhalten", "kills", "assists", "tode")])
    schreiben(aus / "klassen.csv", ["unterklasse", "spieler_wellen", "schaden_je_welle", "erhalten_je_welle",
                                     "heilung_gegeben_je_welle", "heilung_erhalten_je_welle", "kills_je_welle",
                                     "assists_je_welle", "tode_je_welle"], zeilen)
    gesamt = sum(quellen.values()) or 1.0
    schreiben(aus / "schadensquellen.csv", ["quelle", "schaden", "anteil"],
              [[q, round(v, 1), round(v / gesamt, 3)] for q, v in sorted(quellen.items(), key=lambda x: -x[1])])

    # Gegner
    gegner = defaultdict(lambda: defaultdict(float))
    for n in naechte:
        for typ, g in n.get("gegner", {}).items():
            z = gegner[typ]
            z["gespawnt"] += g.get("gespawnt", 0)
            z["getoetet"] += g.get("getoetet", 0)
            z["ueberlebt"] += g.get("ueberlebtSekundenSumme", 0)
            z["schaden"] += g.get("schadenAnSpielern", 0)
    schreiben(aus / "gegner.csv", ["typ", "gespawnt", "getoetet", "ueberlebt_s_schnitt", "schaden_an_spielern",
                                    "schaden_je_gespawntem"],
              [[t, int(z["gespawnt"]), int(z["getoetet"]), round(z["ueberlebt"] / z["getoetet"], 1) if z["getoetet"] else 0.0,
                round(z["schaden"], 1), round(z["schaden"] / z["gespawnt"], 2) if z["gespawnt"] else 0.0]
               for t, z in sorted(gegner.items(), key=lambda x: -x[1]["schaden"])])

    # Türme
    tuerme = defaultdict(lambda: defaultdict(float))
    for n in naechte:
        for t in n.get("tuerme", {}).values():
            z = tuerme[t.get("art", "?")]
            for feld in ("schaden", "schadenBemannt", "kills", "killsBemannt", "sekundenBemannt"):
                z[feld] += t.get(feld, 0)
    schreiben(aus / "tuerme.csv", ["art", "schaden", "davon_bemannt", "kills", "davon_bemannt", "sekunden_bemannt"],
              [[a, round(z["schaden"], 1), round(z["schadenBemannt"], 1), int(z["kills"]), int(z["killsBemannt"]),
                int(z["sekundenBemannt"])] for a, z in sorted(tuerme.items())])

    # Dungeons und Heerlager
    def versuche(liste):
        return [[d.get("start"), d.get("typ"), d.get("stufe"), d.get("versuch"), len(d.get("spieler", [])),
                 " ".join((s.get("unterklasse") or s.get("klasse") or "?") for s in d.get("spieler", [])),
                 d.get("raeumeGeraeumt"), d.get("raeume"), d.get("wellenErreicht"), d.get("championErreicht"),
                 d.get("tode"), d.get("dauerSekunden"), d.get("ergebnis")] for d in liste]
    kopf = ["start", "typ", "stufe", "versuch", "spieler", "klassen", "raeume_geraeumt", "raeume", "wellen",
            "champion", "tode", "dauer_s", "ergebnis"]
    schreiben(aus / "dungeons.csv", kopf, versuche(dungeons))
    schreiben(aus / "heerlager.csv", kopf, versuche(lager))

    # Kisten
    kz = defaultdict(lambda: defaultdict(int))
    for k in kisten:
        kz[k.get("gerollteArt", "?")][k.get("seltenheit", "?")] += 1
    seltenheiten = sorted({s for z in kz.values() for s in z})
    zeilen = []
    for art, z in sorted(kz.items()):
        summe = sum(z.values()) or 1
        zeilen.append([art, summe] + [z.get(s, 0) for s in seltenheiten] + [round(z.get(s, 0) / summe, 3) for s in seltenheiten])
    schreiben(aus / "kisten.csv", ["kistenart", "geoeffnet"] + seltenheiten + [s + "_anteil" for s in seltenheiten], zeilen)

    print(f"{len(naechte)} Nächte, {len(dungeons)} Dungeon-Versuche, {len(lager)} Heerlager-Versuche, "
          f"{len(kisten)} Kisten -> {aus}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
