package de.objektbaum;

import java.util.Objects;

/**
 * Textfeld, gebunden an ein Feld des Objekts.
 *
 * <pre>
 * TextField.input("Bezeichnung")                 // Eingabe, wird beim Speichern übernommen
 * TextField.output("ObjektId")                   // nur Anzeige
 * TextField.input("Bemerkung").rows(4)           // mehrzeilig
 * TextField.input("Betrag").label("Betrag in €")
 * </pre>
 *
 * @param key      Feld des Objekts
 * @param label    Beschriftung
 * @param editable true = Eingabe, false = nur Ausgabe
 * @param rows     1 = einzeilig, mehr = mehrzeilig
 */
public record TextField(String key, String label, boolean editable, int rows) implements Element.Input {

    public TextField {
        Objects.requireNonNull(key, "key");
        label = label == null ? key : label;
        if (rows < 1) {
            throw new IllegalArgumentException("TextField '" + key + "': rows muss >= 1 sein");
        }
    }

    /** Eingabefeld. */
    public static TextField input(String key) {
        return new TextField(key, key, true, 1);
    }

    /** Ausgabefeld (schreibgeschützt). */
    public static TextField output(String key) {
        return new TextField(key, key, false, 1);
    }

    public TextField label(String newLabel) {
        return new TextField(key, newLabel, editable, rows);
    }

    /** Mehrzeiliges Textfeld mit dieser Zeilenanzahl. */
    public TextField rows(int newRows) {
        return new TextField(key, label, editable, newRows);
    }
}
