#!/usr/bin/env python3
"""Tor 6.0: echte Naechte gegen ihre Nachspiele in der Sim (Eichung).

Aufruf: eichung_vergleich.py <szenarien.json> <sim-stats-ordner> <echt-stats-ordner> [--variante aktuell] [--ausgabe bericht.md]
  szenarien.json     tools/sim/eichung/szenarien.json (gleiche Datei wie fuer die Sim: nachspiel=…)
  sim-stats-ordner   Ergebnis der Sim (dist-balance-run-*/stats/) mit Nachtdateien, deren "sim" auf "|nacht_<id>|" endet
  echt-stats-ordner  Logger-Dateien der echten Naechte (night_*.json). NICHT ins Repo legen (Spielernamen); die Ausgabe
                     enthaelt nur Unterklassen. Je Szenario: Feld "echt_datei" oder, wenn leer, Zuordnung ueber Stufe + Besetzung.

Je Szenario (n Sim-Seeds, bei n < 50 als UNSICHER markiert) und je Unterklasse und Welle:
  Sieg           Sim-Siegquote mit 90-%-Wilson-Band; die echte Nacht "liegt im Band", wenn ihr Ausgang in der Sim
                 mindestens 5 % Wahrscheinlichkeit hat (eine einzelne Nacht ist ein schwacher Test, darum zaehlt vor allem:)
  Siege gesamt   Summe der echten Siege gegen die Verteilung der Summe der Sim-Siegquoten (Poisson-Binomial, 90-%-Band 5–95 %)
  Dauer, Tode, Schaden erhalten (je Welle), Schaden ausgeteilt, Heilung, Faehigkeiten, Zeit bis zum ersten Tod:
                 echt gegen Sim-Mittel und 5–95-%-Band der Sim-Nächte. "ok" = im Band UND (Abweichung <= 20 %
                 oder bei Zaehlwerten <= 1 absolut). Abnahme 6.0: Siege gesamt im Band, jede echte Nacht im Band,
                 erhalten und Tode je Welle <= 20 % daneben.
Zeit bis zum ersten Tod: "ersterTodSekunde" (neue Logger-Felder), sonst Startsekunde der ersten Welle mit Tod (gleich fuer echt und Sim).
Ungueltige Sim-Naechte (0 Kills und kein Schaden: keine Monster) werden verworfen und gezaehlt.
"""
import argparse
import collections
import json
import math
import os
import re
import statistics
import sys


def wilson(k, n, z=1.645):
    if n == 0:
        return (0.0, 1.0)
    p = k / n
    d = 1 + z * z / n
    c = p + z * z / (2 * n)
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return ((c - h) / d, (c + h) / d)


def pct(xs, q):
    xs = sorted(xs)
    if not xs:
        return float("nan")
    i = (len(xs) - 1) * q
    lo, hi = int(math.floor(i)), int(math.ceil(i))
    return xs[lo] + (xs[hi] - xs[lo]) * (i - lo)


def poisson_binomial(ps):
    dist = [1.0]
    for p in ps:
        new = [0.0] * (len(dist) + 1)
        for k, v in enumerate(dist):
            new[k] += v * (1 - p)
            new[k + 1] += v * p
        dist = new
    return dist


def won(n):
    out = n.get("ergebnis") or ""
    return out in ("gewonnen", "test_gehalten") and (not n.get("killZiel") or n.get("kills", 0) >= n.get("killZiel", 0))


def load(folder):
    for root, _, files in os.walk(folder):
        for f in sorted(files):
            if f.endswith(".json") and "dungeon" not in f and "heerlager" not in f:
                try:
                    with open(os.path.join(root, f), encoding="utf-8") as fh:
                        yield f, json.load(fh)
                except (OSError, ValueError):
                    continue


def sub_of(n, name, gruppe):
    info = (n.get("spieler") or {}).get(name) or {}
    sub = info.get("unterklasse") or ""
    if not sub:
        m = re.fullmatch(r"Bot(\d+)", name)
        if m and gruppe and 0 < int(m.group(1)) <= len(gruppe):
            sub = gruppe[int(m.group(1)) - 1]
    return sub or name


def digest(n, gruppe=None):
    """Eine Nacht -> {tot, dauer, tode, wellen: [{erhalten, tode}], subs: {sub: [...] }} (nur Unterklassen)."""
    per_sub = collections.defaultdict(lambda: collections.Counter())
    first_death = {}
    waves = []
    for w in n.get("wellen") or []:
        rec = {"erhalten": 0.0, "tode": 0}
        for name, pw in (w.get("spieler") or {}).items():
            sub = sub_of(n, name, gruppe)
            c = per_sub[sub]
            c["schaden"] += pw.get("schaden", 0.0)
            c["erhalten"] += pw.get("erhalten", 0.0)
            c["heilung"] += pw.get("heilungGegeben", 0.0)
            c["tode"] += pw.get("tode", 0)
            c["faehigkeiten"] += sum((pw.get("faehigkeiten") or {}).values())
            rec["erhalten"] += pw.get("erhalten", 0.0)
            rec["tode"] += pw.get("tode", 0)
            if pw.get("tode", 0) > 0 and sub not in first_death:
                first_death[sub] = w.get("startSekunde", 0)
        waves.append(rec)
    for name, info in (n.get("spieler") or {}).items():
        t = info.get("ersterTodSekunde", -1)
        if t is not None and t >= 0:
            first_death[sub_of(n, name, gruppe)] = t
    for sub, c in per_sub.items():
        c["erster_tod"] = first_death.get(sub, -1)
    return {"sieg": won(n), "dauer": n.get("dauerSekunden", 0), "tode": n.get("tode", 0),
            "wellen": waves, "subs": per_sub, "kills": n.get("kills", 0)}


