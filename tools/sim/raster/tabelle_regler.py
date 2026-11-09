#!/usr/bin/env python3
"""Tor 6.2: Regler fuer das Monster-Budget je Stufe (Formel A der Recherche vom 08.11.).

Aufruf: tabelle_regler.py <raster-daten> [--variante aktuell] [--flach] [--max 0.15] [--json tabelle.json]
  (<raster-daten>: Ordner mit teamsim.csv/sim-log.txt aus dem Raster-Lauf, siehe qa/tor-6/raster.md)

Formel A:   Delta ln(theta) = 0,5 * (logit p_hat - logit p_ziel), je Lauf gedeckelt auf +-15 %.
  p_hat = Vorhersage des gepoolten Modells (Koennen 0,7, ohne Ereignis/Boss), also ohne Schlaue Nacht (im Raster aus).
  p_ziel = 70 %; im Kit-Band faellt das Ziel von 75 % (erste Stufe des Bands) auf 65 % (letzte Stufe): Baender
  1-5, 6-10, 11-19, 20-25 (--flach: immer 70 %).
  Spruenge: der rohe Regelwert je Stufe wird mit [1, Stufe, I(L>=6), I(L>=11), I(L>=20)] gewichtet (n) geglaettet,
  so entstehen die Sprungterme s6, s11, s20 (positives Vorzeichen erwartet: die Monster springen, wo das Kit springt).
  Stopp je Band x Spielerzahl-Bucket (Region), sobald das 95-%-Intervall (Wilson, roh) 70 % enthaelt.
Ausgabe: Faktor je Stufe (neues Budget = altes x Faktor; Anzahl x Leben der normalen Monster), Faktor je
  Spielerzahl-Bucket (c_k), Regionen mit Stopp. Faktor 1,00 = unveraendert. Die Faktoren sind Vorschlaege fuer EINEN Lauf;
  danach auf frischen Seeds mit Koennen 0,5 und 0,9 nachmessen.
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rastermath as rm  # noqa: E402


def deckel(delta, maximum):
    """Delta (ln) -> Faktor, gedeckelt auf 1 +- maximum."""
    return min(1.0 + maximum, max(1.0 - maximum, math.exp(delta)))


def gewichtete_regression(zeilen):
    """zeilen: (stufe, delta, gewicht) -> Koeffizienten [a0, a1, s6, s11, s20] (gewichtete kleinste Quadrate)."""
    k = 5
    xtx = [[0.0] * k for _ in range(k)]
    xty = [0.0] * k
    for L, d, w in zeilen:
        x = [1.0, (L - 13) / 12.0, float(L >= 6), float(L >= 11), float(L >= 20)]
        for i in range(k):
            xty[i] += w * x[i] * d
            for j in range(k):
                xtx[i][j] += w * x[i] * x[j]
    for i in range(k):
        xtx[i][i] += 1e-6 + (0.05 if i >= 2 else 0.0)  # leichte Strafe auf die Spruenge: kein Zittern bei wenig Daten
    inv = rm.solve_inverse(xtx)
    return [sum(inv[i][j] * xty[j] for j in range(k)) for i in range(k)]


def regler(naechte, flach=False, maximum=0.15):
    mod = rm.modell(naechte, True)
    stufen = sorted({x["stufe"] for x in naechte})
    # Regionen: Band x Bucket
    regionen = []
    stopp_band = {}
    for bi in range(len(rm.BAENDER)):
        for ki, (kn, (a, b)) in enumerate(rm.BUCKETS):
            sub = [x for x in naechte if rm.band_of(x["stufe"]) == bi and rm.bucket_of(x["n"]) == ki]
            if not sub:
                continue
            k = sum(x["sieg"] for x in sub)
            p, lo, hi = rm.wilson(k, len(sub))
            stopp = lo <= rm.ZIEL <= hi
            lm = [x["stufe"] for x in sub]
            mitte = sorted(lm)[len(lm) // 2]
            p_mod = rm.vorhersage(mod, rm.referenz_nacht(mitte, a))
            delta = 0.0 if stopp else 0.5 * (rm.logit(p_mod) - rm.LOGIT_ZIEL)
            regionen.append({"band": rm.band_name(bi), "bucket": kn, "n": len(sub), "roh": p, "lo": lo, "hi": hi,
                             "modell": p_mod, "stopp": stopp, "faktor": deckel(delta, maximum)})
            stopp_band.setdefault(bi, []).append(stopp)
    # je Stufe: Regelwert aus dem Modell (Gewicht der Buckets gleich), Ziel je Stufe
    zeilen, stufen_roh = [], {}
    for L in stufen:
        sub = [x for x in naechte if x["stufe"] == L]
        ps = [rm.vorhersage(mod, rm.referenz_nacht(L, a)) for _, (a, b) in rm.BUCKETS if
              any(rm.bucket_of(x["n"]) == rm.bucket_of(a) for x in sub)]
        p_hat = sum(ps) / len(ps) if ps else rm.ZIEL
        ziel = rm.ziel_kurve(L, flach)
        delta = 0.5 * (rm.logit(p_hat) - rm.logit(ziel))
        stufen_roh[L] = (p_hat, ziel, delta, len(sub))
        zeilen.append((L, delta, len(sub)))
    koef = gewichtete_regression(zeilen)
    stufen_aus = []
    for L in stufen:
        p_hat, ziel, delta, n = stufen_roh[L]
        x = [1.0, (L - 13) / 12.0, float(L >= 6), float(L >= 11), float(L >= 20)]
        glatt = sum(c * v for c, v in zip(koef, x))
        band_stopp = all(stopp_band.get(rm.band_of(L), [False]))
        stufen_aus.append({"stufe": L, "n": n, "p_modell": p_hat, "ziel": ziel, "delta_roh": delta, "delta_glatt": glatt,
                           "faktor_roh": deckel(delta, maximum), "faktor": 1.0 if band_stopp else deckel(glatt, maximum),
                           "stopp": band_stopp})
    # Faktor je Bucket: Abweichung des Bucket-Mittels vom Gesamtmittel (relativ zum Ziel, je Stufe gewichtet)
    buckets = []
    for ki, (kn, (a, b)) in enumerate(rm.BUCKETS):
        sub = [x for x in naechte if rm.bucket_of(x["n"]) == ki]
        if not sub:
            continue
        ps = [rm.vorhersage(mod, rm.referenz_nacht(x["stufe"], a)) for x in sub]
        p_mod = sum(ps) / len(ps)
        ziel = sum(rm.ziel_kurve(x["stufe"], flach) for x in sub) / len(sub)
        k = sum(x["sieg"] for x in sub)
        buckets.append({"bucket": kn, "n": len(sub), "roh": k / len(sub), "modell": p_mod, "ziel": ziel,
                        "faktor": deckel(0.5 * (rm.logit(p_mod) - rm.logit(ziel)), maximum)})
    return {"koeffizienten": dict(zip(["a0", "a1", "s6", "s11", "s20"], koef)), "stufen": stufen_aus,
            "buckets": buckets, "regionen": regionen}


def text(erg, kopf):
    z = [kopf, "", "Regler (Formel A): Delta ln = 0,5*(logit p_hat - logit Ziel), je Lauf +-15 %; Faktor auf Anzahl x Leben der normalen Monster"]
    c = erg["koeffizienten"]
    z.append("Geglaettete Regelwerte: a0 %+.3f, je Stufe %+.3f, Sprung s6 %+.3f, s11 %+.3f, s20 %+.3f (logit-Halbschritte)" % (
        c["a0"], c["a1"], c["s6"], c["s11"], c["s20"]))
    z.append("")
    z.append("Stufe   n   p_Modell  Ziel   Faktor roh  Faktor (geglaettet)")
    for s in erg["stufen"]:
        z.append("L%-3d %4d   %5.1f %%  %5.1f %%   %5.2f       %5.2f%s" % (
            s["stufe"], s["n"], 100 * s["p_modell"], 100 * s["ziel"], s["faktor_roh"], s["faktor"],
            "  Stopp (Band enthaelt 70 %)" if s["stopp"] else ""))
    z.append("")
    z.append("Spielerzahl-Bucket: n   roh    Modell  Ziel   Faktor c_k")
    for b in erg["buckets"]:
        z.append("  %-5s %4d  %5.1f %%  %5.1f %%  %5.1f %%  %5.2f" % (
            b["bucket"], b["n"], 100 * b["roh"], 100 * b["modell"], 100 * b["ziel"], b["faktor"]))
    z.append("")
    z.append("Regionen (Band x Bucket), Stopp wenn Wilson-Intervall 70 % enthaelt:")
    for r in erg["regionen"]:
        z.append("  %-7s %-5s n=%-4d roh %5.1f %% [%4.1f-%4.1f]  Modell %5.1f %%  %s Faktor %.2f%s" % (
            r["band"], r["bucket"], r["n"], 100 * r["roh"], 100 * r["lo"], 100 * r["hi"], 100 * r["modell"],
            "Stopp" if r["stopp"] else "     ", r["faktor"], "  (unsicher, n<20)" if r["n"] < 20 else ""))
    return z


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("quelle")
    ap.add_argument("--variante", default="aktuell")
    ap.add_argument("--flach", action="store_true")
    ap.add_argument("--max", type=float, default=0.15)
    ap.add_argument("--json")
    a = ap.parse_args(argv)
    naechte, info = rm.lade(a.quelle, a.variante)
    if not naechte:
        print("keine gueltigen Naechte")
        return None
    erg = regler(naechte, a.flach, a.max)
    print("\n".join(text(erg, rm.kopf(naechte, info))))
    if a.json:
        json.dump(erg, open(a.json, "w", encoding="utf-8"), indent=1)
    return erg


if __name__ == "__main__":
    main()
