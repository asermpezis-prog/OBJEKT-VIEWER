package de.objektbaum.swing;

import de.objektbaum.ObjectNode;
import de.objektbaum.TreeColumn;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Baumtabelle wie in der Browser-Version: eingerückte Namensspalte mit Auf-/Zuklappen,
 * frei wählbare Spalten, Filterzeile (* und ?), Sortierung je Ebene, Alle auf/zu,
 * ausgewählte Zeile markiert. Lazy Knoten laden beim Aufklappen im Hintergrund.
 */
final class TreeTablePanel {

    private final int INDENT = Ui.px(18);
    private final int TOGGLE = Ui.px(20);

    private record Row(ObjectNode node, int depth) {
    }

    /**
     * Zustand, der beim Neuaufbau (geänderte Spalten in viewer.xml, anderes Aussehen) erhalten bleibt.
     * Filter, Sortierung und Breiten hängen an der Spaltenüberschrift – entfällt eine Spalte, entfällt ihr Zustand.
     */
    record State(Set<ObjectNode> expanded, java.util.Map<String, String> filters, String sortHeader, int dir,
                 java.util.Map<String, Integer> widths, ObjectNode selected) {
    }

    private final ObjectNode root;
    private final List<TreeColumn> columns;
    private final Consumer<ObjectNode> open;
    /** Wird aufgerufen, wenn ein Knoten hier nachgeladen wurde (damit die Navigation nachzieht). */
    private final Consumer<ObjectNode> loaded;

    private final Set<ObjectNode> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ObjectNode> loading = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ObjectNode> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Row> rows = new ArrayList<>();
    private final String[] filters;
    private int sortCol = -1;
    private int dir = 1;
    private ObjectNode selected;

    private final AbstractTableModel model;
    private final JTable table;
    private final JLabel count = Ui.label("", Font.PLAIN, 12, Ui.ACCENT);
    private final List<JTextField> filterFields = new ArrayList<>();
    private final JComponent component;

    TreeTablePanel(ObjectNode root, List<TreeColumn> columns, Consumer<ObjectNode> open, Consumer<ObjectNode> loaded) {
        this(root, columns, open, loaded, null);
    }

    /** @param previous Zustand einer früheren Baumtabelle, der übernommen wird, oder null */
    TreeTablePanel(ObjectNode root, List<TreeColumn> columns, Consumer<ObjectNode> open, Consumer<ObjectNode> loaded,
                   State previous) {
        this.root = root;
        this.columns = List.copyOf(columns);
        this.open = open;
        this.loaded = loaded;
        this.filters = new String[columns.size()];
        java.util.Arrays.fill(filters, "*");

        model = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return columns.size() + 1;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "" : columns.get(c - 1).header();
            }

