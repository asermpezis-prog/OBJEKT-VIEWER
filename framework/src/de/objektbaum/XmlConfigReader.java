package de.objektbaum;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Liest eine viewer.xml. Prüft streng und meldet Fehler mit Zeilennummer und dem, was erlaubt ist.
 *
 * <p>Aufbau (Kurzfassung, vollständig in der README):
 * <pre>
 * &lt;objektviewer title="…"&gt;
 *   &lt;types&gt;&lt;type id="VORGANG" label="Vorgang" icon="📋"/&gt;&lt;/types&gt;
 *   &lt;treetable&gt;&lt;column kind="name|type|field" key="…" header="…"/&gt;&lt;/treetable&gt;
 *   &lt;views&gt;
 *     &lt;view name="…" type="VORGANG" children="true"&gt;
 *       &lt;column width="1"&gt;
 *         &lt;segment title="…" height="1" layout="vertical|horizontal"&gt;
 *           &lt;fields keys="A | B" /&gt;  &lt;fields select="all|remaining"/&gt;
 *           &lt;text key="…" label="…" rows="1"/&gt;  &lt;output key="…"/&gt;
 *           &lt;number key="…" decimals="2" min="0" max="…" unit="€"/&gt;  &lt;date key="…"/&gt;  &lt;check key="…"/&gt;
 *           &lt;combo key="…" options="a | b" source="…" free="false"/&gt;
 *           &lt;list key="…" options="a | b" multiple="true" rows="4"/&gt;
 *           &lt;button label="…" action="…"/&gt;
 * </pre>
 * Listen (keys, options) werden mit | getrennt, weil Werte selbst Kommas enthalten können.
 */
public final class XmlConfigReader {

    private final Bindings bindings;

    public XmlConfigReader(Bindings bindings) {
        this.bindings = bindings;
    }

