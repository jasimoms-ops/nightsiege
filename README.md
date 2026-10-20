# Night Siege

Fabric-Mod für Minecraft 26.2. Belagerungen greifen deine Basis an, sobald du dort
zweimal geschlafen hast. Nur Vanilla-Mobs, keine Belohnungen, harte Strafe bei
Niederlage.

---

## Bevor du baust — bitte lesen

Dieser Mod wurde noch **nie gegen das echte Minecraft kompiliert und nie im Spiel
getestet**. Er wurde in einer Umgebung ohne Zugriff auf die Minecraft- und
Fabric-Bibliotheken geschrieben.

Damit er trotzdem nicht geraten ist, habe ich die API-Signaturen von Version 26.2
aus dem Bytecode deiner vorhandenen Siege-Core-Mod ausgelesen und den gesamten
Code gegen nachgebaute Klassen mit genau diesen Signaturen kompiliert. Das hat
Tippfehler und Typfehler ausgeschlossen, aber es ersetzt keinen echten Build.

**Rechne beim ersten Bauen mit ein paar Fehlern.** Diese Stellen sind am
wahrscheinlichsten betroffen:

| Datei | Was | Warum unsicher |
|---|---|---|
| `game/SleepWatch.java` | `player.isSleeping()` und `player.getSleepingPos()` | Die einzigen zwei Aufrufe, die ich nicht aus deiner alten Mod belegen konnte. Absichtlich in einer winzigen Datei isoliert. |
| `gradle.properties` | `fabric_version`, `loader_version` | Müssen auf die aktuellen Werte von https://fabricmc.net/develop/ gesetzt werden. |
| `build.gradle` | `fabric-loom` Version | Ebenfalls dort nachsehen. |
| `game/SiegeManager.java` | `ChatFormatting.DARK_RED` | Existiert mit sehr hoher Wahrscheinlichkeit, war aber nicht in der alten Mod belegt. |

Schick mir einfach die Fehlermeldungen, dann korrigiere ich sie.

---

## Bauen

Der Mod braucht **Mojang-Mappings**, nicht Yarn. Das ist in `build.gradle` bereits
eingestellt (`loom.officialMojangMappings()`) — bitte nicht auf Yarn umstellen,
sonst passt kein einziger Klassenname mehr.

**Mit IntelliJ IDEA (empfohlen):** Ordner als Gradle-Projekt öffnen, importieren
lassen, dann die Gradle-Aufgabe `build` ausführen.

**Auf der Kommandozeile:** Gradle 8.14 oder neuer und ein **JDK 25** werden gebraucht
(Minecraft 26.2 ist Java-25-Bytecode, mit einem aelteren JDK bricht der Build ab).

```
gradle wrapper      # einmalig, legt ./gradlew an
./gradlew build
```

Die fertige Datei liegt danach in `build/libs/nightsiege-1.0.0.jar`. Die Datei mit
`-sources` im Namen wird nicht gebraucht.

Tests der Spiellogik laufen mit `./gradlew test`.

---

## Spielablauf

**Basis anmelden.** Schlaf zweimal im selben Bett. Nach dem ersten Mal kommt eine
Fortschrittsmeldung, nach dem zweiten gilt das Bett als Basis. Baust du das Bett
ab, ist die Basis wieder weg.

**Vorwarnung.** Bei Sonnenuntergang kommt die Meldung, dass heute Nacht angegriffen
wird, inklusive Stufe. Bis zum Start bleiben etwa zweieinhalb Minuten.

**Die Belagerung.** Startet bei voller Nacht. Oben erscheint eine Boss-Leiste mit
Stufe und Restzeit. Mobs spawnen 20 bis 40 Blöcke entfernt und kommen in Schüben
nach. Sie greifen gezielt die Verteidiger an und brechen sich notfalls durch Wände.

**Gewonnen** hast du, wenn die Zeit abläuft. Übrige Mobs verschwinden, die Stufe
steigt um eins. Es gibt bewusst keine Belohnung.

**Verloren** ist es, wenn alle Teilnehmer gefallen sind. Dann wird am Ankerbett
alles im Umkreis von 12 Blöcken zerstört und die Basis abgemeldet. Kisten und
andere Behälter bleiben heil, damit du nicht deinen gesamten Besitz verlierst.

Wer stirbt, ist für den Rest der Belagerung draußen. Respawnen bringt dich nicht
zurück ins Rennen. Das Ankerbett während der Belagerung abzubauen gilt als
Niederlage, sonst wäre es ein billiger Ausweg. Loggen alle aus, ohne gestorben zu
sein, wird die Belagerung folgenlos abgebrochen.

**Mehrere Basen.** Es läuft immer nur eine Belagerung. Angegriffen wird das Bett
mit den meisten Spielern im Umkreis von 50 Blöcken.

---

## Schwierigkeit

Die Stufe gilt serverweit, startet bei 1 und steigt um eins pro bestandener
Belagerung sowie um eins pro 8 Ingame-Tage. Maximum ist 20.

