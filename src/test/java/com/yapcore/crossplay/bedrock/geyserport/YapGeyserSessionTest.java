package com.yapcore.crossplay.bedrock.geyserport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.junit.jupiter.api.Test;

/** Asserts YapGeyserSession.connect() packet order matches GeyserSession.connect(). */
final class YapGeyserSessionTest {

    @Test
    void connectEmitsGeyserWireOrder() {
        CloudburstSession cb = CloudburstSession.create(2169);
        BedrockBridgeContext ctx = newCtx();
        YapGeyserSession session = YapGeyserSession.open(
                ctx, 1L, 1L, "TestPlayer", UUID.randomUUID(), 2169, cb);
        List<BedrockPacket> packets = session.connectCollecting(8, 64, -8);

        List<String> types = typeNames(packets);
        List<String> stripped = new ArrayList<>(types.size());
        for (String t : types) {
            if (!"SyncEntityPropertyPacket".equals(t)) {
                stripped.add(t);
            }
        }
        assertEquals(expectedVanillaTypeOrder(), stripped,
                "join wire order must match GeyserSession.connect() only");

        assertFalse(types.contains("DimensionDataPacket"));
        assertEquals(1, types.stream().filter("LevelChunkPacket"::equals).count());
        assertFalse(types.contains("ChunkRadiusUpdatedPacket"));
        assertFalse(types.contains("NetworkChunkPublisherUpdatePacket"));
        assertFalse(types.contains("RespawnPacket"));
        assertFalse(types.contains("CraftingDataPacket"));

        StartGamePacket start = find(packets, StartGamePacket.class);
        assertFalse(start.isBlockNetworkIdsHashed());
        assertEquals(3, start.getExperiments().size());
        assertEquals(AuthoritativeMovementMode.CLIENT, start.getAuthoritativeMovementMode());
        // YaP StartGame uses Paper spawn (no Java teleport); connectCollecting(8,64,-8).
        assertEquals(8.5f, start.getPlayerPosition().getX(), 0.001f);
        assertEquals(64.0f, start.getPlayerPosition().getY(), 0.001f);
        assertEquals(-7.5f, start.getPlayerPosition().getZ(), 0.001f);

        LevelChunkPacket connectChunk = find(packets, LevelChunkPacket.class);
        assertEquals(8 >> 4, connectChunk.getChunkX());
        assertEquals(-8 >> 4, connectChunk.getChunkZ());

        PlayStatusPacket spawn = find(packets, PlayStatusPacket.class);
        assertEquals(PlayStatusPacket.Status.PLAYER_SPAWN, spawn.getStatus());

        for (BedrockPacket packet : packets) {
            ByteBuf buf = cb.encode(packet);
            try {
                assertTrue(buf.readableBytes() > 0, packet.getClass().getSimpleName());
            } finally {
                buf.release();
                if (packet instanceof LevelChunkPacket chunk && chunk.getData() != null) {
                    chunk.getData().release();
                }
            }
        }
    }

    @Test
    void emptyChunkPayloadMatchesGeyser() {
        byte[] expected = new byte[26];
        expected[0] = 0x01;
        expected[1] = 0x00;
        for (int i = 0; i < 23; i++) {
            expected[2 + i] = (byte) 0xFF;
        }
        expected[25] = 0;
        assertArrayEquals(expected, ChunkUtils.emptyChunkPayload(24));
        assertEquals(13, ChunkUtils.squareToCircle(8));
    }

    @Test
    void setServerRenderDistanceSendsChunkRadiusUpdated() {
        CloudburstSession cb = CloudburstSession.create(2169);
        YapGeyserSession session = YapGeyserSession.open(
                newCtx(), 2L, 2L, "Radius", UUID.randomUUID(), 2169, cb);
        List<BedrockPacket> packets = session.setServerRenderDistanceCollecting(8);
        assertEquals(1, packets.size());
        assertEquals("ChunkRadiusUpdatedPacket", packets.get(0).getClass().getSimpleName());
    }

    private static BedrockBridgeContext newCtx() {
        return new BedrockBridgeContext(
                new BedrockSessionManager(),
                new FloodgateAuth(),
                new SkinService(),
                new FormService());
    }

    private static List<String> expectedVanillaTypeOrder() {
        return List.of(
                "VoxelShapesPacket",
                "StartGamePacket",
                "ItemComponentPacket",
                "LevelChunkPacket",
                "BiomeDefinitionListPacket",
                "AvailableEntityIdentifiersPacket",
                "CameraPresetsPacket",
                "CreativeContentPacket",
                "PlayStatusPacket",
                "SetCommandsEnabledPacket",
                "UpdateAttributesPacket",
                "GameRulesChangedPacket",
                "SetTimePacket",
                "GameRulesChangedPacket");
    }

    private static List<String> typeNames(List<? extends BedrockPacket> packets) {
        List<String> names = new ArrayList<>(packets.size());
        for (BedrockPacket packet : packets) {
            names.add(packet.getClass().getSimpleName());
        }
        return names;
    }

    private static <T extends BedrockPacket> T find(List<BedrockPacket> packets, Class<T> type) {
        for (BedrockPacket p : packets) {
            if (type.isInstance(p)) {
                return type.cast(p);
            }
        }
        throw new AssertionError("missing " + type.getSimpleName());
    }
}
