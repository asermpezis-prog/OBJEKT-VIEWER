package de.objektbaum.swing;

import de.objektbaum.FormSubmit;
import de.objektbaum.HtmlRenderer;
import de.objektbaum.HtmlRenderer.Layout;
import de.objektbaum.NodeView;
import de.objektbaum.ObjectViewer;
import de.objektbaum.ObjectNode;
import de.objektbaum.TreeColumn;
import de.objektbaum.ViewRegistry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Swing-Version des Objekt-Viewers: gleicher Aufbau und gleiche Ansichten wie die Browser-Version
 * (Navigation oder Baumtabelle, Header, Frames mit Segmenten, Unterobjekte), aber als eigenes Fenster
 * ohne Webserver.
 *
 * <pre>
 * new SwingViewer(root, "Objekt-Viewer", views, spalten).open();
 * </pre>
 */
public final class SwingViewer {

    private final Supplier<String> title;
    private final Supplier<String> configError;
    private final Supplier<Object> configStamp;
    private Object lastStamp;
    private final ViewRegistry views;

    private final NavTree nav;
    private final TreeTablePanel treeTable;
    private final JComponent navComponent;
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final JPanel header = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout());
    private JFrame frame;

    private ObjectNode current;
    private HtmlRenderer.Message message;
    private Layout layout;
    private boolean tableMode;
    private final Map<ObjectNode, String> loadErrors = new IdentityHashMap<>();
    private final Set<ObjectNode> loading = Collections.newSetFromMap(new IdentityHashMap<>());

    public SwingViewer(ObjectNode root, String title, ViewRegistry views, List<TreeColumn> treeColumns) {
        this(root, () -> title, views, treeColumns, () -> null, () -> null);
    }

    /**
     * Swing-Fenster zu einem {@link ObjectViewer}: gleiche Daten, Ansichten (inkl. viewer.xml) und Aktionen
     * wie die Browser-Version. Änderungen an viewer.xml werden automatisch übernommen.
     */
    public SwingViewer(ObjectViewer viewer) {
        this(viewer.root(), viewer::title, viewer.views(), viewer.treeColumns(), viewer::configError, viewer::configStamp);
    }

    private SwingViewer(ObjectNode root, Supplier<String> title, ViewRegistry views, List<TreeColumn> treeColumns,
                        Supplier<String> configError, Supplier<Object> configStamp) {
        Ui.install();
        this.title = title;
        this.configError = configError;
        this.configStamp = configStamp;
        this.lastStamp = configStamp.get();
        this.views = views == null ? new ViewRegistry() : views;
        List<TreeColumn> cols = treeColumns == null || treeColumns.isEmpty()
                ? HtmlRenderer.DEFAULT_TREE_COLUMNS : treeColumns;
        this.layout = Layout.fromCode(Splits.PREFS.get("layout", "v"));
        this.tableMode = "table".equals(Splits.PREFS.get("mode", "nav"));

        nav = new NavTree(root, this::show);
        navComponent = nav.component(title.get());
        treeTable = new TreeTablePanel(root, cols, this::show, n -> nav.refresh(n));

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Ui.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(Ui.line(0, 0, 1, 0), Ui.pad(14, 28, 14, 28)));
        content.setBackground(Ui.BG);
        rootPanel.setBackground(Ui.BG);
        relayout();
        show(root);
    }

    /** Öffnet das Fenster. Muss im Event-Thread aufgerufen werden ({@link SwingUtilities#invokeLater}). */
    public void open() {
        frame = new JFrame(title.get());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(rootPanel);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(1440, screen.width - 40), Math.min(920, screen.height - 60));
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        updateTitle();
        frame.setVisible(true);
        // viewer.xml beobachten: geändert -> aktuelle Ansicht neu aufbauen
        new javax.swing.Timer(1000, e -> {
            Object stamp = configStamp.get();
            if (!java.util.Objects.equals(stamp, lastStamp)) {
                lastStamp = stamp;
                if (current != null) show(current, message);
            }
        }).start();
    }

    /** Der gesamte Inhalt, z. B. um ihn in ein eigenes Fenster einzubetten. */
    public JComponent content() {
        return rootPanel;
    }

    /** Zeigt ein Objekt an (wie ein Klick in Navigation oder Baumtabelle). */
    public void show(ObjectNode node) {
        show(node, null);
    }

    // ================================================================= Aufbau

    private void relayout() {
        rootPanel.removeAll();
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Ui.BG);
        right.add(header, BorderLayout.NORTH);
        right.add(content, BorderLayout.CENTER);
        if (tableMode) {
            rootPanel.add(right);
        } else {
            JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, navComponent, right);
            sp.setUI(new Splits.FlatSplitUI());
            sp.setBorder(null);
            sp.setDividerSize(4);
            sp.setResizeWeight(0);
            sp.setDividerLocation(Splits.PREFS.getInt("nav.width", 290));
            sp.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                    e -> Splits.PREFS.putInt("nav.width", sp.getDividerLocation()));
            navComponent.setMinimumSize(new Dimension(160, 100));
            rootPanel.add(sp);
        }
        rootPanel.revalidate();
        rootPanel.repaint();
    }

    private void show(ObjectNode node, HtmlRenderer.Message msg) {
        current = node;
        message = msg;
        if (!node.isLoaded() && !loadErrors.containsKey(node)) {
            loadChildren(node);
        }
        renderHeader();
        renderContent();
        nav.select(node);
        treeTable.setSelected(node);
        updateTitle();
    }

    private void updateTitle() {
        if (frame != null && current != null) frame.setTitle(current.name() + " – " + title.get());
    }

    // ================================================================= Header

    private void renderHeader() {
        ObjectNode node = current;
        header.removeAll();

        String cfgError = configError.get();
        if (cfgError != null) {
            Ui.FilledLabel err = new Ui.FilledLabel("<html>" + cfgError.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
            err.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
            err.setForeground(Ui.ERR_FG);
            err.colors(Ui.ERR_BG, Ui.ERR_LINE);
            err.setBorder(Ui.pad(8, 12, 8, 12));
            err.setAlignmentX(Component.LEFT_ALIGNMENT);
            err.setMaximumSize(new Dimension(Integer.MAX_VALUE, err.getPreferredSize().height));
            header.add(err);
            header.add(Box.createVerticalStrut(10));
        }

        JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        crumbs.setOpaque(false);
        List<ObjectNode> path = node.path();
        for (int i = 0; i < path.size(); i++) {
            ObjectNode p = path.get(i);
            if (i > 0) crumbs.add(Ui.label("  /  ", Font.PLAIN, 13, Ui.MUTED));
            if (p == node) {
                crumbs.add(Ui.label(p.name(), Font.PLAIN, 13, Ui.MUTED));
            } else {
                JButton l = Ui.link(p.name(), () -> show(p));
                l.setFont(Ui.font(Font.PLAIN, 13));
                crumbs.add(l);
            }
        }

        JPanel titleRow = new JPanel(new BorderLayout(16, 0));
        titleRow.setOpaque(false);
        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleLeft.setOpaque(false);
        JLabel h1 = Ui.label(node.name(), Font.BOLD, 24, Ui.FG);
        titleLeft.add(h1);
        titleLeft.add(Box.createHorizontalStrut(10));
        titleLeft.add(Ui.badge(node.type()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        if (node.isLazy()) {
            JButton reload = Ui.button(Ui.sym("↻ ", "") + "Neu laden", false);
            reload.setFont(Ui.font(Font.PLAIN, 13));
            reload.setBorder(Ui.pad(6, 12, 6, 12));
            reload.setToolTipText("Unterobjekte verwerfen und neu laden");
            reload.addActionListener(e -> reload(node));
            actions.add(reload);
        }
        JToggleButton tree = new JToggleButton("Baum", !tableMode);
        JToggleButton table = new JToggleButton("Baumtabelle", tableMode);
        tree.addActionListener(e -> setTableMode(false));
        table.addActionListener(e -> setTableMode(true));
        actions.add(Ui.segmented(List.of(tree, table)));
        JToggleButton kv = new JToggleButton("Key / Value", layout == Layout.VERTICAL);
        JToggleButton cols = new JToggleButton("Keys als Spalten", layout == Layout.HORIZONTAL);
        kv.addActionListener(e -> setLayout(Layout.VERTICAL));
        cols.addActionListener(e -> setLayout(Layout.HORIZONTAL));
        actions.add(Ui.segmented(List.of(kv, cols)));
        titleRow.add(titleLeft, BorderLayout.WEST);
        titleRow.add(actions, BorderLayout.EAST);

        NodeView view = views.resolve(node);
        StringBuilder meta = new StringBuilder();
        meta.append(node.fields().size()).append(" Felder · ")
            .append(node.isLoaded() ? node.loadedChildren().size() + " Unterobjekte" : "Unterobjekte nicht geladen");
        if (node.isLazy()) meta.append(" · lazy");
        if (view != null) meta.append(" · Ansicht: ").append(view.name());

        for (JComponent c : List.of(crumbs, titleRow)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(c);
            header.add(Box.createVerticalStrut(4));
        }
        JLabel metaLabel = Ui.label(meta.toString(), Font.PLAIN, 13, Ui.MUTED);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(metaLabel);

        if (message != null) {
            Ui.FilledLabel msg = new Ui.FilledLabel(message.text());
            msg.setFont(Ui.font(Font.PLAIN, 14));
            msg.setForeground(message.error() ? Ui.ERR_FG : Ui.OK_FG);
            msg.colors(message.error() ? Ui.ERR_BG : Ui.OK_BG, message.error() ? Ui.ERR_LINE : Ui.OK_LINE);
            msg.setBorder(Ui.pad(8, 12, 8, 12));
            msg.setAlignmentX(Component.LEFT_ALIGNMENT);
            msg.setMaximumSize(new Dimension(Integer.MAX_VALUE, msg.getPreferredSize().height));
            msg.getAccessibleContext().setAccessibleDescription(message.error() ? "Fehler" : "Meldung");
            header.add(Box.createVerticalStrut(10));
            header.add(msg);
        }
        header.revalidate();
        header.repaint();
    }

    private void setTableMode(boolean table) {
        if (tableMode == table) return;
        tableMode = table;
        Splits.PREFS.put("mode", table ? "table" : "nav");
        relayout();
        renderContent();
        treeTable.setSelected(current);
    }

    private void setLayout(Layout l) {
        if (layout == l) return;
        layout = l;
        Splits.PREFS.put("layout", l.code());
        renderContent();
    }

    // ================================================================= Inhalt

    private void renderContent() {
        content.removeAll();
        JComponent main = mainArea(current);
        if (tableMode) {
            Splits s = new Splits("treetable");
            JComponent tt = treeTable.component();
            JPanel ttWrap = new JPanel(new BorderLayout());
            ttWrap.setBackground(Ui.BG);
            ttWrap.setBorder(Ui.pad(20, 28, 10, 28));
            ttWrap.add(tt);
            content.add(s.split(JSplitPane.VERTICAL_SPLIT, ttWrap, main, 0.36));
        } else {
            content.add(main);
        }
        content.revalidate();
        content.repaint();
    }

    private JComponent mainArea(ObjectNode node) {
        NodeView view = views.resolve(node);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Ui.BG);
        main.setBorder(Ui.pad(tableMode ? 10 : 24, 28, 20, 28));

        if (view != null) {
            JComponent frames = new FramesPanel(node, view, layout, this::submit).build();
            if (view.showChildren()) {
                Splits s = new Splits("children." + view.name());
                JComponent children = childrenSection(node);
                children.setBorder(Ui.pad(16, 0, 0, 0));
                main.add(s.split(JSplitPane.VERTICAL_SPLIT, frames, children, 0.76));
            } else {
                main.add(frames);
            }
            return main;
        }

        // Standarddarstellung: alle Felder, darunter die Unterobjekte
        Ui.ScrollColumn col = new Ui.ScrollColumn();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(Ui.BG);
        col.add(sectionTitle("Felder"));
        Map<String, Object> fields = node.fields();
        col.add(fields.isEmpty() ? Ui.hint("Dieses Objekt hat keine Felder.")
                : layout == Layout.VERTICAL ? Ui.kvTable(fields) : Ui.rowTable(fields));
        col.add(Box.createVerticalStrut(24));
        col.add(sectionTitle("Unterobjekte"));
        col.add(childrenContent(node, false));
        col.add(Box.createVerticalGlue());
        JScrollPane sp = new JScrollPane(col);
        sp.setBorder(null);
        sp.getViewport().setBackground(Ui.BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        main.add(sp);
        return main;
    }

    private static JComponent sectionTitle(String text) {
        JLabel l = Ui.label(text.toUpperCase(java.util.Locale.GERMAN), Font.BOLD, 15, Ui.MUTED);
        l.setBorder(Ui.pad(0, 0, 10, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Abschnitt "Unterobjekte" unter den Frames, mit eigenem Scrollbereich. */
    private JComponent childrenSection(ObjectNode node) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Ui.BG);
        p.add(sectionTitle("Unterobjekte"), BorderLayout.NORTH);
        Ui.ScrollColumn col = new Ui.ScrollColumn();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(Ui.BG);
        col.add(childrenContent(node, true));
        col.add(Box.createVerticalGlue());
        JScrollPane sp = new JScrollPane(col);
        sp.setBorder(null);
        sp.getViewport().setBackground(Ui.BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        p.add(sp, BorderLayout.CENTER);
        p.setMinimumSize(new Dimension(100, 60));
        return p;
    }

    private JComponent childrenContent(ObjectNode node, boolean ownScroll) {
        String error = loadErrors.get(node);
        if (error != null) {
            JPanel e = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            e.setOpaque(false);
            Ui.FilledLabel l = new Ui.FilledLabel(error);
            l.setForeground(Ui.ERR_FG);
            l.colors(Ui.ERR_BG, Ui.ERR_LINE);
            l.setBorder(Ui.pad(8, 12, 8, 12));
            e.add(l);
            e.add(Ui.link("Erneut versuchen", () -> {
                loadErrors.remove(node);
                show(node);
            }));
            e.setAlignmentX(Component.LEFT_ALIGNMENT);
            return e;
        }
        if (!node.isLoaded()) {
            return Ui.hint("Unterobjekte werden geladen" + Ui.sym(" …", " ..."));
        }
        List<ObjectNode> kids = node.loadedChildren();
        if (kids.isEmpty()) {
            return Ui.hint("Keine Unterobjekte.");
        }
        if (layout == Layout.HORIZONTAL) {
            JComponent t = Ui.childrenTable(kids, this::show);
            JScrollPane sp = new JScrollPane(t);
            sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
            sp.getViewport().setBackground(Ui.PANEL);
            int h = ((javax.swing.JTable) t).getRowHeight() * kids.size() + 40;
            sp.setPreferredSize(new Dimension(400, h + 16));
            sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 16));
            sp.setAlignmentX(Component.LEFT_ALIGNMENT);
            return sp;
        }
        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (ObjectNode c : kids) {
            cards.add(card(c));
            cards.add(Box.createVerticalStrut(14));
        }
        return cards;
    }

    private JComponent card(ObjectNode c) {
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        head.setOpaque(false);
        JButton link = Ui.link(c.name(), () -> show(c));
        link.setFont(Ui.font(Font.BOLD, 16));
        head.add(link);
        head.add(Box.createHorizontalStrut(8));
        head.add(Ui.badge(c.type()));
        head.add(Box.createHorizontalStrut(8));
        String info = !c.isLoaded() ? "Unterobjekte werden bei Bedarf geladen"
                : c.loadedChildren().size() == 1 ? "1 Unterobjekt" : c.loadedChildren().size() + " Unterobjekte";
        head.add(Ui.label(info, Font.PLAIN, 13, Ui.MUTED));
        head.setBorder(Ui.pad(0, 0, 8, 0));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Ui.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Ui.RoundBorder(Ui.LINE, null, 10, new Insets(0, 0, 0, 0)), Ui.pad(12, 16, 12, 16)));
        card.add(head, BorderLayout.NORTH);
        Map<String, Object> f = c.fields();
        card.add(f.isEmpty() ? Ui.hint("Keine Felder.") : Ui.kvTable(f), BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    // ================================================================= Aktionen

    /** Button oder Enter in einem Segment: Eingaben speichern, Aktion ausführen (im Hintergrund). */
    private void submit(Map<String, List<String>> form, String buttonId) {
        ObjectNode node = current;
        NodeView view = views.resolve(node);
        rootPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<HtmlRenderer.Message, Void>() {
            @Override
            protected HtmlRenderer.Message doInBackground() {
                return FormSubmit.submit(node, view, form, buttonId);
            }

            @Override
            protected void done() {
                rootPanel.setCursor(Cursor.getDefaultCursor());
                HtmlRenderer.Message result;
                try {
                    result = get();
                } catch (InterruptedException | ExecutionException e) {
                    result = new HtmlRenderer.Message("Fehler: " + e.getCause(), true);
                }
                treeTable.refresh();
                nav.repaint();
                if (current == node) show(node, result);
            }
        }.execute();
    }

    private void loadChildren(ObjectNode node) {
        if (!loading.add(node)) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                node.ensureLoaded();
                return null;
            }

            @Override
            protected void done() {
                loading.remove(node);
                try {
                    get();
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    loadErrors.put(node, cause.getMessage());
                }
                nav.refresh(node);
                treeTable.refresh();
                if (current == node) show(node, message);
            }
        }.execute();
    }

    private void reload(ObjectNode node) {
        node.reload();
        loadErrors.remove(node);
        nav.refresh(node);
        treeTable.forgetExpansion(node);
        show(node);
    }
}
