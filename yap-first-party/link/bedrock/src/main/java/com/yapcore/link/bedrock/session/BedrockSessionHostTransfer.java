package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import java.util.logging.Logger;

/** Bungee/YaPPortals Connect transfer back onto this Link (split from {@link BedrockSessionHostJoin}). */
final class BedrockSessionHostTransfer {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockSessionHostTransfer() {}

    static java.net.InetSocketAddress resolveBackend(BedrockSessionHost host, String user) {
        String preferred = BedrockConnectPending.take(user);
        if (preferred != null) {
            java.net.InetSocketAddress addr = host.config.javaBackendFor(preferred);
            if (addr != null) {
                LOG.info("BE pending Connect → " + preferred + " user=" + user
                        + " backend=" + addr.getHostString() + ":" + addr.getPort());
                return addr;
            }
            LOG.warning("BE pending Connect target unknown: " + preferred + " user=" + user);
        }
        return host.config.javaBackend();
    }

    /**
     * YaPPortals / BungeeCord Connect: remember target and TransferPacket the Bedrock client
     * back to this Link so Phase 2 joins the named Folia backend (same path as a fresh login).
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
        String hostName = host.config.transferHost();
        int port = transferPort(host, state);
        org.cloudburstmc.protocol.bedrock.packet.TransferPacket transfer =
                new org.cloudburstmc.protocol.bedrock.packet.TransferPacket();
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
}
