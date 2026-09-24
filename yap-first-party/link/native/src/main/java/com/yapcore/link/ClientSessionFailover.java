package com.yapcore.link;

import com.yapcore.link.bedrock.FailoverSpawnArrival;
import com.yapcore.link.protocol.PlayChat;
import io.netty.channel.Channel;

import java.util.logging.Logger;

/**
 * Mid-session rescue: when survival/creative/etc crashes or shuts down, soft-switch the
 * player to hub/lobby instead of closing the client TCP to YaP Link.
 */
final class ClientSessionFailover {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    private ClientSessionFailover() {
    }

    /**
     * @return {@code true} if failover was started or is already in progress
     *         (caller must not close the client)
     */
    static boolean tryFallbackToHub(ClientSession session, String cause) {
        if (session == null || session.closed.get()) {
            return false;
        }
        if (!session.server.config().fallbackOnBackendLoss()) {
            return false;
        }
        if (session.switching.get()) {
            return true; // soft-switch already owns the client connection
        }
        Channel client = session.clientCtx != null ? session.clientCtx.channel() : null;
        if (client == null || !client.isActive()) {
            return false;
        }
        String lost = session.currentBackendName;
        LinkConfig.Backend hub = session.server.backendMonitor().pickFallback(lost);
        if (hub == null) {
            LOG.warning("FAILOVER no hub for user=" + session.username
                    + " lost=" + lost + " cause=" + cause);
            return false;
        }
        if (lost != null && hub.name().equalsIgnoreCase(lost)) {
            LOG.warning("FAILOVER hub itself lost user=" + session.username
                    + " hub=" + hub.name() + " cause=" + cause);
            return false;
        }
        LOG.info("FAILOVER user=" + session.username
                + " lost=" + lost + " → " + hub.name()
                + " cause=" + cause);
        // Same pending-spawn mark as YaPPortals Connect — destination joins at /setspawn
        // instead of last logout (portal pad). Must run before SoftSwitch begins.
        FailoverSpawnArrival.mark(session.server.config().home(), session.playerId, hub.name());
        // Run SoftSwitch on the client loop — avoid mutating the dying backend pipeline
        // from inside its channelInactive.
        Runnable start = () -> {
            if (session.closed.get() || !client.isActive()) {
                return;
            }
            if (session.switching.get()) {
                return;
            }
            session.sendPlaySystemChat(client, PlayChat.jsonText(
                    "§e" + (lost == null || lost.isBlank() ? "Server" : lost)
                            + " went offline — returning to " + hub.name() + "…"));
            ClientSessionSoftSwitch.begin(session, hub);
        };
        if (client.eventLoop().inEventLoop()) {
            start.run();
        } else {
            client.eventLoop().execute(start);
        }
        return true;
    }
}
