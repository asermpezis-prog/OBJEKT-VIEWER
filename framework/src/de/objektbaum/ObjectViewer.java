package de.objektbaum;

import de.objektbaum.swing.SwingViewer;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Einstiegspunkt des Frameworks: Daten, Code-Teile und Konfigurationsordner zusammenführen,
 * dann die Browser- und/oder die Swing-Version starten.
 *
 * <pre>
 * ObjectViewer viewer = ObjectViewer.builder()
 *         .title("Meine Anwendung")
 *         .root(wurzelObjekt)
 *         .configDir(Path.of("config"))          // viewer.xml + templates/ – live editierbar
 *         .action("rateBerechnen", MeineAktionen::rate)
 *         .options("berater", node -> ladeBerater())
 *         .build();
 *
 * viewer.startWeb("localhost", 8080);            // Browser-Version
 * viewer.openSwing();                            // und/oder Swing-Fenster
 * </pre>
 *
 * Im Konfigurationsordner:
 * <ul>
 *   <li>{@code viewer.xml} – Objekttypen, Spalten der Baumtabelle, Ansichten</li>
 *   <li>{@code templates/page.html, style.css, app.js} – Aussehen der Browser-Version</li>
 *   <li>{@code swing.properties} – Aussehen der Swing-Version (Look and Feel, hell/dunkel, Schrift, Farben)</li>
 * </ul>
 * Beide werden bei Änderungen sofort neu gelesen. Fehlende Dateien: Es gelten die Standardwerte.
 */
public final class ObjectViewer {

    private volatile ObjectNode root;
    private final String codeTitle;
    private final List<TreeColumn> codeTreeColumns;
    private final ViewRegistry views;
    private final Bindings bindings;
    private final ConfigStore config;
    private final Templates templates;
    private final HtmlRenderer renderer;
    private final Path configDir;
    private final LoginHandler login;
    private final SearchHandler search;
    private final List<String> searchFields;
    private final NodeClickListener clickListener;
    private final java.util.function.Function<ObjectNode, NodeStyle> styler;
    private final List<TreeButton> codeTreeButtons;
    private volatile String user;

