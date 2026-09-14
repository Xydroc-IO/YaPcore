package com.yapcore.fleet.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Standalone fleet agent HTTP API (bearer token). Manages Folia instance dirs under a home path.
 */
public final class FleetAgentHttp {

    private static final Logger LOG = Logger.getLogger("YaP.FleetAgent");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final Path home;
    private final String nodeId;
    private final String token;
    private final ConcurrentHashMap<String, AgentInstanceProcess> processes = new ConcurrentHashMap<>();
    private HttpServer http;

    public FleetAgentHttp(Path home, String nodeId, String token) {
        this.home = home.toAbsolutePath().normalize();
        this.nodeId = nodeId;
        this.token = token;
    }

    public synchronized void start(String bind, int port) throws IOException {
        if (http != null) {
            return;
        }
        Files.createDirectories(home.resolve("instances"));
        http = HttpServer.create(new InetSocketAddress(bind, port), 0);
        http.createContext("/v1/health", this::health);
        http.createContext("/v1/instances", this::instances);
        http.createContext("/v1/players", this::players);
        http.createContext("/v1/command", this::command);
        http.createContext("/v1/deploy", this::deploy);
        http.createContext("/v1/console/stream", this::consoleStream);
        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "yap-fleet-agent");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        LOG.info("yap-fleet-agent " + nodeId + " on http://" + bind + ":" + port);
    }

    public synchronized void stop() {
        processes.values().forEach(AgentInstanceProcess::stop);
        processes.clear();
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    private void health(HttpExchange ex) throws IOException {
        if (!auth(ex) || !method(ex, "GET")) {
            return;
        }
        json(ex, 200, Map.of(
                "ok", true,
                "nodeId", nodeId,
                "role", "yap-fleet-agent",
                "ts", System.currentTimeMillis()));
    }

    private void instances(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        String path = ex.getRequestURI().getPath();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/v1/instances".equals(path)) {
            List<Map<String, Object>> list = new ArrayList<>();
            Path root = home.resolve("instances");
            if (Files.isDirectory(root)) {
                try (var stream = Files.list(root)) {
                    for (Path dir : stream.toList()) {
                        if (!Files.isDirectory(dir)) {
                            continue;
                        }
                        String id = dir.getFileName().toString();
                        AgentInstanceProcess p = processes.get(id);
                        list.add(Map.of(
                                "id", id,
                                "running", p != null && p.isRunning(),
                                "dir", dir.toString()));
                    }
                }
            }
            json(ex, 200, Map.of("ok", true, "instances", list));
            return;
        }
        String[] parts = path.split("/");
        // "", "v1", "instances", "{id}", "start|stop|restart|plugins"
        if (parts.length < 5) {
            json(ex, 404, error("not found"));
            return;
        }
        String id = parts[3];
        String action = parts[4];
        try {
            if ("plugins".equals(action) && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 200, listPlugins(id));
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 405, error("method not allowed"));
                return;
            }
            if ("start".equals(action)) {
                startInstance(id);
                json(ex, 200, Map.of("ok", true, "action", "start", "instanceId", id));
            } else if ("stop".equals(action)) {
                stopInstance(id);
                json(ex, 200, Map.of("ok", true, "action", "stop", "instanceId", id));
            } else if ("restart".equals(action)) {
                stopInstance(id);
                Thread.sleep(750);
                startInstance(id);
                json(ex, 200, Map.of("ok", true, "action", "restart", "instanceId", id));
            } else {
                json(ex, 404, error("unknown action"));
            }
        } catch (Exception e) {
            json(ex, 400, error(e.getMessage()));
        }
    }

    private Map<String, Object> listPlugins(String id) throws IOException {
        Path dir = home.resolve("instances").resolve(id).resolve("plugins");
        List<Map<String, Object>> jars = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString().toLowerCase();
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    if (!(name.endsWith(".jar") || name.endsWith(".jar.disabled"))) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("fileName", p.getFileName().toString());
                    row.put("hardEnabled", !name.endsWith(".jar.disabled"));
                    row.put("sizeBytes", Files.size(p));
                    jars.add(row);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("instanceId", id);
        out.put("plugins", jars);
        return out;
    }

    private void players(HttpExchange ex) throws IOException {
        if (!auth(ex) || !method(ex, "GET")) {
            return;
        }
        // Agent does not scrape Folia tab lists yet — empty list is honest (chassis uses Link health).
        json(ex, 200, Map.of("ok", true, "players", List.of(), "nodeId", nodeId));
    }

    private void command(HttpExchange ex) throws IOException {
        if (!auth(ex) || !method(ex, "POST")) {
            return;
        }
        Map<String, Object> body = readJson(ex);
        String id = String.valueOf(body.getOrDefault("instanceId", ""));
        String cmd = String.valueOf(body.getOrDefault("command", ""));
        AgentInstanceProcess p = processes.get(id);
        if (p == null || !p.isRunning()) {
            json(ex, 409, error("instance not running: " + id));
            return;
        }
        String result = p.dispatch(cmd);
        json(ex, 200, Map.of("ok", true, "result", result));
    }

    private void deploy(HttpExchange ex) throws IOException {
        if (!auth(ex) || !method(ex, "POST")) {
            return;
        }
        Map<String, Object> body = readJson(ex);
        String id = String.valueOf(body.getOrDefault("instanceId", ""));
        String fileName = String.valueOf(body.getOrDefault("fileName", "plugins/deploy.jar"));
        String b64 = String.valueOf(body.getOrDefault("contentBase64", ""));
        if (id.isBlank() || b64.isBlank()) {
            json(ex, 400, error("instanceId and contentBase64 required"));
            return;
        }
        Path dest = home.resolve("instances").resolve(id).resolve(fileName).normalize();
        if (!dest.startsWith(home.resolve("instances").resolve(id))) {
            json(ex, 400, error("invalid fileName"));
            return;
        }
        Files.createDirectories(dest.getParent());
        Files.write(dest, Base64.getDecoder().decode(b64));
        json(ex, 200, Map.of("ok", true, "path", dest.toString(),
                "checksum", String.valueOf(body.getOrDefault("checksum", ""))));
    }

    private void consoleStream(HttpExchange ex) throws IOException {
        if (!auth(ex) || !method(ex, "GET")) {
            return;
        }
        String id = query(ex, "id");
        AgentInstanceProcess p = id == null ? null : processes.get(id);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            String initial = p == null ? "(not running)\n" : p.recentLogs();
            out.write(("data: " + initial.replace("\n", "\ndata: ") + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            if (p != null) {
                p.addLogListener(line -> {
                    try {
                        out.write(("data: " + line).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (IOException ignored) {
                    }
                });
            }
            while (true) {
                Thread.sleep(15_000);
                out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
            // client gone
        }
    }

    private void startInstance(String id) throws IOException, InterruptedException {
        Path dir = home.resolve("instances").resolve(id);
        Files.createDirectories(dir);
        AgentInstanceProcess proc = processes.computeIfAbsent(id, k -> new AgentInstanceProcess(id, dir));
        if (proc.isRunning()) {
            return;
        }
        Path jar = AgentInstanceProcess.findJar(dir, home);
        if (jar == null) {
            throw new IOException("No Folia jar in " + dir + " or " + home.resolve("lib"));
        }
        int port = AgentInstanceProcess.readPort(dir, 25566);
        proc.start(jar, port);
    }

    private void stopInstance(String id) {
        AgentInstanceProcess p = processes.get(id);
        if (p != null) {
            p.stop();
        }
    }

    private boolean auth(HttpExchange ex) throws IOException {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String bearer = null;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            bearer = header.substring(7).trim();
        }
        if (bearer == null || !bearer.equals(token)) {
            json(ex, 401, error("unauthorized"));
            return false;
        }
        return true;
    }

    private boolean method(HttpExchange ex, String expected) throws IOException {
        if (!expected.equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, error("method not allowed"));
            return false;
        }
        return true;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }

    private static void json(HttpExchange ex, int code, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, Object> readJson(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> m = GSON.fromJson(raw, MAP_TYPE);
            return m == null ? new LinkedHashMap<>() : m;
        }
    }

    private static String query(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return part.substring(eq + 1);
            }
        }
        return null;
    }
}
