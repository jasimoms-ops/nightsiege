#!/usr/bin/env python3
"""Tor 6.0: aus echten Nachtdateien (night_*.json des Loggers) Nachspiel-Szenarien bauen.

Aufruf: nacht_zu_szenario.py <ordner-oder-dateien…> [--ausgabe tools/sim/eichung/szenarien.json] [--neu] [--praefix r]
  Liest night_*, testnacht_* und spieltest_*.json; jede Gruppennacht (keine Sim-Nacht, kein Dungeon/Heerlager, >= 2 Spieler), schreibt je Nacht ein Szenario
  (Stufe, Besetzung als Unterklassen, Ereignis, Boss, Punkte -1, echt = Ergebnis/Dauer/Tode) und haengt es an die
  Ausgabedatei an (vorhandene ids bleiben; --neu ersetzt die Datei). Spielernamen kommen NIE in die Ausgabe:
  nur Unterklassen, die Szenario-id ist <praefix><Nummer>_<Stufe>, echt_datei der Dateiname (night_<Zeit>.json).
Danach nachspielen (CI): Commit-Nachricht auf balance-run-eichung:
  [team nachspiel=tools/sim/eichung/szenarien.json N60 V=mensch:mensch=1] [shards 10]
und vergleichen: eichung_vergleich.py <szenarien> <dist>/stats <echt-ordner> --variante mensch
"""
import argparse
import json
import os
import sys


def nights(paths):
    for p in paths:
        files = []
        if os.path.isdir(p):
            for root, _, fs in os.walk(p):
                files += [os.path.join(root, f) for f in sorted(fs)]
        else:
            files.append(p)
        for f in files:
            base = os.path.basename(f)
            if not (base.startswith(("night_", "testnacht_", "spieltest_")) and base.endswith(".json")):
                continue
            try:
                with open(f, encoding="utf-8") as fh:
                    yield base, json.load(fh)
            except (OSError, ValueError):
                continue


def szenario(base, n, sid):
    """Eine echte Nacht -> Szenario-Dict oder None (Sim-Nacht, zu wenige Spieler, nicht abgeschlossen)."""
    if n.get("sim") or not n.get("abgeschlossen", True):
        return None
    subs = [info.get("unterklasse") or "" for info in (n.get("spieler") or {}).values()]
    luecken = []
    if any(not s for s in subs):
        luecken.append("Spieler ohne Unterklasse im Logger (verworfen)")
    subs = [s for s in subs if s]
    if len(subs) < 2:
        return None
    erg = n.get("ergebnis") or ""
    echt = {"ergebnis": "gewonnen" if erg in ("gewonnen", "test_gehalten") else "verloren",
            "sekunden": n.get("dauerSekunden", 0), "tode": n.get("tode", 0), "kills": n.get("kills", 0)}
    if not n.get("ereignis"):
        luecken.append("kein Ereignis (Logger: leer)")
    # punkteImProfil (Logger ab Tor 7): ausgegebene Punkte je Spieler; Szenario nimmt den Median der Werte > 0
    pts = sorted(i.get("punkteImProfil", 0) for i in (n.get("spieler") or {}).values() if i.get("punkteImProfil", 0) > 0)
    punkte = pts[len(pts) // 2] if pts else -1
    if punkte < 0:
        luecken.append("Punkte-/Kit-Stand: Stufenstand angenommen")
    if n.get("ersterTodSekunde", -1) >= 0:
        echt["ersterTodSekunde"] = n["ersterTodSekunde"]
        echt["ersterTodWelle"] = n.get("ersterTodWelle", -1)
    echt["spieltest"] = bool(n.get("spieltest"))
    luecken.append("Burg: Sim-Burg statt der echten")
    return {"id": sid, "stufe": n.get("stufe", 0), "gruppe": subs, "ereignis": (n.get("ereignis") or "").upper(),
            "boss": n.get("boss") or "", "punkte": punkte, "echt_datei": base, "echt": echt, "luecken": luecken}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("quellen", nargs="+")
    ap.add_argument("--ausgabe", default="tools/sim/eichung/szenarien.json")
    ap.add_argument("--neu", action="store_true")
    ap.add_argument("--praefix", default="r")
    a = ap.parse_args()
    doc = {"nacht": []}
    if not a.neu and os.path.exists(a.ausgabe):
        with open(a.ausgabe, encoding="utf-8") as fh:
            doc = json.load(fh)
    have = {s["id"] for s in doc["nacht"]} | {s.get("echt_datei") for s in doc["nacht"]}
    added = 0
    for base, n in nights(a.quellen):
        if base in have:
            continue
        sid = "%s%d_%d" % (a.praefix, len(doc["nacht"]) + 1, n.get("stufe", 0))
        s = szenario(base, n, sid)
        if s:
            doc["nacht"].append(s)
            added += 1
    with open(a.ausgabe, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(doc, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print("%d neue Szenarien, %d gesamt -> %s" % (added, len(doc["nacht"]), a.ausgabe))
    return 0


if __name__ == "__main__":
    sys.exit(main())
