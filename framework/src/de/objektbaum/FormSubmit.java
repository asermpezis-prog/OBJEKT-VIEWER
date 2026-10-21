package de.objektbaum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Übernimmt Eingaben in die Felder eines Objekts und führt die Aktion eines Buttons aus.
 * Wird von der Browser-Version ({@link TreeServer}) und der Swing-Version gleichermaßen benutzt,
 * damit sich beide genau gleich verhalten.
 */
public final class FormSubmit {

    private FormSubmit() {
    }

    /**
     * @param node     das angezeigte Objekt
     * @param view     seine Ansicht (null = nichts zu speichern)
     * @param form     eingegebene Werte, Key = Feldname; Keys mit "_" am Anfang werden ignoriert
     * @param buttonId {@link NodeView#elementId} des geklickten Buttons, oder null/leer = nur speichern
     * @return Meldung für den Benutzer, oder null
     */
    public static HtmlRenderer.Message submit(ObjectNode node, NodeView view,
                                              Map<String, List<String>> form, String buttonId) {
        if (view == null) {
            return null;
        }
        // 1. Eingaben übernehmen (nur Elemente, die die Ansicht als editierbar definiert)
        for (Element.Input in : view.editableInputs()) {
            List<String> values = form.getOrDefault(in.key(), List.of());
            if (in instanceof ComboList cl && cl.multiSelect()) {
                node.field(in.key(), List.copyOf(values));
            } else {
                node.field(in.key(), values.isEmpty() ? "" : values.get(0));
            }
        }

        // 2. Button-Aktion
        String message = "Gespeichert.";
        if (buttonId != null && !buttonId.isEmpty()) {
            Optional<Button> button = view.button(buttonId);
            if (button.isPresent() && button.get().action() != null) {
                Map<String, List<String>> values = new LinkedHashMap<>(form);
                values.keySet().removeIf(k -> k.startsWith("_"));
                try {
                    message = button.get().action().run(node, values);
                } catch (Exception e) {
                    return new HtmlRenderer.Message(button.get().label() + " fehlgeschlagen: " + e.getMessage(), true);
                }
            }
        }
        return message == null || message.isBlank() ? null : new HtmlRenderer.Message(message, false);
    }
}
