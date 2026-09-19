package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PacketClassNamesTest {

    @Test
    void movePlayerPosUsesInnerClass() {
        List<String> names = PacketClassNames.candidates(PacketTypes.Play.Client.MOVE_PLAYER_POS);
        assertEquals("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos", names.get(0));
    }

    @Test
    void addEntityIsClientbound() {
        List<String> names = PacketClassNames.candidates(PacketTypes.Play.Server.ADD_ENTITY);
        assertTrue(names.contains("net.minecraft.network.protocol.game.ClientboundAddEntityPacket"));
    }

    @Test
    void bundleDelimiterDoesNotConcatNull() {
        String joined = String.join(",", PacketClassNames.candidates(PacketTypes.playClient("bundle_delimiter")));
        assertFalse(joined.contains("null"));
        List<String> out = PacketClassNames.candidates(PacketTypes.Play.Server.BUNDLE_DELIMITER);
        assertEquals("net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket", out.get(0));
    }

    @Test
    void handshakeIntentionIncludesMojangName() {
        List<String> names = PacketClassNames.candidates(PacketTypes.Handshake.Client.INTENTION);
        assertTrue(names.contains("net.minecraft.network.protocol.handshake.ClientIntentionPacket"));
    }

    @Test
    void snakeToPascal() {
        assertEquals("SetEntityData", PacketClassNames.snakeToPascal("set_entity_data"));
        assertEquals("AddEntity", PacketClassNames.snakeToPascal("add_entity"));
    }
}
