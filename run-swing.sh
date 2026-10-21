#!/usr/bin/env sh
# Kompiliert und startet die Swing-Version (eigenes Fenster, kein Webserver).
# Braucht eine grafische Oberflaeche. Java 17 oder neuer, keine Abhaengigkeiten.
cd "$(dirname "$0")"

v=$(javac -version 2>&1 | grep -o 'javac [0-9][0-9.]*' | cut -d' ' -f2)
major=$(echo "$v" | cut -d. -f1)
[ "$major" = "1" ] && major=$(echo "$v" | cut -d. -f2)
if [ -z "$major" ]; then
  echo "Kein javac gefunden. Bitte ein JDK 17 oder neuer installieren." >&2
  exit 1
fi
if [ "$major" -lt 17 ]; then
  echo "javac $v ist zu alt, benoetigt wird Java 17 oder neuer." >&2
  exit 1
fi

rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java") || exit 1
java -cp out de.objektbaum.MainSwing "$@"
