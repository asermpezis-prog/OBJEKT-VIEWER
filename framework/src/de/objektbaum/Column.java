package de.objektbaum;

import java.util.List;

/**
 * Eine Spalte einer {@link NodeView}: mehrere Segmente untereinander.
 *
 * @param segments die Segmente von oben nach unten (mindestens eins)
 * @param width    relative Breite (Startwert, danach mit der Maus änderbar)
 */
public record Column(List<Segment> segments, double width) {

    public Column {
        segments = segments == null ? List.of() : List.copyOf(segments);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Eine Spalte braucht mindestens ein Segment");
        }
        if (!(width > 0)) {
            throw new IllegalArgumentException("width muss > 0 sein");
        }
    }

    public static Column of(Segment... segments) {
        return new Column(List.of(segments), 1);
    }

    /** Relative Startbreite, z. B. 2 = doppelt so breit wie eine Spalte mit 1. */
    public Column width(double relativeWidth) {
        return new Column(segments, relativeWidth);
    }
}
