package de.objektbaum.swing;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Aussehen der Swing-Version aus {@code swing.properties} im Konfigurationsordner:
 * Look and Feel, Farbschema (hell/dunkel/System), Schrift, Schriftgröße und einzelne Farben.
 * Wird die Datei geändert, übernimmt das offene Fenster die Änderung von selbst. Ist sie fehlerhaft,
 * bleibt die letzte gültige Fassung aktiv und {@link #error()} nennt Zeile und Problem.
 *
 * <pre>
 * aussehen       = metal            # metal | nimbus | system
 * farbschema     = system           # hell | dunkel | system
 * schrift        = Segoe UI, Inter, Noto Sans, DejaVu Sans
 * schriftgroesse = 14
 * farbe.akzent        = #2563eb     # gilt für hell und dunkel
 * farbe.akzent.dunkel = #60a5fa     # nur im dunklen Schema
 * </pre>
 */
final class SwingStyle {

    enum Laf { METAL, NIMBUS, SYSTEM }

    enum Scheme { LIGHT, DARK, SYSTEM }

    /**
     * Gelesene Einstellungen.
     *
     * @param light Farben, die im hellen Schema überschrieben werden (Name → Farbe)
     * @param dark  Farben, die im dunklen Schema überschrieben werden
     */
    record Settings(Laf laf, Scheme scheme, List<String> fonts, int fontSize,
                    Map<String, Color> light, Map<String, Color> dark) {
        static final Settings DEFAULT = new Settings(Laf.METAL, Scheme.SYSTEM, List.of(), 14, Map.of(), Map.of());
    }

    /** Erlaubte Farbnamen (farbe.&lt;name&gt;) mit Beschreibung – auch für die Fehlermeldung. */
    static final Map<String, String> COLOR_NAMES = new LinkedHashMap<>();

    static {
        COLOR_NAMES.put("hintergrund", "Fläche hinter allem");
        COLOR_NAMES.put("flaeche", "Segmente, Tabellen, Eingaben");
        COLOR_NAMES.put("navigation", "Navigation links");
        COLOR_NAMES.put("navigation.text", "Text in der Navigation");
        COLOR_NAMES.put("navigation.linie", "Linie unter dem Titel der Navigation");
        COLOR_NAMES.put("akzent", "Buttons, Links, Auswahl");
        COLOR_NAMES.put("text", "normaler Text");
        COLOR_NAMES.put("text.grau", "Beschriftungen, Hinweise");
        COLOR_NAMES.put("linie", "Rahmen und Trennlinien");
        COLOR_NAMES.put("zebra", "jede zweite Tabellenzeile, Köpfe");
        COLOR_NAMES.put("auswahl", "ausgewählte Zeile der Baumtabelle");
        COLOR_NAMES.put("ok.hintergrund", "Erfolgsmeldung");
        COLOR_NAMES.put("ok.linie", "Erfolgsmeldung, Rahmen");
        COLOR_NAMES.put("ok.text", "Erfolgsmeldung, Text");
        COLOR_NAMES.put("fehler.hintergrund", "Fehlermeldung");
        COLOR_NAMES.put("fehler.linie", "Fehlermeldung, Rahmen");
        COLOR_NAMES.put("fehler.text", "Fehlermeldung, Text");
    }

    private final Path file;
    private volatile Settings current = Settings.DEFAULT;
    private volatile String error;
    private FileTime lastModified;
    private long lastSize = -1;

    /** @param file swing.properties, darf fehlen (dann gelten die Standardwerte) */
    SwingStyle(Path file) {
        this.file = file;
        reload();
    }

    Settings current() {
        return current;
    }

    String error() {
        return error;
    }

    /**
     * Prüft, ob die Datei geändert wurde, und liest sie ggf. neu.
     *
     * @return true, wenn sich Einstellungen oder Fehlermeldung geändert haben
     */
    synchronized boolean reload() {
        if (file == null) return false;
        Settings before = current;
        String errBefore = error;
        FileTime modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(file);
            size = Files.size(file);
        } catch (NoSuchFileException e) {
            if (lastModified != null) {
                // Datei gelöscht: Standard
                lastModified = null;
                current = Settings.DEFAULT;
                error = null;
            }
            return !before.equals(current) || !java.util.Objects.equals(errBefore, error);
        } catch (IOException e) {
            error = file + " kann nicht gelesen werden: " + e.getMessage();
            return !java.util.Objects.equals(errBefore, error);
        }
        if (modified.equals(lastModified) && size == lastSize) {
            return false;
        }
        boolean initial = lastModified == null && errBefore == null;
        lastModified = modified;
        lastSize = size;
        try {
            current = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
            error = null;
            if (!initial) {
                System.out.println("  Aussehen neu geladen: " + file);
            }
        } catch (StyleException e) {
            error = "Fehler in " + file.getFileName() + ", Zeile " + e.line + ": " + e.getMessage()
                    + " – es gilt weiter die letzte gültige Fassung";
            System.out.println("  " + error);
        } catch (IOException e) {
            error = file + " kann nicht gelesen werden: " + e.getMessage();
        }
        return !before.equals(current) || !java.util.Objects.equals(errBefore, error);
    }

    // ------------------------------------------------------------------ Lesen

    static final class StyleException extends Exception {
        final int line;

        StyleException(int line, String message) {
            super(message);
            this.line = line;
        }
    }

    static Settings parse(List<String> lines) throws StyleException {
        Laf laf = Laf.METAL;
        Scheme scheme = Scheme.SYSTEM;
        List<String> fonts = List.of();
        int fontSize = 14;
        Map<String, Color> light = new LinkedHashMap<>();
        Map<String, Color> dark = new LinkedHashMap<>();
        Map<String, Color> both = new LinkedHashMap<>();
        Map<String, Color> lightOnly = new LinkedHashMap<>();
        Map<String, Color> darkOnly = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            int no = i + 1;
            String line = stripComment(lines.get(i)).strip();
            if (i == 0 && line.startsWith("﻿")) line = line.substring(1);
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq < 0) eq = line.indexOf(':');
            if (eq <= 0) {
                throw new StyleException(no, "„" + line + "“ – erwartet wird name = wert");
            }
            String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).strip();
            Integer prev = seen.put(key, no);
            if (prev != null) {
                throw new StyleException(no, key + " steht schon in Zeile " + prev);
            }
            switch (key) {
                case "aussehen" -> laf = switch (value.toLowerCase(Locale.ROOT)) {
                    case "metal" -> Laf.METAL;
                    case "nimbus" -> Laf.NIMBUS;
                    case "system" -> Laf.SYSTEM;
                    default -> throw new StyleException(no, "aussehen = " + value + " ist unbekannt. Erlaubt: metal, nimbus, system");
                };
                case "farbschema" -> scheme = switch (value.toLowerCase(Locale.ROOT)) {
                    case "hell", "light" -> Scheme.LIGHT;
                    case "dunkel", "dark" -> Scheme.DARK;
                    case "system" -> Scheme.SYSTEM;
                    default -> throw new StyleException(no, "farbschema = " + value + " ist unbekannt. Erlaubt: hell, dunkel, system");
                };
                case "schrift" -> {
                    List<String> f = new ArrayList<>();
                    for (String p : value.split(",")) if (!p.isBlank()) f.add(p.strip());
                    fonts = List.copyOf(f);
                }
                case "schriftgroesse", "schriftgröße" -> {
                    try {
                        fontSize = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        fontSize = -1;
                    }
                    if (fontSize < 10 || fontSize > 24) {
                        throw new StyleException(no, "schriftgroesse = " + value + ": ganze Zahl von 10 bis 24 erwartet (Standard 14)");
                    }
                }
                default -> {
                    if (!key.startsWith("farbe.")) {
                        throw new StyleException(no, "Unbekannter Eintrag „" + key + "“. Erlaubt: aussehen, farbschema, "
                                + "schrift, schriftgroesse, farbe.<name>, farbe.<name>.dunkel");
                    }
                    String name = key.substring("farbe.".length());
                    boolean onlyDark = name.endsWith(".dunkel");
                    boolean onlyLight = name.endsWith(".hell");
                    if (onlyDark) name = name.substring(0, name.length() - ".dunkel".length());
                    if (onlyLight) name = name.substring(0, name.length() - ".hell".length());
                    if (!COLOR_NAMES.containsKey(name)) {
                        throw new StyleException(no, "Unbekannte Farbe „" + name + "“. Erlaubt: "
                                + String.join(", ", COLOR_NAMES.keySet()));
                    }
                    Color c = color(value);
                    if (c == null) {
                        throw new StyleException(no, key + " = " + value + ": Farbe als #rrggbb erwartet, z. B. #2563eb");
                    }
                    (onlyDark ? darkOnly : onlyLight ? lightOnly : both).put(name, c);
                }
            }
        }
        // "farbe.x.dunkel" bzw. "farbe.x.hell" hat Vorrang vor "farbe.x" – unabhängig von der Reihenfolge
        light.putAll(both);
        light.putAll(lightOnly);
        dark.putAll(both);
        dark.putAll(darkOnly);
        return new Settings(laf, scheme, fonts, fontSize, Map.copyOf(light), Map.copyOf(dark));
    }

    /** "#" leitet einen Kommentar ein – außer direkt vor einer Farbe (#2563eb). */
    private static String stripComment(String line) {
        String s = line.strip();
        if (s.startsWith("#") || s.startsWith("!")) return "";
        int i = 0;
        while ((i = line.indexOf('#', i)) >= 0) {
            if (i > 0 && Character.isWhitespace(line.charAt(i - 1)) && !line.substring(i).matches("#[0-9a-fA-F]{6}\\b.*")) {
                return line.substring(0, i);
            }
            i++;
        }
        return line;
    }

    private static Color color(String v) {
        String s = v.strip();
        if (!s.matches("#[0-9a-fA-F]{6}")) return null;
        return new Color(Integer.parseInt(s.substring(1), 16));
    }

    // ------------------------------------------------------------ Systemeinstellung

    /**
     * Ob das Betriebssystem ein dunkles Farbschema eingestellt hat (Windows, macOS, GNOME/KDE).
     * Ohne Bibliotheken: fragt die Systemeinstellung per Programmaufruf ab; unbekannt = hell.
     */
    static boolean systemPrefersDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String out = run("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
                return out.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x0+\\b.*");
            }
            if (os.contains("mac")) {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase(Locale.ROOT).contains("dark");
            }
            String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (scheme.contains("dark")) return true;
            String gtk = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
            return gtk.toLowerCase(Locale.ROOT).contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
