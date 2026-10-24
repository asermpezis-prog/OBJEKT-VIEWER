package de.objektbaum.beispiel;

import de.objektbaum.ObjectNode;
import de.objektbaum.ObjectType;
import de.objektbaum.ObjectViewer;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Beispielanwendung für das Objekt-Viewer-Framework: ein Baufinanzierungs-Vorgang mit erfundenen Daten.
 *
 * <p>Hier steht nur, was Code sein muss: Daten, Lazy-Loader, Button-Aktionen und Auswahllisten.
 * Typen, Baumtabelle und Ansichten stehen in beispiel/config/viewer.xml, das Aussehen in
 * beispiel/config/templates/ – beides ist ohne Neukompilieren änderbar.
 *
 * <pre>
 * ./run.sh                          http://localhost:8080/
 * ./run.sh 9090                     anderer Port
 * ./run.sh 8080 0.0.0.0             aus dem Netzwerk erreichbar
 * ./run.sh 8080 localhost meinOrdner  anderer Konfigurationsordner
 * </pre>
 */
public final class BeispielApp {

    /** Objekttypen dieser Anwendung (Anzeigetext und Icon kommen aus viewer.xml). */
    static final class Typ {
        static final ObjectType ABTEILUNG_1 = ObjectType.of("ABTEILUNG_1");
        static final ObjectType ABTEILUNG_2 = ObjectType.of("ABTEILUNG_2");
        static final ObjectType ABTEILUNG_3 = ObjectType.of("ABTEILUNG_3");
        static final ObjectType ADRESSE = ObjectType.of("ADRESSE");
        static final ObjectType ANGEBOT = ObjectType.of("ANGEBOT");
        static final ObjectType ASE = ObjectType.of("ASE");
        static final ObjectType AUFSCHRIFT = ObjectType.of("AUFSCHRIFT");
        static final ObjectType BESTANDVERZEICHNIS = ObjectType.of("BESTANDVERZEICHNIS");
        static final ObjectType FAEH_ERMITTELN_DOKUMENT = ObjectType.of("FAEH_ERMITTELN_DOKUMENT");
        static final ObjectType FAEH_SIMULATION = ObjectType.of("FAEH_SIMULATION");
        static final ObjectType FZVE = ObjectType.of("FZVE");
        static final ObjectType GEBAEUDE = ObjectType.of("GEBAEUDE");
        static final ObjectType GESAMTGRUNDPFANDRECHT = ObjectType.of("GESAMTGRUNDPFANDRECHT");
        static final ObjectType GRUNDBUCHBLATT = ObjectType.of("GRUNDBUCHBLATT");
        static final ObjectType GRUNDSTUECK = ObjectType.of("GRUNDSTUECK");
        static final ObjectType NEUES_ANGEBOT = ObjectType.of("NEUES_ANGEBOT");
        static final ObjectType PERSON = ObjectType.of("PERSON");
        static final ObjectType SPK_BAUFI_PRODUKT = ObjectType.of("SPK_BAUFI_PRODUKT");
        static final ObjectType VARIANTE = ObjectType.of("VARIANTE");
        static final ObjectType VERBUND = ObjectType.of("VERBUND");
        static final ObjectType VIN_PERSON = ObjectType.of("VIN_PERSON");
        static final ObjectType VORGANG = ObjectType.of("VORGANG");
        static final ObjectType VORGANGSDATEN = ObjectType.of("VORGANGSDATEN");
        static final ObjectType VORHABEN = ObjectType.of("VORHABEN");
        static final ObjectType VP_KONTO = ObjectType.of("VP_KONTO");
        static final ObjectType VP_VERTRAG = ObjectType.of("VP_VERTRAG");
        static final ObjectType WERT_ERMITTLUNG = ObjectType.of("WERT_ERMITTLUNG");
        static final ObjectType GRUPPE = ObjectType.GRUPPE;

