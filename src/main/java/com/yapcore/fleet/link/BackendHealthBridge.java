package com.yapcore.fleet.link;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.fleet.model.BackendHealth;
import com.yapcore.web.DashboardLinkSnapshot;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live backend health for the control plane: prefers Link {@code GET /backends}, else TCP connect
 * probe of configured {@code servers.*} addresses (not a stub — real connectivity check).
 */
public final class BackendHealthBridge {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Health");
    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 2500;

    private BackendHealthBridge() {
    }

    public static List<BackendHealth> collect(Path rootDir, String linkEmbedHome, boolean linkRunning) {
        List<BackendHealth> fromLink = fetchFromLinkHttp(rootDir, linkEmbedHome);
        if (!fromLink.isEmpty()) {
            return fromLink;
        }
        return tcpProbeConfigured(rootDir, linkEmbedHome, linkRunning);
    }

    public static List<Map<String, Object>> collectMaps(
            Path rootDir, String linkEmbedHome, boolean linkRunning) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BackendHealth h : collect(rootDir, linkEmbedHome, linkRunning)) {
            out.add(h.toMap());
        }
        return out;
    }

    static List<BackendHealth> fetchFromLinkHttp(Path rootDir, String linkEmbedHome) {
        Path home = DashboardLinkSnapshot.resolveHome(rootDir, linkEmbedHome);
        Path propsFile = home.resolve("link.properties");
        if (!Files.isRegularFile(propsFile)) {
            return List.of();
        }
        Properties p = loadProps(propsFile);
        if (!"true".equalsIgnoreCase(p.getProperty("metrics-http-enabled", "true"))) {
            return List.of();
        }
        String bind = p.getProperty("metrics-http-bind", "127.0.0.1").trim();
        if ("0.0.0.0".equals(bind) || "::".equals(bind)) {
            bind = "127.0.0.1";
        }
        int port;
        try {
            port = Integer.parseInt(p.getProperty("metrics-http-port", "9091").trim());
        } catch (NumberFormatException e) {
            return List.of();
        }
        if (port <= 0) {
            return List.of();
        }
        String url = "http://" + bind + ":" + port + "/backends";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return List.of();
            }
            try (InputStreamReader reader =
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray arr = root.has("backends") && root.get("backends").isJsonArray()
                        ? root.getAsJsonArray("backends")
                        : new JsonArray();
                List<BackendHealth> list = new ArrayList<>();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = GSON.fromJson(el, Map.class);
                    BackendHealth h = BackendHealth.fromMap(map, "link-http");
                    if (h != null && !h.name().isBlank()) {
                        list.add(h);
                    }
                }
                return list;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Link /backends unreachable: " + e.getMessage());
            return List.of();
        }
    }

    static List<BackendHealth> tcpProbeConfigured(
            Path rootDir, String linkEmbedHome, boolean linkRunning) {
        Map<String, Object> snap = DashboardLinkSnapshot.snapshot(
                rootDir, linkEmbedHome, false, true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) snap.get("servers");
        if (servers == null || servers.isEmpty()) {
            return List.of();
        }
        List<BackendHealth> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map<String, Object> s : servers) {
            String name = String.valueOf(s.getOrDefault("name", ""));
            String address = String.valueOf(s.getOrDefault("address", ""));
            HostPort hp = HostPort.parse(address);
            if (name.isBlank() || hp == null) {
                continue;
            }
            long t0 = System.nanoTime();
            String err = null;
            boolean up = false;
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(hp.host(), hp.port()), CONNECT_TIMEOUT_MS);
                up = true;
            } catch (Exception e) {
                err = e.getMessage();
            }
            long latency = up ? Math.max(0L, (System.nanoTime() - t0) / 1_000_000L) : -1L;
            String source = linkRunning ? "tcp-fallback" : "tcp-link-down";
            out.add(new BackendHealth(
                    name, up, 0, 0, latency, now, err, "", 0, "", source));
        }
        return out;
    }

    private static Properties loadProps(Path file) {
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        } catch (Exception ignored) {
            // empty
        }
        return p;
    }

    record HostPort(String host, int port) {
        static HostPort parse(String address) {
            if (address == null || address.isBlank()) {
                return null;
            }
            String a = address.trim();
            int colon = a.lastIndexOf(':');
            if (colon <= 0 || colon == a.length() - 1) {
                return null;
            }
            try {
                return new HostPort(a.substring(0, colon), Integer.parseInt(a.substring(colon + 1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
