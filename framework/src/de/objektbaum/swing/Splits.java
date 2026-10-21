package de.objektbaum.swing;

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Verschachtelte, verschiebbare Bereiche wie bei Frames. Startgrößen als relative Gewichte,
 * vom Benutzer geänderte Größen werden pro Schlüssel gespeichert (Java Preferences),
 * Doppelklick auf eine Trennlinie setzt alle Bereiche der Gruppe zurück.
 */
final class Splits {

    static final Preferences PREFS = Preferences.userRoot().node("de/objektbaum/viewer");

    private final String key;
    private final List<JSplitPane> panes = new ArrayList<>();
    private final List<Double> defaults = new ArrayList<>();

    /** @param key Schlüssel zum Speichern, z. B. "frames.Variante" */
    Splits(String key) {
        this.key = key;
    }

    /**
     * Verkettet die Komponenten mit Trennlinien.
     *
     * @param orientation {@link JSplitPane#HORIZONTAL_SPLIT} (nebeneinander) oder VERTICAL_SPLIT
     * @param weights     relative Größen, gleiche Anzahl wie comps
     */
    JComponent chain(List<? extends JComponent> comps, List<Double> weights, int orientation) {
        if (comps.size() == 1) {
            return comps.get(0);
        }
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        JComponent rest = chain(comps.subList(1, comps.size()), weights.subList(1, weights.size()), orientation);
        return split(orientation, comps.get(0), rest, weights.get(0) / total);
    }

    /** Eine einzelne Trennung zwischen a und b; weight = Anteil von a. */
    JSplitPane split(int orientation, JComponent a, JComponent b, double weight) {
        JSplitPane sp = new JSplitPane(orientation, true, a, b);
        sp.setUI(new FlatSplitUI());
        sp.setBorder(null);
        sp.setDividerSize(5);
        sp.setResizeWeight(weight);
        sp.setOpaque(false);
        int index = panes.size();
        panes.add(sp);
        defaults.add(weight);
        String prefKey = key + "." + index;

        boolean[] applied = {false};
        sp.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!applied[0] && size(sp) > 0) {
                    applied[0] = true;
                    double saved = PREFS.getDouble(prefKey, -1);
                    sp.setDividerLocation(saved > 0 && saved < 1 ? saved : weight);
                }
            }
        });
        sp.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (applied[0] && sp.getClientProperty("dragging") != null && size(sp) > sp.getDividerSize()) {
                PREFS.putDouble(prefKey, sp.getDividerLocation() / (double) (size(sp) - sp.getDividerSize()));
            }
        });

        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) sp.getUI()).getDivider();
        divider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sp.putClientProperty("dragging", Boolean.TRUE);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                sp.putClientProperty("dragging", null);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    reset();
                }
            }
        });
        // Tastatur: F8 wählt die Trennlinie, Pfeiltasten verschieben (Standard von JSplitPane) – Ergebnis speichern
        sp.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, e -> {
            if (applied[0] && sp.isFocusOwner() && size(sp) > sp.getDividerSize()) {
                PREFS.putDouble(prefKey, sp.getDividerLocation() / (double) (size(sp) - sp.getDividerSize()));
            }
        });
        return sp;
    }

    /** Alle Bereiche dieser Gruppe auf die Startgrößen zurücksetzen und Gespeichertes löschen. */
    void reset() {
        for (int i = 0; i < panes.size(); i++) {
            PREFS.remove(key + "." + i);
            panes.get(i).setDividerLocation(defaults.get(i));
        }
    }

    private static int size(JSplitPane sp) {
        return sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? sp.getWidth() : sp.getHeight();
    }

    /** Flache Trennlinie: grau, beim Darüberfahren und Ziehen blau. */
    static final class FlatSplitUI extends BasicSplitPaneUI {
        @Override
        public BasicSplitPaneDivider createDefaultDivider() {
            BasicSplitPaneDivider d = new BasicSplitPaneDivider(this) {
                private boolean hover;

                {
                    setBorder(null);
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            hover = true;
                            repaint();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            hover = false;
                            repaint();
                        }
                    });
                }

                @Override
                public void paint(Graphics g) {
                    boolean active = hover || splitPane.getClientProperty("dragging") != null || splitPane.isFocusOwner();
                    g.setColor(active ? Ui.ACCENT : Ui.LINE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            return d;
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            splitPane.setBackground(Color.WHITE);
        }
    }
}
