"""Survival-Langlauf (Tor 12 E): wertet server-output.txt aus.

Je Abschnitt (eine Einladung bis zur naechsten) die [langlauf]-Zeilen und die ERROR-/Exception-Zeilen
des Servers; Ausgabe als Text (fuer survival.md) und Exit 0. Aufruf: python3 langlauf.py server-output.txt
"""
import re
import sys

text = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
sections = [("vor Nacht 1", [], [])]
for line in text:
    if "[langlauf] ABSCHNITT" in line:
        sections.append((line[line.index("ABSCHNITT") + 10:].strip()[:120], [], []))
    if "[langlauf]" in line and "[langlauf-skript]" not in line:
        sections[-1][1].append(line[line.index("[langlauf]") + 11:].strip()[:400])
    elif "[langlauf-skript]" in line:
        sections[-1][1].append(line.strip()[:200])
    elif re.search(r"\bERROR\b|Exception|Caused by", line) and "[langlauf]" not in line:
        sections[-1][2].append(line.strip()[:300])

fails = 0
for head, lines, errors in sections:
    print("== " + head)
    for l in lines:
        if l.startswith(("OK", "FAIL", "NACHT", "NEUSTART", "WEITER", "SUMME", "EXTRA", "Abmelden", "Wieder", "nach Neustart", "BURG", "START", "ARTEN", "FEHLERLISTE", "HORN", "NACHZUEGLER", "RUNDE")) or "langlauf-skript" in l:
            print("   " + l)
    distinct = []
    for e in errors:
        key = re.sub(r"^\[[^\]]*\]\s*", "", e)[:160]
        if key not in distinct:
            distinct.append(key)
    if errors:
        fails += 1
        print("   FAIL Log: %d ERROR/Exception-Zeilen, z. B.:" % len(errors))
        for d in distinct[:6]:
            print("      " + d)
    else:
        print("   OK Log: kein ERROR, keine Exception")
print("LOG-ABSCHNITTE MIT FEHLERN: %d von %d" % (fails, len(sections)))
