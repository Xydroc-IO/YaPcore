package com.yapcore.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Encodes chunk meshes to compact JSON / binary and writes world manifests.
 *
 * <p>FORMAT_VERSION 2 uses greedy/model boxes under
 * {@code meshes/{world}/{layer}/lod{N}/} (milliblock units, field {@code u}).
 * JSON remains for compatibility; {@code .ymesh} is preferred by the viewer.
 */
public final class MeshEncoder {

    /** Current write version (greedy / model boxes). Readers must accept v1 and v2. */
    public static final int FORMAT_VERSION = ChunkMeshData.FORMAT_V2;

    private MeshEncoder() {
    }

    public static Path lodDir(Path meshesRoot, String worldName, String layer, int lod) {
        return layerDir(meshesRoot, worldName, layer).resolve("lod" + Math.max(0, lod));
    }

    public static Path meshPath(Path meshesRoot, String worldName, String layer, int lod,
                                int chunkX, int chunkZ) {
        return lodDir(meshesRoot, worldName, layer, lod).resolve(chunkX + "_" + chunkZ + ".json");
    }

    public static Path meshBinaryPath(Path meshesRoot, String worldName, String layer, int lod,
                                      int chunkX, int chunkZ) {
        return lodDir(meshesRoot, worldName, layer, lod)
                .resolve(chunkX + "_" + chunkZ + MeshBinaryCodec.EXTENSION);
    }

    /** LOD0 JSON path (default). */
    public static Path meshPath(Path meshesRoot, String worldName, String layer, int chunkX, int chunkZ) {
        return meshPath(meshesRoot, worldName, layer, 0, chunkX, chunkZ);
    }

    /** Legacy flat path (pre-layer): {@code {world}/{cx}_{cz}.json}. */
    public static Path meshPath(Path meshesRoot, String worldName, int chunkX, int chunkZ) {
        return meshesRoot.resolve(worldName).resolve(chunkX + "_" + chunkZ + ".json");
    }

    public static Path manifestPath(Path meshesRoot, String worldName, String layer) {
        return layerDir(meshesRoot, worldName, layer).resolve("manifest.json");
    }

    public static Path manifestPath(Path meshesRoot, String worldName) {
        return meshesRoot.resolve(worldName).resolve("manifest.json");
    }

    public static Path layerDir(Path meshesRoot, String worldName, String layer) {
        String safe = MapLayerSampler.normalizeMeshLayer(layer);
        return meshesRoot.resolve(worldName).resolve(safe);
    }

