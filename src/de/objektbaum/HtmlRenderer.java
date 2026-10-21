package de.objektbaum;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erzeugt die HTML-Seite: Navigation links, Header und Main rechts.
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

        public static Layout fromCode(String code) {
            return "h".equals(code) ? HORIZONTAL : VERTICAL;
        }
    }

    private final ObjectNode root;
    private final String title;

    public HtmlRenderer(ObjectNode root, String title) {
        this.root = root;
        this.title = title;
    }

    public String render(ObjectNode selected, Layout layout) {
        // Kinder des gewählten Objekts jetzt laden (lazy), Fehler werden im Main angezeigt.
        String loadError = null;
        try {
            selected.ensureLoaded();
        } catch (ChildLoadException e) {
            loadError = e.getMessage();
        }

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
          .append("<title>").append(esc(selected.name())).append(" – ").append(esc(title)).append("</title>\n")
          .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<div class=\"page\">\n");

        // ---------- Navigation ----------
        sb.append("<nav class=\"nav\">\n<div class=\"brand\">").append(esc(title)).append("</div>\n<ul class=\"tree\">\n");
        renderNavNode(sb, root, selected, layout);
        sb.append("</ul>\n</nav>\n");

        // ---------- Header ----------
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
        sb.append("<div class=\"toggle\" role=\"group\" aria-label=\"Tabellen-Layout\">");
        for (Layout l : Layout.values()) {
            sb.append("<a href=\"").append(href(selected, l)).append("\"")
              .append(l == layout ? " class=\"on\" aria-current=\"true\"" : "")
              .append(">").append(l.label).append("</a>");
        }
        List<ObjectNode> kids = selected.loadedChildren();
        sb.append("</div>\n</div>\n</div>\n<div class=\"meta\">")
          .append(selected.fields().size()).append(" Felder · ")
          .append(selected.isLoaded() ? kids.size() + " Unterobjekte" : "Unterobjekte nicht geladen")
          .append(selected.isLazy() ? " · <span class=\"lazy-tag\">lazy</span>" : "")
          .append("</div>\n</header>\n");

        // ---------- Main ----------
        sb.append("<main class=\"main\">\n<section>\n<h2>Felder</h2>\n");
        if (selected.fields().isEmpty()) {
            sb.append("<p class=\"empty\">Dieses Objekt hat keine Felder.</p>\n");
        } else if (layout == Layout.VERTICAL) {
            renderVertical(sb, selected.fields());
        } else {
            renderHorizontal(sb, List.of(selected), false, layout);
        }
        sb.append("</section>\n");

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
        sb.append("</section>\n</main>\n</div>\n<script>").append(JS).append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

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

    // ----- Navigation (Baum) -----

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
        return "<a href=\"" + href(n, layout) + "\"" + cls + " title=\"" + n.type().label() + "\">"
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
              .append(esc(String.valueOf(e.getValue()))).append("</td></tr>\n");
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
            sb.append("<tr>");
            if (withName) {
                sb.append("<th scope=\"row\">").append(link(n, layout, esc(n.name()))).append("</th>")
                  .append("<td>").append(badge(n.type())).append("</td>");
            }
            for (String k : keys) {
                Object v = n.fields().get(k);
                sb.append("<td>").append(v == null && !n.fields().containsKey(k) ? "" : esc(String.valueOf(v))).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table></div>\n");
    }

    // ----- Hilfen -----

    private static String badge(ObjectType t) {
        return "<span class=\"badge t-" + t.name().toLowerCase() + "\">" + t.icon() + " " + esc(t.label()) + "</span>";
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

    /** Lädt die Kinder eines lazy Knotens beim ersten Aufklappen in der Navigation nach. */
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
        """;

    private static final String CSS = """
        :root{--bg:#f6f7f9;--panel:#fff;--nav:#1f2937;--nav-fg:#d1d5db;--nav-hi:#374151;--accent:#2563eb;
              --fg:#111827;--muted:#6b7280;--line:#e5e7eb;--zebra:#f9fafb}
        @media (prefers-color-scheme:dark){:root{--bg:#0f1115;--panel:#171a21;--nav:#0b0d11;--nav-fg:#c9ced6;
              --nav-hi:#232833;--accent:#60a5fa;--fg:#e5e7eb;--muted:#9aa3af;--line:#2a2f3a;--zebra:#1c2029}}
        *{box-sizing:border-box}
        body{margin:0;font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:var(--bg);color:var(--fg)}
        a{color:var(--accent);text-decoration:none} a:hover{text-decoration:underline}
        .page{display:grid;grid-template-columns:280px 1fr;grid-template-rows:auto 1fr;min-height:100vh;
              grid-template-areas:"nav header" "nav main"}
        .nav{grid-area:nav;background:var(--nav);color:var(--nav-fg);padding:16px 12px;overflow:auto;
             position:sticky;top:0;height:100vh}
        .brand{font-weight:700;font-size:17px;color:#fff;padding:4px 8px 14px;border-bottom:1px solid var(--nav-hi);margin-bottom:10px}
        .tree,.tree ul{list-style:none;margin:0;padding:0} .tree ul{padding-left:14px}
        .tree li{margin:1px 0} .tree summary{cursor:pointer;list-style:none;padding-left:0}
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
        tbody tr:nth-child(even) td{background:var(--zebra)}
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
        .ico{display:inline-block;width:20px;text-align:center;margin-right:2px}
        .badge{display:inline-block;font-size:12px;font-weight:500;padding:2px 9px;border-radius:999px;
               border:1px solid var(--line);background:var(--zebra);color:var(--fg);vertical-align:middle;white-space:nowrap}
        @media (max-width:760px){.page{grid-template-columns:1fr;grid-template-areas:"nav" "header" "main"}
             .nav{position:static;height:auto;max-height:45vh} .header,.main{padding:16px}}
        """;
}
