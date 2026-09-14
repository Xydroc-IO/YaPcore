package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;

/**
 * JE play C2S helpers for inventory clicks and plugin channels (Floodgate forms).
 */
public final class JavaPlayInventoryWire {

    private JavaPlayInventoryWire() {
    }

    /**
     * Proto 776 {@code container_click}: windowId varint, stateId varint, slot short,
     * button byte, mode varint, changedSlots (empty), carried HashedSlot (empty).
     */
    public static ByteBuf containerClick(int windowId, int stateId, int slot, int button, int mode) {
        ByteBuf buf = Unpooled.buffer(32);
        McCodec.writeVarInt(buf, JavaPlayWire.SB_CONTAINER_CLICK);
        McCodec.writeVarInt(buf, Math.max(0, windowId));
        McCodec.writeVarInt(buf, Math.max(0, stateId));
        buf.writeShort(slot);
        buf.writeByte(button & 0xff);
        McCodec.writeVarInt(buf, Math.max(0, mode));
        McCodec.writeVarInt(buf, 0); // no changed slots — Folia resyncs via set_slot
        buf.writeBoolean(false); // carried empty HashedSlot
        return buf;
    }

    public static ByteBuf containerClose(int windowId) {
        ByteBuf buf = Unpooled.buffer(8);
        McCodec.writeVarInt(buf, JavaPlayWire.SB_CONTAINER_CLOSE);
        McCodec.writeVarInt(buf, Math.max(0, windowId));
        return buf;
    }

    /** Proto 776 {@code custom_payload}: Identifier + remainder bytes. */
    public static ByteBuf customPayload(String channel, byte[] data) {
        String id = channel == null ? "" : channel;
        byte[] payload = data != null ? data : new byte[0];
        ByteBuf buf = Unpooled.buffer(16 + id.length() + payload.length);
        McCodec.writeVarInt(buf, JavaPlayWire.SB_CUSTOM_PAYLOAD);
        McCodec.writeString(buf, id);
        buf.writeBytes(payload);
        return buf;
    }

    public static byte[] floodgateFormResponse(short formId, String jsonOrNull) {
        if (jsonOrNull == null || "null".equals(jsonOrNull) || jsonOrNull.isEmpty()) {
            return new byte[]{(byte) ((formId >> 8) & 0xff), (byte) (formId & 0xff)};
        }
        byte[] json = jsonOrNull.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + json.length];
        out[0] = (byte) ((formId >> 8) & 0xff);
        out[1] = (byte) (formId & 0xff);
        System.arraycopy(json, 0, out, 2, json.length);
        return out;
    }
}
