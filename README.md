# Objekt-Viewer

Zeigt ein Objekt und seine Unterobjekte als HTML-Seite an. Reines Java (17 oder neuer, getestet mit 21), keine Bibliotheken.

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

## Ansichten: Spalten und Segmente (Frames)

Pro Objekttyp lässt sich festlegen, wie der Main-Bereich aufgeteilt ist: 1 bis 3 Spalten, jede mit beliebig
vielen Segmenten untereinander. Jedes Segment zeigt eine Auswahl von Feldern. Alle Trennlinien lassen sich
mit der Maus verschieben (oder per Tab + Pfeiltasten); die Größen merkt sich der Browser pro Ansicht,
Doppelklick auf eine Trennlinie setzt sie zurück.

```java
ViewRegistry views = new ViewRegistry()
        .register(ObjectType.MITARBEITER, NodeView.of("Mitarbeiter",
                Column.of(
                        Segment.fields("Stammdaten", "Rolle", "Seit").vertical(),
                        Segment.fields("Kontakt", "E-Mail", "Telefon").horizontal().height(2)),
                Column.of(
                        Segment.remainingFields("Weitere Felder")).width(0.8)));

new TreeServer(root, "Objekt-Viewer", views).start(8080);
```

| Baustein | Bedeutung |
|---|---|
| `Segment.fields(titel, keys…)` | genau diese Felder, in dieser Reihenfolge (fehlende werden als – gezeigt) |
| `Segment.allFields(titel)` | alle Felder des Objekts |
| `Segment.remainingFields(titel)` | alle Felder, die kein anderes Segment der Ansicht nennt |
| `.vertical()` / `.horizontal()` | feste Darstellung Key/Value bzw. Keys als Spalten; ohne Angabe gilt der Umschalter im Header |
| `.height(n)` / `Column.width(n)` | relative Startgröße |
| `NodeView.withoutChildren()` | Unterobjekte unter den Frames ausblenden |

Einzelne Knoten können die Ansicht ihres Typs überschreiben: `knoten.view(NodeView.of(...))`.
Typen ohne Ansicht werden wie bisher dargestellt (alle Felder, darunter die Unterobjekte).
Mehr als 3 Spalten oder eine leere Spalte ergeben beim Start eine `IllegalArgumentException`.

## Lazy Loading

Unterobjekte können erst bei Bedarf geladen werden, z. B. aus einer Datenbank:

```java
firma.child("Archiv", ObjectType.ORDNER)
        .lazy(parent -> datenbank.ladeKinderVon(parent.name()));   // List<ObjectNode>
```

- Geladen wird beim ersten Zugriff auf `children()`: wenn das Objekt geöffnet oder in der Navigation aufgeklappt wird.
- Die Navigation lädt nur, was aufgeklappt wird. Der Rest des Baums bleibt ungeladen.
- Jeder Knoten wird genau einmal geladen, auch bei gleichzeitigen Zugriffen.
- Schlägt der Loader fehl, zeigt die Seite die Fehlermeldung; beim nächsten Öffnen wird erneut geladen.
- **↻ Neu laden** im Header (nur bei lazy Objekten) verwirft die Kinder und lädt sie frisch.
- Geladene Kinder können selbst wieder lazy sein (beliebig tief).

Nützliche Methoden an `ObjectNode`: `isLazy()`, `isLoaded()`, `loadedChildren()` (ohne zu laden), `ensureLoaded()`, `reload()`.

Das Beispiel „Archiv“ in `Main.java` lädt Jahre → Monate → Dokumente nach und schreibt jeden Ladevorgang in die Konsole.

## Dateien

| Datei | Inhalt |
|---|---|
| `ObjectNode.java` | Objekt: Name, Typ, Felder, Unterobjekte |
| `ObjectType.java` | Enum der Objekttypen |
| `NodeView.java`, `Column.java`, `Segment.java` | Ansicht: Spalten und Segmente |
| `ViewRegistry.java` | Ansicht pro Objekttyp |
| `ChildLoader.java` | Schnittstelle zum Nachladen von Unterobjekten |
| `ChildLoadException.java` | Fehler beim Nachladen |
| `HtmlRenderer.java` | Baut die HTML-Seite (Navigation, Header, Main, CSS) |
| `TreeServer.java` | Webserver aus dem JDK |
| `Main.java` | Startpunkt und Beispieldaten |
