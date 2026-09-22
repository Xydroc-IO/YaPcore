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
                // InteractSerializer_v898 requires optional mouse Vector3f; OPEN_INVENTORY is often
                // only action+runtimeEntityId (4B) → decode fails. Animate v898 SwingSource and
                // InventoryTransaction item defs also fail decode on modern clients — shops die.
                if (bare == 0x21 || bare == 0x1e || bare == 0x2c) {
                    BedrockJoinProbe.noteEvent(state.guid,
                            "be_decode_fail id=0x" + Integer.toHexString(bare)
                                    + " err=" + (e.getMessage() == null ? e.getClass().getSimpleName()
                                    : e.getMessage()));
                } else {
                    LOG.fine("BE cloudburst decode id=0x" + Integer.toHexString(bare)
                            + ": " + e.getMessage());
                }
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
                if (bare == 0x2c && state.joinSession != null && body.isReadable()) {
                    try {
                        // v898: signed byte action; older: zig-zag varint. SWING_ARM wire = 1.
                        int action = body.readByte();
                        long runtime = VarInts.readUnsignedLong(body);
                        BedrockInventoryOpen.onRawAnimate(state.joinSession, action, runtime);
                        return;
                    } catch (Exception rawEx) {
                        LOG.info("BE Animate raw fallback fail: " + rawEx.getMessage());
                    }
                }
                if (bare == 0x1e && state.joinSession != null && body.isReadable()) {
                    try {
                        BedrockInventoryOpen.onRawInventoryTransaction(state.joinSession, body);
                        return;
                    } catch (Exception rawEx) {
                        LOG.info("BE InventoryTransaction raw fallback fail: " + rawEx.getMessage());
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
            BedrockJoinProbe.noteEvent(join.guid(),
                    "be_INV_TX type=" + tx.getTransactionType()
                            + " actionType=" + tx.getActionType()
                            + " runtime=" + tx.getRuntimeEntityId());
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
            AnimatePacket.Action act = animate.getAction();
            BedrockJoinProbe.noteEvent(join.guid(),
                    "be_Animate action=" + act + " runtime=" + animate.getRuntimeEntityId()
                            + " lookTarget=" + join.lastAttackTarget());
            if (act == AnimatePacket.Action.SWING_ARM) {
                // Mobile tap on a shop villager often sends MOUSEOVER then SWING without
                // InventoryTransaction ITEM_USE_ON_ENTITY — treat look-target + swing as use.
                int target = join.lastAttackTarget();
                if (target <= 0 && animate.getRuntimeEntityId() > 0
                        && animate.getRuntimeEntityId() != join.runtimeId()) {
                    // Some clients put the looked-at actor in Animate.runtimeEntityId.
                    target = (int) animate.getRuntimeEntityId();
                }
                if (target > 0 && join.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED) {
                    BedrockInventoryOpen.useEntity(join, target);
                } else {
                    // Geyser defers Animate swings — miss+tick_end races ahead of real attack.
                    BedrockCombat.noteMissSwing(join);
                }
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
        // Geyser: store client preference; when logged in, forward to Java Client Information.
        LinkBedrockSession join = state.joinSession;
        int requested = Math.max(2, Math.min(32, radius));
        if (join != null) {
            join.setClientRenderDistance(requested);
            int full = join.pendingFullRenderDistance() > 0
                    ? join.pendingFullRenderDistance()
                    : LinkBedrockSession.DEFAULT_JAVA_VIEW;
            if (join.joinPhase() == LinkBedrockSession.JoinPhase.AWAITING_CLIENT_INIT) {
                // Never expand past JOIN_BEDROCK_VIEW before 0x71 — client sliders (10–32)
                // previously widened ChunkRadius and Folia streaming, delaying init by minutes.
                int joinView = Math.min(
                        JavaLoginTranslator.JOIN_BEDROCK_VIEW,
                        Math.min(requested, full));
                joinView = Math.max(2, joinView);
                // Always ACK with ChunkRadiusUpdated — 095401 sent RequestChunkRadius(10)
                // and got no reply because joinView already matched, then sat ~52s.
                join.setServerRenderDistance(joinView);
                JavaDownstreamClient down = join.downstream();
                if (down != null) {
                    down.sendClientInformationView(joinView);
                }
                BedrockJoinProbe.noteEvent(join.guid(),
                        "RequestChunkRadius client=" + requested
                                + " joinView=" + joinView
                                + " fullView=" + full
                                + " phase=AWAITING_CLIENT_INIT ack=ChunkRadiusUpdated");
            } else {
                // After join: ACK Bedrock at the larger of client slider and pending JE full
                // view (floored), so fog is not stuck at soft-playable 8–12 or join radius 2.
                int serverView = Math.max(requested, full);
                serverView = Math.max(
                        JavaLoginTranslator.MIN_POST_INIT_VIEW,
                        Math.min(32, serverView));
                if (join.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED
                        || join.isUpstreamInitialized()) {
                    join.setServerRenderDistance(serverView);
                    JavaDownstreamClient down = join.downstream();
                    if (down != null) {
                        down.sendClientInformationView(serverView);
                    }
                    ChunkUtils.forceUpdateChunkPosition(join, join.spawnBlockPos());
                }
                BedrockJoinProbe.noteEvent(join.guid(),
                        "RequestChunkRadius client=" + requested
                                + " serverView=" + serverView
                                + " fullView=" + full
                                + " phase=" + join.joinPhase());
            }
        }
        LOG.info("BE RequestChunkRadius client=" + requested
                + " user=" + state.username
                + (join != null ? " serverView=" + join.getServerRenderDistance() : ""));
    }

}
