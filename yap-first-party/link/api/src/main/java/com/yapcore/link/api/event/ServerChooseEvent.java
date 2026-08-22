package com.yapcore.link.api.event;

import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;

/** Fired when a player requests {@code /server} or plugin-initiated switch. */
public final class ServerChooseEvent extends LinkEvent {

    private final LinkPlayer player;
    private RegisteredServer target;
    private boolean cancelled;

    public ServerChooseEvent(LinkPlayer player, RegisteredServer target) {
        this.player = player;
        this.target = target;
    }

    public LinkPlayer player() {
        return player;
    }

    public RegisteredServer target() {
        return target;
    }

    public void setTarget(RegisteredServer target) {
        this.target = target;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
