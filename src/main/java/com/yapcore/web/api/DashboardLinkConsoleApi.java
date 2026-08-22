package com.yapcore.web.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.LinkProcessManager;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** SSE + snapshot for YaP Link process console (separate from main YaPcore console). */
public final class DashboardLinkConsoleApi {

    private final DashboardAuth auth;
    private final LinkProcessManager linkProcess;
    private final CopyOnWriteArrayList<OutputStream> sseClients = new CopyOnWriteArrayList<>();

    public DashboardLinkConsoleApi(DashboardAuth auth, LinkProcessManager linkProcess) {
        this.auth = auth;
        this.linkProcess = linkProcess;
    }

    public void broadcastSse(String line) {
        if (line == null) {
            return;
        }
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream out : sseClients) {
            try {
                DashboardHttp.writeSse(out, line.trim());
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

    public void apiLinkConsole(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        DashboardHttp.json(ex, 200, Map.of(
                "text", linkProcess.getRecentText(),
                "running", linkProcess.isRunning(),
                "linkEmbed", linkProcess.isLinkEmbed()));
    }

    public void apiLinkConsoleStream(HttpExchange ex) throws IOException {
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
        String recent = linkProcess.getRecentText();
        if (!recent.isBlank()) {
            DashboardHttp.writeSse(out, recent);
        }
        try {
            while (true) {
                Thread.sleep(15_000);
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