| | Formel | bei Stufe 10 |
|---|---|---|
| Dauer | 4 Min + 20 Sek je Stufe, max 10 Min | 7 Min |
| Mobs je Schub | 6 + 2 je Stufe | 24 |
| Spieler-Faktor | mal 1 + 0,5 je weiterem Spieler | mal 2 bei 3 Spielern |
| Leben der Mobs | mal 1 + 0,08 je Stufe, max mal 2,5 | mal 1,72 |
| Schaden der Mobs | mal 1 + 0,06 je Stufe, max mal 2,0 | mal 1,54 |

Gleichzeitig sind höchstens 40 Mobs unterwegs, damit die Leistung nicht einbricht.

---

## Mobs aus anderen Mods hinzufügen

Das ist der Grund, warum der Mob-Pool in der Konfiguration steht und nicht im Code.
Öffne nach dem ersten Start `config/nightsiege.json` und ergänze unter `mobs`
einen Eintrag:

```json
{ "id": "friendsandfoes:iceologer", "minLevel": 8, "maxLevel": 0, "weight": 5 }
```

* `id` ist die Entity-ID. Steht meist auf der CurseForge-Seite der Mod, sonst
  findest du sie im Spiel über den F3-Bildschirm, wenn du den Mob anvisierst.
* `minLevel` ist die Stufe, ab der er auftaucht.
* `maxLevel` ist die Stufe, bis zu der er auftaucht. `0` heißt ohne Obergrenze.
* `weight` ist die relative Häufigkeit gegenüber den anderen Einträgen.

Ist die Mod nicht installiert, wird der Eintrag übersprungen und einmal ins Log
geschrieben. Es stürzt nichts ab, du kannst also Einträge für Mods vorbereiten, die
du erst später installierst.

Mit `/nightsiege mobs` siehst du im Spiel, welche Mobs auf der aktuellen Stufe
möglich sind und welche davon nicht gefunden wurden.

---

## Befehle

| Befehl | Wirkung |
|---|---|
| `/nightsiege info` | Stufe, laufende Belagerung, Bilanz |
| `/nightsiege bases` | alle angemeldeten Betten |
| `/nightsiege mobs` | Mob-Pool der aktuellen Stufe |
| `/nightsiege start` | Belagerung sofort starten |
| `/nightsiege stop` | Belagerung ohne Strafe beenden |
| `/nightsiege reload` | Konfiguration neu laden |

**Achtung:** Die Befehle sind für alle Spieler offen. Für einen öffentlichen Server
solltest du sie auf Operatoren beschränken. Dazu in `game/SiegeCommands.java`
direkt hinter `Commands.literal("nightsiege")` diese Zeile einfügen:

```java
.requires(source -> source.hasPermission(2))
```

Ich habe sie bewusst weggelassen, weil ich die Signatur nicht belegen konnte und
der Build auf jeden Fall durchlaufen sollte.

---

## Konfiguration

`config/nightsiege.json` entsteht beim ersten Start. Unsinnige Werte werden beim
Laden automatisch korrigiert, du kannst die Datei also nicht kaputt machen.

Die wichtigsten Schalter:

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `daysBetweenSieges` | 3 | Mindestabstand in Ingame-Tagen |
| `sleepsToRegisterBase` | 2 | wie oft geschlafen werden muss |
| `anchorRadius` | 50 | Umkreis für die Auswahl des Ziel-Betts |
| `failureBlastRadius` | 12 | Zerstörungsradius bei Niederlage |
| `failureBlastProtectsContainers` | true | Kisten überleben die Explosion |
| `diggingEnabled` | true | Mobs brechen Blöcke auf |
| `digTicksPerBlock` | 40 | wie lange ein Block hält, 20 Ticks = 1 Sekunde |
| `protectedBlockKeywords` | Liste | Blöcke, deren ID eines dieser Wörter enthält, bleiben heil |
| `maxLiveMobs` | 40 | Obergrenze gleichzeitiger Mobs |

---

## Bekannte Einschränkungen

* **Kein Sound, keine Partikel.** Bewusst weggelassen, weil ich die Signaturen
  nicht belegen konnte und der Build nicht daran scheitern sollte. Lässt sich
  leicht nachrüsten, sobald der Mod einmal baut.
* **Der Speicherstand liegt im Config-Ordner**, nicht im Weltordner. Mehrere Welten
  in derselben Installation teilen sich also Betten und Stufe. Die alte Siege-Core-Mod
  macht das genauso. Umstellen auf pro Welt geht, braucht aber eine API, die ich
  hier nicht prüfen konnte.
* **Die Tageszeit wird aus der Weltzeit berechnet.** Wer `/time set` benutzt,
  verschiebt dadurch den Zeitpunkt der Belagerung. Steht in `SiegeManager.onEndTick`
  und ist eine Zeile.

---

## Aufbau des Codes

Der Code ist bewusst zweigeteilt:

* `core/` enthält die gesamte Spiellogik ohne jeden Minecraft-Bezug: Formeln,
  Zeitplan, Mob-Auswahl, Blockschutz. Dieser Teil ist vollständig getestet.
* `game/` enthält die Anbindung ans Spiel: Spawnen, Boss-Leiste, Befehle,
  Speichern.

Wenn eine künftige Minecraft-Version etwas umbenennt, betrifft das nur `game/`.

