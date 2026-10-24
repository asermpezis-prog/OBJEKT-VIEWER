package de.objektbaum;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Checkbox (Ja/Nein), gebunden an ein Feld des Objekts. Gespeichert wird {@code "ja"} oder {@code "nein"}.
 * Als angehakt gelten vorhandene Werte ja, true, 1, x, yes, wahr (Groß-/Kleinschreibung egal).
 *
 * <pre>
 * CheckBox.of("Geprüft")
 * CheckBox.of("Hauptvariante").readOnly()
 * </pre>
 *
 * @param key      Feld des Objekts
 * @param label    Beschriftung
 * @param editable false = nur Anzeige
 */
public record CheckBox(String key, String label, boolean editable) implements Element.Input {

    public static final String YES = "ja";
    public static final String NO = "nein";

    private static final Set<String> TRUE = Set.of("ja", "j", "true", "1", "x", "yes", "y", "wahr", "on");

    public CheckBox {
        Objects.requireNonNull(key, "key");
        label = label == null ? key : label;
    }

    public static CheckBox of(String key) {
        return new CheckBox(key, key, true);
    }

    public CheckBox label(String newLabel) {
        return new CheckBox(key, newLabel, editable);
    }

    public CheckBox readOnly() {
        return new CheckBox(key, label, false);
    }

    /** true, wenn der Feldwert als "angehakt" gilt. */
    public static boolean isChecked(Object value) {
        return value != null && TRUE.contains(String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String check(String input) {
        return isChecked(input) ? YES : NO;
    }
}
