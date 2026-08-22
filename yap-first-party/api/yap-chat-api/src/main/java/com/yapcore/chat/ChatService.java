package com.yapcore.chat;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cross-plugin chat platform contract.
 * Provided by {@code YaPChat} via {@code ServicesManager}.
 */
public interface ChatService {

    Collection<String> channelIds();

    String defaultChannelId();

    /** Send a formatted message on a channel (local server). */
    void sendChannelMessage(String channelId, UUID senderUuid, String senderName, String plainMessage);

    /** Staff chat bridge hook for YaP Link. */
    CompletableFuture<Void> relayNetworkMessage(String channelId, String serverId,
                                                UUID senderUuid, String senderName, String plainMessage);
}
