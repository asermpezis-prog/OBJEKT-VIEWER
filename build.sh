#!/usr/bin/env sh
# Baut das Framework als dist/objektviewer.jar und kompiliert die Beispielanwendung.
# Java 17 oder neuer, keine Abhaengigkeiten.
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
  echo "Installierte JDKs anzeigen:  update-alternatives --list javac" >&2
  exit 1
fi

rm -rf out dist
mkdir -p out/framework out/beispiel dist

# 1. Framework -> dist/objektviewer.jar
javac -encoding UTF-8 --release 17 -d out/framework $(find framework/src -name "*.java") || exit 1
cp -R framework/resources/. out/framework/
jar --create --file dist/objektviewer.jar --main-class de.objektbaum.Init -C out/framework . || exit 1

# 2. Beispielanwendung gegen das JAR
javac -encoding UTF-8 --release 17 -cp dist/objektviewer.jar -d out/beispiel \
  $(find beispiel/src -name "*.java") || exit 1

echo "Gebaut: dist/objektviewer.jar und Beispielanwendung (out/beispiel)"