            @Override
            public Object getValueAt(int r, int c) {
                Row row = rows.get(r);
                return c == 0 ? "" : columns.get(c - 1).valueOf(row.node());
            }
        };

        table = new JTable(model) {
            // Kopf wird selbst (zusammen mit der Filterzeile) in den Scrollbereich gesetzt
            @Override
            protected void configureEnclosingScrollPane() {
            }

            // ...und beim Umhängen der Tabelle (Navigation) nicht entfernen
            @Override
            protected void unconfigureEnclosingScrollPane() {
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                ObjectNode n = rows.get(r).node();
                return failed.contains(n) ? "Laden fehlgeschlagen – erneut auf + klicken" : null;
            }
        };
        table.setRowHeight(Ui.px(32));
        table.setShowVerticalLines(false);
        table.setGridColor(Ui.LINE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowSelectionAllowed(false);
        table.setFocusable(false);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        table.setBackground(Ui.PANEL);
        table.setForeground(Ui.FG);
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        Ui.styleHeader(header);
        javax.swing.table.TableCellRenderer base = header.getDefaultRenderer();
        header.setDefaultRenderer((t, value, s, f, r, c) -> {
            JLabel l = (JLabel) base.getTableCellRendererComponent(t, value, s, f, r, c);
            l.setForeground(Ui.FG);
            l.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
            l.setIconTextGap(8);
            l.setIcon(c == 0 ? null : new SortIcon(sortCol == c - 1 ? dir : 0));
            return l;
        });
        header.setToolTipText("Klicken zum Sortieren");
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int c = table.columnAtPoint(e.getPoint());
                if (c > 0) sort(c - 1);
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                click(e);
            }
        });

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(Ui.px(32));
        cm.getColumn(0).setMaxWidth(Ui.px(32));
        for (int i = 0; i < columns.size(); i++) {
            Integer w = previous == null ? null : previous.widths().get(columns.get(i).header());
            cm.getColumn(i + 1).setPreferredWidth(w != null ? w
                    : Ui.px(columns.get(i).kind() == TreeColumn.Kind.NAME ? 380 : 190));
        }

        if (previous != null) {
            expanded.addAll(previous.expanded());
            for (int i = 0; i < columns.size(); i++) {
                String f = previous.filters().get(columns.get(i).header());
                if (f != null) filters[i] = f;
                if (columns.get(i).header().equals(previous.sortHeader())) {
                    sortCol = i;
                    dir = previous.dir();
                }
            }
            selected = previous.selected();
        }
        component = build();
        rebuild();
    }

    /** Aktueller Zustand, um ihn an eine neu aufgebaute Baumtabelle weiterzugeben. */
    State state() {
        java.util.Map<String, String> f = new java.util.HashMap<>();
        java.util.Map<String, Integer> w = new java.util.HashMap<>();
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < columns.size(); i++) {
            f.put(columns.get(i).header(), filters[i]);
            w.put(columns.get(i).header(), cm.getColumn(i + 1).getWidth() > 0 ? cm.getColumn(i + 1).getWidth()
                    : cm.getColumn(i + 1).getPreferredWidth());
        }
        Set<ObjectNode> ex = Collections.newSetFromMap(new IdentityHashMap<>());
        ex.addAll(expanded);
        return new State(ex, f, sortCol < 0 ? null : columns.get(sortCol).header(), dir, w, selected);
    }

    /** Die Spalten, mit denen diese Baumtabelle gebaut wurde. */
    List<TreeColumn> columns() {
        return columns;
    }

    JComponent component() {
        return component;
    }

    /** Neues ausgewähltes Objekt: Pfad aufklappen, Zeile markieren und sichtbar machen. */
    void setSelected(ObjectNode node) {
        selected = node;
        for (ObjectNode p : node.path()) {
            if (p.isLoaded() && !p.loadedChildren().isEmpty()) expanded.add(p);
        }
        rebuild();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).node() == node) {
                Rectangle r = table.getCellRect(i, 0, true);
                r.height = Math.max(r.height, 160); // etwas Umgebung mit einblenden
                table.scrollRectToVisible(r);
                break;
            }
        }
    }

    /** Daten haben sich geändert (z. B. Felder nach dem Speichern): neu anzeigen. */
    void refresh() {
        rebuild();
    }

    /** Ein Knoten wurde neu geladen bzw. verworfen. */
    void forgetExpansion(ObjectNode node) {
        expanded.remove(node);
        rebuild();
    }

    // ---------------------------------------------------------------- Aufbau

    private JComponent build() {
        JButton expandAll = small(Ui.sym("⊞ ", "") + "Alle auf", "Alle geladenen aufklappen");
        expandAll.addActionListener(e -> {
            expandLoaded(root);
            rebuild();
        });
        JButton collapseAll = small(Ui.sym("⊟ ", "") + "Alle zu", "Alle zuklappen");
        collapseAll.addActionListener(e -> {
            expanded.clear();
            rebuild();
        });
        JButton clear = small(Ui.sym("✕ ", "") + "Filter löschen", "Filter zurücksetzen");
        clear.addActionListener(e -> filterFields.forEach(f -> f.setText("*")));

        JPanel tools = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(expandAll);
        left.add(collapseAll);
        left.add(clear);
        left.add(Box.createHorizontalStrut(4));
        left.add(count);
        tools.add(left, BorderLayout.WEST);
        tools.add(Ui.label("Filter: Text = enthält, * und ? als Platzhalter  ", Font.PLAIN, 12, Ui.MUTED), BorderLayout.EAST);
        tools.setBackground(Ui.ZEBRA);
        tools.setBorder(BorderFactory.createCompoundBorder(Ui.line(0, 0, 1, 0), Ui.pad(6, 4, 6, 4)));

        // Filterzeile: ein Textfeld je Spalte, genau unter der Spaltenüberschrift
        JPanel filterRow = new JPanel(null) {
            @Override
            public void doLayout() {
                TableColumnModel cm = table.getColumnModel();
                int x = 0;
                for (int i = 0; i < cm.getColumnCount(); i++) {
                    int w = cm.getColumn(i).getWidth();
                    if (i > 0) filterFields.get(i - 1).setBounds(x + 6, Ui.px(4), Math.max(20, w - 12), Ui.px(26));
                    x += w;
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(table.getColumnModel().getTotalColumnWidth(), Ui.px(34));
            }
        };
        filterRow.setBackground(Ui.ZEBRA);
        filterRow.setBorder(Ui.line(0, 0, 1, 0));
        for (int i = 0; i < columns.size(); i++) {
            int col = i;
            JTextField f = new JTextField(filters[i]);
            f.setBackground(Ui.PANEL);
            f.setForeground(Ui.FG);
            f.setCaretColor(Ui.FG);
            f.setFont(Ui.font(Font.PLAIN, 13));
            f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), Ui.pad(2, 6, 2, 6)));
            f.setToolTipText("Filter " + columns.get(i).header());
            f.getAccessibleContext().setAccessibleName("Filter " + columns.get(i).header());
            f.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() {
                    filters[col] = f.getText();
                    rebuild();
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    changed();
                }
            });
            filterFields.add(f);
            filterRow.add(f);
        }
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent e) {
                filterRow.revalidate();
                filterRow.repaint();
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });

        JPanel headerStack = new JPanel(new BorderLayout());
        headerStack.add(table.getTableHeader(), BorderLayout.NORTH);
        headerStack.add(filterRow, BorderLayout.CENTER);

        JScrollPane sp = new JScrollPane(table);
        sp.setColumnHeaderView(headerStack);
        sp.setBorder(null);
        sp.getViewport().setBackground(Ui.PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        JPanel p = new JPanel(new BorderLayout());
        p.add(tools, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.setBorder(new Ui.RoundBorder(Ui.LINE, null, 10, new java.awt.Insets(0, 0, 0, 0)));
        p.setBackground(Ui.PANEL);
        p.setMinimumSize(new Dimension(200, 120));
        return p;
    }

    private static JButton small(String text, String tip) {
        JButton b = Ui.button(text, false);
        b.setFont(Ui.font(Font.PLAIN, 12));
        b.setBorder(Ui.pad(3, 9, 3, 9));
        b.setToolTipText(tip);
        return b;
    }

    // ---------------------------------------------------------------- Zeilen

    private void rebuild() {
        rows.clear();
        boolean active = false;
        for (String f : filters) active |= !Ui.isAll(f);
        Set<ObjectNode> keep = active ? matchingWithAncestors() : null;
        walk(root, 0, keep);
        model.fireTableDataChanged();
        updateHeaders();
        count.setText(keep == null ? "" : rows.size() + " Zeilen passen zum Filter");
    }

    private void walk(ObjectNode n, int depth, Set<ObjectNode> keep) {
        if (keep != null && !keep.contains(n)) return;
        rows.add(new Row(n, depth));
        if (keep != null || expanded.contains(n)) {
            for (ObjectNode c : sorted(n.loadedChildren())) walk(c, depth + 1, keep);
        }
    }

    private List<ObjectNode> sorted(List<ObjectNode> kids) {
        if (sortCol < 0) return kids;
        TreeColumn col = columns.get(sortCol);
        List<ObjectNode> copy = new ArrayList<>(kids);
        Comparator<ObjectNode> cmp = (a, b) -> Ui.natural(col.valueOf(a), col.valueOf(b));
        copy.sort(dir > 0 ? cmp : cmp.reversed());
        return copy;
    }

    private Set<ObjectNode> matchingWithAncestors() {
        Set<ObjectNode> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, keep);
        return keep;
    }

    private void collect(ObjectNode n, Set<ObjectNode> keep) {
        boolean all = true;
        for (int i = 0; i < columns.size() && all; i++) {
            all = Ui.matches(filters[i], columns.get(i).valueOf(n));
        }
        if (all) {
            for (ObjectNode p = n; p != null && keep.add(p); p = p.parent()) {
                // Elternknoten mit aufnehmen
            }
        }
        for (ObjectNode c : n.loadedChildren()) collect(c, keep);
    }

    private void expandLoaded(ObjectNode n) {
        if (n.isLoaded() && !n.loadedChildren().isEmpty()) {
            expanded.add(n);
            for (ObjectNode c : n.loadedChildren()) expandLoaded(c);
        }
    }

    /** Sortierpfeile im Spaltenkopf aktualisieren, ohne Spaltenbreiten anzutasten. */
    private void updateHeaders() {
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            cm.getColumn(i).setHeaderValue(model.getColumnName(i));
        }
        table.getTableHeader().repaint();
    }

    // ---------------------------------------------------------------- Bedienung

    private void click(MouseEvent e) {
        int r = table.rowAtPoint(e.getPoint());
        int c = table.columnAtPoint(e.getPoint());
        if (r < 0) return;
        Row row = rows.get(r);
        ObjectNode n = row.node();
        int nameCol = nameColumn();
        if (c == nameCol && hasChildren(n)) {
            Rectangle cell = table.getCellRect(r, c, false);
            int x = e.getX() - cell.x - 8 - row.depth() * INDENT;
            if (x >= -2 && x <= TOGGLE + 2) {
                toggle(n);
                return;
            }
        }
        open.accept(n);
    }

    private int nameColumn() {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).kind() == TreeColumn.Kind.NAME) return i + 1;
        }
        return 1;
    }

    private static boolean hasChildren(ObjectNode n) {
        return !n.isLoaded() || !n.loadedChildren().isEmpty();
    }

    private void toggle(ObjectNode n) {
        if (!n.isLoaded()) {
            if (!loading.add(n)) return;
            failed.remove(n);
            table.repaint();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    n.ensureLoaded();
                    return null;
                }

                @Override
                protected void done() {
                    loading.remove(n);
                    try {
                        get();
                        expanded.add(n);
                        loaded.accept(n);
                    } catch (InterruptedException | ExecutionException ex) {
                        failed.add(n);
                    }
                    rebuild();
                }
            }.execute();
            return;
        }
        if (!expanded.remove(n)) expanded.add(n);
        rebuild();
    }

    private void sort(int col) {
        dir = sortCol == col ? -dir : 1;
        sortCol = col;
        rebuild();
    }

    // ---------------------------------------------------------------- Darstellung

    private final class CellRenderer extends DefaultTableCellRenderer {
        private Row row;
        private boolean nameCell;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, value, false, false, r, c);
            row = rows.get(r);
            ObjectNode n = row.node();
            nameCell = c == nameColumn();
            markCell = c == 0;
            boolean isSel = n == selected;
            setBackground(isSel ? Ui.SEL : r % 2 == 1 ? Ui.ZEBRA : Ui.PANEL);
            setForeground(Ui.FG);
            setIcon(null);
            if (c == 0) {
                setFont(Ui.font(Font.BOLD, 14));
                setHorizontalAlignment(CENTER);
                setBorder(null);
            } else if (nameCell) {
                setFont(Ui.font(Font.BOLD, 14));
                setHorizontalAlignment(LEFT);
                setIcon(Ui.typeIcon(n.type(), 16));
                setIconTextGap(5);
                setBorder(Ui.pad(0, 8 + row.depth() * INDENT + TOGGLE + 6, 0, 8));
            } else {
                setFont(Ui.font(Font.PLAIN, 14));
                setHorizontalAlignment(LEFT);
                setBorder(Ui.pad(0, 10, 0, 10));
            }
            return this;
        }

        private boolean markCell;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (markCell && row.node() == selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(Ui.FG);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                g2.drawPolyline(new int[]{cx - 6, cx - 2, cx + 6}, new int[]{cy, cy + 4, cy - 5}, 3);
                g2.dispose();
            }
            if (!nameCell || !hasChildren(row.node())) return;
            ObjectNode n = row.node();
            boolean open = expanded.contains(n) && n.isLoaded();
            String sign = loading.contains(n) ? Ui.sym("…", ".") : open ? "−" : "+";
            int x = 8 + row.depth() * INDENT;
            int y = (getHeight() - TOGGLE) / 2;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Ui.PANEL);
            g2.fillRoundRect(x, y, TOGGLE - 1, TOGGLE - 1, 5, 5);
            g2.setColor(failed.contains(n) ? Ui.danger() : Ui.MUTED);
            g2.drawRoundRect(x, y, TOGGLE - 1, TOGGLE - 1, 5, 5);
            g2.setFont(Ui.font(Font.BOLD, 14));
            var fm = g2.getFontMetrics();
            g2.drawString(sign, x + (TOGGLE - fm.stringWidth(sign)) / 2, y + (TOGGLE + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Sortierpfeil im Spaltenkopf: 0 = unsortiert (beide Pfeile, grau), 1 = aufsteigend, -1 = absteigend. */
    private record SortIcon(int state) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int m = x + 5;
            if (state >= 0) {
                g2.setColor(state > 0 ? Ui.ACCENT : Ui.MUTED);
                g2.fillPolygon(new int[]{m - 4, m + 4, m}, new int[]{y + 6, y + 6, y + 1}, 3);
            }
            if (state <= 0) {
                g2.setColor(state < 0 ? Ui.ACCENT : Ui.MUTED);
                g2.fillPolygon(new int[]{m - 4, m + 4, m}, new int[]{y + 8, y + 8, y + 13}, 3);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
