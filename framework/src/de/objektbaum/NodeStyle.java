package de.objektbaum;

/**
 * Aussehen eines einzelnen Objekts in Navigation, Baumtabelle und Header – unabhängig von seinem Typ.
 *
 * <pre>
 * knoten.style(NodeStyle.of().color("#b91c1c").bold());                 // rot und fett
 * knoten.style(NodeStyle.of().background("#fef3c7").icon("⚠"));         // gelb hinterlegt, anderes Icon
 *
 * // oder für alle Objekte nach einer Regel (wird bei jedem Anzeigen gefragt):
 * builder.nodeStyle(n -> "Abgelehnt".equals(n.fields().get("Status"))
 *         ? NodeStyle.of().color("#6b7280").italic() : null);
 * </pre>
 *
 * Farben als {@code #rrggbb}. Ein mit {@link ObjectNode#style(NodeStyle)} gesetztes Aussehen hat Vorrang vor der Regel.
 *
 * @param color      Textfarbe oder null
 * @param background Hintergrund (Zeile in der Baumtabelle, Eintrag in der Navigation) oder null
 * @param isBold     fett
 * @param isItalic   kursiv
 * @param icon       eigenes Icon (Emoji/Zeichen) statt des Typ-Icons, oder null
 * @param tooltip    Hinweis beim Darüberfahren oder null
 */
public record NodeStyle(String color, String background, boolean isBold, boolean isItalic, String icon, String tooltip) {

    public NodeStyle {
        color = hex(color, "color");
        background = hex(background, "background");
        icon = icon == null || icon.isBlank() ? null : icon;
        tooltip = tooltip == null || tooltip.isBlank() ? null : tooltip;
    }

    /** Leeres Aussehen zum Weiterbauen. */
    public static NodeStyle of() {
        return new NodeStyle(null, null, false, false, null, null);
    }

    public NodeStyle color(String hex) {
        return new NodeStyle(hex, background, isBold, isItalic, icon, tooltip);
    }

    public NodeStyle background(String hex) {
        return new NodeStyle(color, hex, isBold, isItalic, icon, tooltip);
    }

    public NodeStyle bold() {
        return new NodeStyle(color, background, true, isItalic, icon, tooltip);
    }

    public NodeStyle italic() {
        return new NodeStyle(color, background, isBold, true, icon, tooltip);
    }

    public NodeStyle icon(String newIcon) {
        return new NodeStyle(color, background, isBold, isItalic, newIcon, tooltip);
    }

    public NodeStyle tooltip(String text) {
        return new NodeStyle(color, background, isBold, isItalic, icon, text);
    }

    private static String hex(String v, String what) {
        if (v == null || v.isBlank()) return null;
        String s = v.strip();
        if (s.matches("#[0-9a-fA-F]{3}")) {
            s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
        }
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("NodeStyle " + what + ": Farbe als #rrggbb erwartet, nicht „" + v + "“");
        }
        return s.toLowerCase(java.util.Locale.ROOT);
    }
}
