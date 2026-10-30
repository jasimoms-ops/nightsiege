"""Datensätze für den Tab „Ausführlicher Test“ im Balance-Dashboard.

Aufruf: test_datasets.py <stats-ordner> <dashboard-ordner> <ausgabe-ordner>
<dashboard-ordner> = dist/dashboard des Laufs (gegner.json, unterklassen.json für Namen).
Siege wie im Dashboard: Burg gehalten und Kill-Ziel erreicht.
"""
import collections
import json
import math
import os
import statistics as st
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from test_load import load  # noqa: E402

RUNS = {
    'final1': {
        'aktuell': ('Wellen', 'Schritt 1: Klassen-Paket, Magier und Henker ab Stufe 11, Stufe 5 schwerer; Monster kommen in Wellen (Limit 9 … 114 je Spielerzahl)'),
        'nachschub': ('Nachschub', 'Schritt 1 und 2: dazu Nachschub (Feld 6 … 38 je Spielerzahl, jedes gefallene Monster kommt sofort nach) und Feld-Stärke 0,5'),
    },
    'final3': {
        'aktuell': ('Neues Balancing', 'Alles, was auf den Server geht: Klassen-Paket mit Axtkämpfer 1,35, Seelenfänger 1,2, Totenbeschwörer 1,0; Pfeilregen auf F; Nachschub (Feld 6 … 38) mit Feld-Stärke 0,4 und angepasster Referenz für 4–10 Spieler; Wächter-Nacht wie im Dungeon; Leiter-Fix'),
        'regen2': ('Pfeilregen ×2', 'Wie Neues Balancing, aber der Pfeilregen macht doppelten Schaden'),
    },
    'final5': {
        'aktuell': ('Server-Stand + Mörser-Fix', 'Server-Stand seit 30.10. 22:25 (Feld-Stärke 0,4); in der Sim zielen Sprengmeister-Bots jetzt ihre Mörser und Techniker überladen ihre Türme (Sim-Fix moerser-sim)'),
        'r4': ('Referenz 4 Spieler 26', 'Wie Server-Stand + Mörser-Fix, aber Feld-Referenz für 4 Spieler 26 statt 32 (Monster für 4 Spieler etwas schwächer)'),
    },
    'final4': {
        'aktuell': ('Feld-Stärke 0,45', 'Wie Server-Stand, aber Feld-Stärke 0,45 (so lief der Server 30.10. 21:57–22:25; Gruppen mit 4 Spielern 61 %)'),
        's40': ('Server-Stand 30.10.', 'Was seit 30.10. 22:25 auf dem Server läuft: Klassen-Paket mit Axtkämpfer 1,5, Seelenfänger 1,2, Totenbeschwörer 1,0; Pfeilregen auf F mit halbem Schaden; Nachschub (Feld 6 … 38) mit Feld-Stärke 0,4; Wächter-Nacht wie im Dungeon; Leiter-Fix'),
    },
}
NAMES = {}
CLASS_ORDER = ['Krieger', 'Bogenschütze', 'Wächter', 'Techniker', 'Beschwörer']


def r(x, n=4):
    return None if x is None or (isinstance(x, float) and math.isnan(x)) else round(x, n)


