package de.objektbaum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typ eines Objekts, z. B. "VORGANG" oder "PERSON".
 *
 * <p>Typen werden über ihre Kennung angesprochen. Anzeigetext und Icon kommen aus der Konfiguration
 * ({@code <type id="VORGANG" label="Vorgang" icon="📋"/>} in viewer.xml) oder aus dem Code
 * ({@link #define}) und können sich im laufenden Betrieb ändern – alle Objekte dieses Typs
 * zeigen dann sofort den neuen Text bzw. das neue Icon.
 *
 * <pre>
 * public static final ObjectType VORGANG = ObjectType.of("VORGANG");
 * ObjectNode n = new ObjectNode("Vorgang 4711", VORGANG);
 * </pre>
 */
public final class ObjectType {

    private static final Map<String, ObjectType> ALL = new ConcurrentHashMap<>();

    /** Sammelknoten ohne eigenen Fachtyp, z. B. "Personen". Zeigt keinen Typtext in der Baumtabelle. */
    public static final ObjectType GRUPPE = define("GRUPPE", "", "📂");

    /** Typ für Objekte ohne Angabe. */
    public static final ObjectType UNBEKANNT = define("UNBEKANNT", "UNBEKANNT", "•");

    private final String id;
    private volatile String label;
    private volatile String icon;

    private ObjectType(String id, String label, String icon) {
        this.id = id;
        this.label = label;
        this.icon = icon;
    }

    /**
     * Den Typ mit dieser Kennung holen; gibt es ihn noch nicht, wird er angelegt
     * (Anzeigetext = Kennung, Icon = Punkt), bis die Konfiguration etwas anderes sagt.
     */
    public static ObjectType of(String id) {
        String key = normalize(id);
        return ALL.computeIfAbsent(key, k -> new ObjectType(k, k, "•"));
    }

    /** Typ anlegen bzw. Anzeigetext und Icon eines vorhandenen Typs ändern. */
    public static ObjectType define(String id, String label, String icon) {
        ObjectType t = of(id);
        t.label = label == null ? t.id : label;
        t.icon = icon == null || icon.isBlank() ? "•" : icon;
        return t;
    }

    /** Alle bekannten Typen (für Auswahllisten, Dokumentation, Tests). */
    public static List<ObjectType> all() {
        List<ObjectType> list = new ArrayList<>(ALL.values());
        list.sort((a, b) -> a.id.compareTo(b.id));
        return list;
    }

    private static String normalize(String id) {
        Objects.requireNonNull(id, "id");
        String s = id.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Typ-Kennung darf nicht leer sein");
        }
        return s;
    }

    /** Kennung, z. B. "VORGANG". */
    public String id() { return id; }

    /** Kennung (wie bei einem Enum). */
    public String name() { return id; }

    /** Anzeigetext; bei {@link #GRUPPE} leer. */
    public String label() { return label; }

    public String icon() { return icon; }

    @Override
    public String toString() {
        return id;
    }
}
