# Objekt-Viewer

Zeigt ein Objekt und seine Unterobjekte als HTML-Seite an. Reines Java (17 oder neuer, getestet mit 21), keine Bibliotheken.

- **Baum** links als Navigation, oder wahlweise als **Baumtabelle** mit Spalten, Filter und Sortierung
- **Header** mit Pfad, Name, Typ und den Umschaltern
- **Main** mit Feldern und Bedienelementen in bis zu 3 verschiebbaren Spalten, darunter die Unterobjekte

## Starten

```
./run.sh                  (Linux/macOS)  -> http://localhost:8080/
./run.sh 9090             anderer Port
./run.sh 8080 0.0.0.0     aus dem Netzwerk erreichbar, z. B. http://hawk:8080/
run.bat                   (Windows, gleiche Parameter)
```

## Eigene Objekte

In `Main.beispielDaten()` den Baum aufbauen:

```java
ObjectNode vorgang = new ObjectNode("Vorgang Baufinanzierung", ObjectType.VORGANG)
        .field("ObjektId", "8893-08-21-19.09.24.405926")
        .field("Status", "In Bearbeitung");

ObjectNode personen = vorgang.child("Personen", ObjectType.GRUPPE);      // Sammelknoten
personen.child("Max Mustermann", ObjectType.PERSON).field("Beziehungsart", "Antragsteller");
```

Die Typen stehen in `ObjectType.java` (Vorgang, Vorhaben, Variante, Angebot, Person, Grundstueck,
Grundbuchblatt, … UNBEKANNT) und können frei angepasst werden (Name, Anzeigetext, Icon).
`GRUPPE` ist für Sammelknoten wie „Personen“ oder „ASEs_AM_VORGANG“ gedacht und hat keinen Typtext.

## Baum oder Baumtabelle

Im Header zwischen **Baum** (Navigation links) und **Baumtabelle** umschalten; die Wahl merkt sich der Browser.

Die Baumtabelle:
- Spalten frei wählbar, Standard: Objektname und Typ
- Filterzeile: `*` = alles, Text = enthält, `*` und `?` als Platzhalter (z. B. `VHV_44*`). Treffer werden mit ihren Elternknoten gezeigt
- ⇅ sortiert die Geschwister innerhalb jedes Knotens, erneuter Klick dreht die Richtung
- **Alle auf / Alle zu**; lazy Knoten werden dabei nicht ungewollt geladen, sondern erst per `+`
- die ausgewählte Zeile ist markiert (✔), ein Klick auf einen Namen öffnet das Objekt
- die Höhe des Bereichs lässt sich unten rechts ziehen

```java
List<TreeColumn> spalten = List.of(
        TreeColumn.name("Objektname"),
        TreeColumn.field("ObjektId"),
        TreeColumn.type("Typ"),
        TreeColumn.field("Beziehungsart"),
        TreeColumn.field("Bezeichnung"));

new TreeServer(root, "Objekt-Viewer", views, spalten).start(8080);
```

## Ansichten: Spalten und Segmente (Frames)

Pro Objekttyp lässt sich festlegen, wie der Main-Bereich aufgeteilt ist: 1 bis 3 Spalten, jede mit beliebig
vielen Segmenten untereinander. Alle Trennlinien lassen sich mit der Maus verschieben (oder per Tab + Pfeiltasten);
die Größen merkt sich der Browser pro Ansicht, Doppelklick auf eine Trennlinie setzt sie zurück.

```java
ViewRegistry views = new ViewRegistry()
        .register(ObjectType.ANGEBOT, NodeView.of("Angebot",
                Column.of(
                        Segment.of("Konditionen",
                                TextField.input("Darlehensbetrag"),
                                TextField.input("Sollzins").label("Sollzins %"),
                                ComboBox.of("Zinsbindung", "5 Jahre", "10 Jahre", "15 Jahre"),
                                Button.of("Rate berechnen", (node, values) -> {
                                    node.field("Monatsrate", "…");
                                    return "Rate berechnet";          // Meldung oben auf der Seite
                                }),
                                Button.save("Speichern"))),
                Column.of(
                        Segment.fields("Ergebnis", "Monatsrate").vertical(),
                        Segment.remainingFields("Weitere Felder")).width(0.8)));
```

### Inhalte eines Segments

