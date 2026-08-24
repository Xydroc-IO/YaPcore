package com.yapcore.web.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.util.ThreadMetrics;
import com.yapcore.web.TinyJson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chassis {@code GET /metrics} — Prometheus scrape (no auth; firewall it). */
public final class ChassisMetricsHandler {

    private final YaPcoreServer server;

    public ChassisMetricsHandler(YaPcoreServer server) {
        this.server = server;
    }

    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        byte[] body = render().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    public Map<String, Object> statusSnippet() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("players", server.getOnlinePlayers());
        m.put("ticks", server.getEngine().gameCore().getTickCounter());
        m.put("threadMetricKeys", ThreadMetrics.keyCount());
        m.put("lagguard", readLagGuardStats());
        return m;
    }

    private String render() {
        StringBuilder sb = new StringBuilder();
        Map<String, Long> counters = new LinkedHashMap<>();
        Map<String, Long> gauges = new LinkedHashMap<>();

        gauges.put("yapcore_players_online", (long) server.getOnlinePlayers());
        gauges.put("yapcore_players_max", (long) server.getMaxPlayers());
        gauges.put("yapcore_ticks", server.getEngine().gameCore().getTickCounter());
        gauges.put("yapcore_heap_used_mb",
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
        gauges.put("yapcore_link_process", server.getLinkProcess().isRunning() ? 1L : 0L);

        ThreadMetrics.snapshot().forEach((key, value) ->
                counters.put("yapcore_tm_" + key.replace(':', '_'), value));

        Map<String, Object> lag = readLagGuardStats();
        if (lag != null) {
            putLong(counters, "yapcore_lagguard_trips", lag.get("trips"));
            putLong(counters, "yapcore_lagguard_entities_cancelled", lag.get("entitiesCancelled"));
            putLong(counters, "yapcore_lagguard_tnt_cancelled", lag.get("tntCancelled"));
            putLong(counters, "yapcore_lagguard_hopper_throttled", lag.get("hopperThrottled"));
            putLong(counters, "yapcore_lagguard_redstone_throttled", lag.get("redstoneThrottled"));
            Object en = lag.get("enabled");
            gauges.put("yapcore_lagguard_enabled", Boolean.TRUE.equals(en) || "true".equals(String.valueOf(en)) ? 1L : 0L);
        }

        sb.append(PrometheusText.counters(counters));
        sb.append(PrometheusText.gauges(gauges));
        return sb.toString();
    }

    private Map<String, Object> readLagGuardStats() {
        Path file = server.getRootDir().resolve("plugins").resolve("YaPLagGuard").resolve("stats.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> flat = TinyJson.parseFlatObject(raw);
            if (flat.isEmpty()) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            flat.forEach((k, v) -> out.put(k, TinyJson.parseValue(v)));
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putLong(Map<String, Long> target, String key, Object value) {
        if (value instanceof Number n) {
            target.put(key, n.longValue());
        } else if (value != null) {
            try {
                target.put(key, Long.parseLong(value.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
