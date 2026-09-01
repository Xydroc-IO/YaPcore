package com.yapcore.world.web;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditTool;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/** Serves the browser world-edit studio + session API. */
public final class WorldEditHttpServer {

    private static final Logger LOG = Logger.getLogger("YaPWorld.Editor");

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldEditSessionRegistry sessions;
    private final WorldEditActionHandler actions;
    private HttpServer http;

    public WorldEditHttpServer(WorldPlugin plugin, WorldConfig config, WorldEditSessionRegistry sessions,
                               WorldManagerServiceImpl worldManager, SelectionServiceImpl selection,
                               BrushService brushService, SelectionEditService selectionEdit,
                               UndoService undoService, SchematicPaster paster, WorldEditTool tool) {
        this.plugin = plugin;
        this.config = config;
        this.sessions = sessions;
        this.actions = new WorldEditActionHandler(plugin, config, worldManager, selection, brushService,
                selectionEdit, undoService, paster, tool);
    }

    public synchronized void start() throws IOException {
        if (http != null || !config.editorEnabled()) {
            return;
        }
        String bind = config.editorBind();
        int port = config.editorPort();
        InetSocketAddress addr = new InetSocketAddress("0.0.0.0".equals(bind) ? "0.0.0.0" : bind, port);
        http = HttpServer.create(addr, 0);
        http.createContext("/editor/", this::serveStatic);
        http.createContext("/api/world-edit/state", this::apiState);
        http.createContext("/api/world-edit/action", this::apiAction);
        http.createContext("/api/world-edit/schematic/download", this::schematicDownload);
        http.createContext("/editor/health", ex -> text(ex, 200, "ok"));
        http.setExecutor(Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "yap-world-edit-http");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        LOG.info("World edit studio http://" + bind + ":" + port + "/editor/");
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    public String editorUrl(String token) {
        return "http://" + config.editorPublicHost() + ":" + config.editorPort()
                + "/editor/?token=" + token;
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String path = ex.getRequestURI().getPath();
        String rel = path.substring("/editor/".length());
        if (rel.isBlank()) {
            rel = "index.html";
        }
        if (rel.contains("..") || rel.contains("\\")) {
            ex.sendResponseHeaders(400, -1);
            return;
        }
        String resource = "/editor/" + rel;
        try (InputStream in = WorldEditHttpServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = in.readAllBytes();
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", contentType(rel));
            h.set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private void schematicDownload(HttpExchange ex) throws IOException {
        addCors(ex);
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Optional<WorldEditSessionRegistry.Entry> entry = resolveSession(ex);
        if (entry.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(entry.get().playerId());
        if (player == null || !player.isOnline() || !WorldPlugin.canUseEditor(player)) {
            json(ex, 403, Map.of("error", "forbidden"));
            return;
        }
        String name = query(ex, "name");
        Path file = actions.resolveSchematicFile(name);
        if (file == null || !Files.isRegularFile(file)) {
            json(ex, 404, Map.of("error", "not found"));
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/octet-stream");
        h.set("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void apiState(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        Optional<WorldEditSessionRegistry.Entry> entry = resolveSession(ex);
        if (entry.isEmpty()) {
            return;
        }
        UUID id = entry.get().playerId();
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            json(ex, 400, Map.of("error", "player offline"));
            return;
        }
        if (!WorldPlugin.canUseEditor(player)) {
            json(ex, 403, Map.of("error", "no permission"));
            return;
        }
        json(ex, 200, actions.buildState(id, entry.get().playerName(), player));
    }

    private void apiAction(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());
        String token = WorldEditJson.parseField(body, "token");
        Optional<WorldEditSessionRegistry.Entry> entry = sessions.resolve(token);
        if (entry.isEmpty()) {
            json(ex, 401, Map.of("error", "invalid session"));
            return;
        }
        UUID playerId = entry.get().playerId();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            json(ex, 400, Map.of("error", "player offline"));
            return;
        }
        if (!WorldPlugin.canUseEditor(player)) {
            json(ex, 403, Map.of("error", "no permission"));
            return;
        }
        String action = WorldEditJson.parseField(body, "action").toLowerCase(Locale.ROOT);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result =
                new AtomicReference<>(Map.of("error", "failed"));
        AtomicInteger status = new AtomicInteger(500);
        YapSched.global(plugin, () -> {
            try {
                actions.handle(player, action, body, result, status);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(45, TimeUnit.SECONDS)) {
                json(ex, 504, Map.of("error", "operation timed out"));
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            json(ex, 500, Map.of("error", "interrupted"));
            return;
        }
        json(ex, status.get(), result.get());
    }

    private Optional<WorldEditSessionRegistry.Entry> resolveSession(HttpExchange ex) throws IOException {
        String token = query(ex, "token");
        Optional<WorldEditSessionRegistry.Entry> entry = sessions.resolve(token);
        if (entry.isEmpty()) {
            json(ex, 401, Map.of("error", "invalid or expired session — run /yapworld editor in-game"));
            return Optional.empty();
        }
        return entry;
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return "";
        }
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return "";
    }

    private static void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void json(HttpExchange ex, int code, Map<String, ?> body) throws IOException {
        byte[] bytes = WorldEditJson.object(body);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }
}
