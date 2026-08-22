package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPacketCodecGameplayTest {

    @Test
    void itemstatesLoadedAndStartGameIncludesThem() {
        assertTrue(BedrockItemStates.all().size() > 1000);
        ByteBuf pkt = BedrockPacketCodec.startGame(1, 1, "YaPcore", 0, 64, 0,
                java.util.UUID.randomUUID());
        int size = pkt.readableBytes();
        assertEquals(BedrockPacketIds.START_GAME.id, BedrockPacketCodec.decode(pkt).id());
        assertTrue(size > 10_000, "itemstates should inflate start_game, got " + size);
        pkt.release();
    }

    @Test
    void entityAndBiomeIdentifierPacketsEncode() {
        ByteBuf ent = BedrockPacketCodec.availableEntityIdentifiersEmpty();
        assertEquals(BedrockPacketIds.AVAILABLE_ACTOR_IDENTIFIERS.id,
                BedrockPacketCodec.decode(ent).id());
        ent.release();
        ByteBuf bio = BedrockPacketCodec.biomeDefinitionListEmpty();
        assertEquals(BedrockPacketIds.BIOME_DEFINITION_LIST.id,
                BedrockPacketCodec.decode(bio).id());
        bio.release();
    }

    @Test
    void blockRuntimeCatalogMapsCommonMaterials() {
        BedrockBlockRuntimeIds.warm();
        assertTrue(BedrockBlockRuntimeIds.all().size() > 1000);
        assertEquals(BedrockPacketCodec.hashedAir(), BedrockBlockRuntimeIds.hashedForMaterial("AIR"));
        assertEquals(BedrockPacketCodec.hashedStone(), BedrockBlockRuntimeIds.hashedForMaterial("STONE"));
        assertEquals(BedrockPacketCodec.hashedGrass(), BedrockBlockRuntimeIds.hashedForMaterial("GRASS_BLOCK"));
        int oak = BedrockBlockRuntimeIds.hashedForMaterial("OAK_LOG");
        assertTrue(oak > 0 && oak != BedrockPacketCodec.hashedAir());
        int water = BedrockBlockRuntimeIds.hashedForMaterial("minecraft:water");
        assertTrue(water > 0 && water != BedrockPacketCodec.hashedAir());
        assertTrue(BedrockBlockRuntimeIds.jeStates().size() > 10_000);
        int oakY = BedrockBlockRuntimeIds.hashedForJeBlockData("minecraft:oak_log[axis=y]", "OAK_LOG");
        int oakX = BedrockBlockRuntimeIds.hashedForJeBlockData("minecraft:oak_log[axis=x]", "OAK_LOG");
        assertTrue(oakY != oakX);
    }

    @Test
    void paperColumnEncoderAndNbtDumps() {
        int[][] col = new int[24][4096];
        for (int s = 0; s < 24; s++) {
            java.util.Arrays.fill(col[s], BedrockPacketCodec.hashedAir());
        }
        java.util.Arrays.fill(col[8], BedrockPacketCodec.hashedStone());
        col[8][0] = BedrockPacketCodec.hashedGrass();
        ByteBuf chunk = BedrockPacketCodec.levelChunkFromColumn(0, 0, col);
        assertEquals(BedrockPacketIds.LEVEL_CHUNK.id, BedrockPacketCodec.decode(chunk).id());
        chunk.release();

        ByteBuf ent = BedrockNbtDumps.availableEntityIdentifiers();
        assertEquals(BedrockPacketIds.AVAILABLE_ACTOR_IDENTIFIERS.id,
                BedrockPacketCodec.decode(ent).id());
        assertTrue(ent.readableBytes() > 32);
        ent.release();

        ByteBuf bio = BedrockNbtDumps.biomeDefinitionList();
        assertEquals(BedrockPacketIds.BIOME_DEFINITION_LIST.id,
                BedrockPacketCodec.decode(bio).id());
        assertTrue(bio.readableBytes() > 1000);
        bio.release();
    }

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
    void itemstatesAndCreativeAndMetadataEncode() {
        assertTrue(BedrockItemStates.all().size() > 1000);

        java.util.UUID uuid = java.util.UUID.randomUUID();
        ByteBuf start = BedrockPacketCodec.startGame(1, 1, "YaPcore", 0, 64, 0, uuid);
        int startSize = start.readableBytes();
        assertEquals(BedrockPacketCodec.ID_START_GAME, BedrockPacketCodec.decode(start).id());
        assertTrue(startSize > 10_000, "itemstates inflate start_game, got " + startSize);
        start.release();

        ByteBuf creative = BedrockPacketCodec.creativeContentFull();
        int creativeSize = creative.readableBytes();
        assertEquals(BedrockPacketIds.CREATIVE_CONTENT.id, BedrockPacketCodec.decode(creative).id());
        assertTrue(creativeSize > 5_000, "creative catalog size=" + creativeSize);
        creative.release();

        ByteBuf add = BedrockPacketCodec.addPlayer(uuid, "Steve", 7, 1f, 65f, 2f, 0f, 0f);
        int addSize = add.readableBytes();
        assertEquals(BedrockPacketCodec.ID_ADD_PLAYER, BedrockPacketCodec.decode(add).id());
        assertTrue(addSize > 80, "dense metadata add_player size=" + addSize);
        add.release();

        ByteBuf chunk = BedrockPacketCodec.levelChunkFlat(0, 0);
        int chunkSize = chunk.readableBytes();
        assertEquals(BedrockPacketIds.LEVEL_CHUNK.id, BedrockPacketCodec.decode(chunk).id());
        assertTrue(chunkSize > 200, "layered chunk size=" + chunkSize);
        chunk.release();
    }

    @Test
    void inventoryAuthoritySwapAndGive() {
        BedrockInventoryAuthority inv = new BedrockInventoryAuthority();
        inv.ensure("Alex");
        inv.give("Alex", 1, 16); // stone-ish network id
        assertTrue(inv.storageNetworkIds("Alex")[0] == 1);
        var actions = java.util.List.of(
                new BedrockPacketCodec.StackAction(
                        BedrockPacketCodec.StackActionType.SWAP, 0, 1, 0, 0));
        assertTrue(inv.applyActions("Alex", actions));
        int[] slots = inv.storageNetworkIds("Alex");
        assertEquals(0, slots[0]);
        assertEquals(1, slots[1]);
        inv.clear("Alex");
        assertEquals(0, inv.storageNetworkIds("Alex")[1]);
    }

    @Test
    void craftWithoutPaperDoesNotTreatRecipeNetAsItem() {
        BedrockInventoryAuthority inv = new BedrockInventoryAuthority();
        inv.ensure("Crafty");
        // Seed craft grid with stone-ish ids but no Paper recipes attached
        inv.give("Crafty", 1, 2);
        // Manually place into craft slots via absolute indices (container 13 → CRAFT_BASE)
        var place = java.util.List.of(
                new BedrockPacketCodec.StackAction(
                        BedrockPacketCodec.StackActionType.PLACE,
                        0, BedrockInventoryAuthority.CRAFT_BASE, 1, 0),
                new BedrockPacketCodec.StackAction(
                        BedrockPacketCodec.StackActionType.PLACE,
                        0, BedrockInventoryAuthority.CRAFT_BASE + 1, 1, 0)
        );
        inv.applyActions("Crafty", place);
        // Huge recipe net-id must not become a fake item when Paper can't resolve
        boolean crafted = inv.applyActions("Crafty", java.util.List.of(
                new BedrockPacketCodec.StackAction(
                        BedrockPacketCodec.StackActionType.CRAFT_RECIPE, -1, -1, 1, 999999)));
        assertTrue(!crafted || inv.storageCounts("Crafty") != null);
        // Cursor should not hold 999999 as an item id
        int[] ids = inv.storageNetworkIds("Crafty");
        for (int id : ids) {
            assertTrue(id != 999999);
        }
    }

    @Test
    void clearItemRemovesOnlyMatchingNetworkId() {
        BedrockInventoryAuthority inv = new BedrockInventoryAuthority();
        inv.ensure("ClearMe");
        inv.give("ClearMe", 1, 8);
        inv.give("ClearMe", 2, 4);
        inv.clearItem("ClearMe", 1);
        int[] ids = inv.storageNetworkIds("ClearMe");
        int ones = 0;
        int twos = 0;
        for (int id : ids) {
            if (id == 1) {
                ones++;
            }
            if (id == 2) {
                twos++;
            }
        }
        assertEquals(0, ones);
        assertTrue(twos > 0);
    }

    @Test
    void setActorDataEncodesAndAabbVariesByType() {
        ByteBuf pig = BedrockPacketCodec.setActorData(42L, "minecraft:pig", "Pig", 10f);
        assertEquals(BedrockPacketIds.SET_ACTOR_DATA.id, BedrockPacketCodec.decode(pig).id());
        assertTrue(pig.readableBytes() > 40);
        pig.release();

        ByteBuf enderman = BedrockPacketCodec.setActorData(7L, "minecraft:enderman", "Enderman", 40f);
        assertEquals(BedrockPacketIds.SET_ACTOR_DATA.id, BedrockPacketCodec.decode(enderman).id());
        enderman.release();

        float[] chicken = BedrockPacketCodec.aabbForActor("minecraft:chicken");
        float[] player = BedrockPacketCodec.aabbForActor("minecraft:player");
        assertTrue(chicken[1] < player[1]);
    }

    @Test
    void entityTrackerPushesSetActorDataOnHealthChange() {
        java.util.concurrent.atomic.AtomicReference<java.util.List<ByteBuf>> last =
                new java.util.concurrent.atomic.AtomicReference<>();
        BedrockEntityTracker tracker = new BedrockEntityTracker();
        tracker.setBroadcast((except, pkts) -> last.set(pkts));
        tracker.addActor(9L, 9L, "minecraft:zombie", 0f, 64f, 0f, false);
        tracker.updateData(9L, 8f, "Zombie", true);
        assertNotNull(last.get());
        assertEquals(1, last.get().size());
        assertEquals(BedrockPacketIds.SET_ACTOR_DATA.id,
                BedrockPacketCodec.decode(last.get().get(0)).id());
        last.get().get(0).release();
    }

    @Test
    void playerEnchantOptionsEmptyEncodes() {
        ByteBuf empty = BedrockPacketCodec.playerEnchantOptions(java.util.List.of());
        assertEquals(BedrockPacketIds.PLAYER_ENCHANT_OPTIONS.id,
                BedrockPacketCodec.decode(empty).id());
        empty.release();
    }

    @Test
    void containerOpenWindowStoresTraderRuntime() {
        BedrockContainerBridge bridge = new BedrockContainerBridge();
        bridge.setSender((u, b) -> b.release());
        var w = bridge.openVillager("Trader", 12345L, "Farmer");
        assertEquals(12345L, w.entityRuntimeId());
        assertEquals(BedrockContainerBridge.TYPE_VILLAGER, w.type());
        bridge.close("Trader", true);
    }
}