    public ViewerConfig read(InputStream in) throws ConfigException {
        Node root = parse(in);
        expect(root, "objektviewer", "Wurzelelement muss <objektviewer> sein");
        allowed(root, "title");

        String title = root.attrs.get("title");
        List<ViewerConfig.TypeDef> types = new ArrayList<>();
        List<TreeColumn> treeColumns = null;
        List<ObjectViewer.TreeButton> treeButtons = new ArrayList<>();
        Map<ObjectType, NodeView> views = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Node section : root.children) {
            if (!seen.add(section.name)) {
                throw new ConfigException(section.line, "<" + section.name + "> darf nur einmal vorkommen");
            }
            switch (section.name) {
                case "types" -> {
                    allowed(section);
                    for (Node t : section.children) {
                        expect(t, "type", "In <types> sind nur <type> erlaubt");
                        allowed(t, "id", "label", "icon");
                        types.add(new ViewerConfig.TypeDef(required(t, "id"), t.attrs.get("label"), t.attrs.get("icon")));
                    }
                }
                case "treetable" -> {
                    allowed(section);
                    treeColumns = new ArrayList<>();
                    for (Node c : section.children) {
                        if (c.name.equals("button")) {
                            Button b = (Button) element(c);
                            treeButtons.add(new ObjectViewer.TreeButton(b.label(), b.action()));
                            continue;
                        }
                        expect(c, "column", "In <treetable> sind nur <column> und <button> erlaubt");
                        treeColumns.add(treeColumn(c));
                    }
                    if (treeColumns.stream().noneMatch(c -> c.kind() == TreeColumn.Kind.NAME)) {
                        throw new ConfigException(section.line, "<treetable> braucht eine Spalte mit kind=\"name\"");
                    }
                }
                case "views" -> {
                    allowed(section);
                    for (Node v : section.children) {
                        expect(v, "view", "In <views> sind nur <view> erlaubt");
                        NodeView view = view(v);
                        for (String typeId : list(required(v, "type"))) {
                            ObjectType type = ObjectType.of(typeId);
                            if (views.put(type, view) != null) {
                                throw new ConfigException(v.line, "Für Typ '" + typeId + "' gibt es schon eine Ansicht");
                            }
                        }
                    }
                }
                default -> throw new ConfigException(section.line, "Unbekanntes Element <" + section.name
                        + ">. Erlaubt in <objektviewer>: <types>, <treetable>, <views>");
            }
        }
        return new ViewerConfig(title, types, treeColumns, views, treeButtons);
    }

    // ------------------------------------------------------------------ Teile

    private TreeColumn treeColumn(Node c) throws ConfigException {
        allowed(c, "kind", "key", "header");
        String kind = required(c, "kind");
        switch (kind) {
            case "name":
                return TreeColumn.name(c.attrs.getOrDefault("header", "Objektname"));
            case "type":
                return TreeColumn.type(c.attrs.getOrDefault("header", "Typ"));
            case "field": {
                String key = required(c, "key");
                return TreeColumn.field(key, c.attrs.getOrDefault("header", key));
            }
            default:
                throw new ConfigException(c.line, "kind=\"" + kind + "\" ist unbekannt. Erlaubt: name, type, field");
        }
    }

    private NodeView view(Node v) throws ConfigException {
        allowed(v, "name", "type", "children");
        String name = v.attrs.getOrDefault("name", v.attrs.getOrDefault("type", "Ansicht"));
        boolean children = bool(v, "children", true);
        List<Column> cols = new ArrayList<>();
        for (Node c : v.children) {
            expect(c, "column", "In <view> sind nur <column> erlaubt");
            allowed(c, "width");
            List<Segment> segs = new ArrayList<>();
            for (Node s : c.children) {
                expect(s, "segment", "In <column> sind nur <segment> erlaubt");
                segs.add(segment(s));
            }
            if (segs.isEmpty()) {
                throw new ConfigException(c.line, "<column> braucht mindestens ein <segment>");
            }
            cols.add(new Column(segs, positive(c, "width", 1)));
        }
        try {
            NodeView view = new NodeView(name, cols, true);
            return children ? view : view.withoutChildren();
        } catch (IllegalArgumentException e) {
            throw new ConfigException(v.line, e.getMessage());
        }
    }

    private Segment segment(Node s) throws ConfigException {
        allowed(s, "title", "height", "layout");
        String title = required(s, "title");
        List<Element> elements = new ArrayList<>();
        for (Node e : s.children) {
            elements.add(element(e));
        }
        if (elements.isEmpty()) {
            throw new ConfigException(s.line, "<segment title=\"" + title + "\"> ist leer");
        }
        return new Segment(title, elements, layout(s), positive(s, "height", 1));
    }

    private Element element(Node e) throws ConfigException {
        switch (e.name) {
            case "fields": {
                allowed(e, "keys", "select", "layout");
                String select = e.attrs.get("select");
                String keys = e.attrs.get("keys");
                FieldTable t;
                if (keys != null && select != null) {
                    throw new ConfigException(e.line, "<fields>: entweder keys=\"…\" oder select=\"…\", nicht beides");
                } else if (keys != null) {
                    List<String> k = list(keys);
                    if (k.isEmpty()) throw new ConfigException(e.line, "<fields keys=\"\">: mindestens ein Key angeben");
                    t = new FieldTable(FieldTable.Selection.KEYS, k, null);
                } else if ("all".equals(select)) {
                    t = FieldTable.all();
                } else if ("remaining".equals(select)) {
                    t = FieldTable.remaining();
                } else {
                    throw new ConfigException(e.line, select == null
                            ? "<fields> braucht keys=\"A | B\" oder select=\"all\" bzw. select=\"remaining\""
                            : "select=\"" + select + "\" ist unbekannt. Erlaubt: all, remaining");
                }
                HtmlRenderer.Layout l = layout(e);
                return l == null ? t : new FieldTable(t.selection(), t.keys(), l);
            }
            case "text":
            case "output": {
                allowed(e, "key", "label", "rows");
                boolean editable = e.name.equals("text");
                TextField tf = new TextField(required(e, "key"), e.attrs.get("label"), editable, integer(e, "rows", 1, 1));
                return tf;
            }
            case "number": {
                allowed(e, "key", "label", "decimals", "min", "max", "unit", "editable");
                try {
                    return new NumberField(required(e, "key"), e.attrs.get("label"), integer(e, "decimals", 2, 0),
                            number(e, "min"), number(e, "max"), e.attrs.get("unit"), bool(e, "editable", true));
                } catch (IllegalArgumentException ex) {
                    throw new ConfigException(e.line, ex.getMessage());
                }
            }
            case "date": {
                allowed(e, "key", "label", "editable");
                return new DateField(required(e, "key"), e.attrs.get("label"), bool(e, "editable", true));
            }
            case "check": {
                allowed(e, "key", "label", "editable");
                return new CheckBox(required(e, "key"), e.attrs.get("label"), bool(e, "editable", true));
            }
            case "combo": {
                allowed(e, "key", "label", "options", "source", "free", "editable");
                OptionSource src = optionSource(e);
                return new ComboBox(required(e, "key"), e.attrs.get("label"), src, bool(e, "free", false),
                        bool(e, "editable", true));
            }
            case "list": {
                allowed(e, "key", "label", "options", "source", "multiple", "rows", "editable");
                OptionSource src = optionSource(e);
                return new ComboList(required(e, "key"), e.attrs.get("label"), src, bool(e, "multiple", false),
                        integer(e, "rows", 4, 2), bool(e, "editable", true));
            }
            case "button": {
                allowed(e, "label", "action");
                String label = required(e, "label");
                String actionName = e.attrs.get("action");
                if (actionName == null || actionName.isBlank()) {
                    return Button.save(label);
                }
                Button.Action a = bindings.action(actionName);
                if (a == null) {
                    String known = bindings.actionNames();
                    throw new ConfigException(e.line, "Aktion '" + actionName + "' ist im Code nicht angemeldet. "
                            + (known.isEmpty() ? "Es sind keine Aktionen angemeldet." : "Angemeldet: " + known)
                            + " (ohne action=\"…\" speichert der Button nur)");
                }
                return Button.of(label, a);
            }
            default:
                throw new ConfigException(e.line, "Unbekanntes Element <" + e.name + "> in <segment>. "
                        + "Erlaubt: <fields>, <text>, <output>, <number>, <date>, <check>, <combo>, <list>, <button>");
        }
    }

    private OptionSource optionSource(Node e) throws ConfigException {
        String options = e.attrs.get("options");
        String source = e.attrs.get("source");
        if (options != null && source != null) {
            throw new ConfigException(e.line, "<" + e.name + ">: entweder options=\"…\" oder source=\"…\", nicht beides");
        }
        if (options != null) {
            return OptionSource.of(list(options).toArray(String[]::new));
        }
        if (source != null) {
            OptionSource s = bindings.options(source);
            if (s == null) {
                String known = bindings.optionNames();
                throw new ConfigException(e.line, "Optionsquelle '" + source + "' ist im Code nicht angemeldet. "
                        + (known.isEmpty() ? "Es sind keine angemeldet." : "Angemeldet: " + known));
            }
            return s;
        }
        throw new ConfigException(e.line, "<" + e.name + "> braucht options=\"a | b | c\" oder source=\"…\"");
    }

    // ------------------------------------------------------------------ Prüfungen

    private static void expect(Node n, String name, String message) throws ConfigException {
        if (!n.name.equals(name)) {
            throw new ConfigException(n.line, message + " (gefunden: <" + n.name + ">)");
        }
    }

    private static void allowed(Node n, String... names) throws ConfigException {
        Set<String> ok = new LinkedHashSet<>(Arrays.asList(names));
        for (String a : n.attrs.keySet()) {
            if (!ok.contains(a)) {
                throw new ConfigException(n.line, "Unbekanntes Attribut " + a + "=\"…\" in <" + n.name + ">. "
                        + (ok.isEmpty() ? "Hier sind keine Attribute erlaubt." : "Erlaubt: " + String.join(", ", ok)));
            }
        }
        if (!n.text.isBlank()) {
            throw new ConfigException(n.line, "<" + n.name + "> darf keinen Text enthalten: „" + n.text.strip() + "“");
        }
    }

    private static String required(Node n, String attr) throws ConfigException {
        String v = n.attrs.get(attr);
        if (v == null || v.isBlank()) {
            throw new ConfigException(n.line, "<" + n.name + "> braucht " + attr + "=\"…\"");
        }
        return v.trim();
    }

    private static boolean bool(Node n, String attr, boolean def) throws ConfigException {
        String v = n.attrs.get(attr);
        if (v == null) return def;
        switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "true", "ja", "yes", "1":
                return true;
            case "false", "nein", "no", "0":
                return false;
            default:
                throw new ConfigException(n.line, attr + "=\"" + v + "\" in <" + n.name + ">: erlaubt sind true oder false");
        }
    }

    private static int integer(Node n, String attr, int def, int min) throws ConfigException {
        String v = n.attrs.get(attr);
        if (v == null) return def;
        try {
            int i = Integer.parseInt(v.trim());
            if (i < min) throw new NumberFormatException();
            return i;
        } catch (NumberFormatException e) {
            throw new ConfigException(n.line, attr + "=\"" + v + "\" in <" + n.name + ">: ganze Zahl ab " + min + " erwartet");
        }
    }

    private static java.math.BigDecimal number(Node n, String attr) throws ConfigException {
        String v = n.attrs.get(attr);
        if (v == null || v.isBlank()) return null;
        try {
            return NumberField.parse(v);
        } catch (NumberFormatException e) {
            throw new ConfigException(n.line, attr + "=\"" + v + "\" in <" + n.name + ">: Zahl erwartet, z. B. 0 oder 1.000.000,00");
        }
    }

    private static double positive(Node n, String attr, double def) throws ConfigException {
        String v = n.attrs.get(attr);
        if (v == null) return def;
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.'));
            if (!(d > 0)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw new ConfigException(n.line, attr + "=\"" + v + "\" in <" + n.name + ">: Zahl größer 0 erwartet, z. B. 1 oder 0.8");
        }
    }

    private static HtmlRenderer.Layout layout(Node n) throws ConfigException {
        String v = n.attrs.get("layout");
        if (v == null) return null;
        switch (v.trim()) {
            case "vertical":
                return HtmlRenderer.Layout.VERTICAL;
            case "horizontal":
                return HtmlRenderer.Layout.HORIZONTAL;
            default:
                throw new ConfigException(n.line, "layout=\"" + v + "\" ist unbekannt. Erlaubt: vertical (Key/Value), "
                        + "horizontal (Keys als Spalten)");
        }
    }

    /** "A | B | C" → [A, B, C]; leere Einträge werden ignoriert. */
    private static List<String> list(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split("\\|")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------------ XML

    /** Element mit Zeilennummer (DOM kennt keine Zeilennummern, daher eigener kleiner Baum). */
    private static final class Node {
        final String name;
        final Map<String, String> attrs = new LinkedHashMap<>();
        final List<Node> children = new ArrayList<>();
        final int line;
        String text = "";

        Node(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }

    private static Node parse(InputStream in) throws ConfigException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(false);
            // Sicherheit: keine DTDs, keine externen Entitäten
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            SAXParser p = f.newSAXParser();
            try {
                // Meldungen des XML-Parsers auf Deutsch (falls die Laufzeit das unterstützt)
                p.getXMLReader().setProperty("http://apache.org/xml/properties/locale", Locale.GERMAN);
            } catch (Exception ignored) {
                // dann eben englisch
            }
            Deque<Node> stack = new ArrayDeque<>();
            Node[] root = new Node[1];
            p.parse(in, new DefaultHandler() {
                private Locator locator;

                @Override
                public void setDocumentLocator(Locator l) {
                    locator = l;
                }

                @Override
                public void startElement(String uri, String local, String qName, Attributes a) {
                    Node n = new Node(qName, locator == null ? 0 : locator.getLineNumber());
                    for (int i = 0; i < a.getLength(); i++) n.attrs.put(a.getQName(i), a.getValue(i));
                    if (stack.isEmpty()) root[0] = n;
                    else stack.peek().children.add(n);
                    stack.push(n);
                }

                @Override
                public void endElement(String uri, String local, String qName) {
                    stack.pop();
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (!stack.isEmpty()) stack.peek().text += new String(ch, start, length);
                }
            });
            return root[0];
        } catch (SAXParseException e) {
            throw new ConfigException(e.getLineNumber(), "XML-Fehler: " + e.getMessage());
        } catch (Exception e) {
            throw new ConfigException(0, "Datei konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }
}
