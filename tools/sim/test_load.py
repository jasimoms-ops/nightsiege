import json, glob, sys, collections, statistics as st

def load(dirs):
    nights = []
    for d in dirs:
        for f in sorted(glob.glob(d + '/*.json')):
            try:
                n = json.load(open(f))
            except Exception:
                continue
            sim = n.get('sim') or ''
            parts = sim.split('|')
            if len(parts) < 5:
                continue
            var, L, G, kind, seed = parts[:5]
            held = n.get('ergebnis') in ('test_gehalten', 'gehalten', 'gewonnen'); win = held and (n.get('kills') or 0) >= (n.get('killZiel') or 0)
            players = {}
            for name, p in n['spieler'].items():
                players[name] = dict(sub=p.get('unterklasse'), cls=p.get('klasse'), dmg=0.0, taken=0.0, heal=0.0,
                                     kills=0, deaths=0, src=collections.Counter(), tak=collections.Counter())
            for w in n.get('wellen', []):
                for name, s in (w.get('spieler') or {}).items():
                    if name not in players:
                        continue
                    p = players[name]
                    p['dmg'] += s.get('schaden', 0) or 0
                    p['taken'] += s.get('erhalten', 0) or 0
                    p['heal'] += s.get('heilungGegeben', 0) or 0
                    p['kills'] += s.get('kills', 0) or 0
                    p['deaths'] += s.get('tode', 0) or 0
                    for k, v in (s.get('schadenNachQuelle') or {}).items():
                        p['src'][k] += v
                    for k, v in (s.get('erhaltenNachGegner') or {}).items():
                        p['tak'][k] += v
            nights.append(dict(file=f, var=var, L=int(L[1:]), G=int(G[1:]), kind=kind, seed=seed, win=win,
                               res=n.get('ergebnis'), dur=n.get('dauerSekunden'), deaths=n.get('tode', 0),
                               players=players, gegner=n.get('gegner', {}), killZiel=n.get('killZiel'), kills=n.get('kills')))
    return nights
