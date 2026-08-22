package com.yapcore.link.api;

import java.util.Objects;

/** Minecraft plugin channel identifier ({@code namespace:key}). */
public record ChannelIdentifier(String namespace, String key) {

    public ChannelIdentifier {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
    }

    public static ChannelIdentifier of(String namespace, String key) {
        return new ChannelIdentifier(namespace, key);
    }

    /** Parse {@code namespace:key} wire identifier (e.g. {@code yap:chat}). */
    public static ChannelIdentifier fromMcChannel(String mcChannel) {
        if (mcChannel == null || mcChannel.isBlank()) {
            throw new IllegalArgumentException("blank channel");
        }
        int idx = mcChannel.indexOf(':');
        if (idx <= 0) {
            return new ChannelIdentifier("minecraft", mcChannel);
        }
        return new ChannelIdentifier(mcChannel.substring(0, idx), mcChannel.substring(idx + 1));
    }

    public String id() {
        return namespace + ":" + key;
    }

    @Override
    public String toString() {
        return id();
    }
}
