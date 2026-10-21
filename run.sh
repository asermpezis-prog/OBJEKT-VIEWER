#!/usr/bin/env sh
# Kompiliert und startet den Objekt-Viewer (Java 21, keine Abhängigkeiten)
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java") || exit 1
java -cp out de.objektbaum.Main "$@"
