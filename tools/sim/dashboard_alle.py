#!/usr/bin/env python3
"""Fuehrt alle Team-Sim-Laeufe fuers Balance-Dashboard zusammen.

Aufruf (im Repo, nach `git fetch origin '+refs/heads/*balance-run-*:refs/remotes/origin/*balance-run-*'`):
    dashboard_alle.py <ausgabe-ordner> [--remote origin]

Liest jeden Branch dist-balance-run-* (dist/dashboard/*.json aus dashboard_data.py) und die
Commit-Nachricht des Laufs ([team …]) und schreibt je eine Datei mit den Spalten lauf und variante:
  sim_laeufe.json     ein Lauf je Zeile: Name, Art, Zeit, Testnaechte, Stufen, Spielerzahlen, Beschreibung
  sim_varianten.json  je Lauf und Variante: Einstellungen, Testnaechte, Siege, ob es der Server-Stand ist
  sim_quote.json      Siegquote je Lauf, Variante, Stufe und Spielerzahl
  sim_uk.json         je Lauf, Variante und Unterklasse: Siegquote der Gruppe, Schaden/erhalten/Heilung/Tode je Lauf
  sim_gegner.json     je Lauf und Variante die 15 gefaehrlichsten Gegner (Anteil am Schaden)
  sim_gruppen.json    Siegquote je Gruppenart und Spielerzahl
  manifest.json       Datensatz-ID im Dashboard -> Datei, mit Pruefsumme (fuer ein spaeteres Auto-Update)
Laeufe ohne Testnaechte (abgebrochen) fehlen.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from zoneinfo import ZoneInfo

BERLIN = ZoneInfo("Europe/Berlin")
HERE = os.path.dirname(os.path.abspath(__file__))

ARTEN = [("kriegnormal", "Vergleich"), ("krieg", "Kriegsführer"), ("probe", "Probe"), ("burg", "Probe"),
         ("eich", "Eichung"), ("kalib", "Eichung"), ("skal", "Skalierung"), ("klassen", "Klassen"),
         ("last", "Serverlast"), ("gross", "Großer Lauf")]
BOT_KNOBS = ("koennen", "wagemut")
# Vor dem grossen Lauf (gross1) spielten die Bots ohne Angabe perfekt und am Posten;
# seitdem geeicht (Koennen 0,7, Wagemut 1, TeamSim.CALIBRATED_*).
CALIBRATED_SINCE = 1793295900   # 2026-10-29 17:45 UTC, zwischen eich2 und gross1


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=False).stdout


def show_json(ref, path):
    out = git("show", "%s:%s" % (ref, path))
    try:
        return json.loads(out) if out.strip() else []
    except ValueError:
        return []


def dashboard_files(ref, team, lauf):
    """dist/dashboard/*.json eines Laufs; aeltere Laeufe haben nur summary.json, dann wie die CI mit
    dashboard_data.py erzeugen."""
    names = ("zellen", "unterklassen", "gegner", "mischungen", "lauf")
    if git("ls-tree", "--name-only", ref, "dist/dashboard/zellen.json").strip():
        return {n: show_json(ref, "dist/dashboard/%s.json" % n) for n in names}
    summary = git("show", "%s:dist/summary.json" % ref)
    if not summary.strip():
        return {n: [] for n in names}
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(os.path.join(tmp, "in"))
        with open(os.path.join(tmp, "in", "summary.json"), "w", encoding="utf-8") as fh:
            fh.write(summary)
        subprocess.run([sys.executable, os.path.join(HERE, "dashboard_data.py"), os.path.join(tmp, "in"),
                        os.path.join(tmp, "out"), "--lauf", lauf, "--team", team], capture_output=True, check=False)
        out = {}
        for n in names:
            try:
                out[n] = json.load(open(os.path.join(tmp, "out", n + ".json"), encoding="utf-8"))
            except (OSError, ValueError):
                out[n] = []
        return out


def art_of(name):
    for prefix, art in ARTEN:
        if name.startswith(prefix):
            return art
    return "Sonstiges"


def parse_team(msg):
    """[team L3,8 G1,2 N12 T1 voll k=v V=name:k=v,…] -> Teile."""
    m = re.search(r"\[team ([^\]]*)\]", msg or "")
    spec = {"stufen": "", "spieler": "", "modus": "", "gemeinsam": [], "varianten": {}}
    if not m:
        return spec
    for tok in m.group(1).split():
        if tok == "krieg":
            spec["modus"] = "krieg"
        elif tok == "voll":
            spec["gemeinsam"].append("voller Skillbaum")
        elif re.fullmatch(r"L[\d,]+", tok):
            spec["stufen"] = tok[1:].replace(",", ", ")
        elif re.fullmatch(r"G[\d,]+", tok):
            spec["spieler"] = tok[1:].replace(",", ", ")
        elif re.fullmatch(r"[NT]\d+", tok):
            pass
        elif tok.startswith("V="):
            name, _, kvs = tok[2:].partition(":")
            spec["varianten"][name] = [kv for kv in kvs.split(",") if kv]
        elif "=" in tok:
            spec["gemeinsam"].append(tok)
    return spec


def describe(msg):
    first = (msg or "").splitlines()[0] if msg else ""
    first = re.sub(r"\[[^\]]*\]", "", first).strip()
    return re.sub(r"^Team-Sim\s*", "", first).strip(" :") or first


def nice(kvs):
    return ", ".join(kv.replace("=", " = ") for kv in kvs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--remote", default="origin")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    refs = git("for-each-ref", "--format=%(refname:short)\t%(subject)\t%(committerdate:unix)",
               "refs/remotes/%s/dist-balance-run-*" % a.remote).splitlines()

    laeufe, varianten, quote, uk, gegner, gruppen = [], [], [], [], [], []
    for line in refs:
        ref, subject, stamp = line.split("\t")
        when = datetime.datetime.fromtimestamp(int(stamp), BERLIN).strftime("%Y-%m-%d %H:%M")
        name = ref.split("/dist-", 1)[1]                       # balance-run-skal4
        kurz = name[len("balance-run-"):]
        found = re.search(r"[0-9a-f]{40}", subject)
        sha = found.group(0) if found else ""
        msg = git("log", "-1", "--format=%B", sha) if sha else ""
        team = (re.search(r"\[team [^\]]*\]", msg) or [""])[0] if re.search(r"\[team [^\]]*\]", msg) else ""
        team = team[6:-1] if team else ""
        if int(stamp) < CALIBRATED_SINCE and "koennen=" not in team.split("V=")[0]:
            team = "koennen=1 wagemut=0 " + team
        data = dashboard_files(ref, team, name)
        zellen = data["zellen"]
        nights = sum(z.get("laeufe", 0) for z in zellen)
        if not nights:
            continue
        spec = parse_team(msg)
        info = (data["lauf"] or [{}])[0]
        common_bot = [kv for kv in spec["gemeinsam"] if kv.split("=")[0].split(".")[0] in BOT_KNOBS]
        common_cfg = [kv for kv in spec["gemeinsam"] if kv not in common_bot]
        art = "Kriegsführer" if spec["modus"] == "krieg" else art_of(kurz)
        laeufe.append({
            "lauf": kurz, "art": art, "zeit": when, "datum": when[:10],
            "testnaechte": nights, "boden": info.get("boden", ""), "stufen": spec["stufen"], "spieler": spec["spieler"],
            "beschreibung": describe(msg) or kurz, "gemeinsam": nice(spec["gemeinsam"]), "quelle": sha[:8],
        })

        per_var = {}
        for z in zellen:
            v = per_var.setdefault(z["variante"], {"testnaechte": 0, "siege": 0, "koennen": z.get("koennen"),
                                                   "wagemut": z.get("wagemut"), "bots": z.get("variante_name", "")})
            v["testnaechte"] += z["laeufe"]
            v["siege"] += z["siege"]
            quote.append({"lauf": kurz, "variante": z["variante"], "stufe": z["stufe"], "spieler": z["spieler"],
                          "laeufe": z["laeufe"], "siege": z["siege"], "siegquote": z["siegquote"],
                          "tode_je_lauf": z.get("tode_je_lauf", 0), "sekunden_je_lauf": z.get("sekunden_je_lauf", 0)})
        for vname, v in per_var.items():
            own = spec["varianten"].get(vname, [])
            own_cfg = [kv for kv in own if kv.split("=")[0].split(".")[0] not in BOT_KNOBS]
            server = vname == "aktuell" and not common_cfg
            if vname == "aktuell":
                text = "Server-Config" + (" mit " + nice(common_cfg) if common_cfg else "")
            else:
                text = nice(common_cfg + own_cfg) or "Server-Config"
            varianten.append({
                "lauf": kurz, "variante": vname, "id": kurz + "/" + vname, "einstellungen": text,
                "bots": v["bots"], "koennen": v["koennen"], "wagemut": v["wagemut"],
                "server_stand": server, "testnaechte": v["testnaechte"], "siege": v["siege"],
                "siegquote": round(v["siege"] / max(1, v["testnaechte"]), 4),
            })

        for u in data["unterklassen"]:
            uk.append(dict({"lauf": kurz}, **u))
        top = {}
        for g in sorted(data["gegner"], key=lambda g: -g.get("schaden", 0)):
            if g.get("typ", "").endswith(("in_wall", "arrow")) or not g.get("schaden"):
                continue
            if top.get(g["variante"], 0) >= 15:
                continue
            top[g["variante"]] = top.get(g["variante"], 0) + 1
            gegner.append(dict({"lauf": kurz}, **g))
        for m in data["mischungen"]:
            gruppen.append(dict({"lauf": kurz}, **m))

    laeufe.sort(key=lambda r: r["zeit"], reverse=True)
    files = {
        "sim_laeufe": ("sim_laeufe.json", laeufe), "sim_varianten": ("sim_varianten.json", varianten),
        "sim_quote": ("sim_quote.json", quote), "sim_uk": ("sim_uk.json", uk),
        "sim_gegner": ("sim_gegner.json", gegner), "sim_gruppen": ("sim_gruppen.json", gruppen),
    }
    manifest = {}
    for ds, (fname, rows) in files.items():
        body = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
        with open(os.path.join(a.out, fname), "w", encoding="utf-8") as fh:
            fh.write(body)
        manifest[ds] = {"datei": fname, "zeilen": len(rows), "bytes": len(body.encode()),
                        "sha256": hashlib.sha256(body.encode()).hexdigest()}
    with open(os.path.join(a.out, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=1)
    print("%d Läufe, %d Varianten" % (len(laeufe), len(varianten)))
    for ds, m in manifest.items():
        print("  %-14s %5d Zeilen %8d Bytes" % (ds, m["zeilen"], m["bytes"]))


if __name__ == "__main__":
    main()
