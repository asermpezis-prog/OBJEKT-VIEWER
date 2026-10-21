package de.objektbaum;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Kleiner Webserver aus dem JDK (keine Bibliotheken nötig), der die Seite ausliefert.
 *
 * <pre>
 * GET  /?id=&lt;id&gt;&amp;layout=v|h[&amp;reload=1]     Seite eines Objekts
 * GET  /nav?id=&lt;id&gt;&amp;sel=&lt;id&gt;&amp;layout=v|h      Kinder für die Navigation (lazy)
 * GET  /rows?id=&lt;id&gt;&amp;sel=&lt;id&gt;&amp;layout=v|h     Kinder-Zeilen für die Baumtabelle (lazy)
 * POST /action                              Eingaben speichern + Button-Aktion
 * </pre>
 */
public final class TreeServer {

    private static final int MAX_FORM_BYTES = 1_000_000;

    private final ObjectNode root;
    private final HtmlRenderer renderer;
    private HttpServer server;

    public TreeServer(ObjectNode root, String title) {
        this(root, title, new ViewRegistry());
    }

    public TreeServer(ObjectNode root, String title, ViewRegistry views) {
        this(root, title, views, HtmlRenderer.DEFAULT_TREE_COLUMNS);
    }

    public TreeServer(ObjectNode root, String title, ViewRegistry views, List<TreeColumn> treeColumns) {
        this.root = root;
        this.renderer = new HtmlRenderer(root, title, views, treeColumns);
    }

    /** Startet auf localhost (nur von diesem Rechner erreichbar). */
    public void start(int port) throws IOException {
        start("localhost", port);
    }

    /** @param host "localhost" oder z. B. "0.0.0.0" für Zugriff aus dem Netzwerk */
    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, List<String>> q = parse(ex.getRequestURI().getRawQuery());
            HtmlRenderer.Layout layout = HtmlRenderer.Layout.fromCode(first(q, "layout"));

            switch (path) {
                case "/" -> {
                    ObjectNode selected = lookup(first(q, "id")).orElse(root);
                    if ("1".equals(first(q, "reload"))) {
                        selected.reload();
                    }
                    String msg = first(q, "msg");
                    HtmlRenderer.Message message = msg == null ? null
                            : new HtmlRenderer.Message(msg, "1".equals(first(q, "err")));
                    send(ex, 200, "text/html; charset=utf-8", renderer.render(selected, layout, message));
                }
                case "/nav" -> {
                    Optional<ObjectNode> node = lookup(first(q, "id"));
                    if (node.isEmpty()) {
                        send(ex, 404, "text/plain; charset=utf-8", "Objekt nicht gefunden");
                        return;
                    }
                    ObjectNode selected = lookup(first(q, "sel")).orElse(null);
                    send(ex, 200, "text/html; charset=utf-8", renderer.renderNavChildren(node.get(), selected, layout));
                }
                case "/rows" -> {
                    Optional<ObjectNode> node = lookup(first(q, "id"));
                    if (node.isEmpty()) {
                        send(ex, 404, "text/plain; charset=utf-8", "Objekt nicht gefunden");
                        return;
                    }
                    ObjectNode selected = lookup(first(q, "sel")).orElse(null);
                    try {
                        send(ex, 200, "text/html; charset=utf-8", renderer.renderTreeRows(node.get(), selected, layout));
                    } catch (ChildLoadException e) {
                        send(ex, 502, "text/plain; charset=utf-8", e.getMessage());
                    }
                }
                case "/action" -> handleAction(ex);
                default -> send(ex, 404, "text/plain; charset=utf-8", "Nicht gefunden");
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, "text/plain; charset=utf-8", "Interner Fehler: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    /** Übernimmt die Eingaben in die Felder des Objekts und führt die Aktion des Buttons aus. */
    private void handleAction(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Nur POST erlaubt");
            return;
        }
        Map<String, List<String>> form = parse(readBody(ex.getRequestBody()));
        HtmlRenderer.Layout layout = HtmlRenderer.Layout.fromCode(first(form, "_layout"));
        Optional<ObjectNode> found = lookup(first(form, "_id"));
        if (found.isEmpty()) {
            send(ex, 404, "text/plain; charset=utf-8", "Objekt nicht gefunden");
            return;
        }
        ObjectNode node = found.get();
        NodeView view = renderer.views().resolve(node);
        HtmlRenderer.Message result = FormSubmit.submit(node, view, form, first(form, "_btn"));

        // Post/Redirect/Get: Seite neu laden, Meldung als Parameter
        StringBuilder loc = new StringBuilder("/?id=").append(node.id()).append("&layout=").append(layout.code());
        if (result != null) {
            loc.append("&msg=").append(URLEncoder.encode(result.text(), StandardCharsets.UTF_8));
            if (result.error()) loc.append("&err=1");
        }
        ex.getResponseHeaders().set("Location", loc.toString());
        ex.sendResponseHeaders(303, -1);
    }

    /** Sucht ein Objekt im bereits geladenen Baum. */
    private Optional<ObjectNode> lookup(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try {
            return root.find(Integer.parseInt(id.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_FORM_BYTES + 1);
        if (bytes.length > MAX_FORM_BYTES) {
            throw new IllegalArgumentException("Formular zu groß");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Parst Query-String oder Formulardaten; ein Key kann mehrfach vorkommen (Mehrfachauswahl). */
    private static Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? part : part.substring(0, i), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(i < 0 ? "" : part.substring(i + 1), StandardCharsets.UTF_8);
            map.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
        }
        return map;
    }

    private static String first(Map<String, List<String>> map, String key) {
        List<String> v = map.get(key);
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
