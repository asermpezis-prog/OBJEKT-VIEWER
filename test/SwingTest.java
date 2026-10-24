/*
 * Fenstertest der Swing-Variante (braucht einen Bildschirm, z. B. xvfb-run unter Linux).
 *   ./build.sh
 *   cp -r beispiel/config /tmp/testcfg
 *   javac -cp dist/objektviewer.jar -d out/test test/SwingTest.java
 *   xvfb-run -a -s "-screen 0 1600x1000x24" java -cp "dist/objektviewer.jar:out/beispiel:out/test" SwingTest /tmp/testcfg /tmp/shots
 * Achtung: verändert die Dateien im angegebenen Konfigurationsordner und löscht die gespeicherten Swing-Einstellungen.
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
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

/** Fenstertest der Swing-Variante: echte Maus/Tastatur per Robot, Screenshots, Prüfung des Datenmodells. */
public class SwingTest {

    static Robot robot;
    static Path cfg, shots;
    static ObjectViewer viewer;
    static SwingViewer sv;
    static JFrame frame;
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        cfg = Path.of(a[0]);
        shots = Path.of(a[1]);
        Files.createDirectories(shots);
        Preferences.userRoot().node("de/objektbaum/viewer").removeNode();
        robot = new Robot();
        robot.setAutoDelay(25);

        // Beispielanwendung über ihre (paketprivate) Methode viewer(configDir)
        Class<?> app = Class.forName("de.objektbaum.beispiel.BeispielApp");
        Method m = app.getDeclaredMethod("viewer", Path.class);
        m.setAccessible(true);
        viewer = (ObjectViewer) m.invoke(null, cfg);
        SwingUtilities.invokeAndWait(() -> {
            sv = new SwingViewer(viewer);
            sv.open();
        });
        sleep(1500);
        frame = (JFrame) Arrays.stream(Window.getWindows()).filter(w -> w instanceof JFrame && w.isShowing()).findFirst().orElseThrow();
        String only = a.length > 2 ? a[2] : "all";
        try {
            run(only);
        } catch (Throwable t) {
            t.printStackTrace();
            failures++;
            try { shot("ZZ-abbruch"); } catch (Exception ignored) { }
        }
        System.out.println(failures == 0 ? "ALLE TESTS OK" : failures + " FEHLER");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void run(String only) throws Exception {
        t01_hellStart();
        t02_zahlUngueltig();
        t03_zahlGueltigRate();
        t04_datumCheckbox();
        t05_dunkel();
        t06_nimbus();
        t07_fehlerInProperties();
        t08_schriftgroesse();
        t09_spaltenLive();
        t10_frameGroessenNachTitel();
        t11_eingabeBleibtBeiLiveAenderung();
    }

    // ------------------------------------------------------------------ Tests

    static void t01_hellStart() throws Exception {
        props("farbschema = hell\n");
        sleep(1600);
        shot("01-hell-vorgang");
        check("Titel enthält Vorgang", frame.getTitle().contains("Vorgang"));
    }

    static void t02_zahlUngueltig() throws Exception {
        ObjectNode angebot = find("SPK Darlehen");
        open(angebot);
        JTextField betrag = field("Darlehensbetrag");
        typeInto(betrag, "abc");
        typeInto(field("Sollzins"), "3,45");
        click(button("Rate berechnen"));
        sleep(800);
        shot("02-zahl-ungueltig");
        check("Darlehensbetrag unverändert im Objekt", "150.000,00".equals(angebot.fields().get("Darlehensbetrag")));
        check("Fehlermeldung im Header", headerText().contains("Nicht gespeichert"));
        check("Eingabe 'abc' bleibt stehen", "abc".equals(field("Darlehensbetrag").getText()));
        check("Fehlertext am Feld sichtbar", labelVisible("„abc“ ist keine Zahl"));
    }

    static void t03_zahlGueltigRate() throws Exception {
        ObjectNode angebot = find("SPK Darlehen");
        typeInto(field("Darlehensbetrag"), "150000");
        click(field("Sollzins"));          // Fokus wechseln -> formatieren
        sleep(300);
        check("150000 -> 150.000,00 beim Verlassen", "150.000,00".equals(field("Darlehensbetrag").getText()));
        typeInto(field("Anf. Tilgung"), "2");
        click(button("Rate berechnen"));
        sleep(800);
        shot("03-rate-berechnet");
        check("Monatsrate 681,25 €", String.valueOf(angebot.fields().get("Monatsrate")).startsWith("681,25"));
        check("Tilgung gespeichert als 2,00", "2,00".equals(angebot.fields().get("Tilgung")));
        typeInto(field("Sollzins"), "25");
        click(field("Anf. Tilgung"));
        sleep(300);
        check("Sollzins 25 sofort rot (max 20)", labelVisible("höchstens 20,00 %"));
        typeInto(field("Sollzins"), "3,45");
        click(field("Anf. Tilgung"));
        sleep(300);
        check("Fehler nach Korrektur weg", !labelVisible("höchstens 20,00 %"));
    }

