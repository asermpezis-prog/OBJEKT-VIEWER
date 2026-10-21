package de.objektbaum;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Erzeugt die HTML-Seite: Navigation (Baum oder Baumtabelle), Header und Main.
 */
public final class HtmlRenderer {

    /** Darstellung der Feld-Tabellen. */
    public enum Layout {
        /** Zwei Spalten: Key | Value */
        VERTICAL("v", "Key / Value"),
        /** Keys als Spaltenüberschriften, Values darunter */
        HORIZONTAL("h", "Keys als Spalten");

        final String code;
        final String label;

        Layout(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        public static Layout fromCode(String code) {
            return "h".equals(code) ? HORIZONTAL : VERTICAL;
        }
    }

    /** Meldung oben auf der Seite, z. B. nach einem Button-Klick. */
    public record Message(String text, boolean error) { }

    /** Platzhalter für ein Feld, das eine Ansicht nennt, das Objekt aber nicht hat. */
    private static final Object MISSING = new Object();

    public static final List<TreeColumn> DEFAULT_TREE_COLUMNS =
            List.of(TreeColumn.name("Objektname"), TreeColumn.type("Typ"));

    private final ObjectNode root;
    private final String title;
    private final ViewRegistry views;
    private final List<TreeColumn> treeColumns;

    public HtmlRenderer(ObjectNode root, String title) {
        this(root, title, new ViewRegistry(), DEFAULT_TREE_COLUMNS);
    }

    public HtmlRenderer(ObjectNode root, String title, ViewRegistry views) {
        this(root, title, views, DEFAULT_TREE_COLUMNS);
    }

    public HtmlRenderer(ObjectNode root, String title, ViewRegistry views, List<TreeColumn> treeColumns) {
        this.root = root;
        this.title = title;
        this.views = views == null ? new ViewRegistry() : views;
        this.treeColumns = treeColumns == null || treeColumns.isEmpty() ? DEFAULT_TREE_COLUMNS : List.copyOf(treeColumns);
        if (this.treeColumns.stream().noneMatch(c -> c.kind() == TreeColumn.Kind.NAME)) {
            throw new IllegalArgumentException("Die Baumtabelle braucht eine Spalte TreeColumn.name(...)");
        }
    }

    public ViewRegistry views() {
        return views;
    }

    public String render(ObjectNode selected, Layout layout) {
        return render(selected, layout, null);
    }

