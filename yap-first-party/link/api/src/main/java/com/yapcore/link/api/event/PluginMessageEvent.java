package com.yapcore.link.api.event;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;

import java.util.Optional;

/** Plugin message from client or backend. */
public final class PluginMessageEvent extends LinkEvent {

    public enum SourceKind { PLAYER, BACKEND }

    private final SourceKind sourceKind;
    private final Optional<LinkPlayer> player;
    private final Optional<RegisteredServer> server;
    private final ChannelIdentifier channel;
    private final byte[] data;
    private Result result = Result.CONTINUE;

    public PluginMessageEvent(
            SourceKind sourceKind,
            Optional<LinkPlayer> player,
            Optional<RegisteredServer> server,
            ChannelIdentifier channel,
            byte[] data
    ) {
        this.sourceKind = sourceKind;
        this.player = player;
        this.server = server;
        this.channel = channel;
        this.data = data;
    }

    public SourceKind sourceKind() {
        return sourceKind;
    }

    public Optional<LinkPlayer> player() {
        return player;
    }

    public Optional<RegisteredServer> server() {
        return server;
    }

    public ChannelIdentifier channel() {
        return channel;
    }

    public byte[] data() {
        return data;
    }

    public Result result() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public enum Result {
        /** Relay to peer as usual. */
        CONTINUE,
        /** Plugin handled; do not relay further. */
        HANDLED
    }
}
