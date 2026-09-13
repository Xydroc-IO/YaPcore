package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.bedrock.parity.BedrockPortBlockRegistry;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;

/**
 * Legacy raw-buffer C2S handlers (split from {@link BedrockPacketDispatch} for ≤500-line gate).
 */
final class BedrockPacketDispatchLegacy {

    private BedrockPacketDispatchLegacy() {}

    static void handleLegacyEmote(BedrockPacketDispatch d, long guid, String user,
                                  BedrockPacketCodec.Decoded decoded,
                                  List<BedrockGameplayBridge.GameAction> actions) {
        try {
            ByteBuf body = decoded.body().duplicate();
            long runtimeId = BedrockPacketCodec.readUnsignedVarLong(body);
            String emoteId = BedrockPacketCodec.readString(body);
            if (emoteId == null || emoteId.isBlank()) {
                return;
            }
            Map<String, String> payload = new HashMap<>();
            payload.put("emoteId", emoteId.trim());
            payload.put("runtimeId", Long.toString(runtimeId));
            payload.put("pkt", "EMOTE_LEGACY");
            UUID linked = linkedUuid(d, user);
            if (linked != null) {
                payload.put("uuid", linked.toString());
            }
            actions.add(new BedrockGameplayBridge.GameAction("EMOTE", user, payload));
        } catch (Exception e) {
            BedrockBridgeContext.LOG.fine("Legacy EMOTE decode failed: " + e.getMessage());
        }
    }

    static UUID linkedUuid(BedrockPacketDispatch d, String user) {
        if (user == null || d.ctx.floodgate == null) {
            return null;
        }
        try {
            return d.ctx.floodgate.uuidFor(user);
        } catch (Exception e) {
            return null;
        }
    }

