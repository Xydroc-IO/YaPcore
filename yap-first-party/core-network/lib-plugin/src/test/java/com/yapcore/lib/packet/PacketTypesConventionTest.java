package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PacketTypesConventionTest {

    @Test
    void clientMeansFromPlayerServerbound() {
        assertEquals(PacketDirection.SERVERBOUND, PacketTypes.Play.Client.CHAT.direction());
        assertEquals(PacketDirection.SERVERBOUND, PacketTypes.Play.Client.MOVE_PLAYER_POS.direction());
        assertEquals(PacketDirection.SERVERBOUND, PacketTypes.Play.Client.INTERACT.direction());
        assertEquals(PacketDirection.SERVERBOUND, PacketTypes.Handshake.Client.INTENTION.direction());
        assertSame(PacketTypes.Play.Client.INTERACT, PacketTypes.Play.Client.USE_ENTITY);
        assertSame(PacketTypes.Play.Client.MOVE_PLAYER_POS, PacketTypes.Play.Client.POSITION);
    }

    @Test
    void serverMeansToPlayerClientbound() {
        assertEquals(PacketDirection.CLIENTBOUND, PacketTypes.Play.Server.ADD_ENTITY.direction());
        assertEquals(PacketDirection.CLIENTBOUND, PacketTypes.Play.Server.SYSTEM_CHAT.direction());
        assertEquals(PacketDirection.CLIENTBOUND, PacketTypes.Play.Server.SET_ENTITY_DATA.direction());
        assertSame(PacketTypes.Play.Server.ADD_ENTITY, PacketTypes.Play.Server.SPAWN_ENTITY);
        assertSame(PacketTypes.Play.Server.SET_ENTITY_DATA, PacketTypes.Play.Server.ENTITY_METADATA);
        assertSame(PacketTypes.Handshake.Client.INTENTION, PacketTypes.Handshake.Client.SET_PROTOCOL);
    }

    @Test
    void factoriesMatchNestedConstants() {
        assertEquals(PacketTypes.Play.Client.CHAT, PacketTypes.playClient("chat"));
        assertEquals(PacketTypes.Play.Server.ADD_ENTITY, PacketTypes.playServer("add_entity"));
        assertFalse(PacketTypes.Play.Client.CHAT.equals(PacketTypes.Play.Server.CHAT));
    }
}
