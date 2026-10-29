#!/usr/bin/env python3
"""Eichung der Team-Bots: vergleicht Bot-Varianten mit echten Naechten aus dem Logger.

Aufruf: eichung.py <sim-stats-ordner> <echt-stats-ordner> <ausgabe.json> [--team "<[team …]-Einstellungen>"]

Je Variante und fuer die echten Spieler, getrennt nach allein (1 Spieler) und Gruppe (2+), Stufe 1–15,
ohne Bossnaechte und abgebrochene Naechte:
  schaden_je_min   Schaden, den ein Spieler je Minute austeilt
  erhalten_je_min  Schaden, den er je Minute einsteckt
  tod_anteil       Anteil der Spieler-Naechte mit mindestens einem Tod
  ueberlebt_s      Median der Nachtdauer in Naechten mit Tod (solo: wie lange man durchhielt)
  fern_anteil      Anteil des eingesteckten Schadens von Fernkaempfern (Skelette, Pluenderer, Magier …)
abstand: mittlere |log(Bot/echt)| ueber die vier Werte (0 = gleich); die kleinste je Gruppe passt am besten.
"""
import argparse
import collections
import json
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dashboard_data import knobs_from_team, koennen_of, variant_label  # noqa: E402

RANGED = {"skeleton", "stray", "bogged", "parched", "pillager", "archer", "witch", "evoker", "illusioner",
          "blaze", "breeze", "wildfire", "iceologer", "ghast", "vex", "phantom"}


def collect(folder, sim):
    """{(variante, art): [per player-night (schaden/min, erhalten/min, tod, dauer, fern, gesamt)]}"""
    out = collections.defaultdict(list)
    for f in sorted(os.listdir(folder)):
        if not f.endswith(".json") or "dungeon" in f or "heerlager" in f:
            continue
        try:
            n = json.load(open(os.path.join(folder, f), encoding="utf-8"))
        except (OSError, ValueError):
            continue
        label = n.get("sim") or ""
        if bool(label) != sim:
            continue
        if n.get("ergebnis") == "abgebrochen" or n.get("boss") or (n.get("dauerSekunden") or 0) < 15:
            continue
        level = int(n.get("stufe") or 0)
        if level < 1 or level > 15:
            continue
        players = n.get("spieler") or {}
        kind = "allein" if len(players) <= 1 else "gruppe"
        variant = label.split("|")[0] if sim else "echt"
        secs = max(1, int(n.get("dauerSekunden") or 1))
        per = collections.defaultdict(lambda: [0.0, 0.0, 0, 0.0, 0.0])
        for w in n.get("wellen") or []:
            for name, pw in (w.get("spieler") or {}).items():
                p = per[name]
                p[0] += pw.get("schaden", 0.0)
                p[1] += pw.get("erhalten", 0.0)
                p[2] += pw.get("tode", 0)
                for src, v in (pw.get("erhaltenNachGegner") or {}).items():
                    p[4] += v                       # all of it
                    if src.split(":")[-1] in RANGED:
                        p[3] += v                   # from ranged attackers
        for d, e, t, fern, gesamt in per.values():
            out[(variant, kind)].append((d * 60 / secs, e * 60 / secs, t > 0, secs, fern, gesamt))
    return out


def summary(rows):
    died = [r[3] for r in rows if r[2]]
    fern = sum(r[4] for r in rows)
    total = sum(r[5] for r in rows) or 1.0
    return {
        "n": len(rows),
        "schaden_je_min": round(statistics.median(r[0] for r in rows)),
        "erhalten_je_min": round(statistics.median(r[1] for r in rows)),
        "tod_anteil": round(sum(1 for r in rows if r[2]) / len(rows), 3),
        "ueberlebt_s": round(statistics.median(died)) if died else None,
        "fern_anteil": round(fern / total, 3),
    }


def distance(bot, real):
    terms = []
    for key in ("schaden_je_min", "erhalten_je_min", "ueberlebt_s", "fern_anteil"):
        b, r = bot.get(key), real.get(key)
        if b and r:
            terms.append(abs(math.log(max(b, 1e-3) / max(r, 1e-3))))
    return round(sum(terms) / len(terms), 3) if terms else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sim")
    ap.add_argument("echt")
    ap.add_argument("out")
    ap.add_argument("--team", default="")
    a = ap.parse_args()
    common, team_variants = knobs_from_team(a.team)
    sim = collect(a.sim, True)
    real = collect(a.echt, False)
    rows = []
    for kind in ("allein", "gruppe"):
        r = real.get(("echt", kind))
        rs = summary(r) if r else None
        if rs:
            rows.append(dict(quelle="echt", variante="echt", name="Ihr (echte Nächte)", art=kind, koennen=None, wagemut=None,
                             abstand=0.0, **rs))
        for (variant, k), xs in sorted(sim.items()):
            if k != kind:
                continue
            s = summary(xs)
            kv = koennen_of(variant, {}, "koennen", common, team_variants)
            wv = koennen_of(variant, {}, "wagemut", common, team_variants)
            rows.append(dict(quelle="sim", variante=variant, name="Bots: " + variant_label(kv, wv), art=kind,
                             koennen=kv, wagemut=wv, abstand=distance(s, rs) if rs else None, **s))
    with open(a.out, "w", encoding="utf-8") as fh:
        json.dump(rows, fh, ensure_ascii=False, separators=(",", ":"))
    for kind in ("allein", "gruppe"):
        cand = [r for r in rows if r["art"] == kind and r["quelle"] == "sim" and r["abstand"] is not None]
        for r in [x for x in rows if x["art"] == kind]:
            print("%-6s %-42s n=%3d  Schaden/min %5d  eingesteckt/min %4d  Tod %3.0f%%  überlebt %4s s  Fern %3.0f%%  Abstand %s"
                  % (kind, r["name"], r["n"], r["schaden_je_min"], r["erhalten_je_min"], 100 * r["tod_anteil"],
                     r["ueberlebt_s"], 100 * r["fern_anteil"], r["abstand"]))
        if cand:
            best = min(cand, key=lambda r: r["abstand"])
            print("  am besten (%s): %s" % (kind, best["name"]))


if __name__ == "__main__":
    main()
