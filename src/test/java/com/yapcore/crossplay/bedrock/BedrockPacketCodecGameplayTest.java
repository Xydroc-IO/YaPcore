package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPacketCodecGameplayTest {

    @Test
    void networkSettingsRoundTripId() {
        ByteBuf pkt = BedrockPacketCodec.networkSettingsUncompressed();
        BedrockPacketCodec.Decoded d = BedrockPacketCodec.decode(pkt);
        assertEquals(BedrockPacketIds.NETWORK_SETTINGS.id, d.id());
        pkt.release();
    }

    @Test
    void chunkRadiusAndLevelChunkEncode() {
        ByteBuf radius = BedrockPacketCodec.chunkRadiusUpdated(8);
        assertEquals(BedrockPacketIds.CHUNK_RADIUS_UPDATED.id,
                BedrockPacketCodec.decode(radius).id());
        radius.release();

        ByteBuf chunk = BedrockPacketCodec.levelChunkEmpty(0, 0);
        assertEquals(BedrockPacketIds.LEVEL_CHUNK.id,
                BedrockPacketCodec.decode(chunk).id());
        chunk.release();
    }

    @Test
    void playerActionDecode() {
        ByteBuf body = Unpooled.buffer();
        BedrockPacketCodec.writeUnsignedVarInt(body, 1); // entity
        BedrockPacketCodec.writeUnsignedVarInt(body, 0); // start break
        BedrockPacketCodec.writeBlockPosition(body, 10, 64, -5);
        BedrockPacketCodec.writeUnsignedVarInt(body, 1); // face
        var decoded = BedrockPacketCodec.tryDecodePlayerAction(body);
        assertNotNull(decoded);
        assertEquals(10, decoded.x());
        assertEquals(64, decoded.y());
        assertEquals(-5, decoded.z());
        assertTrue(decoded.isBreakRelated());
        body.release();
    }

    @Test
    void updateBlockEncode() {
        ByteBuf pkt = BedrockPacketCodec.updateBlock(1, 2, 3, 0, 0, 0);
        assertEquals(BedrockPacketCodec.ID_UPDATE_BLOCK,
                BedrockPacketCodec.decode(pkt).id());
        pkt.release();
    }

    @Test
    void addRemovePlayerAndStackResponse() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        ByteBuf add = BedrockPacketCodec.addPlayer(uuid, "Steve", 7, 1f, 64f, 2f, 0f, 0f);
        assertEquals(BedrockPacketCodec.ID_ADD_PLAYER, BedrockPacketCodec.decode(add).id());
        add.release();

        ByteBuf rem = BedrockPacketCodec.removeActor(7);
        assertEquals(BedrockPacketCodec.ID_REMOVE_ENTITY, BedrockPacketCodec.decode(rem).id());
        rem.release();

        ByteBuf inv = BedrockPacketCodec.inventoryContentEmpty(0, 4);
        assertEquals(BedrockPacketCodec.ID_INVENTORY_CONTENT, BedrockPacketCodec.decode(inv).id());
        inv.release();

        ByteBuf body = Unpooled.buffer();
        BedrockPacketCodec.writeSignedVarInt(body, 42);
        BedrockPacketCodec.writeUnsignedVarInt(body, 1);
        var req = BedrockPacketCodec.tryDecodeItemStackRequest(body);
        assertNotNull(req);
        assertEquals(42, req.requestId());
        body.release();

        ByteBuf resp = BedrockPacketCodec.itemStackResponseOk(42);
        assertEquals(BedrockPacketIds.ITEM_STACK_RESPONSE.id, BedrockPacketCodec.decode(resp).id());
        resp.release();
    }

    @Test
    void entityTrackerAddRemove() {
        BedrockEntityTracker tracker = new BedrockEntityTracker();
        tracker.addPlayer(1, 1, java.util.UUID.randomUUID(), "Alex", 0, 64, 0, false);
        assertEquals(1, tracker.all().size());
        assertEquals(1, tracker.snapshotPackets().size());
        tracker.removeByName("Alex");
        assertTrue(tracker.all().isEmpty());
    }
}
