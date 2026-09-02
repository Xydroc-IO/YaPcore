package com.yapcore.resourcepack;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lightweight HTTP host so Java and Bedrock clients can download the active pack
 * directly from the YaPcore process (seamless, no external CDN required).
 * Also serves YaPMap tiles and UI at {@code /map/} and {@code /tiles/} when configured.
 */
public final class ResourcePackHttpServer {

    private static final Logger LOG = Logger.getLogger("YaPcore.PackHttp");

    private final String bindHost;
    private final int port;
    private final Path packsDir;
    private final Path mapWebDir;
    private final Path mapTilesDir;
    private HttpServer http;

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir) {
        this(bindHost, port, packsDir, null, null);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir,
                                  Path mapWebDir, Path mapTilesDir) {
        this.bindHost = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost;
        this.port = port;
        this.packsDir = packsDir;
        this.mapWebDir = mapWebDir;
        this.mapTilesDir = mapTilesDir;
    }

    public int getPort() {
        return port;
    }

    public synchronized void start() throws IOException {
        if (http != null) {
            return;
        }
        Files.createDirectories(packsDir);
        InetSocketAddress addr = new InetSocketAddress(
                "0.0.0.0".equals(bindHost) ? "0.0.0.0" : bindHost, port);
        http = HttpServer.create(addr, 0);
        http.createContext("/pack/", this::servePack);
        if (mapWebDir != null) {
            http.createContext("/map/", this::serveMapStatic);
        }
        if (mapTilesDir != null) {
            Files.createDirectories(mapTilesDir);
            http.createContext("/tiles/", this::serveMapTiles);
        }
        http.createContext("/health", ex -> {
            byte[] ok = "ok".getBytes();
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(ok);
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "yap-pack-http");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        LOG.info("Resource pack HTTP server on :" + port + " (dir=" + packsDir.toAbsolutePath() + ")");
        if (mapWebDir != null) {
            LOG.info("YaPMap UI at http://127.0.0.1:" + port + "/map/ (web=" + mapWebDir.toAbsolutePath() + ")");
        }
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
            LOG.info("Resource pack HTTP server stopped");
        }
    }

    private void servePack(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String name = path.substring("/pack/".length());
            if (name.contains("..") || name.contains("/") || name.contains("\\") || name.isBlank()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Path file = packsDir.resolve(name).normalize();
            if (!file.startsWith(packsDir.toAbsolutePath().normalize()) && !file.startsWith(packsDir.normalize())) {
                Path abs = packsDir.toAbsolutePath().normalize().resolve(name).normalize();
                if (!abs.startsWith(packsDir.toAbsolutePath().normalize())) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                file = abs;
            } else {
                file = packsDir.toAbsolutePath().normalize().resolve(name).normalize();
            }
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/zip");
            headers.add("Content-Disposition", "attachment; filename=\"" + name + "\"");
            headers.add("Cache-Control", "no-cache");
            long size = Files.size(file);
            // Explicit length — some MC clients fail when HEAD/GET omit Content-Length.
            headers.add("Content-Length", Long.toString(size));
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                // -1 + manual Content-Length: body omitted, length still advertised
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, size);
            try (InputStream in = Files.newInputStream(file);
                 OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
            LOG.info("Served resource pack " + name + " (" + size + " bytes) to "
                    + exchange.getRemoteAddress());
        } finally {
            exchange.close();
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

    private void serveMapTiles(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String rel = path.substring("/tiles/".length());
            if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\") || rel.isBlank()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Path file = mapTilesDir.resolve(rel).normalize();
            Path root = mapTilesDir.toAbsolutePath().normalize();
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
