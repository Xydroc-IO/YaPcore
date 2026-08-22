package com.yapcore.web.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.yapcore.console.ConsoleBus;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DashboardConsoleApi {

    private final DashboardAuth auth;
    private final CopyOnWriteArrayList<OutputStream> sseClients = new CopyOnWriteArrayList<>();

    public DashboardConsoleApi(DashboardAuth auth) {
        this.auth = auth;
    }

    public void broadcastSse(String line) {
        if (line == null) {
            return;
        }
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream out : sseClients) {
            try {
                DashboardHttp.writeSse(out, line);
            } catch (IOException e) {
                dead.add(out);
            }
        }
        sseClients.removeAll(dead);
    }

    public void closeAllClients() {
        for (OutputStream out : sseClients) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        sseClients.clear();
    }

    public void apiConsole(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        DashboardHttp.json(ex, 200, Map.of("text", ConsoleBus.get().getRecentText()));
    }

    public void apiConsoleStream(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/event-stream; charset=utf-8");
        h.set("Cache-Control", "no-cache");
        h.set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();
        sseClients.add(out);
        DashboardHttp.writeSse(out, ConsoleBus.get().getRecentText());
        try {
            while (true) {
                Thread.sleep(15_000);
                DashboardHttp.writeSse(out, "");
                out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            sseClients.remove(out);
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }
}
