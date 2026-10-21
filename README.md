# Objekt-Viewer – Framework

Framework zum Anzeigen und Bearbeiten von Objektbäumen: Objekte mit Typ, Feldern (Key/Value) und
Unterobjekten – **im Browser** oder als **Swing-Fenster**. Reines Java 17+, keine Bibliotheken.

- Navigation als **Baum** oder **Baumtabelle** (Spalten, Filter, Sortierung, Auf-/Zuklappen)
- **Ansichten je Objekttyp**: bis zu 3 verschiebbare Spalten mit Segmenten; darin Feld-Tabellen,
  Textfelder, ComboBoxen, ComboListen und Buttons mit Java-Aktionen
- **Lazy Loading** von Unterobjekten
- **Nach der Entwicklung selbst anpassbar, ohne Neukompilieren und ohne Neustart:**
  Objekttypen, Baumtabelle und Ansichten in `viewer.xml`, Aussehen in `templates/`

---

## Schnellstart (Beispielanwendung)

```
./run.sh                       Browser-Version  -> http://localhost:8080/
./run.sh 8080 0.0.0.0          aus dem Netzwerk erreichbar, z. B. http://hawk:8080/
./run-swing.sh                 Swing-Fenster (braucht eine grafische Oberfläche)
```
Windows: `run.bat`, `run-swing.bat`. Das Beispiel liest `beispiel/config/` – dort einfach
`viewer.xml` oder `templates/style.css` ändern, speichern und die Seite neu laden.

## Projektaufbau

```
framework/src/          Framework-Quellcode (wird zu dist/objektviewer.jar)
framework/resources/    Standard-Vorlagen im JAR (page.html, style.css, app.js, viewer.xml)
beispiel/src/           Beispielanwendung: Daten, Lazy-Loader, Button-Aktionen
beispiel/config/        viewer.xml + templates/ der Beispielanwendung  ← zum Bearbeiten
build.sh / build.bat    baut dist/objektviewer.jar und das Beispiel
```

---

## Im eigenen Projekt einsetzen

**1. JAR bauen:** `./build.sh` → `dist/objektviewer.jar` ins eigene Projekt übernehmen (Classpath).

**2. Konfigurationsordner anlegen:**
```
java -jar objektviewer.jar init config
```
legt `config/viewer.xml` (kommentierte Vorlage) und `config/templates/` (page.html, style.css, app.js) an.

**3. Im Code nur das, was Code sein muss** – Daten, Loader, Aktionen:
```java
import de.objektbaum.*;

public class MeineApp {
    static final ObjectType VORGANG = ObjectType.of("VORGANG");   // Text/Icon kommen aus viewer.xml
    static final ObjectType PERSON  = ObjectType.of("PERSON");

    public static void main(String[] args) throws Exception {
        ObjectNode root = new ObjectNode("Vorgang 4711", VORGANG)
                .field("Status", "Neu");
        root.child("Personen", ObjectType.GRUPPE)
            .lazy(parent -> datenbank.ladePersonen());             // erst beim Aufklappen laden

        ObjectViewer viewer = ObjectViewer.builder()
                .title("Meine Anwendung")
                .root(root)
                .configDir(Path.of("config"))                      // viewer.xml + templates/
                .action("pruefen", (node, values) -> {             // <button action="pruefen"/>
                    node.field("Geprüft", "ja");
                    return "Geprüft.";                             // Meldung oben auf der Seite
                })
                .options("berater", node -> datenbank.berater())   // <combo source="berater"/>
                .build();

        viewer.startWeb("localhost", 8080);                       // Browser-Version
        // viewer.openSwing();                                     // und/oder Swing-Fenster
    }
}
```

**4. Ansichten und Aussehen** in `config/` bearbeiten – siehe unten.

`java -jar objektviewer.jar check config` prüft eine `viewer.xml`, ohne die Anwendung zu starten.

### Was steht wo?

