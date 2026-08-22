package com.yapcore.link.api.event;

import com.yapcore.link.api.LinkPlayer;

import java.net.InetSocketAddress;
import java.util.UUID;

/** Fired during login before backend connect (ban checks, etc.). Cancellable. */
public final class LoginEvent extends LinkEvent {

    private final UUID uuid;
    private final String username;
    private final InetSocketAddress address;
    private String denyReason;
    private boolean cancelled;

    public LoginEvent(UUID uuid, String username, InetSocketAddress address) {
        this.uuid = uuid;
        this.username = username;
        this.address = address;
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

    public boolean isCancelled() {
        return cancelled;
    }

    public void deny(String reason) {
        this.cancelled = true;
        this.denyReason = reason;
    }

    public String denyReason() {
        return denyReason;
    }
}
