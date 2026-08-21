package com.yapcore.protocol.compat;

import com.yapcore.protocol.java.ProtocolBand;

import java.util.logging.Logger;

/**
 * YaPcore built-in multi-version protocol compatibility.
 * Speaks each client's {@link ProtocolBand} natively — no third-party translators.
 */
public final class ProtocolCompat {

    private static final Logger LOG = Logger.getLogger("YaPcore.ProtocolCompat");
    private static volatile boolean online;

    private ProtocolCompat() {
    }

    public static void start() {
        online = true;
        StringBuilder bands = new StringBuilder();
        for (ProtocolBand b : ProtocolBand.values()) {
            if (bands.length() > 0) {
                bands.append(", ");
            }
            bands.append(b.name()).append('[').append(b.minProtocol())
                    .append('-')
                    .append(b.maxProtocol() == Integer.MAX_VALUE ? "…" : b.maxProtocol())
                    .append(']');
        }
        LOG.info("Built-in multi-version online (native YaPcore bands): " + bands);
        LOG.info("JE_JOIN_BUILD=2026-08-20-native-world-m1 (YapEngine flat overworld, no Mojang proxy)");
    }

    public static void stop() {
        online = false;
        LOG.info("Built-in multi-version stopped");
    }

    public static boolean isOnline() {
        return online;
    }

    /** Resolve the native codec band for a client protocol ID. */
    public static ProtocolBand bandFor(int clientProtocol) {
        return ProtocolBand.of(clientProtocol);
    }
}
