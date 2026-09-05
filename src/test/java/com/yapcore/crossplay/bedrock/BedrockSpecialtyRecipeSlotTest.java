package com.yapcore.crossplay.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockSpecialtyRecipeSlotTest {

    @Test
    void specialtyResultSlots() {
        assertEquals(1, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_STONECUTTER));
        assertEquals(2, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_CARTOGRAPHY));
        assertEquals(3, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_LOOM));
        assertEquals(3, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_SMITHING));
        assertEquals(-1, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_CHEST));
        assertEquals(-1, BedrockPaperRecipes.specialtyResultSlot(BedrockContainerBridge.TYPE_ANVIL));
    }
}
