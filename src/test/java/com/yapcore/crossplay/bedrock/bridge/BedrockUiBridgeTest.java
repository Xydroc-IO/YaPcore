package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockUiBridgeTest {

    @Test
    void titleCommandMirrorsToSetTitlePacket() {
        BedrockBridgeContext ctx = new BedrockBridgeContext(
                new BedrockSessionManager(), new FloodgateAuth(), new SkinService(), new FormService());
        List<ByteBuf> captured = new ArrayList<>();
        ctx.outbound = (guid, packets) -> captured.addAll(packets);
        BedrockUiBridge ui = new BedrockUiBridge(ctx);
        ui.applyCommandUiHints(1L, "Alex", "/title @s title \"Phase 21\"");
        assertTrue(captured.stream().anyMatch(p -> {
            int id = p.getUnsignedByte(p.readerIndex());
            return id == BedrockPacketIds.SET_TITLE.id || peekPacketId(p) == BedrockPacketIds.SET_TITLE.id;
        }));
        captured.forEach(ByteBuf::release);
    }

    private static int peekPacketId(ByteBuf p) {
        int mark = p.readerIndex();
        try {
            return com.yapcore.crossplay.bedrock.BedrockPacketCodec.readUnsignedVarInt(p.duplicate());
        } finally {
            p.readerIndex(mark);
        }
    }
}
