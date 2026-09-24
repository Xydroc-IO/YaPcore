package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.FailoverSpawnArrival;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import java.util.UUID;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;

/** Bungee/YaPPortals Connect transfer back onto this Link (split from {@link BedrockSessionHostJoin}). */
final class BedrockSessionHostTransfer {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockSessionHostTransfer() {}

    static java.net.InetSocketAddress resolveBackend(BedrockSessionHost host, String user) {
        return resolveBackendNamed(host, user).address();
    }

    /** Resolve JE backend address + Link {@code servers.*} name in one take of pending Connect. */
    static NamedBackend resolveBackendNamed(BedrockSessionHost host, String user) {
        String preferred = BedrockConnectPending.take(user);
        if (preferred != null) {
            java.net.InetSocketAddress addr = host.config.javaBackendFor(preferred);
            if (addr != null) {
                LOG.info("BE pending Connect → " + preferred + " user=" + user
                        + " backend=" + addr.getHostString() + ":" + addr.getPort());
                return new NamedBackend(preferred.trim(), addr);
            }
            LOG.warning("BE pending Connect target unknown: " + preferred + " user=" + user);
        }
        String hub = host.config.fallbackHubServer();
        if (hub == null || hub.isBlank()) {
            hub = "lobby";
        }
        return new NamedBackend(hub, host.config.javaBackend());
    }

    record NamedBackend(String name, java.net.InetSocketAddress address) {}

    /**
     * Mid-session rescue when the JE backend crashes/stops: soft-switch Bedrock to hub.
     * @return {@code true} if failover was started
     */
    static boolean tryFallbackToHub(
            BedrockSessionHost host, BedrockSessionHost.ClientState state, String cause) {
        if (host == null || state == null || !host.config.fallbackOnBackendLoss()) {
            return false;
        }
        LinkBedrockSession join = state.joinSession;
        if (join == null || !join.isSentSpawnPacket()) {
            return false;
        }
        String hub = host.config.fallbackHubServer();
        if (hub == null || hub.isBlank()) {
            hub = "lobby";
        }
        String lost = state.currentBackendName;
        // Soft-switch in progress: ignore the old JE close. If hub reconnect already failed
        // (currentBackendName already set to hub), let the caller disconnect the client.
        if (join.softBackendSwitch) {
            if (lost != null && hub.equalsIgnoreCase(lost)) {
                join.softBackendSwitch = false;
                return false;
            }
            return true;
        }
        if (lost != null && hub.equalsIgnoreCase(lost)) {
            LOG.warning("BE FAILOVER hub itself lost user=" + state.username
                    + " hub=" + hub + " cause=" + cause);
            return false;
        }
        java.net.InetSocketAddress dest = host.config.javaBackendFor(hub);
        if (dest == null) {
            LOG.warning("BE FAILOVER no hub address user=" + state.username
                    + " hub=" + hub + " cause=" + cause);
            return false;
        }
        LOG.info("BE FAILOVER user=" + state.username
                + " lost=" + lost + " → " + hub + " cause=" + cause);
        // Pending SPAWN so destination PortalArrivalListener lands at /setspawn
        // (not last-logout in front of the portal pad).
        java.util.UUID uuid = null;
        if (state.identity != null) {
            uuid = state.identity.javaUuid();
        }
        if (uuid == null && join != null) {
            uuid = join.uuid();
        }
        FailoverSpawnArrival.mark(host.config.linkHome(), uuid, hub.trim());
        softSwitchJavaBackend(host, state, hub.trim(), dest);
        return true;
    }

