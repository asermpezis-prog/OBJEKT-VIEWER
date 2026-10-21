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
 * </ul>
 * Beide werden bei Änderungen sofort neu gelesen. Fehlende Dateien: Es gelten die Standardwerte.
 */
public final class ObjectViewer {

    private final ObjectNode root;
    private final String codeTitle;
    private final List<TreeColumn> codeTreeColumns;
    private final ViewRegistry views;
    private final Bindings bindings;
    private final ConfigStore config;
    private final Templates templates;
    private final HtmlRenderer renderer;

    private ObjectViewer(Builder b) {
        this.root = Objects.requireNonNull(b.root, "root: Wurzelobjekt fehlt");
        this.codeTitle = b.title;
        this.codeTreeColumns = b.treeColumns;
        this.views = b.views;
        this.bindings = b.bindings;
        if (b.configDir != null) {
            Path dir = b.configDir.toAbsolutePath().normalize();
            this.config = new ConfigStore(dir.resolve("viewer.xml"), bindings);
            this.templates = new Templates(dir.resolve("templates"));
        } else {
            this.config = null;
            this.templates = new Templates(null);
        }
        views.configured(() -> config == null ? java.util.Map.of() : config.current().views());
        this.renderer = new HtmlRenderer(root, this::title, views, this::treeColumns, templates, this::configError);
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

    public ObjectNode root() {
        return root;
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

        private Builder() {
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