    public String render(ObjectNode selected, Layout layout, Message message) {
        // Kinder des gewählten Objekts jetzt laden (lazy), Fehler werden im Main angezeigt.
        String loadError = null;
        try {
            selected.ensureLoaded();
        } catch (ChildLoadException e) {
            loadError = e.getMessage();
        }

        StringBuilder sb = new StringBuilder(16384);
        sb.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
          .append("<title>").append(esc(selected.name())).append(" – ").append(esc(title)).append("</title>\n")
          .append("<style>").append(CSS).append("</style>\n")
          // Baum-Modus vor dem ersten Zeichnen setzen (kein Flackern)
          .append("<script>try{if(localStorage.getItem('objektviewer.mode')==='table')")
          .append("document.documentElement.classList.add('mode-table')}catch(e){}</script>\n")
          .append("</head>\n<body>\n<div class=\"page\">\n");

        // ---------- Navigation (Baum) ----------
        sb.append("<nav class=\"nav\">\n<div class=\"brand\">").append(esc(title)).append("</div>\n<ul class=\"tree\">\n");
        renderNavNode(sb, root, selected, layout);
        sb.append("</ul>\n</nav>\n");

        // ---------- Header ----------
        NodeView view = views.resolve(selected);
        renderHeader(sb, selected, view, layout, message);

        // ---------- Main ----------
        sb.append("<main class=\"main\">\n");
        renderTreeTable(sb, selected, layout);
        List<ObjectNode> kids = selected.loadedChildren();
        if (view != null) {
            renderFrames(sb, selected, view, layout);
            if (view.showChildren()) {
                renderChildrenSection(sb, kids, loadError, selected, layout);
            }
        } else {
            sb.append("<section>\n<h2>Felder</h2>\n");
            if (selected.fields().isEmpty()) {
                sb.append("<p class=\"empty\">Dieses Objekt hat keine Felder.</p>\n");
            } else if (layout == Layout.VERTICAL) {
                renderVertical(sb, selected.fields());
            } else {
                renderHorizontal(sb, List.of(selected), false, layout);
            }
            sb.append("</section>\n");
            renderChildrenSection(sb, kids, loadError, selected, layout);
        }
        sb.append("</main>\n</div>\n<script>").append(JS).append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    // ----- Header -----

    private void renderHeader(StringBuilder sb, ObjectNode selected, NodeView view, Layout layout, Message message) {
        sb.append("<header class=\"header\">\n<div class=\"crumbs\">");
        List<ObjectNode> path = selected.path();
        for (int i = 0; i < path.size(); i++) {
            ObjectNode n = path.get(i);
            if (i > 0) sb.append("<span class=\"sep\">/</span>");
            if (n == selected) {
                sb.append("<span>").append(esc(n.name())).append("</span>");
            } else {
                sb.append(link(n, layout, esc(n.name())));
            }
        }
        sb.append("</div>\n<div class=\"head-row\">\n<h1>").append(esc(selected.name()))
          .append(" ").append(badge(selected.type())).append("</h1>\n<div class=\"actions\">");
        if (selected.isLazy()) {
            sb.append("<a class=\"btn\" href=\"").append(href(selected, layout)).append("&amp;reload=1\" ")
              .append("title=\"Unterobjekte verwerfen und neu laden\">↻ Neu laden</a>");
        }
        sb.append("<div class=\"toggle\" role=\"group\" aria-label=\"Darstellung des Baums\">")
          .append("<button type=\"button\" data-mode=\"nav\">Baum</button>")
          .append("<button type=\"button\" data-mode=\"table\">Baumtabelle</button></div>");
        sb.append("<div class=\"toggle\" role=\"group\" aria-label=\"Tabellen-Layout\">");
        for (Layout l : Layout.values()) {
            sb.append("<a href=\"").append(href(selected, l)).append("\"")
              .append(l == layout ? " class=\"on\" aria-current=\"true\"" : "")
              .append(">").append(l.label).append("</a>");
        }
        sb.append("</div>\n</div>\n</div>\n<div class=\"meta\">")
          .append(selected.fields().size()).append(" Felder · ")
          .append(selected.isLoaded() ? selected.loadedChildren().size() + " Unterobjekte" : "Unterobjekte nicht geladen")
          .append(selected.isLazy() ? " · <span class=\"lazy-tag\">lazy</span>" : "");
        if (view != null) {
            sb.append(" · Ansicht: ").append(esc(view.name()));
        }
        sb.append("</div>\n");
        if (message != null && message.text() != null && !message.text().isBlank()) {
            sb.append("<div class=\"msg").append(message.error() ? " err" : "").append("\" role=\"")
              .append(message.error() ? "alert" : "status").append("\">").append(esc(message.text())).append("</div>\n");
        }
        sb.append("</header>\n");
    }

    // ----- Unterobjekte -----

    private void renderChildrenSection(StringBuilder sb, List<ObjectNode> kids, String loadError,
                                       ObjectNode selected, Layout layout) {
        sb.append("<section>\n<h2>Unterobjekte</h2>\n");
        if (loadError != null) {
            sb.append("<p class=\"error\">").append(esc(loadError)).append(" <a href=\"")
              .append(href(selected, layout)).append("\">Erneut versuchen</a></p>\n");
        } else if (kids.isEmpty()) {
            sb.append("<p class=\"empty\">Keine Unterobjekte.</p>\n");
        } else if (layout == Layout.HORIZONTAL) {
            renderHorizontal(sb, kids, true, layout);
        } else {
            for (ObjectNode c : kids) {
                sb.append("<div class=\"card\">\n<h3>").append(link(c, layout, esc(c.name())))
                  .append(" ").append(badge(c.type())).append(" <small>").append(childInfo(c)).append("</small></h3>\n");
                if (c.fields().isEmpty()) {
                    sb.append("<p class=\"empty\">Keine Felder.</p>\n");
                } else {
                    renderVertical(sb, c.fields());
                }
                sb.append("</div>\n");
            }
        }
        sb.append("</section>\n");
    }

    // ----- Frames (Ansicht mit Spalten und Segmenten) -----

    private void renderFrames(StringBuilder sb, ObjectNode node, NodeView view, Layout pageLayout) {
        Map<String, Object> fields = node.fields();
        boolean form = view.isInteractive();
        String tag = form ? "form" : "div";
        sb.append('<').append(tag).append(" class=\"frames\" data-view=\"").append(esc(view.name())).append('"');
        if (form) {
            sb.append(" method=\"post\" action=\"/action\"");
        }
        sb.append(">\n");
        if (form) {
            sb.append("<input type=\"hidden\" name=\"_id\" value=\"").append(node.id()).append("\">")
              .append("<input type=\"hidden\" name=\"_layout\" value=\"").append(pageLayout.code).append("\">")
              // Enter in einem Textfeld = nur speichern, keine Button-Aktion
              .append("<button type=\"submit\" name=\"_btn\" value=\"\" hidden tabindex=\"-1\"></button>\n");
        }
        List<Column> cols = view.columns();
        for (int ci = 0; ci < cols.size(); ci++) {
            Column col = cols.get(ci);
            if (ci > 0) {
                sb.append("<div class=\"split-v\" role=\"separator\" aria-orientation=\"vertical\" tabindex=\"0\" ")
                  .append("title=\"Ziehen zum Ändern der Breite, Doppelklick setzt zurück\"></div>\n");
            }
            sb.append("<div class=\"fcol\" style=\"flex-grow:").append(num(col.width()))
              .append("\" data-grow=\"").append(num(col.width())).append("\">\n");
            List<Segment> segs = col.segments();
            for (int si = 0; si < segs.size(); si++) {
                Segment seg = segs.get(si);
                if (si > 0) {
                    sb.append("<div class=\"split-h\" role=\"separator\" aria-orientation=\"horizontal\" tabindex=\"0\" ")
                      .append("title=\"Ziehen zum Ändern der Höhe, Doppelklick setzt zurück\"></div>\n");
                }
                sb.append("<section class=\"fseg\" style=\"flex-grow:").append(num(seg.height()))
                  .append("\" data-grow=\"").append(num(seg.height())).append("\">\n")
                  .append("<div class=\"fseg-head\">").append(esc(seg.title())).append("</div>\n<div class=\"fseg-body\">\n");
                renderElements(sb, node, fields, view, seg, ci, si, pageLayout);
                sb.append("</div>\n</section>\n");
            }
            sb.append("</div>\n");
        }
        sb.append("</").append(tag).append(">\n");
    }

    /** Elemente eines Segments; aufeinanderfolgende Eingaben bzw. Buttons werden gruppiert. */
    private void renderElements(StringBuilder sb, ObjectNode node, Map<String, Object> fields, NodeView view,
                                Segment seg, int ci, int si, Layout pageLayout) {
        List<Element> els = seg.elements();
        String open = null; // "grid" oder "btns"
        boolean firstButton = true;
        for (int ei = 0; ei < els.size(); ei++) {
            Element el = els.get(ei);
            String group = el instanceof Element.Input ? "grid" : el instanceof Button ? "btns" : null;
            if (!Objects.equals(group, open)) {
                if (open != null) sb.append("</div>\n");
                if ("grid".equals(group)) sb.append("<div class=\"form-grid\">\n");
                if ("btns".equals(group)) sb.append("<div class=\"btn-row\">\n");
                open = group;
            }
            String id = "f-" + NodeView.elementId(ci, si, ei);
            if (el instanceof FieldTable t) {
                Map<String, Object> shown = new LinkedHashMap<>();
                for (String k : view.keysFor(t, fields)) {
                    shown.put(k, fields.containsKey(k) ? fields.get(k) : MISSING);
                }
                Layout l = t.layout() != null ? t.layout() : seg.layout() != null ? seg.layout() : pageLayout;
                if (shown.isEmpty()) {
                    sb.append("<p class=\"empty\">Keine Felder.</p>\n");
                } else if (l == Layout.VERTICAL) {
                    renderVertical(sb, shown);
                } else {
                    renderSingleRow(sb, shown);
                }
            } else if (el instanceof TextField tf) {
                renderTextField(sb, id, tf, fields.get(tf.key()));
            } else if (el instanceof ComboBox cb) {
                renderComboBox(sb, id, cb, fields.get(cb.key()), cb.options().options(node));
            } else if (el instanceof ComboList cl) {
                renderComboList(sb, id, cl, fields.get(cl.key()), cl.options().options(node));
            } else if (el instanceof Button b) {
                sb.append("<button type=\"submit\" class=\"pbtn").append(firstButton ? " primary" : "")
                  .append("\" name=\"_btn\" value=\"").append(NodeView.elementId(ci, si, ei)).append("\">")
                  .append(esc(b.label())).append("</button>\n");
                firstButton = false;
            }
        }
        if (open != null) sb.append("</div>\n");
    }

    private void renderTextField(StringBuilder sb, String id, TextField tf, Object value) {
        String v = value == null ? "" : text(value);
        sb.append("<label for=\"").append(id).append("\">").append(esc(tf.label())).append("</label>");
        if (tf.rows() > 1) {
            sb.append("<textarea id=\"").append(id).append("\" rows=\"").append(tf.rows()).append('"');
            sb.append(tf.editable() ? " name=\"" + esc(tf.key()) + "\"" : " readonly");
            sb.append(">").append(esc(v)).append("</textarea>\n");
        } else {
            sb.append("<input type=\"text\" id=\"").append(id).append("\" value=\"").append(esc(v)).append('"');
            sb.append(tf.editable() ? " name=\"" + esc(tf.key()) + "\"" : " readonly");
            sb.append(">\n");
        }
    }

    private void renderComboBox(StringBuilder sb, String id, ComboBox cb, Object value, List<String> options) {
        String v = value == null ? "" : text(value);
        sb.append("<label for=\"").append(id).append("\">").append(esc(cb.label())).append("</label>");
        if (cb.allowFreeInput()) {
            sb.append("<div><input type=\"text\" id=\"").append(id).append("\" list=\"").append(id).append("-dl\" value=\"")
              .append(esc(v)).append('"').append(cb.editable() ? " name=\"" + esc(cb.key()) + "\"" : " readonly")
              .append("><datalist id=\"").append(id).append("-dl\">");
            for (String o : options) sb.append("<option value=\"").append(esc(o)).append("\">");
            sb.append("</datalist></div>\n");
            return;
        }
        sb.append("<select id=\"").append(id).append('"')
          .append(cb.editable() ? " name=\"" + esc(cb.key()) + "\"" : " disabled").append(">");
        sb.append("<option value=\"\"").append(v.isEmpty() ? " selected" : "").append(">– bitte wählen –</option>");
        boolean found = v.isEmpty();
        for (String o : options) {
            boolean sel = o.equals(v);
            found |= sel;
            sb.append("<option").append(sel ? " selected" : "").append(">").append(esc(o)).append("</option>");
        }
        if (!found) {
            // aktueller Wert steht nicht (mehr) in der Liste: trotzdem anzeigen, damit er nicht verloren geht
            sb.append("<option selected>").append(esc(v)).append("</option>");
        }
        sb.append("</select>\n");
    }

    private void renderComboList(StringBuilder sb, String id, ComboList cl, Object value, List<String> options) {
        Set<String> current = new LinkedHashSet<>();
        if (value instanceof Collection<?> c) {
            for (Object o : c) current.add(String.valueOf(o));
        } else if (value != null && !String.valueOf(value).isEmpty()) {
            current.add(String.valueOf(value));
        }
        sb.append("<label for=\"").append(id).append("\">").append(esc(cl.label()))
          .append(cl.multiSelect() && cl.editable() ? "<small>Mehrfachauswahl: Strg/Cmd + Klick</small>" : "")
          .append("</label>");
        sb.append("<select id=\"").append(id).append("\" size=\"").append(cl.rows()).append('"')
          .append(cl.multiSelect() ? " multiple" : "")
          .append(cl.editable() ? " name=\"" + esc(cl.key()) + "\"" : " disabled").append(">");
        Set<String> rest = new LinkedHashSet<>(current);
        for (String o : options) {
            sb.append("<option").append(current.contains(o) ? " selected" : "").append(">").append(esc(o)).append("</option>");
            rest.remove(o);
        }
        for (String o : rest) {
            sb.append("<option selected>").append(esc(o)).append("</option>");
        }
        sb.append("</select>\n");
    }

    /** Keys als Spalten, eine Zeile mit den Values. */
    private void renderSingleRow(StringBuilder sb, Map<String, Object> fields) {
        sb.append("<div class=\"scroll\"><table class=\"grid\">\n<thead><tr>");
        for (String k : fields.keySet()) sb.append("<th>").append(esc(k)).append("</th>");
        sb.append("</tr></thead>\n<tbody>\n<tr>");
        for (Object v : fields.values()) sb.append("<td>").append(value(v)).append("</td>");
        sb.append("</tr>\n</tbody>\n</table></div>\n");
    }

    // ----- Baumtabelle -----

    private void renderTreeTable(StringBuilder sb, ObjectNode selected, Layout layout) {
        sb.append("<section class=\"tt-panel\" aria-label=\"Baumtabelle\">\n<div class=\"tt-tools\">")
          .append("<button type=\"button\" class=\"tbtn\" data-tt=\"expand\" title=\"Alle geladenen aufklappen\">⊞ Alle auf</button>")
          .append("<button type=\"button\" class=\"tbtn\" data-tt=\"collapse\" title=\"Alle zuklappen\">⊟ Alle zu</button>")
          .append("<button type=\"button\" class=\"tbtn\" data-tt=\"clear\" title=\"Filter zurücksetzen\">✕ Filter</button>")
          .append("<span class=\"tt-count\" aria-live=\"polite\"></span>")
          .append("<span class=\"tt-hint\">Filter: Text = enthält, * und ? als Platzhalter</span></div>\n")
          .append("<div class=\"tt-scroll\">\n<table class=\"tt\" data-sel=\"").append(selected.id())
          .append("\" data-layout=\"").append(layout.code).append("\">\n<thead><tr><th class=\"tt-mark\"></th>");
        for (int i = 0; i < treeColumns.size(); i++) {
            sb.append("<th><div class=\"tt-h\"><span>").append(esc(treeColumns.get(i).header()))
              .append("</span><button type=\"button\" class=\"tt-sort\" data-col=\"").append(i)
              .append("\" title=\"Sortieren\" aria-label=\"Nach ").append(esc(treeColumns.get(i).header()))
              .append(" sortieren\">⇅</button></div></th>");
        }
        sb.append("</tr>\n<tr class=\"tt-filter\"><th class=\"tt-mark\"></th>");
        for (int i = 0; i < treeColumns.size(); i++) {
            sb.append("<th><input type=\"text\" value=\"*\" data-col=\"").append(i).append("\" aria-label=\"Filter ")
              .append(esc(treeColumns.get(i).header())).append("\" spellcheck=\"false\"></th>");
        }
        sb.append("</tr></thead>\n<tbody>\n");
        renderTreeRow(sb, root, 0, selected, layout, true);
        sb.append("</tbody>\n</table>\n</div>\n</section>\n");
    }

    /**
     * Zeilen der Kinder eines Knotens für die Baumtabelle (per fetch nachgeladen).
     * Löst das Laden der Kinder aus.
     *
     * @throws ChildLoadException wenn der Loader scheitert
     */
    public String renderTreeRows(ObjectNode node, ObjectNode selected, Layout layout) {
        StringBuilder sb = new StringBuilder(2048);
        int depth = node.path().size();
        for (ObjectNode c : node.children()) {
            renderTreeRow(sb, c, depth, selected, layout, true);
        }
        return sb.toString();
    }

    private void renderTreeRow(StringBuilder sb, ObjectNode node, int depth, ObjectNode selected,
                               Layout layout, boolean visible) {
        boolean isSel = node == selected;
        boolean lazy = !node.isLoaded();
        List<ObjectNode> kids = node.loadedChildren();
        boolean hasKids = lazy || !kids.isEmpty();
        boolean open = !lazy && !kids.isEmpty() && (node.parent() == null || node.isAncestorOf(selected));

        sb.append("<tr data-id=\"").append(node.id()).append("\" data-pid=\"")
          .append(node.parent() == null ? "" : String.valueOf(node.parent().id())).append('"');
        if (lazy) sb.append(" data-lazy=\"1\"");
        if (hasKids) sb.append(" data-open=\"").append(open ? 1 : 0).append('"');
        if (isSel) sb.append(" class=\"sel\" aria-selected=\"true\"");
        if (!visible) sb.append(" hidden");
        sb.append("><td class=\"tt-mark\">").append(isSel ? "✔" : "").append("</td>");
        for (TreeColumn col : treeColumns) {
            String v = col.valueOf(node);
            sb.append("<td data-v=\"").append(esc(v)).append('"');
            if (col.kind() == TreeColumn.Kind.NAME) {
                sb.append(" class=\"tt-name\" style=\"padding-left:").append(8 + depth * 18).append("px\">");
                if (hasKids) {
                    sb.append("<button type=\"button\" class=\"tt-tog\" aria-label=\"")
                      .append(open ? "Zuklappen" : "Aufklappen").append("\">").append(open ? "−" : "+").append("</button>");
                } else {
                    sb.append("<span class=\"tt-sp\"></span>");
                }
                sb.append("<a href=\"").append(href(node, layout)).append("\"><span class=\"ico\">")
                  .append(node.type().icon()).append("</span>").append(esc(v)).append("</a>");
            } else {
                sb.append('>').append(esc(v));
            }
            sb.append("</td>");
        }
        sb.append("</tr>\n");
        for (ObjectNode c : kids) {
            renderTreeRow(sb, c, depth + 1, selected, layout, visible && open);
        }
    }

    // ----- Navigation (Baum) -----

    /**
     * HTML-Fragment mit den Kindern eines Knotens für die Navigation (wird beim Aufklappen
     * eines lazy Knotens per fetch nachgeladen). Löst das Laden der Kinder aus.
     */
    public String renderNavChildren(ObjectNode node, ObjectNode selected, Layout layout) {
        StringBuilder sb = new StringBuilder(1024);
        try {
            List<ObjectNode> kids = node.children();
            if (kids.isEmpty()) {
                sb.append("<li class=\"note\">keine Unterobjekte</li>\n");
            }
            for (ObjectNode c : kids) {
                renderNavNode(sb, c, selected, layout);
            }
        } catch (ChildLoadException e) {
            sb.append("<li class=\"note err\">Laden fehlgeschlagen</li>\n");
        }
        return sb.toString();
    }

    /** Rendert einen Knoten. Löst selbst kein Laden aus: ungeladene Knoten werden aufklappbar
     *  dargestellt und holen ihre Kinder erst beim Aufklappen. */
    private void renderNavNode(StringBuilder sb, ObjectNode node, ObjectNode selected, Layout layout) {
        String cls = node == selected ? " class=\"active\"" : "";
        sb.append("<li>");
        if (!node.isLoaded()) {
            sb.append("<details class=\"lazy\" data-id=\"").append(node.id()).append("\"><summary>")
              .append(navLink(node, layout, cls)).append("</summary>\n<ul></ul>\n</details>");
        } else if (node.loadedChildren().isEmpty()) {
            sb.append("<span class=\"leaf\"></span>").append(navLink(node, layout, cls));
        } else {
            boolean open = node.parent() == null || node.isAncestorOf(selected);
            sb.append("<details").append(open ? " open" : "").append("><summary>")
              .append(navLink(node, layout, cls)).append("</summary>\n<ul>\n");
            for (ObjectNode c : node.loadedChildren()) {
                renderNavNode(sb, c, selected, layout);
            }
            sb.append("</ul>\n</details>");
        }
        sb.append("</li>\n");
    }

    private String navLink(ObjectNode n, Layout layout, String cls) {
        return "<a href=\"" + href(n, layout) + "\"" + cls + " title=\"" + esc(typeLabel(n.type())) + "\">"
                + "<span class=\"ico\">" + n.type().icon() + "</span>" + esc(n.name()) + "</a>";
    }

    /** Info zu den Kindern eines Unterobjekts, ohne sie zu laden. */
    private static String childInfo(ObjectNode c) {
        if (!c.isLoaded()) return "Unterobjekte werden bei Bedarf geladen";
        int n = c.loadedChildren().size();
        return n == 1 ? "1 Unterobjekt" : n + " Unterobjekte";
    }

    // ----- Tabellen -----

    /** Zwei Spalten: Key | Value */
    private void renderVertical(StringBuilder sb, Map<String, Object> fields) {
        sb.append("<div class=\"scroll\"><table class=\"kv\">\n<thead><tr><th>Key</th><th>Value</th></tr></thead>\n<tbody>\n");
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            sb.append("<tr><th scope=\"row\">").append(esc(e.getKey())).append("</th><td>")
              .append(value(e.getValue())).append("</td></tr>\n");
        }
        sb.append("</tbody>\n</table></div>\n");
    }

