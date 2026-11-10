#!/usr/bin/env python3
"""Tor 6.1-6.5: gemeinsame Rechenbausteine (reines Python, kein numpy).

Wilson-Intervall, logistisches Modell (Newton mit Ridge), Einlesen der Team-Sim-CSV (RASTER-, LAUF-, KNOTEN-,
TICK-Zeilen; Ersatz: sim-log.txt mit "[Night Siege sim] RASTER;..."), Stufenbaender und Spieler-Buckets.
"""
import math
import os

Z95 = 1.959964
ZIEL = 0.70
LOGIT_ZIEL = math.log(0.7 / 0.3)  # 0,847
# Kit-Baender (Kit springt bei 6, 11 und 20); Bandkanten 5/6, 10/11, 19/20
BAENDER = [(1, 5), (6, 10), (11, 19), (20, 25)]
BUCKETS = [("1", (1, 1)), ("2", (2, 2)), ("3-4", (3, 4)), ("5-6", (5, 6)), ("7-10", (7, 10))]
UNTERKLASSEN = ["blademaster", "berserker", "axe", "paladin", "shieldfighter", "bulwark", "medic", "warden",
                "swift", "heavy", "bolter", "trapper", "gunner", "electrician", "demolition", "alchemist",
                "tamer", "golemsmith", "necromancer", "soulcatcher"]
EREIGNISSE = ["BLOOD_MOON", "ECLIPSE", "FOG", "THUNDER", "NETHER_RIFT", "FROST"]
BOSSE = ["grave_king", "spider_queen", "bone_lich", "warlord", "frost_jarl", "ember_colossus", "storm_witch", "ram_titan"]


def expit(x):
    if x >= 0:
        return 1.0 / (1.0 + math.exp(-x))
    e = math.exp(x)
    return e / (1.0 + e)


def logit(p):
    p = min(max(p, 1e-6), 1 - 1e-6)
    return math.log(p / (1 - p))


def wilson(k, n, z=Z95):
    """Wilson-Intervall (Anteil, unten, oben); n = 0 gibt (nan, nan, nan)."""
    if n <= 0:
        return (float("nan"),) * 3
    p = k / n
    d = 1 + z * z / n
    mid = (p + z * z / (2 * n)) / d
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return p, max(0.0, mid - half), min(1.0, mid + half)


def band_of(stufe):
    for i, (a, b) in enumerate(BAENDER):
        if a <= stufe <= b:
            return i
    return len(BAENDER) - 1 if stufe > BAENDER[-1][1] else 0


def band_name(i):
    a, b = BAENDER[i]
    return "L%d-%d" % (a, b)


def bucket_of(n):
    for i, (_, (a, b)) in enumerate(BUCKETS):
        if a <= n <= b:
            return i
    return len(BUCKETS) - 1


def ziel_kurve(stufe, flach=False):
    """Zielquote je Stufe: 70 %; im Kit-Band von 75 % am Bandanfang auf 65 % am Bandende (Mittel 70 %)."""
    if flach:
        return ZIEL
    a, b = BAENDER[band_of(stufe)]
    if b == a:
        return ZIEL
    return 0.75 - 0.10 * (stufe - a) / (b - a)


# ---------------------------------------------------------------- lineare Algebra (klein)

