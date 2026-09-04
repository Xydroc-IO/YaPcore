package com.yapcore.factions.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactionChatStateTest {

    @Test
    void defaultsToPublic() {
        FactionChatState state = new FactionChatState();
        UUID id = UUID.randomUUID();
        assertEquals(FactionChatState.Channel.PUBLIC, state.channel(id));
    }

    @Test
    void remembersFactionAndAllyChannels() {
        FactionChatState state = new FactionChatState();
        UUID id = UUID.randomUUID();
        state.setChannel(id, FactionChatState.Channel.FACTION);
        assertEquals(FactionChatState.Channel.FACTION, state.channel(id));
        state.setChannel(id, FactionChatState.Channel.ALLY);
        assertEquals(FactionChatState.Channel.ALLY, state.channel(id));
    }

    @Test
    void publicClearsStoredChannel() {
        FactionChatState state = new FactionChatState();
        UUID id = UUID.randomUUID();
        state.setChannel(id, FactionChatState.Channel.FACTION);
        state.setChannel(id, FactionChatState.Channel.PUBLIC);
        assertEquals(FactionChatState.Channel.PUBLIC, state.channel(id));
        state.setChannel(id, FactionChatState.Channel.ALLY);
        state.clear(id);
        assertEquals(FactionChatState.Channel.PUBLIC, state.channel(id));
    }
}
