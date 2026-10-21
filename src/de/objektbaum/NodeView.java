package de.objektbaum;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Darstellung eines Knotens im Main-Bereich: 1 bis 3 Spalten, jede mit mehreren Segmenten.
 * Die Trennlinien zwischen Spalten und Segmenten sind im Browser verschiebbar.
 *
 * <pre>
 * NodeView.of("Person",
 *     Column.of(
 *         Segment.fields("Stammdaten", "Name", "Geburtsdatum"),
 *         Segment.of("Bearbeiten", TextField.input("Telefon"), Button.save("Speichern"))),
 *     Column.of(
 *         Segment.remainingFields("Weitere")).width(0.7));
 * </pre>
 *
 * @param name         Name der Ansicht; dient auch als Schlüssel zum Merken der Größen im Browser
 * @param columns      1 bis 3 Spalten
 * @param showChildren ob unter den Frames die Unterobjekte angezeigt werden
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

    /** Eindeutige Kennung eines Elements innerhalb der Ansicht, z. B. "c0s1e2". */
    public static String elementId(int column, int segment, int element) {
        return "c" + column + "s" + segment + "e" + element;
    }

    /** Sucht einen Button über seine {@link #elementId}. */
    public Optional<Button> button(String id) {
        for (int c = 0; c < columns.size(); c++) {
            List<Segment> segs = columns.get(c).segments();
            for (int s = 0; s < segs.size(); s++) {
                List<Element> els = segs.get(s).elements();
                for (int e = 0; e < els.size(); e++) {
                    if (els.get(e) instanceof Button b && elementId(c, s, e).equals(id)) {
                        return Optional.of(b);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Alle Eingabe-Elemente, deren Werte beim Absenden gespeichert werden. */
    public List<Element.Input> editableInputs() {
        List<Element.Input> result = new ArrayList<>();
        for (Column c : columns) {
            for (Segment s : c.segments()) {
                for (Element e : s.elements()) {
                    if (e instanceof Element.Input in && in.editable()) {
                        result.add(in);
                    }
                }
            }
        }
        return result;
    }

    /** true, wenn die Ansicht Eingaben oder Buttons enthält (dann wird ein Formular erzeugt). */
    public boolean isInteractive() {
        for (Column c : columns) {
            for (Segment s : c.segments()) {
                for (Element e : s.elements()) {
                    if (e instanceof Element.Input || e instanceof Button) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Keys, die ein Element dieser Ansicht ausdrücklich nennt (Tabellen mit Keys und Eingaben). */
    public Set<String> explicitKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Column c : columns) {
            for (Segment s : c.segments()) {
                for (Element e : s.elements()) {
                    if (e instanceof FieldTable t && t.selection() == FieldTable.Selection.KEYS) {
                        keys.addAll(t.keys());
                    } else if (e instanceof Element.Input in) {
                        keys.add(in.key());
                    }
                }
            }
        }
        return keys;
    }

    /** Die Keys, die eine Tabelle für dieses Objekt anzeigt (in Anzeigereihenfolge). */
    public List<String> keysFor(FieldTable table, Map<String, Object> fields) {
        switch (table.selection()) {
            case KEYS:
                return table.keys();
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