def solve_inverse(a):
    """Inverse einer kleinen symmetrischen Matrix (Gauss-Jordan mit Pivotsuche)."""
    n = len(a)
    m = [list(row) + [1.0 if i == j else 0.0 for j in range(n)] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        if abs(m[p][c]) < 1e-12:
            m[p][c] = 1e-12
        m[c], m[p] = m[p], m[c]
        piv = m[c][c]
        m[c] = [v / piv for v in m[c]]
        for r in range(n):
            if r != c and m[r][c] != 0.0:
                f = m[r][c]
                m[r] = [v - f * w for v, w in zip(m[r], m[c])]
    return [row[n:] for row in m]


def fit_logit(x, y, pen=None, w=None, iters=40):
    """Logistisches Modell mit Ridge. x: Zeilen (Listen), y: 0/1 (oder Anteile bei Gewicht w), pen: Strafe je Merkmal.

    Gibt (beta, se, cov) zurueck; die Standardfehler gehoeren zur gestraften Kurvenkruemmung (Ridge-Naeherung).
    """
    n = len(x)
    k = len(x[0]) if n else 0
    pen = [1e-6 + (pen[j] if pen else 0.0) for j in range(k)]
    beta = [0.0] * k
    w = w or [1.0] * n
    cov = None
    for _ in range(iters):
        grad = [-pen[j] * beta[j] for j in range(k)]
        hess = [[0.0] * k for _ in range(k)]
        for xi, yi, wi in zip(x, y, w):
            eta = sum(b * v for b, v in zip(beta, xi))
            p = expit(eta)
            r = wi * (yi - p)
            s = wi * p * (1 - p)
            for j in range(k):
                xj = xi[j]
                if xj == 0.0:
                    continue
                grad[j] += r * xj
                sj = s * xj
                hj = hess[j]
                for l in range(j, k):
                    xl = xi[l]
                    if xl != 0.0:
                        hj[l] += sj * xl
        for j in range(k):
            hess[j][j] += pen[j]
            for l in range(j):
                hess[j][l] = hess[l][j]
        cov = solve_inverse(hess)
        delta = [sum(cov[j][l] * grad[l] for l in range(k)) for j in range(k)]
        step = max(abs(d) for d in delta) if delta else 0.0
        if step > 3.0:  # Schrittweite begrenzen (fast getrennte Daten)
            delta = [d * 3.0 / step for d in delta]
        beta = [b + d for b, d in zip(beta, delta)]
        if step < 1e-7:
            break
    se = [math.sqrt(max(cov[j][j], 0.0)) for j in range(k)]
    return beta, se, cov


# ---------------------------------------------------------------- Einlesen

GUELTIG_NICHT = ("start_fehlgeschlagen", "gestoppt", "unbekannt")


def _read_lines(pfad):
    if os.path.isdir(pfad):
        for name in ("teamsim.csv", "sim-log.txt"):
            f = os.path.join(pfad, name)
            if os.path.exists(f):
                pfad = f
                break
    with open(pfad, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n").rstrip("\r")
            if "[Night Siege sim] " in line:
                line = line[line.index("[Night Siege sim] ") + 18:]
            yield line


def lade(pfad, variante="aktuell"):
    """Liest Naechte aus teamsim.csv oder sim-log.txt.

    Gibt (naechte, info) zurueck. Eine Nacht ist ein dict: stufe, n, team (Liste), mix, lauf, ereignis, boss, skill,
    sieg, mspt, live, frueh, tode, sekunden, ergebnis, knoten (Liste (unterklasse, wahl, [knoten])),
    ticks (Liste (live, p95)). info zaehlt die ungueltigen Laeufe (0 Kills oder nicht gespielt).
    """
    raster, lauf, knoten, ticks, seen = {}, {}, {}, {}, set()
    for line in _read_lines(pfad):
        p = line.split(";")
        if len(p) < 6 or p[0] not in ("RASTER", "LAUF", "KNOTEN", "TICK"):
            continue
        if p[1] != variante or not p[2].lstrip("-").isdigit():
            continue  # Kopfzeilen und andere Varianten
        key = tuple(p[1:6])
        if p[0] == "RASTER" and len(p) >= 16:
            if key in raster:
                continue  # csv und Log doppelt: die erste zaehlt
            raster[key] = p
        elif p[0] == "LAUF" and len(p) >= 14:
            lauf[key] = p
        elif p[0] == "KNOTEN" and len(p) >= 9:
            knoten.setdefault(key, []).append((p[6], p[7], [k for k in p[8].split("+") if k]))
        elif p[0] == "TICK" and len(p) >= 10:
            ticks.setdefault(key, []).append((int(p[7]), float(p[8])))
    out, info = [], {"gesamt": len(raster), "ungueltig_0_kills": 0, "ungueltig_nicht_gespielt": 0}
    for key, p in raster.items():
        l = lauf.get(key)
        kills = int(l[9]) if l else None
        ergebnis = p[15]
        if ergebnis in GUELTIG_NICHT or ergebnis.startswith("Fehler") or ergebnis.startswith("Bot"):
            info["ungueltig_nicht_gespielt"] += 1
            continue
        if kills is not None and kills == 0:
            info["ungueltig_0_kills"] += 1  # es kamen keine Monster: Lauf ungueltig (28.10.)
            continue
        team = l[13].split("+") if l and l[13] else []
        out.append({
            "stufe": int(p[2]), "n": int(p[3]), "mix": p[4], "lauf": int(p[5]), "ereignis": p[6], "boss": p[7],
            "skill": float(p[8]), "sieg": int(p[9]), "mspt": float(p[10]), "live": int(p[11]), "frueh": int(p[12]),
            "tode": int(p[13]), "sekunden": int(p[14]), "ergebnis": ergebnis, "team": team,
            "knoten": knoten.get(key, []), "ticks": ticks.get(key, []),
        })
    info["gueltig"] = len(out)
    return out, info


def kopf(naechte, info):
    return "Quelle: %d Nächte gültig von %d (ungültig: %d ohne Kills, %d nicht gespielt), Siege %d (%.1f %%)" % (
        info["gueltig"], info["gesamt"], info["ungueltig_0_kills"], info["ungueltig_nicht_gespielt"],
        sum(x["sieg"] for x in naechte), 100.0 * sum(x["sieg"] for x in naechte) / max(1, len(naechte)))


# ---------------------------------------------------------------- Modell der Naechte

def basis_merkmale(nacht, mit_inhalt=True):
    """(Namen, Werte, Strafen) der Grundmerkmale: Achsenabschnitt, Stufe, Kit-Spruenge, Spielerzahl, Koennen, Inhalt."""
    L = nacht["stufe"]
    namen = ["const", "stufe", "s6", "s11", "s20"] + ["n_" + b[0] for b in BUCKETS[:-1]] + ["koennen"]
    werte = [1.0, (L - 13) / 12.0, float(L >= 6), float(L >= 11), float(L >= 20)]
    werte += [1.0 if bucket_of(nacht["n"]) == i else 0.0 for i in range(len(BUCKETS) - 1)]
    werte += [(nacht["skill"] - 0.55) / 0.15]
    pen = [0.0, 0.0, 0.01, 0.01, 0.01] + [0.01] * (len(BUCKETS) - 1) + [0.0]
    if mit_inhalt:
        for e in EREIGNISSE:
            namen.append("ereignis_" + e)
            werte.append(1.0 if nacht["ereignis"] == e else 0.0)
            pen.append(1.0)
        for b in BOSSE:
            namen.append("boss_" + b)
            werte.append(1.0 if nacht["boss"] == b else 0.0)
            pen.append(1.0)
    return namen, werte, pen


def modell(naechte, mit_inhalt=True, extra=None, extra_pen=0.0):
    """Gepooltes logistisches Modell. extra(nacht) -> (namen, werte) fuer zusaetzliche gestrafte Merkmale."""
    x, y, namen, pen = [], [], None, None
    for nt in naechte:
        nm, wv, pn = basis_merkmale(nt, mit_inhalt)
        if extra:
            en, ew = extra(nt)
            nm = nm + en
            wv = wv + ew
            pn = pn + [extra_pen] * len(en)
        namen, pen = nm, pn
        x.append(wv)
        y.append(float(nt["sieg"]))
    beta, se, cov = fit_logit(x, y, pen)
    return {"namen": namen, "beta": beta, "se": se, "cov": cov, "pen": pen}


def vorhersage(mod, nacht, mit_inhalt=True):
    nm, wv, _ = basis_merkmale(nacht, mit_inhalt)
    idx = {n: i for i, n in enumerate(mod["namen"])}
    return expit(sum(mod["beta"][idx[n]] * v for n, v in zip(nm, wv)))


def referenz_nacht(stufe, n, skill=0.55):
    return {"stufe": stufe, "n": n, "skill": skill, "ereignis": "NONE", "boss": "-"}
