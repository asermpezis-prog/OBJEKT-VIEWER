package de.objektbaum;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;

/**
 * Startet den Viewer mit Beispieldaten.
 * Danach im Browser öffnen: http://localhost:8080/
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        ObjectNode root = beispielDaten();

        TreeServer server = new TreeServer(root, "Objekt-Viewer");
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
                .field("Standort", "München");

        ObjectNode backend = entwicklung.child("Team Backend", ObjectType.TEAM)
                .field("Sprache", "Java 21")
                .field("Sprint-Länge", "2 Wochen");
        backend.child("Max Huber", ObjectType.MITARBEITER)
                .field("Rolle", "Senior Developer").field("Seit", 2017).field("E-Mail", "max.huber@example.com");
        backend.child("Lena Wolf", ObjectType.MITARBEITER)
                .field("Rolle", "Developer").field("Seit", 2022).field("E-Mail", "lena.wolf@example.com")
                .field("Homeoffice", true);

        ObjectNode frontend = entwicklung.child("Team Frontend", ObjectType.TEAM)
                .field("Sprache", "TypeScript");
        frontend.child("Jonas Keller", ObjectType.MITARBEITER)
                .field("Rolle", "UX Engineer").field("Seit", 2020);

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

        return firma;
    }
}
