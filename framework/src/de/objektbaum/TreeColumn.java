package de.objektbaum;

import java.util.Objects;

/**
 * Spalte der Baumtabelle.
 *
 * <pre>
 * List.of(
 *     TreeColumn.name("Objektname"),       // Name mit Einrückung und Auf-/Zuklappen
 *     TreeColumn.field("ObjektId"),        // Wert eines Felds
 *     TreeColumn.type("Typ"),              // Objekttyp
 *     TreeColumn.field("Beziehungsart"),
 *     TreeColumn.field("Bezeichnung"))
 * </pre>
 *
 * @param header Spaltenüberschrift
 * @param kind   Art der Spalte
 * @param key    Feld-Key bei {@link Kind#FIELD}, sonst null
 */
public record TreeColumn(String header, Kind kind, String key) {

    public enum Kind { NAME, TYPE, FIELD }

    public TreeColumn {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.FIELD) {
            Objects.requireNonNull(key, "key");
        }
    }

    public static TreeColumn name(String header) {
        return new TreeColumn(header, Kind.NAME, null);
    }

    public static TreeColumn type(String header) {
        return new TreeColumn(header, Kind.TYPE, null);
    }

    /** Feldwert; Überschrift = Key. */
    public static TreeColumn field(String key) {
        return new TreeColumn(key, Kind.FIELD, key);
    }

    public static TreeColumn field(String key, String header) {
        return new TreeColumn(header, Kind.FIELD, key);
    }

    /** Wert dieser Spalte für einen Knoten (ohne HTML). */
    public String valueOf(ObjectNode node) {
        switch (kind) {
            case NAME:
                return node.name();
            case TYPE:
                return node.type().label();
            default: {
                Object v = node.fields().get(key);
                return v == null ? "" : HtmlRenderer.text(v);
            }
        }
    }
}
