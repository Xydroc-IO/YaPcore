package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.floodgate.LinkFloodgateAuth;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.raknet.RakNetSessionManager;
import com.yapcore.link.bedrock.translator.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;


/** Packet dispatch + post-join gameplay routing (split from {@link BedrockSessionHost}). */
final class BedrockSessionHostPlay {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final BedrockSessionHost host;

    BedrockSessionHostPlay(BedrockSessionHost host) {
        this.host = host;
    }

    void handlePacket(BedrockSessionHost.ClientState state, String address, int id, ByteBuf body) {
        int bare = id & 0x3ff;
        LinkCloudburstCodecs.Session cb = state.codec;

        if (bare == BedrockSessionHost.ID_REQUEST_NETWORK_SETTINGS) {
            int proto = body.readableBytes() >= 4 ? body.readInt() : 0;
            if (proto != 0) {
                state.protocol = proto;
            }
            if (LinkCloudburstCodecs.isModern(state.protocol)) {
                state.codec = LinkCloudburstCodecs.open(state.protocol);
                cb = state.codec;
            }
            host.loginLogic.sendNetworkSettings(state);
            return;
        }

        if (bare == BedrockSessionHost.ID_LOGIN) {
            host.loginLogic.beginLogin(state, address, body.retainedDuplicate());
            return;
        }

        if (cb != null && LinkCloudburstCodecs.isModern(state.protocol)) {
            body.markReaderIndex();
            try {
                BedrockPacket packet = cb.decode(bare, body);
                if (packet instanceof RequestNetworkSettingsPacket rns) {
                    state.protocol = rns.getProtocolVersion();
                    state.codec = LinkCloudburstCodecs.open(state.protocol);
                    host.loginLogic.sendNetworkSettings(state);
                    return;
                }
                if (packet instanceof LoginPacket) {
                    // Prefer raw LOGIN wire for Floodgate — fall through shouldn't happen.
                    return;
                }
                if (packet instanceof ClientToServerHandshakePacket) {
                    LOG.info("BE ClientToServerHandshake guid=" + Long.toHexString(state.guid)
                            + " (enc ack)");
                    host.loginLogic.sendLoginSuccessAndEmptyPacks(state);
                    return;
                }
                if (packet instanceof ResourcePackClientResponsePacket resp) {
                    host.loginLogic.handlePackStatus(state, host.loginLogic.mapPackStatus(resp.getStatus()));
                    return;
                }
                if (packet instanceof SetLocalPlayerAsInitializedPacket) {
                    handleClientInitialized(state);
                    return;
                }
                if (packet instanceof RequestChunkRadiusPacket radius) {
                    handleRequestChunkRadius(state, radius.getRadius());
                    return;
                }
                if (dispatchGameplay(state, packet)) {
                    return;
                }
            } catch (Exception e) {
                body.resetReaderIndex();
                LOG.fine("BE cloudburst decode id=0x" + Integer.toHexString(bare)
                        + ": " + e.getMessage());
                // InteractSerializer_v898 requires optional mouse Vector3f; OPEN_INVENTORY is often
                // only action+runtimeEntityId (4B) → decode fails. Raw-handle inventory/attack.
                if (bare == 0x21 && state.joinSession != null && body.isReadable()) {
                    try {
                        int action = body.readUnsignedByte();
                        long runtime = VarInts.readUnsignedLong(body);
                        BedrockInventoryOpen.onRawInteract(state.joinSession, action, runtime);
                        return;
                    } catch (Exception rawEx) {
                        LOG.info("BE Interact raw fallback fail: " + rawEx.getMessage());
                    }
                }
            }
        }

        if (bare == BedrockSessionHost.ID_CLIENT_TO_SERVER_HANDSHAKE) {
            LOG.info("BE ClientToServerHandshake guid=" + Long.toHexString(state.guid)
                    + " (enc ack)");
            host.loginLogic.sendLoginSuccessAndEmptyPacks(state);
            return;
        }
        if (bare == BedrockSessionHost.ID_RESOURCE_PACK_CLIENT_RESPONSE) {
            int status = host.loginLogic.decodePackStatus(body, state.protocol);
            host.loginLogic.handlePackStatus(state, status);
            return;
        }
        if (bare == BedrockSessionHost.ID_SET_LOCAL_PLAYER_AS_INITIALIZED) {
            handleClientInitialized(state);
            return;
        }
        if (bare == BedrockSessionHost.ID_REQUEST_CHUNK_RADIUS) {
            int radius = body.isReadable() ? body.readUnsignedByte() : 0;
            handleRequestChunkRadius(state, radius);
        }
    }

