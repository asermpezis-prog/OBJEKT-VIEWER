package de.objektbaum;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vorlagen der Browser-Version: page.html, style.css, app.js und eigene Dateien (z. B. Logo).
 *
 * <p>Gesucht wird zuerst im Vorlagen-Ordner (z. B. config/templates), danach die Standardfassung
 * im JAR. Geänderte Dateien werden beim nächsten Zugriff neu gelesen – ohne Neustart.
 */
public final class Templates {

    /** Die Standard-Vorlagen im JAR. */
    public static final List<String> DEFAULTS = List.of("page.html", "style.css", "app.js");

    private static final String RESOURCE_DIR = "/de/objektbaum/defaults/";

    private final Path dir;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(long modified, long size, byte[] data) {
    }

    /** @param dir Ordner mit eigenen Vorlagen, oder null = nur die Standard-Vorlagen */
    public Templates(Path dir) {
        this.dir = dir == null ? null : dir.toAbsolutePath().normalize();
    }

    public Path dir() {
        return dir;
    }

    /** Text einer Vorlage (UTF-8). */
    public String text(String name) {
        return new String(bytes(name).orElseThrow(() ->
                new IllegalStateException("Vorlage fehlt: " + name)), StandardCharsets.UTF_8);
    }

    /**
     * Inhalt einer Datei. Nur einfache relative Namen innerhalb des Vorlagen-Ordners sind erlaubt
     * (kein "..", keine absoluten Pfade).
     */
    public Optional<byte[]> bytes(String name) {
        if (!isSafe(name)) {
            return Optional.empty();
        }
        if (dir != null) {
            Path p = dir.resolve(name).normalize();
            if (p.startsWith(dir) && Files.isRegularFile(p)) {
                try {
                    long mod = Files.getLastModifiedTime(p).toMillis();
                    long size = Files.size(p);
                    Cached c = cache.get(name);
                    if (c == null || c.modified != mod || c.size != size) {
                        c = new Cached(mod, size, Files.readAllBytes(p));
                        cache.put(name, c);
                    }
                    return Optional.of(c.data);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return defaultBytes(name);
    }

    /** true, wenn die Datei im Vorlagen-Ordner liegt (also angepasst wurde). */
    public boolean isCustom(String name) {
        return dir != null && isSafe(name) && Files.isRegularFile(dir.resolve(name).normalize());
    }

    /**
     * Kopiert die Standard-Vorlagen in einen Ordner, damit man sie bearbeiten kann.
     * Vorhandene Dateien werden nur mit overwrite=true ersetzt.
     *
     * @return die tatsächlich geschriebenen Dateien
     */
    public static List<Path> exportDefaults(Path target, boolean overwrite) throws IOException {
        Files.createDirectories(target);
        List<Path> written = new java.util.ArrayList<>();
        for (String name : DEFAULTS) {
            Path p = target.resolve(name);
            if (Files.exists(p) && !overwrite) continue;
            try (InputStream in = Templates.class.getResourceAsStream(RESOURCE_DIR + name)) {
                if (in == null) throw new IOException("Standard-Vorlage fehlt im JAR: " + name);
                Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
                written.add(p);
            }
        }
        return written;
    }

    /** Eine Datei aus den Standard-Vorlagen des JAR (z. B. für den init-Befehl). */
    public static Optional<byte[]> defaultBytes(String name) {
        if (!isSafe(name)) return Optional.empty();
        try (InputStream in = Templates.class.getResourceAsStream(RESOURCE_DIR + name)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isSafe(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.startsWith("\\")) return false;
        for (String part : name.split("[/\\\\]")) {
            if (part.isEmpty() || part.equals("..") || part.equals(".") || part.contains(":")) return false;
        }
        return true;
    }

    /** Ersetzt {{name}} durch die Werte. Unbekannte Platzhalter bleiben stehen. */
    public static String fill(String template, Map<String, String> values) {
        StringBuilder out = new StringBuilder(template.length() + 8192);
        int i = 0;
        while (true) {
            int start = template.indexOf("{{", i);
            if (start < 0) break;
            int end = template.indexOf("}}", start + 2);
            if (end < 0) break;
            String key = template.substring(start + 2, end).trim();
            String v = values.get(key);
            out.append(template, i, start);
            out.append(v != null ? v : template.substring(start, end + 2));
            i = end + 2;
        }
        out.append(template, i, template.length());
        return out.toString();
    }
}