def main(stats, dashdir, out, lauf='final1'):
    global NAMES
    NAMES = RUNS.get(lauf, {})
    os.makedirs(out, exist_ok=True)
    N = load([stats])
    raw = {}
    for f in os.listdir(stats):
        if f.endswith('.json'):
            try:
                raw[os.path.join(stats, f)] = json.load(open(os.path.join(stats, f)))
            except ValueError:
                pass
    uk_info = {}
    for u in json.load(open(os.path.join(dashdir, 'unterklassen.json'))):
        uk_info[u['unterklasse']] = (u['name'], u['klasse'])
    variants = sorted({n['var'] for n in N}, key=lambda v: (v != 'aktuell', v))
    vname = lambda v: NAMES.get(v, (v, v))[0]

    # per night: load figures
    for n in N:
        d = raw.get(n['file'], {})
        W = d.get('wellen', [])
        ms = [w.get('msptSchnitt') for w in W if w.get('msptSchnitt') is not None]
        n['ms'] = st.mean(ms) if ms else None
        n['o50'] = 100.0 * sum(w.get('ticksUeber50', 0) or 0 for w in W) / max(1, (d.get('dauerSekunden') or 1) * 20)
        lv = [w.get('lebendMax') for w in W if w.get('lebendMax') is not None]
        n['live'] = max(lv) if lv else None
        mb = [w.get('mobsMax') for w in W if w.get('mobsMax') is not None]
        n['mobs'] = max(mb) if mb else None

    def agg(ns):
        k = len(ns)
        ms = [n['ms'] for n in ns if n['ms'] is not None]
        return dict(laeufe=k, siege=sum(n['win'] for n in ns), siegquote=r(st.mean(n['win'] for n in ns)) if k else None,
                    tode_je_nacht=r(st.mean(n['deaths'] for n in ns), 2) if k else None,
                    kills_je_nacht=r(st.mean(n['kills'] or 0 for n in ns), 1) if k else None,
                    ziel_je_nacht=r(st.mean(n['killZiel'] or 0 for n in ns), 1) if k else None,
                    ms_tick=r(st.mean(ms), 1) if ms else None,
                    ueber50=r(st.mean(n['o50'] for n in ns), 1) if k else None,
                    monster_max=r(st.mean(n['live'] for n in ns if n['live'] is not None), 1) if k else None,
                    mobs_max=r(st.mean(n['mobs'] for n in ns if n['mobs'] is not None), 1) if k else None)

    Gs = sorted({n['G'] for n in N})
    Ls = sorted({n['L'] for n in N})
    gmax = max(Gs)
    tv, ts, tl, tz = [], [], [], []
    for v in variants:
        ns = [n for n in N if n['var'] == v]
        a = agg(ns)
        big = agg([n for n in ns if n['G'] == gmax])
        tv.append(dict(variante=v, name=vname(v), beschreibung=NAMES.get(v, (v, v))[1], testnaechte=a['laeufe'], siege=a['siege'],
                       siegquote=a['siegquote'], tode_je_nacht=a['tode_je_nacht'], kills_je_nacht=a['kills_je_nacht'],
                       spieler_gross=gmax, ms_tick_gross=big['ms_tick'], ueber50_gross=big['ueber50'], monster_max_gross=big['monster_max']))
        for G in Gs:
            ts.append(dict(variante=v, name=vname(v), spieler=G, **agg([n for n in ns if n['G'] == G])))
        for L in Ls:
            x = agg([n for n in ns if n['L'] == L])
            tl.append(dict(variante=v, name=vname(v), stufe=L, laeufe=x['laeufe'], siege=x['siege'], siegquote=x['siegquote'], tode_je_nacht=x['tode_je_nacht']))
        for L in Ls:
            for G in Gs:
                x = [n for n in ns if n['L'] == L and n['G'] == G]
                if x:
                    tz.append(dict(variante=v, name=vname(v), stufe=L, spieler=G, laeufe=len(x), siege=sum(n['win'] for n in x),
                                   siegquote=r(st.mean(n['win'] for n in x))))

    # subclasses and classes
    cell = collections.defaultdict(list)
    for n in N:
        if n['kind'] == 'zufall':
            cell[(n['var'], n['L'], n['G'])].append(n['win'])
    cm = {k: st.mean(x) for k, x in cell.items()}
    per = collections.defaultdict(lambda: collections.defaultdict(list))
    percls = collections.defaultdict(lambda: collections.defaultdict(list))
    con = collections.defaultdict(list)
    for n in N:
        ps = list(n['players'].values())
        m = sum(p['dmg'] for p in ps) / len(ps) if ps else 0
        for p in ps:
            k = (n['var'], p['sub'])
            per[k]['anteil'].append(p['dmg'] / m if m > 0 else 0)
            per[k]['schaden'].append(p['dmg'])
            per[k]['eingesteckt'].append(p['taken'])
            per[k]['heilung'].append(p['heal'])
            per[k]['geheilt'].append(p['healed'])
            per[k]['abgefangen'].append(p['raw'])
            per[k]['treffer'].append(p['hits'])
            per[k]['lebensraub'].append(p['steal'])
            per[k]['tode'].append(p['deaths'])
            per[k]['sieg'].append(n['win'])
            cls = uk_info.get(p['sub'], (p['sub'], p['cls']))[1]
            percls[(n['var'], cls)]['anteil'].append(p['dmg'] / m if m > 0 else 0)
            percls[(n['var'], cls)]['tode'].append(p['deaths'])
        if n['kind'] == 'zufall':
            for s in {p['sub'] for p in ps}:
                con[(n['var'], s)].append(n['win'] - cm[(n['var'], n['L'], n['G'])])
            for c in {uk_info.get(p['sub'], (p['sub'], p['cls']))[1] for p in ps}:
                con[(n['var'], 'K:' + c)].append(n['win'] - cm[(n['var'], n['L'], n['G'])])
    tu = []
    for (v, s), d in sorted(per.items(), key=lambda kv: (kv[0][0] != 'aktuell', kv[0][0],
                                                         CLASS_ORDER.index(uk_info.get(kv[0][1], ('', 'x'))[1]) if uk_info.get(kv[0][1], ('', 'x'))[1] in CLASS_ORDER else 9,
                                                         -st.mean(kv[1]['anteil']))):
        name, cls = uk_info.get(s, (s, ''))
        c = con.get((v, s), [])
        tu.append(dict(variante=v, name=vname(v), unterklasse=s, uname=name, klasse=cls, naechte=len(d['anteil']),
                       anteil=r(st.mean(d['anteil']), 3), schaden=round(st.mean(d['schaden'])), eingesteckt=round(st.mean(d['eingesteckt'])),
                       heilung=round(st.mean(d['heilung'])), geheilt=round(st.mean(d['geheilt'])),
                       abgefangen=round(st.mean(d['abgefangen'])) if any(d['abgefangen']) else None,
                       treffer=round(st.mean(d['treffer'])) if any(d['treffer']) else None,
                       lebensraub=round(st.mean(d['lebensraub'])) if any(d['abgefangen']) else None,
                       tode=r(st.mean(d['tode']), 2), siegquote=r(st.mean(d['sieg'])),
                       beitrag=r(st.mean(c), 3) if c else None))
    tk = []
    for v in variants:
        for c in CLASS_ORDER:
            d = percls.get((v, c))
            if not d:
                continue
            cc = con.get((v, 'K:' + c), [])
            tk.append(dict(variante=v, name=vname(v), klasse=c, anteil=r(st.mean(d['anteil']), 3), tode=r(st.mean(d['tode']), 2),
                           beitrag=r(st.mean(cc), 3) if cc else None, spielernaechte=len(d['anteil'])))

    tg = []
    gg = json.load(open(os.path.join(dashdir, 'gegner.json')))
    for v in variants:
        rows = [g for g in gg if g['variante'] == v and g.get('schaden') and not g['typ'].endswith(('arrow', 'in_wall'))]
        tot = sum(g['schaden'] for g in rows) or 1
        for i, g in enumerate(sorted(rows, key=lambda g: -g['schaden'])[:12]):
            tg.append(dict(variante=v, name=vname(v), rang=i + 1, gegner=g['name'], typ=g['typ'], anteil=r(g['schaden'] / tot), gespawnt=g.get('gespawnt')))

    files = {'test_varianten': tv, 'test_spieler': ts, 'test_stufe': tl, 'test_zellen': tz, 'test_klassen': tk, 'test_uk': tu, 'test_gegner': tg}
    for k, rows in files.items():
        rows[:] = [dict({'lauf': lauf}, **row) for row in rows]
        with open(os.path.join(out, k + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(rows, fh, ensure_ascii=False, separators=(',', ':'))
        print(k, len(rows), os.path.getsize(os.path.join(out, k + '.json')))


if __name__ == '__main__':
    # test_datasets.py <stats> <dashboard> <out> [lauf]
    main(*sys.argv[1:5])
