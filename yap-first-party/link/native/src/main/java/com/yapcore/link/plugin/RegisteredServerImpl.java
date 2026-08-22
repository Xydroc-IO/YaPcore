package com.yapcore.link.plugin;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.LinkServer;
import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;

import java.util.function.Consumer;

/** Backend registration for plugin API. */
public final class RegisteredServerImpl implements RegisteredServer {

    private final LinkServer server;
    private final LinkConfig.Backend backend;

    public RegisteredServerImpl(LinkServer server, LinkConfig.Backend backend) {
        this.server = server;
        this.backend = backend;
    }

    @Override
    public String name() {
        return backend.name();
    }

    @Override
    public String host() {
        return backend.host();
    }

    @Override
    public int port() {
        return backend.port();
    }

    @Override
    public void sendPluginMessage(ChannelIdentifier channel, byte[] data) {
        deliverPluginMessage(channel, data);
    }

    void deliverPluginMessage(ChannelIdentifier channel, byte[] data) {
        server.broadcastToBackend(name(), channel, data);
    }
}
