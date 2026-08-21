package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * JE item slot / inventory packet body remaps for mid bands.
 * <ul>
 *   <li>Same-era components (766+): remap item id; preserve component bytes when skippable</li>
 *   <li>NBT era (≤765): remap id; skip optional NBT</li>
 *   <li>Cross-era: translate empty↔plain stacks (components/NBT stripped)</li>
 *   <li>C2S clicks: NBT slots or HashedSlot (770+)</li>
 * </ul>
 */
public final class SlotCodec {

    private final ProtocolBand sourceBand;
    private final ProtocolBand targetBand;
    private final ItemRemapper items;
    private final boolean towardClient;
    private final int sourceProtocol;
    private final int targetProtocol;

    public SlotCodec(ProtocolBand sourceBand, ProtocolBand targetBand, ItemRemapper items, boolean towardClient) {
        this(sourceBand, targetBand, items, towardClient, sourceBand.minProtocol(), targetBand.minProtocol());
    }

    public SlotCodec(ProtocolBand sourceBand, ProtocolBand targetBand, ItemRemapper items,
                     boolean towardClient, int sourceProtocol, int targetProtocol) {
        this.sourceBand = sourceBand;
        this.targetBand = targetBand;
        this.items = items;
        this.towardClient = towardClient;
        this.sourceProtocol = sourceProtocol;
        this.targetProtocol = targetProtocol;
    }

    public static boolean usesComponents(ProtocolBand band) {
        return band.ordinal() >= ProtocolBand.V1_21.ordinal();
    }

    /** HashedSlot on window_click from protocol 770 (1.21.5)+. */
    public static boolean usesHashedClickSlots(int protocol) {
        return protocol >= 770;
    }

    public static boolean windowIdVarInt(ProtocolBand band) {
        return band.ordinal() >= ProtocolBand.V1_21_11.ordinal();
    }

    public int remapItemId(int id) {
        return towardClient ? items.remapToClient(id) : items.remapToServer(id);
    }

    public void readWindowId(ByteBuf in, ByteBuf out) {
        if (windowIdVarInt(sourceBand)) {
            int id = McCodec.readVarInt(in);
            if (windowIdVarInt(targetBand)) {
                McCodec.writeVarInt(out, id);
            } else {
                out.writeByte(id & 0xFF);
            }
        } else {
            int id = in.readUnsignedByte();
            if (windowIdVarInt(targetBand)) {
                McCodec.writeVarInt(out, id);
            } else {
                out.writeByte(id);
            }
        }
    }

    public void remapSlot(ByteBuf in, ByteBuf out) {
        boolean srcComp = usesComponents(sourceBand);
        boolean dstComp = usesComponents(targetBand);
        if (srcComp && dstComp) {
            remapComponentSlotSameEra(in, out);
        } else if (!srcComp && !dstComp) {
            remapNbtSlotSameEra(in, out);
        } else if (srcComp) {
            componentToNbt(in, out);
        } else {
            nbtToComponent(in, out);
        }
    }

    public void remapClickItem(ByteBuf in, ByteBuf out) {
        boolean srcH = usesHashedClickSlots(sourceProtocol);
        boolean dstH = usesHashedClickSlots(targetProtocol);
        if (srcH && dstH) {
            remapOptionalHashedSlot(in, out);
            return;
        }
        if (!srcH && !dstH) {
            remapSlot(in, out);
            return;
        }
        if (srcH) {
            hashedToLegacySlot(in, out);
            return;
        }
        legacyToHashedSlot(in, out);
    }

    private void remapOptionalHashedSlot(ByteBuf in, ByteBuf out) {
        boolean present = in.readBoolean();
        out.writeBoolean(present);
        if (!present) {
            return;
        }
        int itemId = McCodec.readVarInt(in);
        int count = McCodec.readVarInt(in);
        McCodec.writeVarInt(out, remapItemId(itemId));
        McCodec.writeVarInt(out, count);
        int nAdd = McCodec.readVarInt(in);
        McCodec.writeVarInt(out, nAdd);
        for (int i = 0; i < nAdd; i++) {
            McCodec.writeVarInt(out, McCodec.readVarInt(in));
            out.writeInt(in.readInt());
        }
        int nRem = McCodec.readVarInt(in);
        McCodec.writeVarInt(out, nRem);
        for (int i = 0; i < nRem; i++) {
            McCodec.writeVarInt(out, McCodec.readVarInt(in));
        }
    }

