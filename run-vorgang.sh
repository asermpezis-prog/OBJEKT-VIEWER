#!/usr/bin/env sh
# Baut alles und startet das Beispiel "Vorgang suchen" als Swing-Fenster:
# Anmeldung (demo/demo) -> VorgangsNummer 100000001 -> Vorgang lesen -> Baum baut sich per Klick auf.
#   ./run-vorgang.sh            Konfiguration aus beispiel/config
#   ./run-vorgang.sh ordner     anderer Konfigurationsordner
cd "$(dirname "$0")"
./build.sh > /dev/null || { ./build.sh; exit 1; }
java -cp "dist/objektviewer.jar:out/beispiel" de.objektbaum.beispiel.BeispielVorgang "$@"
