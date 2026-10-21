package de.objektbaum;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Kleiner Webserver aus dem JDK (keine Bibliotheken nötig), der die Seite ausliefert.
 * Aufruf: http://localhost:8080/?id=<objektId>&layout=v|h[&reload=1]
 * Navigation nachladen: /nav?id=<objektId>&sel=<gewaehltesObjekt>&layout=v|h
 */
public final class TreeServer {

    private final ObjectNode root;
    private final HtmlRenderer renderer;
    private HttpServer server;

    public TreeServer(ObjectNode root, String title) {
        this.root = root;
        this.renderer = new HtmlRenderer(root, title);
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            HtmlRenderer.Layout layout = HtmlRenderer.Layout.fromCode(q.get("layout"));

            switch (path) {
                case "/" -> {
                    ObjectNode selected = lookup(q.get("id")).orElse(root);
                    if ("1".equals(q.get("reload"))) {
                        selected.reload();
                    }
                    send(ex, 200, "text/html; charset=utf-8", renderer.render(selected, layout));
                }
                case "/nav" -> {
                    // HTML-Fragment: Kinder eines Knotens für die Navigation (lazy nachladen)
                    Optional<ObjectNode> node = lookup(q.get("id"));
                    if (node.isEmpty()) {
                        send(ex, 404, "text/plain; charset=utf-8", "Objekt nicht gefunden");
                        return;
                    }
                    ObjectNode selected = lookup(q.get("sel")).orElse(null);
                    send(ex, 200, "text/html; charset=utf-8", renderer.renderNavChildren(node.get(), selected, layout));
                }
                default -> send(ex, 404, "text/plain; charset=utf-8", "Nicht gefunden");
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, "text/plain; charset=utf-8", "Interner Fehler: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    /** Sucht ein Objekt im bereits geladenen Baum. */
    private Optional<ObjectNode> lookup(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try {
            return root.find(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            String k = i < 0 ? part : part.substring(0, i);
            String v = i < 0 ? "" : part.substring(i + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }
}
