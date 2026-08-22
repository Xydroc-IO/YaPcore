package com.yapcore.link.api.event;

import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;

import java.net.InetSocketAddress;
import java.util.UUID;

/** Fired before a player is connected to a backend. Cancellable. */
public final class PreConnectEvent extends LinkEvent {

    private final UUID uuid;
    private final String username;
    private final InetSocketAddress address;
    private RegisteredServer target;
    private String denyReason;
    private boolean cancelled;

    public PreConnectEvent(UUID uuid, String username, InetSocketAddress address, RegisteredServer target) {
        this.uuid = uuid;
        this.username = username;
        this.address = address;
        this.target = target;
    }

    public UUID uuid() {
        return uuid;
    }

    public String username() {
        return username;
    }

    public InetSocketAddress address() {
        return address;
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

    public String denyReason() {
        return denyReason;
    }

    public void deny(String reason) {
        this.cancelled = true;
        this.denyReason = reason;
    }
}
