package de.objektbaum.swing;

import de.objektbaum.LoginHandler;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Anmeldemaske: Benutzer, Passwort, Button „Anmelden“. Die Prüfung macht der {@link LoginHandler}
 * der Anwendung – im Hintergrund; bei Fehler erscheint seine Meldung rot in der Maske.
 */
final class LoginPanel {

    private final JPanel root = new JPanel(new GridBagLayout());
    private final JTextField user = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final Ui.FilledLabel error = new Ui.FilledLabel("");
    private final JButton submit = Ui.button("Anmelden", true);
    private final LoginHandler handler;
    private final Consumer<String> success;
    private boolean busy;

    /**
     * @param title     Anwendungstitel über der Maske
     * @param lastUser  vorbelegter Benutzername (z. B. nach Abmelden) oder null
     * @param handler   Prüfung der Anwendung
     * @param success   wird im Event-Thread mit dem Benutzernamen aufgerufen, wenn die Anmeldung gelingt
     */
    LoginPanel(String title, String lastUser, LoginHandler handler, Consumer<String> success) {
        this.handler = handler;
        this.success = success;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Ui.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Ui.RoundBorder(Ui.LINE, null, 12, new Insets(0, 0, 0, 0)), Ui.pad(28, 32, 28, 32)));

        JLabel app = Ui.label(title, Font.BOLD, 22, Ui.FG);
        JLabel sub = Ui.label("Bitte anmelden", Font.PLAIN, 14, Ui.MUTED);
        add(card, app, 2);
        add(card, sub, 22);

        JLabel userLabel = Ui.label("Benutzer", Font.BOLD, 13, Ui.MUTED);
        JLabel pwLabel = Ui.label("Passwort", Font.BOLD, 13, Ui.MUTED);
        userLabel.setLabelFor(user);
        pwLabel.setLabelFor(password);
        style(user);
        style(password);
        user.setText(lastUser == null ? "" : lastUser);
        user.getAccessibleContext().setAccessibleName("Benutzer");
        password.getAccessibleContext().setAccessibleName("Passwort");
        add(card, userLabel, 4);
        add(card, user, 14);
        add(card, pwLabel, 4);
        add(card, password, 16);

        error.setForeground(Ui.ERR_FG);
        error.colors(Ui.ERR_BG, Ui.ERR_LINE);
        error.setBorder(Ui.pad(8, 12, 8, 12));
        error.setFont(Ui.font(Font.PLAIN, 13));
        error.setVisible(false);
        error.getAccessibleContext().setAccessibleDescription("Fehler bei der Anmeldung");
        add(card, error, 12);

        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.addActionListener(e -> submit());
        user.addActionListener(e -> password.requestFocusInWindow());   // Enter im Benutzerfeld: weiter zum Passwort
        password.addActionListener(e -> submit());                        // Enter im Passwort: anmelden
        card.add(submit);
        card.setMaximumSize(card.getPreferredSize());

        root.setBackground(Ui.BG);
        root.add(card);
    }

    JComponent component() {
        return root;
    }

    /** Fokus ins erste leere Feld. */
    void focus() {
        (user.getText().isBlank() ? user : password).requestFocusInWindow();
    }

    String userText() {
        return user.getText();
    }

    private static void add(JPanel card, JComponent c, int gapBelow) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JTextField) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        }
        card.add(c);
        card.add(Box.createVerticalStrut(gapBelow));
    }

    private static void style(JTextField t) {
        t.setFont(Ui.font(Font.PLAIN, 15));
        t.setBackground(Ui.PANEL);
        t.setForeground(Ui.FG);
        t.setCaretColor(Ui.FG);
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), Ui.pad(7, 10, 7, 10)));
    }

    private void showError(String text) {
        error.setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        error.setVisible(true);
        error.setMaximumSize(new Dimension(Integer.MAX_VALUE, error.getPreferredSize().height));
        root.revalidate();
        root.repaint();
    }

    private void submit() {
        if (busy) return;
        String name = user.getText().strip();
        char[] pw = password.getPassword();
        if (name.isEmpty()) {
            showError("Bitte Benutzer eingeben.");
            user.requestFocusInWindow();
            return;
        }
        if (pw.length == 0) {
            showError("Bitte Passwort eingeben.");
            password.requestFocusInWindow();
            return;
        }
        busy = true;
        submit.setEnabled(false);
        submit.setText("Anmelden" + Ui.sym(" …", " ..."));
        root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    handler.login(name, pw);
                } finally {
                    Arrays.fill(pw, '\0');
                }
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                submit.setEnabled(true);
                submit.setText("Anmelden");
                root.setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    password.setText("");
                    success.accept(name);
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause.getMessage();
                    showError(msg == null || msg.isBlank() ? "Anmeldung fehlgeschlagen." : msg);
                    password.setText("");
                    password.requestFocusInWindow();
                }
            }
        }.execute();
    }
}
