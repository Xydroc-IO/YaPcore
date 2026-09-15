package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.downstream.JavaPlayWire;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;

/**
 * Full Bedrock {@link ItemStackRequestPacket} → local inventory + JE container_click / drop.
 *
 * <p>Covers survival take / place / swap / drop with {@link ItemStackResponsePacket} acks.
 * JE remains authoritative via {@code container_set_*} snapshots; this path keeps the
 * Bedrock UI responsive when {@code inventoriesServerAuthoritative=false}.
 */
public final class BedrockItemStackRequests {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockItemStackRequests() {
    }

    public static void translate(LinkBedrockSession session, ItemStackRequestPacket packet) {
        if (session == null || packet == null || packet.getRequests() == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        ItemStackResponsePacket response = new ItemStackResponsePacket();
        for (ItemStackRequest request : packet.getRequests()) {
            if (request == null) {
                continue;
            }
            response.getEntries().add(handleOne(session, request));
        }
        if (!response.getEntries().isEmpty()) {
            session.sendUpstreamPacket(response);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "BE ItemStackRequest handled n=" + packet.getRequests().size());
    }

    private static ItemStackResponse handleOne(LinkBedrockSession session, ItemStackRequest request) {
        int requestId = request.getRequestId();
        ItemStackRequestAction[] actions = request.getActions();
        if (actions == null || actions.length == 0) {
            return ok(requestId, List.of());
        }
        Map<ContainerSlotType, List<ItemStackResponseSlot>> touched = new HashMap<>();
        boolean ok = true;
        for (ItemStackRequestAction action : actions) {
            if (action == null) {
                continue;
            }
            ItemStackRequestActionType type = action.getType();
            try {
                if (type == ItemStackRequestActionType.TAKE || type == ItemStackRequestActionType.PLACE) {
                    applyTransfer(session, (TransferItemStackRequestAction) action, touched);
                } else if (type == ItemStackRequestActionType.SWAP) {
                    applySwap(session, (SwapAction) action, touched);
                } else if (type == ItemStackRequestActionType.DROP) {
                    applyDrop(session, (DropAction) action, touched);
                } else if (type == ItemStackRequestActionType.DESTROY
                        || type == ItemStackRequestActionType.CONSUME) {
                    // Creative destroy / consume — treat as drop into void (no world item).
                    if (action instanceof TransferItemStackRequestAction transfer) {
                        clearSource(session, transfer.getSource(), transfer.getCount(), touched);
                    } else if (action instanceof DropAction drop) {
                        applyDrop(session, drop, touched);
                    }
                } else if (type == ItemStackRequestActionType.CRAFT_RECIPE
                        || type == ItemStackRequestActionType.CRAFT_RECIPE_AUTO
                        || type == ItemStackRequestActionType.CRAFT_CREATIVE
                        || type == ItemStackRequestActionType.CRAFT_RESULTS_DEPRECATED
                        || type == ItemStackRequestActionType.CRAFT_RECIPE_OPTIONAL) {
                    // Client-auth craft: ingredients arrive as TAKE/PLACE; take result via JE click.
                    BedrockItemStackJeForward.applyCraftResultClick(session, action);
                } else {
                    LOG.fine("BE ItemStackRequest skip type=" + type + " user=" + session.username());
                }
            } catch (Exception e) {
                ok = false;
                LOG.warning("BE ItemStackRequest fail type=" + type + ": " + e.getMessage());
                BedrockJoinProbe.noteEvent(session.guid(),
                        "BE ItemStackRequest FAIL type=" + type + " err=" + e.getMessage());
                break;
            }
        }
        if (!ok) {
            // Resync from last JE snapshot so client recovers.
            JavaInventoryTranslator.pushPlayerInventory(session);
            return new ItemStackResponse(ItemStackResponseStatus.ERROR, requestId, List.of());
        }
        List<ItemStackResponseContainer> containers = new ArrayList<>();
        for (Map.Entry<ContainerSlotType, List<ItemStackResponseSlot>> e : touched.entrySet()) {
            containers.add(new ItemStackResponseContainer(
                    e.getKey(), e.getValue(), new FullContainerName(e.getKey(), null)));
        }
        return ok(requestId, containers);
    }

    private static ItemStackResponse ok(int requestId, List<ItemStackResponseContainer> containers) {
        return new ItemStackResponse(ItemStackResponseStatus.OK, requestId, containers);
    }

    private static void applyTransfer(LinkBedrockSession session,
                                      TransferItemStackRequestAction action,
                                      Map<ContainerSlotType, List<ItemStackResponseSlot>> touched) {
        int count = Math.max(1, action.getCount());
        ItemStackRequestSlotData source = action.getSource();
        ItemStackRequestSlotData dest = action.getDestination();
        ItemData taken = takeFrom(session, source, count);
        if (taken == null || taken == ItemData.AIR || taken.isNull()) {
            noteSlot(touched, source, ItemData.AIR, session);
            return;
        }
        ItemData placed = putInto(session, dest, taken);
        noteSlot(touched, source, getSlot(session, source), session);
        noteSlot(touched, dest, placed != null ? placed : getSlot(session, dest), session);
        BedrockItemStackJeForward.forwardJeClick(session, source, dest, count, false);
    }

    private static void applySwap(LinkBedrockSession session, SwapAction action,
                                  Map<ContainerSlotType, List<ItemStackResponseSlot>> touched) {
        ItemStackRequestSlotData a = action.getSource();
        ItemStackRequestSlotData b = action.getDestination();
        ItemData left = getSlot(session, a);
        ItemData right = getSlot(session, b);
        setSlot(session, a, right != null ? right : ItemData.AIR);
        setSlot(session, b, left != null ? left : ItemData.AIR);
        noteSlot(touched, a, getSlot(session, a), session);
        noteSlot(touched, b, getSlot(session, b), session);
        BedrockItemStackJeForward.forwardJeClick(session, a, b, 1, true);
    }

    private static void applyDrop(LinkBedrockSession session, DropAction action,
                                  Map<ContainerSlotType, List<ItemStackResponseSlot>> touched) {
        int count = Math.max(1, action.getCount());
        ItemStackRequestSlotData source = action.getSource();
        ItemData taken = takeFrom(session, source, count);
        noteSlot(touched, source, getSlot(session, source), session);
        if (taken == null || taken == ItemData.AIR || taken.isNull()) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
            boolean all = count >= Math.max(1, taken.getCount());
            int status = all ? JavaPlayWire.ACTION_DROP_ALL : JavaPlayWire.ACTION_DROP_ITEM;
            int seq = session.nextBlockSequence();
            down.sendPlayerAction(status,
                    (int) Math.floor(session.posX()),
                    (int) Math.floor(session.posY()),
                    (int) Math.floor(session.posZ()),
                    0, seq);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE drop via=ItemStackRequest all=" + all + " count=" + count);
        }
    }

