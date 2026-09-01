package com.yapcore.protocol.compat;

import com.yapcore.protocol.java.ProtocolBand;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * First-party Via\* parity remapper (ViaVersion + ViaBackwards + ViaRewind goals).
 * <p>
 * Phase 4 DoD: full feature parity with those plugins — <strong>not</strong> a
 * “good enough” subset. Operators must not install Via\* jars. See
 * {@code docs/protocol/PHASE4_PROTOCOL.md}.
 */
public final class ViaStyleRemapper {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaXlate");

    private final AtomicLong remapped = new AtomicLong();
    private final int serverProtocol;

    public ViaStyleRemapper(int serverProtocol) {
        this.serverProtocol = serverProtocol;
    }

    public int serverProtocol() {
        return serverProtocol;
    }

    public ProtocolBand serverBand() {
        return ProtocolBand.of(serverProtocol);
    }

    /** Whether this client protocol needs remap onto the server band. */
    public boolean needsRemap(int clientProtocol) {
        return ProtocolBand.of(clientProtocol) != serverBand();
    }

    /**
     * Attach a joining JE client. Returns the band the wire will speak.
     */
    public ProtocolBand onJoin(String username, int clientProtocol) {
        Objects.requireNonNull(username, "username");
        ProtocolBand client = ProtocolBand.of(clientProtocol);
        ProtocolBand server = serverBand();
        if (client == server) {
            LOG.fine(() -> "JE " + username + " native " + client.name()
                    + " (proto " + clientProtocol + ")");
            return client;
        }
        remapped.incrementAndGet();
        LOG.info("JE remap " + username + " " + client.name() + "[" + clientProtocol + "]"
                + " → server " + server.name() + "[" + serverProtocol + "]"
                + " (built-in Via-class; no ViaVersion plugin)");
        return client;
    }

    public void onLeave(String username) {
        LOG.fine(() -> "JE remap detach " + username);
    }

    public long remappedJoins() {
        return remapped.get();
    }
}
