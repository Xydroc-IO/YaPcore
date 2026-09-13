package com.yapcore.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yapcore.crossplay.skin.BedrockCanonicalSkin;
import com.yapcore.crossplay.skin.SkinService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Lightweight HTTP host so Java and Bedrock clients can download the active pack
 * directly from the YaPcore process (seamless, no external CDN required).
 * Also serves YaPMap UI / tiles / meshes at {@code /map/}, {@code /tiles/}, {@code /meshes/}
 * when configured, Tailor skin apply at {@code POST /skin/apply}, and emote play at
 * {@code POST /emote/play}.
 */
public final class ResourcePackHttpServer {

    private static final Logger LOG = Logger.getLogger("YaPcore.PackHttp");

    private final String bindHost;
    private final int port;
    private final Path packsDir;
    private final Path mapWebDir;
    private final Path mapTilesDir;
    private final Path mapMeshesDir;
    private final Path skinsDir;
    private volatile SkinService skinService;
    private volatile BiConsumer<String, BedrockCanonicalSkin> skinApplyHandler;
    private volatile EmotePlayHandler emotePlayHandler;
    private HttpServer http;

    @FunctionalInterface
    public interface EmotePlayHandler {
        boolean play(String username, UUID uuid, String emoteId);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir) {
        this(bindHost, port, packsDir, null, null, null, null, null);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir,
                                  Path mapWebDir, Path mapTilesDir) {
        this(bindHost, port, packsDir, mapWebDir, mapTilesDir, null, null, null);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir,
                                  Path mapWebDir, Path mapTilesDir, Path mapMeshesDir) {
        this(bindHost, port, packsDir, mapWebDir, mapTilesDir, mapMeshesDir, null, null);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir,
                                  Path mapWebDir, Path mapTilesDir, Path mapMeshesDir,
                                  Path skinsDir) {
        this(bindHost, port, packsDir, mapWebDir, mapTilesDir, mapMeshesDir, skinsDir, null);
    }

    public ResourcePackHttpServer(String bindHost, int port, Path packsDir,
                                  Path mapWebDir, Path mapTilesDir, Path mapMeshesDir,
                                  Path skinsDir, SkinService skinService) {
        this.bindHost = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost;
        this.port = port;
        this.packsDir = packsDir;
        this.mapWebDir = mapWebDir;
        this.mapTilesDir = mapTilesDir;
        this.mapMeshesDir = mapMeshesDir;
        this.skinsDir = skinsDir;
        this.skinService = skinService;
    }

    public Path getSkinsDir() {
        return skinsDir;
    }

    public int getPort() {
        return port;
    }

    public void setSkinService(SkinService skinService) {
        this.skinService = skinService;
    }

    /** Optional override; default applies via {@link SkinService#putCanonical}. */
    public void setSkinApplyHandler(BiConsumer<String, BedrockCanonicalSkin> handler) {
        this.skinApplyHandler = handler;
    }

