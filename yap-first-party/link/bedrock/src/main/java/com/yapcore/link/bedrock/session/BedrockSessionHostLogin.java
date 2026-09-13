package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.crypto.BedrockEncryption;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.floodgate.LinkFloodgateAuth;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.raknet.RakNetSessionManager;
import com.yapcore.link.bedrock.translator.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;

/** Login / packs / Phase2–3 join bootstrap (split from {@link BedrockSessionHost}). */
final class BedrockSessionHostLogin {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final BedrockSessionHost host;

    BedrockSessionHostLogin(BedrockSessionHost host) {
        this.host = host;
    }

    void sendNetworkSettings(BedrockSessionHost.ClientState state) {
        LOG.info("BE network settings → guid=" + Long.toHexString(state.guid)
                + " proto=" + state.protocol + " (Geyser zlib threshold=512)");
        if (state.codec == null && LinkCloudburstCodecs.isModern(state.protocol)) {
            state.codec = LinkCloudburstCodecs.open(state.protocol);
        }
        if (state.codec != null) {
            host.sendPacket(state, LinkCloudburstCodecs.networkSettingsGeyser());
        }
        state.peer.setGameCompressionHeader(true);
    }

    void beginLogin(BedrockSessionHost.ClientState state, String address, ByteBuf loginBody) {
        try {
            if (state.phase != BedrockSessionHost.LoginPhase.NONE && state.phase != null) {
                LOG.info("BE ignore duplicate Login guid=" + Long.toHexString(state.guid)
                        + " phase=" + state.phase);
                return;
            }
            LinkFloodgateAuth.Identity identity = host.floodgate.authenticate(loginBody, address);
            int proto = state.protocol > 0 ? state.protocol : identity.protocol();
            if (proto <= 0) {
                proto = 2169;
            }
            state.protocol = proto;
            state.username = identity.username();
            state.identity = identity;
            if (LinkCloudburstCodecs.isModern(proto)) {
                state.codec = LinkCloudburstCodecs.open(proto);
            }
            BedrockJoinProbe.start(state.guid, identity.username(), proto, address);
            BedrockJoinProbe.noteEvent(state.guid, "login_begin floodgate_ok");

            if (!encryptPlayerConnection(state)) {
                LOG.warning("BE encryption handshake failed for " + identity.username()
                        + " — aborting login");
                return;
            }
            LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                    + " uuid=" + identity.javaUuid()
                    + " pack=empty-handshake proto=" + proto + " enc=on path=link-native");
        } finally {
            loginBody.release();
        }
    }

    boolean encryptPlayerConnection(BedrockSessionHost.ClientState state) {
        LinkFloodgateAuth.Identity identity = state.identity;
        if (identity == null) {
            return false;
        }
        String pubB64 = identity.identityPublicKey();
        if (pubB64 == null || pubB64.isBlank()) {
            LOG.warning("BE encryption: missing identityPublicKey for " + identity.username());
            return false;
        }
        try {
            PublicKey clientKey = BedrockEncryption.parseKey(pubB64);
            KeyPair serverKeyPair = BedrockEncryption.createKeyPair();
            byte[] token = BedrockEncryption.generateRandomToken();
            String jwt = BedrockEncryption.createHandshakeJwt(serverKeyPair, token);
            SecretKey secret = BedrockEncryption.getSecretKey(
                    serverKeyPair.getPrivate(), clientKey, token);

            host.sendPacket(state, LinkCloudburstCodecs.serverToClientHandshake(jwt));
            state.peer.enableEncryption(secret, state.protocol);
            state.phase = BedrockSessionHost.LoginPhase.AWAITING_ENCRYPTION;
            BedrockJoinProbe.notePhase(state.guid, state.phase.name());
            LOG.info("BE ServerToClientHandshake + enableEncryption guid="
                    + Long.toHexString(state.guid)
                    + " proto=" + state.protocol
                    + " ctr=" + (state.protocol >= BedrockEncryption.CTR_PROTOCOL_FLOOR));
            // Geyser order: LOGIN_SUCCESS + empty packs immediately (do not wait for C2S handshake).
            sendLoginSuccessAndEmptyPacks(state);
            return true;
        } catch (Exception e) {
            LOG.warning("BE startEncryptionHandshake failed: " + e.getMessage());
            return false;
        }
    }

    void sendLoginSuccessAndEmptyPacks(BedrockSessionHost.ClientState state) {
        if (state.phase != BedrockSessionHost.LoginPhase.AWAITING_ENCRYPTION) {
            return;
        }
        if (state.codec == null) {
            return;
        }
        host.sendPackets(state, List.of(
                LinkCloudburstCodecs.playStatusLoginSuccess(),
                LinkCloudburstCodecs.resourcePacksInfoEmpty()));
        state.phase = BedrockSessionHost.LoginPhase.AWAITING_PACKS;
        BedrockJoinProbe.notePhase(state.guid, state.phase.name());
        LOG.info("BE LOGIN_SUCCESS + ResourcePacksInfo empty guid="
                + Long.toHexString(state.guid) + " pack=empty-handshake proto=" + state.protocol);
    }

    void handlePackStatus(BedrockSessionHost.ClientState state, int status) {
        BedrockSessionHost.LoginPhase phase = state.phase;
        LOG.info("BE pack response guid=" + Long.toHexString(state.guid)
                + " status=" + status + " phase=" + phase + " proto=" + state.protocol);

        if (phase == BedrockSessionHost.LoginPhase.AWAITING_JOIN) {
            return;
        }

        if (phase == BedrockSessionHost.LoginPhase.AWAITING_STACK_COMPLETE) {
            if (status == BedrockSessionHost.PACK_COMPLETED || status < 0) {
                phase1Done(state);
            }
            return;
        }

        if (phase != BedrockSessionHost.LoginPhase.AWAITING_PACKS) {
            if (status == BedrockSessionHost.PACK_COMPLETED) {
                phase1Done(state);
            }
            return;
        }

        if (status == BedrockSessionHost.PACK_REFUSED || status == BedrockSessionHost.PACK_SEND_PACKS || status == BedrockSessionHost.PACK_HAVE_ALL || status < 0) {
            if (status == BedrockSessionHost.PACK_SEND_PACKS) {
                LOG.info("BE pack SEND_PACKS — Phase-1 empty stack (no CDN)");
            }
            sendStackAndAwait(state);
            return;
        }

        if (status == BedrockSessionHost.PACK_COMPLETED) {
            phase1Done(state);
            return;
        }
        sendStackAndAwait(state);
    }

    void sendStackAndAwait(BedrockSessionHost.ClientState state) {
        host.sendPacket(state, LinkCloudburstCodecs.resourcePackStackEmpty());
        state.phase = BedrockSessionHost.LoginPhase.AWAITING_STACK_COMPLETE;
        BedrockJoinProbe.notePhase(state.guid, state.phase.name());
        LOG.info("BE ResourcePackStack empty → await COMPLETED guid="
                + Long.toHexString(state.guid));
    }

    void phase1Done(BedrockSessionHost.ClientState state) {
        state.phase = BedrockSessionHost.LoginPhase.AWAITING_JOIN;
        BedrockJoinProbe.notePhase(state.guid, state.phase.name());
        BedrockJoinProbe.noteEvent(state.guid, "phase1_done packs_COMPLETED");
        LOG.info("BE Phase 1 Done (packs COMPLETED → AWAITING_JOIN) user=" + state.username
                + " guid=" + Long.toHexString(state.guid)
                + " proto=" + state.protocol
                + " — starting Java downstream + join");
        startPhase2And3(state);
    }

    void startPhase2And3(BedrockSessionHost.ClientState state) {
        BedrockSessionHostJoin.startPhase2And3(host, state);
    }

    static int mapPackStatus(ResourcePackClientResponsePacket.Status status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case REFUSED -> BedrockSessionHost.PACK_REFUSED;
            case SEND_PACKS -> BedrockSessionHost.PACK_SEND_PACKS;
            case HAVE_ALL_PACKS -> BedrockSessionHost.PACK_HAVE_ALL;
            case COMPLETED -> BedrockSessionHost.PACK_COMPLETED;
            case NONE -> -1;
        };
    }

    static int decodePackStatus(ByteBuf body, int protocol) {
        if (body == null || !body.isReadable()) {
            return -1;
        }
        ByteBuf b = body.duplicate();
        try {
            if (protocol >= 2168) {
                int wire = BedrockSessionHost.readUnsignedVarInt(b);
                return wire + 1;
            }
            return b.readUnsignedByte();
        } catch (Exception e) {
            return -1;
        }
    }

}
