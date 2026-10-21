package de.objektbaum;

import java.util.Objects;

/**
 * Sichtbare Auswahlliste, gebunden an ein Feld des Objekts.
 * Bei Mehrfachauswahl wird im Feld eine {@code List<String>} gespeichert, sonst ein String.
 *
 * <pre>
 * ComboList.of("Produkte", "Annuitätendarlehen", "KfW", "Bausparvertrag").multiple().rows(5)
 * ComboList.of("Sicherheit", node -> ...)
 * </pre>
 *
 * @param key      Feld des Objekts
 * @param label    Beschriftung
 * @param options  Auswahlwerte
 * @param multiSelect Mehrfachauswahl erlaubt
 * @param rows     sichtbare Zeilen
 * @param editable false = nur Anzeige
 */
public record ComboList(String key, String label, OptionSource options, boolean multiSelect, int rows, boolean editable)
        implements Element.Input {

    public ComboList {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(options, "options");
        label = label == null ? key : label;
        if (rows < 2) {
            throw new IllegalArgumentException("ComboList '" + key + "': rows muss >= 2 sein");
        }
    }

    public static ComboList of(String key, String... options) {
        return new ComboList(key, key, OptionSource.of(options), false, 4, true);
    }

    public static ComboList of(String key, OptionSource options) {
        return new ComboList(key, key, options, false, 4, true);
    }

    public ComboList label(String newLabel) {
        return new ComboList(key, newLabel, options, multiSelect, rows, editable);
    }

    /** Mehrfachauswahl (Strg/Cmd + Klick). */
    public ComboList multiple() {
        return new ComboList(key, label, options, true, rows, editable);
    }

    public ComboList rows(int visibleRows) {
        return new ComboList(key, label, options, multiSelect, visibleRows, editable);
    }

    public ComboList readOnly() {
        return new ComboList(key, label, options, multiSelect, rows, false);
    }
}
