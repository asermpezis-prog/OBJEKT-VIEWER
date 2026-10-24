package de.objektbaum;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;

/**
 * Datumsfeld mit Prüfung, gebunden an ein Feld des Objekts. Gespeichert wird {@code TT.MM.JJJJ}
 * (z. B. {@code "02.09.2026"}), wie die übrigen Datumswerte im Fachsystem.
 *
 * <p>Erlaubte Eingaben: {@code 2.9.2026}, {@code 02.09.2026}, {@code 2.9.26} (= 2026), {@code 2026-09-02}.
 * Leer ist erlaubt.
 *
 * <pre>
 * DateField.of("Eröffnet")
 * DateField.of("Geburtsdatum").readOnly()
 * </pre>
 *
 * @param key      Feld des Objekts
 * @param label    Beschriftung
 * @param editable false = nur Anzeige
 */
public record DateField(String key, String label, boolean editable) implements Element.Input {

    /** Speicher- und Anzeigeformat. */
    public static final DateTimeFormatter GERMAN = DateTimeFormatter.ofPattern("dd.MM.uuuu");

    private static final DateTimeFormatter LOOSE = DateTimeFormatter.ofPattern("d.M.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    public DateField {
        Objects.requireNonNull(key, "key");
        label = label == null ? key : label;
    }

    public static DateField of(String key) {
        return new DateField(key, key, true);
    }

    public DateField label(String newLabel) {
        return new DateField(key, newLabel, editable);
    }

    public DateField readOnly() {
        return new DateField(key, label, false);
    }

    @Override
    public String check(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return "";
        LocalDate d = parse(s);
        if (d == null) {
            throw new IllegalArgumentException("„" + s + "“ ist kein gültiges Datum (TT.MM.JJJJ)");
        }
        return GERMAN.format(d);
    }

    /** Liest ein Datum (TT.MM.JJJJ, T.M.JJ oder JJJJ-MM-TT); null, wenn es keins ist. */
    public static LocalDate parse(String text) {
        if (text == null) return null;
        String s = text.trim();
        try {
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(s);
            }
            if (s.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{2}")) {
                int dot = s.lastIndexOf('.');
                s = s.substring(0, dot + 1) + "20" + s.substring(dot + 1);
            }
            if (s.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
                return LocalDate.parse(s, LOOSE);
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return null;
    }
}
