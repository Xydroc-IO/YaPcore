package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.junit.jupiter.api.Test;

final class BedrockItemStackSlotsTest {

    @Test
    void cursorMapsToNullNotMinusOne() {
        ItemStackRequestSlotData cursor = new ItemStackRequestSlotData(
                ContainerSlotType.CURSOR, 0, 0, new FullContainerName(ContainerSlotType.CURSOR, null));
        assertNull(BedrockItemStackSlots.toJeSlot(null, cursor));
    }

    @Test
    void hotbarAndSpecialtyResultSlots() {
        ItemStackRequestSlotData hotbar = new ItemStackRequestSlotData(
                ContainerSlotType.HOTBAR, 3, 0, new FullContainerName(ContainerSlotType.HOTBAR, null));
        assertEquals(39, BedrockItemStackSlots.toJeSlot(null, hotbar));
        assertEquals(3, BedrockItemStackSlots.jeResultSlot(21)); // smithing
        assertEquals(1, BedrockItemStackSlots.jeResultSlot(20)); // stonecutter
        assertEquals(2, BedrockItemStackSlots.jeResultSlot(7));  // anvil
    }
}
