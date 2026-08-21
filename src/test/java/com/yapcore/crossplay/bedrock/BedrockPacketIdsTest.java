package com.yapcore.crossplay.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedrockPacketIdsTest {

    @Test
    void registryCoversGameplaySurface() {
        assertTrue(BedrockPacketIds.size() > 150);
        assertEquals(BedrockPacketIds.LOGIN, BedrockPacketIds.byId(0x01));
        assertEquals(BedrockPacketIds.START_GAME, BedrockPacketIds.byId(0x0b));
        assertEquals(BedrockPacketIds.PLAYER_AUTH_INPUT, BedrockPacketIds.byId(0x90));
        assertEquals(BedrockPacketIds.REQUEST_NETWORK_SETTINGS, BedrockPacketIds.byId(0xc1));
        assertEquals(BedrockPacketIds.MODAL_FORM_REQUEST, BedrockPacketIds.byId(0x64));
    }
}
