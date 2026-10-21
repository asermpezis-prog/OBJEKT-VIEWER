package de.objektbaum;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Darstellung eines Knotens im Main-Bereich: 1 bis 3 Spalten, jede mit mehreren Segmenten.
 * Die Trennlinien zwischen Spalten und Segmenten sind im Browser verschiebbar.
 *
 * <pre>
 * NodeView.of("Mitarbeiter",
 *     Column.of(
 *         Segment.fields("Stammdaten", "Rolle", "Seit"),
 *         Segment.fields("Kontakt", "E-Mail", "Telefon").horizontal()),
 *     Column.of(
 *         Segment.remainingFields("Weitere")).width(0.7));
 * </pre>
 *
 * @param name          Name der Ansicht; dient auch als Schlüssel zum Merken der Größen im Browser
 * @param columns       1 bis 3 Spalten
 * @param showChildren  ob unter den Frames die Unterobjekte angezeigt werden
 */
public record NodeView(String name, List<Column> columns, boolean showChildren) {

    public static final int MAX_COLUMNS = 3;

    public NodeView {
        Objects.requireNonNull(name, "name");
        columns = columns == null ? List.of() : List.copyOf(columns);
        if (columns.isEmpty() || columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("Ansicht '" + name + "': 1 bis " + MAX_COLUMNS
                    + " Spalten erlaubt, angegeben: " + columns.size());
        }
    }

    public static NodeView of(String name, Column... columns) {
        return new NodeView(name, List.of(columns), true);
    }

    /** Unterobjekte unter den Frames ausblenden. */
    public NodeView withoutChildren() {
        return new NodeView(name, columns, false);
    }

    /** Keys, die ein Segment dieser Ansicht ausdrücklich nennt. */
    public Set<String> explicitKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Column c : columns) {
            for (Segment s : c.segments()) {
                if (s.selection() == Segment.Selection.KEYS) {
                    keys.addAll(s.keys());
                }
            }
        }
        return keys;
    }

    /** Ermittelt die Keys, die ein Segment für dieses Objekt anzeigt (in Anzeigereihenfolge). */
    public List<String> keysFor(Segment segment, Map<String, Object> fields) {
        switch (segment.selection()) {
            case KEYS:
                return segment.keys();
            case ALL:
                return List.copyOf(fields.keySet());
            default: {
                Set<String> rest = new LinkedHashSet<>(fields.keySet());
                rest.removeAll(explicitKeys());
                return List.copyOf(rest);
            }
        }
    }
}
