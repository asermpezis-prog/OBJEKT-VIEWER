# Objekt-Viewer – Framework

Framework zum Anzeigen und Bearbeiten von Objektbäumen: Objekte mit Typ, Feldern (Key/Value) und
Unterobjekten – **im Browser** oder als **Swing-Fenster**. Reines Java 17+, keine Bibliotheken.

- Navigation als **Baum** oder **Baumtabelle** (Spalten, Filter, Sortierung, Auf-/Zuklappen)
- **Ansichten je Objekttyp**: bis zu 3 verschiebbare Spalten mit Segmenten; darin Feld-Tabellen,
  Textfelder, ComboBoxen, ComboListen und Buttons mit Java-Aktionen
- **Lazy Loading** von Unterobjekten
- **Nach der Entwicklung selbst anpassbar, ohne Neukompilieren und ohne Neustart:**
  Objekttypen, Baumtabelle und Ansichten in `viewer.xml`, Aussehen in `templates/` (Browser) bzw. `swing.properties` (Swing)

---

## Schnellstart (Beispielanwendung)

```
./run.sh                       Browser-Version  -> http://localhost:8080/
./run.sh 8080 0.0.0.0          aus dem Netzwerk erreichbar, z. B. http://hawk:8080/
./run-swing.sh                 Swing-Fenster (braucht eine grafische Oberfläche)
./run-vorgang.sh               Swing: Anmeldung (demo/demo) -> Vorgang suchen (100000001) -> Baum per Klick
```
Windows: `run.bat`, `run-swing.bat`, `run-vorgang.bat`. Das Beispiel liest `beispiel/config/` – dort einfach
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
legt `config/viewer.xml` (kommentierte Vorlage), `config/swing.properties` und `config/templates/` (page.html, style.css, app.js) an.

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
| Spalten der Baumtabelle | `viewer.xml` `<treetable>` | ✔ sofort |
| Ansichten: Spalten, Segmente, Felder, Eingaben, Buttons | `viewer.xml` `<views>` | ✔ sofort |
| Anwendungstitel | `viewer.xml` `title="…"` (sonst Code) | ✔ sofort |
| Seitengerüst, Farben, Schriften, Verhalten im Browser | `templates/page.html`, `style.css`, `app.js` | ✔ sofort |
| Aussehen der Swing-Version: Look and Feel, hell/dunkel, Schrift, Farben | `swing.properties` | ✔ sofort |

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
| `<number>` | `key`*, `label`, `decimals` (Standard 2), `min`, `max`, `unit`, `editable` | Zahl mit Prüfung; gespeichert deutsch formatiert (`150000` → `150.000,00`); `unit` steht hinter dem Feld |
| `<date>` | `key`*, `label`, `editable` | Datum mit Prüfung, gespeichert `TT.MM.JJJJ`; erlaubt auch `2.9.26` und `2026-09-02`; Kalender (Swing) bzw. Datumsauswahl (Browser) |
| `<check>` | `key`*, `label`, `editable` | Checkbox; gespeichert `ja` / `nein` |
| `<combo>` | `key`*, `label`, `options` oder `source`, `free`, `editable` | Dropdown; `free="true"` erlaubt eigene Eingaben; `source` = Werte aus dem Code |
| `<list>` | `key`*, `label`, `options` oder `source`, `multiple`, `rows`, `editable` | sichtbare Liste; `multiple="true"` speichert eine Liste |
| `<button>` | `label`*, `action` | ohne `action`: speichert; mit: speichert und ruft die Aktion aus dem Code auf |

Ist eine Eingabe ungültig (z. B. `abc` in `<number>`, `31.02.2026` in `<date>`), wird **nichts gespeichert** und keine Aktion
ausgeführt; die Meldung nennt die Felder. In Swing bleiben die Eingaben stehen, das Feld ist rot markiert, der Fehler
steht darunter – geprüft wird schon beim Verlassen des Felds.

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

## Anmeldung, Vorgang suchen, Baum per Klick (Swing)

