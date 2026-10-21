package de.objektbaum;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Startet den Viewer mit Beispieldaten.
 * Danach im Browser öffnen: http://localhost:8080/
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        ObjectNode root = beispielDaten();
        ViewRegistry views = ansichten();

        TreeServer server = new TreeServer(root, "Objekt-Viewer", views);
        server.start(port);

        String url = "http://localhost:" + port + "/";
        System.out.println("Objekt-Viewer läuft auf " + url + "  (Beenden mit Strg+C)");

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | Error ignored) {
            // kein Desktop verfügbar (z. B. Server ohne GUI) -> URL manuell öffnen
        }
    }

    /**
     * Ansichten pro Objekttyp: 1 bis 3 Spalten, jede mit beliebig vielen Segmenten.
     * Typen ohne Ansicht werden in der Standarddarstellung gezeigt.
     */
    static ViewRegistry ansichten() {
        return new ViewRegistry()
                // 3 Spalten
                .register(ObjectType.MITARBEITER, NodeView.of("Mitarbeiter",
                        Column.of(
                                Segment.fields("Stammdaten", "Rolle", "Abteilung", "Seit").vertical(),
                                Segment.fields("Arbeitsplatz", "Raum", "Homeoffice").vertical()),
                        Column.of(
                                Segment.fields("Kontakt", "E-Mail", "Telefon", "Mobil").vertical().height(2),
                                Segment.fields("Kennung", "Kürzel", "Personalnr.").horizontal()),
                        Column.of(
                                Segment.remainingFields("Weitere Felder")).width(0.8)
                ).withoutChildren())

                // 2 Spalten, links breiter
                .register(ObjectType.ABTEILUNG, NodeView.of("Abteilung",
                        Column.of(
                                Segment.fields("Organisation", "Leitung", "Kostenstelle", "Standort").vertical(),
                                Segment.fields("Kennzahlen", "Budget €", "Mitarbeiter", "Offene Stellen").horizontal())
                                .width(2),
                        Column.of(
                                Segment.remainingFields("Sonstiges"))))

                // 1 Spalte, 2 Segmente
                .register(ObjectType.DOKUMENT, NodeView.of("Dokument",
                        Column.of(
                                Segment.fields("Rechnung", "Betrag €", "Datum", "Bezahlt").horizontal(),
                                Segment.allFields("Alle Felder").vertical().height(2))
                ).withoutChildren());
    }

    /** Beispielbaum. Hier eigene Objekte aufbauen. */
    static ObjectNode beispielDaten() {
        ObjectNode firma = new ObjectNode("Muster GmbH", ObjectType.FIRMA)
                .field("Rechtsform", "GmbH")
                .field("Sitz", "München")
                .field("Gegründet", 2004)
                .field("Mitarbeiter", 42);

        ObjectNode entwicklung = firma.child("Entwicklung", ObjectType.ABTEILUNG)
                .field("Leitung", "Anna Berger")
                .field("Kostenstelle", "KST-100")
                .field("Standort", "München")
                .field("Budget €", "1.200.000")
                .field("Mitarbeiter", 18)
                .field("Offene Stellen", 3)
                .field("Gegründet", 2006);

        ObjectNode backend = entwicklung.child("Team Backend", ObjectType.TEAM)
                .field("Sprache", "Java 21")
                .field("Sprint-Länge", "2 Wochen");
        backend.child("Max Huber", ObjectType.MITARBEITER)
                .field("Rolle", "Senior Developer").field("Abteilung", "Entwicklung").field("Seit", 2017)
                .field("E-Mail", "max.huber@example.com").field("Telefon", "+49 89 1234-101")
                .field("Mobil", "+49 170 0000101").field("Raum", "2.14").field("Homeoffice", false)
                .field("Kürzel", "MHU").field("Personalnr.", 1017).field("Geburtstag", "12.03.")
                .field("Skills", "Java, SQL, Kafka");
        backend.child("Lena Wolf", ObjectType.MITARBEITER)
                .field("Rolle", "Developer").field("Abteilung", "Entwicklung").field("Seit", 2022)
                .field("E-Mail", "lena.wolf@example.com").field("Telefon", "+49 89 1234-102")
                .field("Raum", "2.15").field("Homeoffice", true).field("Kürzel", "LWO").field("Personalnr.", 1102);

        ObjectNode frontend = entwicklung.child("Team Frontend", ObjectType.TEAM)
                .field("Sprache", "TypeScript");
        frontend.child("Jonas Keller", ObjectType.MITARBEITER)
                .field("Rolle", "UX Engineer").field("Seit", 2020)
                // Einzelner Knoten mit eigener Ansicht (überschreibt die Ansicht des Typs)
                .view(NodeView.of("Kompakt",
                        Column.of(Segment.allFields("Alles auf einen Blick").horizontal())));

        ObjectNode projekt = entwicklung.child("Projekt Phoenix", ObjectType.PROJEKT)
                .field("Status", "In Arbeit")
                .field("Start", LocalDate.of(2026, 3, 1))
                .field("Budget €", 180_000);
        projekt.child("Pflichtenheft.pdf", ObjectType.DOKUMENT)
                .field("Version", "1.3").field("Seiten", 48);
        projekt.child("Architektur.md", ObjectType.DOKUMENT)
                .field("Version", "0.9");

        ObjectNode vertrieb = firma.child("Vertrieb", ObjectType.ABTEILUNG)
                .field("Leitung", "Thomas Klein")
                .field("Kostenstelle", "KST-200");
        vertrieb.child("Sabine Roth", ObjectType.MITARBEITER)
                .field("Rolle", "Key Account").field("Region", "Süd");

        firma.child("Notizen", ObjectType.SONSTIGES); // Objekt ohne Felder und Kinder

        // Lazy Loading: die Kinder werden erst beim ersten Öffnen/Aufklappen geladen.
        firma.child("Archiv", ObjectType.ORDNER)
                .field("Hinweis", "Unterobjekte werden erst bei Bedarf geladen")
                .lazy(Main::ladeJahre);

        return firma;
    }

    // ------------------------------------------------------------------
    // Beispiel-Loader. In der Praxis z. B. Datenbankabfrage oder REST-Aufruf.
    // ------------------------------------------------------------------

    private static List<ObjectNode> ladeJahre(ObjectNode archiv) throws InterruptedException {
        log(archiv);
        List<ObjectNode> jahre = new ArrayList<>();
        for (int jahr = 2026; jahr >= 2022; jahr--) {
            int j = jahr;
            jahre.add(new ObjectNode(String.valueOf(jahr), ObjectType.ORDNER)
                    .field("Jahr", jahr)
                    .lazy(parent -> ladeMonate(parent, j)));
        }
        return jahre;
    }

    private static List<ObjectNode> ladeMonate(ObjectNode jahrKnoten, int jahr) throws InterruptedException {
        log(jahrKnoten);
        List<ObjectNode> monate = new ArrayList<>();
        int bisMonat = jahr == LocalDate.now().getYear() ? LocalDate.now().getMonthValue() : 12;
        for (int m = 1; m <= bisMonat; m++) {
            YearMonth ym = YearMonth.of(jahr, m);
            monate.add(new ObjectNode(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + jahr,
                    ObjectType.ORDNER)
                    .field("Monat", ym.toString())
                    .lazy(parent -> ladeDokumente(parent, ym)));
        }
        return monate;
    }

    private static List<ObjectNode> ladeDokumente(ObjectNode monatKnoten, YearMonth ym) throws InterruptedException {
        log(monatKnoten);
        Random rnd = new Random(ym.hashCode()); // gleiche Daten bei jedem Laden
        List<ObjectNode> docs = new ArrayList<>();
        int anzahl = rnd.nextInt(5); // 0..4 Dokumente, manche Monate sind leer
        for (int i = 1; i <= anzahl; i++) {
            docs.add(new ObjectNode("Rechnung-" + ym + "-" + String.format("%03d", i) + ".pdf", ObjectType.DOKUMENT)
                    .field("Betrag €", String.format(Locale.GERMANY, "%,.2f", 100 + rnd.nextDouble() * 4900))
                    .field("Datum", ym.atDay(1 + rnd.nextInt(ym.lengthOfMonth())))
                    .field("Bezahlt", rnd.nextBoolean()));
        }
        return docs;
    }

    /** Simuliert eine langsame Datenquelle und zeigt in der Konsole, wann geladen wird. */
    private static void log(ObjectNode node) throws InterruptedException {
        System.out.println("  lade Unterobjekte von: " + node.name());
        Thread.sleep(300);
    }
}
