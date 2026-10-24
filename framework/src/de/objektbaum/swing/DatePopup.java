package de.objektbaum.swing;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Kleiner Kalender zum Auswählen eines Datums (ohne Bibliotheken): Monat blättern, Tag anklicken.
 * Tastatur: Pfeiltasten = Tag/Woche, Bild auf/ab = Monat, Enter = übernehmen, Esc = schließen.
 */
final class DatePopup {

    private final JPopupMenu popup = new JPopupMenu();
    private final Consumer<LocalDate> chosen;
    private final JLabel title = Ui.label("", Font.BOLD, 14, Ui.FG);
    private final JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));
    private YearMonth month;
    private LocalDate focus;
    private final LocalDate initial;

    private DatePopup(LocalDate initial, Consumer<LocalDate> chosen) {
        this.initial = initial;
        this.focus = initial != null ? initial : LocalDate.now();
        this.month = YearMonth.from(focus);
        this.chosen = chosen;

        JButton prev = nav(Ui.sym("‹", "<"), "Voriger Monat", -1);
        JButton next = nav(Ui.sym("›", ">"), "Nächster Monat", 1);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);
        head.setBorder(Ui.pad(0, 0, 6, 0));

        days.setOpaque(false);

        JButton today = Ui.link("Heute", () -> pick(LocalDate.now()));
        today.setFont(Ui.font(Font.PLAIN, 13));
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.add(today, BorderLayout.WEST);
        foot.setBorder(Ui.pad(8, 4, 0, 0));

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Ui.PANEL);
        p.setBorder(Ui.pad(10, 10, 10, 10));
        p.add(head, BorderLayout.NORTH);
        p.add(days, BorderLayout.CENTER);
        p.add(foot, BorderLayout.SOUTH);
        popup.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        popup.setBackground(Ui.PANEL);
        popup.add(p);

        key(p, KeyEvent.VK_LEFT, () -> move(focus.minusDays(1)));
        key(p, KeyEvent.VK_RIGHT, () -> move(focus.plusDays(1)));
        key(p, KeyEvent.VK_UP, () -> move(focus.minusWeeks(1)));
        key(p, KeyEvent.VK_DOWN, () -> move(focus.plusWeeks(1)));
        key(p, KeyEvent.VK_PAGE_UP, () -> move(focus.minusMonths(1)));
        key(p, KeyEvent.VK_PAGE_DOWN, () -> move(focus.plusMonths(1)));
        key(p, KeyEvent.VK_ENTER, () -> pick(focus));
        key(p, KeyEvent.VK_ESCAPE, () -> popup.setVisible(false));
        fill();
    }

    /** Öffnet den Kalender unter der Komponente. initial = vorausgewählt (null = heute). */
    static void show(JComponent anchor, LocalDate initial, Consumer<LocalDate> chosen) {
        DatePopup d = new DatePopup(initial, chosen);
        d.popup.show(anchor, anchor.getWidth() - d.popup.getPreferredSize().width, anchor.getHeight() + 2);
        d.days.requestFocusInWindow();
    }

    private JButton nav(String text, String tip, int delta) {
        JButton b = Ui.button(text, false);
        b.putClientProperty("noBorder", Boolean.TRUE);
        b.setBorder(Ui.pad(2, 10, 2, 10));
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.addActionListener(e -> move(focus.plusMonths(delta)));
        return b;
    }

    private void key(JComponent c, int code, Runnable r) {
        String name = "datum-" + code;
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, 0), name);
        c.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void move(LocalDate d) {
        focus = d;
        month = YearMonth.from(d);
        fill();
    }

    private void pick(LocalDate d) {
        popup.setVisible(false);
        chosen.accept(d);
    }

    private void fill() {
        String m = month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN);
        title.setText(m + " " + month.getYear());
        days.removeAll();
        for (DayOfWeek w : DayOfWeek.values()) {
            JLabel l = Ui.label(w.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.GERMAN).replace(".", ""),
                    Font.BOLD, 12, Ui.MUTED);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            days.add(l);
        }
        int lead = month.atDay(1).getDayOfWeek().getValue() - 1; // Montag zuerst
        for (int i = 0; i < lead; i++) days.add(new JLabel());
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            boolean sel = date.equals(initial);
            JButton b = Ui.button(String.valueOf(d), sel);
            b.setFont(Ui.font(date.equals(today) ? Font.BOLD : Font.PLAIN, 13));
            b.setBorder(Ui.pad(4, 2, 4, 2));
            b.putClientProperty("arc", 6);
            if (!sel && !date.equals(focus)) b.putClientProperty("noBorder", Boolean.TRUE);
            if (date.getDayOfWeek().getValue() >= 6 && !sel) b.setForeground(Ui.MUTED);
            b.setToolTipText(de.objektbaum.DateField.GERMAN.format(date));
            b.getAccessibleContext().setAccessibleName(de.objektbaum.DateField.GERMAN.format(date));
            b.addActionListener(e -> pick(date));
            b.setPreferredSize(new java.awt.Dimension(Ui.px(34), Ui.px(28)));
            days.add(b);
        }
        days.revalidate();
        days.repaint();
        popup.pack();
    }
}
