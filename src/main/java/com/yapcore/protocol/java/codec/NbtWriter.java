package com.yapcore.protocol.java.codec;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal binary NBT writer for configuration registries.
 * Network NBT (protocol ≥764) omits the root compound name.
 */
public final class NbtWriter {

    private NbtWriter() {
    }

    public static void writeEnd(ByteBuf out) {
        out.writeByte(0);
    }

    public static void writeByte(ByteBuf out, String name, int v) {
        out.writeByte(1);
        writeName(out, name);
        out.writeByte(v);
    }

    public static void writeInt(ByteBuf out, String name, int v) {
        out.writeByte(3);
        writeName(out, name);
        out.writeInt(v);
    }

    public static void writeLong(ByteBuf out, String name, long v) {
        out.writeByte(4);
        writeName(out, name);
        out.writeLong(v);
    }

    public static void writeFloat(ByteBuf out, String name, float v) {
        out.writeByte(5);
        writeName(out, name);
        out.writeFloat(v);
    }

    public static void writeDouble(ByteBuf out, String name, double v) {
        out.writeByte(6);
        writeName(out, name);
        out.writeDouble(v);
    }

    public static void writeString(ByteBuf out, String name, String v) {
        out.writeByte(8);
        writeName(out, name);
        byte[] b = v.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.writeBytes(b);
    }

    public static void writeCompound(ByteBuf out, String name, Runnable fields) {
        out.writeByte(10);
        writeName(out, name);
        fields.run();
        out.writeByte(0);
    }

    public static void writeListCompound(ByteBuf out, String name, List<Runnable> elements) {
        out.writeByte(9);
        writeName(out, name);
        out.writeByte(10);
        out.writeInt(elements.size());
        for (Runnable el : elements) {
            el.run();
            out.writeByte(0);
        }
    }

    private static void writeName(ByteBuf out, String name) {
        byte[] b = name.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.writeBytes(b);
    }
}
