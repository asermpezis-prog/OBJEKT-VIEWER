package de.objektbaum;

/**
 * Typ eines Objekts. Werte nach Bedarf anpassen – die Anzeige passt sich automatisch an.
 * Der Anzeigetext entspricht dem Typnamen im Fachsystem.
 */
public enum ObjectType {

    // ---- Vorgang, Personen, Angebote
    VORGANGSDATEN("Vorgangsdaten", "🗃"),
    VORGANG("Vorgang", "📋"),
    VIN_VORGANG("VinVorgang", "📋"),
    VORHABEN("Vorhaben", "🏗"),
    VARIANTE("Variante", "🔀"),
    ANGEBOT("Angebot", "💶"),
    NEUES_ANGEBOT("NeuesAngebot", "💶"),
    ASE("Ase", "📎"),
    PERSON("Person", "👤"),
    VERBUND("Verbund", "👥"),
    VIN_PERSON("VinPerson", "👤"),
    VIN_ANGEBOT("VinAngebot", "💶"),
    ADRESSE("Adresse", "📍"),
    VP_VERTRAG("VpVertrag", "📑"),
    VP_KONTO("VpKonto", "🏦"),
    FZVE("Fzve", "⚙"),

    // ---- Objekt, Grundbuch, Bewertung
    GEBAEUDE("Gebaeude", "🏠"),
    GESAMTGRUNDPFANDRECHT("Gesamtgrundpfandrecht", "⚖"),
    ABTEILUNG_1("Abteilung_1", "Ⅰ"),
    ABTEILUNG_2("Abteilung_2", "Ⅱ"),
    ABTEILUNG_3("Abteilung_3", "Ⅲ"),
    BESTANDVERZEICHNIS("Bestandverzeichnis", "📒"),
    AUFSCHRIFT("Aufschrift", "🏷"),
    GRUNDSTUECK("Grundstueck", "🗺"),
    GRUNDBUCHBLATT("Grundbuchblatt", "📘"),
    FAEH_SIMULATION("FaeHSimulation", "🧮"),
    GEGENENTSCHEIDUNGSGRUND("Gegenentscheidungsgrund", "❗"),
    SPK_BAUFI_PRODUKT("SpkBaufiProdukt", "📦"),
    WERT_ERMITTLUNG("WertErmittlung", "📐"),
    FAEH_ERMITTELN_DOKUMENT("FaehErmittelnDokument", "📄"),

    // ---- Sonstiges
    /** Sammelknoten ohne eigenen Fachtyp, z. B. "Personen" oder "ASEs_AM_VORGANG". */
    GRUPPE("", "📂"),
    UNBEKANNT("UNBEKANNT", "•");

    private final String label;
    private final String icon;

    ObjectType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    /** Anzeigetext; bei {@link #GRUPPE} leer. */
    public String label() { return label; }
    public String icon() { return icon; }
}
