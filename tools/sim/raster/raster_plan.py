#!/usr/bin/env python3
"""Tor 6.1 / 6.3 / 6.5: Szenario-Dateien fuer die Team-Sim (Token nachspiel=<datei>), mit festen Seeds.

Aufruf:
  raster_plan.py raster     [--ausgabe tools/sim/raster/raster.json] [--seed 20261109] [--stufen 1-25] [--gruppen 1-10]
                            [--n-pro-zelle 8] [--p-ereignis 0.5] [--p-boss 0.2] [--teil 1/2] [--wahl-hinweis]
  raster_plan.py ereignisse [--ausgabe tools/sim/raster/ereignisse.json] [--nur netherriss,glutkoloss,blutmond | --alle]
                            [--n 140] [--stufen 1-25] [--gruppen 4-10] [--seed 20261110]
  raster_plan.py paartausch --klasse swift --gegen heavy,bolter [--n 140] [--ausgabe .../paartausch.json]

raster: je Zelle (Stufe x Gruppengroesse) --n-pro-zelle Naechte mit zufaelligem Team (keine Unterklasse doppelt),
  Ereignis und Boss als eigene Faktoren (unabhaengig gezogen), Bot-Koennen 0,5/0,7/0,9 im Verhaeltnis 1:2:1 (je Zelle
  gleichmaessig gemischt). Die Reihenfolge ist gemischt, damit die CI-Teile (jeder k-te Lauf) gleich lange laufen.
ereignisse: dieselben --n Zellen (Stufe, Gruppe, Team, Koennen) einmal ohne Inhalt ("basis") und je Inhalt einmal mit
  ihm: gepaarter Vergleich. paartausch: dieselbe Zelle mit Klasse X und mit X durch Y ersetzt.
"""
import argparse
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rastermath import BOSSE, EREIGNISSE, UNTERKLASSEN  # noqa: E402

ALIAS = {"netherriss": ("ereignis", "NETHER_RIFT"), "blutmond": ("ereignis", "BLOOD_MOON"),
         "glutkoloss": ("boss", "ember_colossus")}
SKILLS = [0.40, 0.55, 0.55, 0.70]  # 1:2:1 um den geeichten Wert 0,55 (Tor 8.1); 6.1 hatte 0,5/0,7/0,9


def spanne(text):
    a, _, b = text.partition("-")
    return list(range(int(a), int(b or a) + 1))


def zelle_skills(rng, count):
    """Bot-Koennen 1:2:1, gleichmaessig ueber die Zelle gemischt."""
    out = []
    while len(out) < count:
        block = list(SKILLS)
        rng.shuffle(block)
        out += block
    return out[:count]


def team(rng, n):
    return rng.sample(UNTERKLASSEN, n)  # keine Unterklasse doppelt (wie echte Spieler, bis 10)


def raster(a):
    rng = random.Random(a.seed)
    nachten = []
    stufen, gruppen = spanne(a.stufen), spanne(a.gruppen)
    for L in stufen:
        for g in gruppen:
            for k, sk in enumerate(zelle_skills(rng, a.n_pro_zelle)):
                ereignis = rng.choice(EREIGNISSE) if rng.random() < a.p_ereignis else ""
                boss = rng.choice(BOSSE) if rng.random() < a.p_boss else ""
                nachten.append({"id": "r%02d_g%02d_%03d" % (L, g, k), "stufe": L, "gruppe": team(rng, g),
                                "ereignis": ereignis, "boss": boss, "punkte": -1, "koennen": sk})
    rng.shuffle(nachten)
    if a.teil:
        i, _, k = a.teil.partition("/")
        nachten = [x for j, x in enumerate(nachten) if j % int(k) == int(i) - 1]
    return nachten


def zellen(rng, stufen, gruppen, n):
    cells = []
    for j in range(n):
        L = stufen[j % len(stufen)]
        g = rng.choice(gruppen)
        cells.append({"stufe": L, "gruppe": team(rng, g), "koennen": rng.choice(SKILLS)})
    rng.shuffle(cells)
    return cells