    private static void clearSource(LinkBedrockSession session, ItemStackRequestSlotData source,
                                    int count,
                                    Map<ContainerSlotType, List<ItemStackResponseSlot>> touched) {
        takeFrom(session, source, Math.max(1, count));
        noteSlot(touched, source, getSlot(session, source), session);
    }

    private static ItemData takeFrom(LinkBedrockSession session, ItemStackRequestSlotData slot, int count) {
        ItemData current = getSlot(session, slot);
        if (current == null || current == ItemData.AIR || current.isNull()) {
            return ItemData.AIR;
        }
        int have = Math.max(1, current.getCount());
        int take = Math.min(count, have);
        if (take >= have) {
            setSlot(session, slot, ItemData.AIR);
            return current;
        }
        ItemData remain = ItemData.builder()
                .definition(current.getDefinition())
                .count(have - take)
                .damage(current.getDamage())
                .blockDefinition(current.getBlockDefinition())
                .build();
        ItemData moved = ItemData.builder()
                .definition(current.getDefinition())
                .count(take)
                .damage(current.getDamage())
                .blockDefinition(current.getBlockDefinition())
                .build();
        setSlot(session, slot, remain);
        return moved;
    }

    private static ItemData putInto(LinkBedrockSession session, ItemStackRequestSlotData slot, ItemData item) {
        if (item == null || item == ItemData.AIR || item.isNull()) {
            return ItemData.AIR;
        }
        ItemData existing = getSlot(session, slot);
        if (existing == null || existing == ItemData.AIR || existing.isNull()) {
            setSlot(session, slot, item);
            return item;
        }
        if (sameStack(existing, item)) {
            int merged = Math.min(64, existing.getCount() + item.getCount());
            ItemData out = ItemData.builder()
                    .definition(existing.getDefinition())
                    .count(merged)
                    .damage(existing.getDamage())
                    .blockDefinition(existing.getBlockDefinition())
                    .build();
            setSlot(session, slot, out);
            return out;
        }
        // Occupied with different item — swap (client usually sends SWAP, but be safe).
        setSlot(session, slot, item);
        return item;
    }

