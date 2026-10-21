package de.objektbaum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Hält die aktuelle viewer.xml. Wird die Datei geändert, liest der nächste Zugriff sie neu ein –
 * ohne Neustart. Ist die neue Fassung fehlerhaft, bleibt die letzte gültige aktiv und
 * {@link #error()} liefert die Meldung mit Zeilennummer.
 */
public final class ConfigStore {

    /** Höchstens so oft wird auf Änderungen geprüft (Millisekunden). */
    private static final long CHECK_INTERVAL = 250;

    private final Path file;
    private final XmlConfigReader reader;

    private volatile ViewerConfig current = ViewerConfig.EMPTY;
    private volatile String error;
    private FileTime lastModified;
    private long lastSize = -1;
    private long lastCheck;

    public ConfigStore(Path file, Bindings bindings) {
        this.file = file;
        this.reader = new XmlConfigReader(bindings);
        reload(true);
    }

    /** Die aktuelle (letzte gültige) Konfiguration; prüft vorher, ob die Datei geändert wurde. */
    public ViewerConfig current() {
        reload(false);
        return current;
    }

    /** Fehlermeldung zur aktuellen Datei, oder null wenn sie in Ordnung ist. */
    public String error() {
        reload(false);
        return error;
    }

    public Path file() {
        return file;
    }

    private synchronized void reload(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastCheck < CHECK_INTERVAL) {
            return;
        }
        lastCheck = now;
        FileTime modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(file);
            size = Files.size(file);
        } catch (NoSuchFileException e) {
            if (lastModified != null || force) {
                error = force ? null : "Datei " + file + " fehlt – es gilt weiter die zuletzt gelesene Fassung";
                lastModified = null;
            }
            return;
        } catch (IOException e) {
            error = file + " kann nicht gelesen werden: " + e.getMessage();
            return;
        }
        if (!force && modified.equals(lastModified) && size == lastSize) {
            return;
        }
        lastModified = modified;
        lastSize = size;
        try (InputStream in = Files.newInputStream(file)) {
            ViewerConfig cfg = reader.read(in);
            for (ViewerConfig.TypeDef t : cfg.types()) {
                ObjectType.define(t.id(), t.label(), t.icon());
            }
            current = cfg;
            error = null;
            if (!force) {
                System.out.println("  Konfiguration neu geladen: " + file);
            }
        } catch (ConfigException e) {
            error = "Fehler in " + file.getFileName() + ", " + e.describe()
                    + (current == ViewerConfig.EMPTY ? "" : " – es gilt weiter die letzte gültige Fassung");
            System.out.println("  " + error);
        } catch (IOException e) {
            error = file + " kann nicht gelesen werden: " + e.getMessage();
        }
    }
}
