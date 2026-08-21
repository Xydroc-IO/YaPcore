package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Decodes Bedrock game batches → actions; builds login/spawn reply sequences.
 */
public final class BedrockGameplayBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockBridge");

    public record GameAction(String type, String username, Map<String, String> payload) {
    }

    private final BedrockSessionManager sessions;
    private final FloodgateAuth floodgate;
    private final SkinService skins;
    private final FormService forms;
    private final BedrockEntityTracker entities = new BedrockEntityTracker();
    private final AtomicLong runtimeIds = new AtomicLong(1);
    private final Map<Long, Integer> chunkRadius = new ConcurrentHashMap<>();
    private final Map<Long, Long> runtimeByGuid = new ConcurrentHashMap<>();
    private BiConsumer<Long, List<ByteBuf>> outbound = (guid, packets) -> {
    };

    public BedrockGameplayBridge(BedrockSessionManager sessions,
                                 FloodgateAuth floodgate,
                                 SkinService skins,
                                 FormService forms) {
        this.sessions = sessions;
        this.floodgate = floodgate;
        this.skins = skins;
        this.forms = forms;
    }

    public BedrockEntityTracker entities() {
        return entities;
    }

    public void setOutbound(BiConsumer<Long, List<ByteBuf>> outbound) {
        this.outbound = outbound != null ? outbound : (g, p) -> {
        };
        entities.setBroadcast((except, packets) -> {
            for (Long guid : sessions.allGuids()) {
                if (except != null && except >= 0 && except.equals(guid)) {
                    continue;
                }
                this.outbound.accept(guid, copyPackets(packets));
            }
        });
    }

    private static List<ByteBuf> copyPackets(List<ByteBuf> packets) {
        List<ByteBuf> copy = new ArrayList<>(packets.size());
        for (ByteBuf p : packets) {
            copy.add(p.retainedDuplicate());
        }
        return copy;
    }

    public List<GameAction> onGameBatch(long guid, String address, ByteBuf batch) {
        List<GameAction> actions = new ArrayList<>();
        while (batch.isReadable()) {
            try {
                int len = BedrockPacketCodec.readUnsignedVarInt(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                ByteBuf pkt = batch.readSlice(len);
                BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(pkt);
                handlePacket(guid, address, decoded, actions);
            } catch (Exception e) {
                LOG.fine("BE batch parse: " + e.getMessage());
                break;
            }
        }
        return actions;
    }

    private void handlePacket(long guid, String address, BedrockPacketCodec.Decoded decoded,
                              List<GameAction> actions) {
        BedrockPacketIds kind = BedrockPacketIds.byId(decoded.id());
        if (kind == null) {
            LOG.fine("BE unknown pkt id=" + decoded.id() + " guid=" + Long.toHexString(guid));
            return;
        }
        BedrockSessionManager.BedrockSession s = sessions.get(guid);
        String user = s != null ? s.username() : "BedrockPlayer";
        switch (kind) {
            case REQUEST_NETWORK_SETTINGS -> sendNetworkSettings(guid);
            case LOGIN -> beginLogin(guid, address, decoded.body(), actions);
            case CLIENT_TO_SERVER_HANDSHAKE, RESOURCE_PACK_CLIENT_RESPONSE, SET_LOCAL_PLAYER_AS_INITIALIZED ->
                    sendSpawnSequence(guid, sessions.get(guid));
            case MOVE_PLAYER -> {
                var move = BedrockPacketCodec.tryDecodeMove(decoded.body());
                if (move != null) {
                    actions.add(moveAction(user, move.x(), move.y(), move.z(), move.yaw(), move.pitch()));
                }
            }
            case PLAYER_AUTH_INPUT -> {
                var auth = BedrockPacketCodec.tryDecodeAuthInput(decoded.body());
                if (auth != null) {
                    Map<String, String> p = new HashMap<>();
                    p.put("x", Integer.toString((int) auth.x()));
                    p.put("y", Integer.toString((int) auth.y()));
                    p.put("z", Integer.toString((int) auth.z()));
                    p.put("yaw", Float.toString(auth.yaw()));
                    p.put("pitch", Float.toString(auth.pitch()));
                    p.put("tick", Long.toString(auth.tick()));
                    actions.add(new GameAction("MOVE", user, p));
                    Long runtime = runtimeByGuid.get(guid);
                    if (runtime != null) {
                        entities.move(runtime, auth.x(), auth.y(), auth.z(), auth.yaw(), auth.pitch());
                    }
                }
            }
            case TEXT -> {
                var text = BedrockPacketCodec.tryDecodeText(decoded.body());
                if (text != null) {
                    actions.add(new GameAction("CHAT", user, Map.of("msg", text.message())));
                }
            }
            case PLAYER_ACTION -> {
                var act = BedrockPacketCodec.tryDecodePlayerAction(decoded.body());
                if (act != null && act.isBreakRelated()) {
                    actions.add(new GameAction("BREAK", user, Map.of(
                            "x", Integer.toString(act.x()),
                            "y", Integer.toString(act.y()),
                            "z", Integer.toString(act.z()),
                            "face", Integer.toString(act.face()),
                            "action", Integer.toString(act.action())
                    )));
                    sendBlockUpdate(guid, act.x(), act.y(), act.z(), 0);
                } else if (act != null) {
                    actions.add(new GameAction("PLACE", user, Map.of(
                            "x", Integer.toString(act.x()),
                            "y", Integer.toString(act.y()),
                            "z", Integer.toString(act.z()),
                            "face", Integer.toString(act.face()),
                            "action", Integer.toString(act.action())
                    )));
                } else {
                    actions.add(new GameAction("BREAK", user, Map.of("pkt", kind.name())));
                }
            }
            case INVENTORY_TRANSACTION -> {
                var tx = BedrockPacketCodec.tryDecodeInventoryTransaction(decoded.body());
                if (tx != null && tx.hasPos() && tx.likelyUseItemOn()) {
                    actions.add(new GameAction("PLACE", user, Map.of(
                            "x", Integer.toString(tx.x()),
                            "y", Integer.toString(tx.y()),
                            "z", Integer.toString(tx.z()),
                            "tx", Integer.toString(tx.transactionType())
                    )));
                } else if (tx != null && tx.hasPos()) {
                    actions.add(new GameAction("BREAK", user, Map.of(
                            "x", Integer.toString(tx.x()),
                            "y", Integer.toString(tx.y()),
                            "z", Integer.toString(tx.z())
                    )));
                    sendBlockUpdate(guid, tx.x(), tx.y(), tx.z(), 0);
                } else {
                    actions.add(new GameAction("BREAK", user, Map.of("pkt", kind.name())));
                }
            }
            case INTERACT -> {
                var interact = BedrockPacketCodec.tryDecodeInteract(decoded.body());
                if (interact != null) {
                    actions.add(new GameAction("ATTACK", user, Map.of(
                            "target", Long.toString(interact.targetRuntimeId()),
                            "action", Byte.toString(interact.action())
                    )));
                } else {
                    actions.add(new GameAction("ATTACK", user, Map.of("pkt", kind.name())));
                }
            }
            case ANIMATE ->
                    actions.add(new GameAction("ATTACK", user, Map.of("pkt", kind.name())));
            case MOB_EQUIPMENT, INVENTORY_CONTENT, INVENTORY_SLOT, PLAYER_HOTBAR ->
                    actions.add(new GameAction("INV", user, Map.of("pkt", kind.name())));
            case ITEM_STACK_REQUEST -> {
                var req = BedrockPacketCodec.tryDecodeItemStackRequest(decoded.body());
                if (req != null) {
                    outbound.accept(guid, List.of(BedrockPacketCodec.itemStackResponseOk(req.requestId())));
                    actions.add(new GameAction("INV", user, Map.of(
                            "requestId", Integer.toString(req.requestId()),
                            "actions", Integer.toString(req.actionCount())
                    )));
                } else {
                    actions.add(new GameAction("INV", user, Map.of("pkt", kind.name())));
                }
            }
            case MODAL_FORM_RESPONSE -> forms.handleResponse(user, decoded.body());
            case PLAYER_SKIN -> skins.ingestClientSkin(user, decoded.body());
            case COMMAND_REQUEST ->
                    actions.add(new GameAction("CHAT", user, Map.of("msg", "/cmd")));
            case REQUEST_CHUNK_RADIUS -> handleChunkRadius(guid, decoded.body(), actions, user);
            default -> LOG.fine("BE pkt " + kind + " id=" + decoded.id());
        }
    }

    private static GameAction moveAction(String user, float x, float y, float z, float yaw, float pitch) {
        return new GameAction("MOVE", user, Map.of(
                "x", Integer.toString((int) x),
                "y", Integer.toString((int) y),
                "z", Integer.toString((int) z),
                "yaw", Float.toString(yaw),
                "pitch", Float.toString(pitch)
        ));
    }

    private void sendNetworkSettings(long guid) {
        outbound.accept(guid, List.of(BedrockPacketCodec.networkSettingsUncompressed()));
        LOG.fine("BE network settings → guid=" + Long.toHexString(guid));
    }

    private void handleChunkRadius(long guid, ByteBuf body, List<GameAction> actions, String user) {
        int radius = 8;
        try {
            radius = Math.max(2, Math.min(16, BedrockPacketCodec.readUnsignedVarInt(body.duplicate())));
        } catch (Exception ignored) {
            // default
        }
        chunkRadius.put(guid, radius);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(8, 64, -8, radius * 16));
        // Send a 3×3 air chunk ring around spawn so clients leave the loading screen
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                out.add(BedrockPacketCodec.levelChunkEmpty(cx, cz));
            }
        }
        outbound.accept(guid, out);
        actions.add(new GameAction("MOVE", user, Map.of("chunkRadius", Integer.toString(radius))));
    }

    private void sendBlockUpdate(long guid, int x, int y, int z, int runtimeAir) {
        outbound.accept(guid, List.of(
                BedrockPacketCodec.updateBlock(x, y, z, runtimeAir, 0, 0)
        ));
    }

    private void beginLogin(long guid, String address, ByteBuf body, List<GameAction> actions) {
        FloodgateAuth.Identity identity = floodgate.authenticate(body, address);
        long runtime = runtimeIds.getAndIncrement();
        sessions.open(guid, identity.username(), identity.protocol(), address);
        runtimeByGuid.put(guid, runtime);
        skins.registerDefault(identity.username(), identity.javaUuid());
        entities.addPlayer(runtime, runtime, identity.javaUuid(), identity.username(),
                8.5f, 65.62f, -7.5f, false);
        // Tell everyone else about the new player
        ByteBuf announce = BedrockPacketCodec.addPlayer(
                identity.javaUuid(), identity.username(), runtime, 8.5f, 65.62f, -7.5f, 0f, 0f);
        for (Long other : sessions.allGuids()) {
            if (!other.equals(guid)) {
                outbound.accept(other, List.of(announce.retainedDuplicate()));
            }
        }
        announce.release();
        actions.add(new GameAction("JOIN", identity.username(), Map.of(
                "protocol", Integer.toString(identity.protocol()),
                "xuid", identity.xuid(),
                "uuid", identity.javaUuid().toString(),
                "floodgate", "true",
                "runtimeId", Long.toString(runtime)
        )));
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_SUCCESS));
        out.add(BedrockPacketCodec.resourcePacksInfoEmpty());
        out.add(BedrockPacketCodec.resourcePackStackEmpty());
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore",
                8, 64, -8, identity.javaUuid()));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(8));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(8, 64, -8, 128));
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                out.add(BedrockPacketCodec.levelChunkEmpty(cx, cz));
            }
        }
        out.add(BedrockPacketCodec.inventoryContentEmpty(0, 36));
        out.addAll(entities.snapshotPackets());
        ByteBuf skin = skins.clientboundSkinPacket(identity.username());
        if (skin != null) {
            out.add(skin);
        }
        outbound.accept(guid, out);
        LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                + " uuid=" + identity.javaUuid());
    }

    private void sendSpawnSequence(long guid, BedrockSessionManager.BedrockSession session) {
        if (session == null) {
            return;
        }
        long runtime = runtimeIds.getAndIncrement();
        UUID uuid = floodgate.uuidFor(session.username());
        int radius = chunkRadius.getOrDefault(guid, 8);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore", 8, 64, -8, uuid));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(8, 64, -8, radius * 16));
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                out.add(BedrockPacketCodec.levelChunkEmpty(cx, cz));
            }
        }
        outbound.accept(guid, out);
    }

    public ByteBuf encodeChatToClient(String source, String message) {
        return BedrockPacketCodec.textChat(source, message);
    }

    public ByteBuf encodeBlockUpdate(int x, int y, int z, int runtimeId) {
        return BedrockPacketCodec.updateBlock(x, y, z, runtimeId, 0, 0);
    }

    public void onDisconnect(long guid) {
        Long runtime = runtimeByGuid.remove(guid);
        BedrockSessionManager.BedrockSession session = sessions.get(guid);
        if (session != null) {
            entities.removeByName(session.username());
        } else if (runtime != null) {
            entities.remove(runtime);
        }
        sessions.close(guid);
        chunkRadius.remove(guid);
    }
}
