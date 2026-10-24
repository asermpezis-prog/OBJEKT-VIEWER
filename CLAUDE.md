# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Framework zum Anzeigen/Bearbeiten von Objektbäumen im Browser (eingebauter `com.sun.net.httpserver`) oder als Swing-Fenster.
Reines Java 17, **keine Bibliotheken, kein Maven/Gradle**. Code, Kommentare, Meldungen und README sind auf Deutsch – so beibehalten.
Die README.md ist die Nutzerdokumentation (viewer.xml-Elemente, swing.properties, Java-API) und wird bei neuen Features mitgepflegt.

## Befehle

```
./build.sh            # javac --release 17 -> dist/objektviewer.jar (Main: de.objektbaum.Init) + out/beispiel
./run.sh [port] [host] [configDir]   # Browser-Beispiel (BeispielApp), Standard localhost:8080, beispiel/config
./run-swing.sh        # Swing-Beispiel (BeispielSwing)
./run-vorgang.sh      # Swing: Anmeldung demo/demo -> Vorgang 100000001 -> Baum per Klick (BeispielVorgang)
java -jar dist/objektviewer.jar init <ordner> [--force]   # Konfigurationsordner aus den Standardvorlagen anlegen
java -jar dist/objektviewer.jar check <ordner>            # viewer.xml prüfen
```

Kein Linter, kein Unit-Test-Framework. Die Tests in `test/` sind Fenstertests (java.awt.Robot, Screenshots, Prüfung des Datenmodells), jeweils mit eigenem `main`; ein Test = eine Datei:

```
./build.sh
cp -r beispiel/config /tmp/testcfg      # Tests verändern den Konfigurationsordner!
javac -cp dist/objektviewer.jar:out/beispiel -d out/test test/VorgangTest.java
xvfb-run -a -s "-screen 0 1600x1000x24" java -cp "dist/objektviewer.jar:out/beispiel:out/test" VorgangTest /tmp/testcfg /tmp/shots
```
(`SwingTest` analog.) Beide löschen die gespeicherten Swing-Einstellungen (Java Preferences `de/objektbaum/viewer`). Screenshots in `/tmp/shots` ansehen, um UI-Änderungen zu prüfen.

## Architektur

- `framework/src/de/objektbaum/` – Modell, Konfiguration, Browser-Rendering; `framework/src/de/objektbaum/swing/` – Swing-Oberfläche.
  `framework/resources/de/objektbaum/defaults/` – Standardfassungen (`viewer.xml` mit allen Elementen als Kommentar, `swing.properties`, `page.html`, `style.css`, `app.js`), ins JAR kopiert und von `init` ausgegeben.
- `beispiel/` – Beispielanwendung gegen das JAR; `beispiel/config/` ist ihr live bearbeitbarer Konfigurationsordner.
- **Einstieg `ObjectViewer`** (Builder): hält Wurzel-`ObjectNode`, `ViewRegistry`, `Bindings`, `Templates`, `HtmlRenderer`, Login/Search/Click-Handler, `NodeStyle`-Regel, Tree-Buttons; startet `TreeServer` (`startWeb`) bzw. `SwingViewer` (`openSwing`).
- **Live-Konfiguration ohne Neustart:** `ConfigStore` liest `viewer.xml` (über `XmlConfigReader`) bei Zugriff neu, wenn sich Datei/Größe geändert haben (max. alle 250 ms). Bei Fehlern bleibt die letzte gültige `ViewerConfig` aktiv, `error()` liefert die Meldung mit Zeilennummer (rotes Band in beiden UIs). Swing pollt per `javax.swing.Timer` (1 s) `configStamp()` und `swing.properties` (`SwingStyle`) und baut neu auf, wobei Auswahl, Aufklappzustand, Filter und ungespeicherte Eingaben erhalten bleiben müssen.
- **Ansichtsauflösung (`ViewRegistry.resolve`):** eigene Ansicht des Knotens → viewer.xml → im Code registriert → Standarddarstellung.
- **Code ↔ XML:** `Bindings` verbindet Namen aus viewer.xml (`action=`, `source=`) mit Java (`Button.Action`, `OptionSource`).
- **Ansichtsmodell:** `NodeView` → `Column` (1–3) → `Segment` → `Element`s (`FieldTable`, `TextField`, `NumberField`, `DateField`, `CheckBox`, `ComboBox`, `ComboList`, `Button`). Eingaben werden als Strings in den `ObjectNode`-Feldern gespeichert; Prüfung über `Element.Input#check`/`FormSubmit` – ist eine Eingabe ungültig, wird nichts gespeichert und keine Aktion ausgeführt.
- **Neues Element hinzufügen** berührt mehrere Stellen: Element-Klasse, `XmlConfigReader` (Parsing + erlaubte Attribute für die Fehlermeldung), `HtmlRenderer` (+ ggf. `app.js`/`style.css`), `swing/FramesPanel`, kommentierte `defaults/viewer.xml`, README-Tabelle.
- Browser: `TreeServer` (HTTP, Formulare, `/static/<name>` nur aus `templates/`) + `HtmlRenderer` + `Templates` (Datei in `templates/` ersetzt die JAR-Fassung vollständig; Platzhalter `{{…}}`, Inhalte bereits maskiert).
- Swing: `SwingViewer` orchestriert `LoginPanel` → `SearchPanel` → `NavTree`/`TreeTablePanel` + `FramesPanel`; `Splits` speichert Frame-Größen in Java Preferences, geschlüsselt nach Segmenttiteln; `Ui` enthält gemeinsame Bausteine. Login, Suche, Klick-Listener (`NodeClickListener`, läuft im Hintergrund-Thread) und Tree-Buttons gibt es nur in Swing.

## Sonstiges

`Aufruf`, `WsdlSetup`, `WsdlAnheben` im Wurzelverzeichnis sind lose Java-8-Hilfsdateien (ohne Endung) für ein anderes Projekt (DynS/CXF-Webservices, Windows-Pfade). Sie gehören nicht zum Build und werden nicht kompiliert.
