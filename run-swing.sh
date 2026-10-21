#!/usr/bin/env sh
# Baut alles und startet die Beispielanwendung als Swing-Fenster (kein Webserver).
# Braucht eine grafische Oberflaeche.
#   ./run-swing.sh            Konfiguration aus beispiel/config
#   ./run-swing.sh ordner     anderer Konfigurationsordner
cd "$(dirname "$0")"
./build.sh > /dev/null || { ./build.sh; exit 1; }
java -cp "dist/objektviewer.jar:out/beispiel" de.objektbaum.beispiel.BeispielSwing "$@"