    static void t04_datumCheckbox() throws Exception {
        ObjectNode vorgang = viewer.root();
        open(vorgang);
        JTextField d = field("Wiedervorlage");
        typeInto(d, "31.2.2026");
        click(button("Speichern"));
        sleep(700);
        check("31.2.2026 abgelehnt", headerText().contains("kein gültiges Datum"));
        typeInto(field("Wiedervorlage"), "");
        // Kalender öffnen und den 15. anklicken
        click(button(b -> "Kalender für Wiedervorlage".equals(b.getAccessibleContext().getAccessibleName())));
        sleep(500);
        shot("04a-kalender");
        JButton day = (JButton) findIn(popupRoot(), c -> c instanceof JButton b && "15".equals(b.getText()));
        click(day);
        sleep(300);
        String expected = String.format("15.%02d.%d", java.time.LocalDate.now().getMonthValue(), java.time.LocalDate.now().getYear());
        check("Kalender setzt " + expected, expected.equals(field("Wiedervorlage").getText()));
        JCheckBox cb = checkbox("Unterlagen vollständig");
        click(cb);
        typeInto(field("Eröffnet"), "2.9.26");
        click(button("Speichern"));
        sleep(800);
        shot("04b-datum-checkbox-gespeichert");
        check("Wiedervorlage gespeichert", expected.equals(vorgang.fields().get("Wiedervorlage")));
        check("Eröffnet normalisiert 02.09.2026", "02.09.2026".equals(vorgang.fields().get("Eröffnet")));
        check("Checkbox gespeichert = ja", "ja".equals(vorgang.fields().get("Unterlagen vollständig")));
    }

    static void t05_dunkel() throws Exception {
        props("farbschema = dunkel\n");
        sleep(1800);
        shot("05a-dunkel-vorgang");
        open(find("SPK Darlehen"));
        sleep(400);
        shot("05b-dunkel-angebot");
        clickToggle("Baumtabelle");
        sleep(600);
        shot("05c-dunkel-baumtabelle");
        Color bg = robot.getPixelColor(frame.getX() + frame.getWidth() - 30, frame.getY() + 95);
        check("Hintergrund ist dunkel (" + bg + ")", bg.getRed() + bg.getGreen() + bg.getBlue() < 150);
        clickToggle("Baum");
        sleep(500);
    }

    static void t06_nimbus() throws Exception {
        props("aussehen = nimbus\nfarbschema = hell\n");
        sleep(1800);
        open(find("VHV_440718_Finanzierungsvariante"));
        sleep(500);
        shot("06a-nimbus-hell-variante");
        props("aussehen = nimbus\nfarbschema = dunkel\n");
        sleep(1800);
        shot("06b-nimbus-dunkel-variante");
        props("aussehen = metal\nfarbschema = hell\nfarbe.akzent = #c2410c\nfarbe.navigation = #14213d\n");
        sleep(1800);
        shot("06c-metal-eigene-farben");
        check("Ansicht Variante noch offen", frame.getTitle().startsWith("VHV_440718"));
    }

    static void t07_fehlerInProperties() throws Exception {
        props("aussehen = metal\nfarbschema = hell\nfarbe.akzentt = #123456\n");
        sleep(1800);
        shot("07a-fehler-properties");
        check("Fehlerband nennt Zeile 3", headerText().contains("Zeile 3") && headerText().contains("akzentt"));
        props("aussehen = metal\nfarbschema = hell\n");
        sleep(1800);
        check("Fehlerband weg nach Reparatur", !headerText().contains("swing.properties"));
    }

    static void t08_schriftgroesse() throws Exception {
        props("farbschema = hell\nschriftgroesse = 17\n");
        sleep(1800);
        open(find("SPK Darlehen"));
        sleep(400);
        shot("08-schrift-17");
        check("Schrift skaliert", field("Darlehensbetrag").getFont().getSize() == Math.round(14 * 17 / 14f));
        props("farbschema = hell\n");
        sleep(1800);
    }

