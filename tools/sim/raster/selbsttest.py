#!/usr/bin/env python3
"""Selbsttest der Tor-6-Werkzeuge auf erfundenen Daten (kein Sim-Lauf noetig).

Aufruf: /c/Users/simon/AppData/Local/Programs/Python/Python315/python.exe -I tools/sim/raster/selbsttest.py
Baut aus raster_plan.py Szenarien, wuerfelt Ergebnisse nach einem bekannten Modell (swift -0,5 logit, Netherriss -0,6,
Stufe 6 und 11 Sprung +0,5, Knoten "k_a" +0,4), schreibt eine teamsim.csv im Format der Sim und prueft, dass
raster_auswertung, tabelle_regler, ereignisse_auswertung und klassen_ridge die Werte wiederfinden.
"""
import math
import os
import random
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ereignisse_auswertung as ea  # noqa: E402
import klassen_ridge as kr  # noqa: E402
import raster_auswertung as ra  # noqa: E402
import raster_plan as rp  # noqa: E402
import rastermath as rm  # noqa: E402
import tabelle_regler as tr  # noqa: E402

fehler = []


def pruefe(name, ok, detail=""):
    print(("ok     " if ok else "FEHLER ") + name + (" " + detail if detail else ""))
    if not ok:
        fehler.append(name)