    /**
     * YaPPortals / BungeeCord Connect: switch this Bedrock session to another Folia backend.
     *
     * <p>Prefer an in-proxy JE soft-switch (same idea as Java SoftSwitch). TransferPacket to the
     * public {@code transferHost} while the RakNet session stays open is what left Bedrock in a
     * ghost world after lobby disconnect — Folia saw quit, the phone kept rendering with no JE.
     */
    static void transferToBackend(
            BedrockSessionHost host, BedrockSessionHost.ClientState state, String targetServer) {
        if (host == null || state == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        java.net.InetSocketAddress dest = host.config.javaBackendFor(targetServer);
        if (dest == null) {
            LOG.warning("BE Connect ignored — unknown server " + targetServer
                    + " user=" + state.username);
            return;
        }
        String user = state.username != null ? state.username : "BedrockPlayer";
        BedrockConnectPending.put(user, targetServer.trim());

        LinkBedrockSession join = state.joinSession;
        if (join != null && join.isSentSpawnPacket()) {
            softSwitchJavaBackend(host, state, targetServer.trim(), dest);
            return;
        }

        // Pre-StartGame: TransferPacket back to this Link, then force-drop RakNet so the
        // client cannot keep a JE-less ghost session if Transfer is ignored.
        String hostName = transferAddress(host, state);
        int port = transferPort(host, state);
        TransferPacket transfer = new TransferPacket();
        transfer.setAddress(hostName);
        transfer.setPort(port);
        transfer.setReloadWorld(true);
        LOG.info("BE TransferPacket Connect → " + targetServer
                + " user=" + user + " via " + hostName + ":" + port
                + " (JE " + dest.getHostString() + ":" + dest.getPort() + ")");
        BedrockJoinProbe.noteEvent(state.guid,
                "bungee_connect→transfer target=" + targetServer + " via=" + hostName + ":" + port);
        host.sendPacket(state, transfer);
        host.closeDownstream(state);
        host.disconnectClient(state, "Transferring to " + targetServer.trim() + "…");
    }

    /**
     * Keep the Bedrock RakNet session; swap the JE downstream to {@code dest} and reload the
     * world via ChangeDimension when the new Folia login arrives.
     */
    private static void softSwitchJavaBackend(
            BedrockSessionHost host,
            BedrockSessionHost.ClientState state,
            String targetServer,
            java.net.InetSocketAddress dest) {
        LinkBedrockSession join = state.joinSession;
        if (join == null) {
            return;
        }
        LOG.info("BE soft-switch Connect → " + targetServer
                + " user=" + state.username
                + " je=" + dest.getHostString() + ":" + dest.getPort());
        BedrockJoinProbe.noteEvent(state.guid,
                "bungee_connect→soft_switch target=" + targetServer
                        + " je=" + dest.getHostString() + ":" + dest.getPort());

        // Same as JE SoftSwitch: SPAWN pending unless portal already marked island/rtp/home.
        UUID uuid = join.uuid();
        if (uuid == null && state.identity != null) {
            uuid = state.identity.javaUuid();
        }
        if (uuid != null) {
            FailoverSpawnArrival.markIfAbsent(host.config.linkHome(), uuid, targetServer.trim());
        }

        // Consume Connect-pending routing hint — reconnect uses dest directly.
        BedrockConnectPending.take(state.username != null ? state.username : "BedrockPlayer");

        join.beginSoftBackendSwitch(targetServer);
        host.closeDownstream(state);
        join.setDownstream(null);

        BedrockSessionHostJoin.reconnectJavaBackend(host, state, dest, targetServer);
    }

    /**
     * Prefer a host the Bedrock device can reach. LAN peers must not be TransferPacket'd
     * to a public DNS that hairpins poorly.
     */
    private static String transferAddress(
            BedrockSessionHost host, BedrockSessionHost.ClientState state) {
        String configured = host.config.transferHost();
        if (configured == null || configured.isBlank()) {
            configured = "127.0.0.1";
        }
        boolean peerPrivate = false;
        try {
            if (state.peer != null && state.peer.address() != null
                    && state.peer.address().getAddress() != null) {
                peerPrivate = isSiteLocal(state.peer.address().getAddress());
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (!peerPrivate) {
            return configured.trim();
        }
        // Peer is private — always prefer a site-local bind IP over public transferHost.
        java.util.List<java.net.InetSocketAddress> binds = host.config.bindAddresses();
        if (binds != null) {
            for (java.net.InetSocketAddress a : binds) {
                if (a == null || a.getAddress() == null) {
                    continue;
                }
                java.net.InetAddress addr = a.getAddress();
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
                    continue;
                }
                if (isSiteLocal(addr)) {
                    return addr.getHostAddress();
                }
            }
        }
        if (isProbablyLocalHost(configured)) {
            return configured.trim();
        }
        // Last resort: peer's own interface address family — still better than unreachable DNS.
        try {
            if (state.peer != null && state.peer.address() != null
                    && state.peer.address().getAddress() != null) {
                // Cannot invent a host; keep configured but log — soft-switch avoids this path.
                LOG.warning("BE Transfer LAN peer but no site-local bind — using " + configured.trim());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return configured.trim();
    }

    private static boolean isSiteLocal(java.net.InetAddress addr) {
        return addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress();
    }

    private static boolean isProbablyLocalHost(String host) {
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1")) {
            return true;
        }
        try {
            return isSiteLocal(java.net.InetAddress.getByName(h));
        } catch (Exception e) {
            return false;
        }
    }

    private static int transferPort(BedrockSessionHost host, BedrockSessionHost.ClientState state) {
        try {
            if (state.peer != null && state.peer.listenChannel() != null
                    && state.peer.listenChannel().localAddress()
                    instanceof java.net.InetSocketAddress local
                    && local.getPort() > 0) {
                return local.getPort();
            }
        } catch (Exception ignored) {
            // fall through
        }
        java.util.List<java.net.InetSocketAddress> binds = host.config.bindAddresses();
        if (binds != null) {
            for (java.net.InetSocketAddress a : binds) {
                if (a != null && a.getPort() == 19132) {
                    return 19132;
                }
            }
            for (java.net.InetSocketAddress a : binds) {
                if (a != null && a.getPort() > 0) {
                    return a.getPort();
                }
            }
        }
        return 19132;
    }

    /** Build a DisconnectPacket used by {@link BedrockSessionHost#disconnectClient}. */
    static DisconnectPacket disconnectPacket(String reason) {
        DisconnectPacket packet = new DisconnectPacket();
        packet.setMessageSkipped(false);
        packet.setKickMessage(reason != null && !reason.isBlank() ? reason : "Disconnected");
        packet.setFilteredMessage("");
        return packet;
    }
}