| Was | Wo | Ändern ohne Neukompilieren |
|---|---|---|
| Daten, Lazy-Loader | Java (`ObjectNode`, `.lazy(...)`) | – |
| Button-Aktionen, dynamische Auswahllisten | Java (`.action(...)`, `.options(...)`) | – |
| Objekttypen: Anzeigetext, Icon | `viewer.xml` `<types>` | ✔ sofort |
| Spalten der Baumtabelle | `viewer.xml` `<treetable>` | ✔ sofort (Swing: beim Neustart) |
| Ansichten: Spalten, Segmente, Felder, Eingaben, Buttons | `viewer.xml` `<views>` | ✔ sofort |
| Anwendungstitel | `viewer.xml` `title="…"` (sonst Code) | ✔ sofort |
| Seitengerüst, Farben, Schriften, Verhalten im Browser | `templates/page.html`, `style.css`, `app.js` | ✔ sofort |

„Sofort“ heißt: Datei speichern → Seite neu laden. Die Swing-Version aktualisiert sich innerhalb einer Sekunde selbst.

### Fehler beim Bearbeiten

Enthält `viewer.xml` einen Fehler, erscheint oben ein **rotes Band mit Zeilennummer** und einer Erklärung,
was erlaubt ist (z. B. *„Zeile 42: Unbekanntes Attribut titel=… in &lt;segment&gt;. Erlaubt: title, height, layout“*).
Bis zur Korrektur gilt die **letzte gültige Fassung** – ein Tippfehler legt die Anwendung nicht lahm.

---

## viewer.xml

```xml
<objektviewer title="Meine Anwendung">

  <types>
    <type id="VORGANG" label="Vorgang" icon="📋"/>
  </types>

  <treetable>
    <column kind="name"  header="Objektname"/>
    <column kind="field" key="ObjektId"/>
    <column kind="type"  header="Typ"/>
  </treetable>

  <views>
    <view name="Vorgang" type="VORGANG">
      <column width="1">
        <segment title="Bearbeiten">
          <output key="ObjektId"/>
          <text   key="Bezeichnung"/>
          <combo  key="Status" options="Neu | In Arbeit | Erledigt"/>
          <button label="Speichern"/>
        </segment>
      </column>
      <column width="0.8">
        <segment title="Weitere Felder" layout="vertical">
          <fields select="remaining"/>
        </segment>
      </column>
    </view>
  </views>

</objektviewer>
```

Listen (`keys`, `options`) werden mit **`|`** getrennt, weil Werte selbst Kommas enthalten können.
Die mit `init` angelegte `viewer.xml` enthält alle Elemente als Kommentar zum Nachschlagen.

| Element | Attribute | Bedeutung |
|---|---|---|
| `<objektviewer>` | `title` | Wurzel; Titel überschreibt den aus dem Code |
| `<type>` | `id`*, `label`, `icon` | Objekttyp: Kennung wie im Code, Anzeigetext, Icon (Emoji/Zeichen) |
| `<treetable><column>` | `kind`* = `name`/`type`/`field`, `key` (bei field), `header` | Spalten der Baumtabelle; `name` ist Pflicht |
| `<view>` | `type`* (auch `A \| B`), `name`, `children`=true/false | Ansicht für einen oder mehrere Typen; `children="false"` blendet die Unterobjekte aus |
| `<column>` | `width` (relativ, Standard 1) | 1 bis 3 Spalten je Ansicht |
| `<segment>` | `title`*, `height` (relativ), `layout`=`vertical`/`horizontal` | Bereich in einer Spalte; `layout` gilt für die Feld-Tabellen darin |
| `<fields>` | `keys="A \| B"` oder `select`=`all`/`remaining`, `layout` | Feld-Tabelle; `remaining` = alle Felder, die sonst nirgends stehen |
| `<text>` | `key`*, `label`, `rows` | Eingabefeld, `rows` > 1 = mehrzeilig |
| `<output>` | `key`*, `label`, `rows` | nur Anzeige |
| `<combo>` | `key`*, `label`, `options` oder `source`, `free`, `editable` | Dropdown; `free="true"` erlaubt eigene Eingaben; `source` = Werte aus dem Code |
| `<list>` | `key`*, `label`, `options` oder `source`, `multiple`, `rows`, `editable` | sichtbare Liste; `multiple="true"` speichert eine Liste |
| `<button>` | `label`*, `action` | ohne `action`: speichert; mit: speichert und ruft die Aktion aus dem Code auf |

