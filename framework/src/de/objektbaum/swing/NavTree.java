package de.objektbaum.swing;

import de.objektbaum.ObjectNode;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Navigation links: Baum der Objekte. Lazy Knoten laden ihre Kinder erst beim Aufklappen,
 * im Hintergrund – das Fenster bleibt bedienbar.
 */
final class NavTree {

    /** Platzhalter unter einem noch nicht geladenen Knoten. */
    private static final String LOADING = "lädt" + Ui.sym(" …", " ...");
    private static final String NOT_LOADED = " ";

    private final Consumer<ObjectNode> open;
    private final DefaultTreeModel model;
    private final JTree tree;
    private final Map<ObjectNode, DefaultMutableTreeNode> map = new IdentityHashMap<>();
    private boolean programmatic;

    NavTree(ObjectNode root, Consumer<ObjectNode> open) {
        this.open = open;
        this.model = new DefaultTreeModel(build(root));
        this.tree = new JTree(model);
        style();
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                DefaultMutableTreeNode d = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                if (hasPlaceholder(d)) {
                    load(d);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
            }
        });
        tree.addTreeSelectionListener(e -> {
            if (programmatic || e.getNewLeadSelectionPath() == null) return;
            Object u = ((DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent()).getUserObject();
            if (u instanceof ObjectNode n) open.accept(n);
        });
        tree.expandRow(0);
    }

    JComponent component(String title) {
        JLabel brand = Ui.label(title, Font.BOLD, 17, Color.WHITE);
        brand.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.NAV_HI), Ui.pad(4, 8, 14, 8)));
        JScrollPane sp = new JScrollPane(tree);
        sp.setBorder(Ui.pad(8, 0, 0, 0));
        sp.getViewport().setBackground(Ui.NAV);
        sp.setBackground(Ui.NAV);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Ui.NAV);
        p.setBorder(Ui.pad(16, 12, 0, 4));
        p.add(brand, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    /** Markiert das Objekt im Baum und klappt seinen Pfad auf (ohne Navigation auszulösen). */
    void select(ObjectNode node) {
        programmatic = true;
        try {
            DefaultMutableTreeNode d = null;
            for (ObjectNode p : node.path()) {
                d = map.get(p);
                if (d == null) return;
                if (hasPlaceholder(d) && p.isLoaded()) populate(d);
            }
            TreePath path = new TreePath(d.getPath());
            if (path.getParentPath() != null) tree.expandPath(path.getParentPath());
            if (node.isLoaded() && !node.loadedChildren().isEmpty()) tree.expandPath(path);
            tree.setSelectionPath(path);
            // nur senkrecht scrollen, damit Einrückung und Icons links sichtbar bleiben
            Rectangle b = tree.getPathBounds(path);
            if (b != null) tree.scrollRectToVisible(new Rectangle(0, b.y, 1, b.height));
        } finally {
            programmatic = false;
        }
    }

    /** Nach "Neu laden" oder Laden an anderer Stelle: Kinder des Knotens neu aufbauen. */
    void refresh(ObjectNode node) {
        DefaultMutableTreeNode d = map.get(node);
        if (d == null) return;
        TreePath path = new TreePath(d.getPath());
        boolean wasExpanded = tree.isExpanded(path);
        populate(d);
        if (wasExpanded && node.isLoaded()) tree.expandPath(path);
    }

    void repaint() {
        tree.repaint();
    }

    // ---------------------------------------------------------------- intern

    private DefaultMutableTreeNode build(ObjectNode n) {
        DefaultMutableTreeNode d = new DefaultMutableTreeNode(n);
        map.put(n, d);
        fill(d, n);
        return d;
    }

    private void fill(DefaultMutableTreeNode d, ObjectNode n) {
        if (!n.isLoaded()) {
            d.add(new DefaultMutableTreeNode(NOT_LOADED));
        } else {
            for (ObjectNode c : n.loadedChildren()) d.add(build(c));
        }
    }

    private void populate(DefaultMutableTreeNode d) {
        ObjectNode n = (ObjectNode) d.getUserObject();
        for (int i = 0; i < d.getChildCount(); i++) {
            Object u = ((DefaultMutableTreeNode) d.getChildAt(i)).getUserObject();
            if (u instanceof ObjectNode c) forget(c);
        }
        d.removeAllChildren();
        fill(d, n);
        model.nodeStructureChanged(d);
    }

    private void forget(ObjectNode n) {
        map.remove(n);
        for (ObjectNode c : n.loadedChildren()) forget(c);
    }

    private static boolean hasPlaceholder(DefaultMutableTreeNode d) {
        return d.getChildCount() == 1 && ((DefaultMutableTreeNode) d.getChildAt(0)).getUserObject() instanceof String;
    }

    private void load(DefaultMutableTreeNode d) {
        ObjectNode n = (ObjectNode) d.getUserObject();
        if (n.isLoaded()) {
            populate(d);
            return;
        }
        DefaultMutableTreeNode ph = (DefaultMutableTreeNode) d.getChildAt(0);
        if (LOADING.equals(ph.getUserObject())) return;
        ph.setUserObject(LOADING);
        model.nodeChanged(ph);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                n.ensureLoaded();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    populate(d);
                    if (d.getChildCount() == 0) {
                        model.nodeChanged(d);
                    } else {
                        tree.expandPath(new TreePath(d.getPath()));
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    // Meldung sichtbar lassen; erneutes Zu- und Aufklappen versucht es noch einmal
                    ph.setUserObject("Laden fehlgeschlagen – zum Wiederholen zu- und aufklappen");
                    model.nodeChanged(ph);
                }
            }
        }.execute();
    }

    private void style() {
        tree.setBackground(Ui.NAV);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(30);
        tree.putClientProperty("JTree.lineStyle", "None");
        tree.setToggleClickCount(0);
        javax.swing.plaf.basic.BasicTreeUI ui = new javax.swing.plaf.basic.BasicTreeUI() {
            @Override
            protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {
            }

            @Override
            protected void paintVerticalLine(Graphics g, JComponent c, int x, int top, int bottom) {
            }
        };
        tree.setUI(ui);
        ui.setExpandedIcon(new Arrow(true));
        ui.setCollapsedIcon(new Arrow(false));
        ui.setLeftChildIndent(8);
        ui.setRightChildIndent(14);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean exp,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(t, value, sel, exp, leaf, row, false);
                Object u = ((DefaultMutableTreeNode) value).getUserObject();
                setBorder(Ui.pad(3, 6, 3, 8));
                if (u instanceof ObjectNode n) {
                    setText(n.name());
                    setIcon(Ui.typeIcon(n.type(), 16));
                    setFont(Ui.font(Font.PLAIN, 14));
                    setToolTipText(Ui.typeLabel(n.type()));
                    setForeground(sel ? Color.WHITE : Ui.NAV_FG);
                } else {
                    setText(String.valueOf(u));
                    setIcon(null);
                    setFont(Ui.font(Font.ITALIC, 13));
                    setForeground(String.valueOf(u).startsWith("Laden") ? new Color(0xf87171) : Ui.MUTED);
                }
                return this;
            }

            {
                setBackgroundNonSelectionColor(Ui.NAV);
                setBackgroundSelectionColor(Ui.ACCENT);
                setBorderSelectionColor(Ui.ACCENT);
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (selected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.ACCENT);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                }
                boolean s = selected;
                selected = false; // Standard-Hintergrund nicht malen
                super.paintComponent(g);
                selected = s;
            }
        });
        tree.setToolTipText("");
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
    }

    /** Kleines Dreieck zum Auf-/Zuklappen, hell für den dunklen Hintergrund. */
    private record Arrow(boolean expanded) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Ui.MUTED);
            int[] xs, ys;
            if (expanded) {
                xs = new int[]{x + 2, x + 10, x + 6};
                ys = new int[]{y + 4, y + 4, y + 9};
            } else {
                xs = new int[]{x + 4, x + 9, x + 4};
                ys = new int[]{y + 2, y + 6, y + 10};
            }
            g2.fillPolygon(xs, ys, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