    public void setEmotePlayHandler(EmotePlayHandler handler) {
        this.emotePlayHandler = handler;
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
        if (mapMeshesDir != null) {
            Files.createDirectories(mapMeshesDir);
            http.createContext("/meshes/", this::serveMapMeshes);
        }
        if (skinsDir != null) {
            Files.createDirectories(skinsDir);
            http.createContext("/skin/", this::serveSkin);
        }
        http.createContext("/skin/apply", this::handleSkinApply);
        http.createContext("/emote/play", this::handleEmotePlay);
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
        if (skinsDir != null) {
            LOG.info("Skin HTTP at /skin/ (dir=" + skinsDir.toAbsolutePath() + ")");
        }
        LOG.info("Skin apply POST at /skin/apply");
        LOG.info("Emote play POST at /emote/play");
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
            LOG.info("Resource pack HTTP server stopped");
        }
    }

    private void handleEmotePlay(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", "POST");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bodyBytes;
            try (InputStream in = exchange.getRequestBody()) {
                bodyBytes = in.readAllBytes();
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String username = o.has("username") && !o.get("username").isJsonNull()
                    ? o.get("username").getAsString() : "";
            String emoteId = o.has("emoteId") && !o.get("emoteId").isJsonNull()
                    ? o.get("emoteId").getAsString() : "";
            if (username.isBlank() || emoteId.isBlank()) {
                writeJson(exchange, 400, "{\"ok\":false,\"error\":\"username and emoteId required\"}");
                return;
            }
            UUID uuid;
            if (o.has("uuid") && !o.get("uuid").isJsonNull() && !o.get("uuid").getAsString().isBlank()) {
                uuid = UUID.fromString(o.get("uuid").getAsString());
            } else {
                writeJson(exchange, 400, "{\"ok\":false,\"error\":\"uuid required\"}");
                return;
            }
            EmotePlayHandler handler = emotePlayHandler;
            if (handler == null) {
                writeJson(exchange, 503, "{\"ok\":false,\"error\":\"emote service not wired\"}");
                return;
            }
            boolean ok = handler.play(username, uuid, emoteId.trim());
            if (!ok) {
                writeJson(exchange, 400, "{\"ok\":false,\"error\":\"emote rejected (unknown or cooldown)\"}");
                return;
            }
            writeJson(exchange, 200, "{\"ok\":true,\"emoteId\":\"" + emoteId.replace("\"", "") + "\"}");
            LOG.info("Emote play via HTTP for " + username + " id=" + emoteId);
        } catch (Exception e) {
            LOG.warning("POST /emote/play failed: " + e.getMessage());
            writeJson(exchange, 400, "{\"ok\":false,\"error\":\""
                    + (e.getMessage() == null ? "bad request" : e.getMessage().replace("\"", "'"))
                    + "\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleSkinApply(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", "POST");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bodyBytes;
            try (InputStream in = exchange.getRequestBody()) {
                bodyBytes = in.readAllBytes();
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String username = o.has("username") && !o.get("username").isJsonNull()
                    ? o.get("username").getAsString() : "";
            if (username.isBlank()) {
                writeJson(exchange, 400, "{\"ok\":false,\"error\":\"username required\"}");
                return;
            }
            BedrockCanonicalSkin skin;
            if (o.has("bedrockCanonicalJson") && !o.get("bedrockCanonicalJson").isJsonNull()
                    && !o.get("bedrockCanonicalJson").getAsString().isBlank()) {
                skin = BedrockCanonicalSkin.fromJson(o.get("bedrockCanonicalJson").getAsString());
            } else {
                UUID uuid;
                if (o.has("uuid") && !o.get("uuid").isJsonNull() && !o.get("uuid").getAsString().isBlank()) {
                    uuid = UUID.fromString(o.get("uuid").getAsString());
                } else {
                    writeJson(exchange, 400, "{\"ok\":false,\"error\":\"uuid required\"}");
                    return;
                }
                boolean slim = o.has("slim") && !o.get("slim").isJsonNull() && o.get("slim").getAsBoolean();
                byte[] skinPng = decodeB64Field(o, "skinPngBase64");
                byte[] capePng = decodeB64Field(o, "capePngBase64");
                skin = BedrockCanonicalSkin.classicPng(uuid, skinPng, capePng, slim).ensureGeometryData();
            }
            BiConsumer<String, BedrockCanonicalSkin> handler = skinApplyHandler;
            SkinService svc = skinService;
            if (handler != null) {
                handler.accept(username, skin);
            } else if (svc != null) {
                svc.putCanonical(username, skin);
            } else {
                writeJson(exchange, 503, "{\"ok\":false,\"error\":\"skin service not wired\"}");
                return;
            }
            writeJson(exchange, 200, "{\"ok\":true,\"username\":\"" + username.replace("\"", "")
                    + "\",\"contentSha256\":\"" + skin.ensureGeometryData().contentSha256() + "\"}");
            LOG.info("Skin apply via HTTP for " + username);
        } catch (Exception e) {
            LOG.warning("POST /skin/apply failed: " + e.getMessage());
            writeJson(exchange, 400, "{\"ok\":false,\"error\":\""
                    + (e.getMessage() == null ? "bad request" : e.getMessage().replace("\"", "'"))
                    + "\"}");
        } finally {
            exchange.close();
        }
    }

    private static byte[] decodeB64Field(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        String s = o.get(key).getAsString();
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void writeJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
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
        serveSafeFile(exchange, mapTilesDir, "/tiles/", "image/png");
    }

    private void serveMapMeshes(HttpExchange exchange) throws IOException {
        serveSafeFile(exchange, mapMeshesDir, "/meshes/", null);
    }

    private void serveSkin(HttpExchange exchange) throws IOException {
        serveSafeFile(exchange, skinsDir, "/skin/", "image/png");
    }

    private static void serveSafeFile(HttpExchange exchange, Path rootDir, String prefix, String forcedContentType)
            throws IOException {
        try {
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
            String ct = forcedContentType != null ? forcedContentType : contentType(rel);
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
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".ymesh")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }
}
