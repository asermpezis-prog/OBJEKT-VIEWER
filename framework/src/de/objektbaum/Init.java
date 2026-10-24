package de.objektbaum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Hilfsprogramm im JAR: legt einen Konfigurationsordner mit den Standard-Dateien an.
 *
 * <pre>
 * java -jar objektviewer.jar init config            viewer.xml, swing.properties + templates/ anlegen (vorhandene bleiben)
 * java -jar objektviewer.jar init config --force    vorhandene Dateien überschreiben
 * java -jar objektviewer.jar check config           viewer.xml und swing.properties prüfen (ohne Aktionen aus dem Code)
 * </pre>
 */
public final class Init {

    private Init() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length >= 2 && args[0].equals("init")) {
            init(Path.of(args[1]), List.of(args).contains("--force"));
        } else if (args.length >= 2 && args[0].equals("check")) {
            System.exit(check(Path.of(args[1])));
        } else {
            System.out.println("""
                    Objekt-Viewer Framework

                      java -jar objektviewer.jar init <ordner> [--force]
                          legt <ordner>/viewer.xml, <ordner>/swing.properties und <ordner>/templates/
                          (page.html, style.css, app.js) an

                      java -jar objektviewer.jar check <ordner>
                          prüft <ordner>/viewer.xml (Aktionen und Optionsquellen aus dem Code werden dabei
                          nicht geprüft, weil sie erst die Anwendung anmeldet) und <ordner>/swing.properties

                    In der eigenen Anwendung:
                      ObjectViewer.builder().root(...).configDir(Path.of("<ordner>")).build().startWeb("localhost", 8080);
                    """);
        }
    }

    private static void init(Path dir, boolean force) throws IOException {
        Files.createDirectories(dir);
        Path xml = dir.resolve("viewer.xml");
        if (force || !Files.exists(xml)) {
            Files.write(xml, Templates.defaultBytes("viewer.xml").orElseThrow());
            System.out.println("angelegt: " + xml);
        } else {
            System.out.println("vorhanden, nicht überschrieben: " + xml);
        }
        Path props = dir.resolve("swing.properties");
        if (force || !Files.exists(props)) {
            Files.write(props, Templates.defaultBytes("swing.properties").orElseThrow());
            System.out.println("angelegt: " + props);
        } else {
            System.out.println("vorhanden, nicht überschrieben: " + props);
        }
        Path tdir = dir.resolve("templates");
        List<Path> written = Templates.exportDefaults(tdir, force);
        for (String name : Templates.DEFAULTS) {
            Path p = tdir.resolve(name);
            System.out.println((written.contains(p) ? "angelegt: " : "vorhanden, nicht überschrieben: ") + p);
        }
        System.out.println("\nDiese Dateien können jetzt bearbeitet werden; Änderungen wirken sofort.");
    }

    private static int check(Path dir) {
        int result = checkXml(dir);
        Path props = dir.resolve("swing.properties");
        if (Files.exists(props)) {
            String error = de.objektbaum.swing.SwingViewer.checkStyle(props);
            System.out.println(error == null ? "OK: " + props : error);
            if (error != null && result == 0) result = 1;
        }
        return result;
    }

    private static int checkXml(Path dir) {
        Path xml = dir.resolve("viewer.xml");
        // Aktionen/Optionsquellen kennt nur die Anwendung: hier jeden Namen akzeptieren
        Bindings any = new Bindings() {
            @Override
            public Button.Action action(String name) {
                return (n, v) -> null;
            }

            @Override
            public OptionSource options(String name) {
                return n -> List.of();
            }
        };
        try (var in = Files.newInputStream(xml)) {
            ViewerConfig c = new XmlConfigReader(any).read(in);
            System.out.println("OK: " + xml + " – " + c.types().size() + " Typen, " + c.views().size() + " Ansichten");
            return 0;
        } catch (ConfigException e) {
            System.out.println("Fehler in " + xml + ", " + e.describe());
            return 1;
        } catch (IOException e) {
            System.out.println(xml + " kann nicht gelesen werden: " + e.getMessage());
            return 2;
        }
    }
}
