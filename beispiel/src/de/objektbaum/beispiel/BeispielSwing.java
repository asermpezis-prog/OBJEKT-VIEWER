package de.objektbaum.beispiel;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * Startet die Beispielanwendung als Swing-Fenster (kein Webserver). Gleiche Daten, Aktionen
 * und Konfiguration wie {@link BeispielApp}.
 *
 * <pre>
 * ./run-swing.sh                  Konfiguration aus beispiel/config
 * ./run-swing.sh meinOrdner       anderer Konfigurationsordner
 * </pre>
 */
public final class BeispielSwing {

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Kein Bildschirm verfügbar: Die Swing-Version braucht eine grafische Oberfläche.");
            System.err.println("Auf einem Server ohne Bildschirm stattdessen die Browser-Version starten: ./run.sh");
            System.exit(1);
        }
        Path config = Path.of(args.length > 0 ? args[0] : "beispiel/config");
        BeispielApp.viewer(config).openSwing();
    }
}