    static void t09_spaltenLive() throws Exception {
        clickToggle("Baumtabelle");
        sleep(600);
        // Filter in Spalte Objektname setzen
        JTextField filter = (JTextField) findIn(frame, c -> c instanceof JTextField t && "Filter Objektname".equals(t.getAccessibleContext().getAccessibleName()));
        typeInto(filter, "Variante");
        sleep(300);
        String xml = Files.readString(cfg.resolve("viewer.xml"));
        String changed = xml.replace("<column kind=\"field\" key=\"Beziehungsart\"/>", "<column kind=\"field\" key=\"Status\"/>");
        check("viewer.xml enthält Beziehungsart-Spalte", !changed.equals(xml));
        write(cfg.resolve("viewer.xml"), changed);
        sleep(2000);
        shot("09-spalten-live");
        JTable tt = (JTable) findIn(frame, c -> c instanceof JTable t && t.getColumnCount() > 4);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < tt.getColumnCount(); i++) headers.add(String.valueOf(tt.getColumnModel().getColumn(i).getHeaderValue()));
        check("Spalte Status statt Beziehungsart " + headers, headers.contains("Status") && !headers.contains("Beziehungsart"));
        JTextField f2 = (JTextField) findIn(frame, c -> c instanceof JTextField t && "Filter Objektname".equals(t.getAccessibleContext().getAccessibleName()));
        check("Filter blieb erhalten", "Variante".equals(f2.getText()));
        write(cfg.resolve("viewer.xml"), xml);
        sleep(1600);
        typeInto(f2Fresh(), "*");
        clickToggle("Baum");
        sleep(500);
    }

    static JTextField f2Fresh() {
        return (JTextField) findIn(frame, c -> c instanceof JTextField t && "Filter Objektname".equals(t.getAccessibleContext().getAccessibleName()));
    }

    static void t10_frameGroessenNachTitel() throws Exception {
        open(find("VHV_440718_Finanzierungsvariante"));
        sleep(500);
        // senkrechte Trennlinie zwischen den Spalten nach links ziehen
        JSplitPane outer = (JSplitPane) findIn(frame, c -> c instanceof JSplitPane sp && sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                && sp.getParent() != frame.getContentPane());
        Component div = ((javax.swing.plaf.basic.BasicSplitPaneUI) outer.getUI()).getDivider();
        Point p = div.getLocationOnScreen();
        int x = p.x + 2, y = p.y + div.getHeight() / 2;
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 10; i++) robot.mouseMove(x - i * 15, y);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleep(400);
        Preferences prefs = Preferences.userRoot().node("de/objektbaum/viewer");
        List<String> keys = Arrays.stream(prefs.keys()).filter(k -> k.startsWith("frames.Variante")).toList();
        System.out.println("    gespeicherte Schlüssel: " + keys);
        check("Schlüssel nach Segmenttiteln", keys.stream().anyMatch(k -> k.contains("h:Variante|Kennzahlen/Bemerkung/Weitere Felder")));
        double saved = prefs.getDouble(keys.get(0), -1);
        // Segment in Spalte 2 einfügen: Spalten-Trennlinie hat jetzt einen anderen Namen -> Startgröße
        String xml = Files.readString(cfg.resolve("viewer.xml"));
        String changed = xml.replace("<segment title=\"Bemerkung\">", "<segment title=\"Neu\"><output key=\"ObjektId\"/></segment>\n        <segment title=\"Bemerkung\">");
        write(cfg.resolve("viewer.xml"), changed);
        sleep(2000);
        shot("10a-neues-segment");
        double ratio = outer2Ratio();
        check("Mit neuem Segment: Startgröße statt alter Wert (Anteil " + String.format("%.2f", ratio) + ", gespeichert " + String.format("%.2f", saved) + ")",
                Math.abs(ratio - 1.2 / 2.2) < 0.05);
        write(cfg.resolve("viewer.xml"), xml);
        sleep(2000);
        double back = outer2Ratio();
        check("Ohne neues Segment: gespeicherter Wert wieder da (Anteil " + String.format("%.2f", back) + ")", Math.abs(back - saved) < 0.03);
        shot("10b-wieder-alt");
    }

    static double outer2Ratio() {
        JSplitPane outer = (JSplitPane) findIn(frame, c -> c instanceof JSplitPane sp && sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                && sp.getParent() != frame.getContentPane());
        return outer.getDividerLocation() / (double) (outer.getWidth() - outer.getDividerSize());
    }

    static void t11_eingabeBleibtBeiLiveAenderung() throws Exception {
        open(find("SPK Darlehen"));
        sleep(400);
        typeInto(field("Sollzins"), "4,1");
        // Live-Änderung am Aussehen, ohne Speichern
        props("farbschema = dunkel\n");
        sleep(1800);
        check("Ungespeicherte Eingabe bleibt beim Umschalten auf dunkel", "4,1".equals(field("Sollzins").getText()));
        props("farbschema = hell\n");
        sleep(1800);
        shot("11-eingabe-bleibt");
    }

    // ------------------------------------------------------------------ Hilfen

    static Container contentArea() {
        // Bereich rechts (Header + Inhalt): der Elternteil mit den Frames
        return frame.getContentPane();
    }

    static void props(String content) throws Exception {
        write(cfg.resolve("swing.properties"), content);
    }

    static void write(Path p, String s) throws Exception {
        Thread.sleep(50);
        Files.writeString(p, s, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
    }

    static ObjectNode find(String name) {
        return findNode(viewer.root(), name);
    }

    static ObjectNode findNode(ObjectNode n, String name) {
        if (n.name().equals(name)) return n;
        for (ObjectNode c : n.loadedChildren()) {
            ObjectNode r = findNode(c, name);
            if (r != null) return r;
        }
        return null;
    }

    static void open(ObjectNode n) throws Exception {
        SwingUtilities.invokeAndWait(() -> sv.show(n));
        sleep(400);
    }

    static JTextField field(String label) {
        JLabel l = (JLabel) findIn(frame, c -> c instanceof JLabel lb && lb.isShowing() && label.equals(lb.getText()) && lb.getLabelFor() != null);
        if (l == null) throw new IllegalStateException("Feld nicht gefunden: " + label);
        Component f = l.getLabelFor();
        return (JTextField) f;
    }

    static JCheckBox checkbox(String label) {
        return (JCheckBox) findIn(frame, c -> c instanceof JCheckBox b && b.isShowing() && label.equals(b.getAccessibleContext().getAccessibleName()));
    }

    static JButton button(String text) {
        return button(b -> text.equals(b.getText()));
    }

    static JButton button(Predicate<JButton> p) {
        JButton b = (JButton) findIn(frame, c -> c instanceof JButton jb && jb.isShowing() && p.test(jb));
        if (b == null) throw new IllegalStateException("Button nicht gefunden");
        return b;
    }

    static void clickToggle(String text) throws Exception {
        Component t = findIn(frame, c -> c instanceof JToggleButton b && b.isShowing() && text.equals(b.getText()));
        click(t);
    }

    static Container popupRoot() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w != frame) return w;
        }
        return frame.getLayeredPane(); // leichtes Popup liegt im Fenster
    }

    static boolean labelVisible(String text) {
        return findIn(frame, c -> c instanceof JLabel l && l.isShowing() && text.equals(l.getText())) != null;
    }

    static String headerText() {
        StringBuilder sb = new StringBuilder();
        collectText(frame.getContentPane(), sb);
        return sb.toString();
    }

    static void collectText(Component c, StringBuilder sb) {
        if (c instanceof JLabel l && l.isShowing()) sb.append(l.getText()).append('\n');
        if (c instanceof Container ct) for (Component k : ct.getComponents()) collectText(k, sb);
    }

    static Component findIn(Container root, Predicate<Component> p) {
        if (p.test(root)) return root;
        for (Component c : root.getComponents()) {
            if (p.test(c)) return c;
            if (c instanceof Container ct) {
                Component r = findIn(ct, p);
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

    static void typeInto(JTextComponent t, String text) throws Exception {
        click(t);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_DELETE);
        robot.keyRelease(KeyEvent.VK_DELETE);
        for (char ch : text.toCharArray()) typeChar(ch);
        sleep(100);
    }

    static void typeChar(char ch) {
        int code;
        boolean shift = false;
        switch (ch) {
            case ',' -> code = KeyEvent.VK_COMMA;
            case '.' -> code = KeyEvent.VK_PERIOD;
            case '*' -> { code = KeyEvent.VK_8; shift = true; }
            case ' ' -> code = KeyEvent.VK_SPACE;
            default -> {
                code = KeyEvent.getExtendedKeyCodeForChar(ch);
                shift = Character.isUpperCase(ch);
            }
        }
        if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(code);
        robot.keyRelease(code);
        if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    static void shot(String name) throws Exception {
        sleep(250);
        Rectangle r = frame.getBounds();
        ImageIO.write(robot.createScreenCapture(r), "png", shots.resolve(name + ".png").toFile());
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
