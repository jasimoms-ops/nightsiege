#!/usr/bin/env python3
"""Tor 6.5 (und Vorbereitung 8.3b): Beitrag je Unterklasse und je Wahlknoten per Ridge-Modell aus den Raster-Daten.

Aufruf:
  klassen_ridge.py <daten> [--variante aktuell] [--lambda 2.0] [--json klassen.json] [--alle-knoten]
  klassen_ridge.py <daten> --paartausch   (Daten aus raster_plan.py paartausch: dieselbe Zelle mit X und mit Y statt X)

Modell: logit P(Sieg) = Grundmerkmale (Stufe, Kit-Spruenge, Spielerzahl, Koennen, Ereignis, Boss)
        + beta_c fuer jede Unterklasse c im Team (0/1, Ridge) + gamma_k fuer jeden Wahlknoten k, den ein Bot des Teams
        gelernt hat (Anzahl, Ridge; nur Knoten aus den KNOTEN-Zeilen, also bei "wahl=zufall" gleichverteilt, 20 % Standard-Pfad).
Ausgabe: beta (und gamma) in logit +- 95 %; Mitte abgezogen (Klasse im Verhaeltnis zum Schnitt der 20); Ziel je Klasse +-0,15 logit.
  auffaellig = |beta - Mittel| > 0,15 (sicher, wenn das Intervall 0 nicht enthaelt). Ausreisser dann per Paartausch pruefen.
  Ridge zieht Richtung 0 (Strafe lambda); Standardfehler grob. Mit ~2 000 Naechten und 5-6 Klassen je Team liegt der
  Standardfehler je Klasse bei etwa 0,1 logit: ein Intervall von +-0,2 ist normal, nicht jede Abweichung ist sicher.
"""
import argparse
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rastermath as rm  # noqa: E402

ZIEL_LOGIT = 0.15


def extra_klassen(nacht):
    namen = ["klasse_" + c for c in rm.UNTERKLASSEN]
    team = set(nacht["team"])
    return namen, [1.0 if c in team else 0.0 for c in rm.UNTERKLASSEN]


def knoten_liste(naechte):
    s = set()
    for x in naechte:
        for _, _, ids in x["knoten"]:
            s.update(ids)
    return sorted(s)


def fit(naechte, lam=2.0, mit_knoten=True):
    knoten = knoten_liste(naechte) if mit_knoten else []
    # Wahlknoten nur nutzen, wenn Zufallswahl dabei ist (sonst haengen sie voll an der Unterklasse)
    zufall = sum(1 for x in naechte for _, w, _ in x["knoten"] if w == "zufall")
    if zufall == 0:
        knoten = []

    def extra(nacht):
        n1, w1 = extra_klassen(nacht)
        cnt = {}
        for _, _, ids in nacht["knoten"]:
            for i in ids:
                cnt[i] = cnt.get(i, 0) + 1
        return n1 + ["knoten_" + k for k in knoten], w1 + [float(cnt.get(k, 0)) for k in knoten]

    mod = rm.modell(naechte, True, extra, lam)
    return mod, knoten


def klassen_tabelle(mod):
    idx = {n: i for i, n in enumerate(mod["namen"])}
    zeilen = []
    for c in rm.UNTERKLASSEN:
        i = idx["klasse_" + c]
        zeilen.append([c, mod["beta"][i], mod["se"][i]])
    mittel = sum(z[1] for z in zeilen) / len(zeilen)
    out = []
    for c, b, s in zeilen:
        d = b - mittel
        out.append({"klasse": c, "beta": b, "rel": d, "ci": rm.Z95 * s, "auffaellig": abs(d) > ZIEL_LOGIT,
                    "sicher": abs(d) - rm.Z95 * s > 0})
    return out, mittel


def knoten_tabelle(mod, naechte):
    haeufig = {}
    for x in naechte:
        for _, w, ids in x["knoten"]:
            if w == "zufall":
                for i in ids:
                    haeufig[i] = haeufig.get(i, 0) + 1
    out = []
    for i, nm in enumerate(mod["namen"]):
        if nm.startswith("knoten_"):
            k = nm[7:]
            out.append({"knoten": k, "gamma": mod["beta"][i], "ci": rm.Z95 * mod["se"][i], "bots": haeufig.get(k, 0),
                        "auffaellig": abs(mod["beta"][i]) > ZIEL_LOGIT,
                        "sicher": abs(mod["beta"][i]) - rm.Z95 * mod["se"][i] > 0})
    out.sort(key=lambda r: -abs(r["gamma"]))
    return out


