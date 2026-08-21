package com.yapcore.protocol.compat;

import com.yapcore.protocol.java.ProtocolBand;

import java.util.logging.Logger;

/**
 * YaPcore built-in multi-version protocol compatibility (Via-class).
 * Speaks each client's {@link ProtocolBand} — no ViaVersion / ViaBackwards /
 * ViaRewind plugins. Parallel product story to Geyser-class Bedrock translation.
 */
public final class ProtocolCompat {

    private static final Logger LOG = Logger.getLogger("YaPcore.ProtocolCompat");
    /** Current Paper / product target protocol (26.2). */
    public static final int SERVER_PROTOCOL = 776;

    private static volatile boolean online;
    private static volatile ViaStyleRemapper remapper;

    private ProtocolCompat() {
    }

    public static void start() {
        remapper = new ViaStyleRemapper(SERVER_PROTOCOL);
        com.yapcore.protocol.via.ViaBootstrap.start(SERVER_PROTOCOL);
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
        LOG.info("Built-in JE multi-version online (full Via parity path, no Via plugins): " + bands);
        LOG.info("Server band=" + remapper.serverBand().name()
                + " protocol=" + SERVER_PROTOCOL
                + " | ViaBootstrap=" + com.yapcore.protocol.via.ViaBootstrap.isOnline()
                + " | Bedrock=Geyser parity path");
    }

    public static void stop() {
        online = false;
        remapper = null;
        com.yapcore.protocol.via.ViaBootstrap.stop();
        LOG.info("Built-in multi-version stopped");
    }

    public static boolean isOnline() {
        return online;
    }

    public static ViaStyleRemapper remapper() {
        return remapper;
    }

    /** Resolve the native codec band for a client protocol ID. */
    public static ProtocolBand bandFor(int clientProtocol) {
        return ProtocolBand.of(clientProtocol);
    }

    /** Record a JE join for Via-class remap accounting (safe no-op if stopped). */
    public static ProtocolBand onJavaJoin(String username, int clientProtocol) {
        ViaStyleRemapper r = remapper;
        if (r == null) {
            return ProtocolBand.of(clientProtocol);
        }
        return r.onJoin(username, clientProtocol);
    }
}
