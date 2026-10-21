package de.objektbaum;

/** Fehler in der Konfiguration, mit Zeilennummer (0 = unbekannt). */
public class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int line;

    public ConfigException(int line, String message) {
        super(message);
        this.line = line;
    }

    public ConfigException(int line, String message, Throwable cause) {
        super(message, cause);
        this.line = line;
    }

    public int line() {
        return line;
    }

    /** "Zeile 12: …" bzw. nur die Meldung. */
    public String describe() {
        return line > 0 ? "Zeile " + line + ": " + getMessage() : getMessage();
    }
}