    static void handlePlayerAction(BedrockPacketDispatch d, long guid, String user,
                                   BedrockPacketCodec.Decoded decoded,
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
            d.world.sendBlockUpdate(guid, act.x(), act.y(), act.z(), 0);
            d.world.resendColumn(guid, act.x() >> 4, act.z() >> 4);
            d.world.maybeSyncSkull(guid, act.x(), act.y(), act.z());
        } else if (act != null) {
            actions.add(new BedrockGameplayBridge.GameAction("PLACE", user, Map.of(
                    "x", Integer.toString(act.x()),
                    "y", Integer.toString(act.y()),
                    "z", Integer.toString(act.z()),
                    "face", Integer.toString(act.face()),
                    "action", Integer.toString(act.action())
            )));
            d.world.resendColumn(guid, act.x() >> 4, act.z() >> 4);
            d.world.maybeSyncSkull(guid, act.x(), act.y(), act.z());
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "PLAYER_ACTION")));
        }
    }

    static void handleInventoryTransaction(BedrockPacketDispatch d, long guid, String user,
                                           BedrockPacketCodec.Decoded decoded,
                                           List<BedrockGameplayBridge.GameAction> actions) {
        var tx = BedrockPacketCodec.tryDecodeInventoryTransaction(decoded.body());
        if (tx != null && tx.hasPos() && tx.likelyUseItemOn()) {
            String block = d.world.paperBlockHint(tx.x(), tx.y(), tx.z());
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
                d.ctx.containers.open(user, ctype, tx.x(), tx.y(), tx.z());
                if (ctype == BedrockContainerBridge.TYPE_ENCHANT) {
                    d.inventory.pushEnchantOptions(guid, user);
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
                d.world.resendColumn(guid, tx.x() >> 4, tx.z() >> 4);
                d.world.maybeSyncSkull(guid, tx.x(), tx.y(), tx.z());
            }
        } else if (tx != null && tx.hasPos()) {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of(
                    "x", Integer.toString(tx.x()),
                    "y", Integer.toString(tx.y()),
                    "z", Integer.toString(tx.z())
            )));
            d.world.sendBlockUpdate(guid, tx.x(), tx.y(), tx.z(), 0);
            d.world.resendColumn(guid, tx.x() >> 4, tx.z() >> 4);
            d.world.maybeSyncSkull(guid, tx.x(), tx.y(), tx.z());
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "INVENTORY_TRANSACTION")));
        }
    }

    static void handleInteract(BedrockPacketDispatch d, long guid, String user,
                               BedrockPacketCodec.Decoded decoded,
                               List<BedrockGameplayBridge.GameAction> actions) {
        var interact = BedrockPacketCodec.tryDecodeInteract(decoded.body());
        if (interact != null) {
            byte act = interact.action();
            if (act == 1 || act == 4) {
                actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of(
                        "target", Long.toString(interact.targetRuntimeId()),
                        "targetName", d.world.nameForRuntime(interact.targetRuntimeId()),
                        "targetUuid", d.world.uuidForRuntime(interact.targetRuntimeId()),
                        "action", Byte.toString(act)
                )));
            } else {
                BedrockEntityTracker.Tracked target = d.ctx.entities.get(interact.targetRuntimeId());
                String targetName = target != null ? target.name() : d.world.nameForRuntime(interact.targetRuntimeId());
                String actorType = target != null ? target.actorType() : "";
                if (BedrockWorldPush.isVillagerActor(actorType, targetName)) {
                    long trader = interact.targetRuntimeId();
                    BedrockContainerBridge.OpenWindow w = d.ctx.containers.openVillager(
                            user, trader, targetName);
                    d.inventory.pushVillagerTrade(guid, user, w, trader);
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

    static void handleItemStackRequest(BedrockPacketDispatch d, long guid, String user,
                                       BedrockPacketCodec.Decoded decoded,
                                       List<BedrockGameplayBridge.GameAction> actions,
                                       BedrockPacketIds kind) {
        var req = BedrockPacketCodec.tryDecodeItemStackRequest(decoded.body());
        if (req != null) {
            d.ctx.inventory.ensure(user);
            boolean mutated = d.ctx.inventory.applyActions(user, req.actions());
            d.ctx.send(guid, BedrockPacketCodec.itemStackResponseOk(req.requestId()));
            if (mutated) {
                d.inventory.pushInventory(guid, user);
                d.inventory.pushOpenContainer(guid, user);
                BedrockContainerBridge.OpenWindow ow = d.ctx.containers.current(user);
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_ENCHANT) {
                    d.inventory.pushEnchantOptions(guid, user);
                }
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_VILLAGER) {
                    d.inventory.pushVillagerTrade(guid, user, ow, ow.entityRuntimeId());
                }
                if (ow != null && ow.type() == BedrockContainerBridge.TYPE_FURNACE) {
                    d.inventory.pushFurnaceProgress(guid, ow);
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

    static void handleCommandRequest(BedrockPacketDispatch d, long guid, String user,
                                     BedrockPacketCodec.Decoded decoded,
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
        d.commands.applyCommandInventoryHints(user, line);
        d.ui.applyCommandUiHints(guid, user, line);
        d.ctx.send(guid, List.of(
                BedrockPacketCodec.commandOutputSimple(result == null ? "" : result, ok),
                BedrockPacketCodec.textChat("YaPcore", result == null ? line : result)
        ));
        d.inventory.pushInventory(guid, user);
        actions.add(new BedrockGameplayBridge.GameAction("COMMAND", user, Map.of("msg", line, "result",
                result == null ? "" : result)));
    }

    static BedrockGameplayBridge.GameAction moveAction(String user, float x, float y, float z,
                                                       float yaw, float pitch) {
        return new BedrockGameplayBridge.GameAction("MOVE", user, Map.of(
                "x", Integer.toString((int) x),
                "y", Integer.toString((int) y),
                "z", Integer.toString((int) z),
                "yaw", Float.toString(yaw),
                "pitch", Float.toString(pitch)
        ));
    }

    /**
     * Resolve a frozen catalog port id from the Bedrock item / block definition on a place tx.
     * Returns Bedrock {@code minecraft:…} id when it matches {@link BedrockPortBlockRegistry}.
     */
    static String portBlockHintFromTransaction(InventoryTransactionPacket tx) {
        if (tx == null) {
            return null;
        }
        BedrockPortBlockRegistry reg = BedrockPortBlockRegistry.loadDefault();
        ItemData hand = tx.getItemInHand();
        if (hand != null && !hand.isNull()) {
            ItemDefinition def = hand.getDefinition();
            if (def != null) {
                String id = def.getIdentifier();
                if (reg.resolve(id).isPresent()) {
                    return id;
                }
            }
            BlockDefinition itemBlock = hand.getBlockDefinition();
            String fromItemBlock = portIdFromBlockDefinition(reg, itemBlock);
            if (fromItemBlock != null) {
                return fromItemBlock;
            }
        }
        return portIdFromBlockDefinition(reg, tx.getBlockDefinition());
    }

    static String portIdFromBlockDefinition(BedrockPortBlockRegistry reg, BlockDefinition bd) {
        if (bd == null) {
            return null;
        }
        if (bd instanceof SimpleBlockDefinition simple) {
            String id = simple.getIdentifier();
            if (reg.resolve(id).isPresent()) {
                return id;
            }
        }
        int rt = bd.getRuntimeId();
        return reg.byRuntimeIndex(rt).map(BedrockPortBlockRegistry.PortBlock::bedrockId).orElse(null);
    }
}
