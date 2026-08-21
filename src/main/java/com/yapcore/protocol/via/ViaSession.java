package com.yapcore.protocol.via;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.ConnState;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/** Per-connection Via remap state (client band ↔ server band). */
public final class ViaSession {

    private static final AtomicLong IDS = new AtomicLong();

    private final long id = IDS.incrementAndGet();
    private final int clientProtocol;
    private final int serverProtocol;
    private final int backendPort;
    private final ProtocolBand clientBand;
    private final ProtocolBand serverBand;
    private volatile ConnState state = ConnState.HANDSHAKE;
    private volatile String username = "?";
    /** Minecraft network-compression-threshold; {@code -1} = off. */
    private volatile int compressionThreshold = -1;
    private volatile boolean compressionPending;
    /** Legacy client: swallow Paper CONFIG and auto-ACK until PLAY. */
    private volatile boolean configSkip;
    /** After login_success to legacy client, inject Login Acknowledged toward Paper. */
    private volatile boolean pendingLoginAckInject;
    private final Queue<ConfigAutoReply> configAutoReplies = new ArrayDeque<>();
    private final AtomicLong clientToServer = new AtomicLong();
    private final AtomicLong serverToClient = new AtomicLong();

    public ViaSession(int clientProtocol, int serverProtocol) {
        this(clientProtocol, serverProtocol, 0);
    }

    public ViaSession(int clientProtocol, int serverProtocol, int backendPort) {
        this.clientProtocol = clientProtocol;
        this.serverProtocol = serverProtocol;
        this.backendPort = backendPort;
        this.clientBand = ProtocolBand.of(clientProtocol);
        this.serverBand = ProtocolBand.of(serverProtocol);
    }

    public int backendPort() {
        return backendPort;
    }

    public long id() {
        return id;
    }

    public int clientProtocol() {
        return clientProtocol;
    }

    public int serverProtocol() {
        return serverProtocol;
    }

    public ProtocolBand clientBand() {
        return clientBand;
    }

    public ProtocolBand serverBand() {
        return serverBand;
    }

    public boolean needsRemap() {
        return clientBand != serverBand || clientProtocol != serverProtocol;
    }

    public boolean needsForward() {
        return clientProtocol > serverProtocol;
    }

    public boolean needsBackwards() {
        return clientProtocol < serverProtocol;
    }

    public ConnState state() {
        return state;
    }

    public void setState(ConnState state) {
        this.state = state;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "?" : username;
    }

    public int compressionThreshold() {
        return compressionThreshold;
    }

    public void enableCompression(int threshold) {
        this.compressionThreshold = threshold;
        this.compressionPending = true;
    }

    public boolean consumeCompressionPending() {
        if (!compressionPending) {
            return false;
        }
        compressionPending = false;
        return true;
    }

    public void armConfigSkip() {
        this.configSkip = true;
        this.pendingLoginAckInject = true;
    }

    public boolean isConfigSkip() {
        return configSkip;
    }

    public void clearConfigSkip() {
        this.configSkip = false;
    }

    public boolean consumePendingLoginAckInject() {
        if (!pendingLoginAckInject) {
            return false;
        }
        pendingLoginAckInject = false;
        return true;
    }

    public enum ConfigAutoReply {
        KNOWN_PACKS,
        FINISH
    }

    public synchronized void noteConfigAutoReply(ConfigAutoReply reply) {
        configAutoReplies.add(reply);
    }

    public synchronized ConfigAutoReply pollConfigAutoReply() {
        return configAutoReplies.poll();
    }

    public void bumpC2S() {
        clientToServer.incrementAndGet();
    }

    public void bumpS2C() {
        serverToClient.incrementAndGet();
    }

    public long packetsC2S() {
        return clientToServer.get();
    }

    public long packetsS2C() {
        return serverToClient.get();
    }
}
