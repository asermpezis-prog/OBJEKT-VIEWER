package de.objektbaum.swing;

import de.objektbaum.HtmlRenderer;
import de.objektbaum.ObjectNode;
import de.objektbaum.ObjectType;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Farben, Schriften und kleine Bausteine der Swing-Oberfläche – abgestimmt auf die Browser-Version.
 */
final class Ui {

    private Ui() {
    }

    static final Color BG = new Color(0xf6f7f9);
    static final Color PANEL = Color.WHITE;
    static final Color NAV = new Color(0x1f2937);
    static final Color NAV_FG = new Color(0xd1d5db);
    static final Color NAV_HI = new Color(0x374151);
    static final Color ACCENT = new Color(0x2563eb);
    static final Color FG = new Color(0x111827);
    static final Color MUTED = new Color(0x6b7280);
    static final Color LINE = new Color(0xe5e7eb);
    static final Color ZEBRA = new Color(0xf9fafb);
    static final Color SEL = new Color(0xfdf0b8);
    static final Color OK_BG = new Color(0xecfdf5);
    static final Color OK_LINE = new Color(0xa7f3d0);
    static final Color OK_FG = new Color(0x065f46);
    static final Color ERR_BG = new Color(0xfef2f2);
    static final Color ERR_LINE = new Color(0xfecaca);
    static final Color ERR_FG = new Color(0x991b1b);

    static final String FONT = pickFont();

    /** Platzhalter für ein Feld, das eine Ansicht nennt, das Objekt aber nicht hat. */
    static final Object MISSING = new Object();