        private Typ() {
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String host = args.length > 1 ? args[1] : "localhost";
        Path config = Path.of(args.length > 2 ? args[2] : "beispiel/config");

        ObjectViewer viewer = viewer(config);
        viewer.startWeb(host, port);

        String url = "http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port + "/";
        System.out.println("Objekt-Viewer läuft auf " + url + "  (Beenden mit Strg+C)");
        System.out.println("Konfiguration: " + config.toAbsolutePath().normalize()
                + "  – Änderungen an viewer.xml und templates/ wirken sofort");
        if (viewer.configError() != null) {
            System.out.println("  " + viewer.configError());
        }
    }

    /** Richtet den Viewer ein – gemeinsam für Browser- und Swing-Version. */
    static ObjectViewer viewer(Path configDir) {
        return builder(configDir)
                .title("Objekt-Viewer")
                .root(beispielDaten())
                .build();
    }

    /** Gemeinsame Teile aller Beispiele: Konfigurationsordner, Aktionen und Auswahllisten. */
    static ObjectViewer.Builder builder(Path configDir) {
        return ObjectViewer.builder()
                .configDir(configDir)
                // Aktionen und Auswahllisten, die viewer.xml per Name aufruft
                .action("rateBerechnen", BeispielApp::rateBerechnen)
                .action("alsHauptvariante", (node, values) -> {
                    node.field("Hauptvariante", "ja");
                    return "„" + node.name() + "“ ist jetzt die Hauptvariante.";
                })
                .options("berater", node -> List.of("A. Huber", "B. Wagner", "C. Schmid"))
                // Auswahlwerte der Variante, z. B. aus Datenbank, Webservice oder Enum.
                // Wird bei jedem Anzeigen aufgerufen; "node" ist das angezeigte Objekt.
                .options("statusVariante", BeispielApp::statusWerte)
                .options("produkte", BeispielApp::produkte);
    }

    // =====================================================================
    // Auswahlwerte für viewer.xml (source="…")
    // =====================================================================

    /** Status einer Variante; abgelehnte Varianten können nur noch abgelehnt bleiben. */
    private static List<String> statusWerte(ObjectNode node) {
        if ("Abgelehnt".equals(node.fields().get("Status"))) {
            return List.of("Abgelehnt");
        }
        return List.of("Entwurf", "Angeboten", "Angenommen", "Abgelehnt");
    }

    /** Produkte – in der Praxis z. B. aus dem Produktkatalog geladen. */
    private static List<String> produkte(ObjectNode node) {
        return List.of("Annuitätendarlehen", "KfW-Wohneigentum", "Bausparvertrag",
                "Forward-Darlehen", "Riester-Darlehen");
    }

    // =====================================================================
    // Button-Aktionen
    // =====================================================================

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
        return parent.child(name, Typ.GRUPPE);
    }

