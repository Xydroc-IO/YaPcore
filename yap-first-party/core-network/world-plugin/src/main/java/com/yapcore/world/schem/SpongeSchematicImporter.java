package com.yapcore.world.schem;

import com.yapcore.world.schem.nbt.MinimalNbt;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Imports WorldEdit / Sponge {@code .schem} (v2 byte palette + v3 varint) into {@link Schematic}. */
public final class SpongeSchematicImporter {

    private SpongeSchematicImporter() {
    }

    public static Schematic importFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MinimalNbt.Compound root = MinimalNbt.readGzipCompound(in);
            return fromCompound(root, file.getFileName().toString());
        }
    }

    public static Schematic fromCompound(MinimalNbt.Compound root, String label) throws IOException {
        int width = root.getShort("Width", (short) 0);
        int height = root.getShort("Height", (short) 0);
        int length = root.getShort("Length", (short) 0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("invalid dimensions in " + label);
        }
        Map<Integer, String> palette = root.palette();
        if (palette.isEmpty()) {
            throw new IOException("missing Palette in " + label);
        }
        int volume = width * height * length;
        int[] indices = readBlockIndices(root, volume);
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    if (index >= indices.length) {
                        continue;
                    }
                    String state = palette.get(indices[index]);
                    if (state == null || isAir(state)) {
                        continue;
                    }
                    BlockData data = Bukkit.createBlockData(normalizeState(state));
                    blocks.add(new Schematic.BlockEntry(x, y, z, BlockCodec.encode(data)));
                }
            }
        }
        return new Schematic("imported", 0, 0, 0, blocks);
    }

    private static int[] readBlockIndices(MinimalNbt.Compound root, int volume) throws IOException {
        int[] intArray = root.getIntArray("BlockData");
        if (intArray.length >= volume) {
            return intArray;
        }
        byte[] bytes = root.getByteArray("BlockData");
        if (bytes.length == 0) {
            throw new IOException("missing BlockData");
        }
        if (bytes.length == volume) {
            int[] out = new int[volume];
            for (int i = 0; i < volume; i++) {
                out[i] = bytes[i] & 0xFF;
            }
            return out;
        }
        return MinimalNbt.readVarIntArray(bytes, volume);
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.endsWith(":air");
    }

    private static String normalizeState(String state) {
        if (state.contains("[")) {
            return state;
        }
        return state;
    }
}
