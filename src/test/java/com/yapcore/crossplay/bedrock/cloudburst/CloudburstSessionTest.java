package com.yapcore.crossplay.bedrock.cloudburst;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.junit.jupiter.api.Test;

final class CloudburstSessionTest {

    @Test
    void loadsPalettesAndEncodesJoinPackets() {
        CloudburstPaletteRegistry.BandPalettes band40 =
                CloudburstPaletteRegistry.get().forProtocol(2168);
        assertNotNull(band40);
        assertTrue(band40.itemDefinitionList().size() > 100, "items loaded");
        assertTrue(band40.blockDefinitionList().size() > 1000, "blocks loaded");
        assertEquals(0, band40.air().getRuntimeId());

        CloudburstPaletteRegistry.BandPalettes band50 =
                CloudburstPaletteRegistry.get().forProtocol(2192);
        assertNotNull(band50);
        assertEquals("band_26_50", band50.band());

        CloudburstSession session = CloudburstSession.create(2207);
        assertEquals(2192, session.codec().getProtocolVersion());
        assertNotNull(session.helper().getCameraPresetDefinitions());

        StartGamePacket start = CloudburstPackets.startGame(
                1L, 1L, "YaPcore", 8, 64, -8, UUID.randomUUID(), session);
        assertFalse(start.isBlockNetworkIdsHashed(),
                "hashed=false matches Geyser default / 22:18 gold (EMPTY_CHUNK join)");
        assertEquals(3, start.getExperiments().size());
        assertTrue(start.getBlockProperties().isEmpty(), "vanilla: blockProperties empty; palette on helper");
        assertTrue(session.palettes().blockDefinitionList().size() > 1000);
        assertTrue(CloudburstPaletteRegistry.get().biomes().getDefinitions().size() > 10,
                "vanilla biomes required (empty → IC-90 Block)");

        BiomeDefinitionListPacket biomes = CloudburstPackets.biomeDefinitionListVanilla();
        assertTrue(biomes.getBiomes().getDefinitions().size() > 10, "vanilla biome map non-empty");
        assertFalse(biomes.getBiomes().getDefinitions().isEmpty());
        ByteBuf biomeBuf = session.encode(biomes);
        try {
            assertTrue(biomeBuf.readableBytes() > 64, "full biome encode must be substantial");
        } finally {
            biomeBuf.release();
        }

        SyncEntityPropertyPacket sync = CloudburstPackets.syncEntityPropertyEmpty();
        ByteBuf syncBuf = session.encode(sync);
        try {
            assertTrue(syncBuf.readableBytes() > 1);
        } finally {
            syncBuf.release();
        }
        // Vanilla Geyser omits SyncEntityProperty when the registry set is empty.
        assertNotNull(CloudburstPackets.syncEntityProperties());

        var voxel = CloudburstPackets.voxelShapesEmpty();
        ByteBuf voxelBuf = session.encode(voxel);
        try {
            assertTrue(voxelBuf.readableBytes() > 2);
        } finally {
            voxelBuf.release();
        }

        ByteBuf startBuf = session.encode(start);
        try {
            assertTrue(startBuf.readableBytes() > 32);
            int id = VarInts.readUnsignedInt(startBuf.duplicate());
            assertTrue(id > 0);
            // Round-trip decode StartGame body.
            ByteBuf body = startBuf.duplicate();
            VarInts.readUnsignedInt(body);
            var decoded = session.decode(id, body);
            assertTrue(decoded instanceof StartGamePacket);
            StartGamePacket round = (StartGamePacket) decoded;
            assertEquals("YaPcore", round.getLevelName());
        } finally {
            startBuf.release();
        }

        ItemComponentPacket items = CloudburstPackets.itemComponentFull(session);
        ByteBuf itemBuf = session.encode(items);
        try {
            assertTrue(itemBuf.readableBytes() > 64);
            assertFalse(items.getItems().isEmpty());
        } finally {
            itemBuf.release();
        }

        LevelChunkPacket chunk = CloudburstPackets.levelChunkEmpty(0, 0);
        ByteBuf chunkBuf = session.encode(chunk);
        try {
            assertTrue(chunkBuf.readableBytes() > 8);
            // Payload must match Geyser ChunkUtils EMPTY_CHUNK_PAYLOAD (24 sections).
            ByteBuf payload = CloudburstPackets.emptyBiomePayload();
            try {
                byte[] got = new byte[payload.readableBytes()];
                payload.readBytes(got);
                String hex = java.util.HexFormat.of().formatHex(got);
                assertEquals(
                        "0100ffffffffffffffffffffffffffffffffffffffffffffff00",
                        hex,
                        "emptyBiomePayload must be byte-exact vs Geyser EMPTY_CHUNK_PAYLOAD");
            } finally {
                payload.release();
            }
        } finally {
            chunk.release();
            chunkBuf.release();
        }

        var playerList = CloudburstPackets.playerListAddSelf(UUID.randomUUID(), 1L, "TestPlayer");
        assertEquals(1, playerList.getEntries().size());
        assertTrue(playerList.getEntries().get(0).getSkin().isValid(),
                "PlayerList skin must be valid (64x64) — 0x0 empty skins abort IC-90");
        ByteBuf listBuf = session.encode(playerList);
        try {
            assertTrue(listBuf.readableBytes() > 64);
        } finally {
            listBuf.release();
        }

        assertNotNull(CloudburstPackets.gameRulesChangedInitial());
        assertNotNull(CloudburstPackets.setTime(0));
        assertNotNull(CloudburstPackets.setDifficulty(1));
    }

    @Test
    void emptyPackHandshakeEncodes() {
        CloudburstSession session = CloudburstSession.create(2169);
        ResourcePacksInfoPacket info = CloudburstPackets.resourcePacksInfoEmpty();
        assertTrue(info.getResourcePackInfos().isEmpty());
        assertTrue(info.getBehaviorPackInfos().isEmpty());
        assertFalse(info.isForcedToAccept());
        ByteBuf infoBuf = session.encode(info);
        try {
            assertTrue(infoBuf.readableBytes() > 8);
        } finally {
            infoBuf.release();
        }

        ResourcePackStackPacket stack = CloudburstPackets.resourcePackStackEmpty();
        assertTrue(stack.getResourcePacks().isEmpty());
        assertEquals("*", stack.getGameVersion());
        ByteBuf stackBuf = session.encode(stack);
        try {
            assertTrue(stackBuf.readableBytes() > 4);
        } finally {
            stackBuf.release();
        }
    }

    @Test
    void codecIndexBands() {
        assertEquals("band_26_40", CloudburstCodecIndex.bandFor(2168));
        assertEquals("band_26_40", CloudburstCodecIndex.bandFor(2169));
        assertEquals("band_26_50", CloudburstCodecIndex.bandFor(2192));
        assertEquals(2168, CloudburstCodecIndex.MIN_MODERN);
    }
}
