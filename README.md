# Objekt-Viewer

Zeigt ein Objekt und seine Unterobjekte als HTML-Seite an. Reines Java 21, keine Bibliotheken.

- **Links:** Navigation als Baum (aufklappbar)
- **Rechts oben:** Header mit Pfad, Name, Typ und Umschalter für das Tabellen-Layout
- **Rechts Main:** Felder des Objekts und seine Unterobjekte als Tabellen

Tabellen-Layout umschaltbar:
- **Key / Value** – zwei Spalten
- **Keys als Spalten** – Keys als Überschriften, Values darunter (bei Unterobjekten eine Zeile pro Objekt)

## Starten

```
./run.sh          (Linux/macOS)
run.bat           (Windows)
```

Dann im Browser: http://localhost:8080/  (anderer Port: `./run.sh 9090`)

## Eigene Objekte

In `Main.beispielDaten()` den Baum aufbauen:

```java
ObjectNode firma = new ObjectNode("Muster GmbH", ObjectType.FIRMA)
        .field("Sitz", "München")
        .field("Mitarbeiter", 42);

ObjectNode abteilung = firma.child("Entwicklung", ObjectType.ABTEILUNG)
        .field("Leitung", "Anna Berger");
```

Die Typen stehen in `ObjectType.java` und können frei angepasst werden (Name, Anzeigetext, Icon).

## Dateien

| Datei | Inhalt |
|---|---|
| `ObjectNode.java` | Objekt: Name, Typ, Felder, Unterobjekte |
| `ObjectType.java` | Enum der Objekttypen |
| `HtmlRenderer.java` | Baut die HTML-Seite (Navigation, Header, Main, CSS) |
| `TreeServer.java` | Webserver aus dem JDK |
| `Main.java` | Startpunkt und Beispieldaten |
