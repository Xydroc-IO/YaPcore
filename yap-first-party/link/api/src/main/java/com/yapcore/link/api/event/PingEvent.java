package com.yapcore.link.api.event;

/** Fired for status (ping) requests. Plugins may replace MOTD JSON. */
public final class PingEvent extends LinkEvent {

    private final int protocolVersion;
    private String statusJson;
    private boolean handled;

    public PingEvent(int protocolVersion, String statusJson) {
        this.protocolVersion = protocolVersion;
        this.statusJson = statusJson;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String statusJson() {
        return statusJson;
    }

    public void setStatusJson(String statusJson) {
        this.statusJson = statusJson;
        this.handled = true;
    }

    public boolean isHandled() {
        return handled;
    }
}
