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

    default int motdProtocol() {
        return 2169;
    }

    default String motdVersion() {
        return "26.45";
    }

    default String motdSub() {
        return "YaP Link";
    }
}