    private static boolean sameStack(ItemData a, ItemData b) {
        if (a == null || b == null || a.getDefinition() == null || b.getDefinition() == null) {
            return false;
        }
        return a.getDefinition().getRuntimeId() == b.getDefinition().getRuntimeId()
                && a.getDamage() == b.getDamage();
    }

    private static ItemData getSlot(LinkBedrockSession session, ItemStackRequestSlotData slot) {
        if (slot == null) {
            return ItemData.AIR;
        }
        ContainerSlotType type = slot.getContainer();
        int index = Math.max(0, slot.getSlot());
        if (type == ContainerSlotType.CURSOR) {
            return session.cursorItem();
        }
        if (type == ContainerSlotType.HOTBAR || type == ContainerSlotType.HOTBAR_AND_INVENTORY) {
            ItemData[] inv = ensureInv(session);
            int i = type == ContainerSlotType.HOTBAR ? index : (index < 9 ? index : index);
            if (type == ContainerSlotType.HOTBAR_AND_INVENTORY) {
                // 0-8 hotbar, 9-35 main — matches Bedrock combined layout.
                if (index >= 0 && index < inv.length) {
                    return inv[index] != null ? inv[index] : ItemData.AIR;
                }
                return ItemData.AIR;
            }
            if (i >= 0 && i < 9) {
                return inv[i] != null ? inv[i] : ItemData.AIR;
            }
            return ItemData.AIR;
        }
        if (type == ContainerSlotType.INVENTORY) {
            ItemData[] inv = ensureInv(session);
            // Bedrock inventory container slots are often 0-26 → JE main 9-35 → BE inv[9..35]
            int be = index <= 26 ? index + 9 : index;
            if (be >= 0 && be < inv.length) {
                return inv[be] != null ? inv[be] : ItemData.AIR;
            }
            return ItemData.AIR;
        }
        if (type == ContainerSlotType.ARMOR) {
            ItemData[] armor = ensureArmor(session);
            if (index >= 0 && index < armor.length) {
                return armor[index] != null ? armor[index] : ItemData.AIR;
            }
            return ItemData.AIR;
        }
        if (type == ContainerSlotType.OFFHAND) {
            ItemData off = session.bedrockOffhand();
            return off != null ? off : ItemData.AIR;
        }
        // Craft / specialty / open container — JE window content cache keyed by JE slot.
        Integer je = BedrockItemStackSlots.toJeSlot(session, slot);
        int cacheIdx = je != null && je >= 0 ? je : index;
        ItemData cached = session.containerSlot(cacheIdx);
        return cached != null ? cached : ItemData.AIR;
    }

