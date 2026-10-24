package de.objektbaum.swing;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Suchmaske „Vorgang suchen“: ein Textfeld je Suchfeld (z. B. VorgangsId, VorgangsNummer) und „Vorgang lesen“.
 * Enter in einem Feld = lesen. Leere Felder werden nicht übergeben.
 */
final class SearchPanel {

    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JButton read = Ui.button("Vorgang lesen", true);
    private final JButton clear = Ui.button("Leeren", false);

    /**
     * @param names  Suchfelder
     * @param values vorbelegte Werte (nach einem Neuaufbau) oder leer
     * @param onRead wird mit den gefüllten Feldern aufgerufen
     */
    SearchPanel(List<String> names, Map<String, String> values, Consumer<Map<String, String>> onRead) {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        int col = 0;
        for (String name : names) {
            JLabel l = Ui.label(name + ":", Font.BOLD, 13, Ui.MUTED);
            JTextField t = new JTextField(values.getOrDefault(name, ""), 22);
            t.setFont(Ui.font(Font.PLAIN, 14));
            t.setBackground(Ui.PANEL);
            t.setForeground(Ui.FG);
            t.setCaretColor(Ui.FG);
            t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), Ui.pad(5, 8, 5, 8)));
            t.getAccessibleContext().setAccessibleName(name);
            t.addActionListener(e -> onRead.accept(values()));
            l.setLabelFor(t);
            fields.put(name, t);
            c.gridx = col++;
            c.insets = new Insets(0, col == 1 ? 0 : 18, 0, 8);
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            grid.add(l, c);
            c.gridx = col++;
            c.insets = new Insets(0, 0, 0, 0);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            grid.add(t, c);
        }
        read.addActionListener(e -> onRead.accept(values()));
        clear.addActionListener(e -> {
            fields.values().forEach(f -> f.setText(""));
            fields.values().iterator().next().requestFocusInWindow();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(clear);
        buttons.add(read);

        JLabel title = Ui.sectionTitle("Vorgang suchen");
        title.setBorder(Ui.pad(0, 0, 8, 0));
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        row.add(grid, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        root.add(title, BorderLayout.NORTH);
        root.add(row, BorderLayout.CENTER);
        root.setBackground(Ui.PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(Ui.line(0, 0, 1, 0), Ui.pad(12, 28, 12, 28)));
    }

    JComponent component() {
        return root;
    }

    /** Gefüllte Felder: Name → Wert (ohne Leerzeichen am Rand). */
    Map<String, String> values() {
        Map<String, String> m = new LinkedHashMap<>();
        fields.forEach((k, t) -> {
            String v = t.getText().strip();
            if (!v.isEmpty()) m.put(k, v);
        });
        return m;
    }

    /** Alle Felder, auch leere (um sie nach einem Neuaufbau wieder einzusetzen). */
    Map<String, String> allValues() {
        Map<String, String> m = new LinkedHashMap<>();
        fields.forEach((k, t) -> m.put(k, t.getText()));
        return m;
    }

    void busy(boolean b) {
        read.setEnabled(!b);
        read.setText(b ? "Vorgang wird gelesen" + Ui.sym(" …", " ...") : "Vorgang lesen");
    }

    void focus() {
        for (JTextField f : fields.values()) {
            if (f.getText().isBlank()) {
                f.requestFocusInWindow();
                return;
            }
        }
        read.requestFocusInWindow();
    }
}
