package de.objektbaum;

import java.util.Objects;

/**
 * Dropdown-Auswahl, gebunden an ein Feld des Objekts.
 *
 * <pre>
 * ComboBox.of("Status", "Offen", "In Prüfung", "Abgeschlossen")
 * ComboBox.of("Berater", node -> beraterAusDatenbank())
 * ComboBox.of("Region", "Nord", "Süd").freeInput()   // Vorschläge, aber freie Eingabe erlaubt
 * ComboBox.of("Status", "Offen").readOnly()          // nur Anzeige
 * </pre>
 *
 * @param key       Feld des Objekts
 * @param label     Beschriftung
 * @param options   Auswahlwerte
 * @param allowFreeInput true = Werte sind nur Vorschläge, beliebiger Text ist erlaubt
 * @param editable  false = nur Anzeige
 */
public record ComboBox(String key, String label, OptionSource options, boolean allowFreeInput, boolean editable)
        implements Element.Input {

    public ComboBox {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(options, "options");
        label = label == null ? key : label;
    }

    public static ComboBox of(String key, String... options) {
        return new ComboBox(key, key, OptionSource.of(options), false, true);
    }

    public static ComboBox of(String key, OptionSource options) {
        return new ComboBox(key, key, options, false, true);
    }

    public ComboBox label(String newLabel) {
        return new ComboBox(key, newLabel, options, allowFreeInput, editable);
    }

    /** Werte als Vorschläge, beliebige Eingabe erlaubt. */
    public ComboBox freeInput() {
        return new ComboBox(key, label, options, true, editable);
    }

    public ComboBox readOnly() {
        return new ComboBox(key, label, options, allowFreeInput, false);
    }
}
