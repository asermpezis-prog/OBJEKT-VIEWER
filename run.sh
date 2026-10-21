#!/usr/bin/env sh
# Baut alles und startet die Beispielanwendung im Browser-Modus.
#   ./run.sh                          http://localhost:8080/
#   ./run.sh 9090                     anderer Port
#   ./run.sh 8080 0.0.0.0             aus dem Netzwerk erreichbar, z. B. http://hawk:8080/
#   ./run.sh 8080 localhost ordner    anderer Konfigurationsordner (Standard: beispiel/config)
cd "$(dirname "$0")"
./build.sh > /dev/null || { ./build.sh; exit 1; }
java -cp "dist/objektviewer.jar:out/beispiel" de.objektbaum.beispiel.BeispielApp "$@"
