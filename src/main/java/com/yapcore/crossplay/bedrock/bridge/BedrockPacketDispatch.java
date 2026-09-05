package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Inbound Bedrock packet dispatch → game actions and side effects. */
public final class BedrockPacketDispatch {

    private final BedrockBridgeContext ctx;
    private final BedrockLoginFlow login;
    private final BedrockWorldPush world;
    private final BedrockInventoryPush inventory;
    private final BedrockCommandHints commands;
    private final BedrockUiBridge ui;

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
                login.sendNetworkSettings(guid);
            }
            case LOGIN -> login.beginLogin(guid, address, decoded.body(), actions);
            case CLIENT_TO_SERVER_HANDSHAKE, RESOURCE_PACK_CLIENT_RESPONSE, SET_LOCAL_PLAYER_AS_INITIALIZED ->
                    login.sendSpawnSequence(guid, ctx.sessions.get(guid));
            case MOVE_PLAYER -> {
                var move = BedrockPacketCodec.tryDecodeMove(decoded.body());
                if (move != null) {
                    actions.add(moveAction(user, move.x(), move.y(), move.z(), move.yaw(), move.pitch()));
                    world.streamColumnsAround(guid, (int) move.x(), (int) move.y(), (int) move.z(), false);
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
                    world.streamColumnsAround(guid, (int) auth.x(), (int) auth.y(), (int) auth.z(), false);
                    inventory.maybePushPaperInventory(guid, user);
                    inventory.pushOpenContainerProgress(guid, user);
                    if ((auth.tick() & 7L) == 0L) {
                        world.mirrorPaperPlayers();
                    }
                }
            }
            case TEXT -> {
                var text = BedrockPacketCodec.tryDecodeText(decoded.body());
                if (text != null) {
                    actions.add(new BedrockGameplayBridge.GameAction("CHAT", user, Map.of("msg", text.message())));
                }
            }
            case PLAYER_ACTION -> handlePlayerAction(guid, user, decoded, actions);
            case INVENTORY_TRANSACTION -> handleInventoryTransaction(guid, user, decoded, actions);
            case INTERACT -> handleInteract(guid, user, decoded, actions);
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
            case ITEM_STACK_REQUEST -> handleItemStackRequest(guid, user, decoded, actions, kind);
            case MODAL_FORM_RESPONSE -> ctx.forms.handleResponse(user, decoded.body());
            case PLAYER_SKIN -> ctx.skins.ingestClientSkin(user, decoded.body());
            case COMMAND_REQUEST -> handleCommandRequest(guid, user, decoded, actions);
            case REQUEST_CHUNK_RADIUS -> login.handleChunkRadius(guid, decoded.body(), actions, user);
            default -> BedrockBridgeContext.LOG.fine("BE pkt " + kind + " id=" + decoded.id());
        }
    }

    private void handlePlayerAction(long guid, String user, BedrockPacketCodec.Decoded decoded,
                                    List<BedrockGameplayBridge.GameAction> actions) {
        var act = BedrockPacketCodec.tryDecodePlayerAction(decoded.body());
        if (act != null && act.isBreakRelated()) {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of(
                    "x", Integer.toString(act.x()),
                    "y", Integer.toString(act.y()),
                    "z", Integer.toString(act.z()),
                    "face", Integer.toString(act.face()),
                    "action", Integer.toString(act.action())
            )));
            world.sendBlockUpdate(guid, act.x(), act.y(), act.z(), 0);
            world.resendColumn(guid, act.x() >> 4, act.z() >> 4);
            world.maybeSyncSkull(guid, act.x(), act.y(), act.z());
        } else if (act != null) {
            actions.add(new BedrockGameplayBridge.GameAction("PLACE", user, Map.of(
                    "x", Integer.toString(act.x()),
                    "y", Integer.toString(act.y()),
                    "z", Integer.toString(act.z()),
                    "face", Integer.toString(act.face()),
                    "action", Integer.toString(act.action())
            )));
            world.resendColumn(guid, act.x() >> 4, act.z() >> 4);
            world.maybeSyncSkull(guid, act.x(), act.y(), act.z());
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "PLAYER_ACTION")));
        }
    }

    private void handleInventoryTransaction(long guid, String user, BedrockPacketCodec.Decoded decoded,
                                            List<BedrockGameplayBridge.GameAction> actions) {
        var tx = BedrockPacketCodec.tryDecodeInventoryTransaction(decoded.body());
        if (tx != null && tx.hasPos() && tx.likelyUseItemOn()) {
            String block = world.paperBlockHint(tx.x(), tx.y(), tx.z());
            int ctype = BedrockContainerBridge.typeForBlock(block);
            if (block != null && (block.contains("CHEST") || block.contains("BARREL")
                    || block.contains("SHULKER") || block.contains("FURNACE")
                    || block.contains("SMOKER") || block.contains("BLAST")
                    || block.contains("ENCHANT") || block.contains("HOPPER")
                    || block.contains("CRAFTING") || block.contains("WORKBENCH")
                    || block.contains("ANVIL") || block.contains("SMITHING")
                    || block.contains("LOOM") || block.contains("STONECUTTER")
                    || block.contains("CARTOGRAPH"))) {
                if (block.contains("CRAFTING") || block.contains("WORKBENCH")) {
                    ctype = BedrockContainerBridge.TYPE_WORKBENCH;
                }
                ctx.containers.open(user, ctype, tx.x(), tx.y(), tx.z());
                if (ctype == BedrockContainerBridge.TYPE_ENCHANT) {
                    inventory.pushEnchantOptions(guid, user);
                }
                actions.add(new BedrockGameplayBridge.GameAction("OPEN_CONTAINER", user, Map.of(
                        "type", Integer.toString(ctype),
                        "x", Integer.toString(tx.x()),
                        "y", Integer.toString(tx.y()),
                        "z", Integer.toString(tx.z()),
                        "block", block
                )));
            } else {
                actions.add(new BedrockGameplayBridge.GameAction("PLACE", user, Map.of(
                        "x", Integer.toString(tx.x()),
                        "y", Integer.toString(tx.y()),
                        "z", Integer.toString(tx.z()),
                        "tx", Integer.toString(tx.transactionType())
                )));
                world.resendColumn(guid, tx.x() >> 4, tx.z() >> 4);
                world.maybeSyncSkull(guid, tx.x(), tx.y(), tx.z());
            }
        } else if (tx != null && tx.hasPos()) {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of(
                    "x", Integer.toString(tx.x()),
                    "y", Integer.toString(tx.y()),
                    "z", Integer.toString(tx.z())
            )));
            world.sendBlockUpdate(guid, tx.x(), tx.y(), tx.z(), 0);
            world.resendColumn(guid, tx.x() >> 4, tx.z() >> 4);
            world.maybeSyncSkull(guid, tx.x(), tx.y(), tx.z());
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "INVENTORY_TRANSACTION")));
        }
    }

    private void handleInteract(long guid, String user, BedrockPacketCodec.Decoded decoded,
                                List<BedrockGameplayBridge.GameAction> actions) {
        var interact = BedrockPacketCodec.tryDecodeInteract(decoded.body());
        if (interact != null) {
            byte act = interact.action();
            if (act == 1 || act == 4) {
                actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of(
                        "target", Long.toString(interact.targetRuntimeId()),
                        "targetName", world.nameForRuntime(interact.targetRuntimeId()),
                        "targetUuid", world.uuidForRuntime(interact.targetRuntimeId()),
                        "action", Byte.toString(act)
                )));
            } else {
                BedrockEntityTracker.Tracked target = ctx.entities.get(interact.targetRuntimeId());
                String targetName = target != null ? target.name() : world.nameForRuntime(interact.targetRuntimeId());
                String actorType = target != null ? target.actorType() : "";
                if (BedrockWorldPush.isVillagerActor(actorType, targetName)) {
                    long trader = interact.targetRuntimeId();
                    BedrockContainerBridge.OpenWindow w = ctx.containers.openVillager(
                            user, trader, targetName);
                    inventory.pushVillagerTrade(guid, user, w, trader);
                    actions.add(new BedrockGameplayBridge.GameAction("OPEN_CONTAINER", user, Map.of(
                            "type", Integer.toString(BedrockContainerBridge.TYPE_VILLAGER),
                            "target", targetName == null ? "" : targetName,
                            "runtime", Long.toString(trader)
                    )));
                } else {
                    actions.add(new BedrockGameplayBridge.GameAction("INTERACT", user, Map.of(
                            "target", Long.toString(interact.targetRuntimeId()),
                            "targetName", targetName == null ? "" : targetName,
                            "action", Byte.toString(act)
                    )));
                }
            }
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of("pkt", "INTERACT")));
        }
    }

    private void handleItemStackRequest(long guid, String user, BedrockPacketCodec.Decoded decoded,
                                        List<BedrockGameplayBridge.GameAction> actions,
                                        BedrockPacketIds kind) {
        var req = BedrockPacketCodec.tryDecodeItemStackRequest(decoded.body());
        if (req != null) {
            ctx.inventory.ensure(user);
            boolean mutated = ctx.inventory.applyActions(user, req.actions());
            ctx.send(guid, BedrockPacketCodec.itemStackResponseOk(req.requestId()));
            if (mutated) {
                inventory.pushInventory(guid, user);
                inventory.pushOpenContainer(guid, user);
                BedrockContainerBridge.OpenWindow ow = ctx.containers.current(user);
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_ENCHANT) {
                    inventory.pushEnchantOptions(guid, user);
                }
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_VILLAGER) {
                    inventory.pushVillagerTrade(guid, user, ow, ow.entityRuntimeId());
                }
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_FURNACE) {
                    inventory.pushFurnaceProgress(guid, ow);
                }
            }
            actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of(
                    "requestId", Integer.toString(req.requestId()),
                    "actions", Integer.toString(req.actionCount()),
                    "mutated", Boolean.toString(mutated)
            )));
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of("pkt", kind.name())));
        }
    }

    private void handleCommandRequest(long guid, String user, BedrockPacketCodec.Decoded decoded,
                                      List<BedrockGameplayBridge.GameAction> actions) {
        var cmd = BedrockPacketCodec.tryDecodeCommandRequest(decoded.body());
        String line = "/";
        if (cmd != null && cmd.command() != null && !cmd.command().isBlank()) {
            line = cmd.command().trim();
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
    }

    private static BedrockGameplayBridge.GameAction moveAction(String user, float x, float y, float z,
                                                               float yaw, float pitch) {
        return new BedrockGameplayBridge.GameAction("MOVE", user, Map.of(
                "x", Integer.toString((int) x),
                "y", Integer.toString((int) y),
                "z", Integer.toString((int) z),
                "yaw", Float.toString(yaw),
                "pitch", Float.toString(pitch)
        ));
    }
}
