package de.objektbaum;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verbindet Namen aus der Konfiguration mit Java-Code.
 *
 * <pre>
 * // Java
 * bindings.action("rateBerechnen", (node, values) -> { ...; return "Rate berechnet"; });
 * bindings.options("berater", node -> beraterAusDatenbank());
 *
 * &lt;!-- viewer.xml --&gt;
 * &lt;button label="Rate berechnen" action="rateBerechnen"/&gt;
 * &lt;combo key="Berater" source="berater"/&gt;
 * </pre>
 */
public class Bindings {

    private final Map<String, Button.Action> actions = new ConcurrentHashMap<>();
    private final Map<String, OptionSource> options = new ConcurrentHashMap<>();

    /** Button-Aktion unter einem Namen anmelden. */
    public Bindings action(String name, Button.Action action) {
        actions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(action, "action"));
        return this;
    }

    /** Quelle für Auswahlwerte (ComboBox, ComboListe) unter einem Namen anmelden. */
    public Bindings options(String name, OptionSource source) {
        options.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(source, "source"));
        return this;
    }

    public Button.Action action(String name) {
        return actions.get(name);
    }

    public OptionSource options(String name) {
        return options.get(name);
    }

    /** Angemeldete Aktionsnamen, sortiert (für Fehlermeldungen). */
    public String actionNames() {
        return String.join(", ", new TreeMap<>(actions).keySet());
    }

    /** Angemeldete Optionsquellen, sortiert (für Fehlermeldungen). */
    public String optionNames() {
        return String.join(", ", new TreeMap<>(options).keySet());
    }
}
