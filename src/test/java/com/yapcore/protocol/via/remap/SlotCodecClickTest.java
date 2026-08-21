package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.id.PacketIdDump;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.1 dump wiring + P4.2 window_click / creative body remaps. */
class SlotCodecClickTest {

    @Test
    void dumpsWiredForMidFloorIncluding1205() {
        for (int proto : new int[]{764, 765, 766, 767, 769, 771, 773, 774, 775, 776}) {
            PacketIdDump dump = PacketIdDump.forProtocol(proto);
            assertTrue(dump.hasPlay(), "dump missing for " + proto);
            assertTrue(dump.playC2sId("window_click") >= 0
                            || dump.playC2sId("container_click") >= 0,
                    "click C2S missing @" + proto);
            assertTrue(dump.playC2sId("set_creative_slot") >= 0
                            || dump.playC2sId("set_creative_mode_slot") >= 0,
                    "creative C2S missing @" + proto);
        }
    }

    @Test
    void protocol766Uses1205Dump() {
        PacketIdDump d = PacketIdDump.forProtocol(766);
        assertTrue(d.hasPlay());
        // 1.20.5 dump protocol field
        assertEquals(766, d.protocol());
    }

    @Test
    void remapWindowClickNbtSameEra() {
        ItemRemapper items = new ItemRemapper(ProtocolBand.V1_20_2, ProtocolBand.V26_2);
        SlotCodec slots = new SlotCodec(ProtocolBand.V1_20_2, ProtocolBand.V26_2, items, false);

        ByteBuf body = Unpooled.buffer();
        body.writeByte(0); // window id u8
        McCodec.writeVarInt(body, 1); // state
        body.writeShort(10); // slot
        body.writeByte(0); // button
        McCodec.writeVarInt(body, 0); // mode PICKUP
        McCodec.writeVarInt(body, 0); // changed count
        body.writeBoolean(false); // carried empty (NBT era)

        int outId = 18; // container_click on 776
        ByteBuf out = slots.remapWindowClick(body, outId);
        assertNotNull(out);
        assertEquals(outId, McCodec.readVarInt(out));
        // 776 window id is varint
        assertEquals(0, McCodec.readVarInt(out));
        assertEquals(1, McCodec.readVarInt(out));
        assertEquals(10, out.readShort());
        assertEquals(0, out.readByte());
        assertEquals(0, McCodec.readVarInt(out));
        assertEquals(0, McCodec.readVarInt(out));
        // carried → empty component stack (count 0)
        assertEquals(0, McCodec.readVarInt(out));
        out.release();
        body.release();
    }

    @Test
    void remapCreativeSlotComponentsSameEra() {
        ItemRemapper items = new ItemRemapper(ProtocolBand.V1_21, ProtocolBand.V26_2);
        SlotCodec slots = new SlotCodec(ProtocolBand.V1_21, ProtocolBand.V26_2, items, false);

        ByteBuf body = Unpooled.buffer();
        body.writeShort(36); // slot
        McCodec.writeVarInt(body, 0); // empty item count

        ByteBuf out = slots.remapCreativeSlot(body, 56);
        assertNotNull(out);
        assertEquals(56, McCodec.readVarInt(out));
        assertEquals(36, out.readShort());
        assertEquals(0, McCodec.readVarInt(out));
        out.release();
        body.release();
    }
}