def schreibe(ordner, nachten, wahrheit, rng, knoten=False):
    zeilen = []
    for x in nachten:
        L, g = x["stufe"], len(x["gruppe"])
        eta = 1.2 - 0.05 * (L - 13) + 0.5 * (L >= 6) + 0.5 * (L >= 11) - 0.8 * (L >= 20) - 0.12 * (10 - g) * 0.3
        eta += (x["koennen"] - 0.7) / 0.2 * 0.4 + wahrheit(x)
        sieg = 1 if rng.random() < rm.expit(eta) else 0
        mix = "nacht_" + x["id"]
        key = "aktuell;%d;%d;%s;0" % (L, g, mix)
        ev = x.get("ereignis") or "NONE"
        boss = x.get("boss") or "-"
        live = int(20 + 6 * L + rng.random() * 10)
        mspt = 10 + 0.5 * live + rng.random() * 3
        frueh = 1 if (not sieg and rng.random() < 0.3) else 0
        # LAUF-Zeile exakt im Sim-Format: art;variante;stufe;gruppe;mix;lauf;ergebnis;gehalten;sieg;kills;ziel;sek;tode;subs
        zeilen.append("LAUF;aktuell;%d;%d;%s;0;%s;%d;%d;%d;100;600;0;%s" % (
            L, g, mix, "test_gehalten" if sieg else "test_verloren", sieg, sieg, 50 if sieg else 30, "+".join(x["gruppe"])))
        zeilen.append("RASTER;%s;%s;%s;%.2f;%d;%.2f;%d;%d;0;600;%s" % (
            key, ev, boss, x["koennen"], sieg, mspt, live, frueh, "test_gehalten" if sieg else "test_verloren"))
        zeilen.append("TICK;%s;199;%d;%.2f;%.2f" % (key, live, mspt, mspt + 5))
        if knoten:
            for c in x["gruppe"][:2]:
                an = rng.random() < 0.5
                zeilen.append("KNOTEN;%s;%s;zufall;%s" % (key, c, "k_a" if an else "k_b"))
    os.makedirs(ordner, exist_ok=True)
    with open(os.path.join(ordner, "teamsim.csv"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(["art;variante;stufe;gruppe;mischung;lauf;ergebnis;gehalten;sieg;kills;ziel;sekunden;tode;unterklassen"] + zeilen) + "\n")


def haupt():
    rng = random.Random(7)
    tmp = tempfile.mkdtemp(prefix="t6_selbsttest_")

    # --- Planer
    plan = rp.main(["raster", "--ausgabe", os.path.join(tmp, "plan.json"), "--n-pro-zelle", "8"])
    pruefe("Raster hat 25 x 10 x 8 = 2000 Naechte", len(plan) == 2000, str(len(plan)))
    sk = [x["koennen"] for x in plan]
    pruefe("Koennen 1:2:1", abs(sk.count(0.4) / 2000 - 0.25) < 0.01 and abs(sk.count(0.55) / 2000 - 0.5) < 0.01)
    pruefe("Teams ohne doppelte Unterklasse", all(len(set(x["gruppe"])) == len(x["gruppe"]) for x in plan))
    pruefe("Solo und 10er dabei", {len(x["gruppe"]) for x in plan} == set(range(1, 11)))
    pruefe("Seed fest", rp.main(["raster", "--ausgabe", os.path.join(tmp, "p2.json")]) == plan)

    # --- Raster-Auswertung und Regler
    def wahrheit(x):
        w = 0.0
        if "swift" in x["gruppe"]:
            w -= 0.5
        if x.get("ereignis") == "NETHER_RIFT":
            w -= 0.6
        return w
    ordner = os.path.join(tmp, "raster")
    schreibe(ordner, plan, wahrheit, rng)
    naechte, info = rm.lade(ordner)
    pruefe("Einlesen: 2000 gueltig", info["gueltig"] == 2000, str(info))
    zeilen, data = ra.auswerten(naechte, info)
    pruefe("Auswertung nennt n und Wilson", any("n=" in z and "[" in z for z in zeilen))
    pruefe("Tickzeit-Deckel gefunden oder nicht erreicht", any("Deckel-Vorschlag" in z for z in zeilen))
    mod = rm.modell(naechte, True)
    b = dict(zip(mod["namen"], mod["beta"]))
    pruefe("Modell: Sprung s6 ~ +0,5", abs(b["s6"] - 0.5) < 0.7, "%.2f" % b["s6"])
    pruefe("Modell: Sprung s20 ~ -0,8", abs(b["s20"] + 0.8) < 0.45, "%.2f" % b["s20"])
    pruefe("Modell: Netherriss ~ -0,6", abs(b["ereignis_NETHER_RIFT"] + 0.6) < 0.3, "%.2f" % b["ereignis_NETHER_RIFT"])
    erg = tr.regler(naechte)
    pruefe("Regler: Faktoren in 0,85-1,15", all(0.85 - 1e-9 <= s["faktor"] <= 1.15 + 1e-9 for s in erg["stufen"]))
    pruefe("Regler: zu leichte Stufe (L6-10) bekommt Faktor > 1 oder Stopp",
           any(s["faktor"] > 1.0 or s["stopp"] for s in erg["stufen"] if 6 <= s["stufe"] <= 10))
    pruefe("Regler: Ziel 75 -> 65 im Band", abs(rm.ziel_kurve(6) - 0.75) < 1e-9 and abs(rm.ziel_kurve(10) - 0.65) < 1e-9)
    erg["knobs"] = tr.knobs(erg)
    pruefe("Regler: Sprung-/Gruppenfaktoren fuer Config (3 und 5 Werte)", len(erg["knobs"]["jump"]) == 3 and len(erg["knobs"]["group"]) == 5,
           erg["knobs"]["variante"])
    tr.text(erg, "x")
    # Rechnung aus der Recherche: 76 % -> +15 % (gedeckelt), 64 % -> -13 %
    pruefe("Formel A Beispiel 76 %", abs(tr.deckel(0.5 * (rm.logit(0.76) - rm.LOGIT_ZIEL), 0.15) - 1.15) < 1e-9)
    pruefe("Formel A Beispiel 64 %", abs(tr.deckel(0.5 * (rm.logit(0.64) - rm.LOGIT_ZIEL), 0.15) - 0.873) < 0.01)

    # --- Klassen (swift -0,5), Knoten
    ktab, mittel = kr.klassen_tabelle(kr.fit(naechte, 2.0, False)[0])
    swift = [r for r in ktab if r["klasse"] == "swift"][0]
    pruefe("Klassen: swift schwaecher als der Schnitt", swift["rel"] < -0.2, "%.2f +-%.2f" % (swift["rel"], swift["ci"]))
    pruefe("Klassen: swift als auffaellig markiert", swift["auffaellig"])
    pruefe("Klassen: andere nahe 0", sum(1 for r in ktab if r["klasse"] != "swift" and abs(r["rel"]) > 0.35) <= 1)

    def wahrheit2(x):
        return wahrheit(x)
    ordner2 = os.path.join(tmp, "knoten")
    schreibe(ordner2, plan, lambda x: wahrheit(x), rng, knoten=True)
    n2, i2 = rm.lade(ordner2)
    z2, d2 = kr.auswerten(n2, i2)
    pruefe("Wahlknoten: Tabelle da", "knoten" in d2 and len(d2["knoten"]) == 2)

    # --- Ereignisse (gepaart)
    ev = rp.main(["ereignisse", "--ausgabe", os.path.join(tmp, "ev.json"), "--n", "140"])
    pruefe("Ereignisse: 140 x (1 + 3) Naechte", len(ev) == 560, str(len(ev)))

    def wahr3(x):
        return -0.6 if x.get("ereignis") == "NETHER_RIFT" else (0.0 if not x.get("boss") else -0.1)
    ordner3 = os.path.join(tmp, "ereignisse")
    schreibe(ordner3, ev, wahr3, rng)
    n3, i3 = rm.lade(ordner3)
    z3, d3 = ea.auswerten(n3, i3)
    nether = [e for e in d3["inhalte"] if e["inhalt"] == "nether_rift"][0]
    pruefe("Ereignisse: Netherriss negativ (gepaart)", nether["diff"] < -0.03, "%+.1f Punkte" % (100 * nether["diff"]))
    pruefe("Ereignisse: 140 Paare", nether["paare"] == 140)
    pruefe("Ereignisse: Urteil gesetzt", "urteil" in nether, nether.get("urteil", ""))

    # --- Paartausch
    pt = rp.main(["paartausch", "--klasse", "swift", "--gegen", "heavy,bolter", "--ausgabe", os.path.join(tmp, "pt.json"), "--n", "140"])
    ordner4 = os.path.join(tmp, "pt")
    schreibe(ordner4, pt, wahrheit, rng)
    n4, i4 = rm.lade(ordner4)
    pa = kr.paartausch(n4)
    pruefe("Paartausch: Ersatz besser als swift", all(e["diff"] < 0.05 for e in pa) and any(e["diff"] < -0.03 for e in pa),
           ", ".join("%s %+.1f" % (e["y"], 100 * e["diff"]) for e in pa))

    # --- 0 Kills ungueltig
    kaputt = os.path.join(tmp, "kaputt")
    os.makedirs(kaputt)
    with open(os.path.join(kaputt, "teamsim.csv"), "w", encoding="utf-8") as fh:
        fh.write("LAUF;aktuell;5;2;nacht_a;0;test_verloren;0;0;0;100;60;2;medic+swift\n"
                 "RASTER;aktuell;5;2;nacht_a;0;NONE;-;0.70;0;12.0;3;0;2;60;test_verloren\n")
    n5, i5 = rm.lade(kaputt)
    pruefe("0 Kills = ungueltig", i5["gueltig"] == 0 and i5["ungueltig_0_kills"] == 1)
    print("\n%s" % ("ALLES OK" if not fehler else "FEHLER: " + ", ".join(fehler)))
    return 1 if fehler else 0


if __name__ == "__main__":
    sys.exit(haupt())
