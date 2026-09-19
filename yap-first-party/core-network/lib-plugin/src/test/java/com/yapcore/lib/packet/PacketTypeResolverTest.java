package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PacketTypeResolverTest {

    @Test
    void playServerAddEntityIsClientbound() {
        assertEquals(PacketState.PLAY,
                PacketTypeResolver.stateOf("net.minecraft.network.protocol.game.ClientboundAddEntityPacket"));
        assertEquals(PacketDirection.CLIENTBOUND,
                PacketTypeResolver.directionOf("ClientboundAddEntityPacket"));
        assertEquals("add_entity", PacketTypeResolver.nameOf("ClientboundAddEntityPacket"));
        assertEquals(PacketDirection.CLIENTBOUND, PacketTypes.Play.Server.ADD_ENTITY.direction());
    }

    @Test
    void playClientMovePosInnerClass() {
        assertEquals("move_player_pos", PacketTypeResolver.nameOf("ServerboundMovePlayerPacket$Pos"));
        assertEquals(PacketDirection.SERVERBOUND,
                PacketTypeResolver.directionOf("ServerboundMovePlayerPacket$Pos"));
    }

    @Test
    void loginHello() {
        assertEquals(PacketState.LOGIN,
                PacketTypeResolver.stateOf("net.minecraft.network.protocol.login.ClientboundHelloPacket"));
        assertEquals("hello", PacketTypeResolver.nameOf("ClientboundHelloPacket"));
    }

    @Test
    void handshakeIntentionIsServerbound() {
        assertEquals(PacketState.HANDSHAKE,
                PacketTypeResolver.stateOf("net.minecraft.network.protocol.handshake.ClientIntentionPacket"));
        assertEquals(PacketDirection.SERVERBOUND,
                PacketTypeResolver.directionOf("ClientIntentionPacket"));
        assertEquals("intention", PacketTypeResolver.nameOf("ClientIntentionPacket"));
    }

    @Test
    void commonCustomPayloadMatchesPlayName() {
        PacketType common = PacketTypes.commonClient("custom_payload");
        PacketType play = PacketTypes.Play.Client.CUSTOM_PAYLOAD;
        assertTrue(play.matches(common));
        assertTrue(PacketType.ALL.matches(play));
    }

    @Test
    void camelToSnake() {
        assertEquals("set_entity_data", PacketTypeResolver.camelToSnake("SetEntityData"));
    }
}
