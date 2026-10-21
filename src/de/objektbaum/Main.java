package de.objektbaum;

import java.awt.Desktop;
import java.net.URI;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Startet den Viewer mit Beispieldaten (Baufinanzierungs-Vorgang).
 *
 * <pre>
 * ./run.sh                 http://localhost:8080/
 * ./run.sh 9090            anderer Port
 * ./run.sh 8080 0.0.0.0    aus dem Netzwerk erreichbar (z. B. http://hawk:8080/)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String host = args.length > 1 ? args[1] : "localhost";

        ObjectNode root = beispielDaten();

        TreeServer server = new TreeServer(root, "Objekt-Viewer", ansichten(), baumtabellenSpalten());
        server.start(host, port);

        String url = "http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port + "/";
        System.out.println("Objekt-Viewer läuft auf " + url + "  (Beenden mit Strg+C)");
        if ("0.0.0.0".equals(host)) {
            System.out.println("Aus dem Netzwerk erreichbar über http://<rechnername>:" + port + "/");
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | Error ignored) {
            // kein Desktop verfügbar (z. B. Server ohne GUI) -> URL manuell öffnen
        }
    }

    // =====================================================================
    // Baumtabelle
    // =====================================================================

    static List<TreeColumn> baumtabellenSpalten() {
        return List.of(
                TreeColumn.name("Objektname"),
                TreeColumn.field("ObjektId"),
                TreeColumn.type("Typ"),
                TreeColumn.field("Beziehungsart"),
                TreeColumn.field("Bezeichnung"));
    }

    // =====================================================================
    // Ansichten pro Objekttyp
    // =====================================================================

    static ViewRegistry ansichten() {
        return new ViewRegistry()

                .register(ObjectType.VORGANG, NodeView.of("Vorgang",
                        Column.of(
                                Segment.of("Vorgang",
                                        TextField.output("ObjektId"),
                                        TextField.input("Bezeichnung"),
                                        ComboBox.of("Status", "Neu", "In Bearbeitung", "Entscheidung", "Abgeschlossen"),
                                        ComboBox.of("Berater", "A. Huber", "B. Wagner", "C. Schmid").freeInput(),
                                        Button.save("Speichern"))),
                        Column.of(
                                Segment.remainingFields("Weitere Felder").vertical()).width(0.8)))

                .register(ObjectType.VARIANTE, NodeView.of("Variante",
                        Column.of(
                                Segment.of("Variante",
                                        TextField.output("ObjektId"),
                                        TextField.input("Bezeichnung"),
                                        ComboBox.of("Status", "Entwurf", "Angeboten", "Angenommen", "Abgelehnt"),
                                        ComboList.of("Produkte", "Annuitätendarlehen", "KfW-Wohneigentum",
                                                "Bausparvertrag", "Forward-Darlehen", "Riester-Darlehen").multiple().rows(5),
                                        Button.save("Speichern"),
                                        Button.of("Als Hauptvariante markieren", (node, values) -> {
                                            node.field("Hauptvariante", "ja");
                                            return "„" + node.name() + "“ ist jetzt die Hauptvariante.";
                                        })))
                                .width(1.2),
                        Column.of(
                                Segment.fields("Kennzahlen", "Gesamtkosten", "Darlehenssumme", "Eigenkapital",
                                        "Beleihungsauslauf").horizontal(),
                                Segment.of("Bemerkung",
                                        TextField.input("Bemerkung").rows(5),
                                        Button.save("Bemerkung speichern")),
                                Segment.remainingFields("Weitere Felder").vertical())))

                .register(ObjectType.ANGEBOT, NodeView.of("Angebot",
                        Column.of(
                                Segment.of("Konditionen",
                                        TextField.input("Darlehensbetrag").label("Darlehensbetrag €"),
                                        TextField.input("Sollzins").label("Sollzins %"),
                                        TextField.input("Tilgung").label("Anf. Tilgung %"),
                                        ComboBox.of("Zinsbindung", "5 Jahre", "10 Jahre", "15 Jahre", "20 Jahre"),
                                        Button.of("Rate berechnen", Main::rateBerechnen),
                                        Button.save("Speichern"))),
                        Column.of(
                                Segment.fields("Ergebnis", "Monatsrate", "Restschuld nach Zinsbindung", "Gezahlte Zinsen")
                                        .vertical(),
                                Segment.remainingFields("Weitere Felder").vertical())))

                .register(ObjectType.PERSON, NodeView.of("Person",
                        Column.of(
                                Segment.fields("Stammdaten", "Vorname", "Nachname", "Geburtsdatum", "Familienstand")
                                        .vertical()),
                        Column.of(
                                Segment.of("Kontakt",
                                        TextField.input("E-Mail"),
                                        TextField.input("Telefon"),
                                        ComboBox.of("Kontaktweg", "E-Mail", "Telefon", "Post"),
                                        Button.save("Speichern"))),
                        Column.of(
                                Segment.remainingFields("Weitere Felder").vertical()).width(0.8)));
    }

    /** Annuität: Monatsrate, Restschuld am Ende der Zinsbindung und gezahlte Zinsen. */
    private static String rateBerechnen(ObjectNode node, Map<String, List<String>> values) {
        double betrag = zahl(node, "Darlehensbetrag");
        double zins = zahl(node, "Sollzins");
        double tilgung = zahl(node, "Tilgung");
        String bindung = String.valueOf(node.fields().getOrDefault("Zinsbindung", "")).replaceAll("\\D", "");
        int jahre = bindung.isEmpty() ? 10 : Integer.parseInt(bindung);

        double rate = betrag * (zins + tilgung) / 100 / 12;
        double rest = betrag;
        double zinsenSumme = 0;
        for (int m = 0; m < jahre * 12 && rest > 0; m++) {
            double z = rest * zins / 100 / 12;
            zinsenSumme += z;
            rest -= Math.min(rest, rate - z);
        }
        NumberFormat eur = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        node.field("Monatsrate", eur.format(rate));
        node.field("Restschuld nach Zinsbindung", eur.format(rest) + " (nach " + jahre + " Jahren)");
        node.field("Gezahlte Zinsen", eur.format(zinsenSumme));
        return "Monatsrate berechnet: " + eur.format(rate);
    }

    /** Liest eine Zahl im deutschen Format (z. B. "150.000,00" oder "3,45"). */
    private static double zahl(ObjectNode node, String key) {
        Object v = node.fields().get(key);
        String s = v == null ? "" : String.valueOf(v).replace("€", "").replace("%", "").trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException(key + " ist leer");
        }
        double d;
        try {
            d = NumberFormat.getNumberInstance(Locale.GERMANY).parse(s).doubleValue();
        } catch (ParseException e) {
            throw new IllegalArgumentException(key + " ist keine Zahl: „" + s + "“");
        }
        if (d < 0) {
            throw new IllegalArgumentException(key + " darf nicht negativ sein");
        }
        return d;
    }

    // =====================================================================
    // Beispieldaten (erfunden)
    // =====================================================================

    private static final Random RND = new Random(42);

    /** ObjektId im Format des Fachsystems, z. B. 8893-08-21-19.09.24.405926 (zufällig, aber reproduzierbar). */
    private static synchronized String objektId() {
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%06d", 1000 + RND.nextInt(9000), 1 + RND.nextInt(12),
                1 + RND.nextInt(28), RND.nextInt(24), RND.nextInt(60), RND.nextInt(60), RND.nextInt(1_000_000));
    }

    /** Fachobjekt mit den Standardfeldern der Baumtabelle. */
    private static ObjectNode obj(ObjectNode parent, String name, ObjectType type, String beziehung) {
        ObjectNode n = new ObjectNode(name, type)
                .field("ObjektId", objektId())
                .field("Beziehungsart", beziehung)
                .field("Bezeichnung", name);
        if (parent != null) parent.add(n);
        return n;
    }

    /** Sammelknoten ohne eigene Fachdaten (z. B. "Personen"). */
    private static ObjectNode gruppe(ObjectNode parent, String name) {
        return parent.child(name, ObjectType.GRUPPE);
    }

    static ObjectNode beispielDaten() {
        ObjectNode vorgang = obj(null, "Vorgang Baufinanzierung, Max Mustermann (100000001)", ObjectType.VORGANG, "")
                .field("Status", "In Bearbeitung")
                .field("Berater", "A. Huber")
                .field("Eröffnet", "02.09.2026")
                .field("Filiale", "Musterstadt Mitte");

        // ASEs am Vorgang: erst beim Aufklappen laden
        gruppe(vorgang, "ASEs_AM_VORGANG").lazy(p -> ases("Vorgang", 3, p));

        ObjectNode personen = gruppe(vorgang, "Personen");
        person(personen, "Max", "Mustermann", "14.05.1985", "Antragsteller");
        person(personen, "Erika", "Mustermann", "02.11.1987", "Mitantragsteller");

        ObjectNode vorhaben = obj(vorgang, "Vorhaben_440717", ObjectType.VORHABEN, "")
                .field("Art", "Kauf Bestandsimmobilie")
                .field("Kaufpreis", "420.000,00 €")
                .field("Nebenkosten", "46.200,00 €")
                .field("Modernisierung", "35.000,00 €");
        gruppe(vorhaben, "ASEs_AM_VORHABEN").lazy(p -> ases("Vorhaben", 2, p));
        beleihungsobjekt(vorhaben);

        obj(vorhaben, "VHV_444914_Finanzierungsvariante (A)", ObjectType.VARIANTE, "")
                .field("Status", "Entwurf")
                .field("Gesamtkosten", "501.200,00 €").field("Darlehenssumme", "380.000,00 €")
                .field("Eigenkapital", "121.200,00 €").field("Beleihungsauslauf", "84 %");

        ObjectNode variante = obj(vorhaben, "VHV_440718_Finanzierungsvariante", ObjectType.VARIANTE, "")
                .field("Status", "Angeboten")
                .field("Produkte", List.of("Annuitätendarlehen", "KfW-Wohneigentum"))
                .field("Gesamtkosten", "501.200,00 €").field("Darlehenssumme", "350.000,00 €")
                .field("Eigenkapital", "151.200,00 €").field("Beleihungsauslauf", "77 %")
                .field("Bemerkung", "Kunde wünscht 15 Jahre Zinsbindung.");
        gruppe(variante, "ASEs_AN_VARIANTE").lazy(p -> ases("Variante", 2, p));
        ObjectNode angebot = obj(variante, "SPK Darlehen", ObjectType.ANGEBOT, "Hauptangebot")
                .field("Bezeichnung", "SPK Darlehen / Neuvertrag 150.000,00 EUR")
                .field("Darlehensbetrag", "150.000,00")
                .field("Sollzins", "3,45")
                .field("Tilgung", "2,00")
                .field("Zinsbindung", "15 Jahre");
        obj(angebot, "SpkBaufi Annuität 15J", ObjectType.SPK_BAUFI_PRODUKT, "Produkt").field("Produktnr.", "AN-15-01");
        obj(variante, "KfW Wohneigentum", ObjectType.NEUES_ANGEBOT, "Zusatzangebot")
                .field("Darlehensbetrag", "100.000,00").field("Sollzins", "2,95").field("Tilgung", "2,00");
        obj(variante, "Machbarkeit", ObjectType.FAEH_SIMULATION, "").field("Ergebnis", "machbar");

        obj(vorhaben, "VHV_444515_Finanzierungsvariante", ObjectType.VARIANTE, "")
                .field("Status", "Abgelehnt").field("Darlehenssumme", "420.000,00 €");

        gruppe(vorgang, "VMGOs").lazy(Main::vmgos);
        obj(vorgang, "Fzve", ObjectType.FZVE, "");
        obj(vorgang, "Vorgangsdaten", ObjectType.VORGANGSDATEN, "").field("Kanal", "Filiale").field("Priorität", "normal");
        return vorgang;
    }

    private static void person(ObjectNode parent, String vorname, String nachname, String geb, String rolle) {
        ObjectNode p = obj(parent, vorname + " " + nachname, ObjectType.PERSON, rolle)
                .field("Vorname", vorname).field("Nachname", nachname).field("Geburtsdatum", geb)
                .field("Familienstand", "verheiratet")
                .field("E-Mail", vorname.toLowerCase(Locale.ROOT) + "." + nachname.toLowerCase(Locale.ROOT) + "@example.com")
                .field("Telefon", "").field("Kontaktweg", "E-Mail")
                .field("Beruf", "Angestellte/r").field("Nettoeinkommen", "3.400,00 €");
        obj(p, "Musterweg 1, 12345 Musterstadt", ObjectType.ADRESSE, "Wohnadresse")
                .field("Straße", "Musterweg 1").field("PLZ", "12345").field("Ort", "Musterstadt");
        obj(p, "VinPerson " + vorname, ObjectType.VIN_PERSON, "Verknüpfung");
    }

    private static void beleihungsobjekt(ObjectNode vorhaben) {
        ObjectNode objekt = gruppe(vorhaben, "Beleihungsobjekt");
        ObjectNode grundstueck = obj(objekt, "Grundstück Musterweg 1", ObjectType.GRUNDSTUECK, "")
                .field("Fläche", "512 m²").field("Flur", "7").field("Flurstück", "123/4");
        obj(grundstueck, "Einfamilienhaus", ObjectType.GEBAEUDE, "")
                .field("Baujahr", 1998).field("Wohnfläche", "142 m²").field("Bauweise", "massiv");

        ObjectNode blatt = obj(objekt, "Grundbuch Musterstadt Blatt 4711", ObjectType.GRUNDBUCHBLATT, "");
        obj(blatt, "Aufschrift", ObjectType.AUFSCHRIFT, "").field("Amtsgericht", "Musterstadt");
        obj(blatt, "Bestandsverzeichnis", ObjectType.BESTANDVERZEICHNIS, "").field("Lfd. Nr.", 1);
        obj(blatt, "Abteilung I – Eigentümer", ObjectType.ABTEILUNG_1, "").field("Eigentümer", "Verkäufer (bisher)");
        obj(blatt, "Abteilung II – Lasten", ObjectType.ABTEILUNG_2, "").field("Eintrag", "Wegerecht");
        ObjectNode abt3 = obj(blatt, "Abteilung III – Grundpfandrechte", ObjectType.ABTEILUNG_3, "");
        obj(abt3, "Grundschuld 350.000 €", ObjectType.GESAMTGRUNDPFANDRECHT, "neu einzutragen").field("Betrag", "350.000,00 €");

        ObjectNode wert = obj(objekt, "Wertermittlung", ObjectType.WERT_ERMITTLUNG, "")
                .field("Marktwert", "430.000,00 €").field("Beleihungswert", "365.000,00 €");
        obj(wert, "Gutachten.pdf", ObjectType.FAEH_ERMITTELN_DOKUMENT, "Anlage");
    }

    // ---- Lazy-Loader (in der Praxis z. B. Datenbank oder Webservice)

    private static List<ObjectNode> ases(String an, int anzahl, ObjectNode parent) throws InterruptedException {
        langsam(parent);
        String[] arten = {"Gehaltsnachweis", "Kaufvertragsentwurf", "Exposé", "Grundbuchauszug", "Selbstauskunft"};
        List<ObjectNode> list = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            list.add(obj(null, "ASE " + arten[(i + an.length()) % arten.length], ObjectType.ASE, "Unterlage am " + an)
                    .field("Eingang", String.format("%02d.09.2026", 3 + i * 4))
                    .field("Geprüft", i % 2 == 0 ? "ja" : "nein"));
        }
        return list;
    }

    private static List<ObjectNode> vmgos(ObjectNode parent) throws InterruptedException {
        langsam(parent);
        ObjectNode vertrag = obj(null, "Girokonto-Vertrag", ObjectType.VP_VERTRAG, "Bestand").field("Seit", "2011");
        obj(vertrag, "DE00 1234 5678 0000 0000 00", ObjectType.VP_KONTO, "Konto").field("Saldo", "4.120,55 €");
        ObjectNode bauspar = obj(null, "Bausparvertrag", ObjectType.VP_VERTRAG, "Bestand")
                .field("Bausparsumme", "50.000,00 €");
        ObjectNode verbund = obj(null, "Verbund Mustermann", ObjectType.VERBUND, "Haushalt").field("Personen", 2);
        return List.of(vertrag, bauspar, verbund);
    }

    /** Simuliert eine langsame Datenquelle und zeigt in der Konsole, wann geladen wird. */
    private static void langsam(ObjectNode node) throws InterruptedException {
        System.out.println("  lade Unterobjekte von: " + node.name());
        Thread.sleep(300);
    }
}
