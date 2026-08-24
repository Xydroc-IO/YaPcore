package com.yapcore.link.metrics;

import com.sun.net.httpserver.HttpServer;
import com.yapcore.link.LinkServer;
import com.yapcore.link.plugin.LinkMetricsImpl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Lightweight Prometheus scrape endpoint for YaP Link. */
public final class LinkMetricsHttp {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Metrics");

    private final LinkServer server;
    private HttpServer http;

    public LinkMetricsHttp(LinkServer server) {
        this.server = server;
    }

    public synchronized void start(String bindHost, int port) throws IOException {
        if (http != null || port <= 0) {
            return;
        }
        InetSocketAddress addr = new InetSocketAddress(
                "0.0.0.0".equals(bindHost) ? "0.0.0.0" : bindHost, port);
        http = HttpServer.create(addr, 0);
        http.createContext("/metrics", ex -> {
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
        });
        http.createContext("/health", ex -> {
            byte[] body = "ok\n".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "yap-link-metrics");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        LOG.info("Link /metrics on http://" + ("0.0.0.0".equals(bindHost) ? "127.0.0.1" : bindHost)
                + ":" + port + "/metrics");
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    private String render() {
        LinkMetricsImpl m = server.metrics();
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("yap_link_players_joined", m.counter("players.joined"));
        counters.put("yap_link_players_left", m.counter("players.left"));
        counters.put("yap_link_connect_throttled", m.counter("connect.throttled"));
        counters.put("yap_link_connect_accepted", m.counter("connect.accepted"));
        counters.put("yap_link_handshake_dropped", m.counter("handshake.dropped"));
        counters.put("yap_link_login_dropped", m.counter("login.dropped"));
        counters.put("yap_link_login_attempts", m.counter("login.attempts"));
        counters.put("yap_link_plugin_messages", m.counter("plugin.messages"));

        Map<String, Long> gauges = new LinkedHashMap<>();
        gauges.put("yap_link_players_online", m.gauge("players.online"));
        gauges.put("yap_link_sessions", (long) server.sessions().size());
        if (server.rateGuard() != null) {
            server.rateGuard().snapshot().forEach((k, v) -> gauges.put("yap_link_rate_" + k, v));
        }
        return PrometheusText.render("", counters, gauges);
    }
}
