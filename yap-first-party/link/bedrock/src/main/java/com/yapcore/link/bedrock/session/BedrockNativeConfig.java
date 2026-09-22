package com.yapcore.link.bedrock.session;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntSupplier;

/** Config surface for Link-native Bedrock ({@link BedrockSessionHost}). */
public interface BedrockNativeConfig {

    List<InetSocketAddress> bindAddresses();

    String motd();

    int maxPlayers();

    IntSupplier onlineCount();

    Path linkHome();

    /** Floodgate key path; may be unused when offline fallback is on. */
    String floodgateKeyPath();

    /** Java backend for Phase 2 — stored now. */
    InetSocketAddress javaBackend();

    /**
     * Resolve a named Link backend ({@code servers.*}) for Bedrock portal / Connect transfers.
     * @return null when unknown
     */
    default InetSocketAddress javaBackendFor(String serverName) {
        return null;
    }

    /** Public hostname Bedrock clients use to reach this Link (TransferPacket). */
    default String transferHost() {
        return "127.0.0.1";
    }

    /**
     * HTTP port for {@code /pack/yapcore-default.mcpack}. Prefer 80/443 behind nginx for
     * public hosts — Bedrock phones cannot reach chassis {@code :8081} through most NATs.
     */
    default int packCdnPort() {
        return 80;
    }

    default int motdProtocol() {
        return 2169;
    }

    default String motdVersion() {
        return "26.45";
    }

    default String motdSub() {
        return "YaP Link";
    }

    /**
     * Hub/lobby server id for mid-session failover when the current JE backend dies.
     * Default {@code lobby}.
     */
    default String fallbackHubServer() {
        return "lobby";
    }

    /** When true, reconnect Bedrock players to hub instead of leaving a ghost session. */
    default boolean fallbackOnBackendLoss() {
        return true;
    }
}
