package de.objektbaum;

/**
 * Typ eines Objekts. Werte nach Bedarf anpassen – die Anzeige passt sich automatisch an.
 */
public enum ObjectType {
    FIRMA("Firma", "🏢"),
    ABTEILUNG("Abteilung", "🗂"),
    TEAM("Team", "👥"),
    MITARBEITER("Mitarbeiter", "👤"),
    PROJEKT("Projekt", "📁"),
    ORDNER("Ordner", "📂"),
    DOKUMENT("Dokument", "📄"),
    SONSTIGES("Sonstiges", "•");

    private final String label;
    private final String icon;

    ObjectType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() { return label; }
    public String icon() { return icon; }
}
