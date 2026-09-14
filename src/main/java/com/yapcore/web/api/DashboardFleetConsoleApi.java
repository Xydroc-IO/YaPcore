package com.yapcore.web.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.yapcore.fleet.local.LocalInstanceSupervisor;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Fleet instance console SSE + recent text. */
public final class DashboardFleetConsoleApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;
    private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

    public DashboardFleetConsoleApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiConsole(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        String id = queryParam(ex, "id");
        if (id == null || id.isBlank()) {
            DashboardHttp.json(ex, 400, Map.of("error", "id required"));
            return;
        }
        Optional<LocalInstanceSupervisor> sup = server.fleet().localSupervisor(id);
        String text = sup.map(LocalInstanceSupervisor::recentLogs).orElse("");
        DashboardHttp.json(ex, 200, Map.of("ok", true, "id", id, "text", text));
    }

    public void apiConsoleStream(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        String id = queryParam(ex, "id");
        if (id == null || id.isBlank()) {
            DashboardHttp.json(ex, 400, Map.of("error", "id required"));
            return;
        }
        Optional<LocalInstanceSupervisor> sup = server.fleet().localSupervisor(id);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/event-stream; charset=utf-8");
        h.set("Cache-Control", "no-cache");
        h.set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();
        SseClient client = new SseClient(id, out);
        clients.add(client);
        if (sup.isPresent() && sup.get().process() != null) {
            DashboardHttp.writeSse(out, sup.get().recentLogs());
            Consumer<String> listener = line -> {
                try {
                    DashboardHttp.writeSse(out, line);
                } catch (IOException e) {
                    clients.remove(client);
                }
            };
            client.listener = listener;
            sup.get().process().addLogListener(listener);
        } else {
            DashboardHttp.writeSse(out, "(instance not running locally: " + id + ")\n");
        }
        try {
            while (true) {
                Thread.sleep(15_000);
                out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            clients.remove(client);
            if (client.listener != null && sup.isPresent() && sup.get().process() != null) {
                sup.get().process().removeLogListener(client.listener);
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void closeAllClients() {
        for (SseClient c : clients) {
            try {
                c.out.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();
    }

    private static String queryParam(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static final class SseClient {
        final String id;
        final OutputStream out;
        Consumer<String> listener;

        SseClient(String id, OutputStream out) {
            this.id = id;
            this.out = out;
        }
    }
}
