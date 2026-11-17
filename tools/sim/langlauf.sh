#!/bin/bash
# Survival-Langlauf (Tor 12 E): echter Server ohne Grafik, neue Welt, Bots spielen den ganzen Kreislauf.
# Der Server startet neu, solange der Lauf die Datei run/langlauf-neustart hinterlegt (Neustart-Faelle).
# Aufruf: LANGLAUF="spieler=2 bis=24 min=300" tools/sim/langlauf.sh [gradle-Argumente]
set +e
mkdir -p run/config
echo "eula=true" > run/eula.txt
printf "level-seed=nightsiege\nonline-mode=false\nmax-tick-time=-1\ndifficulty=hard\nspawn-protection=0\nview-distance=8\nsimulation-distance=8\nsync-chunk-writes=false\n" > run/server.properties
rm -rf run/world run/langlauf-state.json run/langlauf.txt run/langlauf-neustart run/config/nightsiege.json
export NIGHTSIEGE_LANGLAUF="${LANGLAUF:-spieler=1 bis=24 min=300}"
if [ -f tools/sim/burg.json.gz ]; then export NIGHTSIEGE_LANGLAUF_KARTE="$PWD/tools/sim/burg.json.gz"; fi
: > server-output.txt
for i in 1 2 3 4 5 6; do
  timeout "${LANGLAUF_TIMEOUT:-19000}" gradle runServer --no-daemon --args=nogui "$@" >> server-output.txt 2>&1
  echo "[langlauf-skript] exit=$? start=$i" >> server-output.txt
  [ -f run/langlauf-neustart ] || break
  echo "[langlauf-skript] Neustart $i" >> server-output.txt
done