Ablauf wie im Fachsystem: **Anmeldemaske → „Vorgang suchen“ → „Vorgang lesen“ zeigt nur den Vorgang → jeder Klick
auf ein Objekt ruft einen Listener auf, der Kinder anhängen kann** – der Baum baut sich Klick für Klick auf.
Die Anwendung liefert nur die vier Teile in Java; Masken, Hintergrund-Threads, Meldungen und Aktualisierung macht
das Framework. Ausprobieren: `./run-vorgang.sh` (Benutzer `demo`, Passwort `demo`, VorgangsNummer `100000001`).

```java
ObjectViewer.builder()
        .configDir(Path.of("config"))
        .login((benutzer, passwort) -> {                 // normales Ende = angemeldet
            if (!ldap.pruefe(benutzer, passwort)) throw new Exception("Benutzer oder Passwort falsch.");
        })
        .searchFields("VorgangsId", "VorgangsNummer")    // Standard
        .search(kriterien -> dyns.leseVorgang(           // leere Felder fehlen; null = „Nichts gefunden“
                kriterien.get("VorgangsId"), kriterien.get("VorgangsNummer")))
        .onNodeClick(knoten -> {                         // bei JEDEM Klick, im Hintergrund
            knoten.removeChildren();                     // optional: frisch aufbauen
            for (ObjectNode kind : dyns.kinder(knoten)) knoten.add(kind);
            return null;                                 // oder eine Meldung; Exception = rote Meldung
        })
        .nodeStyle(n -> "Abgelehnt".equals(n.fields().get("Status"))
                ? NodeStyle.of().color("#9ca3af").italic() : null)
        .treeButton("Angebot löschen", (knoten, werte) -> { knoten.parent().removeChild(knoten); return "gelöscht"; })
        .build()
        .openSwing();
```

| Teil | Interface | Hinweise |
|---|---|---|
| Anmeldung | `LoginHandler.login(user, char[] passwort)` | Passwort wird danach überschrieben; `viewer.user()` = angemeldeter Benutzer; „Abmelden“ oben rechts verwirft den Vorgang |
| Suche | `SearchHandler.search(Map kriterien) → ObjectNode` | Ergebnis = neue Wurzel; `viewer.root()` liefert sie; `root(...)` im Builder ist dann nicht nötig |
| Klick | `NodeClickListener.clicked(knoten) → Meldung` | gilt für Navigation, Baumtabelle, Pfad und Unterobjekte; Auf-/Zuklappen (+/−, Dreieck) löst ihn **nicht** aus; nur das Ergebnis des letzten Klicks wird angezeigt |
| Buttons unter der Baumtabelle | `Button.Action` mit dem ausgewählten Objekt | auch in `viewer.xml`: `<treetable><button label="…" action="…"/></treetable>`; wird das Objekt entfernt, erscheint sein Elternobjekt |

Mit Suchmaske startet die Swing-Version in der Baumtabelle (wie im Fachsystem); „Baum“ bleibt umschaltbar.
Wer unter den Frames keine Unterobjekte sehen will: `<view … children="false">`.
Die Browser-Version zeigt `NodeStyle` ebenfalls; Anmeldung, Suche und Klick-Listener gibt es bisher nur in Swing.

### Aussehen je Objekt

```java
knoten.style(NodeStyle.of().color("#b91c1c").background("#fee2e2").bold().icon("⚠").tooltip("Prüfen"));
builder.nodeStyle(n -> …);   // Regel für alle; knoten.style(...) hat Vorrang
```

Wirkt in Navigation, Baumtabelle (ganze Zeile) und Überschrift – in Swing und im Browser. Farben als `#rrggbb`.

---

## swing.properties (Swing-Version)

Liegt im Konfigurationsordner neben `viewer.xml` (`init` legt sie mit Erklärungen an). Änderungen übernimmt das
offene Fenster innerhalb einer Sekunde; Auswahl, aufgeklappte Knoten, Filter und **ungespeicherte Eingaben bleiben**.
Fehler erscheinen wie bei `viewer.xml` als rotes Band mit Zeilennummer, es gilt die letzte gültige Fassung.

