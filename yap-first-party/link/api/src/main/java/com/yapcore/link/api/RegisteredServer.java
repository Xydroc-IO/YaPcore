package com.yapcore.link.api;

/** Registered backend from {@code link.properties} {@code servers.*}. */
public interface RegisteredServer {

    String name();

    String host();

    int port();

    void sendPluginMessage(ChannelIdentifier channel, byte[] data);
}
