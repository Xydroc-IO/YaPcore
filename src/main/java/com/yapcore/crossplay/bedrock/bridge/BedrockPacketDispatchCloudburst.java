package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;

/**
 * Cloudburst-decoded C2S handlers (split from {@link BedrockPacketDispatch} for ≤500-line gate).
 */
final class BedrockPacketDispatchCloudburst {

    private BedrockPacketDispatchCloudburst() {}

    static int mapPackStatus(ResourcePackClientResponsePacket.Status status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case REFUSED -> 1;
            case SEND_PACKS -> 2;
            case HAVE_ALL_PACKS -> 3;
            case COMPLETED -> 4;
            default -> -1;
        };
    }

    static void handleCloudburstPlayerAction(BedrockPacketDispatch d, long guid, String user,
                                             PlayerActionPacket act,
                                             List<BedrockGameplayBridge.GameAction> actions) {
        PlayerActionType type = act.getAction();
        Vector3i pos = act.getBlockPosition();
        int x = pos != null ? pos.getX() : 0;
        int y = pos != null ? pos.getY() : 0;
        int z = pos != null ? pos.getZ() : 0;
        int face = act.getFace();
        int actionOrd = type != null ? type.ordinal() : -1;
        boolean breakRelated = type == PlayerActionType.START_BREAK
                || type == PlayerActionType.ABORT_BREAK
                || type == PlayerActionType.STOP_BREAK
                || type == PlayerActionType.CONTINUE_BREAK
                || type == PlayerActionType.BLOCK_PREDICT_DESTROY
                || type == PlayerActionType.BLOCK_CONTINUE_DESTROY;
        if (breakRelated) {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of(
                    "x", Integer.toString(x), "y", Integer.toString(y), "z", Integer.toString(z),
                    "face", Integer.toString(face), "action", Integer.toString(actionOrd))));
            d.world.sendBlockUpdate(guid, x, y, z, 0);
            d.world.resendColumn(guid, x >> 4, z >> 4);
            d.world.maybeSyncSkull(guid, x, y, z);
        } else if (type != null) {
            actions.add(new BedrockGameplayBridge.GameAction("PLACE", user, Map.of(
                    "x", Integer.toString(x), "y", Integer.toString(y), "z", Integer.toString(z),
                    "face", Integer.toString(face), "action", Integer.toString(actionOrd))));
            d.world.resendColumn(guid, x >> 4, z >> 4);
            d.world.maybeSyncSkull(guid, x, y, z);
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "PLAYER_ACTION")));
        }
    }

    static void handleCloudburstInventoryTransaction(BedrockPacketDispatch d, long guid, String user,
                                                     InventoryTransactionPacket tx,
                                                     List<BedrockGameplayBridge.GameAction> actions) {
        Vector3i pos = tx.getBlockPosition();
        boolean hasPos = pos != null;
        InventoryTransactionType type = tx.getTransactionType();
        boolean useItemOn = type == InventoryTransactionType.ITEM_USE;
        if (hasPos && useItemOn) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            String block = d.world.paperBlockHint(x, y, z);
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
                d.ctx.containers.open(user, ctype, x, y, z);
                if (ctype == BedrockContainerBridge.TYPE_ENCHANT) {
                    d.inventory.pushEnchantOptions(guid, user);
                }
                actions.add(new BedrockGameplayBridge.GameAction("OPEN_CONTAINER", user, Map.of(
                        "type", Integer.toString(ctype),
                        "x", Integer.toString(x), "y", Integer.toString(y), "z", Integer.toString(z),
                        "block", block)));
            } else {
                String portBlock = BedrockPacketDispatchLegacy.portBlockHintFromTransaction(tx);
                Map<String, String> place = new HashMap<>();
                place.put("x", Integer.toString(x));
                place.put("y", Integer.toString(y));
                place.put("z", Integer.toString(z));
                place.put("face", Integer.toString(tx.getBlockFace()));
                place.put("tx", Integer.toString(type != null ? type.ordinal() : -1));
                if (portBlock != null) {
                    place.put("block", portBlock);
                }
                actions.add(new BedrockGameplayBridge.GameAction("PLACE", user, place));
                d.world.resendColumn(guid, x >> 4, z >> 4);
                d.world.maybeSyncSkull(guid, x, y, z);
            }
        } else if (hasPos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of(
                    "x", Integer.toString(x), "y", Integer.toString(y), "z", Integer.toString(z))));
            d.world.sendBlockUpdate(guid, x, y, z, 0);
            d.world.resendColumn(guid, x >> 4, z >> 4);
            d.world.maybeSyncSkull(guid, x, y, z);
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("BREAK", user, Map.of("pkt", "INVENTORY_TRANSACTION")));
        }
    }

    static void handleCloudburstInteract(BedrockPacketDispatch d, long guid, String user,
                                         InteractPacket interact,
                                         List<BedrockGameplayBridge.GameAction> actions) {
        InteractPacket.Action act = interact.getAction();
        long target = interact.getRuntimeEntityId();
        if (act == InteractPacket.Action.DAMAGE) {
            actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of(
                    "target", Long.toString(target),
                    "targetName", d.world.nameForRuntime(target),
                    "targetUuid", d.world.uuidForRuntime(target),
                    "action", "DAMAGE")));
        } else if (act == InteractPacket.Action.INTERACT || act == InteractPacket.Action.NPC_OPEN) {
            BedrockEntityTracker.Tracked targetEnt = d.ctx.entities.get(target);
            String targetName = targetEnt != null ? targetEnt.name() : d.world.nameForRuntime(target);
            String actorType = targetEnt != null ? targetEnt.actorType() : "";
            if (BedrockWorldPush.isVillagerActor(actorType, targetName)) {
                BedrockContainerBridge.OpenWindow w = d.ctx.containers.openVillager(user, target, targetName);
                d.inventory.pushVillagerTrade(guid, user, w, target);
                actions.add(new BedrockGameplayBridge.GameAction("OPEN_CONTAINER", user, Map.of(
                        "type", Integer.toString(BedrockContainerBridge.TYPE_VILLAGER),
                        "target", targetName == null ? "" : targetName,
                        "runtime", Long.toString(target))));
            } else {
                actions.add(new BedrockGameplayBridge.GameAction("INTERACT", user, Map.of(
                        "target", Long.toString(target),
                        "targetName", targetName == null ? "" : targetName,
                        "action", act.name())));
            }
        } else {
            actions.add(new BedrockGameplayBridge.GameAction("ATTACK", user, Map.of("pkt", "INTERACT")));
        }
    }

    static void handleCloudburstItemStackRequest(BedrockPacketDispatch d, long guid, String user,
                                                 ItemStackRequestPacket stackReq,
                                                 List<BedrockGameplayBridge.GameAction> actions) {
        var requests = stackReq.getRequests();
        if (requests == null || requests.isEmpty()) {
            actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of("pkt", "ITEM_STACK_REQUEST")));
            return;
        }
        var first = requests.get(0);
        int requestId = first.getRequestId();
        d.ctx.inventory.ensure(user);
        d.ctx.send(guid, BedrockPacketCodec.itemStackResponseOk(requestId));
        d.inventory.pushInventory(guid, user);
        d.inventory.pushOpenContainer(guid, user);
        actions.add(new BedrockGameplayBridge.GameAction("INV", user, Map.of(
                "requestId", Integer.toString(requestId),
                "actions", Integer.toString(first.getActions() != null ? first.getActions().length : 0),
                "mutated", "false")));
    }

    static void handleCloudburstEmote(BedrockPacketDispatch d, long guid, String user, EmotePacket emote,
                                      List<BedrockGameplayBridge.GameAction> actions) {
        String emoteId = emote.getEmoteId();
        if (emoteId == null || emoteId.isBlank()) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("emoteId", emoteId.trim());
        payload.put("runtimeId", Long.toString(emote.getRuntimeEntityId()));
        payload.put("pkt", "EMOTE");
        UUID linked = BedrockPacketDispatchLegacy.linkedUuid(d, user);
        if (linked != null) {
            payload.put("uuid", linked.toString());
        }
        actions.add(new BedrockGameplayBridge.GameAction("EMOTE", user, payload));
    }
}