    private void hashedToLegacySlot(ByteBuf in, ByteBuf out) {
        boolean present = in.readBoolean();
        if (!present) {
            if (usesComponents(targetBand)) {
                writeItemCount(out, 0);
            } else {
                out.writeBoolean(false);
            }
            return;
        }
        int itemId = McCodec.readVarInt(in);
        int count = McCodec.readVarInt(in);
        int nAdd = McCodec.readVarInt(in);
        for (int i = 0; i < nAdd; i++) {
            McCodec.readVarInt(in);
            in.readInt();
        }
        int nRem = McCodec.readVarInt(in);
        for (int i = 0; i < nRem; i++) {
            McCodec.readVarInt(in);
        }
        int mapped = remapItemId(itemId);
        if (usesComponents(targetBand)) {
            writeItemCount(out, count);
            McCodec.writeVarInt(out, mapped);
            McCodec.writeVarInt(out, 0);
            McCodec.writeVarInt(out, 0);
        } else {
            out.writeBoolean(true);
            McCodec.writeVarInt(out, mapped);
            out.writeByte((byte) Math.min(127, Math.max(0, count)));
            out.writeByte(0);
        }
    }

    private void legacyToHashedSlot(ByteBuf in, ByteBuf out) {
        boolean srcComp = usesComponents(sourceBand);
        int itemId;
        int count;
        if (srcComp) {
            count = readItemCount(in);
            if (count == 0) {
                out.writeBoolean(false);
                return;
            }
            itemId = McCodec.readVarInt(in);
            int add = McCodec.readVarInt(in);
            int rem = McCodec.readVarInt(in);
            skipComponentPayload(in, add, rem);
        } else {
            if (!in.readBoolean()) {
                out.writeBoolean(false);
                return;
            }
            itemId = McCodec.readVarInt(in);
            count = in.readByte() & 0xFF;
            skipOptionalNbt(in);
        }
        out.writeBoolean(true);
        McCodec.writeVarInt(out, remapItemId(itemId));
        McCodec.writeVarInt(out, count);
        McCodec.writeVarInt(out, 0);
        McCodec.writeVarInt(out, 0);
    }

    private void remapComponentSlotSameEra(ByteBuf in, ByteBuf out) {
        int count = readItemCount(in);
        if (count == 0) {
            writeItemCount(out, 0);
            return;
        }
        int itemId = McCodec.readVarInt(in);
        int mapped = remapItemId(itemId);
        int add = McCodec.readVarInt(in);
        int rem = McCodec.readVarInt(in);
        writeItemCount(out, count);
        McCodec.writeVarInt(out, mapped);
        if (add == 0 && rem == 0) {
            McCodec.writeVarInt(out, 0);
            McCodec.writeVarInt(out, 0);
            return;
        }
        int mark = in.readerIndex();
        try {
            for (int i = 0; i < add; i++) {
                SlotComponentCodec.skipOne(in);
            }
            for (int i = 0; i < rem; i++) {
                McCodec.readVarInt(in);
            }
            int end = in.readerIndex();
            McCodec.writeVarInt(out, add);
            McCodec.writeVarInt(out, rem);
            out.writeBytes(in, mark, end - mark);
        } catch (Exception e) {
            in.readerIndex(mark);
            // VB.12 kick-safety: strip components rather than aborting the play remap
            McCodec.writeVarInt(out, 0);
            McCodec.writeVarInt(out, 0);
            try {
                skipComponentPayload(in, add, rem);
            } catch (Exception ignored) {
                // stream may be inconsistent; prefer silent strip over client kick
            }
        }
    }

    private static void skipComponentPayload(ByteBuf in, int add, int rem) {
        for (int i = 0; i < add; i++) {
            SlotComponentCodec.skipOne(in);
        }
        for (int i = 0; i < rem; i++) {
            McCodec.readVarInt(in);
        }
    }

    private void remapNbtSlotSameEra(ByteBuf in, ByteBuf out) {
        boolean present = in.readBoolean();
        out.writeBoolean(present);
        if (!present) {
            return;
        }
        int itemId = McCodec.readVarInt(in);
        byte count = in.readByte();
        McCodec.writeVarInt(out, remapItemId(itemId));
        out.writeByte(count);
        copyOptionalNbt(in, out);
    }

    private void componentToNbt(ByteBuf in, ByteBuf out) {
        int count = readItemCount(in);
        if (count == 0) {
            out.writeBoolean(false);
            return;
        }
        int itemId = McCodec.readVarInt(in);
        int add = McCodec.readVarInt(in);
        int rem = McCodec.readVarInt(in);
        skipComponentPayload(in, add, rem);
        out.writeBoolean(true);
        McCodec.writeVarInt(out, remapItemId(itemId));
        out.writeByte((byte) Math.min(127, Math.max(0, count)));
        out.writeByte(0);
    }

    private void nbtToComponent(ByteBuf in, ByteBuf out) {
        boolean present = in.readBoolean();
        if (!present) {
            writeItemCount(out, 0);
            return;
        }
        int itemId = McCodec.readVarInt(in);
        int count = in.readByte() & 0xFF;
        skipOptionalNbt(in);
        writeItemCount(out, count);
        McCodec.writeVarInt(out, remapItemId(itemId));
        McCodec.writeVarInt(out, 0);
        McCodec.writeVarInt(out, 0);
    }

