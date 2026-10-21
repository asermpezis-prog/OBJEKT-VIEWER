package de.objektbaum;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Button in einem Segment. Beim Klick werden zuerst alle Eingaben der Seite in die Felder
 * des Objekts übernommen, danach läuft die Aktion. Ihr Rückgabetext wird als Meldung angezeigt.
 *
 * <pre>
 * Button.save("Speichern")
 * Button.of("Rate berechnen", (node, values) -> {
 *     node.field("Monatsrate", ...);
 *     return "Rate berechnet";
 * })
 * </pre>
 *
 * @param label  Beschriftung
 * @param action Aktion nach dem Speichern; null = nur speichern
 */
public record Button(String label, Action action) implements Element {

    /** Aktion eines Buttons. */
    @FunctionalInterface
    public interface Action {
        /**
         * @param node   das angezeigte Objekt (Eingaben sind bereits übernommen)
         * @param values alle abgeschickten Formularwerte, Key = Feldname
         * @return Meldung für den Benutzer, oder null für keine Meldung
         * @throws Exception wird als Fehlermeldung angezeigt
         */
        String run(ObjectNode node, Map<String, List<String>> values) throws Exception;
    }

    public Button {
        Objects.requireNonNull(label, "label");
    }

    /** Speichert nur die Eingaben. */
    public static Button save(String label) {
        return new Button(label, (node, values) -> "Gespeichert.");
    }

    public static Button of(String label, Action action) {
        return new Button(label, Objects.requireNonNull(action, "action"));
    }
}
