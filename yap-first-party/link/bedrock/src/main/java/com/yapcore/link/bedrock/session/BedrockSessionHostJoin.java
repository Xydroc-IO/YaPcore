package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.floodgate.LinkFloodgateAuth;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Phase 2/3 Java downstream + join session bootstrap (split from {@link BedrockSessionHost}). */
final class BedrockSessionHostJoin {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockSessionHostJoin() {}

    static void startPhase2And3(BedrockSessionHost host, BedrockSessionHost.ClientState state) {
        if (state.downstream != null || host.group == null) {
            return;
        }
        if (state.codec == null && LinkCloudburstCodecs.isModern(state.protocol)) {
            state.codec = LinkCloudburstCodecs.open(state.protocol);
        }
        LinkFloodgateAuth.Identity identity = state.identity;
        UUID javaUuid = identity != null ? identity.javaUuid() : null;
        String user = state.username != null ? state.username : "BedrockPlayer";
        if (javaUuid == null) {
            javaUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8));
        }
        long runtimeId = state.guid != 0L ? Math.abs(state.guid) : ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        state.joinSession = LinkBedrockSession.open(
                state.guid,
                runtimeId,
                user,
                javaUuid,
                state.protocol,
                state.codec,
                packets -> host.sendPackets(state, packets));
        state.joinSession.setJoinPhase(LinkBedrockSession.JoinPhase.AWAITING_JAVA_LOGIN);
        state.joinSession.setBackendSwitchHandler(target ->
                BedrockSessionHostTransfer.transferToBackend(host, state, target));

        BedrockSessionHostTransfer.NamedBackend named =
                BedrockSessionHostTransfer.resolveBackendNamed(host, user);
        attachJavaDownstream(host, state, named.address(), named.name(), user, javaUuid);
    }

    /**
     * Soft-switch: reuse the existing Bedrock {@link LinkBedrockSession} and open a new JE
     * downstream to {@code backend} (YaPPortals Connect without TransferPacket ghost).
     */
    static void reconnectJavaBackend(
            BedrockSessionHost host,
            BedrockSessionHost.ClientState state,
            java.net.InetSocketAddress backend) {
        reconnectJavaBackend(host, state, backend, null);
    }

    static void reconnectJavaBackend(
            BedrockSessionHost host,
            BedrockSessionHost.ClientState state,
            java.net.InetSocketAddress backend,
            String backendName) {
        if (host == null || state == null || backend == null || host.group == null) {
            return;
        }
        if (state.joinSession == null) {
            startPhase2And3(host, state);
            return;
        }
        if (state.downstream != null) {
            host.closeDownstream(state);
        }
        LinkFloodgateAuth.Identity identity = state.identity;
        UUID javaUuid = identity != null ? identity.javaUuid() : state.joinSession.uuid();
        String user = state.username != null ? state.username : state.joinSession.username();
        if (javaUuid == null) {
            javaUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8));
        }
        state.joinSession.setJoinPhase(LinkBedrockSession.JoinPhase.AWAITING_JAVA_LOGIN);
        attachJavaDownstream(host, state, backend, backendName, user, javaUuid);
    }

    private static void attachJavaDownstream(
            BedrockSessionHost host,
            BedrockSessionHost.ClientState state,
            java.net.InetSocketAddress backend,
            String backendName,
            String user,
            UUID javaUuid) {
        String clientIp = state.peer != null && state.peer.address() != null
                ? state.peer.address().getAddress().getHostAddress()
                : "127.0.0.1";
        if (backendName != null && !backendName.isBlank()) {
            state.currentBackendName = backendName.trim();
        }
        JavaDownstreamClient.Listener listener = BedrockSessionHostJoinListener.create(host, state);
        state.downstream = new JavaDownstreamClient(
                host.group,
                backend,
                user,
                javaUuid,
                clientIp,
                host.config.linkHome(),
                listener);
        if (state.joinSession != null) {
            state.joinSession.setDownstream(state.downstream);
        }
        String backendStr = backend.getHostString() + ":" + backend.getPort();
        BedrockJoinProbe.noteJavaBackend(state.guid, backendStr);
        state.downstream.connect();
        LOG.info("BE Phase 2 started JavaDownstream → " + backendStr
                + " name=" + state.currentBackendName
                + " user=" + user + " uuid=" + javaUuid
                + " (must match JE lobby for same world)");
        BedrockJoinProbe.noteEvent(state.guid,
                "java_downstream_connect host=" + backend.getHostString()
                        + " port=" + backend.getPort()
                        + " name=" + state.currentBackendName);
    }
}
