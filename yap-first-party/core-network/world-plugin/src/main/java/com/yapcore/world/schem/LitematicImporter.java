package com.yapcore.world.schem;

import com.yapcore.world.BlockStateAliases;
import com.yapcore.world.schem.nbt.MinimalNbt;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports Litematica {@code .litematic} (gzip NBT) into {@link Schematic}.
 * <p>
 * Parses real {@code BlockStatePalette} + bit-packed {@code BlockStates} (no FAWE).
 * Multi-region files are merged using each region's {@code Position} offset.
 * Entities / tile entities are skipped (block paste only).
 */
public final class LitematicImporter {

    private LitematicImporter() {
    }

    public static Schematic importFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MinimalNbt.Compound root = MinimalNbt.readGzipCompound(in);
            return fromRoot(root, file.getFileName().toString());
        }
    }

    static Schematic fromRoot(MinimalNbt.Compound root, String label) throws IOException {
        MinimalNbt.Compound regions = root.getCompound("Regions");
        if (regions == null || regions.childCompounds().isEmpty()) {
            throw new IOException("no Regions in .litematic: " + label);
        }
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;

        for (Map.Entry<String, MinimalNbt.Compound> entry : regions.childCompounds().entrySet()) {
            RegionBlocks region = parseRegion(entry.getValue(), entry.getKey());
            for (Schematic.BlockEntry b : region.blocks()) {
                int x = b.dx();
                int y = b.dy();
                int z = b.dz();
                blocks.add(b);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
                any = true;
            }
        }
        if (!any) {
            throw new IOException("empty .litematic (no non-air blocks): " + label);
        }
        // Normalize so clipboard origin is (0,0,0)
        if (minX != 0 || minY != 0 || minZ != 0) {
            List<Schematic.BlockEntry> shifted = new ArrayList<>(blocks.size());
            for (Schematic.BlockEntry b : blocks) {
                shifted.add(new Schematic.BlockEntry(
                        b.dx() - minX, b.dy() - minY, b.dz() - minZ, b.encoded(), b.tileNbt()));
            }
            blocks = shifted;
        }
        return new Schematic("imported", 0, 0, 0, blocks);
    }

    private static RegionBlocks parseRegion(MinimalNbt.Compound region, String name) throws IOException {
        int[] size = readVec3(region, "Size");
        int[] pos = readVec3(region, "Position");
        int sizeX = size[0];
        int sizeY = size[1];
        int sizeZ = size[2];
        int width = Math.abs(sizeX);
        int height = Math.abs(sizeY);
        int length = Math.abs(sizeZ);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("invalid Size in region " + name);
        }

        List<String> palette = readPalette(region);
        if (palette.isEmpty()) {
            throw new IOException("missing BlockStatePalette in region " + name);
        }
        long[] packed = region.getLongArray("BlockStates");
        if (packed.length == 0) {
            throw new IOException("missing BlockStates in region " + name);
        }

        int bits = bitsPerBlock(palette.size());
        int volume = width * height * length;
        int originX = Math.min(pos[0], pos[0] + sizeX + (sizeX < 0 ? 1 : 0));
        int originY = Math.min(pos[1], pos[1] + sizeY + (sizeY < 0 ? 1 : 0));
        int originZ = Math.min(pos[2], pos[2] + sizeZ + (sizeZ < 0 ? 1 : 0));

        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    if (index >= volume) {
                        continue;
                    }
                    int paletteId = unpack(packed, index, bits);
                    if (paletteId < 0 || paletteId >= palette.size()) {
                        continue;
                    }
                    String state = palette.get(paletteId);
                    if (state == null || isAir(state)) {
                        continue;
                    }
                    try {
                        BlockData data = BlockStateAliases.create(state);
                        int wx = originX + x;
                        int wy = originY + y;
                        int wz = originZ + z;
                        blocks.add(new Schematic.BlockEntry(wx, wy, wz, BlockCodec.encode(data)));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown / unmapped — skip
                    }
                }
            }
        }
        return new RegionBlocks(blocks);
    }

    /** Size/Position as compound {x,y,z} or int array [x,y,z]. */
    static int[] readVec3(MinimalNbt.Compound c, String key) throws IOException {
        MinimalNbt.Compound compound = c.getCompound(key);
        if (compound != null) {
            return new int[]{
                    compound.getInt("x", 0),
                    compound.getInt("y", 0),
                    compound.getInt("z", 0)
            };
        }
        int[] arr = c.getIntArray(key);
        if (arr.length >= 3) {
            return new int[]{arr[0], arr[1], arr[2]};
        }
        throw new IOException("missing " + key + " vec3");
    }

    static List<String> readPalette(MinimalNbt.Compound region) {
        List<MinimalNbt.TagValue> list = region.getList("BlockStatePalette");
        List<String> out = new ArrayList<>(list.size());
        for (MinimalNbt.TagValue v : list) {
            MinimalNbt.Compound entry = v.asCompound();
            if (entry == null) {
                out.add("minecraft:air");
                continue;
            }
            out.add(paletteEntryToState(entry));
        }
        return out;
    }

    static String paletteEntryToState(MinimalNbt.Compound entry) {
        String name = entry.getString("Name", "minecraft:air");
        if (name.isBlank()) {
            name = "minecraft:air";
        }
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        String bracket = propsToBracket(entry.getCompound("Properties"));
        return bracket.isEmpty() ? name : name + bracket;
    }

    private static String propsToBracket(MinimalNbt.Compound props) {
        if (props == null) {
            return "";
        }
        Map<String, String> entries = props.stringEntries();
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean any = false;
        for (var e : entries.entrySet()) {
            if (any) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            any = true;
        }
        sb.append(']');
        return sb.toString();
    }

    /** Litematica: at least 2 bits; ceil(log2(paletteSize)). */
    static int bitsPerBlock(int paletteSize) {
        int size = Math.max(1, paletteSize);
        int bits = Integer.SIZE - Integer.numberOfLeadingZeros(size - 1);
        return Math.max(2, bits);
    }

    /** Minecraft / Litematica long-array bit packing (index → palette id). */
    static int unpack(long[] data, int index, int bits) {
        long maxEntryValue = (1L << bits) - 1L;
        int startOffset = index * bits;
        int startArrIndex = startOffset >> 6;
        int endArrIndex = ((index + 1) * bits - 1) >> 6;
        int startBitOffset = startOffset & 0x3F;
        if (startArrIndex >= data.length) {
            return 0;
        }
        if (startArrIndex == endArrIndex) {
            return (int) (data[startArrIndex] >>> startBitOffset & maxEntryValue);
        }
        if (endArrIndex >= data.length) {
            return (int) (data[startArrIndex] >>> startBitOffset & maxEntryValue);
        }
        int endOffset = 64 - startBitOffset;
        return (int) ((data[startArrIndex] >>> startBitOffset | data[endArrIndex] << endOffset) & maxEntryValue);
    }

    private static boolean isAir(String state) {
        String n = BlockStateAliases.normalize(state).toLowerCase(Locale.ROOT);
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air")
                || n.equals("minecraft:void_air") || n.endsWith(":air") || n.equals("air");
    }

    private record RegionBlocks(List<Schematic.BlockEntry> blocks) {
    }
}
