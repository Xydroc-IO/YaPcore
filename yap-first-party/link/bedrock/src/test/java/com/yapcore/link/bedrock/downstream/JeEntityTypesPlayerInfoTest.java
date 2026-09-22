package com.yapcore.link.bedrock.downstream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeEntityTypesPlayerInfoTest {

    @Test
    void foliaSulfurCubeShiftsVillagerAndPlayerIds() {
        assertEquals("minecraft:villager", JeEntityTypes.bedrockIdentifier(140));
        assertEquals("minecraft:player", JeEntityTypes.bedrockIdentifier(156));
        assertEquals("minecraft:vindicator", JeEntityTypes.bedrockIdentifier(141));
        assertEquals("minecraft:zombie_piglin", JeEntityTypes.bedrockIdentifier(155));
        assertEquals("minecraft:npc", JeEntityTypes.bedrockIdentifier(83));
        assertEquals("minecraft:magma_cube", JeEntityTypes.bedrockIdentifier(130)); // sulfur_cube
    }

    @Test
    void fixedBitSetMaskMatchesEnumSetByte() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0xFF);
        assertEquals(0xFF, JavaDownstreamNbt.readFixedBitSetAsMask(buf, 8));
        // VarInt would have continued past 0xFF — that was the player_info bug.
        buf.writeByte(0x01);
        buf.writeByte(0x00);
        assertEquals(0x01, JavaDownstreamNbt.readFixedBitSetAsMask(buf, 8));
    }
}
