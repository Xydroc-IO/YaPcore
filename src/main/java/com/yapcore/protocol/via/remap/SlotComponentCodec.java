package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;

/**
 * Skip SlotComponent payloads for mid same-era item remaps (minecraft-data 1.21.1 ids).
 * Nested {@code Slot} components recurse. Unknown component types fail-soft
 * (assume varint / strip) so mid clients are never kicked.
 */
public final class SlotComponentCodec {

    private SlotComponentCodec() {
    }

    /** Read one SlotComponent (type + data) and skip its payload. */
    public static void skipOne(ByteBuf in) {
        int typeId = McCodec.readVarInt(in);
        skipData(typeId, in);
    }

    /** Skip a full component-era Slot (itemCount + optional id/components). */
    public static void skipFullSlot(ByteBuf in) {
        int count = McCodec.readVarInt(in);
        if (count == 0) {
            return;
        }
        McCodec.readVarInt(in); // item id
        int add = McCodec.readVarInt(in);
        int rem = McCodec.readVarInt(in);
        for (int i = 0; i < add; i++) {
            skipOne(in);
        }
        for (int i = 0; i < rem; i++) {
            McCodec.readVarInt(in);
        }
    }

    private static void skipData(int typeId, ByteBuf in) {
        switch (typeId) {
            case 0, 5, 6, 19, 27, 36, 37, 38, 39, 43, 55, 56 -> skipNbt(in); // anonymousNbt
            case 1, 2, 3, 8, 13, 16, 26, 28, 41, 50 -> McCodec.readVarInt(in); // + base_color
            case 4, 18 -> in.readBoolean(); // unbreakable / enchantment_glint_override
            case 14, 15, 17, 21 -> {
                // void flags
            }
            case 7 -> { // lore: array anonOptionalNbt
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    skipOptionalNbt(in);
                }
            }
            case 9, 23 -> { // enchantments / stored_enchantments
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in);
                    McCodec.readVarInt(in);
                }
                in.readBoolean();
            }
            case 12 -> { // attribute_modifiers
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in);
                    McCodec.readString(in, 32767);
                    in.readDouble();
                    McCodec.readVarInt(in);
                    McCodec.readVarInt(in);
                }
                in.readBoolean();
            }
            case 20 -> { // food
                McCodec.readVarInt(in);
                in.readFloat();
                in.readBoolean();
                in.readFloat();
                skipFullSlot(in); // usingConvertsTo
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in);
                    in.readFloat();
                }
            }
            case 24 -> { // dyed_color
                in.readInt();
                in.readBoolean();
            }
            case 25 -> in.readInt(); // map_color
            case 29, 30, 52 -> { // charged_projectiles / bundle / container → Slot[]
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    skipFullSlot(in);
                }
            }
            case 31 -> { // potion_contents (simplified 1.21.1)
                if (in.readBoolean()) {
                    McCodec.readVarInt(in);
                }
                if (in.readBoolean()) {
                    in.readInt();
                }
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in); // effect id
                    McCodec.readVarInt(in);
                    McCodec.readVarInt(in);
                    in.readBoolean();
                    in.readBoolean();
                    in.readBoolean();
                    if (in.readBoolean()) {
                        skipNbt(in); // optional factor data — best-effort as nbt
                    }
                }
                if (in.readBoolean()) {
                    McCodec.readString(in, 32767);
                }
            }
            case 32 -> { // suspicious_stew_effects
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in);
                    McCodec.readVarInt(in);
                }
            }
            case 33, 34 -> { // writable/written book — strings + optional
                skipBookContent(in, typeId == 34);
            }
            case 45 -> skipFireworkExplosion(in);
            case 46 -> { // fireworks
                McCodec.readVarInt(in);
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    skipFireworkExplosion(in);
                }
            }
            case 47 -> skipProfile(in);
            case 48 -> McCodec.readString(in, 32767); // note_block_sound
            case 51 -> { // pot_decorations
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readVarInt(in);
                }
            }
            case 53 -> { // block_state properties
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    McCodec.readString(in, 32767);
                    McCodec.readString(in, 32767);
                }
            }
            case 54 -> { // bees
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    skipNbt(in);
                    McCodec.readVarInt(in);
                    McCodec.readVarInt(in);
                }
            }
            case 10, 11 -> { // can_place_on / can_break
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    RegistryHolderSkip.skipItemBlockPredicate(in);
                }
                in.readBoolean();
            }
            case 22 -> { // tool
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    RegistryHolderSkip.skipHolderSet(in); // blocks IDSet
                    if (in.readBoolean()) {
                        in.readFloat();
                    }
                    if (in.readBoolean()) {
                        in.readBoolean();
                    }
                }
                in.readFloat();
                McCodec.readVarInt(in);
            }
            case 35 -> { // trim
                RegistryHolderSkip.skipHolder(in, () -> RegistryHolderSkip.skipArmorTrimMaterial(in));
                RegistryHolderSkip.skipHolder(in, () -> RegistryHolderSkip.skipArmorTrimPattern(in));
                in.readBoolean();
            }
            case 40 -> RegistryHolderSkip.skipHolder(in, () -> RegistryHolderSkip.skipInstrumentData(in));
            case 42 -> { // jukebox_playable (1.21.1: bool + holder + bool — shapes vary; best-effort)
                in.readBoolean();
                RegistryHolderSkip.skipHolder(in, () -> {
                    McCodec.readString(in, 32767);
                    if (in.readBoolean()) {
                        in.readFloat();
                    }
                });
                in.readBoolean();
            }
            case 44 -> { // lodestone_tracker
                if (in.readBoolean()) {
                    McCodec.readString(in, 32767); // dimension
                    in.readLong(); // block pos packed
                }
                in.readBoolean();
            }
            case 49 -> { // banner_patterns
                int n = McCodec.readVarInt(in);
                for (int i = 0; i < n; i++) {
                    RegistryHolderSkip.skipHolder(in, () -> {
                        McCodec.readString(in, 32767);
                        McCodec.readString(in, 32767);
                    });
                    McCodec.readVarInt(in); // dye color
                }
            }
            default -> {
                // VB.12 kick-safety: never throw on unknown component layouts.
                // 26.x animal/paint/dye variants are almost always a single varint.
                if (typeId >= 57 && typeId <= 200) {
                    McCodec.readVarInt(in);
                    return;
                }
                // Last resort — single varint (common) so the mid client keeps the stack type/count.
                McCodec.readVarInt(in);
            }
        }
    }

    private static void skipBookContent(ByteBuf in, boolean written) {
        // pages: array of filterable strings (string + optional string)
        int pages = McCodec.readVarInt(in);
        for (int i = 0; i < pages; i++) {
            McCodec.readString(in, 32767);
            if (in.readBoolean()) {
                McCodec.readString(in, 32767);
            }
        }
        if (written) {
            McCodec.readString(in, 32767); // title
            if (in.readBoolean()) {
                McCodec.readString(in, 32767);
            }
            McCodec.readString(in, 32767); // author
            McCodec.readVarInt(in); // generation
            in.readBoolean(); // resolved
        }
    }

    private static void skipFireworkExplosion(ByteBuf in) {
        McCodec.readVarInt(in); // shape
        int colors = McCodec.readVarInt(in);
        in.skipBytes(colors * 4);
        int fade = McCodec.readVarInt(in);
        in.skipBytes(fade * 4);
        in.readBoolean();
        in.readBoolean();
    }

    private static void skipProfile(ByteBuf in) {
        // GameProfile-ish: optional name, optional uuid, properties array
        if (in.readBoolean()) {
            McCodec.readString(in, 16);
        }
        if (in.readBoolean()) {
            in.skipBytes(16);
        }
        int n = McCodec.readVarInt(in);
        for (int i = 0; i < n; i++) {
            McCodec.readString(in, 32767);
            McCodec.readString(in, 32767);
            if (in.readBoolean()) {
                McCodec.readString(in, 32767);
            }
        }
    }

    static void skipNbt(ByteBuf buf) {
        byte type = buf.readByte();
        if (type == 0) {
            return;
        }
        skipNbtPayload(buf, type);
    }

    static void skipOptionalNbt(ByteBuf buf) {
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
}
