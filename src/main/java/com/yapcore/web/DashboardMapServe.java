package com.yapcore.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yapcore.server.YaPcoreServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves YaPMap web UI, PNG tiles, and JSON meshes from disk (same origin as the admin dashboard). */
public final class DashboardMapServe {

    private DashboardMapServe() {
    }

    public static HttpHandler mapStatic(Path rootDir) {
        return mapStatic(rootDir, null);
    }

    public static HttpHandler mapStatic(Path rootDir, YaPcoreServer server) {
        Path mapWebDir = rootDir.resolve("plugins").resolve("YaPMap").resolve("web");
        return exchange -> serveMapStatic(exchange, mapWebDir, rootDir, server);
    }

    public static HttpHandler mapTiles(Path rootDir) {
        Path mapTilesDir = rootDir.resolve("plugins").resolve("YaPMap").resolve("map/tiles");
        return exchange -> serveSafeFile(exchange, mapTilesDir, "/tiles/", "image/png");
    }

    public static HttpHandler mapMeshes(Path rootDir) {
        Path mapMeshesDir = rootDir.resolve("plugins").resolve("YaPMap").resolve("map/meshes");
        return exchange -> serveSafeFile(exchange, mapMeshesDir, "/meshes/", null);
    }

    private static void serveMapStatic(HttpExchange exchange, Path mapWebDir, Path rootDir,
                                       YaPcoreServer server) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String rel = path.substring("/map/".length());
            if (rel.isBlank()) {
                rel = "index.html";
            }
            if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if ("markers.json".equals(rel)) {
                byte[] body = DashboardMapMarkers.build(server, rootDir).getBytes(StandardCharsets.UTF_8);
                Headers headers = exchange.getResponseHeaders();
                headers.add("Content-Type", "application/json; charset=utf-8");
                headers.add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                return;
            }
            Path file = mapWebDir.resolve(rel).normalize();
            Path root = mapWebDir.toAbsolutePath().normalize();
            if (!file.startsWith(root)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", contentType(rel));
            headers.add("Cache-Control", "no-cache");
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

    private static void serveSafeFile(HttpExchange exchange, Path rootDir, String prefix, String contentType)
            throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String rel = path.substring(prefix.length());
            if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\") || rel.isBlank()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Path file = rootDir.resolve(rel).normalize();
            Path root = rootDir.toAbsolutePath().normalize();
            if (!file.startsWith(root)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            String ct = contentType != null ? contentType : contentType(rel);
            headers.add("Content-Type", ct);
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
        if (name.endsWith(".ymesh") || name.endsWith(".png")) {
            return name.endsWith(".png") ? "image/png" : "application/octet-stream";
        }
        return "application/octet-stream";
    }
}
