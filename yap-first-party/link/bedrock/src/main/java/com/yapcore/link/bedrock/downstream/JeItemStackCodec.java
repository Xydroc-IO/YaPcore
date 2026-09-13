package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;

/**
 * Best-effort JE 26.2 {@code Slot} reader: item id + count, skip data components.
 *
 * <p>Full component codecs are huge; we skip common types precisely and fail the slot
 * (leaving reader at a safe point via mark) only when an unknown payload cannot be skipped.
 */
public final class JeItemStackCodec {

    public record Stack(int itemId, int count, int damage) {
        public static final Stack AIR = new Stack(0, 0, 0);

        public boolean isEmpty() {
            return itemId <= 0 || count <= 0;
        }
    }

    private JeItemStackCodec() {
    }

    public static Stack readSlot(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return Stack.AIR;
        }
        int count = McCodec.readVarInt(buf);
        if (count <= 0) {
            return Stack.AIR;
        }
        int itemId = McCodec.readVarInt(buf);
        int added = McCodec.readVarInt(buf);
        int removed = McCodec.readVarInt(buf);
        int damage = 0;
        for (int i = 0; i < added && i < 256 && buf.isReadable(); i++) {
            int type = McCodec.readVarInt(buf);
            if (type == 3) { // damage
                damage = McCodec.readVarInt(buf);
            } else {
                skipComponentData(buf, type);
            }
        }
        for (int i = 0; i < removed && i < 256 && buf.isReadable(); i++) {
            McCodec.readVarInt(buf); // SlotComponentType only
        }
        return new Stack(itemId, count, damage);
    }

    /**
     * Skip one component payload after its type id was consumed.
     * Unknown types: try anonymous NBT then varint — may throw.
     */
    private static void skipComponentData(ByteBuf buf, int type) {
        switch (type) {
            case 0, 6, 9 -> skipAnonymousNbt(buf); // custom_data / custom_name / item_name
            case 1, 2, 3, 12, 19, 31, 46, 52, 63 -> McCodec.readVarInt(buf);
            case 4, 20, 22, 34 -> { /* void */ }
            case 5 -> { // use_effects
                buf.readBoolean();
                buf.readBoolean();
                buf.readFloat();
            }
            case 7 -> buf.readFloat();
            case 10, 35, 71 -> McCodec.readString(buf, 32767);
            case 11 -> { // lore: array of anonymousNbt
                int n = McCodec.readVarInt(buf);
                for (int i = 0; i < n && i < 256; i++) {
                    skipAnonymousNbt(buf);
                }
            }
            case 13, 42 -> { // enchantments / stored_enchantments
                int n = McCodec.readVarInt(buf);
                for (int i = 0; i < n && i < 256; i++) {
                    McCodec.readVarInt(buf);
                    McCodec.readVarInt(buf);
                }
            }
            case 17 -> { // custom_model_data — floats + flags + strings (best-effort 1.21+)
                skipCustomModelData(buf);
            }
            case 18 -> { // tooltip_display
                buf.readBoolean();
                int n = McCodec.readVarInt(buf);
                for (int i = 0; i < n && i < 256; i++) {
                    McCodec.readVarInt(buf);
                }
            }
            case 21 -> buf.readBoolean(); // enchantment_glint_override
            case 23 -> { // food
                buf.readFloat();
                buf.readBoolean();
            }
            case 43, 44, 45, 84, 89, 90, 107, 108, 109 -> McCodec.readVarInt(buf); // colors / variants
            case 58, 59, 60 -> skipAnonymousNbt(buf); // entity/block entity data
            default -> {
                // Variants and simple registry holders are usually a single varint.
                if (type >= 81 && type <= 109) {
                    McCodec.readVarInt(buf);
                    return;
                }
                // Last resort: anonymous NBT (custom_data-shaped) then give up.
                int marked = buf.readerIndex();
                try {
                    skipAnonymousNbt(buf);
                } catch (Exception e) {
                    buf.readerIndex(marked);
                    McCodec.readVarInt(buf);
                }
            }
        }
    }

    private static void skipCustomModelData(ByteBuf buf) {
        int floats = McCodec.readVarInt(buf);
        for (int i = 0; i < floats && i < 64; i++) {
            buf.readFloat();
        }
        int flags = McCodec.readVarInt(buf);
        for (int i = 0; i < flags && i < 64; i++) {
            buf.readBoolean();
        }
        int strings = McCodec.readVarInt(buf);
        for (int i = 0; i < strings && i < 64; i++) {
            McCodec.readString(buf, 32767);
        }
        int colors = McCodec.readVarInt(buf);
        for (int i = 0; i < colors && i < 64; i++) {
            buf.readInt();
        }
    }

    private static void skipAnonymousNbt(ByteBuf buf) {
        if (!buf.isReadable()) {
            return;
        }
        byte type = buf.readByte();
        skipNbtValue(buf, type);
    }

    private static void skipNbtValue(ByteBuf buf, byte type) {
        switch (type) {
            case 0 -> { /* end */ }
            case 1 -> buf.readByte();
            case 2 -> buf.readShort();
            case 3 -> buf.readInt();
            case 4 -> buf.readLong();
            case 5 -> buf.readFloat();
            case 6 -> buf.readDouble();
            case 7 -> {
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes())));
            }
            case 8 -> {
                int utflen = buf.readUnsignedShort();
                buf.skipBytes(Math.max(0, Math.min(utflen, buf.readableBytes())));
            }
            case 9 -> {
                byte elem = buf.readByte();
                int count = buf.readInt();
                for (int i = 0; i < count && i < 4096 && buf.isReadable(); i++) {
                    skipNbtValue(buf, elem);
                }
            }
            case 10 -> {
                while (buf.isReadable()) {
                    byte ft = buf.readByte();
                    if (ft == 0) {
                        break;
                    }
                    int nameLen = buf.readUnsignedShort();
                    buf.skipBytes(Math.max(0, Math.min(nameLen, buf.readableBytes())));
                    skipNbtValue(buf, ft);
                }
            }
            case 11 -> {
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes() / 4)) * 4);
            }
            case 12 -> {
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes() / 8)) * 8);
            }
            default -> {
                // unknown
            }
        }
    }
}