    private static int readItemCount(ByteBuf in) {
        return McCodec.readVarInt(in);
    }

    private static void writeItemCount(ByteBuf out, int count) {
        McCodec.writeVarInt(out, count);
    }

    private static void copyOptionalNbt(ByteBuf in, ByteBuf out) {
        if (!in.isReadable()) {
            out.writeByte(0);
            return;
        }
        int mark = in.readerIndex();
        if (in.getByte(mark) == 0) {
            out.writeByte(in.readByte());
            return;
        }
        skipOptionalNbt(in);
        out.writeBytes(in, mark, in.readerIndex() - mark);
    }

    private static void skipOptionalNbt(ByteBuf buf) {
        if (!buf.isReadable()) {
            return;
        }
        byte type = buf.readByte();
        if (type == 0) {
            return;
        }
        if (type == 0x0a) {
            int nameLen = buf.readUnsignedShort();
            buf.skipBytes(nameLen);
            skipNbtPayload(buf, type);
            return;
        }
        skipNbtPayload(buf, type);
    }

    private static void skipNbtPayload(ByteBuf buf, byte type) {
        switch (type) {
            case 0x00 -> {
            }
            case 0x01 -> buf.skipBytes(1);
            case 0x02 -> buf.skipBytes(2);
            case 0x03, 0x05 -> buf.skipBytes(4);
            case 0x04, 0x06 -> buf.skipBytes(8);
            case 0x07 -> {
                int len = buf.readInt();
                buf.skipBytes(Math.max(0, len));
            }
            case 0x08 -> {
                int len = buf.readUnsignedShort();
                buf.skipBytes(len);
            }
            case 0x09 -> {
                byte elem = buf.readByte();
                int len = buf.readInt();
                for (int i = 0; i < len; i++) {
                    skipNbtPayload(buf, elem);
                }
            }
            case 0x0a -> {
                while (buf.isReadable()) {
                    byte t = buf.readByte();
                    if (t == 0x00) {
                        break;
                    }
                    int nameLen = buf.readUnsignedShort();
                    buf.skipBytes(nameLen);
                    skipNbtPayload(buf, t);
                }
            }
            case 0x0b -> {
                int len = buf.readInt();
                buf.skipBytes(len * 4);
            }
            case 0x0c -> {
                int len = buf.readInt();
                buf.skipBytes(len * 8);
            }
            default -> {
            }
        }
    }

    public ByteBuf remapSetSlot(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outPacketId);
            readWindowId(body, out);
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            out.writeShort(body.readShort());
            remapSlot(body, out);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public ByteBuf remapWindowItems(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 64);
            McCodec.writeVarInt(out, outPacketId);
            readWindowId(body, out);
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            int n = McCodec.readVarInt(body);
            McCodec.writeVarInt(out, n);
            for (int i = 0; i < n; i++) {
                remapSlot(body, out);
            }
            remapSlot(body, out);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public ByteBuf remapEquipment(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outPacketId);
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            while (body.isReadable()) {
                byte slot = body.readByte();
                out.writeByte(slot);
                remapSlot(body, out);
                if ((slot & 0x80) == 0) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public ByteBuf remapEntityMetadata(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outPacketId);
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            while (body.isReadable()) {
                int key = body.readUnsignedByte();
                out.writeByte(key);
                if (key == 0xFF) {
                    break;
                }
                int type = McCodec.readVarInt(body);
                McCodec.writeVarInt(out, type);
                if (type == 7) {
                    remapSlot(body, out);
                    continue;
                }
                if (!EntityMetadataSkip.copyValue(sourceBand, type, body, out)) {
                    out.writeByte(0xFF);
                    return out;
                }
            }
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public ByteBuf remapWindowClick(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 64);
            McCodec.writeVarInt(out, outPacketId);
            readWindowId(body, out);
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            out.writeShort(body.readShort());
            out.writeByte(body.readByte());
            McCodec.writeVarInt(out, McCodec.readVarInt(body));
            int changed = McCodec.readVarInt(body);
            McCodec.writeVarInt(out, changed);
            for (int i = 0; i < changed; i++) {
                out.writeShort(body.readShort());
                remapClickItem(body, out);
            }
            remapClickItem(body, out);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public ByteBuf remapCreativeSlot(ByteBuf body, int outPacketId) {
        int mark = body.readerIndex();
        try {
            ByteBuf out = Unpooled.buffer(body.readableBytes() + 32);
            McCodec.writeVarInt(out, outPacketId);
            out.writeShort(body.readShort());
            remapSlot(body, out);
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
}
