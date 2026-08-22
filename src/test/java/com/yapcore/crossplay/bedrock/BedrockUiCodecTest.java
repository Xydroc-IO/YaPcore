package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.codec.BedrockUiCodec;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockUiCodecTest {

    @Test
    void setTitleAndBossEventEncode() {
        ByteBuf title = BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_SET, "Hello", 10, 70, 20);
        assertEquals(BedrockPacketIds.SET_TITLE.id, BedrockPacketCodec.decode(title).id());
        title.release();

        ByteBuf bar = BedrockPacketCodec.bossEventShow(42L, "Boss", 0.5f, 2);
        assertEquals(BedrockPacketIds.BOSS_EVENT.id, BedrockPacketCodec.decode(bar).id());
        bar.release();

        ByteBuf score = BedrockPacketCodec.setScore(0, 1L, "obj", 5,
                BedrockUiCodec.SCORE_TYPE_FAKE, 0L, "Steve");
        assertEquals(BedrockPacketIds.SET_SCORE.id, BedrockPacketCodec.decode(score).id());
        score.release();
    }

    @Test
    void resourcePackOfferEncodesEntry() {
        UUID id = UUID.randomUUID();
        ByteBuf info = BedrockPacketCodec.resourcePacksInfoOffer(id, "1.0.0", 1024L,
                "http://127.0.0.1:8080/pack.zip", true);
        assertEquals(BedrockPacketCodec.ID_RESOURCE_PACKS_INFO, BedrockPacketCodec.decode(info).id());
        assertTrue(info.readableBytes() > 48);
        info.release();

        ByteBuf stack = BedrockPacketCodec.resourcePackStackOffer(id, "1.0.0", true);
        assertEquals(BedrockPacketCodec.ID_RESOURCE_PACK_STACK, BedrockPacketCodec.decode(stack).id());
        stack.release();
    }

    @Test
    void skullBlockActorEncodes() {
        ByteBuf skull = BedrockPacketCodec.blockActorSkull(1, 64, 2, "Notch");
        assertEquals(BedrockPacketIds.BLOCK_ACTOR_DATA.id, BedrockPacketCodec.decode(skull).id());
        skull.release();
    }
}
