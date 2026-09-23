package com.yapcore.world.schem;

import com.yapcore.world.schem.nbt.MinimalNbt;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nested Sponge/WorldEdit .schem roots (no Bukkit). */
class SpongeSchematicImporterTest {

    @Test
    void unwrapsEmptyNamedRootWithSchematicChild() throws IOException {
        byte[] gzip = writeNestedV2Schem(7, 11, 5);
        MinimalNbt.Compound root = MinimalNbt.readGzipCompound(new ByteArrayInputStream(gzip));
        assertEquals(0, SpongeSchematicImporter.unsignedShort(root, "Width"));

        MinimalNbt.Compound schem = SpongeSchematicImporter.resolveSchematicRoot(root);
        assertNotNull(schem);
        assertEquals(7, SpongeSchematicImporter.unsignedShort(schem, "Width"));
        assertEquals(11, SpongeSchematicImporter.unsignedShort(schem, "Height"));
        assertEquals(5, SpongeSchematicImporter.unsignedShort(schem, "Length"));
        assertTrue(schem.palette().containsKey(0));
        assertEquals("minecraft:air", schem.palette().get(0));
    }

    @Test
    void flatSchematicRootStillResolves() throws IOException {
        byte[] gzip = writeFlatV2Schem((short) 3, (short) 4, (short) 5);
        MinimalNbt.Compound root = MinimalNbt.readGzipCompound(new ByteArrayInputStream(gzip));
        MinimalNbt.Compound schem = SpongeSchematicImporter.resolveSchematicRoot(root);
        assertEquals(3, SpongeSchematicImporter.unsignedShort(schem, "Width"));
        assertEquals(4, SpongeSchematicImporter.unsignedShort(schem, "Height"));
        assertEquals(5, SpongeSchematicImporter.unsignedShort(schem, "Length"));
    }

    @Test
    void unsignedShortTreatsHighBitAsLargeDimension() throws IOException {
        // 40000 as signed short is negative; Sponge stores unsigned
        byte[] gzip = writeFlatV2Schem((short) 40000, (short) 1, (short) 1);
        MinimalNbt.Compound root = MinimalNbt.readGzipCompound(new ByteArrayInputStream(gzip));
        MinimalNbt.Compound schem = SpongeSchematicImporter.resolveSchematicRoot(root);
        assertEquals(40000, SpongeSchematicImporter.unsignedShort(schem, "Width"));
    }

    /** Modern downloads: empty root name + child {@code Schematic} compound. */
    private static byte[] writeNestedV2Schem(int w, int h, int l) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(10);
            writeUtf(out, ""); // empty root name
            out.writeByte(10);
            writeUtf(out, "Schematic");
            writeSchematicBody(out, (short) w, (short) h, (short) l);
            out.writeByte(0); // end Schematic
            out.writeByte(0); // end root
        }
        return bos.toByteArray();
    }

    private static byte[] writeFlatV2Schem(short w, short h, short l) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(10);
            writeUtf(out, "Schematic");
            writeSchematicBody(out, w, h, l);
            out.writeByte(0);
        }
        return bos.toByteArray();
    }

    private static void writeSchematicBody(DataOutputStream out, short w, short h, short l) throws IOException {
        writeShortTag(out, "Width", w);
        writeShortTag(out, "Height", h);
        writeShortTag(out, "Length", l);
        writeIntTag(out, "Version", 2);
        out.writeByte(10);
        writeUtf(out, "Palette");
        writeIntTag(out, "minecraft:air", 0);
        writeIntTag(out, "minecraft:stone", 1);
        out.writeByte(0);
        int volume = (w & 0xFFFF) * (h & 0xFFFF) * (l & 0xFFFF);
        // Cap tiny fixtures so huge unsigned widths don't allocate gigabytes in this unit test
        byte[] data = new byte[Math.min(volume, 8)];
        writeByteArrayTag(out, "BlockData", data);
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void writeShortTag(DataOutputStream out, String name, short v) throws IOException {
        out.writeByte(2);
        writeUtf(out, name);
        out.writeShort(v);
    }

    private static void writeIntTag(DataOutputStream out, String name, int v) throws IOException {
        out.writeByte(3);
        writeUtf(out, name);
        out.writeInt(v);
    }

    private static void writeByteArrayTag(DataOutputStream out, String name, byte[] data) throws IOException {
        out.writeByte(7);
        writeUtf(out, name);
        out.writeInt(data.length);
        out.write(data);
    }
}
