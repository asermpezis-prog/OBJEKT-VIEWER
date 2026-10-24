package de.objektbaum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

/**
 * Zahlenfeld mit Prüfung, gebunden an ein Feld des Objekts. Der Wert wird im deutschen Format
 * gespeichert, z. B. {@code "150.000,00"} – so wie die übrigen Beträge im Fachsystem.
 *
 * <p>Erlaubte Eingaben: {@code 150000}, {@code 150.000}, {@code 150.000,5}, {@code 3,45}, {@code 3.45},
 * auch mit Einheit ({@code "150.000 €"}). Leer ist erlaubt und speichert einen leeren Text.
 *
 * <pre>
 * NumberField.of("Darlehensbetrag").decimals(2).unit("€").range("0", "10000000")
 * NumberField.of("Sollzins").decimals(2).unit("%")
 * NumberField.of("Personen").decimals(0)
 * </pre>
 *
 * @param key      Feld des Objekts
 * @param label    Beschriftung
 * @param decimals Nachkommastellen beim Speichern (0 = ganze Zahl)
 * @param min      kleinster erlaubter Wert oder null
 * @param max      größter erlaubter Wert oder null
 * @param unit     Einheit hinter dem Feld (nur Anzeige, wird nicht gespeichert) oder ""
 * @param editable false = nur Anzeige
 */
public record NumberField(String key, String label, int decimals, BigDecimal min, BigDecimal max, String unit,
                          boolean editable) implements Element.Input {

    public NumberField {
        Objects.requireNonNull(key, "key");
        label = label == null ? key : label;
        unit = unit == null ? "" : unit;
        if (decimals < 0 || decimals > 10) {
            throw new IllegalArgumentException("NumberField '" + key + "': decimals muss zwischen 0 und 10 liegen");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("NumberField '" + key + "': min ist größer als max");
        }
    }

    public static NumberField of(String key) {
        return new NumberField(key, key, 2, null, null, "", true);
    }

    public NumberField label(String newLabel) {
        return new NumberField(key, newLabel, decimals, min, max, unit, editable);
    }

    public NumberField decimals(int n) {
        return new NumberField(key, label, n, min, max, unit, editable);
    }

    /** Erlaubter Bereich; Werte im deutschen oder englischen Format, null = offen. */
    public NumberField range(String newMin, String newMax) {
        return new NumberField(key, label, decimals, newMin == null ? null : parse(newMin),
                newMax == null ? null : parse(newMax), unit, editable);
    }

    public NumberField unit(String newUnit) {
        return new NumberField(key, label, decimals, min, max, newUnit, editable);
    }

    public NumberField readOnly() {
        return new NumberField(key, label, decimals, min, max, unit, false);
    }

    /** Prüft die Eingabe und liefert sie im Speicherformat, z. B. "150.000,00". */
    @Override
    public String check(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return "";
        BigDecimal d;
        try {
            d = parse(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("„" + s + "“ ist keine Zahl");
        }
        d = d.setScale(decimals, RoundingMode.HALF_UP);
        if (min != null && d.compareTo(min) < 0) {
            throw new IllegalArgumentException("mindestens " + format(min, decimals) + (unit.isEmpty() ? "" : " " + unit));
        }
        if (max != null && d.compareTo(max) > 0) {
            throw new IllegalArgumentException("höchstens " + format(max, decimals) + (unit.isEmpty() ? "" : " " + unit));
        }
        return format(d, decimals);
    }

    /**
     * Liest eine Zahl im deutschen Format ("150.000,00", "3,45") oder mit Dezimalpunkt ("3.45").
     * Einheiten wie €, %, m² und Leerzeichen werden ignoriert.
     *
     * @throws NumberFormatException wenn es keine Zahl ist
     */
    public static BigDecimal parse(String text) {
        String s = text.replace(' ', ' ').replaceAll("[\\s€%]|EUR|m²|m2", "");
        if (s.isEmpty() || !s.matches("[+-]?[0-9.,]+")) {
            throw new NumberFormatException(text);
        }
        if (s.contains(",")) {
            // deutsch: Punkte trennen Tausender, Komma = Dezimaltrenner
            if (s.indexOf(',') != s.lastIndexOf(',') || !s.substring(0, s.indexOf(',')).matches("[+-]?(\\d{1,3}(\\.\\d{3})*|\\d+)")) {
                throw new NumberFormatException(text);
            }
            s = s.replace(".", "").replace(',', '.');
        } else if (s.matches("[+-]?\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "");            // 150.000 = Tausenderpunkte
        } else if (s.indexOf('.') != s.lastIndexOf('.')) {
            throw new NumberFormatException(text);
        }
        if (s.endsWith(".") || s.startsWith(".")) {
            throw new NumberFormatException(text);
        }
        return new BigDecimal(s);
    }

    /** Deutsches Format mit Tausenderpunkten, z. B. 150.000,00. */
    public static String format(BigDecimal d, int decimals) {
        DecimalFormat f = new DecimalFormat(decimals == 0 ? "#,##0" : "#,##0." + "0".repeat(decimals),
                DecimalFormatSymbols.getInstance(Locale.GERMANY));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(d);
    }
}