```properties
aussehen       = metal        # metal (empfohlen) | nimbus | system
farbschema     = system       # hell | dunkel | system (folgt Windows, macOS, GNOME)
schrift        = Segoe UI, Inter, Noto Sans, DejaVu Sans
schriftgroesse = 14           # 10 bis 24, Zeilenhöhen wachsen mit
farbe.akzent        = #2563eb # für hell und dunkel
farbe.akzent.dunkel = #60a5fa # nur dunkel (ebenso .hell)
```

Farbnamen: `hintergrund, flaeche, navigation, navigation.text, navigation.linie, akzent, text, text.grau, linie,
zebra, auswahl, ok.hintergrund, ok.linie, ok.text, fehler.hintergrund, fehler.linie, fehler.text`.
Bei `aussehen = system` übernehmen Eingabefelder, Listen und Scrollbalken die Farben nur teilweise.

Gespeicherte Größen der Frames hängen an den **Segmenttiteln**: Ändert sich in `viewer.xml` Anzahl oder Reihenfolge,
gelten für die betroffenen Trennlinien wieder die Startgrößen (statt alter Werte an falscher Stelle).

---

## Java-API (Kurzüberblick)

| Klasse | Zweck |
|---|---|
| `ObjectViewer` | Einstieg: `builder()…build()`, dann `startWeb(host, port)` / `openSwing()` |
| `ObjectNode` | Objekt: `field(k, v)`, `child(name, typ)`, `add(node)`, `removeChild(n)`, `removeChildren()`, `lazy(loader)`, `reload()`, `view(nodeView)`, `style(nodeStyle)` |
| `LoginHandler`, `SearchHandler`, `NodeClickListener`, `NodeStyle` | Anmeldung, Vorgang suchen, Baum per Klick, Aussehen je Objekt |
| `ObjectType` | Typ: `of("ID")`, `define(id, label, icon)`, `GRUPPE`, `UNBEKANNT` |
| `Button.Action` | `(node, werte) -> "Meldung"` – Eingaben sind bereits gespeichert; Exception = Fehlermeldung |
| `OptionSource` | `node -> List<String>` – Auswahlwerte für combo/list |
| `NodeView`, `Column`, `Segment`, `FieldTable`, `TextField`, `NumberField`, `DateField`, `CheckBox`, `ComboBox`, `ComboList`, `Button` | Ansichten im Code statt in XML |
| `Element.Input#check` | Prüfung einer Eingabe; eigene Prüfungen über `FormSubmit.check(...)` |

Gespeicherte Eingaben liegen in den Feldern der `ObjectNode`s (Arbeitsspeicher). Dauerhaft speichern
(Datenbank, Datei) erledigt eine Button-Aktion.

## Browser oder Swing

| | Browser | Swing |
|---|---|---|
| Baum / Baumtabelle, Filter, Sortierung, Alle auf/zu | ✔ | ✔ |
| Frames verschieben, Größen merken, Doppelklick = zurücksetzen | ✔ | ✔ |
| Textfeld, Zahl, Datum, Checkbox, ComboBox, ComboListe, Buttons mit Java-Aktion | ✔ | ✔ |
| Lazy Loading, Neu laden | ✔ | ✔ |
| viewer.xml live | ✔ beim Neuladen der Seite | ✔ automatisch |
| Aussehen ohne Neukompilieren | ✔ templates/ | ✔ swing.properties |
| Dunkles Farbschema | ✔ nach Systemeinstellung | ✔ hell / dunkel / nach Systemeinstellung |
| Aussehen je Objekt (`NodeStyle`) | ✔ | ✔ |
| Anmeldung, Vorgang suchen, Klick-Listener, Buttons unter der Baumtabelle | – | ✔ |
| Zugriff von anderen Rechnern / ohne Bildschirm | ✔ | – |
