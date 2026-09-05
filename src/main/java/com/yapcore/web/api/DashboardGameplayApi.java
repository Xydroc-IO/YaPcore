package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.auth.DashboardAuth;

import java.io.IOException;

/** Dashboard gameplay routes — thin delegate to content/ops/network/link/modes helpers. */
public final class DashboardGameplayApi {

    private final DashboardGameplayNetworkApi network;
    private final DashboardGameplayOpsApi ops;
    private final DashboardGameplayContentApi content;
    private final DashboardGameplayLinkApi link;
    private final DashboardGameplayModesApi modes;

    public DashboardGameplayApi(YaPcoreServer server, DashboardAuth auth) {
        this.network = new DashboardGameplayNetworkApi(server, auth);
        this.ops = new DashboardGameplayOpsApi(server, auth);
        this.content = new DashboardGameplayContentApi(server, auth);
        this.link = new DashboardGameplayLinkApi(server, auth);
        this.modes = new DashboardGameplayModesApi(server, auth);
    }

    public void apiPregen(HttpExchange ex) throws IOException { content.apiPregen(ex); }
    public void apiRanks(HttpExchange ex) throws IOException { content.apiRanks(ex); }
    public void apiEssentials(HttpExchange ex) throws IOException { content.apiEssentials(ex); }
    public void apiLink(HttpExchange ex) throws IOException { link.apiLink(ex); }
    public void apiProtect(HttpExchange ex) throws IOException { ops.apiProtect(ex); }
    public void apiDisasters(HttpExchange ex) throws IOException { content.apiDisasters(ex); }
    public void apiStacker(HttpExchange ex) throws IOException { content.apiStacker(ex); }
    public void apiWorld(HttpExchange ex) throws IOException { ops.apiWorld(ex); }
    public void apiChat(HttpExchange ex) throws IOException { ops.apiChat(ex); }
    public void apiModeration(HttpExchange ex) throws IOException { ops.apiModeration(ex); }
    public void apiPerms(HttpExchange ex) throws IOException { ops.apiPerms(ex); }
    public void apiPlayerdata(HttpExchange ex) throws IOException { ops.apiPlayerdata(ex); }
    public void apiDiscord(HttpExchange ex) throws IOException { network.apiDiscord(ex); }
    public void apiTebex(HttpExchange ex) throws IOException { network.apiTebex(ex); }
    public void apiTab(HttpExchange ex) throws IOException { network.apiTab(ex); }
    public void apiMap(HttpExchange ex) throws IOException { network.apiMap(ex); }
    public void apiGuard(HttpExchange ex) throws IOException { network.apiGuard(ex); }
    public void apiRegions(HttpExchange ex) throws IOException { network.apiRegions(ex); }
    public void apiNpcs(HttpExchange ex) throws IOException { network.apiNpcs(ex); }
    public void apiSkills(HttpExchange ex) throws IOException { modes.apiSkills(ex); }
    public void apiFactions(HttpExchange ex) throws IOException { modes.apiFactions(ex); }
}
