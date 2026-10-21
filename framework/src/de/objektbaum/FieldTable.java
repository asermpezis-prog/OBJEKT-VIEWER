package de.objektbaum;

import java.util.List;
import java.util.Objects;

/**
 * Felder des Objekts als Tabelle.
 *
 * <pre>
 * FieldTable.of("Rolle", "Seit")          // genau diese Felder
 * FieldTable.all()                        // alle Felder
 * FieldTable.remaining().horizontal()     // alle Felder, die sonst nirgends in der Ansicht stehen
 * </pre>
 *
 * @param selection welche Felder
 * @param keys      die Keys bei {@link Selection#KEYS}
 * @param layout    feste Darstellung; null = Darstellung des Segments bzw. Umschalter im Header
 */
public record FieldTable(Selection selection, List<String> keys, HtmlRenderer.Layout layout) implements Element {

    /** Welche Felder eine Tabelle zeigt. */
    public enum Selection {
        /** Nur die angegebenen Keys. */
        KEYS,
        /** Alle Felder des Objekts. */
        ALL,
        /** Alle Felder, die kein anderes Element derselben Ansicht ausdrücklich nennt. */
        REMAINING
    }

    public FieldTable {
        Objects.requireNonNull(selection, "selection");
        keys = keys == null ? List.of() : List.copyOf(keys);
        if (selection == Selection.KEYS && keys.isEmpty()) {
            throw new IllegalArgumentException("FieldTable: mindestens ein Key angeben");
        }
    }

    public static FieldTable of(String... keys) {
        return new FieldTable(Selection.KEYS, List.of(keys), null);
    }

    public static FieldTable all() {
        return new FieldTable(Selection.ALL, List.of(), null);
    }

    public static FieldTable remaining() {
        return new FieldTable(Selection.REMAINING, List.of(), null);
    }

    /** Zwei Spalten: Key | Value. */
    public FieldTable vertical() {
        return new FieldTable(selection, keys, HtmlRenderer.Layout.VERTICAL);
    }

    /** Keys als Spalten, Values darunter. */
    public FieldTable horizontal() {
        return new FieldTable(selection, keys, HtmlRenderer.Layout.HORIZONTAL);
    }
}
