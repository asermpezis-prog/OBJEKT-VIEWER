package de.objektbaum.swing;

import de.objektbaum.Button;
import de.objektbaum.Column;
import de.objektbaum.ComboBox;
import de.objektbaum.ComboList;
import de.objektbaum.Element;
import de.objektbaum.FieldTable;
import de.objektbaum.HtmlRenderer;
import de.objektbaum.NodeView;
import de.objektbaum.ObjectNode;
import de.objektbaum.Segment;
import de.objektbaum.TextField;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Die Frames einer {@link NodeView}: bis zu 3 Spalten mit Segmenten, alle Trennlinien verschiebbar.
 * Segmente enthalten Tabellen, Textfelder, ComboBoxen, ComboListen und Buttons.
 */
final class FramesPanel {

    private final ObjectNode node;
    private final NodeView view;
    private final HtmlRenderer.Layout pageLayout;
    /** Wird bei Button-Klick bzw. Enter aufgerufen: (Formularwerte, Button-Id oder ""). */
    private final BiConsumer<Map<String, List<String>>, String> submit;
    /** Liest die aktuellen Werte der editierbaren Eingaben. */
    private final Map<String, Supplier<List<String>>> readers = new LinkedHashMap<>();
    private final List<JButton> buttons = new ArrayList<>();

    FramesPanel(ObjectNode node, NodeView view, HtmlRenderer.Layout pageLayout,
                BiConsumer<Map<String, List<String>>, String> submit) {
        this.node = node;
        this.view = view;
        this.pageLayout = pageLayout;
        this.submit = submit;
    }

    /** Baut die Frames. Größen werden pro Ansicht gespeichert. */
    JComponent build() {
        Map<String, Object> fields = node.fields();
        Splits splits = new Splits("frames." + view.name());
        List<JComponent> cols = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        List<Column> columns = view.columns();
        for (int ci = 0; ci < columns.size(); ci++) {
            Column col = columns.get(ci);
            List<JComponent> segs = new ArrayList<>();
            List<Double> heights = new ArrayList<>();
            for (int si = 0; si < col.segments().size(); si++) {
                Segment seg = col.segments().get(si);
                segs.add(segment(seg, fields, ci, si));
                heights.add(seg.height());
            }
            cols.add(splits.chain(segs, heights, JSplitPane.VERTICAL_SPLIT));
            widths.add(col.width());
        }
        JComponent frames = splits.chain(cols, widths, JSplitPane.HORIZONTAL_SPLIT);
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(frames);
        outer.setBorder(new Ui.RoundBorder(Ui.LINE, null, 10, new Insets(0, 0, 0, 0)));
        outer.setBackground(Ui.PANEL);
        return outer;
    }

    /** Werte aller editierbaren Eingaben, wie sie ein Formular abschicken würde. */
    private Map<String, List<String>> formValues() {
        Map<String, List<String>> form = new LinkedHashMap<>();
        readers.forEach((k, r) -> form.put(k, r.get()));
        return form;
    }

    // ---------------------------------------------------------------- Segment