    static ObjectNode beispielDaten() {
        ObjectNode vorgang = obj(null, "Vorgang Baufinanzierung, Max Mustermann (100000001)", Typ.VORGANG, "")
                .field("Status", "In Bearbeitung")
                .field("Berater", "A. Huber")
                .field("Eröffnet", "02.09.2026")
                .field("Wiedervorlage", "")
                .field("Unterlagen vollständig", "nein")
                .field("Filiale", "Musterstadt Mitte");

        // ASEs am Vorgang: erst beim Aufklappen laden
        gruppe(vorgang, "ASEs_AM_VORGANG").lazy(p -> ases("Vorgang", 3, p));

        ObjectNode personen = gruppe(vorgang, "Personen");
        person(personen, "Max", "Mustermann", "14.05.1985", "Antragsteller");
        person(personen, "Erika", "Mustermann", "02.11.1987", "Mitantragsteller");

        ObjectNode vorhaben = obj(vorgang, "Vorhaben_440717", Typ.VORHABEN, "")
                .field("Art", "Kauf Bestandsimmobilie")
                .field("Kaufpreis", "420.000,00 €")
                .field("Nebenkosten", "46.200,00 €")
                .field("Modernisierung", "35.000,00 €");
        gruppe(vorhaben, "ASEs_AM_VORHABEN").lazy(p -> ases("Vorhaben", 2, p));
        beleihungsobjekt(vorhaben);

        obj(vorhaben, "VHV_444914_Finanzierungsvariante (A)", Typ.VARIANTE, "")
                .field("Status", "Entwurf")
                .field("Gesamtkosten", "501.200,00 €").field("Darlehenssumme", "380.000,00 €")
                .field("Eigenkapital", "121.200,00 €").field("Beleihungsauslauf", "84 %");

        ObjectNode variante = obj(vorhaben, "VHV_440718_Finanzierungsvariante", Typ.VARIANTE, "")
                .field("Status", "Angeboten")
                .field("Produkte", List.of("Annuitätendarlehen", "KfW-Wohneigentum"))
                .field("Gesamtkosten", "501.200,00 €").field("Darlehenssumme", "350.000,00 €")
                .field("Eigenkapital", "151.200,00 €").field("Beleihungsauslauf", "77 %")
                .field("Bemerkung", "Kunde wünscht 15 Jahre Zinsbindung.");
        gruppe(variante, "ASEs_AN_VARIANTE").lazy(p -> ases("Variante", 2, p));
        ObjectNode angebot = obj(variante, "SPK Darlehen", Typ.ANGEBOT, "Hauptangebot")
                .field("Bezeichnung", "SPK Darlehen / Neuvertrag 150.000,00 EUR")
                .field("Darlehensbetrag", "150.000,00")
                .field("Sollzins", "3,45")
                .field("Tilgung", "2,00")
                .field("Zinsbindung", "15 Jahre");
        obj(angebot, "SpkBaufi Annuität 15J", Typ.SPK_BAUFI_PRODUKT, "Produkt").field("Produktnr.", "AN-15-01");
        obj(variante, "KfW Wohneigentum", Typ.NEUES_ANGEBOT, "Zusatzangebot")
                .field("Darlehensbetrag", "100.000,00").field("Sollzins", "2,95").field("Tilgung", "2,00");
        obj(variante, "Machbarkeit", Typ.FAEH_SIMULATION, "").field("Ergebnis", "machbar");

        obj(vorhaben, "VHV_444515_Finanzierungsvariante", Typ.VARIANTE, "")
                .field("Status", "Abgelehnt").field("Darlehenssumme", "420.000,00 €");

        gruppe(vorgang, "VMGOs").lazy(BeispielApp::vmgos);
        obj(vorgang, "Fzve", Typ.FZVE, "");
        obj(vorgang, "Vorgangsdaten", Typ.VORGANGSDATEN, "").field("Kanal", "Filiale").field("Priorität", "normal");
        return vorgang;
    }

    private static void person(ObjectNode parent, String vorname, String nachname, String geb, String rolle) {
        ObjectNode p = obj(parent, vorname + " " + nachname, Typ.PERSON, rolle)
                .field("Vorname", vorname).field("Nachname", nachname).field("Geburtsdatum", geb)
                .field("Familienstand", "verheiratet")
                .field("E-Mail", vorname.toLowerCase(Locale.ROOT) + "." + nachname.toLowerCase(Locale.ROOT) + "@example.com")
                .field("Telefon", "").field("Kontaktweg", "E-Mail")
                .field("Beruf", "Angestellte/r").field("Nettoeinkommen", "3.400,00 €")
                .field("Werbeeinwilligung", "nein");
        obj(p, "Musterweg 1, 12345 Musterstadt", Typ.ADRESSE, "Wohnadresse")
                .field("Straße", "Musterweg 1").field("PLZ", "12345").field("Ort", "Musterstadt");
        obj(p, "VinPerson " + vorname, Typ.VIN_PERSON, "Verknüpfung");
    }

