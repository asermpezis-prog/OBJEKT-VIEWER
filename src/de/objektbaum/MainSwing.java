package de.objektbaum;

import de.objektbaum.swing.SwingViewer;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * Startet die Swing-Version mit denselben Beispieldaten, Ansichten und Baumtabellen-Spalten
 * wie die Browser-Version ({@link Main}). Kein Webserver nötig.
 */
public final class MainSwing {

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Kein Bildschirm verfügbar: Die Swing-Version braucht eine grafische Oberfläche.");
            System.err.println("Auf einem Server ohne Bildschirm stattdessen die Browser-Version starten: ./run.sh");
            System.exit(1);
        }
        ObjectNode root = Main.beispielDaten();
        SwingUtilities.invokeLater(() ->
                new SwingViewer(root, "Objekt-Viewer", Main.ansichten(), Main.baumtabellenSpalten()).open());
    }
}