    private JComponent segment(Segment seg, Map<String, Object> fields, int ci, int si) {
        JLabel head = Ui.sectionTitle(seg.title());
        head.setOpaque(true);
        head.setBackground(Ui.ZEBRA);
        head.setBorder(BorderFactory.createCompoundBorder(Ui.line(0, 0, 1, 0), Ui.pad(7, 14, 7, 14)));

        Ui.ScrollColumn body = new Ui.ScrollColumn();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(Ui.pad(12, 14, 12, 14));

        JPanel grid = null;
        JPanel btnRow = null;
        boolean firstButton = true;
        List<Element> els = seg.elements();
        for (int ei = 0; ei < els.size(); ei++) {
            Element el = els.get(ei);
            if (!(el instanceof Element.Input)) grid = null;
            if (!(el instanceof Button)) btnRow = null;

            if (el instanceof FieldTable t) {
                addGap(body);
                Map<String, Object> shown = new LinkedHashMap<>();
                for (String k : view.keysFor(t, fields)) {
                    shown.put(k, fields.containsKey(k) ? fields.get(k) : Ui.MISSING);
                }
                HtmlRenderer.Layout l = t.layout() != null ? t.layout() : seg.layout() != null ? seg.layout() : pageLayout;
                body.add(shown.isEmpty() ? Ui.hint("Keine Felder.")
                        : l == HtmlRenderer.Layout.VERTICAL ? Ui.kvTable(shown) : Ui.rowTable(shown));
            } else if (el instanceof Element.Input in) {
                if (grid == null) {
                    addGap(body);
                    grid = new Ui.FitHeight(new GridBagLayout());
                    grid.setOpaque(false);
                    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(grid);
                }
                addInput(grid, in, fields.get(in.key()));
            } else if (el instanceof Button b) {
                if (btnRow == null) {
                    addGap(body);
                    btnRow = new Ui.FitHeight(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    btnRow.setOpaque(false);
                    btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(btnRow);
                }
                JButton jb = Ui.button(b.label(), firstButton);
                firstButton = false;
                String id = NodeView.elementId(ci, si, ei);
                jb.addActionListener(e -> submit.accept(formValues(), id));
                buttons.add(jb);
                if (btnRow.getComponentCount() > 0) btnRow.add(Box.createHorizontalStrut(8));
                btnRow.add(jb);
            }
        }
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Ui.PANEL);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel p = new JPanel(new BorderLayout());
        p.add(head, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.setBackground(Ui.PANEL);
        p.setMinimumSize(new Dimension(80, 60));
        return p;
    }

    private static void addGap(JPanel body) {
        if (body.getComponentCount() > 0) body.add(Box.createVerticalStrut(12));
    }

    // ---------------------------------------------------------------- Eingaben

    private void addInput(JPanel grid, Element.Input in, Object value) {
        int row = grid.getComponentCount() / 2;
        JLabel label = Ui.label(in.label(), Font.BOLD, 13, Ui.MUTED);
        label.setVerticalAlignment(SwingConstants.TOP);
        JComponent field;
        Supplier<List<String>> reader = null;

        if (in instanceof TextField tf) {
            String v = value == null ? "" : HtmlRenderer.text(value);
            if (tf.rows() > 1) {
                JTextArea ta = new JTextArea(v, tf.rows(), 20);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                styleText(ta, tf.editable());
                JScrollPane sp = new JScrollPane(ta);
                sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
                field = sp;
                reader = () -> List.of(ta.getText());
            } else {
                JTextField t = new JTextField(v, 20);
                styleText(t, tf.editable());
                t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), Ui.pad(5, 8, 5, 8)));
                if (tf.editable()) {
                    // Enter = nur speichern (wie im Browser)
                    t.addActionListener(e -> submit.accept(formValues(), ""));
                }
                field = t;
                reader = () -> List.of(t.getText());
            }
        } else if (in instanceof ComboBox cb) {
            String v = value == null ? "" : HtmlRenderer.text(value);
            List<String> options = cb.options().options(node);
            JComboBox<String> box = new JComboBox<>();
            if (!cb.allowFreeInput()) box.addItem("");
            for (String o : options) box.addItem(o);
            if (!v.isEmpty() && !options.contains(v)) box.addItem(v); // Wert nicht verlieren
            box.setEditable(cb.allowFreeInput());
            box.setSelectedItem(v);
            box.setEnabled(cb.editable());
            box.setRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object val, int i, boolean sel, boolean foc) {
                    super.getListCellRendererComponent(list, val, i, sel, foc);
                    if ("".equals(val)) {
                        setText("– bitte wählen –");
                        setForeground(sel ? getForeground() : Ui.MUTED);
                    }
                    setBorder(Ui.pad(4, 8, 4, 8));
                    return this;
                }
            });
            box.setBackground(Ui.PANEL);
            field = box;
            reader = () -> {
                Object sel = cb.allowFreeInput() ? box.getEditor().getItem() : box.getSelectedItem();
                return List.of(sel == null ? "" : String.valueOf(sel));
            };
        } else if (in instanceof ComboList cl) {
            Set<String> current = new LinkedHashSet<>();
            if (value instanceof Collection<?> c) {
                for (Object o : c) current.add(String.valueOf(o));
            } else if (value != null && !String.valueOf(value).isEmpty()) {
                current.add(String.valueOf(value));
            }
            DefaultListModel<String> model = new DefaultListModel<>();
            for (String o : cl.options().options(node)) model.addElement(o);
            for (String o : current) if (!model.contains(o)) model.addElement(o);
            JList<String> list = new JList<>(model);
            list.setSelectionMode(cl.multiSelect() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                    : ListSelectionModel.SINGLE_SELECTION);
            list.setVisibleRowCount(cl.rows());
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < model.size(); i++) if (current.contains(model.get(i))) idx.add(i);
            list.setSelectedIndices(idx.stream().mapToInt(Integer::intValue).toArray());
            list.setEnabled(cl.editable());
            list.setFixedCellHeight(24);
            list.setBorder(Ui.pad(2, 6, 2, 6));
            JScrollPane sp = new JScrollPane(list);
            sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
            field = sp;
            reader = () -> List.copyOf(list.getSelectedValuesList());
            if (cl.multiSelect() && cl.editable()) {
                label.setText("<html>" + esc(cl.label()) + "<br><span style='font-weight:normal;font-size:9px'>"
                        + "Mehrfachauswahl: Strg/Cmd + Klick</span></html>");
            }
        } else {
            return;
        }

        if (in.editable() && reader != null) {
            readers.put(in.key(), reader);
        }
        label.setLabelFor(field);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 12);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridx = 0;
        label.setBorder(Ui.pad(7, 0, 0, 0));
        grid.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 0);
        grid.add(field, c);
    }

    private static void styleText(JTextComponent t, boolean editable) {
        t.setEditable(editable);
        t.setFont(Ui.font(Font.PLAIN, 14));
        t.setForeground(editable ? Ui.FG : Ui.MUTED);
        t.setBackground(editable ? Ui.PANEL : Ui.ZEBRA);
        t.setBorder(Ui.pad(5, 8, 5, 8));
    }

    private static String esc(String s) {
        return Objects.requireNonNullElse(s, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