    /** Button unter der Baumtabelle: wirkt auf das ausgewählte Objekt. */
    public record TreeButton(String label, Button.Action action) {
        public TreeButton {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    private ObjectViewer(Builder b) {
        if (b.root == null && b.search == null) {
            throw new NullPointerException("root: Wurzelobjekt fehlt (oder .search(...) für eine Suchmaske angeben)");
        }
        this.root = b.root != null ? b.root : placeholderRoot();
        this.login = b.login;
        this.search = b.search;
        this.searchFields = List.copyOf(b.searchFields);
        this.clickListener = b.clickListener;
        this.styler = b.styler;
        this.codeTreeButtons = List.copyOf(b.treeButtons);
        this.codeTitle = b.title;
        this.codeTreeColumns = b.treeColumns;
        this.views = b.views;
        this.bindings = b.bindings;
        this.configDir = b.configDir == null ? null : b.configDir.toAbsolutePath().normalize();
        if (b.configDir != null) {
            Path dir = configDir;
            this.config = new ConfigStore(dir.resolve("viewer.xml"), bindings);
            this.templates = new Templates(dir.resolve("templates"));
        } else {
            this.config = null;
            this.templates = new Templates(null);
        }
        views.configured(() -> config == null ? java.util.Map.of() : config.current().views());
        this.renderer = new HtmlRenderer(root, this::title, views, this::treeColumns, templates, this::configError);
        this.renderer.styler(this::styleOf);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Titel: aus viewer.xml (title="…"), sonst aus dem Code. */
    public String title() {
        String t = config == null ? null : config.current().title();
        return t != null && !t.isBlank() ? t : codeTitle;
    }

    /** Spalten der Baumtabelle: aus viewer.xml, sonst aus dem Code. */
    public List<TreeColumn> treeColumns() {
        List<TreeColumn> c = config == null ? null : config.current().treeColumns();
        return c != null ? c : codeTreeColumns;
    }

    /** Aktuelle Fehlermeldung zu viewer.xml, oder null. */
    public String configError() {
        return config == null ? null : config.error();
    }

    /**
     * Ändert sich, sobald die Konfiguration neu geladen wurde oder ein Fehler auftritt/verschwindet
     * (damit die Swing-Version sich selbst aktualisieren kann).
     */
    public Object configStamp() {
        if (config == null) return null;
        return java.util.List.of(config.current(), String.valueOf(config.error()));
    }

    /** Platzhalter-Wurzel, solange noch kein Vorgang gelesen wurde. */
    public static ObjectNode placeholderRoot() {
        return new ObjectNode("Kein Vorgang geladen", ObjectType.GRUPPE).field("_platzhalter", Boolean.TRUE);
    }

    /** true für die Platzhalter-Wurzel aus {@link #placeholderRoot()}. */
    public static boolean isPlaceholder(ObjectNode node) {
        return node != null && Boolean.TRUE.equals(node.fields().get("_platzhalter"));
    }

    /** Anmeldung oder null (dann keine Anmeldemaske). */
    public LoginHandler login() {
        return login;
    }

    /** Suche (Vorgang lesen) oder null (dann keine Suchmaske). */
    public SearchHandler search() {
        return search;
    }

    /** Felder der Suchmaske, z. B. VorgangsId, VorgangsNummer. */
    public List<String> searchFields() {
        return searchFields;
    }

    /** Listener für Klicks auf Objekte oder null. */
    public NodeClickListener clickListener() {
        return clickListener;
    }

    /** Angemeldeter Benutzer oder null. */
    public String user() {
        return user;
    }

    /** Wird von der Anmeldemaske gesetzt (null = abgemeldet). */
    public void user(String name) {
        this.user = name;
    }

    /** Aussehen eines Objekts: eigenes ({@link ObjectNode#style()}), sonst Regel aus dem Builder, sonst null. */
    public NodeStyle styleOf(ObjectNode node) {
        NodeStyle s = node.style();
        if (s == null && styler != null) {
            try {
                s = styler.apply(node);
            } catch (RuntimeException e) {
                s = null;
            }
        }
        return s;
    }

    /** Buttons unter der Baumtabelle: aus viewer.xml, sonst aus dem Code. */
    public List<TreeButton> treeButtons() {
        List<TreeButton> b = config == null ? null : config.current().treeButtons();
        return b != null && !b.isEmpty() ? b : codeTreeButtons;
    }

    /** Konfigurationsordner (absolut) oder null. */
    public Path configDir() {
        return configDir;
    }

    /** Die aktuelle Wurzel (mit Suchmaske: der zuletzt gelesene Vorgang bzw. der Platzhalter). */
    public ObjectNode root() {
        return root;
    }

    /** Setzt die aktuelle Wurzel; die Swing-Version ruft das nach „Vorgang lesen“ und beim Abmelden auf. */
    public void root(ObjectNode newRoot) {
        this.root = Objects.requireNonNull(newRoot, "root");
    }

    public ViewRegistry views() {
        return views;
    }

    public Bindings bindings() {
        return bindings;
    }

    public Templates templates() {
        return templates;
    }

    public HtmlRenderer renderer() {
        return renderer;
    }

    /** Startet die Browser-Version. host "localhost" = nur dieser Rechner, "0.0.0.0" = aus dem Netzwerk. */
    public TreeServer startWeb(String host, int port) throws IOException {
        TreeServer server = new TreeServer(root, renderer);
        server.start(host, port);
        return server;
    }

    /**
     * Öffnet die Swing-Version (eigenes Fenster).
     *
     * @throws IllegalStateException wenn kein Bildschirm verfügbar ist
     */
    public void openSwing() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Kein Bildschirm verfügbar: Die Swing-Version braucht eine grafische Oberfläche.");
        }
        SwingUtilities.invokeLater(() -> new SwingViewer(this).open());
    }