    /** Phase 4–6 Bedrock C2S → JE translators. Returns true if handled. */
    boolean dispatchGameplay(BedrockSessionHost.ClientState state, BedrockPacket packet) {
        LinkBedrockSession join = state.joinSession;
        if (join == null) {
            return false;
        }
        if (packet instanceof PlayerAuthInputPacket auth) {
            BedrockMoveTranslator.translateAuthInput(join, auth);
            return true;
        }
        if (packet instanceof MovePlayerPacket move) {
            BedrockMoveTranslator.translateMovePlayer(join, move);
            return true;
        }
        if (packet instanceof MobEquipmentPacket eq) {
            BedrockInventoryTranslator.translateMobEquipment(join, eq);
            return true;
        }
        if (packet instanceof ItemStackRequestPacket stack) {
            BedrockInventoryTranslator.translateItemStackRequest(join, stack);
            return true;
        }
        if (packet instanceof PlayerActionPacket act) {
            BedrockActionTranslator.translatePlayerAction(join, act);
            return true;
        }
        if (packet instanceof InventoryTransactionPacket tx) {
            BedrockActionTranslator.translateInventoryTransaction(join, tx);
            BedrockInventoryTranslator.translateInventoryTransaction(join, tx);
            return true;
        }
        if (packet instanceof TextPacket text) {
            ChatTranslator.bedrockToJava(join, text);
            return true;
        }
        if (packet instanceof CommandRequestPacket cmd) {
            BedrockCommandTranslator.translate(join, cmd);
            return true;
        }
        if (packet instanceof InteractPacket interact) {
            BedrockInventoryOpen.onInteract(join, interact);
            return true;
        }
        if (packet instanceof ContainerClosePacket close) {
            BedrockInventoryOpen.onContainerClose(join, close);
            return true;
        }
        if (packet instanceof FilterTextPacket filter) {
            BedrockFilterTextTranslator.translate(join, filter);
            return true;
        }
        if (packet instanceof AnimatePacket animate) {
            if (animate.getAction() == AnimatePacket.Action.SWING_ARM) {
                // Geyser defers Animate swings — immediate miss+tick_end here raced ahead of
                // ITEM_USE_ON_ENTITY and zeroed AttackStrengthTicker before the real attack.
                BedrockCombat.noteMissSwing(join);
            }
            return true;
        }
        if (packet instanceof ModalFormResponsePacket form) {
            BedrockFormBridge.translate(join, form);
            return true;
        }
        return false;
    }

    void handleClientInitialized(BedrockSessionHost.ClientState state) {
        LinkBedrockSession join = state.joinSession;
        if (join == null) {
            LOG.info("BE 0x71 before join session guid=" + Long.toHexString(state.guid));
            return;
        }
        BedrockSetLocalPlayerAsInitializedTranslator.translate(join);
        state.phase = BedrockSessionHost.LoginPhase.SPAWNED;
        BedrockJoinProbe.notePhase(state.guid, state.phase.name());
    }

    void handleRequestChunkRadius(BedrockSessionHost.ClientState state, int radius) {
        // Geyser: store only — do not emit ChunkRadiusUpdated while awaiting 0x71.
        LinkBedrockSession join = state.joinSession;
        if (join != null) {
            join.setClientRenderDistance(Math.max(2, Math.min(32, radius)));
        }
        LOG.info("BE RequestChunkRadius store-only radius=" + radius
                + " user=" + state.username);
    }

}
