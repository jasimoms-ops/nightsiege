#!/usr/bin/env python3
"""Tor 6.3: Ereignisse und Bosse einzeln eichen (Ziel: Abweichung vom Normalfall hoechstens +-3 Punkte, +-0,15 logit).

Aufruf: ereignisse_auswertung.py <daten> [--variante aktuell] [--json ausgabe.json]
Eingabe: Lauf mit tools/sim/raster/ereignisse.json (raster_plan.py ereignisse). Szenario-Ids e_basis_<k> (ohne Inhalt) und
e_<inhalt>_<k> haben dieselbe Zelle k (Stufe, Gruppe, Team, Koennen): gepaarter Vergleich.
Je Inhalt: n, Quote mit Wilson, Quote der gepaarten Basis, Differenz in Punkten mit 95-%-Intervall (gepaart, Normalnaeherung),
Koeffizient im gepoolten Modell (logit +- 95 %). Urteil: ok = Intervall liegt in +-3 Punkten; Abweichung = Intervall
liegt ganz ausserhalb von +-3; unklar = Punktwert in +-3 aber Intervall breiter (mehr Naechte noetig). Mit 140 Naechten ist
das Intervall rund +-6 bis +-8 Punkte breit: "ok" ist daher nur mit mehr Naechten oder starken Effekten moeglich;
die Tabelle nennt die noetige Zahl Naechte fuer +-3 Punkte.
"""
import argparse
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rastermath as rm  # noqa: E402

GRENZE = 0.03
LOGIT_GRENZE = 0.15


def zelle_von(mix):
    m = re.match(r"nacht_e_(.+)_(\d+)$", mix)
    return (m.group(1), int(m.group(2))) if m else (None, None)


def vergleich(naechte):
    basis, inhalte = {}, {}
    for x in naechte:
        name, k = zelle_von(x["mix"])
        if name is None:
            continue
        if name == "basis":
            basis[k] = x
        else:
            inhalte.setdefault(name, {})[k] = x
    erg = []
    for name in sorted(inhalte):
        paare = [(inhalte[name][k]["sieg"], basis[k]["sieg"]) for k in inhalte[name] if k in basis]
        n = len(inhalte[name])
        kk = sum(x["sieg"] for x in inhalte[name].values())
        p, lo, hi = rm.wilson(kk, n)
        zeile = {"inhalt": name, "n": n, "paare": len(paare), "quote": p, "lo": lo, "hi": hi}
        if len(paare) >= 2:
            d = [a - b for a, b in paare]
            mean = sum(d) / len(d)
            var = sum((v - mean) ** 2 for v in d) / (len(d) - 1)
            se = math.sqrt(var / len(d))
            zeile.update({"basis": sum(b for _, b in paare) / len(paare), "diff": mean, "diff_lo": mean - rm.Z95 * se,
                          "diff_hi": mean + rm.Z95 * se, "se": se})
            # Zahl Naechte fuer +-3 Punkte (95 %)
            zeile["n_fuer_3"] = int(math.ceil((rm.Z95 * math.sqrt(max(var, 1e-6)) / GRENZE) ** 2))
            if zeile["diff_lo"] >= -GRENZE and zeile["diff_hi"] <= GRENZE:
                urteil = "ok"
            elif zeile["diff_lo"] > GRENZE or zeile["diff_hi"] < -GRENZE:
                urteil = "Abweichung"
            else:
                urteil = "unklar" if abs(mean) <= GRENZE else "Abweichung (Punktwert), Intervall breit"
            zeile["urteil"] = urteil
        erg.append(zeile)
    return erg


def modell_koeffizienten(naechte):
    sub = [x for x in naechte if zelle_von(x["mix"])[0] is not None]
    if len(sub) < 20:
        return {}
    mod = rm.modell(sub, True)
    out = {}
    for nm, b, s in zip(mod["namen"], mod["beta"], mod["se"]):
        if nm.startswith("ereignis_") or nm.startswith("boss_"):
            out[nm.split("_", 1)[1].lower()] = (b, rm.Z95 * s)
    return out


def auswerten(naechte, info):
    z = [rm.kopf(naechte, info), "",
         "Inhalt (Ziel: Abweichung vom Normalfall hoechstens +-3 Punkte = +-0,15 logit; gepaart ueber dieselben Zellen)"]
    erg = vergleich(naechte)
    koef = modell_koeffizienten(naechte)
    z.append("Inhalt           n (Paare)  Quote [Wilson]       Basis    Diff Punkte [95 %]      logit +-     n fuer +-3   Urteil")
    for e in erg:
        k = koef.get(e["inhalt"].lower())
        z.append("%-16s %3d (%3d)  %4.1f %% [%4.1f-%4.1f]  %s  %s  %s  %s  %s%s" % (
            e["inhalt"], e["n"], e["paare"], 100 * e["quote"], 100 * e["lo"], 100 * e["hi"],
            ("%4.1f %%" % (100 * e["basis"])) if "basis" in e else "   -  ",
            ("%+5.1f [%+5.1f..%+5.1f]" % (100 * e["diff"], 100 * e["diff_lo"], 100 * e["diff_hi"])) if "diff" in e else "         -          ",
            ("%+5.2f +-%.2f" % k) if k else "      -      ",
            ("%5d" % e["n_fuer_3"]) if "n_fuer_3" in e else "    -",
            e.get("urteil", "-"), "  (n<140: unsicher)" if e["n"] < 140 else ""))
    z.append("Ridge zieht die Koeffizienten Richtung 0; der gepaarte Punktwert ist der ehrlichere Wert.")
    return z, {"inhalte": erg, "koeffizienten": {k: list(v) for k, v in koef.items()}}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("quelle")
    ap.add_argument("--variante", default="aktuell")
    ap.add_argument("--json")
    a = ap.parse_args(argv)
    naechte, info = rm.lade(a.quelle, a.variante)
    zeilen, data = auswerten(naechte, info)
    print("\n".join(zeilen))
    if a.json:
        json.dump(data, open(a.json, "w", encoding="utf-8"), indent=1)
    return data


if __name__ == "__main__":
    main()
