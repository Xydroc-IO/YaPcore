package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockAvailableCommands;
import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import com.yapcore.crossplay.bedrock.BedrockPaperRecipes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

public final class BedrockInventoryCodec {
    private BedrockInventoryCodec() {}
    public static ByteBuf containerOpen(int windowId, int windowType, int x, int y, int z, long entityRuntimeId) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_CONTAINER_OPEN);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, windowType);
        writeBlockPosition(out, x, y, z);
        writeSignedVarLong(out, entityRuntimeId);
        return out;
    }

    /**
     * PLAYER_ENCHANT_OPTIONS — list of enchant choices for the open table.
     */
    public static ByteBuf playerEnchantOptions(java.util.List<BedrockPaperRecipes.EnchantOption> options) {
        ByteBuf out = Unpooled.buffer(64);
        writeUnsignedVarInt(out, BedrockPacketIds.PLAYER_ENCHANT_OPTIONS.id);
        int n = options == null ? 0 : Math.min(options.size(), 3);
        writeUnsignedVarInt(out, n);
        for (int i = 0; i < n; i++) {
            BedrockPaperRecipes.EnchantOption o = options.get(i);
            writeSignedVarInt(out, o.cost()); // min cost / level
            out.writeIntLE(0); // primary slot
            // enchants0 — primary list
            writeUnsignedVarInt(out, 1);
            out.writeByte(o.enchantType() & 0xFF);
            out.writeByte(Math.max(1, o.enchantLevel()) & 0xFF);
            writeUnsignedVarInt(out, 0); // enchants1
            writeUnsignedVarInt(out, 0); // enchants2
            writeString(out, o.primaryName() == null ? "enchant" : o.primaryName());
            writeUnsignedVarInt(out, o.netId());
        }
        return out;
    }

    public static ByteBuf containerClose(int windowId, boolean serverInitiated) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_CONTAINER_CLOSE);
        out.writeByte(windowId & 0xFF);
        out.writeBoolean(serverInitiated);
        return out;
    }

    /**
     * CONTAINER_SET_DATA — property/value pairs for furnace progress / enchant costs.
     * Enchant table typically uses property 0..2 for option costs.
     */
    public static ByteBuf containerSetData(int windowId, int property, int value) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, BedrockPacketIds.CONTAINER_SET_DATA.id);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, property);
        writeSignedVarInt(out, value);
        return out;
    }

    /**
     * UPDATE_TRADE — shallow villager offers for 1.21.50-style clients.
     * Each offer: buyA + optional buyB + sell as item legacy stubs (network id + count).
     */
    public static ByteBuf updateTrade(int windowId, int windowType, int size, int tradeTier,
                                      boolean recipeAdded, boolean isEconomy, long traderEntityId,
                                      long playerEntityId, String displayName,
                                      java.util.List<int[]> offers) {
        ByteBuf out = Unpooled.buffer(256);
        writeUnsignedVarInt(out, BedrockPacketIds.UPDATE_TRADE.id);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, windowType);
        writeSignedVarInt(out, size);
        writeSignedVarInt(out, tradeTier);
        out.writeBoolean(recipeAdded);
        out.writeBoolean(isEconomy);
        writeSignedVarLong(out, traderEntityId);
        writeSignedVarLong(out, playerEntityId);
        writeString(out, displayName == null ? "Villager" : displayName);
        // Remaining demand / recipe nbt — send empty compound list via offer count
        int n = offers == null ? 0 : Math.min(offers.size(), 64);
        writeUnsignedVarInt(out, n);
        for (int i = 0; i < n; i++) {
            int[] o = offers.get(i);
            // buyA
            writeItemLegacyTrade(out, o.length > 0 ? o[0] : 0, o.length > 1 ? o[1] : 0);
            // sell
            writeItemLegacyTrade(out, o.length > 4 ? o[4] : 0, o.length > 5 ? o[5] : 0);
            out.writeBoolean(o.length > 2 && o[2] > 0); // has buyB
            if (o.length > 2 && o[2] > 0) {
                writeItemLegacyTrade(out, o[2], o.length > 3 ? o[3] : 1);
            }
            out.writeBoolean(true); // enabled
            writeSignedVarInt(out, -1); // uses
            writeSignedVarInt(out, Integer.MAX_VALUE); // max uses
            writeSignedVarInt(out, 0); // trader exp
            writeSignedVarInt(out, 0); // reward exp
            writeSignedVarInt(out, 0); // price multiplier
            writeSignedVarInt(out, 0); // demand
        }
        return out;
    }

    private static void writeItemLegacyTrade(ByteBuf out, int networkId, int count) {
        writeSignedVarInt(out, networkId);
        if (networkId != 0) {
            out.writeShortLE(Math.max(1, count) & 0xFFFF);
            writeUnsignedVarInt(out, 0); // metadata
            writeSignedVarInt(out, 0); // block_runtime
            writeUnsignedVarInt(out, 0); // user data / nbt empty
        }
    }

    public static BedrockPacketCodec.ContainerCloseDecode tryDecodeContainerClose(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int windowId = body.readUnsignedByte();
            boolean server = body.isReadable() && body.readBoolean();
            return new BedrockPacketCodec.ContainerCloseDecode(windowId, server);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
    public static ByteBuf creativeContentEmpty() {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.CREATIVE_CONTENT.id);
        writeUnsignedVarInt(out, 0);
        return out;
    }

    /**
     * Full creative catalog from vanilla itemstates (ItemLegacy entries).
     * Skips air; shield gets blocking_tick=0 extra.
     */
    public static ByteBuf creativeContentFull() {
        List<BedrockItemStates.ItemState> states = BedrockItemStates.all();
        ByteBuf out = Unpooled.buffer(Math.max(64, states.size() * 24));
        writeUnsignedVarInt(out, BedrockPacketIds.CREATIVE_CONTENT.id);
        int count = 0;
        for (BedrockItemStates.ItemState s : states) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            count++;
        }
        writeUnsignedVarInt(out, count);
        int entryId = 1;
        for (BedrockItemStates.ItemState s : states) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            writeUnsignedVarInt(out, entryId++);
            writeItemLegacy(out, s.runtimeId() & 0xFFFF);
        }
        return out;
    }

    /** ItemLegacy for creative / inventory (network_id != 0). */
    private static final int SHIELD_NETWORK_ID = 1162;

    static void writeItemLegacy(ByteBuf out, int networkId) {
        writeItemLegacy(out, networkId, 1);
    }

    static void writeItemLegacy(ByteBuf out, int networkId, int count) {
        writeItemLegacy(out, networkId, count, null);
    }

    /**
     * ItemLegacy with optional SkullOwner name NBT (G.33 item-in-hand heads).
     * Other NBT (full profile hash, enchant lists) is Stretch / still Limited.
     */
    static void writeItemLegacy(ByteBuf out, int networkId, int count, String skullOwner) {
        writeSignedVarInt(out, networkId);
        out.writeShortLE(Math.max(1, Math.min(64, count)));
        writeUnsignedVarInt(out, 0); // metadata
        writeSignedVarInt(out, 0); // block_runtime_id
        ByteBuf extra = Unpooled.buffer(64);
        if (skullOwner != null && !skullOwner.isBlank()) {
            extra.writeShortLE(0xFFFF); // has network NBT
            // Minimal compound: SkullOwner: { Name: "..." }
            writeBedrockString(extra, ""); // root name unused in network item NBT
            writeBedrockString(extra, "SkullOwner");
            extra.writeByte(10); // TAG_Compound
            writeBedrockString(extra, "Name");
            extra.writeByte(8); // TAG_String
            writeBedrockString(extra, skullOwner.trim());
            extra.writeByte(0); // TAG_End (SkullOwner)
            extra.writeByte(0); // TAG_End (root)
        } else {
            extra.writeShortLE(0); // has_nbt false
        }
        extra.writeIntLE(0); // can_place_on
        extra.writeIntLE(0); // can_destroy
        if (networkId == SHIELD_NETWORK_ID) {
            extra.writeLongLE(0L);
        }
        writeUnsignedVarInt(out, extra.readableBytes());
        out.writeBytes(extra);
        extra.release();
    }

    private static void writeBedrockString(ByteBuf out, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeUnsignedVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    public static ByteBuf inventoryContentEmpty(int windowId, int size) {
        int[] air = new int[Math.max(0, size)];
        return inventoryContent(windowId, air);
    }

    /**
     * Inventory content with ItemLegacy slots (0 = air). Used for Paper inventory authority push.
     */
    public static ByteBuf inventoryContent(int windowId, int[] networkIds) {
        return inventoryContent(windowId, networkIds, null);
    }

    /**
     * @param counts optional parallel stack sizes (clamped 1–64); null → count 1
     */
    public static ByteBuf inventoryContent(int windowId, int[] networkIds, int[] counts) {
        return inventoryContent(windowId, networkIds, counts, null);
    }

    /**
     * @param skullOwners optional parallel SkullOwner names for player heads (null elsewhere)
     */
    public static ByteBuf inventoryContent(int windowId, int[] networkIds, int[] counts, String[] skullOwners) {
        ByteBuf out = Unpooled.buffer(32 + networkIds.length * 16);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_INVENTORY_CONTENT);
        writeUnsignedVarInt(out, windowId);
        writeUnsignedVarInt(out, networkIds.length);
        for (int i = 0; i < networkIds.length; i++) {
            int networkId = networkIds[i];
            if (networkId == 0) {
                writeSignedVarInt(out, 0);
            } else {
                int c = 1;
                if (counts != null && i < counts.length && counts[i] > 0) {
                    c = counts[i];
                }
                String owner = skullOwners != null && i < skullOwners.length ? skullOwners[i] : null;
                writeItemLegacy(out, networkId, c, owner);
            }
        }
        out.writeByte(29); // inventory container
        out.writeByte(0);
        writeSignedVarInt(out, 0); // storage_item air
        return out;
    }

    /**
     * ITEM_STACK_RESPONSE — acknowledge request id with empty OK container.
     * Layout: responses count, each: result (byte), requestId (varint), containers…
     */
    public static ByteBuf itemStackResponseOk(int requestId) {
        ByteBuf out = Unpooled.buffer(24);
        writeUnsignedVarInt(out, BedrockPacketIds.ITEM_STACK_RESPONSE.id);
        writeUnsignedVarInt(out, 1); // responses
        out.writeByte(0); // OK
        writeUnsignedVarInt(out, requestId);
        writeUnsignedVarInt(out, 0); // container infos
        return out;
    }

    public static BedrockPacketCodec.ItemStackRequestDecode tryDecodeItemStackRequest(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            java.util.List<BedrockPacketCodec.StackAction> actions = new java.util.ArrayList<>();
            int requestId;
            int saved = body.readerIndex();
            int first = readUnsignedVarInt(body);
            // Protocol: requests[] count. If count is 1 and next looks like request_id, use array form.
            // Legacy test/path: first value is zigzag request_id directly.
            if (first == 1 && body.isReadable()) {
                int beforeReq = body.readerIndex();
                try {
                    requestId = readSignedVarInt(body);
                    int actionCount = readUnsignedVarInt(body);
                    parseStackActions(body, actionCount, actions);
                    return new BedrockPacketCodec.ItemStackRequestDecode(requestId, actions.size(), List.copyOf(actions));
                } catch (Exception e) {
                    body.readerIndex(beforeReq);
                }
            }
            // Legacy: rewind and treat first as request_id zigzag
            body.readerIndex(saved);
            requestId = readSignedVarInt(body);
            int actionCount = readUnsignedVarInt(body);
            parseStackActions(body, actionCount, actions);
            return new BedrockPacketCodec.ItemStackRequestDecode(requestId, Math.max(actionCount, actions.size()), List.copyOf(actions));
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    private static void parseStackActions(ByteBuf body, int actionCount,
                                          java.util.List<BedrockPacketCodec.StackAction> out) {
        int n = Math.min(Math.max(0, actionCount), 64);
        for (int i = 0; i < n; i++) {
            if (body.readableBytes() < 1) {
                return;
            }
            int typeId = body.readUnsignedByte();
            try {
                switch (typeId) {
                    case 0, 1 -> { // take / place
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        int dst = readSlotInfoMapped(body);
                        out.add(new BedrockPacketCodec.StackAction(typeId == 0 ? BedrockPacketCodec.StackActionType.TAKE : BedrockPacketCodec.StackActionType.PLACE,
                                src, dst, count, 0));
                    }
                    case 2 -> { // swap
                        int src = readSlotInfoMapped(body);
                        int dst = readSlotInfoMapped(body);
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.SWAP, src, dst, 0, 0));
                    }
                    case 3 -> { // drop
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        body.readBoolean(); // randomly
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.DROP, src, -1, count, 0));
                    }
                    case 4 -> { // destroy
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.DESTROY, src, -1, count, 0));
                    }
                    case 5 -> { // consume
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.CONSUME, src, -1, count, 0));
                    }
                    case 6 -> { // create — result lands on cursor
                        int results = body.readUnsignedByte();
                        int networkId = results > 0 ? creativeEntryToNetworkId(results) : 0;
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.CREATE, -1, -1, 1, networkId));
                    }
                    case 10, 11 -> { // craft_recipe / craft_recipe_auto
                        int recipeNetId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? Math.max(1, body.readUnsignedByte()) : 1;
                        out.add(new BedrockPacketCodec.StackAction(
                                typeId == 10 ? BedrockPacketCodec.StackActionType.CRAFT_RECIPE : BedrockPacketCodec.StackActionType.CRAFT_RECIPE_AUTO,
                                -1, -1, times, recipeNetId));
                    }
                    case 13 -> { // craft_recipe_optional (enchant option / filter trade)
                        int recipeNetId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? Math.max(1, body.readUnsignedByte()) : 1;
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.CRAFT_RECIPE_OPTIONAL,
                                -1, -1, times, recipeNetId));
                    }
                    case 12, 14 -> { // craft_creative (12 modern / 14 legacy)
                        int itemId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? body.readUnsignedByte() : 1;
                        int networkId = creativeEntryToNetworkId(itemId);
                        out.add(new BedrockPacketCodec.StackAction(BedrockPacketCodec.StackActionType.CRAFT_CREATIVE, -1, -1,
                                Math.max(1, times), networkId));
                    }
                    default -> {
                        return;
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    /** FullContainerName + slot u8 + stack_id zigzag32 → packed (containerId<<16)|slot. */
    private static int readSlotInfoMapped(ByteBuf body) {
        int containerId = body.readUnsignedByte();
        // option&lt;u32&gt; dynamic_container_id
        if (body.readBoolean()) {
            body.readIntLE();
        }
        int slot = body.readUnsignedByte();
        readSignedVarInt(body); // stack_id
        return (containerId << 16) | (slot & 0xffff);
    }

    private static int creativeEntryToNetworkId(int entryId) {
        // creative_content entry_ids are 1-based over non-air itemstates
        int idx = 0;
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            idx++;
            if (idx == entryId) {
                return s.runtimeId() & 0xFFFF;
            }
        }
        return entryId; // fallback: treat as network id
    }




    public static BedrockPacketCodec.MobEquipmentDecode tryDecodeMobEquipment(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long runtimeId = readUnsignedVarLong(body);
            // ItemLegacy — may be air
            int networkId = readSignedVarInt(body);
            if (networkId != 0) {
                body.readUnsignedShortLE(); // count
                readUnsignedVarInt(body); // metadata
                readSignedVarInt(body); // block_runtime_id
                int extraLen = readUnsignedVarInt(body);
                if (extraLen > 0 && body.readableBytes() >= extraLen) {
                    body.skipBytes(extraLen);
                }
            }
            int inventorySlot = body.readUnsignedByte();
            int hotbarSlot = body.readUnsignedByte();
            int windowId = body.readUnsignedByte();
            return new BedrockPacketCodec.MobEquipmentDecode(runtimeId, networkId, inventorySlot, hotbarSlot, windowId);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

}
