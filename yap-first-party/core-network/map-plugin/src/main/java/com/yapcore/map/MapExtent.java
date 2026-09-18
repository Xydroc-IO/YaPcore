package com.yapcore.map;

import org.bukkit.World;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chunks that already exist in Anvil region files. The map renders these, not an 8-chunk window.
 * Header-only read: a zero location entry means the chunk was never generated, so it is skipped.
 */
public final class MapExtent {

    /** [tileX, tileZ, worldChunkX, worldChunkZ] with tile indices starting at 0. */
    public record Snapshot(int minChunkX, int minChunkZ, int gridX, int gridZ, List<int[]> chunks) {

        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, List.of());
        }

        public boolean present() {
            return chunks != null && !chunks.isEmpty();
        }
    }

    private MapExtent() {
    }

    public static Snapshot scan(World world) {
        if (world == null) {
            return Snapshot.empty();
        }
        return scanRegions(regionDir(world));
    }

    public static Snapshot scanRegions(Path regionDir) {
        if (regionDir == null || !Files.isDirectory(regionDir)) {
            return Snapshot.empty();
        }
        List<int[]> found = new ArrayList<>();
        try (var stream = Files.list(regionDir)) {
            List<Path> files = stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("r.") && name.endsWith(".mca");
            }).toList();
            for (Path file : files) {
                int[] region = regionCoords(file.getFileName().toString());
                if (region == null) {
                    continue;
                }
                readRegion(file, region[0], region[1], found);
            }
        } catch (IOException e) {
            return Snapshot.empty();
        }
        if (found.isEmpty()) {
            return Snapshot.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] chunk : found) {
            minX = Math.min(minX, chunk[0]);
            minZ = Math.min(minZ, chunk[1]);
            maxX = Math.max(maxX, chunk[0]);
            maxZ = Math.max(maxZ, chunk[1]);
        }
        List<int[]> tiles = new ArrayList<>(found.size());
        for (int[] chunk : found) {
            tiles.add(new int[] {chunk[0] - minX, chunk[1] - minZ, chunk[0], chunk[1]});
        }
        tiles.sort(Comparator.comparingInt((int[] c) -> c[0]).thenComparingInt(c -> c[1]));
        return new Snapshot(minX, minZ, maxX - minX + 1, maxZ - minZ + 1, List.copyOf(tiles));
    }

    static Path regionDir(World world) {
        Path folder = world.getWorldFolder().toPath();
        Path direct = folder.resolve("region");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        String dim = switch (world.getEnvironment()) {
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
        Path nested = folder.resolve("dimensions").resolve("minecraft").resolve(dim).resolve("region");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return direct;
    }

    private static int[] regionCoords(String fileName) {
        if (fileName.length() < 6) {
            return null;
        }
        String body = fileName.substring(2, fileName.length() - 4);
        int split = body.lastIndexOf('.');
        if (split <= 0 || split >= body.length() - 1) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(body.substring(0, split)),
                    Integer.parseInt(body.substring(split + 1))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void readRegion(Path file, int regionX, int regionZ, List<int[]> out) throws IOException {
        byte[] header = new byte[4096];
        try (InputStream in = Files.newInputStream(file)) {
            int read = 0;
            while (read < header.length) {
                int n = in.read(header, read, header.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read < header.length) {
                return;
            }
        }
        for (int i = 0; i < 1024; i++) {
            int b0 = header[i * 4] & 0xff;
            int b1 = header[i * 4 + 1] & 0xff;
            int b2 = header[i * 4 + 2] & 0xff;
            int offset = (b0 << 16) | (b1 << 8) | b2;
            if (offset == 0) {
                continue;
            }
            int localX = i & 31;
            int localZ = i >>> 5;
            out.add(new int[] {regionX * 32 + localX, regionZ * 32 + localZ});
        }
    }
}