    /**
     * Compact JSON v2 (milliblocks):
     * {@code {"v":2,"u":1000,"cx":N,"cz":N,"n":N,"d":[lx,y,lz,sx,sy,sz,rgb,…]}}.
     */
    public static String encode(ChunkMeshData data) {
        int version = data.formatVersion() >= ChunkMeshData.FORMAT_V2
                ? FORMAT_VERSION
                : ChunkMeshData.FORMAT_V1;
        StringBuilder sb = new StringBuilder(80 + data.packedLength() * 8);
        sb.append("{\"v\":").append(version);
        if (version >= ChunkMeshData.FORMAT_V2) {
            sb.append(",\"u\":").append(MeshUnits.UNIT);
        }
        sb.append(",\"cx\":").append(data.chunkX())
                .append(",\"cz\":").append(data.chunkZ())
                .append(",\"n\":").append(data.blockCount())
                .append(",\"d\":[");
        int[] p = data.packed();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(p[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    public static Path writeChunk(Path meshesRoot, String worldName, String layer, ChunkMeshData data)
            throws IOException {
        return writeChunk(meshesRoot, worldName, layer, 0, data, true);
    }

    public static Path writeChunk(Path meshesRoot, String worldName, String layer, int lod,
                                  ChunkMeshData data, boolean writeBinary) throws IOException {
        Path out = meshPath(meshesRoot, worldName, layer, lod, data.chunkX(), data.chunkZ());
        Files.createDirectories(out.getParent());
        Files.writeString(out, encode(data), StandardCharsets.UTF_8);
        if (writeBinary) {
            Path bin = meshBinaryPath(meshesRoot, worldName, layer, lod, data.chunkX(), data.chunkZ());
            Files.write(bin, MeshBinaryCodec.encode(data));
        }
        return out;
    }

    /** @deprecated prefer {@link #writeChunk(Path, String, String, ChunkMeshData)} with a layer */
    @Deprecated
    public static Path writeChunk(Path meshesRoot, String worldName, ChunkMeshData data) throws IOException {
        return writeChunk(meshesRoot, worldName, MapLayerSampler.LAYER_FULL, data);
    }

    /**
     * Write LOD0 + derived LOD1/LOD2 for one chunk.
     */
    public static void writeChunkLods(Path meshesRoot, String worldName, String layer,
                                      ChunkMeshData lod0, int maxLod, boolean writeBinary)
            throws IOException {
        int capped = Math.max(0, Math.min(2, maxLod));
        writeChunk(meshesRoot, worldName, layer, 0, lod0, writeBinary);
        if (capped >= 1) {
            writeChunk(meshesRoot, worldName, layer, 1, LodMesher.buildLod1(lod0), writeBinary);
        }
        if (capped >= 2) {
            writeChunk(meshesRoot, worldName, layer, 2, LodMesher.buildLod2(lod0), writeBinary);
        }
    }

    /**
     * Rebuild manifest from on-disk chunk mesh files for one world layer (all LODs).
     */
    public static Path writeManifest(Path meshesRoot, String worldName, String layer,
                                     int originChunkX, int originChunkZ, int radius)
            throws IOException {
        return writeManifest(meshesRoot, worldName, layer, originChunkX, originChunkZ, radius, 2);
    }

    public static Path writeManifest(Path meshesRoot, String worldName, String layer,
                                     int originChunkX, int originChunkZ, int radius, int maxLod)
            throws IOException {
        Path dir = layerDir(meshesRoot, worldName, layer);
        Files.createDirectories(dir);
        Map<String, ChunkEntry> byKey = new HashMap<>();
        int capped = Math.max(0, Math.min(2, maxLod));
        for (int lod = 0; lod <= capped; lod++) {
            Path lodPath = lodDir(meshesRoot, worldName, layer, lod);
            if (!Files.isDirectory(lodPath)) {
                // Fall back: flat layer dir (pre-LOD layout)
                if (lod == 0) {
                    mergeChunkFiles(dir, 0, byKey);
                }
                continue;
            }
            mergeChunkFiles(lodPath, lod, byKey);
        }
        List<ChunkEntry> entries = new ArrayList<>(byKey.values());
        entries.sort(Comparator.comparingInt((ChunkEntry e) -> e.cx).thenComparingInt(e -> e.cz));
        String safeLayer = MapLayerSampler.normalizeMeshLayer(layer);
        StringBuilder sb = new StringBuilder(256 + entries.size() * 64);
        sb.append("{\"v\":").append(FORMAT_VERSION)
                .append(",\"u\":").append(MeshUnits.UNIT)
                .append(",\"world\":").append(jsonString(worldName))
                .append(",\"layer\":").append(jsonString(safeLayer))
                .append(",\"originChunkX\":").append(originChunkX)
                .append(",\"originChunkZ\":").append(originChunkZ)
                .append(",\"radius\":").append(radius)
                .append(",\"maxLod\":").append(capped)
                .append(",\"binary\":true")
                .append(",\"updated\":").append(jsonString(Instant.now().toString()))
                .append(",\"chunks\":[");
        for (int i = 0; i < entries.size(); i++) {
            ChunkEntry e = entries.get(i);
            if (i > 0) {
                sb.append(',');
            }
            String stem = e.cx + "_" + e.cz;
            sb.append("{\"cx\":").append(e.cx)
                    .append(",\"cz\":").append(e.cz)
                    .append(",\"file\":").append(jsonString("lod0/" + stem + ".json"))
                    .append(",\"ymesh\":").append(jsonString("lod0/" + stem + MeshBinaryCodec.EXTENSION))
                    .append(",\"blocks\":").append(e.blocks)
                    .append(",\"lods\":[");
            boolean first = true;
            for (int lod = 0; lod <= capped; lod++) {
                if (!e.hasLod(lod)) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(lod);
            }
            sb.append("]}");
        }
        sb.append("]}");
        Path out = manifestPath(meshesRoot, worldName, safeLayer);
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        return out;
    }

    public static Path writeManifest(Path meshesRoot, String worldName,
                                     int originChunkX, int originChunkZ, int radius)
            throws IOException {
        return writeManifest(meshesRoot, worldName, MapLayerSampler.LAYER_FULL,
                originChunkX, originChunkZ, radius);
    }

    /** Parse block/box count from encoded mesh JSON without a full JSON library. */
    public static int parseBlockCount(String json) {
        if (json == null) {
            return 0;
        }
        int i = json.indexOf("\"n\":");
        if (i < 0) {
            return 0;
        }
        int start = i + 4;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int parseFormatVersion(String json) {
        if (json == null) {
            return ChunkMeshData.FORMAT_V1;
        }
        int i = json.indexOf("\"v\":");
        if (i < 0) {
            return ChunkMeshData.FORMAT_V1;
        }
        int start = i + 4;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return ChunkMeshData.FORMAT_V1;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return ChunkMeshData.FORMAT_V1;
        }
    }

    public static int parseUnit(String json) {
        if (json == null) {
            return 1;
        }
        int i = json.indexOf("\"u\":");
        if (i < 0) {
            return 1;
        }
        int start = i + 4;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(json.substring(start, end)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Decode packed array from encode() output (test / tooling helper). */
    public static ChunkMeshData decode(String json) {
        if (json == null || json.isBlank()) {
            return new ChunkMeshData(0, 0, new int[0]);
        }
        int version = parseFormatVersion(json);
        int cx = readIntField(json, "cx");
        int cz = readIntField(json, "cz");
        int dStart = json.indexOf("\"d\":[");
        if (dStart < 0) {
            return new ChunkMeshData(cx, cz, version, new int[0]);
        }
        int arrStart = json.indexOf('[', dStart);
        int arrEnd = json.indexOf(']', arrStart);
        if (arrStart < 0 || arrEnd < 0) {
            return new ChunkMeshData(cx, cz, version, new int[0]);
        }
        String body = json.substring(arrStart + 1, arrEnd).trim();
        if (body.isEmpty()) {
            return new ChunkMeshData(cx, cz, version, new int[0]);
        }
        String[] parts = body.split(",");
        int[] packed = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            packed[i] = (int) Math.round(Double.parseDouble(parts[i].trim()));
        }
        return new ChunkMeshData(cx, cz, version, packed);
    }

    private static void mergeChunkFiles(Path dir, int lod, Map<String, ChunkEntry> byKey) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return (name.endsWith(".json") || name.endsWith(MeshBinaryCodec.EXTENSION))
                        && !"manifest.json".equals(name);
            }).forEach(p -> {
                String name = p.getFileName().toString();
                String stem;
                if (name.endsWith(MeshBinaryCodec.EXTENSION)) {
                    stem = name.substring(0, name.length() - MeshBinaryCodec.EXTENSION.length());
                } else {
                    stem = name.substring(0, name.length() - 5);
                }
                // Skip if path is under lodN as filename rather than dir
                if (stem.startsWith("lod") && stem.contains("_") && !stem.matches("-?\\d+_-?\\d+")) {
                    return;
                }
                int us = stem.indexOf('_');
                if (us <= 0) {
                    return;
                }
                try {
                    int cx = Integer.parseInt(stem.substring(0, us));
                    int cz = Integer.parseInt(stem.substring(us + 1));
                    String key = cx + "|" + cz;
                    ChunkEntry existing = byKey.get(key);
                    int blocks = existing == null ? 0 : existing.blocks;
                    if (name.endsWith(".json") && lod == 0) {
                        try {
                            blocks = parseBlockCount(Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                        }
                    }
                    if (existing == null) {
                        existing = new ChunkEntry(cx, cz, blocks);
                        byKey.put(key, existing);
                    } else if (blocks > existing.blocks) {
                        existing.blocks = blocks;
                    }
                    existing.markLod(lod);
                } catch (NumberFormatException ignored) {
                }
            });
        }
    }

    private static int readIntField(String json, String field) {
        String needle = "\"" + field + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return 0;
        }
        int start = i + needle.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') {
            end++;
        }
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class ChunkEntry {
        final int cx;
        final int cz;
        int blocks;
        int lodMask;

        ChunkEntry(int cx, int cz, int blocks) {
            this.cx = cx;
            this.cz = cz;
            this.blocks = blocks;
        }

        void markLod(int lod) {
            if (lod >= 0 && lod <= 2) {
                lodMask |= (1 << lod);
            }
        }

        boolean hasLod(int lod) {
            return (lodMask & (1 << lod)) != 0;
        }
    }
}
