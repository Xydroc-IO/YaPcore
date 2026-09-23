package com.yapcore.world.schem;

import com.yapcore.world.schem.nbt.MinimalNbt;
import com.yapcore.world.util.BlockCodec;
import com.yapcore.world.BlockStateAliases;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Imports WorldEdit / Sponge {@code .schem} (v2 byte palette + v3 varint) into {@link Schematic}.
 * <p>
 * Accepts both legacy flat roots ({@code Schematic} as the root name) and the Sponge/WorldEdit
 * nesting where an empty-named root holds a child {@code Schematic} compound. Version 3 stores
 * palette + block indices under {@code Blocks} ({@code Palette}/{@code Data}).
 */
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
        MinimalNbt.Compound schem = resolveSchematicRoot(root);
        int width = unsignedShort(schem, "Width");
        int height = unsignedShort(schem, "Height");
        int length = unsignedShort(schem, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("invalid dimensions in " + label);
        }
        MinimalNbt.Compound blockContainer = schem.getCompound("Blocks");
        MinimalNbt.Compound paletteSource = blockContainer != null ? blockContainer : schem;
        Map<Integer, String> palette = paletteSource.palette();
        if (palette.isEmpty()) {
            throw new IOException("missing Palette in " + label);
        }
        int volume = Math.multiplyExact(Math.multiplyExact(width, height), length);
        int[] indices = readBlockIndices(schem, blockContainer, volume);
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
                    try {
                        BlockData data = BlockStateAliases.create(state);
                        blocks.add(new Schematic.BlockEntry(x, y, z, BlockCodec.encode(data)));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown / unmapped legacy block — skip rather than fail whole paste
                    }
                }
            }
        }
        return new Schematic("imported", 0, 0, 0, blocks);
    }

    /**
     * WorldEdit / Sponge often nest the schematic under a child {@code Schematic} compound
     * (empty-named gzip root). Older exports put Width/Palette directly on the root.
     */
    static MinimalNbt.Compound resolveSchematicRoot(MinimalNbt.Compound root) {
        if (root == null) {
            return null;
        }
        if (hasDimensions(root) || root.getCompound("Palette") != null || root.getCompound("Blocks") != null) {
            return root;
        }
        MinimalNbt.Compound nested = root.getCompound("Schematic");
        if (nested != null) {
            return nested;
        }
        return root;
    }

    private static boolean hasDimensions(MinimalNbt.Compound c) {
        return unsignedShort(c, "Width") > 0
                && unsignedShort(c, "Height") > 0
                && unsignedShort(c, "Length") > 0;
    }

    /** Sponge width/height/length are unsigned shorts stored in signed NBT Short tags. */
    static int unsignedShort(MinimalNbt.Compound c, String key) {
        return c.getShort(key, (short) 0) & 0xFFFF;
    }

    private static int[] readBlockIndices(MinimalNbt.Compound schem, MinimalNbt.Compound blocks,
                                          int volume) throws IOException {
        // v3: Blocks.Data (varint byte array); also accept Blocks.BlockData
        if (blocks != null) {
            byte[] data = blocks.getByteArray("Data");
            if (data.length == 0) {
                data = blocks.getByteArray("BlockData");
            }
            if (data.length > 0) {
                if (data.length == volume) {
                    return bytesToIndices(data, volume);
                }
                return MinimalNbt.readVarIntArray(data, volume);
            }
            int[] ints = blocks.getIntArray("BlockData");
            if (ints.length >= volume) {
                return ints;
            }
        }
        // v2: root BlockData
        return readBlockIndicesV2(schem, volume);
    }

    private static int[] readBlockIndicesV2(MinimalNbt.Compound root, int volume) throws IOException {
        int[] intArray = root.getIntArray("BlockData");
        if (intArray.length >= volume) {
            return intArray;
        }
        byte[] bytes = root.getByteArray("BlockData");
        if (bytes.length == 0) {
            throw new IOException("missing BlockData");
        }
        if (bytes.length == volume) {
            return bytesToIndices(bytes, volume);
        }
        return MinimalNbt.readVarIntArray(bytes, volume);
    }

    private static int[] bytesToIndices(byte[] bytes, int volume) {
        int[] out = new int[volume];
        for (int i = 0; i < volume; i++) {
            out[i] = bytes[i] & 0xFF;
        }
        return out;
    }

    private static boolean isAir(String state) {
        String n = BlockStateAliases.normalize(state);
        return n.equals("minecraft:air") || n.endsWith(":air") || n.equals("air");
    }
}
