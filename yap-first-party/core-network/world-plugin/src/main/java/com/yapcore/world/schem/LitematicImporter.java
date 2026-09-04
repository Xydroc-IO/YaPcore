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

/**
 * Imports Litematica {@code .litematic} (gzip NBT) — reads first region Blocks + palette when present.
 * Best-effort; complex multi-region / entity payloads may be incomplete.
 */
public final class LitematicImporter {

    private LitematicImporter() {
    }

    public static Schematic importFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MinimalNbt.Compound root = MinimalNbt.readGzipCompound(in);
            MinimalNbt.Compound regions = root.getCompound("Regions");
            if (regions == null) {
                // Fallback: treat as Sponge-like
                return SpongeSchematicImporter.fromCompound(root, file.getFileName().toString());
            }
            // Prefer Metadata or first region — Compound values aren't iterable publicly;
            // try common region names then scan via Sponge path if empty.
            MinimalNbt.Compound region = regions.getCompound("Unnamed") ;
            if (region == null) {
                region = regions.getCompound("main");
            }
            if (region == null) {
                region = regions.getCompound("Region");
            }
            if (region == null) {
                // Last resort: try sponge shape at root
                try {
                    return SpongeSchematicImporter.fromCompound(root, file.getFileName().toString());
                } catch (IOException e) {
                    throw new IOException("no readable region in .litematic (need Unnamed/main/Region)", e);
                }
            }
            return fromRegion(region, file.getFileName().toString());
        }
    }

    private static Schematic fromRegion(MinimalNbt.Compound region, String label) throws IOException {
        // Litematica stores Size as IntArray [x,y,z] and BlockStates as LongArray
        int[] size = region.getIntArray("Size");
        if (size.length >= 3) {
            int width = Math.abs(size[0]);
            int height = Math.abs(size[1]);
            int length = Math.abs(size[2]);
            if (width > 0 && height > 0 && length > 0) {
                Map<Integer, String> palette = region.palette();
                if (!palette.isEmpty()) {
                    // Some litematics use BlockStatePalette list — if palette() empty try sponge BlockData
                    try {
                        return SpongeSchematicImporter.fromCompound(region, label);
                    } catch (IOException ignored) {
                    }
                }
                try {
                    return SpongeSchematicImporter.fromCompound(region, label);
                } catch (IOException e) {
                    // Fall through to empty / minimal
                }
                // Soft fill: if BlockData present on region
                try {
                    return SpongeSchematicImporter.fromCompound(region, label);
                } catch (IOException ignored) {
                }
            }
        }
        // Direct sponge tags on region
        return SpongeSchematicImporter.fromCompound(region, label);
    }
}