    private static void beleihungsobjekt(ObjectNode vorhaben) {
        ObjectNode objekt = gruppe(vorhaben, "Beleihungsobjekt");
        ObjectNode grundstueck = obj(objekt, "Grundstück Musterweg 1", Typ.GRUNDSTUECK, "")
                .field("Fläche", "512 m²").field("Flur", "7").field("Flurstück", "123/4");
        obj(grundstueck, "Einfamilienhaus", Typ.GEBAEUDE, "")
                .field("Baujahr", 1998).field("Wohnfläche", "142 m²").field("Bauweise", "massiv");

        ObjectNode blatt = obj(objekt, "Grundbuch Musterstadt Blatt 4711", Typ.GRUNDBUCHBLATT, "");
        obj(blatt, "Aufschrift", Typ.AUFSCHRIFT, "").field("Amtsgericht", "Musterstadt");
        obj(blatt, "Bestandsverzeichnis", Typ.BESTANDVERZEICHNIS, "").field("Lfd. Nr.", 1);
        obj(blatt, "Abteilung I – Eigentümer", Typ.ABTEILUNG_1, "").field("Eigentümer", "Verkäufer (bisher)");
        obj(blatt, "Abteilung II – Lasten", Typ.ABTEILUNG_2, "").field("Eintrag", "Wegerecht");
        ObjectNode abt3 = obj(blatt, "Abteilung III – Grundpfandrechte", Typ.ABTEILUNG_3, "");
        obj(abt3, "Grundschuld 350.000 €", Typ.GESAMTGRUNDPFANDRECHT, "neu einzutragen").field("Betrag", "350.000,00 €");

        ObjectNode wert = obj(objekt, "Wertermittlung", Typ.WERT_ERMITTLUNG, "")
                .field("Marktwert", "430.000,00 €").field("Beleihungswert", "365.000,00 €");
        obj(wert, "Gutachten.pdf", Typ.FAEH_ERMITTELN_DOKUMENT, "Anlage");
    }

    // ---- Lazy-Loader (in der Praxis z. B. Datenbank oder Webservice)

    private static List<ObjectNode> ases(String an, int anzahl, ObjectNode parent) throws InterruptedException {
        langsam(parent);
        String[] arten = {"Gehaltsnachweis", "Kaufvertragsentwurf", "Exposé", "Grundbuchauszug", "Selbstauskunft"};
        List<ObjectNode> list = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            list.add(obj(null, "ASE " + arten[(i + an.length()) % arten.length], Typ.ASE, "Unterlage am " + an)
                    .field("Eingang", String.format("%02d.09.2026", 3 + i * 4))
                    .field("Geprüft", i % 2 == 0 ? "ja" : "nein"));
        }
        return list;
    }

    private static List<ObjectNode> vmgos(ObjectNode parent) throws InterruptedException {
        langsam(parent);
        ObjectNode vertrag = obj(null, "Girokonto-Vertrag", Typ.VP_VERTRAG, "Bestand").field("Seit", "2011");
        obj(vertrag, "DE00 1234 5678 0000 0000 00", Typ.VP_KONTO, "Konto").field("Saldo", "4.120,55 €");
        ObjectNode bauspar = obj(null, "Bausparvertrag", Typ.VP_VERTRAG, "Bestand")
                .field("Bausparsumme", "50.000,00 €");
        ObjectNode verbund = obj(null, "Verbund Mustermann", Typ.VERBUND, "Haushalt").field("Personen", 2);
        return List.of(vertrag, bauspar, verbund);
    }

    /** Simuliert eine langsame Datenquelle und zeigt in der Konsole, wann geladen wird. */
    private static void langsam(ObjectNode node) throws InterruptedException {
        System.out.println("  lade Unterobjekte von: " + node.name());
        Thread.sleep(300);
    }
}
