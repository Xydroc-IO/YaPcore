package com.yapcore.world.schem.nbt;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal NBT reader for Sponge schematic import (compounds, primitives, arrays). */
public final class MinimalNbt {

    private MinimalNbt() {
    }

    public static Compound readGzipCompound(InputStream in) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(in);
             DataInputStream data = new DataInputStream(gzip)) {
            return readCompoundPayload(data);
        }
    }

    public static Compound readCompoundPayload(DataInput in) throws IOException {
        byte type = in.readByte();
        if (type != Tag.COMPOUND) {
            throw new IOException("expected compound tag, got " + type);
        }
        readUtf(in); // root name — ignored
        return readCompound(in);
    }

    private static Compound readCompound(DataInput in) throws IOException {
        Map<String, TagValue> map = new LinkedHashMap<>();
        byte type;
        while ((type = in.readByte()) != Tag.END) {
            String name = readUtf(in);
            map.put(name, readValue(in, type));
        }
        return new Compound(map);
    }

    private static TagValue readValue(DataInput in, byte type) throws IOException {
        return switch (type) {
            case Tag.BYTE -> TagValue.byteVal(in.readByte());
            case Tag.SHORT -> TagValue.shortVal(in.readShort());
            case Tag.INT -> TagValue.intVal(in.readInt());
            case Tag.LONG -> TagValue.longVal(in.readLong());
            case Tag.FLOAT -> TagValue.floatVal(in.readFloat());
            case Tag.DOUBLE -> TagValue.doubleVal(in.readDouble());
            case Tag.BYTE_ARRAY -> TagValue.bytes(readBytes(in));
            case Tag.STRING -> TagValue.string(readUtf(in));
            case Tag.LIST -> readList(in);
            case Tag.COMPOUND -> TagValue.compound(readCompound(in));
            case Tag.INT_ARRAY -> TagValue.ints(readInts(in));
            case Tag.LONG_ARRAY -> TagValue.longs(readLongs(in));
            default -> throw new IOException("unsupported tag type " + type);
        };
    }

    private static TagValue readList(DataInput in) throws IOException {
        byte elemType = in.readByte();
        int len = in.readInt();
        List<TagValue> values = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            values.add(readValue(in, elemType));
        }
        return TagValue.list(values);
    }

    private static byte[] readBytes(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] out = new byte[len];
        in.readFully(out);
        return out;
    }

    private static int[] readInts(DataInput in) throws IOException {
        int len = in.readInt();
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = in.readInt();
        }
        return out;
    }

    private static long[] readLongs(DataInput in) throws IOException {
        int len = in.readInt();
        long[] out = new long[len];
        for (int i = 0; i < len; i++) {
            out[i] = in.readLong();
        }
        return out;
    }

    private static String readUtf(DataInput in) throws IOException {
        int len = in.readUnsignedShort();
        if (len == 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class Compound {
        private final Map<String, TagValue> values;

        Compound(Map<String, TagValue> values) {
            this.values = values;
        }

        public int getInt(String key, int def) {
            TagValue v = values.get(key);
            return v == null ? def : v.asInt(def);
        }

        public short getShort(String key, short def) {
            TagValue v = values.get(key);
            return v == null ? def : v.asShort(def);
        }

        public byte[] getByteArray(String key) {
            TagValue v = values.get(key);
            return v == null ? new byte[0] : v.asBytes();
        }

        public int[] getIntArray(String key) {
            TagValue v = values.get(key);
            return v == null ? new int[0] : v.asInts();
        }

        public Compound getCompound(String key) {
            TagValue v = values.get(key);
            return v == null ? null : v.asCompound();
        }

        public Map<Integer, String> palette() {
            Compound palette = getCompound("Palette");
            if (palette == null) {
                return Map.of();
            }
            Map<Integer, String> out = new HashMap<>();
            for (var entry : palette.values.entrySet()) {
                try {
                    // Inverted form: int-key → state-string
                    out.put(Integer.parseInt(entry.getKey()), entry.getValue().asString(""));
                } catch (NumberFormatException e) {
                    // Standard Sponge: state-string → int-value
                    int idx = entry.getValue().asInt(-1);
                    if (idx >= 0) {
                        out.put(idx, entry.getKey());
                    }
                }
            }
            return out;
        }
    }

    static final class Tag {
        static final byte END = 0;
        static final byte BYTE = 1;
        static final byte SHORT = 2;
        static final byte INT = 3;
        static final byte LONG = 4;
        static final byte FLOAT = 5;
        static final byte DOUBLE = 6;
        static final byte BYTE_ARRAY = 7;
        static final byte STRING = 8;
        static final byte LIST = 9;
        static final byte COMPOUND = 10;
        static final byte INT_ARRAY = 11;
        static final byte LONG_ARRAY = 12;
    }

    public static final class TagValue {
        private final byte kind;
        private final Object value;

        private TagValue(byte kind, Object value) {
            this.kind = kind;
            this.value = value;
        }

        static TagValue byteVal(byte v) {
            return new TagValue(Tag.BYTE, v);
        }

        static TagValue shortVal(short v) {
            return new TagValue(Tag.SHORT, v);
        }

        static TagValue intVal(int v) {
            return new TagValue(Tag.INT, v);
        }

        static TagValue longVal(long v) {
            return new TagValue(Tag.LONG, v);
        }

        static TagValue floatVal(float v) {
            return new TagValue(Tag.FLOAT, v);
        }

        static TagValue doubleVal(double v) {
            return new TagValue(Tag.DOUBLE, v);
        }

        static TagValue bytes(byte[] v) {
            return new TagValue(Tag.BYTE_ARRAY, v);
        }

        static TagValue string(String v) {
            return new TagValue(Tag.STRING, v);
        }

        static TagValue list(List<TagValue> v) {
            return new TagValue(Tag.LIST, v);
        }

        static TagValue compound(Compound v) {
            return new TagValue(Tag.COMPOUND, v);
        }

        static TagValue ints(int[] v) {
            return new TagValue(Tag.INT_ARRAY, v);
        }

        static TagValue longs(long[] v) {
            return new TagValue(Tag.LONG_ARRAY, v);
        }

        int asInt(int def) {
            return switch (kind) {
                case Tag.BYTE -> ((Byte) value).intValue();
                case Tag.SHORT -> ((Short) value).intValue();
                case Tag.INT -> (Integer) value;
                default -> def;
            };
        }

        short asShort(short def) {
            return switch (kind) {
                case Tag.BYTE -> ((Byte) value).shortValue();
                case Tag.SHORT -> (Short) value;
                case Tag.INT -> ((Integer) value).shortValue();
                default -> def;
            };
        }

        byte[] asBytes() {
            return kind == Tag.BYTE_ARRAY ? (byte[]) value : new byte[0];
        }

        int[] asInts() {
            return kind == Tag.INT_ARRAY ? (int[]) value : new int[0];
        }

        String asString(String def) {
            return kind == Tag.STRING ? (String) value : def;
        }

        Compound asCompound() {
            return kind == Tag.COMPOUND ? (Compound) value : null;
        }
    }

    /** Reads varints from a byte stream (Sponge v3 BlockData). */
    public static int[] readVarIntArray(byte[] data, int expected) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int[] out = new int[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = readVarInt(in);
        }
        return out;
    }

    private static int readVarInt(DataInput in) throws IOException {
        int value = 0;
        int size = 0;
        int b;
        while (((b = in.readUnsignedByte()) & 0x80) != 0) {
            value |= (b & 0x7F) << (size * 7);
            size++;
            if (size > 5) {
                throw new IOException("varint too long");
            }
        }
        value |= b << (size * 7);
        return value;
    }
}
