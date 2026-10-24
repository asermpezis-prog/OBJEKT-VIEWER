package de.objektbaum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Übernimmt Eingaben in die Felder eines Objekts und führt die Aktion eines Buttons aus.
 * Wird von der Browser-Version ({@link TreeServer}) und der Swing-Version gleichermaßen benutzt,
 * damit sich beide genau gleich verhalten.
 *
 * <p>Ablauf: Zuerst werden alle Eingaben geprüft ({@link Element.Input#check}). Ist eine ungültig,
 * wird <b>nichts</b> gespeichert und keine Aktion ausgeführt; die Meldung nennt die Felder.
 */
public final class FormSubmit {

    private FormSubmit() {
    }

    /**
     * Ergebnis eines Absendens.
     *
     * @param message Meldung für den Benutzer, oder null
     * @param invalid ungültige Eingaben: Key → Fehlertext (leer, wenn alles gültig war)
     */
    public record Result(HtmlRenderer.Message message, Map<String, String> invalid) {
        public Result {
            invalid = invalid == null ? Map.of() : Map.copyOf(invalid);
        }

        public boolean ok() {
            return invalid.isEmpty();
        }
    }

    /** Wie {@link #check}, liefert nur die Meldung. */
    public static HtmlRenderer.Message submit(ObjectNode node, NodeView view,
                                              Map<String, List<String>> form, String buttonId) {
        return check(node, view, form, buttonId).message();
    }

    /**
     * @param node     das angezeigte Objekt
     * @param view     seine Ansicht (null = nichts zu speichern)
     * @param form     eingegebene Werte, Key = Feldname; Keys mit "_" am Anfang werden ignoriert
     * @param buttonId {@link NodeView#elementId} des geklickten Buttons, oder null/leer = nur speichern
     * @return Meldung und ggf. ungültige Eingaben
     */
    public static Result check(ObjectNode node, NodeView view, Map<String, List<String>> form, String buttonId) {
        if (view == null) {
            return new Result(null, null);
        }
        // 1. Eingaben prüfen (nur Elemente, die die Ansicht als editierbar definiert)
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> invalid = new LinkedHashMap<>();
        StringBuilder problems = new StringBuilder();
        for (Element.Input in : view.editableInputs()) {
            List<String> raw = form.getOrDefault(in.key(), List.of());
            try {
                if (in instanceof ComboList cl && cl.multiSelect()) {
                    values.put(in.key(), List.copyOf(raw));
                } else if (in instanceof CheckBox cb) {
                    // Browser schickt "nein" (verstecktes Feld) und ggf. zusätzlich "ja"
                    values.put(in.key(), raw.stream().anyMatch(CheckBox::isChecked) ? CheckBox.YES : CheckBox.NO);
                } else {
                    values.put(in.key(), in.check(raw.isEmpty() ? "" : raw.get(0)));
                }
            } catch (IllegalArgumentException e) {
                invalid.put(in.key(), e.getMessage());
                if (problems.length() > 0) problems.append("; ");
                problems.append(in.label()).append(": ").append(e.getMessage());
            }
        }
        if (!invalid.isEmpty()) {
            return new Result(new HtmlRenderer.Message("Nicht gespeichert – bitte prüfen: " + problems, true), invalid);
        }

        // 2. Eingaben übernehmen
        values.forEach(node::field);

        // 3. Button-Aktion
        String message = "Gespeichert.";
        if (buttonId != null && !buttonId.isEmpty()) {
            Optional<Button> button = view.button(buttonId);
            if (button.isPresent() && button.get().action() != null) {
                Map<String, List<String>> all = new LinkedHashMap<>(form);
                all.keySet().removeIf(k -> k.startsWith("_"));
                try {
                    message = button.get().action().run(node, all);
                } catch (Exception e) {
                    return new Result(new HtmlRenderer.Message(button.get().label() + " fehlgeschlagen: " + e.getMessage(), true), null);
                }
            }
        }
        return new Result(message == null || message.isBlank() ? null : new HtmlRenderer.Message(message, false), null);
    }
}
