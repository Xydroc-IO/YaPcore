package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.auth.DashboardAuth;

import java.io.IOException;

/** Dashboard routes: Discord, Tebex, TAB, map, guard, regions, NPCs. */
public final class DashboardGameplayNetworkApi {

    private final DashboardGameplayIntegrationsApi integrations;
    private final DashboardGameplayMapGuardApi mapGuard;
    private final DashboardGameplayRegionsApi regions;

    public DashboardGameplayNetworkApi(YaPcoreServer server, DashboardAuth auth) {
        this.integrations = new DashboardGameplayIntegrationsApi(server, auth);
        this.mapGuard = new DashboardGameplayMapGuardApi(server, auth);
        this.regions = new DashboardGameplayRegionsApi(server, auth);
    }

    public void apiDiscord(HttpExchange ex) throws IOException { integrations.apiDiscord(ex); }

    public void apiTebex(HttpExchange ex) throws IOException { integrations.apiTebex(ex); }

    public void apiTab(HttpExchange ex) throws IOException { integrations.apiTab(ex); }

    public void apiMap(HttpExchange ex) throws IOException { mapGuard.apiMap(ex); }

    public void apiGuard(HttpExchange ex) throws IOException { mapGuard.apiGuard(ex); }

    public void apiLagGuard(HttpExchange ex) throws IOException { mapGuard.apiLagGuard(ex); }

    public void apiRegions(HttpExchange ex) throws IOException { regions.apiRegions(ex); }

    public void apiNpcs(HttpExchange ex) throws IOException { regions.apiNpcs(ex); }
}