\* = Pflicht. `layout`: `vertical` = Key/Value, `horizontal` = Keys als Spalten; ohne Angabe gilt der Umschalter im Header.

Reihenfolge, welche Ansicht gilt: eigene Ansicht eines Knotens im Code (`knoten.view(...)`) →
Ansicht aus `viewer.xml` → Ansicht aus dem Code (`builder.view(...)`) → Standarddarstellung (alle Felder + Unterobjekte).

---

## Templates (Browser-Version)

| Datei | Inhalt |
|---|---|
| `page.html` | Seitengerüst mit Platzhaltern |
| `style.css` | Aussehen; Farben zentral als Variablen in `:root` (hell) und im Dunkel-Block |
| `app.js` | Verhalten: Baum/Baumtabelle, Filter, Sortieren, Frames ziehen, Nachladen |

Platzhalter in `page.html` (Inhalte sind bereits sicher maskiert):

| Platzhalter | Inhalt |
|---|---|
| `{{pageTitle}}` | Tab-Titel „Objekt – Anwendung“ |
| `{{appTitle}}` | Anwendungstitel |
| `{{objectName}}`, `{{objectType}}` | angezeigtes Objekt und sein Typ |
| `{{nav}}` | Baum der Navigation |
| `{{header}}` | Pfad, Titel, Umschalter, Meldung |
| `{{treetable}}` | Baumtabelle |
| `{{content}}` | Frames bzw. Felder und Unterobjekte |
| `{{configError}}` | Fehlerband zu viewer.xml (leer, wenn alles stimmt) |

Eigene Dateien (Logo, Schrift, Zusatz-CSS) einfach in `templates/` legen und mit `/static/<name>` einbinden,
z. B. `<img src="/static/logo.svg">`. Es werden nur Dateien aus diesem Ordner ausgeliefert.

**Wichtig bei Framework-Updates:** Eine Datei in `templates/` ersetzt die Standardfassung aus dem JAR vollständig.
Wer eine Datei nicht anpasst, löscht sie einfach – dann gilt automatisch die aktuelle Standardfassung.
`init <ordner> --force` überschreibt die Kopien mit den neuen Standardfassungen.

---

## Java-API (Kurzüberblick)

| Klasse | Zweck |
|---|---|
| `ObjectViewer` | Einstieg: `builder()…build()`, dann `startWeb(host, port)` / `openSwing()` |
| `ObjectNode` | Objekt: `field(k, v)`, `child(name, typ)`, `add(node)`, `lazy(loader)`, `reload()`, `view(nodeView)` |
| `ObjectType` | Typ: `of("ID")`, `define(id, label, icon)`, `GRUPPE`, `UNBEKANNT` |
| `Button.Action` | `(node, werte) -> "Meldung"` – Eingaben sind bereits gespeichert; Exception = Fehlermeldung |
| `OptionSource` | `node -> List<String>` – Auswahlwerte für combo/list |
| `NodeView`, `Column`, `Segment`, `FieldTable`, `TextField`, `ComboBox`, `ComboList`, `Button` | Ansichten im Code statt in XML |

Gespeicherte Eingaben liegen in den Feldern der `ObjectNode`s (Arbeitsspeicher). Dauerhaft speichern
(Datenbank, Datei) erledigt eine Button-Aktion.

## Browser oder Swing

| | Browser | Swing |
|---|---|---|
| Baum / Baumtabelle, Filter, Sortierung, Alle auf/zu | ✔ | ✔ |
| Frames verschieben, Größen merken, Doppelklick = zurücksetzen | ✔ | ✔ |
| Textfeld, ComboBox, ComboListe, Buttons mit Java-Aktion | ✔ | ✔ |
| Lazy Loading, Neu laden | ✔ | ✔ |
| viewer.xml live | ✔ beim Neuladen der Seite | ✔ automatisch |
| Templates (page.html, style.css, app.js) | ✔ | – (eigenes Swing-Aussehen) |
| Zugriff von anderen Rechnern / ohne Bildschirm | ✔ | – |