def paartausch(naechte):
    basis, tausch = {}, {}
    for x in naechte:
        m = re.match(r"nacht_p_([a-z]+)_(\d+)$", x["mix"])
        t = re.match(r"nacht_p_([a-z]+)_([a-z]+)_(\d+)$", x["mix"])
        if m:
            basis[(m.group(1), int(m.group(2)))] = x
        elif t:
            tausch.setdefault((t.group(1), t.group(2)), {})[int(t.group(3))] = x
    erg = []
    for (X, Y), d in sorted(tausch.items()):
        paare = [(basis[(X, k)]["sieg"], d[k]["sieg"]) for k in d if (X, k) in basis]
        if len(paare) < 2:
            continue
        diff = [a - b for a, b in paare]  # X minus Y: positiv = X besser
        mean = sum(diff) / len(diff)
        var = sum((v - mean) ** 2 for v in diff) / (len(diff) - 1)
        se = math.sqrt(var / len(diff))
        erg.append({"x": X, "y": Y, "paare": len(paare), "diff": mean, "lo": mean - rm.Z95 * se, "hi": mean + rm.Z95 * se})
    return erg


def auswerten(naechte, info, lam=2.0, alle_knoten=False):
    z = [rm.kopf(naechte, info), ""]
    mod, knoten = fit(naechte, lam)
    ktab, mittel = klassen_tabelle(mod)
    data = {"lambda": lam, "n": len(naechte), "mittel": mittel, "klassen": ktab}
    z.append("Beitrag je Unterklasse (Ridge lambda=%.1f, n=%d Naechte; logit zum Schnitt der 20, Ziel +-%.2f)" % (lam, len(naechte), ZIEL_LOGIT))
    z.append("  Unterklasse     rel. logit   +-95 %    Urteil")
    for r in sorted(ktab, key=lambda r: r["rel"]):
        urt = "ok"
        if r["auffaellig"]:
            urt = "AUFFAELLIG (sicher)" if r["sicher"] else "auffaellig, Intervall enthaelt 0"
        z.append("  %-14s %+7.2f    %.2f     %s" % (r["klasse"], r["rel"], r["ci"], urt))
    if knoten:
        ntab = knoten_tabelle(mod, naechte)
        data["knoten"] = ntab
        z.append("")
        z.append("Beitrag je Wahlknoten (gamma, logit je Bot mit dem Knoten; %d Knoten, %d auffaellig, %d davon sicher):" % (
            len(ntab), sum(r["auffaellig"] for r in ntab), sum(r["auffaellig"] and r["sicher"] for r in ntab)))
        for r in (ntab if alle_knoten else ntab[:15]):
            z.append("  %-26s %+6.2f +-%.2f  (Bots mit Zufallswahl: %d)%s" % (
                r["knoten"], r["gamma"], r["ci"], r["bots"], "  AUFFAELLIG" if r["auffaellig"] and r["sicher"] else ""))
    else:
        z.append("")
        z.append("Wahlknoten: keine Zufallswahl in den Daten (Lauf mit wahl=zufall noetig, siehe klassen.md).")
    return z, data


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("quelle")
    ap.add_argument("--variante", default="aktuell")
    ap.add_argument("--lambda", dest="lam", type=float, default=2.0)
    ap.add_argument("--json")
    ap.add_argument("--alle-knoten", action="store_true")
    ap.add_argument("--paartausch", action="store_true")
    a = ap.parse_args(argv)
    naechte, info = rm.lade(a.quelle, a.variante)
    if a.paartausch:
        erg = paartausch(naechte)
        print(rm.kopf(naechte, info))
        for e in erg:
            print("  %s statt %s: Diff Siegquote %+5.1f Punkte [%+5.1f..%+5.1f], %d Paare" % (
                e["y"], e["x"], -100 * e["diff"], -100 * e["hi"], -100 * e["lo"], e["paare"]))
        return erg
    zeilen, data = auswerten(naechte, info, a.lam, a.alle_knoten)
    print("\n".join(zeilen))
    if a.json:
        json.dump(data, open(a.json, "w", encoding="utf-8"), indent=1)
    return data


if __name__ == "__main__":
    main()
