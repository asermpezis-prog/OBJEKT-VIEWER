package de.objektbaum;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
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
    private final Supplier<String> title;
    private final ViewRegistry views;
    private final Supplier<List<TreeColumn>> treeColumns;
    private final Templates templates;
    private final Supplier<String> configError;

    public HtmlRenderer(ObjectNode root, String title) {
        this(root, title, new ViewRegistry(), DEFAULT_TREE_COLUMNS);
    }

    public HtmlRenderer(ObjectNode root, String title, ViewRegistry views) {
        this(root, title, views, DEFAULT_TREE_COLUMNS);
    }

    public HtmlRenderer(ObjectNode root, String title, ViewRegistry views, List<TreeColumn> treeColumns) {
        this(root, () -> title, views, () -> treeColumns, new Templates(null), () -> null);
    }

    /**
     * @param title        Anwendungstitel (wird bei jeder Seite abgefragt, kann sich also ändern)
     * @param treeColumns  Spalten der Baumtabelle (wird bei jeder Seite abgefragt)
     * @param templates    Vorlagen page.html, style.css, app.js
     * @param configError  aktuelle Fehlermeldung zur Konfiguration oder null
     */
    public HtmlRenderer(ObjectNode root, Supplier<String> title, ViewRegistry views,
                        Supplier<List<TreeColumn>> treeColumns, Templates templates, Supplier<String> configError) {
        this.root = root;
        this.title = title;
        this.views = views == null ? new ViewRegistry() : views;
        this.treeColumns = treeColumns == null ? () -> DEFAULT_TREE_COLUMNS : treeColumns;
        this.templates = templates == null ? new Templates(null) : templates;
        this.configError = configError == null ? () -> null : configError;
    }

    public ViewRegistry views() {
        return views;
    }

    public Templates templates() {
        return templates;
    }

    /** Aktuelle Spalten der Baumtabelle; ohne Namensspalte gelten die Standardspalten. */
    private List<TreeColumn> columns() {
        List<TreeColumn> c = treeColumns.get();
        if (c == null || c.isEmpty() || c.stream().noneMatch(x -> x.kind() == TreeColumn.Kind.NAME)) {
            return DEFAULT_TREE_COLUMNS;
        }
        return c;
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
        String app = title.get();
        List<TreeColumn> cols = columns();
        NodeView view = views.resolve(selected);

        StringBuilder nav = new StringBuilder(4096).append("<ul class=\"tree\">\n");
        renderNavNode(nav, root, selected, layout);
        nav.append("</ul>");

        StringBuilder header = new StringBuilder(2048);
        renderHeader(header, selected, view, layout, message);

        StringBuilder tt = new StringBuilder(8192);
        renderTreeTable(tt, selected, layout, cols);

        StringBuilder content = new StringBuilder(8192);
        List<ObjectNode> kids = selected.loadedChildren();
        if (view != null) {
            renderFrames(content, selected, view, layout);
            if (view.showChildren()) {
                renderChildrenSection(content, kids, loadError, selected, layout);
            }
        } else {
            content.append("<section>\n<h2>Felder</h2>\n");
            if (selected.fields().isEmpty()) {
                content.append("<p class=\"empty\">Dieses Objekt hat keine Felder.</p>\n");
            } else if (layout == Layout.VERTICAL) {
                renderVertical(content, selected.fields());
            } else {
                renderHorizontal(content, List.of(selected), false, layout);
            }
            content.append("</section>\n");
            renderChildrenSection(content, kids, loadError, selected, layout);
        }

        String err = configError.get();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("pageTitle", esc(selected.name() + " – " + app));
        values.put("appTitle", esc(app));
        values.put("objectName", esc(selected.name()));
        values.put("objectType", esc(typeLabel(selected.type())));
        values.put("nav", nav.toString());
        values.put("header", header.toString());
        values.put("treetable", tt.toString());
        values.put("content", content.toString());
        values.put("configError", err == null ? "" : "<pre class=\"cfg-error\" role=\"alert\">" + esc(err) + "</pre>");
        return Templates.fill(templates.text("page.html"), values);
    }

    // ----- Header -----

    private void renderHeader(StringBuilder sb, ObjectNode selected, NodeView view, Layout layout, Message message) {
        sb.append("<div class=\"crumbs\">");
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
            } else if (el instanceof NumberField nf) {
                renderNumberField(sb, id, nf, fields.get(nf.key()));
            } else if (el instanceof DateField df) {
                renderDateField(sb, id, df, fields.get(df.key()));
            } else if (el instanceof CheckBox cbx) {
                renderCheckBox(sb, id, cbx, fields.get(cbx.key()));
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

    private void renderNumberField(StringBuilder sb, String id, NumberField nf, Object value) {
        String v = value == null ? "" : text(value);
        sb.append("<label for=\"").append(id).append("\">").append(esc(nf.label())).append("</label>");
        sb.append("<div class=\"with-unit\"><input type=\"text\" inputmode=\"decimal\" class=\"num\" id=\"").append(id)
          .append("\" value=\"").append(esc(v)).append('"')
          .append(nf.editable() ? " name=\"" + esc(nf.key()) + "\" pattern=\"\\s*[+\\-]?[0-9.,\\s]*\\s*\""
                  + " title=\"Zahl, z. B. 150.000,00\"" : " readonly")
          .append(">");
        if (!nf.unit().isEmpty()) sb.append("<span class=\"unit\">").append(esc(nf.unit())).append("</span>");
        sb.append("</div>\n");
    }

    private void renderDateField(StringBuilder sb, String id, DateField df, Object value) {
        String v = value == null ? "" : text(value);
        java.time.LocalDate d = DateField.parse(v);
        sb.append("<label for=\"").append(id).append("\">").append(esc(df.label())).append("</label>");
        if (df.editable() && (d != null || v.isBlank())) {
            // Browser-Datumsauswahl; liefert JJJJ-MM-TT, gespeichert wird TT.MM.JJJJ
            sb.append("<input type=\"date\" id=\"").append(id).append("\" name=\"").append(esc(df.key()))
              .append("\" value=\"").append(d == null ? "" : d.toString()).append("\">\n");
        } else {
            sb.append("<input type=\"text\" id=\"").append(id).append("\" value=\"").append(esc(v)).append('"')
              .append(df.editable() ? " name=\"" + esc(df.key()) + "\" placeholder=\"TT.MM.JJJJ\"" : " readonly")
              .append(">\n");
        }
    }

    private void renderCheckBox(StringBuilder sb, String id, CheckBox cb, Object value) {
        boolean on = CheckBox.isChecked(value);
        sb.append("<label for=\"").append(id).append("\">").append(esc(cb.label())).append("</label>");
        sb.append("<div class=\"check\">");
        if (cb.editable()) {
            // nicht angehakte Checkboxen schickt der Browser nicht mit: "nein" als Grundwert
            sb.append("<input type=\"hidden\" name=\"").append(esc(cb.key())).append("\" value=\"nein\">")
              .append("<input type=\"checkbox\" id=\"").append(id).append("\" name=\"").append(esc(cb.key()))
              .append("\" value=\"ja\"").append(on ? " checked" : "").append(">");
        } else {
            sb.append("<input type=\"checkbox\" id=\"").append(id).append("\" disabled").append(on ? " checked" : "").append(">");
        }
        sb.append("</div>\n");
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

    private void renderTreeTable(StringBuilder sb, ObjectNode selected, Layout layout, List<TreeColumn> treeColumns) {
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
        renderTreeRow(sb, root, 0, selected, layout, true, treeColumns);
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
        List<TreeColumn> cols = columns();
        for (ObjectNode c : node.children()) {
            renderTreeRow(sb, c, depth, selected, layout, true, cols);
        }
        return sb.toString();
    }

    private void renderTreeRow(StringBuilder sb, ObjectNode node, int depth, ObjectNode selected,
                               Layout layout, boolean visible, List<TreeColumn> treeColumns) {
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
        NodeStyle st = styler.apply(node);
        String cellCss = isSel ? cssText(st) : cssText(st) + cssBackground(st);
        if (st != null && st.tooltip() != null) sb.append(" title=\"").append(esc(st.tooltip())).append('"');
        sb.append("><td class=\"tt-mark\"").append(styleAttr(isSel ? "" : cssBackground(st))).append(">")
          .append(isSel ? "✔" : "").append("</td>");
        for (TreeColumn col : treeColumns) {
            String v = col.valueOf(node);
            sb.append("<td data-v=\"").append(esc(v)).append('"');
            if (col.kind() == TreeColumn.Kind.NAME) {
                sb.append(" class=\"tt-name\" style=\"padding-left:").append(8 + depth * 18).append("px;")
                  .append(cellCss).append("\">");
                if (hasKids) {
                    sb.append("<button type=\"button\" class=\"tt-tog\" aria-label=\"")
                      .append(open ? "Zuklappen" : "Aufklappen").append("\">").append(open ? "−" : "+").append("</button>");
                } else {
                    sb.append("<span class=\"tt-sp\"></span>");
                }
                sb.append("<a href=\"").append(href(node, layout)).append("\"").append(styleAttr(cssText(st)))
                  .append("><span class=\"ico\">").append(icon(node, st)).append("</span>").append(esc(v)).append("</a>");
            } else {
                sb.append(styleAttr(cellCss)).append('>').append(esc(v));
            }
            sb.append("</td>");
        }
        sb.append("</tr>\n");
        for (ObjectNode c : kids) {
            renderTreeRow(sb, c, depth + 1, selected, layout, visible && open, treeColumns);
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
        boolean leaf = node.isLoaded() && node.loadedChildren().isEmpty();
        sb.append(leaf ? "<li class=\"leaf-item\">" : "<li>");
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
        NodeStyle st = styler.apply(n);
        String title = st != null && st.tooltip() != null ? st.tooltip() : typeLabel(n.type());
        String css = cls.isEmpty() ? cssText(st) + cssBackground(st) : "";
        return "<a href=\"" + href(n, layout) + "\"" + cls + styleAttr(css) + " title=\"" + esc(title) + "\">"
                + "<span class=\"ico\">" + icon(n, st) + "</span>" + esc(n.name()) + "</a>";
    }

    // ----- Aussehen einzelner Objekte (NodeStyle) -----

    private java.util.function.Function<ObjectNode, NodeStyle> styler = ObjectNode::style;

    /** Regel für das Aussehen einzelner Objekte (Standard: nur {@link ObjectNode#style()}). */
    public void styler(java.util.function.Function<ObjectNode, NodeStyle> rule) {
        this.styler = rule == null ? ObjectNode::style : n -> {
            try {
                return rule.apply(n);
            } catch (RuntimeException e) {
                return null;
            }
        };
    }

    /** Farben sind geprüft (#rrggbb), daher ohne Maskierung im style-Attribut erlaubt. */
    private static String cssText(NodeStyle st) {
        if (st == null) return "";
        return (st.color() != null ? "color:" + st.color() + ";" : "")
                + (st.isBold() ? "font-weight:700;" : "")
                + (st.isItalic() ? "font-style:italic;" : "");
    }

    private static String cssBackground(NodeStyle st) {
        return st == null || st.background() == null ? "" : "background:" + st.background() + ";";
    }

    private static String styleAttr(String css) {
        return css.isEmpty() ? "" : " style=\"" + css + "\"";
    }

    private static String icon(ObjectNode n, NodeStyle st) {
        return st != null && st.icon() != null ? esc(st.icon()) : n.type().icon();
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
    public static String text(Object v) {
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


}
