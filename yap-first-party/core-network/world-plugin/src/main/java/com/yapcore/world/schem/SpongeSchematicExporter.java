package com.yapcore.world.schem;

import com.yapcore.world.schem.Schematic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Exports YaP {@link Schematic} as Sponge {@code .schem} (v2-style palette + byte BlockData). */
public final class SpongeSchematicExporter {

    private SpongeSchematicExporter() {
    }

    public static void exportFile(Path file, Schematic schematic) throws IOException {
        Schematic.Bounds b = schematic.bounds();
        int width = Math.max(1, b.sizeX());
        int height = Math.max(1, b.sizeY());
        int length = Math.max(1, b.sizeZ());
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        int next = 1;
        int volume = width * height * length;
        byte[] blockData = new byte[volume];
        for (Schematic.BlockEntry e : schematic.blocks()) {
            if (e.dx() < 0 || e.dy() < 0 || e.dz() < 0
                    || e.dx() >= width || e.dy() >= height || e.dz() >= length) {
                continue;
            }
            String state = toState(e.encoded());
            Integer idx = palette.get(state);
            if (idx == null) {
                if (next > 255) {
                    state = "minecraft:stone";
                    idx = palette.computeIfAbsent(state, k -> 1);
                } else {
                    idx = next++;
                    palette.put(state, idx);
                }
            }
            int index = (e.dy() * length + e.dz()) * width + e.dx();
            blockData[index] = (byte) (idx & 0xFF);
        }
        Files.createDirectories(file.getParent());
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(raw);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(10); // compound
            writeUtf(out, "Schematic");
            writeShortTag(out, "Width", (short) width);
            writeShortTag(out, "Height", (short) height);
            writeShortTag(out, "Length", (short) length);
            writeIntTag(out, "Version", 2);
            writeIntTag(out, "DataVersion", 3700);
            // Palette
            out.writeByte(10);
            writeUtf(out, "Palette");
            for (var e : palette.entrySet()) {
                writeIntTag(out, e.getKey(), e.getValue());
            }
            out.writeByte(0); // end palette
            writeByteArrayTag(out, "BlockData", blockData);
            out.writeByte(0); // end root
        }
    }

    private static String toState(String encoded) {
        if (encoded == null || encoded.isBlank() || "AIR".equalsIgnoreCase(encoded)
                || encoded.startsWith("AIR|")) {
            return "minecraft:air";
        }
        int sep = encoded.indexOf('|');
        if (sep >= 0 && sep + 1 < encoded.length()) {
            return encoded.substring(sep + 1);
        }
        Material mat = Material.matchMaterial(encoded);
        if (mat == null || !mat.isBlock()) {
            mat = Material.STONE;
        }
        return Bukkit.createBlockData(mat).getAsString();
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
