package com.yapcore.link.api.event;

import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;

/** Fired after a player successfully joins a backend. */
public final class PostConnectEvent extends LinkEvent {

    private final LinkPlayer player;
    private final RegisteredServer server;

    public PostConnectEvent(LinkPlayer player, RegisteredServer server) {
        this.player = player;
        this.server = server;
    }

    public LinkPlayer player() {
        return player;
    }

    public RegisteredServer server() {
        return server;
    }
}
