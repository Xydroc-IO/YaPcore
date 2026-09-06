package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.auth.DashboardAuth;

import java.io.IOException;

/** Dashboard routes: protect, world, chat, moderation, perms, playerdata. */
public final class DashboardGameplayOpsApi {

    private final DashboardGameplayProtectWorldApi protectWorld;
    private final DashboardGameplaySocialOpsApi social;

    public DashboardGameplayOpsApi(YaPcoreServer server, DashboardAuth auth) {
        this.protectWorld = new DashboardGameplayProtectWorldApi(server, auth);
        this.social = new DashboardGameplaySocialOpsApi(server, auth);
    }

    public void apiProtect(HttpExchange ex) throws IOException { protectWorld.apiProtect(ex); }

    public void apiWorld(HttpExchange ex) throws IOException { protectWorld.apiWorld(ex); }

    public void apiChat(HttpExchange ex) throws IOException { social.apiChat(ex); }

    public void apiModeration(HttpExchange ex) throws IOException { social.apiModeration(ex); }

    public void apiPerms(HttpExchange ex) throws IOException { social.apiPerms(ex); }

    public void apiPlayerdata(HttpExchange ex) throws IOException { social.apiPlayerdata(ex); }
}
