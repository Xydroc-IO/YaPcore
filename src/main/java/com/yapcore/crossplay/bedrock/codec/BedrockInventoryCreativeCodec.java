package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * Creative-content packet builders (split from {@link BedrockInventoryCodec} for ≤500-line gate).
 */
final class BedrockInventoryCreativeCodec {
    private BedrockInventoryCreativeCodec() {}

    static ByteBuf creativeContentEmpty() {
        // 1.21.60+: Groups[] then Items[] (two slices)
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.CREATIVE_CONTENT.id);
        writeUnsignedVarInt(out, 0); // groups
        writeUnsignedVarInt(out, 0); // items
        return out;
    }

    /**
     * Full creative catalog from vanilla itemstates (ItemLegacy entries).
     * Skips air; shield gets blocking_tick=0 extra.
     */
    static ByteBuf creativeContentFull() {
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
            BedrockInventoryCodec.writeItemLegacy(out, s.runtimeId() & 0xFFFF);
        }
        return out;
    }
}
