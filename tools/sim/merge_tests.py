"""Fasst die Test-Datensätze mehrerer Läufe zusammen: merge_tests.py <out> <ordner1> <ordner2> …"""
import json, os, sys
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
names = ['test_varianten', 'test_spieler', 'test_stufe', 'test_zellen', 'test_klassen', 'test_uk', 'test_gegner']
for n in names:
    rows = []
    for d in sys.argv[2:]:
        rows += json.load(open(os.path.join(d, n + '.json'), encoding='utf-8'))
    with open(os.path.join(out, n + '.json'), 'w', encoding='utf-8') as fh:
        json.dump(rows, fh, ensure_ascii=False, separators=(',', ':'))
    print(n, len(rows), os.path.getsize(os.path.join(out, n + '.json')))
