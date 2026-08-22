package com.yapcore.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yapcore.config.ServerConfig;
import com.yapcore.console.ConsoleBus;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.api.DashboardConsoleApi;
import com.yapcore.web.api.DashboardGameplayApi;
import com.yapcore.web.api.DashboardPluginsApi;
import com.yapcore.web.api.DashboardStatusApi;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Headless control dashboard — mirrors Swing {@code ControlPanel} over HTTP.
 * Default port {@code 8080} (pack HTTP stays on 8081).
 */
public final class WebDashboard {

    private static final Logger LOG = Logger.getLogger("YaPcore.WebDash");

    private final YaPcoreServer server;
    private final DashboardAuth auth = new DashboardAuth();
    private final DashboardStatusApi statusApi;
    private final DashboardPluginsApi pluginsApi;
    private final DashboardConsoleApi consoleApi;
    private final DashboardGameplayApi gameplayApi;
    private final Consumer<String> consoleListener;
    private HttpServer http;

    public WebDashboard(YaPcoreServer server) {
        this.server = server;
        this.statusApi = new DashboardStatusApi(server, auth);
        this.pluginsApi = new DashboardPluginsApi(server, auth);
        this.consoleApi = new DashboardConsoleApi(auth);
        this.gameplayApi = new DashboardGameplayApi(server, auth);
        this.consoleListener = line -> consoleApi.broadcastSse(line);
    }

    public static WebDashboard maybeStart(YaPcoreServer server) {
        ServerConfig cfg = server.getConfig();
        if (!cfg.isWebDashboardEnabled()) {
            LOG.info("Web dashboard disabled (web-dashboard-enabled=false)");
            return null;
        }
        WebDashboard dash = new WebDashboard(server);
        try {
            dash.start();
            return dash;
        } catch (IOException e) {
            LOG.severe("Web dashboard failed to start: " + e.getMessage());
            return null;
        }
    }

    public synchronized void start() throws IOException {
        if (http != null) {
            return;
        }
        ServerConfig cfg = server.getConfig();
        auth.ensureToken(cfg);
        String bind = cfg.getWebDashboardBind();
        if (cfg.isWebDashboardLocalhostOnly()) {
            bind = "127.0.0.1";
        }
        int port = cfg.getWebDashboardPort();
        InetSocketAddress addr = new InetSocketAddress(
                "0.0.0.0".equals(bind) ? "0.0.0.0" : bind, port);
        http = HttpServer.create(addr, 0);

        http.createContext("/", this::serveStatic);
        http.createContext("/api/status", statusApi::apiStatus);
        http.createContext("/api/connect", statusApi::apiConnect);
        http.createContext("/api/config", statusApi::apiConfig);
        http.createContext("/api/server/start", statusApi::apiStart);
        http.createContext("/api/server/stop", statusApi::apiStop);
        http.createContext("/api/command", statusApi::apiCommand);
        http.createContext("/api/plugins", pluginsApi::apiPlugins);
        http.createContext("/api/modules", pluginsApi::apiModules);
        http.createContext("/api/packs", pluginsApi::apiPacks);
        http.createContext("/api/console", consoleApi::apiConsole);
        http.createContext("/api/console/stream", consoleApi::apiConsoleStream);
        http.createContext("/api/vehicles", gameplayApi::apiVehicles);
        http.createContext("/api/pregen", gameplayApi::apiPregen);
        http.createContext("/api/ranks", gameplayApi::apiRanks);
        http.createContext("/api/essentials", gameplayApi::apiEssentials);
        http.createContext("/api/link", gameplayApi::apiLink);
        http.createContext("/api/protect", gameplayApi::apiProtect);
        http.createContext("/api/world", gameplayApi::apiWorld);
        http.createContext("/api/chat", gameplayApi::apiChat);
        http.createContext("/api/moderation", gameplayApi::apiModeration);
        http.createContext("/api/perms", gameplayApi::apiPerms);
        http.createContext("/api/playerdata", gameplayApi::apiPlayerdata);
        http.createContext("/api/discord", gameplayApi::apiDiscord);
        http.createContext("/api/tab", gameplayApi::apiTab);
        http.createContext("/api/map", gameplayApi::apiMap);
        http.createContext("/api/guard", gameplayApi::apiGuard);
        http.createContext("/api/regions", gameplayApi::apiRegions);
        http.createContext("/api/npcs", gameplayApi::apiNpcs);
        http.createContext("/health", ex -> DashboardHttp.text(ex, 200, "ok"));

        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "yap-web-dash");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        ConsoleBus.get().addListener(consoleListener);
        LOG.info("Web dashboard http://" + ("0.0.0.0".equals(bind) ? "127.0.0.1" : bind)
                + ":" + port + "/  (token required — see web-dashboard-token in config)");
        LOG.info("Dashboard login token: " + auth.getToken());
    }

    public synchronized void stop() {
        ConsoleBus.get().removeListener(consoleListener);
        consoleApi.closeAllClients();
        if (http != null) {
            http.stop(0);
            http = null;
            LOG.info("Web dashboard stopped");
        }
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            ex.sendResponseHeaders(400, -1);
            return;
        }
        String resource = "web" + path;
        try (InputStream in = WebDashboard.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                DashboardHttp.text(ex, 404, "not found");
                return;
            }
            byte[] body = in.readAllBytes();
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", DashboardHttp.contentType(path));
            h.set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
