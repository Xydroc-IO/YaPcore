package com.yapcore.crossplay.bedrock.cloudburst;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

/**
 * Inventory / creative / attribute packet factories
 * (split from {@link CloudburstPackets} for the ≤500-line gate).
 */
final class CloudburstPacketsInventory {

    private CloudburstPacketsInventory() {}

    /** @deprecated Prefer {@link CloudburstPackets#creativeContentFull(CloudburstSession)}. */
    @Deprecated
    static CreativeContentPacket creativeContentEmpty() {
        return creativeContentFull(null);
    }

    /**
     * Geyser {@code sendRegistryDefinitions()} CreativeContent — full groups+items from
     * {@code creative_items.26_40.json}, resolved against the session item palette.
     */
    static CreativeContentPacket creativeContentFull(CloudburstSession session) {
        return CloudburstCreativeContentCache.packetFor(session);
    }

    /**
     * Geyser {@code PlayerInventoryTranslator.updateInventory} empty trio:
     * inventory(0)×36, armor(120)×4, offhand(119)×1.
     * FullContainerName uses matching ContainerSlotType (Geyser leaves ctor default ANVIL_INPUT
     * on content packets; we set the slot type that matches the container id for 1.26.x).
     */
    static List<InventoryContentPacket> inventoryContentPlayerEmpty() {
        return List.of(
                inventoryContentAir(ContainerId.INVENTORY, 36, ContainerSlotType.INVENTORY),
                inventoryContentAir(ContainerId.ARMOR, 4, ContainerSlotType.ARMOR),
                inventoryContentAir(ContainerId.OFFHAND, 1, ContainerSlotType.OFFHAND));
    }

    private static InventoryContentPacket inventoryContentAir(
            int containerId, int size, ContainerSlotType slotType) {
        InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(containerId);
        packet.setContainerNameData(new FullContainerName(slotType, null));
        List<ItemData> contents = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            contents.add(ItemData.AIR);
        }
        packet.setContents(contents);
        return packet;
    }

    private static InventoryContentPacket inventoryContentAir(int containerId, int size) {
        return inventoryContentAir(containerId, size, ContainerSlotType.INVENTORY);
    }

    /**
     * Geyser {@code sendInitialPlayerState} — MOVEMENT_SPEED attribute only.
     * Default 0.1 matches {@code player.movement.speed} catalog.
     */
    static UpdateAttributesPacket updateAttributesMovementOnly(long runtimeId) {
        return updateAttributesMovementOnly(runtimeId, 0.1f);
    }

    static UpdateAttributesPacket updateAttributesMovementOnly(long runtimeId, float movementSpeed) {
        UpdateAttributesPacket packet = new UpdateAttributesPacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setTick(0L);
        packet.setAttributes(List.of(
                new AttributeData(
                        "minecraft:movement", 0f, Float.MAX_VALUE, movementSpeed,
                        0f, Float.MAX_VALUE, movementSpeed, List.of())));
        return packet;
    }

    static UpdateAttributesPacket updateAttributesDefault(long runtimeId) {
        return updateAttributesDefault(runtimeId, 0.1f);
    }

    static UpdateAttributesPacket updateAttributesDefault(long runtimeId, float movementSpeed) {
        UpdateAttributesPacket packet = new UpdateAttributesPacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setTick(0L);
        List<AttributeData> attrs = new ArrayList<>(5);
        attrs.add(new AttributeData("minecraft:health", 0f, 20f, 20f, 0f, 20f, 20f, List.of()));
        attrs.add(new AttributeData("minecraft:player.hunger", 0f, 20f, 20f, 0f, 20f, 20f, List.of()));
        attrs.add(new AttributeData("minecraft:movement", 0f, Float.MAX_VALUE, movementSpeed,
                0f, Float.MAX_VALUE, movementSpeed, List.of()));
        attrs.add(new AttributeData("minecraft:player.level", 0f, 24791f, 0f, 0f, 24791f, 0f, List.of()));
        attrs.add(new AttributeData("minecraft:player.experience", 0f, 1f, 0f, 0f, 1f, 0f, List.of()));
        packet.setAttributes(attrs);
        return packet;
    }

    static InventoryContentPacket inventoryContentEmpty(int windowId, int size) {
        // Match Geyser: do not override default FullContainerName (ANVIL_INPUT ctor default).
        return inventoryContentAir(windowId, Math.max(0, size));
    }
}
