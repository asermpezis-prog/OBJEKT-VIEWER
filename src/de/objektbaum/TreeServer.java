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
import java.util.concurrent.Executors;

/**
 * Kleiner Webserver aus dem JDK (keine Bibliotheken nötig), der die Seite ausliefert.
 * Aufruf: http://localhost:8080/?id=<objektId>&layout=v|h
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
        try (ex) {
            if (!"/".equals(ex.getRequestURI().getPath())) {
                send(ex, 404, "text/plain", "Nicht gefunden");
                return;
            }
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            HtmlRenderer.Layout layout = HtmlRenderer.Layout.fromCode(q.get("layout"));
            ObjectNode selected = root;
            try {
                if (q.containsKey("id")) {
                    selected = root.find(Integer.parseInt(q.get("id"))).orElse(root);
                }
            } catch (NumberFormatException ignored) {
                // ungültige id -> Wurzel anzeigen
            }
            send(ex, 200, "text/html; charset=utf-8", renderer.render(selected, layout));
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
