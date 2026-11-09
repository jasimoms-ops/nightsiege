#!/usr/bin/env python3
"""Tor 6.1: Auswertung des Rasters (Stufen 1-25 x 1-10 Spieler, Ereignis/Boss, Bot-Skill).

Aufruf: raster_auswertung.py <ordner-oder-teamsim.csv|sim-log.txt> [--variante aktuell] [--json ausgabe.json]
Ausgabe (Text): n immer dabei.
  1. Siegquote je Stufe und je Spielerzahl-Bucket mit Wilson-Intervall
  2. gepooltes logistisches Modell (Stufe, Kit-Spruenge 6/11/20, Spielerzahl, Koennen, Ereignis, Boss)
  3. Modell-Vorhersage je Stufe x Bucket (Koennen 0,7, ohne Ereignis) mit 95-%-Band; auffaellig = Band ganz ausserhalb 65-75 %
  4. je Bot-Koennen, je Ereignis/Boss die rohe Quote
  5. Tickzeit gegen lebende Monster (P95 je Klasse lebender Monster, ab wann 50 ms), fruehe Entscheidungen je Band
Zellen mit n < 20 gelten als unsicher (E. Messregeln); weniger als 8 Naechte je Stufe x Gruppe: nicht deuten.
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rastermath as rm  # noqa: E402


def quote(naechte):
    k = sum(x["sieg"] for x in naechte)
    return k, len(naechte), rm.wilson(k, len(naechte))


def fmt_q(naechte):
    k, n, (p, lo, hi) = quote(naechte)
    if n == 0:
        return "n=0"
    return "%5.1f %% [%4.1f-%4.1f] n=%d%s" % (100 * p, 100 * lo, 100 * hi, n, " (unsicher)" if n < 20 else "")


def pred_ci(mod, nacht):
    nm, wv, _ = rm.basis_merkmale(nacht, True)
    idx = {n: i for i, n in enumerate(mod["namen"])}
    v = [0.0] * len(mod["namen"])
    for n, x in zip(nm, wv):
        v[idx[n]] = x
    eta = sum(b * x for b, x in zip(mod["beta"], v))
    var = sum(v[i] * mod["cov"][i][j] * v[j] for i in range(len(v)) for j in range(len(v)) if v[i] and v[j])
    s = math.sqrt(max(var, 0.0))
    return rm.expit(eta), rm.expit(eta - rm.Z95 * s), rm.expit(eta + rm.Z95 * s)


def tickzeit(naechte):
    """P95 der Tickzeit je Klasse lebender Monster (aus den TICK-Fenstern, sonst aus RASTER)."""
    klassen = {}
    for x in naechte:
        for live, p95 in x["ticks"]:
            klassen.setdefault(live // 20 * 20, []).append(p95)
        if not x["ticks"] and x["mspt"] >= 0:
            klassen.setdefault(x["live"] // 20 * 20, []).append(x["mspt"])
    zeilen, ab50 = [], None
    for k in sorted(klassen):
        v = sorted(klassen[k])
        p95 = v[min(len(v) - 1, int(math.ceil(0.95 * len(v))) - 1)]
        zeilen.append((k, len(v), p95))
        if ab50 is None and p95 >= 50.0:
            ab50 = k
    return zeilen, ab50


def auswerten(naechte, info):
    out = [rm.kopf(naechte, info)]
    data = {"kopf": out[0]}
    if not naechte:
        return out + ["keine gueltigen Naechte"], data
    out.append("")
    out.append("1. Siegquote je Stufe (alle Spielerzahlen, Wilson 95 %); Ziel 70 %")
    je_stufe = {}
    for L in sorted({x["stufe"] for x in naechte}):
        sub = [x for x in naechte if x["stufe"] == L]
        je_stufe[L] = fmt_q(sub)
        out.append("  L%-2d %s" % (L, je_stufe[L]))
    out.append("  je Spielerzahl:")
    for n in sorted({x["n"] for x in naechte}):
        out.append("  n=%-2d Spieler %s" % (n, fmt_q([x for x in naechte if x["n"] == n])))
    mod = rm.modell(naechte, True)
    out.append("")
    out.append("2. Gepooltes Modell (logit, Ridge; Referenz Stufe 13, 7-10 Spieler, Koennen 0,7, ohne Ereignis/Boss), n=%d" % len(naechte))
    for nm, b, s in zip(mod["namen"], mod["beta"], mod["se"]):
        if nm.startswith(("ereignis_", "boss_")) and abs(b) < 1e-3:
            continue
        out.append("  %-22s %+6.2f +- %.2f" % (nm, b, rm.Z95 * s))
    out.append("")
    out.append("3. Vorhersage Stufe x Bucket (Koennen 0,7, ohne Ereignis): Modell [95-%-Band]; ! = Band ganz ausserhalb 65-75 %")
    head = "  Stufe " + " ".join("%-18s" % b[0] for b in rm.BUCKETS)
    out.append(head)
    auffaellig = []
    data["zellen"] = {}
    for L in sorted({x["stufe"] for x in naechte}):
        zeile = []
        for bi, (bn, (a, b)) in enumerate(rm.BUCKETS):
            n_roh = sum(1 for x in naechte if x["stufe"] == L and rm.bucket_of(x["n"]) == bi)
            if n_roh == 0:
                zeile.append("%-18s" % "-")
                continue
            p, lo, hi = pred_ci(mod, rm.referenz_nacht(L, a))
            flag = "!" if (hi < 0.65 or lo > 0.75) else " "
            if flag == "!":
                auffaellig.append((L, bn))
            data["zellen"]["L%d_n%s" % (L, bn)] = {"p": p, "lo": lo, "hi": hi, "n": n_roh}
            zeile.append("%-18s" % ("%s%3.0f [%3.0f-%3.0f] n%d" % (flag, 100 * p, 100 * lo, 100 * hi, n_roh)))
        out.append("  L%-3d  %s" % (L, " ".join(zeile)))
    out.append("  auffaellig: %s" % (", ".join("L%d/%s" % z for z in auffaellig) or "keine"))
    out.append("")
    out.append("4. Rohquoten je Faktor")
    for sk in sorted({round(x["skill"], 1) for x in naechte}):
        out.append("  Koennen %.1f: %s" % (sk, fmt_q([x for x in naechte if round(x["skill"], 1) == sk])))
    out.append("  ohne Ereignis/Boss: %s" % fmt_q([x for x in naechte if x["ereignis"] == "NONE" and x["boss"] == "-"]))
    for e in rm.EREIGNISSE:
        sub = [x for x in naechte if x["ereignis"] == e]
        if sub:
            out.append("  Ereignis %-11s %s" % (e, fmt_q(sub)))
    for b in rm.BOSSE:
        sub = [x for x in naechte if x["boss"] == b]
        if sub:
            out.append("  Boss %-14s %s" % (b, fmt_q(sub)))
    out.append("")
    out.append("5. Tickzeit gegen lebende Monster (P95 je 20 Monster) und fruehe Entscheidungen (Niederlage im ersten Drittel)")
    zeilen, ab50 = tickzeit(naechte)
    for k, n, p95 in zeilen:
        out.append("  %3d-%3d lebend: P95 %6.1f ms (n=%d Fenster/Naechte)%s" % (k, k + 19, p95, n, "  <- ab 50 ms" if p95 >= 50 and k == ab50 else ""))
    out.append("  Deckel-Vorschlag (erste Klasse mit P95 >= 50 ms, hoechstens 120): %s" % (
        "%d" % min(120, ab50) if ab50 is not None else "nicht erreicht (Deckel 120)"))
    data["tick_ab50"] = ab50
    for i in range(len(rm.BAENDER)):
        sub = [x for x in naechte if rm.band_of(x["stufe"]) == i]
        if sub:
            out.append("  %s: frueh entschieden %d von %d (%.0f %%)" % (rm.band_name(i), sum(x["frueh"] for x in sub), len(sub),
                                                                       100.0 * sum(x["frueh"] for x in sub) / len(sub)))
    return out, data


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
    return zeilen


if __name__ == "__main__":
    main()