def ereignisse(a):
    rng = random.Random(a.seed)
    cells = zellen(rng, spanne(a.stufen), spanne(a.gruppen), a.n)
    inhalte = []
    if a.alle:
        inhalte = [("ereignis", e) for e in EREIGNISSE] + [("boss", b) for b in BOSSE]
    else:
        for name in (a.nur or "netherriss,glutkoloss,blutmond").split(","):
            inhalte.append(ALIAS[name.strip()] if name.strip() in ALIAS else
                           (("ereignis", name.strip().upper()) if name.strip().upper() in EREIGNISSE else ("boss", name.strip())))
    nachten = []
    for k, c in enumerate(cells):
        nachten.append(dict(c, id="e_basis_%03d" % k, ereignis="", boss="", punkte=-1))
        for art, wert in inhalte:
            n = dict(c, id="e_%s_%03d" % (wert.lower(), k), ereignis="", boss="", punkte=-1)
            n[art] = wert
            nachten.append(n)
    rng.shuffle(nachten)
    return nachten


def paartausch(a):
    rng = random.Random(a.seed)
    stufen, gruppen = spanne(a.stufen), spanne(a.gruppen)
    nachten = []
    for k in range(a.n):
        L = stufen[k % len(stufen)]
        g = rng.choice(gruppen)
        rest = [u for u in UNTERKLASSEN if u != a.klasse]
        andere = rng.sample(rest, g - 1)
        sk = rng.choice(SKILLS)
        nachten.append({"id": "p_%s_%03d" % (a.klasse, k), "stufe": L, "gruppe": [a.klasse] + andere, "ereignis": "",
                        "boss": "", "punkte": -1, "koennen": sk})
        for y in a.gegen.split(","):
            y = y.strip()
            if y in andere:
                continue  # Tausch waere doppelt: dieses Paar entfaellt
            nachten.append({"id": "p_%s_%s_%03d" % (a.klasse, y, k), "stufe": L, "gruppe": [y] + andere, "ereignis": "",
                            "boss": "", "punkte": -1, "koennen": sk})
    rng.shuffle(nachten)
    return nachten


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("modus", choices=["raster", "ereignisse", "paartausch"])
    ap.add_argument("--ausgabe")
    ap.add_argument("--seed", type=int)
    ap.add_argument("--stufen", default="1-25")
    ap.add_argument("--gruppen")
    ap.add_argument("--n-pro-zelle", dest="n_pro_zelle", type=int, default=8)
    ap.add_argument("--p-ereignis", dest="p_ereignis", type=float, default=0.5)
    ap.add_argument("--p-boss", dest="p_boss", type=float, default=0.2)
    ap.add_argument("--teil", help="z. B. 1/2: nur dieser Teil (zwei Pushes statt eines Jobs von 5 Stunden)")
    ap.add_argument("--n", type=int, default=140)
    ap.add_argument("--nur")
    ap.add_argument("--alle", action="store_true")
    ap.add_argument("--klasse", default="swift")
    ap.add_argument("--gegen", default="heavy,bolter,gunner")
    a = ap.parse_args(argv)
    here = os.path.dirname(os.path.abspath(__file__))
    if a.modus == "raster":
        a.seed = a.seed or 20261109
        a.gruppen = a.gruppen or "1-10"
        out = raster(a)
    elif a.modus == "ereignisse":
        a.seed = a.seed or 20261110
        a.gruppen = a.gruppen or "4-10"
        out = ereignisse(a)
    else:
        a.seed = a.seed or 20261111
        a.gruppen = a.gruppen or "4-10"
        out = paartausch(a)
    pfad = a.ausgabe or os.path.join(here, {"raster": "raster.json", "ereignisse": "ereignisse.json",
                                             "paartausch": "paartausch.json"}[a.modus])
    os.makedirs(os.path.dirname(os.path.abspath(pfad)), exist_ok=True)
    with open(pfad, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"nacht": out}, fh, ensure_ascii=False, indent=None, separators=(",", ":"))
        fh.write("\n")
    print("%s: %d Nächte nach %s (Seed %d)" % (a.modus, len(out), pfad, a.seed))
    return out


if __name__ == "__main__":
    main()
