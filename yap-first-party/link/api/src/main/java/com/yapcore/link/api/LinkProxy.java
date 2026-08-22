package com.yapcore.link.api;

import com.yapcore.link.api.event.LinkEvent;
import com.yapcore.link.api.event.PluginMessageEvent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;

/** Proxy surface exposed to YaP Link plugins. */
public interface LinkProxy {

    Logger logger();

    Path home();

    Collection<LinkPlayer> players();

    Optional<LinkPlayer> player(String username);

    Collection<RegisteredServer> servers();

    Optional<RegisteredServer> server(String name);

    void registerChannel(ChannelIdentifier channel);

    void fireEvent(LinkEvent event);

    void registerCommand(String name, SimpleCommand command);

    void registerCommand(String name, String permission, SimpleCommand command);

    /** Send a plugin message to all players on a backend except optional source. */
    void broadcastPluginMessage(
            RegisteredServer target,
            ChannelIdentifier channel,
            byte[] data,
            RegisteredServer excludeSource
    );

    /** Metrics hook for dashboard integration (Phase 5). */
    LinkMetrics metrics();
}
