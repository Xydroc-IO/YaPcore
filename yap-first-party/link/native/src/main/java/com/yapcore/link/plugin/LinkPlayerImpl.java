package com.yapcore.link.plugin;

import com.yapcore.link.ClientSession;
import com.yapcore.link.LinkServer;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.SimpleCommand;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live player handle exposed to plugins. */
public final class LinkPlayerImpl implements LinkPlayer, SimpleCommand.CommandSource {

    private final ClientSession session;
    private final UUID uuid;
    private final String username;
    private final InetSocketAddress address;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();

    public LinkPlayerImpl(ClientSession session, UUID uuid, String username, InetSocketAddress address) {
        this.session = session;
        this.uuid = uuid;
        this.username = username;
        this.address = address;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return address;
    }

    @Override
    public Optional<RegisteredServer> currentServer() {
        return session.currentServer();
    }

    @Override
    public void sendMessage(String legacyText) {
        session.sendSystemMessage(legacyText);
    }

    @Override
    public void disconnect(String reason) {
        session.kick(reason);
    }

    @Override
    public void connect(RegisteredServer server) {
        session.switchServer(server.name());
    }

    @Override
    public String name() {
        return username;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public LinkPlayer asPlayer() {
        return this;
    }

    public void grantPermission(String perm) {
        permissions.add(perm);
    }

    public boolean hasPermission(String perm) {
        return permissions.contains(perm) || permissions.contains("yaplink.*");
    }

    ClientSession session() {
        return session;
    }
}
