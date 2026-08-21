package com.yapcore.protocol.via;

import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.java.ProtocolBand;

/**
 * Phase 4 Via\* parity bootstrap — first-party (no ViaVersion jars).
 */
public final class ViaBootstrap {

    private static volatile boolean online;
    private static volatile int serverProtocol = ProtocolCompat.SERVER_PROTOCOL;

    private ViaBootstrap() {
    }

    public static void start(int serverProtocolId) {
        serverProtocol = serverProtocolId;
        online = true;
    }

    public static void stop() {
        online = false;
    }

    public static boolean isOnline() {
        return online;
    }

    public static int serverProtocol() {
        return serverProtocol;
    }

    public static ProtocolBand serverBand() {
        return ProtocolBand.of(serverProtocol);
    }
}
