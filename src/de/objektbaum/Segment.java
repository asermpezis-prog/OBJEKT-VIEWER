package de.objektbaum;

import java.util.List;
import java.util.Objects;

/**
 * Ein Segment (Frame) innerhalb einer Spalte. Zeigt eine Auswahl von Feldern als Tabelle.
 *
 * <pre>
 * Segment.fields("Kontakt", "E-Mail", "Telefon").horizontal()
 * Segment.allFields("Alle Felder")
 * Segment.remainingFields("Weitere")      // alle Felder, die kein anderes Segment zeigt
 * </pre>
 *
 * @param title     Überschrift des Segments
 * @param selection welche Felder gezeigt werden
 * @param keys      die Keys bei {@link Selection#KEYS}, in Anzeigereihenfolge
 * @param layout    feste Tabellen-Darstellung; null = folgt dem Umschalter im Header
 * @param height    relative Höhe innerhalb der Spalte (Startwert, danach mit der Maus änderbar)
 */
public record Segment(String title, Selection selection, List<String> keys, HtmlRenderer.Layout layout, double height) {

    /** Welche Felder ein Segment zeigt. */
    public enum Selection {
        /** Nur die angegebenen Keys. */
        KEYS,
        /** Alle Felder des Objekts. */
        ALL,
        /** Alle Felder, die kein Segment mit {@link #KEYS} derselben Ansicht zeigt. */
        REMAINING
    }

    public Segment {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(selection, "selection");
        keys = keys == null ? List.of() : List.copyOf(keys);
        if (selection == Selection.KEYS && keys.isEmpty()) {
            throw new IllegalArgumentException("Segment '" + title + "': mindestens ein Key angeben");
        }
        if (!(height > 0)) {
            throw new IllegalArgumentException("Segment '" + title + "': height muss > 0 sein");
        }
    }

    /** Zeigt genau diese Felder, in dieser Reihenfolge. */
    public static Segment fields(String title, String... keys) {
        return new Segment(title, Selection.KEYS, List.of(keys), null, 1);
    }

    /** Zeigt alle Felder des Objekts. */
    public static Segment allFields(String title) {
        return new Segment(title, Selection.ALL, List.of(), null, 1);
    }

    /** Zeigt alle Felder, die in keinem anderen Segment der Ansicht ausdrücklich genannt sind. */
    public static Segment remainingFields(String title) {
        return new Segment(title, Selection.REMAINING, List.of(), null, 1);
    }

    /** Darstellung fest auf zwei Spalten Key | Value. */
    public Segment vertical() {
        return new Segment(title, selection, keys, HtmlRenderer.Layout.VERTICAL, height);
    }

    /** Darstellung fest auf Keys als Spalten, Values darunter. */
    public Segment horizontal() {
        return new Segment(title, selection, keys, HtmlRenderer.Layout.HORIZONTAL, height);
    }

    /** Relative Starthöhe, z. B. 2 = doppelt so hoch wie ein Segment mit 1. */
    public Segment height(double relativeHeight) {
        return new Segment(title, selection, keys, layout, relativeHeight);
    }
}