    private static void setSlot(LinkBedrockSession session, ItemStackRequestSlotData slot, ItemData item) {
        if (slot == null) {
            return;
        }
        ItemData value = item != null ? item : ItemData.AIR;
        ContainerSlotType type = slot.getContainer();
        int index = Math.max(0, slot.getSlot());
        if (type == ContainerSlotType.CURSOR) {
            session.setCursorItem(value);
            return;
        }
        if (type == ContainerSlotType.HOTBAR) {
            ItemData[] inv = ensureInv(session).clone();
            if (index >= 0 && index < 9) {
                inv[index] = value;
                session.storeBedrockInventory(inv, ensureArmor(session), session.bedrockOffhand());
                pushBeSlot(session, ContainerId.INVENTORY, index, value);
            }
            return;
        }
        if (type == ContainerSlotType.HOTBAR_AND_INVENTORY) {
            ItemData[] inv = ensureInv(session).clone();
            if (index >= 0 && index < inv.length) {
                inv[index] = value;
                session.storeBedrockInventory(inv, ensureArmor(session), session.bedrockOffhand());
                pushBeSlot(session, ContainerId.INVENTORY, index, value);
            }
            return;
        }
        if (type == ContainerSlotType.INVENTORY) {
            ItemData[] inv = ensureInv(session).clone();
            int be = index <= 26 ? index + 9 : index;
            if (be >= 0 && be < inv.length) {
                inv[be] = value;
                session.storeBedrockInventory(inv, ensureArmor(session), session.bedrockOffhand());
                pushBeSlot(session, ContainerId.INVENTORY, be, value);
            }
            return;
        }
        if (type == ContainerSlotType.ARMOR) {
            ItemData[] armor = ensureArmor(session).clone();
            if (index >= 0 && index < armor.length) {
                armor[index] = value;
                session.storeBedrockInventory(ensureInv(session), armor, session.bedrockOffhand());
                pushBeSlot(session, ContainerId.ARMOR, index, value);
            }
            return;
        }
        if (type == ContainerSlotType.OFFHAND) {
            session.storeBedrockInventory(ensureInv(session), ensureArmor(session), value);
            pushBeSlot(session, ContainerId.OFFHAND, 0, value);
            return;
        }
        Integer je = BedrockItemStackSlots.toJeSlot(session, slot);
        int cacheIdx = je != null && je >= 0 ? je : index;
        session.setContainerSlot(cacheIdx, value);
        int win = Math.max(1, session.lastJeWindowId());
        pushBeSlot(session, win, index, value);
    }

    private static void pushBeSlot(LinkBedrockSession session, int containerId, int slot, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(containerId);
        packet.setSlot(Math.max(0, slot));
        packet.setItem(item != null ? item : ItemData.AIR);
        session.sendUpstreamPacket(packet);
    }

    private static void noteSlot(Map<ContainerSlotType, List<ItemStackResponseSlot>> touched,
                                 ItemStackRequestSlotData slot, ItemData item,
                                 LinkBedrockSession session) {
        if (slot == null || slot.getContainer() == null) {
            return;
        }
        ItemData v = item != null ? item : ItemData.AIR;
        int count = (v == ItemData.AIR || v.isNull()) ? 0 : Math.max(0, v.getCount());
        int netId = session.nextStackNetworkId();
        if (count == 0) {
            netId = 0;
        }
        ItemStackResponseSlot resp = new ItemStackResponseSlot(
                slot.getSlot(), slot.getSlot(), count, netId, "", 0, "");
        touched.computeIfAbsent(slot.getContainer(), k -> new ArrayList<>()).add(resp);
    }

    private static ItemData[] ensureInv(LinkBedrockSession session) {
        ItemData[] inv = session.bedrockInventorySlots();
        if (inv == null || inv.length != 36) {
            inv = new ItemData[36];
            for (int i = 0; i < 36; i++) {
                inv[i] = ItemData.AIR;
            }
            session.storeBedrockInventory(inv, ensureArmor(session), session.bedrockOffhand());
        }
        return inv;
    }

    private static ItemData[] ensureArmor(LinkBedrockSession session) {
        ItemData[] armor = session.bedrockArmorSlots();
        if (armor == null || armor.length != 4) {
            armor = new ItemData[]{ItemData.AIR, ItemData.AIR, ItemData.AIR, ItemData.AIR};
        }
        return armor;
    }
}