    /**
     * Keys als Spalten, eine Zeile pro Objekt. Bei mehreren Objekten wird die
     * Vereinigung aller Keys verwendet; fehlende Werte bleiben leer.
     */
    private void renderHorizontal(StringBuilder sb, List<ObjectNode> nodes, boolean withName, Layout layout) {
        Set<String> keys = new LinkedHashSet<>();
        for (ObjectNode n : nodes) keys.addAll(n.fields().keySet());

        sb.append("<div class=\"scroll\"><table class=\"grid\">\n<thead><tr>");
        if (withName) sb.append("<th>Objekt</th><th>Typ</th>");
        for (String k : keys) sb.append("<th>").append(esc(k)).append("</th>");
        sb.append("</tr></thead>\n<tbody>\n");
        for (ObjectNode n : nodes) {
            Map<String, Object> f = n.fields();
            sb.append("<tr>");
            if (withName) {
                sb.append("<th scope=\"row\">").append(link(n, layout, esc(n.name()))).append("</th>")
                  .append("<td>").append(badge(n.type())).append("</td>");
            }
            for (String k : keys) {
                sb.append("<td>").append(f.containsKey(k) ? value(f.get(k)) : "").append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table></div>\n");
    }

    // ----- Hilfen -----

    /** Text eines Feldwerts: Listen werden mit Komma verbunden. */
    static String text(Object v) {
        if (v instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(v);
    }

    private static String value(Object v) {
        return v == MISSING ? "<span class=\"na\">–</span>" : esc(text(v));
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String typeLabel(ObjectType t) {
        return t.label().isEmpty() ? "Gruppe" : t.label();
    }

    private static String badge(ObjectType t) {
        return "<span class=\"badge t-" + t.name().toLowerCase() + "\">" + t.icon() + " " + esc(typeLabel(t)) + "</span>";
    }

    private static String link(ObjectNode n, Layout layout, String text) {
        return "<a href=\"" + href(n, layout) + "\">" + text + "</a>";
    }

    private static String href(ObjectNode n, Layout layout) {
        return "/?id=" + n.id() + "&amp;layout=" + layout.code;
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Skripte: Navigation nachladen, Frames, Umschalten Baum/Baumtabelle, Baumtabelle. */
    private static final String JS = """
        document.addEventListener('toggle', async (ev) => {
          const d = ev.target;
          if (!(d instanceof HTMLDetailsElement) || !d.open || !d.classList.contains('lazy') || d.dataset.loaded) return;
          d.dataset.loaded = '1';
          const ul = d.querySelector(':scope > ul');
          ul.innerHTML = '<li class="note">lädt …</li>';
          const p = new URLSearchParams(location.search);
          const url = '/nav?id=' + d.dataset.id + '&sel=' + (p.get('id') || '') + '&layout=' + (p.get('layout') || 'v');
          try {
            const r = await fetch(url);
            if (!r.ok) throw new Error(r.status);
            ul.innerHTML = await r.text();
            d.classList.remove('lazy');
          } catch (e) {
            ul.innerHTML = '<li class="note err">Laden fehlgeschlagen</li>';
            delete d.dataset.loaded;
          }
        }, true);

        // ---------- Frames: verschiebbare Trennlinien ----------
        (function () {
          const frames = document.querySelector('.frames');
          if (!frames) return;
          const key = 'objektviewer.frames.' + frames.dataset.view;
          const items = [...frames.querySelectorAll('.fcol, .fseg')];
          const wide = window.matchMedia('(min-width: 761px)');

          const fit = () => {
            if (!wide.matches) { frames.style.height = ''; return; }
            const top = frames.getBoundingClientRect().top + window.scrollY;
            frames.style.height = Math.max(document.documentElement.classList.contains('mode-table') ? 460 : 320, window.innerHeight - top - 24) + 'px';
          };
          fit();
          window.__fitFrames = fit;
          window.addEventListener('resize', fit);

          try {
            const saved = JSON.parse(localStorage.getItem(key) || 'null');
            if (Array.isArray(saved) && saved.length === items.length) {
              items.forEach((el, i) => { if (saved[i] > 0) el.style.flexGrow = saved[i]; });
            }
          } catch (e) { /* kein Speicher verfuegbar */ }

          const save = () => {
            try { localStorage.setItem(key, JSON.stringify(items.map(el => parseFloat(el.style.flexGrow) || 1))); }
            catch (e) { /* ignorieren */ }
          };

          // Verschiebt die Grenze zwischen a und b um delta Pixel.
          const resize = (sp, startA, startB, gA, gB, delta) => {
            const total = startA + startB, gt = gA + gB, min = Math.min(80, total / 2);
            const na = Math.max(min, Math.min(total - min, startA + delta));
            sp.previousElementSibling.style.flexGrow = (gt * na / total).toFixed(4);
            sp.nextElementSibling.style.flexGrow = (gt * (total - na) / total).toFixed(4);
          };
          const measure = (sp) => {
            const vert = sp.classList.contains('split-v');
            const a = sp.previousElementSibling, b = sp.nextElementSibling;
            const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
            return {
              vert,
              sa: vert ? ra.width : ra.height, sb: vert ? rb.width : rb.height,
              ga: parseFloat(getComputedStyle(a).flexGrow) || 1, gb: parseFloat(getComputedStyle(b).flexGrow) || 1
            };
          };

          frames.addEventListener('pointerdown', (ev) => {
            const sp = ev.target.closest('.split-v, .split-h');
            if (!sp || ev.button !== 0) return;
            ev.preventDefault();
            const m = measure(sp);
            const start = m.vert ? ev.clientX : ev.clientY;
            sp.setPointerCapture(ev.pointerId);
            sp.classList.add('drag');
            document.body.classList.add(m.vert ? 'drag-v' : 'drag-h');
            const move = (e) => resize(sp, m.sa, m.sb, m.ga, m.gb, (m.vert ? e.clientX : e.clientY) - start);
            const up = () => {
              sp.removeEventListener('pointermove', move);
              sp.classList.remove('drag');
              document.body.classList.remove('drag-v', 'drag-h');
              save();
            };
            sp.addEventListener('pointermove', move);
            sp.addEventListener('pointerup', up, { once: true });
            sp.addEventListener('pointercancel', up, { once: true });
          });

          // Tastatur: Pfeiltasten verschieben in 20-px-Schritten
          frames.addEventListener('keydown', (ev) => {
            const sp = ev.target.closest('.split-v, .split-h');
            if (!sp) return;
            const m = measure(sp);
            const dir = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[ev.key];
            if (!dir || (m.vert !== (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight'))) return;
            ev.preventDefault();
            resize(sp, m.sa, m.sb, m.ga, m.gb, dir * 20);
            save();
          });

          // Doppelklick auf eine Trennlinie: alle Groessen dieser Ansicht zuruecksetzen
          frames.addEventListener('dblclick', (ev) => {
            if (!ev.target.closest('.split-v, .split-h')) return;
            items.forEach(el => { el.style.flexGrow = el.dataset.grow; });
            try { localStorage.removeItem(key); } catch (e) { /* ignorieren */ }
          });
        })();

        // ---------- Umschalten Baum / Baumtabelle ----------
        (function () {
          const root = document.documentElement;
          const sync = () => document.querySelectorAll('[data-mode]').forEach(b => {
            const on = (b.dataset.mode === 'table') === root.classList.contains('mode-table');
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
          });
          document.querySelectorAll('[data-mode]').forEach(b => b.addEventListener('click', () => {
            const table = b.dataset.mode === 'table';
            root.classList.toggle('mode-table', table);
            try { localStorage.setItem('objektviewer.mode', table ? 'table' : 'nav'); } catch (e) { /* ignorieren */ }
            sync();
            window.dispatchEvent(new Event('resize'));
            if (table && window.__ttReveal) window.__ttReveal();
          }));
          sync();
        })();

        // ---------- Baumtabelle ----------
        (function () {
          const tt = document.querySelector('table.tt');
          if (!tt) return;
          const panel = tt.closest('.tt-panel');
          const scroller = panel.querySelector('.tt-scroll');
          const tbody = tt.tBodies[0];
          const filters = [...tt.querySelectorAll('.tt-filter input')];
          const count = panel.querySelector('.tt-count');
          const rows = () => [...tbody.rows];

          // Platzhalter * und ? ; ohne Platzhalter: "enthaelt"
          const glob = (p, t) => {
            let i = 0, j = 0, star = -1, mark = 0;
            while (j < t.length) {
              if (i < p.length && (p[i] === '?' || p[i] === t[j])) { i++; j++; }
              else if (i < p.length && p[i] === '*') { star = i++; mark = j; }
              else if (star >= 0) { i = star + 1; j = ++mark; }
              else return false;
            }
            while (i < p.length && p[i] === '*') i++;
            return i === p.length;
          };
          const isAll = (p) => { p = (p || '').trim(); return p === '' || p === '*'; };
          const match = (p, t) => {
            p = (p || '').trim().toLowerCase();
            if (isAll(p)) return true;
            t = (t || '').toLowerCase();
            return (p.includes('*') || p.includes('?')) ? glob(p, t) : t.includes(p);
          };

          const setTog = (r, open) => {
            r.dataset.open = open ? '1' : '0';
            const b = r.querySelector('.tt-tog');
            if (b) { b.textContent = open ? '−' : '+'; b.setAttribute('aria-label', open ? 'Zuklappen' : 'Aufklappen'); }
          };

          const update = () => {
            const pats = filters.map(f => f.value);
            const active = pats.some(p => !isAll(p));
            const all = rows();
            const byId = new Map(all.map(r => [r.dataset.id, r]));
            let keep = null;
            if (active) {
              keep = new Set();
              all.forEach(r => {
                const cells = [...r.cells].slice(1);
                if (pats.every((p, i) => match(p, cells[i] && cells[i].dataset.v))) {
                  for (let x = r; x && !keep.has(x.dataset.id); x = byId.get(x.dataset.pid)) keep.add(x.dataset.id);
                }
              });
            }
            const vis = new Map();
            let n = 0;
            all.forEach(r => {
              const pid = r.dataset.pid, parent = byId.get(pid);
              let show;
              if (keep) show = keep.has(r.dataset.id);
              else show = !parent || (vis.get(pid) && parent.dataset.open === '1');
              vis.set(r.dataset.id, show);
              r.hidden = !show;
              if (show) n++;
            });
            count.textContent = keep ? (n + ' Zeilen passen zum Filter') : '';
          };

          const load = async (r) => {
            if (r.dataset.loading) return;
            r.dataset.loading = '1';
            const b = r.querySelector('.tt-tog');
            b.textContent = '…';
            try {
              const res = await fetch('/rows?id=' + r.dataset.id + '&sel=' + tt.dataset.sel + '&layout=' + tt.dataset.layout);
              if (!res.ok) throw new Error(await res.text());
              const html = await res.text();
              delete r.dataset.lazy;
              if (html.trim() === '') {
                b.replaceWith(Object.assign(document.createElement('span'), { className: 'tt-sp' }));
                delete r.dataset.open;
              } else {
                r.insertAdjacentHTML('afterend', html);
                setTog(r, true);
              }
              update();
            } catch (e) {
              b.textContent = '+';
              r.title = 'Laden fehlgeschlagen: ' + e.message;
              r.classList.add('err');
            } finally {
              delete r.dataset.loading;
            }
          };

          tbody.addEventListener('click', (ev) => {
            const b = ev.target.closest('.tt-tog');
            if (!b) return;
            const r = b.closest('tr');
            if (r.dataset.lazy) { load(r); return; }
            setTog(r, r.dataset.open !== '1');
            update();
          });

          panel.querySelector('[data-tt=expand]').addEventListener('click', () => {
            rows().forEach(r => { if (r.dataset.open !== undefined && !r.dataset.lazy) setTog(r, true); });
            update();
          });
          panel.querySelector('[data-tt=collapse]').addEventListener('click', () => {
            rows().forEach(r => { if (r.dataset.open !== undefined) setTog(r, false); });
            update();
          });
          panel.querySelector('[data-tt=clear]').addEventListener('click', () => {
            filters.forEach(f => { f.value = '*'; });
            update();
          });
          filters.forEach(f => f.addEventListener('input', update));

          // Sortieren: Geschwister innerhalb jedes Elternknotens, Klick wechselt auf-/absteigend
          let sortCol = -1, dir = 1;
          tt.querySelectorAll('.tt-sort').forEach(btn => btn.addEventListener('click', () => {
            const col = +btn.dataset.col;
            dir = sortCol === col ? -dir : 1;
            sortCol = col;
            tt.querySelectorAll('.tt-sort').forEach(x => { x.classList.remove('on'); x.textContent = '⇅'; });
            btn.classList.add('on');
            btn.textContent = dir > 0 ? '▲' : '▼';
            const all = rows();
            const kids = new Map();
            all.forEach(r => { const k = r.dataset.pid; if (!kids.has(k)) kids.set(k, []); kids.get(k).push(r); });
            const val = (r) => (r.cells[col + 1] && r.cells[col + 1].dataset.v) || '';
            const cmp = (a, b) => dir * val(a).localeCompare(val(b), 'de', { numeric: true, sensitivity: 'base' });
            const frag = document.createDocumentFragment();
            const walk = (pid) => (kids.get(pid) || []).sort(cmp).forEach(r => { frag.appendChild(r); walk(r.dataset.id); });
            walk(all[0].dataset.pid);
            tbody.appendChild(frag);
            update();
          }));

          // gewaehlte Zeile in den sichtbaren Bereich holen
          window.__ttReveal = () => {
            const sel = tbody.querySelector('tr.sel');
            if (sel && panel.offsetParent) scroller.scrollTop = Math.max(0, sel.offsetTop - scroller.clientHeight / 2);
          };
          window.__ttReveal();

          // Groesse des Panels geaendert: Frames darunter anpassen
          if (window.ResizeObserver) {
            new ResizeObserver(() => { if (window.__fitFrames) window.__fitFrames(); }).observe(panel);
          }
        })();
        """;

    private static final String CSS = """
        :root{--bg:#f6f7f9;--panel:#fff;--nav:#1f2937;--nav-fg:#d1d5db;--nav-hi:#374151;--accent:#2563eb;
              --fg:#111827;--muted:#6b7280;--line:#e5e7eb;--zebra:#f9fafb}
        @media (prefers-color-scheme:dark){:root{--bg:#0f1115;--panel:#171a21;--nav:#0b0d11;--nav-fg:#c9ced6;
              --nav-hi:#232833;--accent:#60a5fa;--fg:#e5e7eb;--muted:#9aa3af;--line:#2a2f3a;--zebra:#1c2029}}
        *{box-sizing:border-box}
        body{margin:0;font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:var(--bg);color:var(--fg)}
        a{color:var(--accent);text-decoration:none} a:hover{text-decoration:underline}
        .page{background:linear-gradient(90deg,var(--nav) 0 280px,var(--bg) 280px);display:grid;grid-template-columns:280px 1fr;grid-template-rows:auto 1fr;min-height:100vh;
              grid-template-areas:"nav header" "nav main"}
        .nav{grid-area:nav;background:var(--nav);color:var(--nav-fg);padding:16px 12px;overflow:auto;
             position:sticky;top:0;height:100vh}
        .brand{font-weight:700;font-size:17px;color:#fff;padding:4px 8px 14px;border-bottom:1px solid var(--nav-hi);margin-bottom:10px}
        .tree,.tree ul{list-style:none;margin:0;padding:0} .tree ul{padding-left:14px}
        .tree li{margin:1px 0} .tree summary{cursor:pointer;list-style:none;padding-left:0;display:flex;align-items:flex-start}
        .tree summary::before{flex:none;margin-top:4px} .tree summary>a{min-width:0}
        .tree summary::-webkit-details-marker{display:none}
        .tree summary::before{content:"▸";display:inline-block;width:14px;color:var(--muted);transition:transform .15s}
        .tree details[open]>summary::before{transform:rotate(90deg)}
        .tree .leaf{display:inline-block;width:14px}
        .tree a{color:var(--nav-fg);display:inline-block;padding:3px 8px;border-radius:6px}
        .tree a:hover{background:var(--nav-hi);text-decoration:none}
        .tree a.active{background:var(--accent);color:#fff}
        .header{grid-area:header;background:var(--panel);border-bottom:1px solid var(--line);padding:16px 28px}
        .crumbs{color:var(--muted);font-size:13px} .sep{margin:0 6px}
        .head-row{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}
        h1{margin:4px 0;font-size:24px} .meta{color:var(--muted);font-size:13px}
        .toggle{display:inline-flex;border:1px solid var(--line);border-radius:8px;overflow:hidden}
        .toggle a{padding:6px 12px;color:var(--fg);font-size:13px} .toggle a:hover{text-decoration:none;background:var(--zebra)}
        .toggle a.on{background:var(--accent);color:#fff}
        .main{grid-area:main;padding:24px 28px;min-width:0}
        section{margin-bottom:28px} h2{font-size:16px;margin:0 0 10px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
        .card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:12px 16px;margin-bottom:14px}
        .card h3{margin:0 0 8px;font-size:16px} .card small{color:var(--muted);font-weight:400;margin-left:6px}
        .scroll{overflow-x:auto}
        table{border-collapse:collapse;background:var(--panel);border:1px solid var(--line);border-radius:8px;min-width:320px}
        th,td{padding:7px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;white-space:nowrap}
        thead th{background:var(--zebra);font-size:13px;color:var(--muted)}
        tbody tr:nth-child(even)>*{background:var(--zebra)}
        table.kv tbody th{font-weight:600;width:1%}
        .empty{color:var(--muted);font-style:italic}
        .error{color:#b91c1c;background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:10px 14px}
        .actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
        .btn{border:1px solid var(--line);border-radius:8px;padding:6px 12px;font-size:13px;color:var(--fg)}
        .btn:hover{text-decoration:none;background:var(--zebra)}
        .lazy-tag{border:1px dashed var(--muted);border-radius:4px;padding:0 5px;font-size:12px}
        .tree .note{color:var(--muted);font-size:13px;font-style:italic;padding:2px 8px 2px 22px}
        .tree .note.err{color:#f87171}
        .tree details.lazy>summary::before{color:var(--nav-fg)}
        .na{color:var(--muted)}
        .frames{display:flex;height:70vh;min-height:320px;margin-bottom:28px;border:1px solid var(--line);
                border-radius:10px;overflow:hidden;background:var(--panel)}
        .fcol{display:flex;flex-direction:column;flex:1 1 0;min-width:0;min-height:0}
        .fseg{display:flex;flex-direction:column;flex:1 1 0;min-height:0;margin:0}
        .fseg-head{flex:none;padding:7px 14px;font-size:12px;font-weight:600;letter-spacing:.04em;text-transform:uppercase;
                   color:var(--muted);background:var(--zebra);border-bottom:1px solid var(--line)}
        .fseg-body{flex:1;overflow:auto;padding:12px 14px}
        .fseg-body table{min-width:0;width:100%}
        .fseg-body td{white-space:normal;overflow-wrap:anywhere}
        .split-v,.split-h{flex:none;background:var(--line);position:relative;z-index:1;touch-action:none}
        .split-v{width:5px;cursor:col-resize} .split-h{height:5px;cursor:row-resize}
        .split-v::after,.split-h::after{content:"";position:absolute;inset:-4px}
        .split-v:hover,.split-h:hover,.split-v.drag,.split-h.drag,.split-v:focus-visible,.split-h:focus-visible{background:var(--accent);outline:none}
        body.drag-v,body.drag-v *{cursor:col-resize!important;user-select:none}
        body.drag-h,body.drag-h *{cursor:row-resize!important;user-select:none}
        .ico{display:inline-block;width:20px;text-align:center;margin-right:2px}
        .badge{display:inline-block;font-size:12px;font-weight:500;padding:2px 9px;border-radius:999px;
               border:1px solid var(--line);background:var(--zebra);color:var(--fg);vertical-align:middle;white-space:nowrap}
        .msg{margin-top:10px;padding:8px 12px;border-radius:8px;font-size:14px;background:#ecfdf5;border:1px solid #a7f3d0;color:#065f46}
        .msg.err{background:#fef2f2;border-color:#fecaca;color:#991b1b}
        .toggle button{font:inherit;font-size:13px;padding:6px 12px;border:0;background:none;color:var(--fg);cursor:pointer}
        .toggle button:hover{background:var(--zebra)} .toggle button.on{background:var(--accent);color:#fff}
        .toggle+.toggle{margin-left:0}
        /* Formular-Elemente in Segmenten */
        .fseg-body>*+*{margin-top:12px}
        .form-grid{display:grid;grid-template-columns:minmax(90px,max-content) minmax(0,1fr);gap:8px 12px;align-items:center}
        .form-grid label{font-size:13px;font-weight:600;color:var(--muted);align-self:start;padding-top:7px}
        .form-grid label small{display:block;font-weight:400;font-size:11px}
        .form-grid input,.form-grid select,.form-grid textarea{width:100%;font:inherit;font-size:14px;padding:6px 8px;
              border:1px solid var(--line);border-radius:6px;background:var(--panel);color:var(--fg)}
        .form-grid textarea{resize:vertical}
        .form-grid input[readonly],.form-grid textarea[readonly],.form-grid select:disabled{background:var(--zebra);color:var(--muted)}
        .form-grid select[size]{padding:2px}
        .form-grid input:focus,.form-grid select:focus,.form-grid textarea:focus{outline:2px solid var(--accent);outline-offset:-1px}
        .btn-row{display:flex;gap:8px;flex-wrap:wrap}
        .pbtn{font:inherit;font-size:14px;padding:7px 14px;border-radius:8px;border:1px solid var(--line);
              background:var(--panel);color:var(--fg);cursor:pointer}
        .pbtn:hover{background:var(--zebra)} .pbtn.primary{background:var(--accent);border-color:var(--accent);color:#fff}
        .pbtn.primary:hover{filter:brightness(1.08)}
        /* Baumtabelle */
        html:not(.mode-table) .tt-panel{display:none}
        html.mode-table .page{grid-template-columns:1fr;grid-template-areas:"header" "main";background:var(--bg)}
        html.mode-table .nav{display:none}
        .tt-panel{display:flex;flex-direction:column;height:42vh;min-height:160px;resize:vertical;overflow:hidden;
              border:1px solid var(--line);border-radius:10px;background:var(--panel);margin-bottom:20px}
        .tt-tools{flex:none;display:flex;gap:6px;align-items:center;flex-wrap:wrap;padding:6px 10px;border-bottom:1px solid var(--line);background:var(--zebra)}
        .tbtn{font:inherit;font-size:12px;padding:3px 9px;border:1px solid var(--line);border-radius:6px;background:var(--panel);color:var(--fg);cursor:pointer}
        .tbtn:hover{border-color:var(--accent)}
        .tt-count{font-size:12px;color:var(--accent);margin-left:6px} .tt-hint{font-size:12px;color:var(--muted);margin-left:auto}
        .tt-scroll{flex:1;overflow:auto}
        table.tt thead th:last-child{padding-right:14px}
        table.tt{width:100%;min-width:640px;border:0;border-radius:0}
        table.tt thead{position:sticky;top:0;z-index:2}
        table.tt th,table.tt td{padding:5px 10px}
        table.tt thead th{background:var(--zebra);color:var(--fg);font-size:13px;vertical-align:bottom}
        .tt-h{display:flex;align-items:center;gap:4px;justify-content:space-between}
        .tt-sort{border:0;background:none;cursor:pointer;color:var(--muted);font-size:13px;padding:0 4px}
        .tt-sort.on{color:var(--accent)}
        .tt-filter th{padding-top:0}
        .tt-filter input{width:100%;min-width:50px;padding:3px 6px;border:1px solid var(--line);border-radius:4px;
              background:var(--panel);color:var(--fg);font:inherit;font-size:13px;font-weight:400}
        .tt-mark{width:30px;text-align:center;font-weight:700}
        .tt-name a{color:var(--fg);font-weight:600} .tt-name a:hover{color:var(--accent)}
        .tt-tog{width:20px;height:20px;border:1px solid var(--muted);background:var(--panel);border-radius:3px;font-size:13px;
              line-height:16px;padding:0;cursor:pointer;margin-right:6px;color:var(--fg);vertical-align:middle}
        .tt-sp{display:inline-block;width:26px}
        table.tt tbody tr.sel>*{background:#fdf0b8}
        table.tt tbody tr.err .tt-tog{border-color:#dc2626;color:#dc2626}
        @media (prefers-color-scheme:dark){table.tt tbody tr.sel>*{background:#4a3f12}
              .msg{background:#0f2e22;border-color:#14532d;color:#86efac} .msg.err{background:#3b1414;border-color:#7f1d1d;color:#fca5a5}}
        @media (max-width:760px){.page{grid-template-columns:1fr;grid-template-areas:"nav" "header" "main"}
             .nav{position:static;height:auto;max-height:45vh} .header,.main{padding:16px}
             .page{background:var(--bg)}
             .frames{flex-direction:column;height:auto!important;min-height:0}
             .fcol,.fseg{flex:none!important} .split-v{display:none} .split-h{display:none}
             .fcol+.split-v+.fcol{border-top:1px solid var(--line)} .fseg+.split-h+.fseg{border-top:1px solid var(--line)}
             .form-grid{grid-template-columns:1fr} .tt-hint{display:none}}
        """;
}
