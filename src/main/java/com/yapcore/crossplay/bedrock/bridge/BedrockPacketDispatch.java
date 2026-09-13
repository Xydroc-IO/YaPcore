package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/** Inbound Bedrock packet dispatch → game actions and side effects. */
public final class BedrockPacketDispatch {

    final BedrockBridgeContext ctx;
    final BedrockLoginFlow login;
    final BedrockWorldPush world;
    final BedrockInventoryPush inventory;
    final BedrockCommandHints commands;
    final BedrockUiBridge ui;

    public BedrockPacketDispatch(BedrockBridgeContext ctx,
                                 BedrockLoginFlow login,
                                 BedrockWorldPush world,
                                 BedrockInventoryPush inventory,
                                 BedrockCommandHints commands,
                                 BedrockUiBridge ui) {
        this.ctx = ctx;
        this.login = login;
        this.world = world;
        this.inventory = inventory;
        this.commands = commands;
        this.ui = ui;
    }

    /** Modern Cloudburst-decoded C2S path (proto &gt;= 2168). */
    public void handleCloudburstPacket(long guid, String address, BedrockPacket packet,
                                       List<BedrockGameplayBridge.GameAction> actions) {
        if (packet == null) {
            return;
        }
        BedrockSessionManager.BedrockSession s = ctx.sessions.get(guid);
        String user = s != null ? s.username() : "BedrockPlayer";

        if (packet instanceof RequestNetworkSettingsPacket rns) {
            int proto = rns.getProtocolVersion();
            if (proto != 0) {
                ctx.pendingProtocol.put(guid, proto);
            }
            if (proto >= CloudburstCodecIndex.MIN_MODERN) {
                ctx.openCloudburst(guid, proto);
            }
            login.sendNetworkSettings(guid);
            return;
        }
        if (packet instanceof LoginPacket loginPkt) {
            int proto = loginPkt.getProtocolVersion();
            if (proto != 0) {
                ctx.pendingProtocol.put(guid, proto);
            }
            if (proto >= CloudburstCodecIndex.MIN_MODERN) {
                ctx.openCloudburst(guid, proto);
            }
            CloudburstSession cb = ctx.getCloudburst(guid);
            ByteBuf body = Unpooled.EMPTY_BUFFER;
            if (cb != null) {
                ByteBuf encoded = cb.encode(loginPkt);
                try {
                    VarInts.readUnsignedInt(encoded);
                    body = encoded.copy();
                } finally {
                    encoded.release();
                }
            }
            try {
                login.beginLogin(guid, address, body, actions);
            } finally {
                if (body != Unpooled.EMPTY_BUFFER) {
                    body.release();
                }
            }
            return;
        }
        if (packet instanceof org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket) {
            // Informational — LOGIN_SUCCESS already sent right after enableEncryption (Geyser order).
            BedrockBridgeContext.LOG.info(
                    "BE ClientToServerHandshake guid=" + Long.toHexString(guid) + " (enc ack)");
            login.onEncryptionAcknowledged(guid);
            return;
        }
        if (packet instanceof ResourcePackClientResponsePacket resp) {
            login.handlePackClientResponseStatus(guid, BedrockPacketDispatchCloudburst.mapPackStatus(resp.getStatus()), actions);
            return;
        }
        if (packet instanceof SetLocalPlayerAsInitializedPacket) {
            BedrockBridgeContext.LOG.info("BE SetLocalPlayerAsInitialized " + user);
            login.onClientInitialized(guid, actions);
            return;
        }
        if (packet instanceof org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket ls) {
            BedrockBridgeContext.LOG.info(
                    "BE ServerboundLoadingScreen " + user
                            + " type=" + ls.getType()
                            + " screenId=" + ls.getLoadingScreenId()
                            + " phase=" + ctx.loginPhase.get(guid));
            BedrockJoinProbe.noteEvent(guid,
                    "loading_screen type=" + ls.getType() + " id=" + ls.getLoadingScreenId());
            return;
        }
        if (packet instanceof MovePlayerPacket move) {
            // Geyser waits for SetLocalPlayerAsInitialized (0x71) — never soft-init from movement.
            Vector3f pos = move.getPosition();
            Vector3f rot = move.getRotation();
            if (pos != null) {
                float yaw = rot != null ? rot.getY() : 0f;
                float pitch = rot != null ? rot.getX() : 0f;
                actions.add(BedrockPacketDispatchLegacy.moveAction(user, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch));
                if (ctx.isSpawnFullyReady(guid)) {
                    world.streamColumnsAround(guid, (int) pos.getX(), (int) pos.getY(), (int) pos.getZ(), false);
                }
            }
            return;
        }
        if (packet instanceof PlayerAuthInputPacket auth) {
            // Geyser waits for SetLocalPlayerAsInitialized (0x71) — never soft-init from auth-input.
            // v818+ wire omits AuthoritativeMovementMode; client defaults SERVER_WITH_REWIND → auth-input.
            Vector3f pos = auth.getPosition();
            Vector3f rot = auth.getRotation();
            if (pos != null) {
                Map<String, String> p = new HashMap<>();
                p.put("x", Integer.toString((int) pos.getX()));
                p.put("y", Integer.toString((int) pos.getY()));
                p.put("z", Integer.toString((int) pos.getZ()));
                p.put("yaw", Float.toString(rot != null ? rot.getY() : 0f));
                p.put("pitch", Float.toString(rot != null ? rot.getX() : 0f));
                p.put("tick", Long.toString(auth.getTick()));
                actions.add(new BedrockGameplayBridge.GameAction("MOVE", user, p));
                Long runtime = ctx.runtimeByGuid.get(guid);
                if (runtime != null) {
                    ctx.entities.move(runtime, pos.getX(), pos.getY(), pos.getZ(),
                            rot != null ? rot.getY() : 0f, rot != null ? rot.getX() : 0f);
                }
                if (ctx.isSpawnFullyReady(guid)) {
                    world.streamColumnsAround(guid, (int) pos.getX(), (int) pos.getY(), (int) pos.getZ(), false);
                    inventory.maybePushPaperInventory(guid, user);
                    inventory.pushOpenContainerProgress(guid, user);
                    if ((auth.getTick() & 7L) == 0L) {
                        world.mirrorPaperPlayers();
                    }
                }
            }
            return;
        }
        if (packet instanceof TextPacket text) {
            if (text.getType() == TextPacket.Type.CHAT || text.getType() == TextPacket.Type.WHISPER) {
                String msg = text.getMessage();
                if (msg != null) {
                    actions.add(new BedrockGameplayBridge.GameAction("CHAT", user, Map.of("msg", msg)));
                }
            }
            return;
        }
        if (packet instanceof PlayerActionPacket act) {
            BedrockPacketDispatchCloudburst.handleCloudburstPlayerAction(this, guid, user, act, actions);
            return;
        }
        if (packet instanceof InventoryTransactionPacket tx) {
            BedrockPacketDispatchCloudburst.handleCloudburstInventoryTransaction(this, guid, user, tx, actions);
            return;
        }
        if (packet instanceof InteractPacket interact) {
            BedrockPacketDispatchCloudburst.handleCloudburstInteract(this, guid, user, interact, actions);
            return;
        }
        if (packet instanceof ContainerClosePacket) {
            ctx.containers.close(user, false);
            actions.add(new BedrockGameplayBridge.GameAction("CLOSE_CONTAINER", user, Map.of("pkt", "CONTAINER_CLOSE")));
            return;
        }
        if (packet instanceof AnimatePacket) {
            actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of("pkt", "ANIMATE")));
            return;
        }
        if (packet instanceof EmotePacket emote) {
            BedrockPacketDispatchCloudburst.handleCloudburstEmote(this, guid, user, emote, actions);
            return;
        }
        if (packet instanceof MobEquipmentPacket eq) {
            ctx.inventory.setHeldHotbar(user, eq.getHotbarSlot());
            inventory.pushInventory(guid, user);
            actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of(
                    "pkt", "MOB_EQUIPMENT",
                    "hotbar", Integer.toString(eq.getHotbarSlot())
            )));
            return;
        }
        if (packet instanceof ItemStackRequestPacket stackReq) {
            BedrockPacketDispatchCloudburst.handleCloudburstItemStackRequest(this, guid, user, stackReq, actions);
            return;
        }
        if (packet instanceof ModalFormResponsePacket form) {
            ByteBuf body = Unpooled.buffer(64);
            BedrockPacketCodec.writeUnsignedVarInt(body, form.getFormId());
            String data = form.getFormData();
            boolean hasData = data != null && !"null".equals(data);
            body.writeBoolean(hasData);
            if (hasData) {
                BedrockPacketCodec.writeString(body, data);
            }
            try {
                ctx.forms.handleResponse(user, body);
            } finally {
                body.release();
            }
            return;
        }
        if (packet instanceof PlayerSkinPacket skinPkt) {
            if (skinPkt.getSkin() != null && skinPkt.getUuid() != null) {
                ctx.skins.ingestClientSkin(user, skinPkt.getUuid(), skinPkt.getSkin());
            } else {
                CloudburstSession cb = ctx.getCloudburst(guid);
                if (cb != null) {
                    ByteBuf encoded = cb.encode(packet);
                    try {
                        VarInts.readUnsignedInt(encoded);
                        ctx.skins.ingestClientSkin(user, encoded, cb.protocol());
                    } finally {
                        encoded.release();
                    }
                }
            }
            return;
        }
        if (packet instanceof CommandRequestPacket cmd) {
            String line = cmd.getCommand();
            if (line == null || line.isBlank()) {
                line = "/";
            } else {
                line = line.trim();
                if (!line.startsWith("/")) {
                    line = "/" + line;
                }
            }
            String result = com.yapcore.game.command.GameCommandBridge.dispatch(line, null);
            boolean ok = result != null
                    && !result.startsWith("Paper not")
                    && !result.startsWith("Game not ready")
                    && !result.startsWith("Folia is not")
                    && !result.startsWith("Could not")
                    && !result.startsWith("Paper command error")
                    && !result.startsWith("Folia stdin error");
            commands.applyCommandInventoryHints(user, line);
            ui.applyCommandUiHints(guid, user, line);
            ctx.send(guid, List.of(
                    BedrockPacketCodec.commandOutputSimple(result == null ? "" : result, ok),
                    BedrockPacketCodec.textChat("YaPcore", result == null ? line : result)
            ));
            inventory.pushInventory(guid, user);
            actions.add(new BedrockGameplayBridge.GameAction("COMMAND", user, Map.of("msg", line, "result",
                    result == null ? "" : result)));
            return;
        }
        if (packet instanceof RequestChunkRadiusPacket radius) {
            login.handleChunkRadius(guid, radius.getRadius(), actions, user);
            return;
        }
        BedrockBridgeContext.LOG.fine("BE Cloudburst pkt " + packet.getPacketType()
                + " guid=" + Long.toHexString(guid));
    }

    public void handlePacket(long guid, String address, BedrockPacketCodec.Decoded decoded,
                             List<BedrockGameplayBridge.GameAction> actions) {
        BedrockPacketIds kind = BedrockPacketIds.byId(decoded.id());
        if (kind == null) {
            BedrockBridgeContext.LOG.fine("BE unknown pkt id=" + decoded.id() + " guid=" + Long.toHexString(guid));
            return;
        }
        BedrockSessionManager.BedrockSession s = ctx.sessions.get(guid);
        String user = s != null ? s.username() : "BedrockPlayer";
        switch (kind) {
            case REQUEST_NETWORK_SETTINGS -> {
                // Cloudburst RequestNetworkSettingsSerializer_v554: buffer.readInt() = BIG-ENDIAN.
                // FloodgateAuth LOGIN also uses readInt() BE. Do NOT use readIntLE here.
                int proto = 0;
                try {
                    ByteBuf b = decoded.body().duplicate();
                    if (b.readableBytes() >= 4) {
                        proto = b.readInt();
                    }
                } catch (Exception ignored) {
                    // default
                }
                if (proto != 0) {
                    ctx.pendingProtocol.put(guid, proto);
                }
                if (proto >= CloudburstCodecIndex.MIN_MODERN) {
                    ctx.openCloudburst(guid, proto);
                }
                login.sendNetworkSettings(guid);
            }
            case LOGIN -> {
                Integer pending = ctx.pendingProtocol.get(guid);
                if (pending != null && pending >= CloudburstCodecIndex.MIN_MODERN) {
                    ctx.openCloudburst(guid, pending);
                }
                login.beginLogin(guid, address, decoded.body(), actions);
            }
            case CLIENT_TO_SERVER_HANDSHAKE -> {
                // Informational — LOGIN_SUCCESS already sent right after enableEncryption (Geyser order).
                BedrockBridgeContext.LOG.info(
                        "BE ClientToServerHandshake guid=" + Long.toHexString(guid) + " (enc ack)");
                login.onEncryptionAcknowledged(guid);
            }
            case RESOURCE_PACK_CLIENT_RESPONSE -> login.handlePackClientResponse(guid, decoded.body(), actions);
            case SET_LOCAL_PLAYER_AS_INITIALIZED -> {
                BedrockBridgeContext.LOG.info("BE SetLocalPlayerAsInitialized " + user);
                login.onClientInitialized(guid, actions);
            }
            case MOVE_PLAYER -> {
                var move = BedrockPacketCodec.tryDecodeMove(decoded.body());
                if (move != null) {
                    actions.add(BedrockPacketDispatchLegacy.moveAction(user, move.x(), move.y(), move.z(), move.yaw(), move.pitch()));
                    if (ctx.isSpawnFullyReady(guid)) {
                        world.streamColumnsAround(guid, (int) move.x(), (int) move.y(), (int) move.z(), false);
                    }
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
                    actions.add(new BedrockGameplayBridge.GameAction("MOVE", user, p));
                    Long runtime = ctx.runtimeByGuid.get(guid);
                    if (runtime != null) {
                        ctx.entities.move(runtime, auth.x(), auth.y(), auth.z(), auth.yaw(), auth.pitch());
                    }
                    if (ctx.isSpawnFullyReady(guid)) {
                        world.streamColumnsAround(guid, (int) auth.x(), (int) auth.y(), (int) auth.z(), false);
                        inventory.maybePushPaperInventory(guid, user);
                        inventory.pushOpenContainerProgress(guid, user);
                        if ((auth.tick() & 7L) == 0L) {
                            world.mirrorPaperPlayers();
                        }
                    }
                }
            }
            case TEXT -> {
                var text = BedrockPacketCodec.tryDecodeText(decoded.body());
                if (text != null) {
                    actions.add(new BedrockGameplayBridge.GameAction("CHAT", user, Map.of("msg", text.message())));
                }
            }
            case PLAYER_ACTION -> BedrockPacketDispatchLegacy.handlePlayerAction(this, guid, user, decoded, actions);
            case INVENTORY_TRANSACTION -> BedrockPacketDispatchLegacy.handleInventoryTransaction(this, guid, user, decoded, actions);
            case INTERACT -> BedrockPacketDispatchLegacy.handleInteract(this, guid, user, decoded, actions);
            case CONTAINER_CLOSE -> {
                ctx.containers.handleClientClose(user, decoded.body());
                actions.add(new BedrockGameplayBridge.GameAction("CLOSE_CONTAINER", user, Map.of("pkt", kind.name())));
            }
            case FILTER_TEXT -> {
                ctx.containers.handleFilterText(user, decoded.body());
                actions.add(new BedrockGameplayBridge.GameAction("FILTER_TEXT", user, Map.of("pkt", kind.name())));
            }
            case ANIMATE ->
                    actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of("pkt", kind.name())));
            case EMOTE -> BedrockPacketDispatchLegacy.handleLegacyEmote(this, guid, user, decoded, actions);
            case MOB_EQUIPMENT -> {
                var eq = BedrockPacketCodec.tryDecodeMobEquipment(decoded.body());
                if (eq != null) {
                    ctx.inventory.setHeldHotbar(user, eq.hotbarSlot());
                    inventory.pushInventory(guid, user);
                    actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of(
                            "pkt", "MOB_EQUIPMENT",
                            "hotbar", Integer.toString(eq.hotbarSlot())
                    )));
                } else {
                    actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of("pkt", kind.name())));
                }
            }
            case INVENTORY_CONTENT, INVENTORY_SLOT, PLAYER_HOTBAR ->
                    actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of("pkt", kind.name())));
            case ITEM_STACK_REQUEST -> BedrockPacketDispatchLegacy.handleItemStackRequest(this, guid, user, decoded, actions, kind);
            case MODAL_FORM_RESPONSE -> ctx.forms.handleResponse(user, decoded.body());
            case PLAYER_SKIN -> {
                int proto = 2207;
                var sess = ctx.sessions.get(guid);
                if (sess != null && sess.protocol() > 0) {
                    proto = sess.protocol();
                } else {
                    Integer pending = ctx.pendingProtocol.get(guid);
                    if (pending != null && pending > 0) {
                        proto = pending;
                    } else {
                        CloudburstSession cb = ctx.getCloudburst(guid);
                        if (cb != null) {
                            proto = cb.protocol();
                        }
                    }
                }
                ctx.skins.ingestClientSkin(user, decoded.body(), proto);
            }
            case COMMAND_REQUEST -> BedrockPacketDispatchLegacy.handleCommandRequest(this, guid, user, decoded, actions);
            case REQUEST_CHUNK_RADIUS -> login.handleChunkRadius(guid, decoded.body(), actions, user);
            default -> BedrockBridgeContext.LOG.fine("BE pkt " + kind + " id=" + decoded.id());
        }
    }
}
