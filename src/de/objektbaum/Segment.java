package de.objektbaum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ein Segment (Frame) innerhalb einer Spalte. Enthält beliebig viele {@link Element}e
 * von oben nach unten: Feld-Tabellen, Textfelder, ComboBoxen, ComboListen und Buttons.
 *
 * <pre>
 * Segment.fields("Kontakt", "E-Mail", "Telefon").horizontal()     // Kurzform: eine Feld-Tabelle
 * Segment.remainingFields("Weitere")
 *
 * Segment.of("Bearbeiten",
 *         TextField.output("ObjektId"),
 *         TextField.input("Bezeichnung"),
 *         ComboBox.of("Status", "Offen", "Abgeschlossen"),
 *         ComboList.of("Produkte", "A", "B", "C").multiple(),
 *         Button.save("Speichern"))
 * </pre>
 *
 * @param title    Überschrift
 * @param elements Inhalt, von oben nach unten
 * @param layout   Darstellung der Feld-Tabellen ohne eigene Angabe; null = Umschalter im Header
 * @param height   relative Starthöhe innerhalb der Spalte
 */
public record Segment(String title, List<Element> elements, HtmlRenderer.Layout layout, double height) {

    public Segment {
        Objects.requireNonNull(title, "title");
        elements = elements == null ? List.of() : List.copyOf(elements);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Segment '" + title + "': mindestens ein Element angeben");
        }
        if (!(height > 0)) {
            throw new IllegalArgumentException("Segment '" + title + "': height muss > 0 sein");
        }
    }

    /** Segment mit beliebigen Elementen. */
    public static Segment of(String title, Element... elements) {
        return new Segment(title, List.of(elements), null, 1);
    }

    /** Kurzform: eine Tabelle mit genau diesen Feldern. */
    public static Segment fields(String title, String... keys) {
        return of(title, FieldTable.of(keys));
    }

    /** Kurzform: eine Tabelle mit allen Feldern. */
    public static Segment allFields(String title) {
        return of(title, FieldTable.all());
    }

    /** Kurzform: eine Tabelle mit allen Feldern, die sonst nirgends in der Ansicht stehen. */
    public static Segment remainingFields(String title) {
        return of(title, FieldTable.remaining());
    }

    /** Hängt weitere Elemente an. */
    public Segment add(Element... more) {
        List<Element> all = new ArrayList<>(elements);
        all.addAll(List.of(more));
        return new Segment(title, all, layout, height);
    }

    /** Feld-Tabellen dieses Segments als Key | Value. */
    public Segment vertical() {
        return new Segment(title, elements, HtmlRenderer.Layout.VERTICAL, height);
    }

    /** Feld-Tabellen dieses Segments mit Keys als Spalten. */
    public Segment horizontal() {
        return new Segment(title, elements, HtmlRenderer.Layout.HORIZONTAL, height);
    }

    /** Relative Starthöhe, z. B. 2 = doppelt so hoch wie ein Segment mit 1. */
    public Segment height(double relativeHeight) {
        return new Segment(title, elements, layout, relativeHeight);
    }
}
