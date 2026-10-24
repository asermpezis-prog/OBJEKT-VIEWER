package de.objektbaum;

/**
 * Ein Baustein innerhalb eines {@link Segment}s.
 *
 * <ul>
 *   <li>{@link FieldTable} – Felder als Tabelle</li>
 *   <li>{@link TextField} – Textfeld zur Eingabe oder nur zur Ausgabe</li>
 *   <li>{@link NumberField} – Zahl mit Prüfung (Nachkommastellen, Bereich, Einheit)</li>
 *   <li>{@link DateField} – Datum mit Prüfung (TT.MM.JJJJ)</li>
 *   <li>{@link CheckBox} – Ja/Nein</li>
 *   <li>{@link ComboBox} – Auswahl aus einer Dropdown-Liste, optional mit freier Eingabe</li>
 *   <li>{@link ComboList} – sichtbare Liste mit Einfach- oder Mehrfachauswahl</li>
 *   <li>{@link Button} – speichert die Eingaben und führt optional eine Aktion aus</li>
 * </ul>
 */
public sealed interface Element permits FieldTable, Button, Element.Input {

    /** Elemente, deren Wert beim Absenden in ein Feld des Objekts geschrieben wird. */
    sealed interface Input extends Element permits TextField, NumberField, DateField, CheckBox, ComboBox, ComboList {

        /** Key des Felds, das angezeigt und beim Speichern beschrieben wird. */
        String key();

        /** Beschriftung neben dem Element. */
        String label();

        /** false = nur Anzeige, wird nicht gespeichert. */
        boolean editable();

        /**
         * Prüft eine Eingabe und liefert den Wert, der gespeichert wird (z. B. einheitlich formatiert).
         *
         * @throws IllegalArgumentException mit einer Meldung für den Benutzer, wenn die Eingabe ungültig ist
         */
        default String check(String input) {
            return input == null ? "" : input;
        }
    }
}