    /** Einmalig beim Start: Look and Feel und Standardschrift setzen. */
    static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // Standard bleibt
        }
        FontUIResource base = new FontUIResource(FONT, Font.PLAIN, 14);
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (String.valueOf(key).endsWith(".font")) {
                UIManager.put(key, base);
            }
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LINE));
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("List.selectionBackground", new Color(0xdbeafe));
        UIManager.put("List.selectionForeground", FG);
        UIManager.put("TextField.inactiveBackground", ZEBRA);
        UIManager.put("ScrollBar.width", 12);
    }

    private static String pickFont() {
        List<String> available = List.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String f : List.of("Segoe UI", "Inter", "Noto Sans", "DejaVu Sans", "Liberation Sans", "Helvetica Neue", "Arial")) {
            if (available.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    static Font font(int style, float size) {
        return new Font(FONT, style, Math.round(size));
    }

    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static Border line(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, LINE);
    }

    // ------------------------------------------------------------------ Texte

    static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /** Überschrift eines Segments bzw. Abschnitts, in Großbuchstaben wie im Browser. */
    static JLabel sectionTitle(String text) {
        JLabel l = label(text.toUpperCase(java.util.Locale.GERMAN), Font.BOLD, 12, MUTED);
        return l;
    }

    static String typeLabel(ObjectType t) {
        return t.label().isEmpty() ? "Gruppe" : t.label();
    }

    /** Typ-Kennzeichen mit Icon und Rahmen, wie das Badge im Browser. */
    static JLabel badge(ObjectType t) {
        FilledLabel l = new FilledLabel(typeLabel(t));
        l.setFont(font(Font.PLAIN, 12));
        l.setForeground(FG);
        l.setIcon(typeIcon(t, 14));
        l.setIconTextGap(4);
        l.arc = 999;
        l.colors(ZEBRA, LINE);
        l.setBorder(pad(2, 8, 2, 10));
        return l;
    }

    // ------------------------------------------------------------------ Icons

    /** Zwischenspeicher; Schlüssel enthält Icon und Text, damit Änderungen aus der Konfiguration greifen. */
    private static final Map<String, Icon> ICONS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Icon eines Typs: das Emoji der Browser-Version, wenn eine Schrift es darstellen kann,
     * sonst ein farbiger Punkt mit Anfangsbuchstaben.
     */
    static Icon typeIcon(ObjectType t, int size) {
        if (size == 16) {
            return ICONS.computeIfAbsent(t.id() + "\u0000" + t.icon() + "\u0000" + t.label(), k -> createIcon(t, 16));
        }
        return createIcon(t, size);
    }

    private static Icon createIcon(ObjectType t, int size) {
        String emoji = t.icon();
        for (String family : List.of("Noto Emoji", "Segoe UI Emoji", "Apple Color Emoji", "Segoe UI Symbol", FONT)) {
            Font f = new Font(family, Font.PLAIN, size - 2);
            if (f.getFamily().equals(family) && f.canDisplayUpTo(emoji) == -1) {
                return new TextIcon(emoji, f, size);
            }
        }
        return new LetterIcon(t, size);
    }

    /** Zeichnet ein Zeichen (z. B. Emoji) als Icon. */
    private record TextIcon(String text, Font font, int size) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            g2.setColor(c != null ? c.getForeground() : FG);
            var fm = g2.getFontMetrics();
            int w = fm.stringWidth(text);
            g2.drawString(text, x + (size - w) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /** Farbiger Kreis mit Anfangsbuchstaben – Ersatz, wenn keine Emoji-Schrift vorhanden ist. */
    private record LetterIcon(ObjectType type, int size) implements Icon {
        private static final Color[] COLORS = {
            new Color(0x2563eb), new Color(0x059669), new Color(0xd97706), new Color(0xdc2626),
            new Color(0x7c3aed), new Color(0x0891b2), new Color(0xdb2777), new Color(0x4b5563)};

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(type == ObjectType.GRUPPE ? new Color(0xf59e0b) : COLORS[Math.floorMod(type.id().hashCode(), COLORS.length)]);
            g2.fillOval(x + 1, y + 1, size - 2, size - 2);
            g2.setColor(Color.WHITE);
            if (type == ObjectType.GRUPPE) {
                // Gruppe: kleiner Ordner
                int s = size, ox = x, oy = y;
                g2.fillRect(ox + s / 4, oy + s * 3 / 8, s / 2, s * 5 / 16);
                g2.fillRect(ox + s / 4, oy + s * 5 / 16, s / 4, s / 8);
                g2.dispose();
                return;
            }
            String letter = typeLabel(type).substring(0, 1).toUpperCase();
            g2.setFont(font(Font.BOLD, size * 0.6f));
            var fm = g2.getFontMetrics();
            g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    // ---------------------------------------------------------------- Buttons

    /** Flacher Button mit abgerundeten Ecken. primary = blau gefüllt. */
    static JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        styleFlat(b, primary);
        return b;
    }

    /** Link-artiger Button (für Pfad und Unterobjekte). */
    static JButton link(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(pad(0, 0, 0, 0));
        b.setForeground(ACCENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    static void styleFlat(AbstractButton b, boolean primary) {
        b.setUI(new FlatButtonUI());
        b.putClientProperty("primary", primary);
        b.setFont(font(Font.PLAIN, 14));
        b.setForeground(primary ? Color.WHITE : FG);
        b.setBorder(pad(7, 14, 7, 14));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setRolloverEnabled(true);
    }

    /** Zeichnet Buttons flach mit runden Ecken; ausgewählte Toggle-Buttons blau. */
    static final class FlatButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            boolean primary = Boolean.TRUE.equals(b.getClientProperty("primary"));
            boolean on = primary || (b instanceof JToggleButton && b.isSelected());
            boolean hover = b.getModel().isRollover() || b.getModel().isPressed();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Integer arc = (Integer) b.getClientProperty("arc");
            int r = arc == null ? 8 : arc;
            Color fill = on ? (hover ? ACCENT.brighter() : ACCENT) : (hover ? ZEBRA : PANEL);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, r, r);
            if (!Boolean.TRUE.equals(b.getClientProperty("noBorder"))) {
                g2.setColor(on ? ACCENT : (hover && !b.isEnabled() ? LINE : LINE));
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, r, r);
            }
            if (b.isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, r, r);
            }
            g2.dispose();
            b.setForeground(on ? Color.WHITE : FG);
            super.paint(g, c);
        }
    }

    /** Zwei oder mehr zusammenhängende Umschalter (wie "Baum | Baumtabelle"). */
    static JPanel segmented(List<JToggleButton> buttons) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, buttons.size(), 0, 0));
        p.setOpaque(false);
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (JToggleButton b : buttons) {
            styleFlat(b, false);
            b.putClientProperty("arc", 0);
            b.setFont(font(Font.PLAIN, 13));
            b.setBorder(pad(6, 12, 6, 12));
            group.add(b);
            p.add(b);
        }
        p.setBorder(new RoundBorder(LINE, null, 8, new Insets(0, 0, 0, 0)));
        return p;
    }

    // ---------------------------------------------------------------- Rahmen

    /** Abgerundeter Rahmen (nur Linie; Füllung nur für Komponenten ohne eigenen Inhalt). */
    static final class RoundBorder implements Border {
        private final Color line;
        private final Color fill;
        private final int arc;
        private final Insets insets;

        RoundBorder(Color line, Color fill, int arc, Insets insets) {
            this.line = line;
            this.fill = fill;
            this.arc = arc;
            this.insets = insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int a = Math.min(arc, h);
            if (fill != null) {
                // Füllung hinter den Inhalt: Komponente ist nicht opak, Rahmen wird zuerst gemalt
                g2.setColor(fill);
                g2.fillRoundRect(x, y, w - 1, h - 1, a, a);
            }
            g2.setColor(line);
            g2.drawRoundRect(x, y, w - 1, h - 1, a, a);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(insets.top + 1, insets.left + 1, insets.bottom + 1, insets.right + 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    /** Label mit gefülltem, abgerundetem Hintergrund (Badge, Meldungen). */
    static final class FilledLabel extends JLabel {
        private Color fill;
        private Color stroke;
        int arc = 10;

        FilledLabel(String text) {
            super(text);
            setOpaque(false);
        }

        void colors(Color fill, Color stroke) {
            this.fill = fill;
            this.stroke = stroke;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fill != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                int a = Math.min(arc, getHeight());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, a, a);
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, a, a);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------- Tabellen

    /** Nicht editierbare Tabelle im Stil der Browser-Version (Zebra, dünne Linien). */
    static JTable table(AbstractTableModel model) {
        JTable t = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int r = rowAtPoint(e.getPoint());
                int c = columnAtPoint(e.getPoint());
                if (r < 0 || c < 0) return null;
                Object v = getValueAt(r, c);
                String s = v == null || v == MISSING ? null : HtmlRenderer.text(v);
                return s == null || s.isEmpty() ? null : s;
            }
        };
        t.setRowHeight(32);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(LINE);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(false);
        t.setFocusable(false);
        t.setRowSelectionAllowed(false);
        t.getTableHeader().setReorderingAllowed(false);
        styleHeader(t.getTableHeader());
        t.setDefaultRenderer(Object.class, new ZebraRenderer());
        return t;
    }

    static void styleHeader(JTableHeader h) {
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus,
                                                           int row, int col) {
                super.getTableCellRendererComponent(table, value, false, false, row, col);
                setFont(font(Font.BOLD, 13));
                setForeground(MUTED);
                setBackground(ZEBRA);
                setBorder(BorderFactory.createCompoundBorder(line(0, 0, 1, 0), pad(6, 12, 6, 12)));
                return this;
            }
        });
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 34));
    }

    /** Zellen mit Zebra-Streifen; MISSING wird als grauer Strich gezeigt; Spalte 0 optional fett. */
    static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus,
                                                       int row, int col) {
            boolean missing = value == MISSING;
            super.getTableCellRendererComponent(table, missing ? "–" : value == null ? "" : HtmlRenderer.text(value),
                    false, false, row, col);
            boolean bold = col == 0 && Boolean.TRUE.equals(table.getClientProperty("boldFirst"));
            setFont(font(bold ? Font.BOLD : Font.PLAIN, 14));
            setForeground(missing ? MUTED : FG);
            setBackground(row % 2 == 1 ? ZEBRA : PANEL);
            setBorder(pad(0, 12, 0, 12));
            return this;
        }
    }

    /** Tabelle in einem Rahmen, Kopfzeile oben – ohne eigene Scrollleiste (wird in Segmenten gestapelt). */
    static JComponent framedTable(JTable t) {
        JPanel p = new FitHeight(new BorderLayout());
        p.add(t.getTableHeader(), BorderLayout.NORTH);
        p.add(t, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createLineBorder(LINE));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Zwei Spalten: Key | Value. */
    static JComponent kvTable(Map<String, Object> fields) {
        List<String> keys = new ArrayList<>(fields.keySet());
        AbstractTableModel m = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return keys.size();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "Key" : "Value";
            }

            @Override
            public Object getValueAt(int r, int c) {
                return c == 0 ? keys.get(r) : fields.get(keys.get(r));
            }
        };
        JTable t = table(m);
        t.putClientProperty("boldFirst", Boolean.TRUE);
        int keyWidth = 60;
        var fm = t.getFontMetrics(font(Font.BOLD, 14));
        for (String k : keys) keyWidth = Math.max(keyWidth, fm.stringWidth(k) + 30);
        t.getColumnModel().getColumn(0).setPreferredWidth(keyWidth);
        t.getColumnModel().getColumn(0).setMaxWidth(Math.max(keyWidth, 260));
        t.getColumnModel().getColumn(1).setPreferredWidth(240);
        return framedTable(t);
    }

    /** Keys als Spalten, eine Zeile mit den Values. */
    static JComponent rowTable(Map<String, Object> fields) {
        List<String> keys = new ArrayList<>(fields.keySet());
        AbstractTableModel m = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return 1;
            }

            @Override
            public int getColumnCount() {
                return keys.size();
            }

            @Override
            public String getColumnName(int c) {
                return keys.get(c);
            }

            @Override
            public Object getValueAt(int r, int c) {
                return fields.get(keys.get(c));
            }
        };
        return framedTable(table(m));
    }

    /** Unterobjekte: eine Zeile pro Objekt, Spalten Objekt, Typ und alle vorkommenden Keys. */
    static JComponent childrenTable(List<ObjectNode> nodes, Consumer<ObjectNode> open) {
        List<String> keys = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ObjectNode n : nodes) {
            Map<String, Object> f = n.fields();
            rows.add(f);
            for (String k : f.keySet()) if (!keys.contains(k)) keys.add(k);
        }
        AbstractTableModel m = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return nodes.size();
            }

            @Override
            public int getColumnCount() {
                return keys.size() + 2;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "Objekt" : c == 1 ? "Typ" : keys.get(c - 2);
            }

            @Override
            public Object getValueAt(int r, int c) {
                ObjectNode n = nodes.get(r);
                if (c == 0) return n.name();
                if (c == 1) return typeLabel(n.type());
                return rows.get(r).get(keys.get(c - 2));
            }
        };
        JTable t = table(m);
        t.getColumnModel().getColumn(0).setCellRenderer(new ZebraRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(table, value, s, f, row, col);
                setForeground(ACCENT);
                setFont(font(Font.BOLD, 14));
                setIcon(typeIcon(nodes.get(row).type(), 16));
                return this;
            }
        });
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int c = 0; c < m.getColumnCount(); c++) {
            t.getColumnModel().getColumn(c).setPreferredWidth(c == 0 ? 260 : 150);
        }
        t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = t.rowAtPoint(e.getPoint());
                if (r >= 0) open.accept(nodes.get(r));
            }
        });
        t.setToolTipText("Klicken zum Öffnen");
        return t;
    }

    // ---------------------------------------------------------------- Sortieren

    private static final java.text.Collator COLLATOR = java.text.Collator.getInstance(java.util.Locale.GERMAN);

    /** Vergleicht Texte "natürlich": Zahlenblöcke numerisch, sonst ohne Groß-/Kleinschreibung. */
    static int natural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int cmp = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = COLLATOR.compare(String.valueOf(ca), String.valueOf(cb));
                if (cmp == 0) cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Filter wie im Browser: * = alles, Text = enthält, * und ? als Platzhalter. */
    static boolean matches(String pattern, String text) {
        String p = pattern == null ? "" : pattern.trim().toLowerCase(java.util.Locale.ROOT);
        if (isAll(p)) return true;
        String t = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        return p.contains("*") || p.contains("?") ? glob(p, t) : t.contains(p);
    }

    static boolean isAll(String p) {
        String s = p == null ? "" : p.trim();
        return s.isEmpty() || s.equals("*");
    }

    private static boolean glob(String p, String t) {
        int i = 0, j = 0, star = -1, mark = 0;
        while (j < t.length()) {
            if (i < p.length() && (p.charAt(i) == '?' || p.charAt(i) == t.charAt(j))) {
                i++;
                j++;
            } else if (i < p.length() && p.charAt(i) == '*') {
                star = i++;
                mark = j;
            } else if (star >= 0) {
                i = star + 1;
                j = ++mark;
            } else {
                return false;
            }
        }
        while (i < p.length() && p.charAt(i) == '*') i++;
        return i == p.length();
    }

    /** Sonderzeichen nur verwenden, wenn die Schrift es darstellen kann; sonst Ersatztext. */
    static String sym(String symbol, String fallback) {
        return font(Font.PLAIN, 14).canDisplayUpTo(symbol) == -1 ? symbol : fallback;
    }

    /** Zentriertes graues Hinweis-Label. */
    static JLabel hint(String text) {
        JLabel l = label(text, Font.ITALIC, 14, MUTED);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Panel, das in einer senkrechten BoxLayout-Spalte nie höher als nötig wird. */
    static class FitHeight extends JPanel {
        FitHeight(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Panel für einen Scrollbereich: so breit wie der sichtbare Bereich, scrollt nur senkrecht. */
    static class ScrollColumn extends JPanel implements javax.swing.Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return Math.max(16, r.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