| Element | Bedeutung |
|---|---|
| `FieldTable.of(keys…)` / `.all()` / `.remaining()` | Felder als Tabelle; `remaining` = alle Felder, die sonst nirgends in der Ansicht stehen |
| `TextField.input(key)` | Eingabefeld, `.rows(n)` mehrzeilig, `.label("…")` eigene Beschriftung |
| `TextField.output(key)` | nur Anzeige |
| `ComboBox.of(key, werte…)` | Dropdown; `.freeInput()` = Werte als Vorschläge, freie Eingabe erlaubt; `.readOnly()` |
| `ComboList.of(key, werte…)` | sichtbare Liste; `.multiple()` Mehrfachauswahl (gespeichert als `List<String>`), `.rows(n)` |
| `Button.save(label)` | speichert alle Eingaben |
| `Button.of(label, aktion)` | speichert alle Eingaben, führt dann die Aktion aus; Rückgabetext = Meldung, Exception = Fehlermeldung |

Auswahlwerte können statt fester Werte auch dynamisch kommen: `ComboBox.of("Berater", node -> ladeBerater())`.

Kurzformen: `Segment.fields(titel, keys…)`, `Segment.allFields(titel)`, `Segment.remainingFields(titel)`.
`Segment.vertical()` / `.horizontal()` legt die Darstellung der Tabellen fest, ohne Angabe gilt der Umschalter im Header.
`.height(n)` / `Column.width(n)` setzen relative Startgrößen, `NodeView.withoutChildren()` blendet die Unterobjekte aus.

Einzelne Knoten können die Ansicht ihres Typs überschreiben: `knoten.view(NodeView.of(...))`.
Typen ohne Ansicht werden in der Standarddarstellung gezeigt (alle Felder, darunter die Unterobjekte).
Mehr als 3 Spalten oder leere Spalten/Segmente ergeben beim Start eine `IllegalArgumentException`.

Enter in einem Textfeld speichert nur, ohne eine Button-Aktion auszulösen.

## Lazy Loading

Unterobjekte können erst bei Bedarf geladen werden, z. B. aus einer Datenbank:

```java
vorgang.child("VMGOs", ObjectType.GRUPPE)
        .lazy(parent -> datenbank.ladeKinderVon(parent.name()));   // List<ObjectNode>
```

- Geladen wird beim ersten Zugriff auf `children()`: wenn das Objekt geöffnet oder im Baum bzw. in der Baumtabelle aufgeklappt wird.
- Jeder Knoten wird genau einmal geladen, auch bei gleichzeitigen Zugriffen.
- Schlägt der Loader fehl, wird die Fehlermeldung angezeigt; beim nächsten Versuch wird erneut geladen.
- **↻ Neu laden** im Header (nur bei lazy Objekten) verwirft die Kinder und lädt sie frisch.
- Geladene Kinder können selbst wieder lazy sein (beliebig tief).

Nützliche Methoden an `ObjectNode`: `isLazy()`, `isLoaded()`, `loadedChildren()` (ohne zu laden), `ensureLoaded()`, `reload()`.

In den Beispieldaten sind `ASEs_AM_VORGANG`, `ASEs_AM_VORHABEN`, `ASEs_AN_VARIANTE` und `VMGOs` lazy;
jeder Ladevorgang erscheint in der Konsole.

## Dateien

| Datei | Inhalt |
|---|---|
| `ObjectNode.java` | Objekt: Name, Typ, Felder, Unterobjekte, eigene Ansicht |
| `ObjectType.java` | Enum der Objekttypen |
| `NodeView.java`, `Column.java`, `Segment.java` | Ansicht: Spalten und Segmente |
| `Element.java`, `FieldTable.java`, `TextField.java`, `ComboBox.java`, `ComboList.java`, `Button.java`, `OptionSource.java` | Inhalte der Segmente |
| `ViewRegistry.java` | Ansicht pro Objekttyp |
| `TreeColumn.java` | Spalten der Baumtabelle |
| `ChildLoader.java`, `ChildLoadException.java` | Nachladen von Unterobjekten |
| `HtmlRenderer.java` | Baut die HTML-Seite (Navigation, Baumtabelle, Header, Frames, CSS, JS) |
| `TreeServer.java` | Webserver aus dem JDK (Seiten, Nachladen, Formular-Aktionen) |
| `Main.java` | Startpunkt, Ansichten und Beispieldaten |