def verdict(real, sim_vals, count=False):
    """-> (text, ok). Echt gegen Sim-Mittel, 5-95-%-Band und +-20 %."""
    if not sim_vals:
        return ("keine Sim-Daten", None)
    mean = statistics.mean(sim_vals)
    lo, hi = pct(sim_vals, 0.05), pct(sim_vals, 0.95)
    inband = lo - 1e-9 <= real <= hi + 1e-9
    off = (mean - real) / real if real else (0.0 if mean == 0 else float("inf"))
    near = abs(off) <= 0.2 or (count and abs(mean - real) <= 1.0)
    ok = inband and near
    return ("echt %.0f | Sim %.0f [%.0f–%.0f] %+.0f %% %s" % (real, mean, lo, hi, off * 100 if abs(off) != float("inf") else 999,
                                                         "ok" if ok else "ABWEICHUNG"), ok)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("szenarien")
    ap.add_argument("sim")
    ap.add_argument("echt")
    ap.add_argument("--variante", default="aktuell")
    ap.add_argument("--ausgabe", default="")
    a = ap.parse_args()
    scen = json.load(open(a.szenarien, encoding="utf-8"))["nacht"]

    sim = collections.defaultdict(list)
    invalid = collections.Counter()
    for _, n in load(a.sim):
        label = n.get("sim") or ""
        parts = label.split("|")
        if len(parts) < 5 or parts[0] != a.variante or not parts[3].startswith("nacht_"):
            continue
        if n.get("ergebnis") == "abgebrochen":
            continue
        sid = parts[3][6:]
        sc = next((s for s in scen if s["id"] == sid), None)
        d = digest(n, sc["gruppe"] if sc else None)
        if d["kills"] == 0 and sum(c["schaden"] for c in d["subs"].values()) == 0:
            invalid[sid] += 1   # no monsters came: invalid run
            continue
        sim[sid].append(d)

    real_nights = [(f, n) for f, n in load(a.echt) if not n.get("sim") and n.get("ergebnis") != "abgebrochen"
                   and n.get("abgeschlossen", True)]
    used = set()
    out = []
    ps, real_wins, judged = [], 0, 0
    # Tor 8.3: je Unterklasse ueber alle Naechte, je Minute (Summen ueber verschieden lange Naechte verzerren)
    permin = collections.defaultdict(collections.Counter)
    allok = True
    for sc in scen:
        sid = sc["id"]
        runs = sim.get(sid, [])
        real = None
        if sc.get("echt_datei"):
            real = next((digest(n) for f, n in real_nights if f == sc["echt_datei"]), None)
        else:
            want = sorted(sc["gruppe"])
            for f, n in real_nights:
                subs = sorted((i.get("unterklasse") or "") for i in (n.get("spieler") or {}).values())
                if f not in used and int(n.get("stufe") or 0) == sc["stufe"] and subs == want:
                    used.add(f)
                    real = digest(n)
                    break
        out.append("## %s: Stufe %s, %d Spieler, Ereignis %s, Boss %s" % (sid, sc["stufe"], len(sc["gruppe"]),
                                                                         sc.get("ereignis") or "-", sc.get("boss") or "-"))
        if sc.get("luecken"):
            out.append("Luecken: " + "; ".join(sc["luecken"]))
        n = len(runs)
        if n == 0:
            out.append("Keine gueltigen Sim-Naechte (ungueltig verworfen: %d)." % invalid[sid])
            continue
        k = sum(1 for r in runs if r["sieg"])
        lo, hi = wilson(k, n)
        flag = "" if n >= 50 else "  UNSICHER (n < 50)"
        out.append("Sim: %d Naechte, %d Siege = %.0f %% [90 %%-Band %.0f–%.0f %%], ungueltig verworfen %d%s" % (
            n, k, 100 * k / n, 100 * lo, 100 * hi, invalid[sid], flag))
        ps.append(k / n)
        if real is None:
            out.append("Echte Nacht: nicht gefunden (Rohdaten fehlen oder Besetzung weicht ab)\n")
            continue
        judged += 1
        real_wins += 1 if real["sieg"] else 0
        p_out = k / n if real["sieg"] else 1 - k / n
        out.append("Ausgang echt: %s; in der Sim %.0f %% -> %s" % ("Sieg" if real["sieg"] else "Niederlage", 100 * p_out,
                                                                 "im Band" if p_out >= 0.05 else "AUSSERHALB"))
        allok &= p_out >= 0.05
        rows = [("Dauer s", verdict(real["dauer"], [r["dauer"] for r in runs]))]
        rows.append(("Tode gesamt", verdict(real["tode"], [r["tode"] for r in runs], True)))
        nw = len(real["wellen"])
        for wi in range(nw):
            vals = [r["wellen"][wi] for r in runs if len(r["wellen"]) > wi]
            if len(vals) < n / 2:
                rows.append(("Welle %d" % (wi + 1), ("Sim erreicht sie nur in %d von %d Naechten" % (len(vals), n), False)))
                allok = False
                continue
            e = verdict(real["wellen"][wi]["erhalten"], [v["erhalten"] for v in vals])
            t = verdict(real["wellen"][wi]["tode"], [v["tode"] for v in vals], True)
            rows.append(("Welle %d erhalten" % (wi + 1), e))
            rows.append(("Welle %d Tode" % (wi + 1), t))
            allok &= bool(e[1]) and bool(t[1])
        for sub in sorted(real["subs"]):
            rc = real["subs"][sub]
            for key, label, cnt in (("schaden", "ausgeteilt", False), ("erhalten", "erhalten", False),
                                    ("heilung", "Heilung", False), ("tode", "Tode", True),
                                    ("faehigkeiten", "Faehigkeiten", True)):
                vals = [r["subs"][sub][key] for r in runs if sub in r["subs"]]
                rows.append(("%s %s" % (sub, label), verdict(rc[key], vals, cnt)))
            sruns = [r for r in runs if sub in r["subs"]]
            if real["dauer"] > 0 and sruns:
                for key, label in (("schaden", "ausgeteilt/min"), ("erhalten", "erhalten/min")):
                    vals = [r["subs"][sub][key] * 60.0 / r["dauer"] for r in sruns if r["dauer"] > 0]
                    rows.append(("%s %s" % (sub, label), verdict(rc[key] * 60.0 / real["dauer"], vals)))
                pm = permin[sub]
                pm["echt_min"] += real["dauer"] / 60.0
                pm["sim_min"] += statistics.mean(r["dauer"] for r in sruns) / 60.0
                for key in ("schaden", "erhalten"):
                    pm["echt_" + key] += rc[key]
                    pm["sim_" + key] += statistics.mean(r["subs"][sub][key] for r in sruns)
            fd = [r["subs"][sub]["erster_tod"] for r in runs if sub in r["subs"]]
            died = [x for x in fd if x >= 0]
            if rc["erster_tod"] >= 0 and died:
                rows.append(("%s erster Tod s" % sub, verdict(rc["erster_tod"], died)))
            else:
                rows.append(("%s erster Tod" % sub, ("echt %s | Sim %d von %d mit Tod" % (
                    "tot bei %ds" % rc["erster_tod"] if rc["erster_tod"] >= 0 else "kein Tod", len(died), len(fd)), None)))
        out.append("| Wert | Vergleich |\n|---|---|")
        out += ["| %s | %s |" % (name, v[0]) for name, v in rows]
        out.append("")
    if permin:
        out.append("## Je Minute je Unterklasse, alle Naechte (echt / Sim, Verhaeltnis Sim/echt; Ziel 0,6-1,5)")
        out.append("| Unterklasse | ausgeteilt/min | erhalten/min |\n|---|---|---|")
        for sub in sorted(permin):
            pm = permin[sub]
            cells = []
            for key in ("schaden", "erhalten"):
                e = pm["echt_" + key] / pm["echt_min"] if pm["echt_min"] else 0.0
                v = pm["sim_" + key] / pm["sim_min"] if pm["sim_min"] else 0.0
                cells.append("%.0f / %.0f (%s)" % (e, v, ("%.2f" % (v / e)).replace(".", ",") if e else "-"))
            out.append("| %s | %s | %s |" % (sub, cells[0], cells[1]))
        out.append("")
    if judged:
        dist = poisson_binomial(ps)
        cum, lo_k, hi_k = 0.0, 0, len(ps)
        for kk, v in enumerate(dist):
            cum += v
            if cum < 0.05:
                lo_k = kk + 1
            if cum >= 0.95:
                hi_k = kk
                break
        ok = lo_k <= real_wins <= hi_k
        out.insert(0, "# Eichung: %d echte Naechte verglichen. Siege gesamt echt %d, Sim erwartet %.1f [90 %%-Band %d–%d] -> %s. "
                      "Abnahme 6.0 (alle Teile) %s\n" % (judged, real_wins, sum(ps), lo_k, hi_k,
                                                          "im Band" if ok else "AUSSERHALB", "erfuellt" if (ok and allok) else "NICHT erfuellt"))
    else:
        out.insert(0, "# Eichung: keine echte Nacht zugeordnet (Rohdaten fehlen?). Nur Sim-Vorhersage:\n")
    text = "\n".join(out)
    if a.ausgabe:
        with open(a.ausgabe, "w", encoding="utf-8") as fh:
            fh.write(text + "\n")
    print(text)


if __name__ == "__main__":
    sys.exit(main())
