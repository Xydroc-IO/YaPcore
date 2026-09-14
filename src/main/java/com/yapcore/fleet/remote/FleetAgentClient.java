package com.yapcore.fleet.remote;

import com.yapcore.fleet.model.FleetNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP client for remote {@code yap-fleet-agent} nodes. */
public final class FleetAgentClient {

    private static final int TIMEOUT_MS = 15_000;

    private final Path rootDir;

    public FleetAgentClient(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    public Map<String, Object> health(FleetNode node) throws IOException {
        return get(node, "/v1/health");
    }

    public List<Map<String, Object>> instances(FleetNode node) throws IOException {
        Map<String, Object> resp = get(node, "/v1/instances");
        return AgentProtocol.asObjectList(resp.get("instances"));
    }

    public Map<String, Object> startInstance(FleetNode node, String id) throws IOException {
        return post(node, "/v1/instances/" + id + "/start", Map.of());
    }

    public Map<String, Object> stopInstance(FleetNode node, String id) throws IOException {
        return post(node, "/v1/instances/" + id + "/stop", Map.of());
    }

    public Map<String, Object> restartInstance(FleetNode node, String id) throws IOException {
        return post(node, "/v1/instances/" + id + "/restart", Map.of());
    }

    public Map<String, Object> listPlugins(FleetNode node, String id) throws IOException {
        return get(node, "/v1/instances/" + id + "/plugins");
    }

    public Map<String, Object> command(FleetNode node, String id, String line) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceId", id);
        body.put("command", line);
        return post(node, "/v1/command", body);
    }

    public List<Map<String, Object>> players(FleetNode node) throws IOException {
        Map<String, Object> resp = get(node, "/v1/players");
        return AgentProtocol.asObjectList(resp.get("players"));
    }

    public Map<String, Object> deploy(
            FleetNode node, String instanceId, String fileName, byte[] bytes, String checksum)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceId", instanceId);
        body.put("fileName", fileName);
        body.put("checksum", checksum == null ? "" : checksum);
        body.put("contentBase64", java.util.Base64.getEncoder().encodeToString(bytes));
        return post(node, "/v1/deploy", body);
    }

    public String readToken(FleetNode node) throws IOException {
        Path tokenFile = rootDir.resolve(node.tokenRef()).normalize();
        if (!tokenFile.startsWith(rootDir.resolve("fleet"))) {
            throw new IOException("tokenRef must stay under fleet/: " + node.tokenRef());
        }
        if (!Files.isRegularFile(tokenFile)) {
            throw new IOException("Missing agent token file: " + tokenFile);
        }
        return Files.readString(tokenFile).trim();
    }

    private Map<String, Object> get(FleetNode node, String path) throws IOException {
        return exchange(node, "GET", path, null);
    }

    private Map<String, Object> post(FleetNode node, String path, Map<String, Object> body)
            throws IOException {
        return exchange(node, "POST", path, body);
    }

    private Map<String, Object> exchange(
            FleetNode node, String method, String path, Map<String, Object> body) throws IOException {
        String token = readToken(node);
        URI uri = URI.create(node.baseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod(method);
        conn.setRequestProperty(AgentProtocol.AUTH_HEADER, AgentProtocol.BEARER_PREFIX + token);
        conn.setRequestProperty("Accept", "application/json");
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] payload = AgentProtocol.toJson(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String raw = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = AgentProtocol.parseJsonObject(raw);
        if (code >= 400) {
            String err = String.valueOf(parsed.getOrDefault("error", "HTTP " + code));
            throw new IOException("Agent " + node.id() + " " + path + ": " + err);
        }
        parsed.putIfAbsent("ok", true);
        parsed.put("httpStatus", code);
        return parsed;
    }
}
