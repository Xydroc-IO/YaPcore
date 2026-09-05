package com.yapcore.world.schem;

import com.yapcore.world.edit.BlockBatch;
import com.yapcore.world.edit.MaskEngine;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure planning for schematic → {@link BlockBatch.Encoded} lists (CFI-lite).
 * Section-aware sort groups 16³ locality without NMS section injection.
 */
public final class SchematicPastePlanner {

    private SchematicPastePlanner() {
    }

    public record Options(
            boolean ignoreAir,
            boolean sectionSort,
            MaskEngine masks,
            UUID playerId
    ) {
        public static Options defaults() {
            return new Options(false, true, null, null);
        }
    }

    /**
     * Build world-absolute encoded plans from a schematic at the given origin.
     * Pass {@code world} when applying masks; otherwise null is fine.
     */
    public static List<BlockBatch.Encoded> plan(Schematic schematic, int originX, int originY, int originZ,
                                                Options options) {
        return plan(schematic, originX, originY, originZ, null, options);
    }

    public static List<BlockBatch.Encoded> plan(Schematic schematic, int originX, int originY, int originZ,
                                                World world, Options options) {
        Options opts = options == null ? Options.defaults() : options;
        List<BlockBatch.Encoded> plans = new ArrayList<>(schematic.blocks().size());
        for (Schematic.BlockEntry entry : schematic.blocks()) {
            if (opts.ignoreAir() && isAirEncoded(entry.encoded())) {
                continue;
            }
            int wx = originX + entry.dx();
            int wy = originY + entry.dy();
            int wz = originZ + entry.dz();
            if (opts.masks() != null && opts.playerId() != null && world != null
                    && !opts.masks().allows(opts.playerId(), world, wx, wy, wz)) {
                continue;
            }
            plans.add(new BlockBatch.Encoded(wx, wy, wz, entry.encoded(), entry.tileNbt()));
        }
        if (opts.sectionSort()) {
            sortBySection(plans);
        }
        return plans;
    }

    /** Sort by chunk Z, chunk X, then section Y — matches BlockBatch wave locality. */
    public static void sortBySection(List<BlockBatch.Encoded> plans) {
        plans.sort(Comparator
                .comparingInt((BlockBatch.Encoded e) -> e.z() >> 4)
                .thenComparingInt(e -> e.x() >> 4)
                .thenComparingInt(e -> e.y() >> 4)
                .thenComparingInt(BlockBatch.Encoded::y)
                .thenComparingInt(BlockBatch.Encoded::x)
                .thenComparingInt(BlockBatch.Encoded::z));
    }

    /** Pack section coordinates for tests / future CFI encoder keys. */
    public static long sectionKey(int x, int y, int z) {
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        return (((long) sx & 0x3fffffL) << 42)
                | (((long) sy & 0xfffffL) << 22)
                | ((long) sz & 0x3fffffL);
    }

    public static boolean isAirEncoded(String encoded) {
        return encoded == null || encoded.startsWith("AIR") || encoded.startsWith("minecraft:air")
                || encoded.startsWith("CAVE_AIR") || encoded.startsWith("VOID_AIR");
    }
}
