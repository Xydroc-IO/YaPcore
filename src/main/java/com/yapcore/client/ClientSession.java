package com.yapcore.client;

import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.resourcepack.ResourcePackOffer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unified session for Java and Bedrock clients.
 */
public final class ClientSession {

    public enum ResourcePackState {
        NONE,
        OFFERED,
        ACCEPTED,
        DOWNLOADING,
        LOADED,
        DECLINED,
        FAILED
    }

    private final UUID sessionId = UUID.randomUUID();
    private final String username;
    private final ClientEdition edition;
    private final ProtocolVersionRegistry.ProtocolVersion protocol;
    private final InetSocketAddress address;
    private final Instant connectedAt = Instant.now();
    private final AtomicReference<ResourcePackState> packState = new AtomicReference<>(ResourcePackState.NONE);
    private volatile ResourcePackOffer activeOffer;

    public ClientSession(String username,
                         ClientEdition edition,
                         ProtocolVersionRegistry.ProtocolVersion protocol,
                         InetSocketAddress address) {
        this.username = Objects.requireNonNull(username);
        this.edition = Objects.requireNonNull(edition);
        this.protocol = Objects.requireNonNull(protocol);
        this.address = address;
    }

    public UUID getSessionId() { return sessionId; }
    public String getUsername() { return username; }
    public ClientEdition getEdition() { return edition; }
    public ProtocolVersionRegistry.ProtocolVersion getProtocol() { return protocol; }
    public InetSocketAddress getAddress() { return address; }
    public Instant getConnectedAt() { return connectedAt; }
    public ResourcePackState getPackState() { return packState.get(); }

    public void offerResourcePack(ResourcePackOffer offer) {
        this.activeOffer = offer;
        packState.set(ResourcePackState.OFFERED);
    }

    public Optional<ResourcePackOffer> getActiveOffer() {
        return Optional.ofNullable(activeOffer);
    }

    public void setPackState(ResourcePackState state) {
        packState.set(state);
    }

    @Override
    public String toString() {
        return username + "/" + edition + "@" + protocol.minecraftVersion()
                + " pack=" + packState.get();
    }
}
