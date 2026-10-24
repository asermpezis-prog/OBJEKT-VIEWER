/*
 * Fenstertest des Ablaufs Anmeldung -> Vorgang suchen -> Vorgang lesen -> Baum per Klick aufbauen.
 *   ./build.sh
 *   cp -r beispiel/config /tmp/testcfg
 *   javac -cp dist/objektviewer.jar:out/beispiel -d out/test test/VorgangTest.java
 *   xvfb-run -a -s "-screen 0 1600x1000x24" java -cp "dist/objektviewer.jar:out/beispiel:out/test" VorgangTest /tmp/testcfg /tmp/shots
 * Achtung: löscht die gespeicherten Swing-Einstellungen.
 */

import de.objektbaum.ObjectNode;
import de.objektbaum.ObjectViewer;
import de.objektbaum.swing.SwingViewer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

public class VorgangTest {

    static Robot robot;
    static Path shots;
    static ObjectViewer viewer;
    static JFrame frame;
    static int failures;

    public static void main(String[] a) throws Exception {
        Path cfg = Path.of(a[0]);
        shots = Path.of(a[1]);
        Files.createDirectories(shots);
        Preferences.userRoot().node("de/objektbaum/viewer").removeNode();
        Files.writeString(cfg.resolve("swing.properties"), "farbschema = hell\n");
        robot = new Robot();
        robot.setAutoDelay(25);
        Class<?> app = Class.forName("de.objektbaum.beispiel.BeispielVorgang");
        Method m = app.getDeclaredMethod("viewer", Path.class);
        m.setAccessible(true);
        viewer = (ObjectViewer) m.invoke(null, cfg);
        SwingUtilities.invokeAndWait(() -> new SwingViewer(viewer).open());
        sleep(1500);
        frame = (JFrame) Arrays.stream(Window.getWindows()).filter(w -> w instanceof JFrame && w.isShowing()).findFirst().orElseThrow();
        try {
            run();
        } catch (Throwable t) {
            t.printStackTrace();
            failures++;
            try { shot("ZZ-abbruch"); } catch (Exception ignored) { }
        }
        System.out.println(failures == 0 ? "ALLE TESTS OK" : failures + " FEHLER");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void run() throws Exception {
        // --- Anmeldung
        shot("v01-anmeldung");
        check("Anmeldemaske sichtbar", field("Benutzer") != null && frame.getTitle().startsWith("Anmelden"));
        type(field("Benutzer"), "demo");
        type(field("Passwort"), "falsch");
        key(KeyEvent.VK_ENTER);
        sleep(900);
        shot("v02-anmeldung-falsch");
        check("Fehler bei falschem Passwort", text().contains("Benutzer oder Passwort falsch"));
        check("Passwortfeld geleert", ((JPasswordField) field("Passwort")).getPassword().length == 0);
        type(field("Passwort"), "demo");
        key(KeyEvent.VK_ENTER);
        sleep(1200);
        check("Angemeldet als demo", "demo".equals(viewer.user()) && text().contains("demo"));
        shot("v03-nach-anmeldung");

        // --- Suche
        click(button("Vorgang lesen"));
        sleep(500);
        check("Leere Suche -> Hinweis", text().contains("VorgangsId und/oder VorgangsNummer eingeben"));
        type(field("VorgangsNummer:"), "999");
        key(KeyEvent.VK_ENTER);
        sleep(1000);
        check("Unbekannte Nummer -> Nichts gefunden", text().contains("Nichts gefunden zu VorgangsNummer 999"));
        type(field("VorgangsNummer:"), "100000001");
        click(button("Vorgang lesen"));
        sleep(1200);
        shot("v04-vorgang-gelesen");
        check("Titel = Vorgang", frame.getTitle().startsWith("Vorgang Baufinanzierung"));
        JTable tt = treeTable();
        check("Nur die Wurzel in der Baumtabelle (" + tt.getRowCount() + " Zeile)", tt.getRowCount() == 1);

        // --- Klicks bauen den Baum auf
        clickRow("Vorgang Baufinanzierung, Max Mustermann (100000001)");
        sleep(900);
        check("Klick auf Vorgang lädt Kinder (" + treeTable().getRowCount() + " Zeilen)", treeTable().getRowCount() > 4);
        clickRow("Vorhaben_440717");
        sleep(900);
        check("Klick auf Vorhaben zeigt Varianten", rowOf("VHV_440718_Finanzierungsvariante") >= 0);
        clickRow("VHV_440718_Finanzierungsvariante");
        sleep(900);
        check("Klick auf Variante zeigt Angebote", rowOf("SPK Darlehen") >= 0);
        shot("v05-baum-aufgebaut");
        clickRow("SPK Darlehen");
        sleep(900);
        check("Angebot geöffnet", frame.getTitle().startsWith("SPK Darlehen"));
        check("Angebot-Ansicht mit Zahlenfeld", field("Darlehensbetrag") != null);
        shot("v06-angebot");

        // --- Buttons unter der Baumtabelle
        ObjectNode angebot = viewer.root().find(nodeId("SPK Darlehen")).orElseThrow();
        click(button("Angebot löschen"));
        sleep(900);
        check("Angebot gelöscht", angebot.parent() == null && rowOf("SPK Darlehen") < 0);
        check("Elternobjekt (Variante) angezeigt", frame.getTitle().startsWith("VHV_440718"));
        click(button("Angebot löschen"));
        sleep(800);
        check("Löschen auf Variante -> Fehlermeldung", text().contains("Bitte zuerst ein Angebot auswählen"));
        shot("v07-nach-loeschen");

        // --- Klick erneut: Listener baut Kinder frisch
        clickRow("VHV_440718_Finanzierungsvariante");
        sleep(900);
        check("Erneuter Klick lädt Angebot wieder (removeChildren + neu)", rowOf("SPK Darlehen") >= 0);

        // --- Aussehen je Objekt (dunkel)
        Files.writeString(Path.of(frameCfg()).resolve("swing.properties"), "farbschema = dunkel\n");
        sleep(1900);
        clickToggle("Baum");
        sleep(600);
        shot("v08-dunkel-navigation-stile");
        clickToggle("Baumtabelle");
        sleep(500);
        Files.writeString(Path.of(frameCfg()).resolve("swing.properties"), "farbschema = hell\n");
        sleep(1900);

        // --- Abmelden
        JButton out = button("Abmelden");
        click(out);
        sleep(800);
        shot("v09-abgemeldet");
        check("Wieder Anmeldemaske, Benutzer vorbelegt", "demo".equals(((JTextField) field("Benutzer")).getText()));
        check("Benutzer abgemeldet", viewer.user() == null);
        type(field("Passwort"), "demo");
        key(KeyEvent.VK_ENTER);
        sleep(1200);
        check("Nach erneuter Anmeldung kein Vorgang mehr geladen", frame.getTitle().startsWith("Kein Vorgang geladen"));
        check("Suchfeld leer", ((JTextField) field("VorgangsNummer:")).getText().isEmpty());
        shot("v10-neu-angemeldet");
    }

    static String cfgDir;

    static String frameCfg() {
        return viewer.configDir().toString();
    }

    // ------------------------------------------------------------------ Hilfen

    static JTable treeTable() {
        return (JTable) find(c -> c instanceof JTable t && t.isShowing() && t.getColumnCount() >= 5);
    }

    static int rowOf(String name) {
        JTable t = treeTable();
        for (int r = 0; r < t.getRowCount(); r++) {
            if (name.equals(t.getValueAt(r, 1))) return r;
        }
        return -1;
    }

    static int nodeId(String name) {
        return findNode(viewer.root(), name).id();
    }

    static ObjectNode findNode(ObjectNode n, String name) {
        if (n.name().equals(name)) return n;
        for (ObjectNode c : n.loadedChildren()) {
            ObjectNode r = findNode(c, name);
            if (r != null) return r;
        }
        return null;
    }

    static void clickRow(String name) throws Exception {
        int r = rowOf(name);
        if (r < 0) throw new IllegalStateException("Zeile fehlt: " + name);
        JTable t = treeTable();
        Rectangle cell = t.getCellRect(r, 2, true);   // Spalte ObjektId: öffnet, ohne +/- zu treffen
        t.scrollRectToVisible(cell);
        sleep(200);
        Point p = t.getLocationOnScreen();
        robot.mouseMove(p.x + cell.x + cell.width / 2, p.y + cell.y + cell.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    static JTextComponent field(String label) {
        JLabel l = (JLabel) find(c -> c instanceof JLabel lb && lb.isShowing() && label.equals(lb.getText()) && lb.getLabelFor() != null);
        return l == null ? null : (JTextComponent) l.getLabelFor();
    }

    static JButton button(String text) {
        JButton b = (JButton) find(c -> c instanceof JButton jb && jb.isShowing() && text.equals(jb.getText()));
        if (b == null) throw new IllegalStateException("Button fehlt: " + text);
        return b;
    }

    static void clickToggle(String text) throws Exception {
        click(find(c -> c instanceof JToggleButton b && b.isShowing() && text.equals(b.getText())));
    }

    static String text() {
        StringBuilder sb = new StringBuilder();
        collect(frame.getContentPane(), sb);
        return sb.toString();
    }

    static void collect(Component c, StringBuilder sb) {
        if (c instanceof JLabel l && l.isShowing()) sb.append(l.getText()).append('\n');
        if (c instanceof Container ct) for (Component k : ct.getComponents()) collect(k, sb);
    }

    static Component find(Predicate<Component> p) {
        return find(frame, p);
    }

    static Component find(Container root, Predicate<Component> p) {
        if (p.test(root)) return root;
        for (Component c : root.getComponents()) {
            if (p.test(c)) return c;
            if (c instanceof Container ct) {
                Component r = find(ct, p);
                if (r != null) return r;
            }
        }
        return null;
    }

    static void click(Component c) throws Exception {
        sleep(100);
        Point p = c.getLocationOnScreen();
        robot.mouseMove(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleep(150);
    }

    static void key(int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
    }

    static void type(JTextComponent t, String text) throws Exception {
        click(t);
        robot.keyPress(KeyEvent.VK_CONTROL);
        key(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        key(KeyEvent.VK_DELETE);
        for (char ch : text.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(ch);
            key(code);
        }
        sleep(100);
    }

    static void shot(String name) throws Exception {
        sleep(250);
        ImageIO.write(robot.createScreenCapture(frame.getBounds()), "png", shots.resolve(name + ".png").toFile());
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) failures++;
    }

    static void sleep(long ms) throws Exception {
        robot.waitForIdle();
        Thread.sleep(ms);
        robot.waitForIdle();
    }
}
