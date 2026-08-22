package com.yapcore.link.api;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

/** Connected player on YaP Link. */
public interface LinkPlayer {

    UUID uuid();

    String username();

    InetSocketAddress remoteAddress();

    Optional<RegisteredServer> currentServer();

    void sendMessage(String legacyText);

    void disconnect(String reason);

    /** Request a backend switch (uses redirect token + Transfer when supported). */
    void connect(RegisteredServer server);
}
