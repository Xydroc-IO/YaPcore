package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class BedrockEmoteDispatchTest {

    @Test
    void emotePacketIdsRegistered() {
        assertEquals(0x8a, BedrockPacketIds.EMOTE.id);
        assertEquals(0x98, BedrockPacketIds.EMOTE_LIST.id);
        assertNotNull(BedrockPacketIds.byId(0x8a));
        assertEquals(BedrockPacketIds.EMOTE, BedrockPacketIds.byId(0x8a));
    }
}
