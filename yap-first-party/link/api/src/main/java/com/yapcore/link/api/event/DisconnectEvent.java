package com.yapcore.link.api.event;

import com.yapcore.link.api.LinkPlayer;

/** Fired when a player disconnects from YaP Link. */
public final class DisconnectEvent extends LinkEvent {

    private final LinkPlayer player;

    public DisconnectEvent(LinkPlayer player) {
        this.player = player;
    }

    public LinkPlayer player() {
        return player;
    }
}
