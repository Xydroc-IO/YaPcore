package com.yapcore.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.api.DashboardAccessApi;
import com.yapcore.web.api.DashboardAdminApi;
import com.yapcore.web.api.DashboardCommandsApi;
import com.yapcore.web.api.DashboardConsoleApi;
import com.yapcore.web.api.DashboardGameplayApi;
import com.yapcore.web.api.DashboardKitsApi;
import com.yapcore.web.api.DashboardLinkConsoleApi;
import com.yapcore.web.api.DashboardPlayersApi;
import com.yapcore.web.api.DashboardPluginsApi;
import com.yapcore.web.api.DashboardStatusApi;
import com.yapcore.web.http.DashboardHttp;
import com.yapcore.web.metrics.ChassisMetricsHandler;

import java.nio.file.Path;

/** Registers all WebDashboard HTTP contexts (paths unchanged). */
final class DashboardRouteRegistrar {

    private DashboardRouteRegistrar() {
    }

    static void register(
            HttpServer http,
            YaPcoreServer server,
            DashboardPlayersApi playersApi,
            DashboardAccessApi accessApi,
            DashboardAdminApi adminApi,
            DashboardStatusApi statusApi,
            DashboardPluginsApi pluginsApi,
            DashboardConsoleApi consoleApi,
            DashboardLinkConsoleApi linkConsoleApi,
            DashboardGameplayApi gameplayApi,
            DashboardKitsApi kitsApi,
            DashboardCommandsApi commandsApi,
            ChassisMetricsHandler metricsHandler,
            HttpHandler serveStatic) {
        Path rootDir = server.getRootDir();

        http.createContext("/map/", DashboardMapServe.mapStatic(rootDir, server));
        http.createContext("/tiles/", DashboardMapServe.mapTiles(rootDir));
        http.createContext("/meshes/", DashboardMapServe.mapMeshes(rootDir));
        http.createContext("/", serveStatic);
        http.createContext("/api/players", playersApi::apiPlayers);
        http.createContext("/api/access", accessApi::apiAccess);
        http.createContext("/api/admin", adminApi::apiAdmin);
        http.createContext("/api/status", statusApi::apiStatus);
        http.createContext("/api/connect", statusApi::apiConnect);
        http.createContext("/api/config", statusApi::apiConfig);
        http.createContext("/api/server/start", statusApi::apiStart);
        http.createContext("/api/server/stop", statusApi::apiStop);
        http.createContext("/api/command", statusApi::apiCommand);
        http.createContext("/api/plugins", pluginsApi::apiPlugins);
        http.createContext("/api/plugin-config", pluginsApi::apiPluginConfig);
        http.createContext("/api/modules", pluginsApi::apiModules);
        http.createContext("/api/packs", pluginsApi::apiPacks);
        http.createContext("/api/console", consoleApi::apiConsole);
        http.createContext("/api/console/stream", consoleApi::apiConsoleStream);
        http.createContext("/api/pregen", gameplayApi::apiPregen);
        http.createContext("/api/ranks", gameplayApi::apiRanks);
        http.createContext("/api/essentials", gameplayApi::apiEssentials);
        http.createContext("/api/link", gameplayApi::apiLink);
        http.createContext("/api/link/console", linkConsoleApi::apiLinkConsole);
        http.createContext("/api/link/console/stream", linkConsoleApi::apiLinkConsoleStream);
        http.createContext("/api/protect", gameplayApi::apiProtect);
        http.createContext("/api/disasters", gameplayApi::apiDisasters);
        http.createContext("/api/stacker", gameplayApi::apiStacker);
        http.createContext("/api/world", gameplayApi::apiWorld);
        http.createContext("/api/chat", gameplayApi::apiChat);
        http.createContext("/api/moderation", gameplayApi::apiModeration);
        http.createContext("/api/perms", gameplayApi::apiPerms);
        http.createContext("/api/playerdata", gameplayApi::apiPlayerdata);
        http.createContext("/api/kits", kitsApi::apiKits);
        http.createContext("/api/commands", commandsApi::apiCommands);
        http.createContext("/api/discord", gameplayApi::apiDiscord);
        http.createContext("/api/tebex", gameplayApi::apiTebex);
        http.createContext("/api/tab", gameplayApi::apiTab);
        http.createContext("/api/map", gameplayApi::apiMap);
        http.createContext("/api/guard", gameplayApi::apiGuard);
        http.createContext("/api/lagguard", gameplayApi::apiLagGuard);
        http.createContext("/api/regions", gameplayApi::apiRegions);
        http.createContext("/api/npcs", gameplayApi::apiNpcs);
        http.createContext("/api/skills", gameplayApi::apiSkills);
        http.createContext("/api/factions", gameplayApi::apiFactions);
        http.createContext("/metrics", metricsHandler::handle);
        http.createContext("/health", ex -> DashboardHttp.text(ex, 200, "ok"));
    }
}