    /** Baut einen {@link ObjectViewer}. */
    public static final class Builder {
        private ObjectNode root;
        private String title = "Objekt-Viewer";
        private Path configDir;
        private final ViewRegistry views = new ViewRegistry();
        private final Bindings bindings = new Bindings();
        private List<TreeColumn> treeColumns = HtmlRenderer.DEFAULT_TREE_COLUMNS;
        private LoginHandler login;
        private SearchHandler search;
        private List<String> searchFields = List.of("VorgangsId", "VorgangsNummer");
        private NodeClickListener clickListener;
        private java.util.function.Function<ObjectNode, NodeStyle> styler;
        private final List<TreeButton> treeButtons = new java.util.ArrayList<>();

        private Builder() {
        }

        /** Anmeldemaske (Benutzer + Passwort) vor dem Hauptfenster; die Prüfung übernimmt der Handler. */
        public Builder login(LoginHandler handler) {
            this.login = handler;
            return this;
        }

        /**
         * Suchmaske „Vorgang suchen“ mit Button „Vorgang lesen“. Das Ergebnis wird zur Wurzel des Baums;
         * {@link #root} ist dann nicht nötig.
         */
        public Builder search(SearchHandler handler) {
            this.search = handler;
            return this;
        }

        /** Felder der Suchmaske (Standard: VorgangsId, VorgangsNummer). */
        public Builder searchFields(String... names) {
            if (names.length == 0) throw new IllegalArgumentException("searchFields: mindestens ein Feld");
            this.searchFields = List.of(names);
            return this;
        }

        /** Wird bei jedem Klick auf ein Objekt aufgerufen; darf Kinder hinzufügen (Baum baut sich auf). */
        public Builder onNodeClick(NodeClickListener listener) {
            this.clickListener = listener;
            return this;
        }

        /** Regel für das Aussehen einzelner Objekte; null-Ergebnis = Standard. */
        public Builder nodeStyle(java.util.function.Function<ObjectNode, NodeStyle> rule) {
            this.styler = rule;
            return this;
        }

        /** Button unter der Baumtabelle, wirkt auf das ausgewählte Objekt (auch in viewer.xml: &lt;treetable&gt;&lt;button&gt;). */
        public Builder treeButton(String label, Button.Action action) {
            treeButtons.add(new TreeButton(label, action));
            return this;
        }

        /** Das oberste Objekt des Baums. */
        public Builder root(ObjectNode root) {
            this.root = root;
            return this;
        }

        /** Anwendungstitel (kann in viewer.xml mit title="…" überschrieben werden). */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /** Ordner mit viewer.xml und templates/. Ohne: nur Code-Ansichten und Standard-Vorlagen. */
        public Builder configDir(Path dir) {
            this.configDir = dir;
            return this;
        }

        /** Ansicht im Code (viewer.xml hat Vorrang, falls dort eine für den Typ steht). */
        public Builder view(ObjectType type, NodeView view) {
            views.register(type, view);
            return this;
        }

        /** Button-Aktion, in viewer.xml aufrufbar mit &lt;button action="name"/&gt;. */
        public Builder action(String name, Button.Action action) {
            bindings.action(name, action);
            return this;
        }

        /** Auswahlwerte, in viewer.xml nutzbar mit &lt;combo source="name"/&gt; bzw. &lt;list source="name"/&gt;. */
        public Builder options(String name, OptionSource source) {
            bindings.options(name, source);
            return this;
        }

        /** Spalten der Baumtabelle (viewer.xml hat Vorrang, falls dort &lt;treetable&gt; steht). */
        public Builder treeColumns(List<TreeColumn> columns) {
            this.treeColumns = columns == null || columns.isEmpty() ? HtmlRenderer.DEFAULT_TREE_COLUMNS : List.copyOf(columns);
            return this;
        }

        public ObjectViewer build() {
            return new ObjectViewer(this);
        }
    }
}
