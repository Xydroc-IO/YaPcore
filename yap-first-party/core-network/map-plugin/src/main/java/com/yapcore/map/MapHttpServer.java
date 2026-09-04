package com.yapcore.map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class MapHttpServer {

    private static final Logger LOG = Logger.getLogger("YaPMap.Http");

    private final String bindHost;
    private final int port;
    private final Path tilesDir;
    private final Supplier<String> markersJson;
    private HttpServer http;

    public MapHttpServer(String bindHost, int port, Path tilesDir) {
        this(bindHost, port, tilesDir, null);
    }

    public MapHttpServer(String bindHost, int port, Path tilesDir, Supplier<String> markersJson) {
        this.bindHost = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost;
        this.port = port;
        this.tilesDir = tilesDir;
        this.markersJson = markersJson;
    }

    public synchronized void start() throws IOException {
        if (http != null) {
            return;
        }
        Files.createDirectories(tilesDir);
        InetSocketAddress addr = new InetSocketAddress(
                "0.0.0.0".equals(bindHost) ? "0.0.0.0" : bindHost, port);
        http = HttpServer.create(addr, 0);
        http.createContext("/map/", this::serveMapStatic);
        http.createContext("/tiles/", this::serveTiles);
        http.createContext("/health", ex -> {
            byte[] ok = "ok".getBytes();
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(ok);
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "yap-map-http");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        LOG.info("Map HTTP server on " + bindHost + ":" + port + " (tiles=" + tilesDir.toAbsolutePath() + ")");
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
            LOG.info("Map HTTP server stopped");
        }
    }

    private void serveMapStatic(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String rel = path.substring("/map/".length());
            if (rel.isBlank()) {
                rel = "index.html";
            }
            if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if ("markers.json".equals(rel) && markersJson != null) {
                byte[] body = markersJson.get().getBytes(StandardCharsets.UTF_8);
                Headers headers = exchange.getResponseHeaders();
                headers.add("Content-Type", "application/json; charset=utf-8");
                headers.add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                return;
            }
            String resource = "/map/" + rel;
            try (InputStream in = MapHttpServer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] body = in.readAllBytes();
                Headers headers = exchange.getResponseHeaders();
                headers.add("Content-Type", contentType(rel));
                headers.add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private void serveTiles(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String rel = path.substring("/tiles/".length());
            if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\") || rel.isBlank()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Path file = tilesDir.resolve(rel).normalize();
            Path root = tilesDir.toAbsolutePath().normalize();
            if (!file.startsWith(root)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "image/png");
            headers.add("Cache-Control", "public, max-age=60");
            long size = Files.size(file);
            exchange.sendResponseHeaders(200, size);
            try (InputStream in = Files.newInputStream(file);
                 OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        } finally {
            exchange.close();
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
