package de.objektbaum;

import java.util.List;

/**
 * Liefert die Auswahlwerte einer {@link ComboBox} oder {@link ComboList}.
 * Wird bei jedem Anzeigen aufgerufen, kann also vom Objekt abhängen
 * (z. B. Werte aus einer Datenbank oder aus den Kindern des Objekts).
 */
@FunctionalInterface
public interface OptionSource {

    List<String> options(ObjectNode node);

    static OptionSource of(String... values) {
        List<String> list = List.of(values);
        return node -> list;
    }
}
