# Balance-Dashboard „Night Siege Balance“

Seite: https://claude.ai/artifact/LAD9XvNpzRBoRiBzN9ZVuk (Dashboard-Typ, Inhalt im Artifact-Speicher).
Quelle der Seite: `tools/sim/dashboard/index.html` (wird als `files/index.html` in den Speicher geschrieben).

## Tabs
- **Überblick:** Siegquote je Spielerzahl für den Server-Stand (Variante `aktuell` im neuesten geeichten Lauf mit mindestens 3 Spielerzahlen), einen Vergleich (Variante mit Siegquoten am nächsten an 70 %, wählbar) und die echten Nächte. Kennzahlen, neueste Läufe.
- **Läufe vergleichen:** beliebige zwei Lauf/Variante-Paare A und B: Siegquote je Spielerzahl und je Stufe, Stufe × Spieler (A, B, B − A), Gegner, Gruppenarten, Liste aller Läufe.
- **Klassen:** dieselbe Auswahl A/B: Unterklassen je Wert (Schaden, Eingesteckt, Heilung, Tode, Siegquote), Tabelle, „Was jede Unterklasse ihrer Gruppe bringt“.
- **Echte Abende:** aus den Logger-Dateien des Servers.
- **Analysen:** Eichung, Skalierungs-Vorschlag, Kill-Ziel-Auswertung, Begriffe.

## Datensätze
| ID | Datei | Herkunft |
|---|---|---|
| `sim_laeufe`, `sim_varianten`, `sim_quote`, `sim_uk`, `sim_gegner`, `sim_gruppen` | `sim_*.json` | `dashboard_alle.py` aus allen `dist-balance-run-*` |
| `echte_naechte`, `echte_zellen`, `echte_unterklassen`, `echte_gegner`, `echte_dungeons` | `echte_*.json` | `echte_naechte.py` aus `playtest/nightsiege/stats` (Server) |
| `eichung` | `eichung.json` | `eichung.py` |
| `uk_beitrag`, `skal_vorschlag`, `skal_ohne_kill` | je eine Datei | einzelne Auswertungen vom 30.10. (nicht automatisch) |

Die Seite rechnet daraus (dash.calc): `sim_je_spieler`, `sim_je_stufe`, `sim_spanne`, `auswahl`, `kennzahlen`, `echt_je_spieler`, `echt_gesamt`.
Der Speicher-Eintrag `auto/stand` hält die Prüfsummen der zuletzt hochgeladenen `sim_*`-Dateien.

## Sim-Daten auf den Stand bringen (von Hand)
```
git fetch origin '+refs/heads/dist-balance-run-*:refs/remotes/origin/dist-balance-run-*' \
                 '+refs/heads/balance-run-*:refs/remotes/origin/balance-run-*'
python3 tools/sim/dashboard_alle.py /tmp/dash
```
Dann für jede Datei, deren `sha256` in `/tmp/dash/manifest.json` von `auto/stand` abweicht:
1. Artifact `publish` mit `url`, `asset: true`, `file_path` = die Datei → neue `/_blob/<id>`.
2. ArtifactData `update` von `datasets/<id>`: `source.url`, `source.name`, `updated {at, by}`.
3. Am Ende `auto/stand` mit den neuen Prüfsummen aktualisieren.
Neue Läufe erscheinen danach von selbst in allen Auswahllisten; die Seite muss nicht geändert werden.

## Später: automatisch (vorbereitet, noch nicht angelegt)
Simon 30.10. 03:59: alle 3 Stunden, ab 5. Dezember; Sim und echte Abende. Für den Anfang gestrichen, „später einfach hinzufügen“.
- **Sim:** geplanter Auftrag in der Cloud, Ablauf wie oben. Braucht keinen PC.
- **Echte Abende:** Die Logger-Dateien liegen nur im MintServers-Panel, erreichbar über den eingebauten Browser auf Simons PC (Auftrag mit „braucht diesen Computer“). Plan: ein Browser-Skript liest `playtest/nightsiege/stats` über die Panel-API, ersetzt Spielernamen durch Nummern und gibt nur die Felder zurück, die `echte_naechte.py` braucht; `echte_naechte.py` wertet das aus und schreibt die `echte_*.json`. Läuft der PC nicht, bleibt der Teil liegen und die Sim läuft trotzdem.
- Vorschlag für den Auftrag: `0 */3 5-31 12 *` in Europe/Berlin (5.–31. Dezember), danach verlängern.
