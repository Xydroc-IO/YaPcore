package com.yapcore.map;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime render telemetry written for the dashboard snapshot. */
public final class MapTelemetry {

    private static final AtomicReference<String> status = new AtomicReference<>("idle");
    private static final AtomicReference<String> lastRenderIso = new AtomicReference<>("");
    private static final AtomicLong lastRenderEpochMs = new AtomicLong(0L);
    private static final AtomicInteger dirtyChunkCount = new AtomicInteger(0);
    private static final AtomicLong tileDiskBytes = new AtomicLong(0L);
    private static final AtomicLong meshDiskBytes = new AtomicLong(0L);
    private static final AtomicInteger meshChunkCount = new AtomicInteger(0);
    private static volatile Path statusFile;

    private MapTelemetry() {
    }

    public static void bind(Path dataFolder) {
        if (dataFolder != null) {
            statusFile = dataFolder.resolve("render-status.json");
        }
    }

    public static void setStatus(String value) {
        status.set(value == null ? "idle" : value);
        writeStatusFile();
    }

    public static void markRenderComplete(Path tilesRoot, int dirtyRemaining) {
        markRenderComplete(tilesRoot, null, dirtyRemaining);
    }

    public static void markRenderComplete(Path tilesRoot, Path meshesRoot, int dirtyRemaining) {
        lastRenderEpochMs.set(System.currentTimeMillis());
        lastRenderIso.set(Instant.now().toString());
        dirtyChunkCount.set(Math.max(0, dirtyRemaining));
        tileDiskBytes.set(measureDiskBytes(tilesRoot, ".png"));
        if (meshesRoot != null) {
            MeshStats stats = measureMeshStats(meshesRoot);
            meshDiskBytes.set(stats.bytes);
            meshChunkCount.set(stats.chunks);
        }
        status.set("idle");
        writeStatusFile();
    }

    public static void updateDirty(int count) {
        dirtyChunkCount.set(Math.max(0, count));
    }

    public static void refreshDiskBytes(Path tilesRoot) {
        refreshDiskBytes(tilesRoot, null);
    }

    public static void refreshDiskBytes(Path tilesRoot, Path meshesRoot) {
        tileDiskBytes.set(measureDiskBytes(tilesRoot, ".png"));
        if (meshesRoot != null) {
            MeshStats stats = measureMeshStats(meshesRoot);
            meshDiskBytes.set(stats.bytes);
            meshChunkCount.set(stats.chunks);
        }
        writeStatusFile();
    }

    public static void writeStatusFile() {
        Path out = statusFile;
        if (out == null) {
            return;
        }
        String json = "{"
                + "\"renderStatus\":" + q(status.get()) + ","
                + "\"lastRenderTime\":" + q(lastRenderIso.get()) + ","
                + "\"lastRenderEpochMs\":" + lastRenderEpochMs.get() + ","
                + "\"dirtyChunkCount\":" + dirtyChunkCount.get() + ","
                + "\"tileDiskBytes\":" + tileDiskBytes.get() + ","
                + "\"meshDiskBytes\":" + meshDiskBytes.get() + ","
                + "\"meshChunkCount\":" + meshChunkCount.get()
                + "}\n";
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, json);
        } catch (IOException ignored) {
            // dashboard falls back to missing fields
        }
    }

    private static long measureDiskBytes(Path root, String suffix) {
        if (root == null || !Files.isDirectory(root)) {
            return 0L;
        }
        AtomicLong total = new AtomicLong();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(suffix)) {
                        total.addAndGet(attrs.size());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return total.get();
        }
        return total.get();
    }

    private static MeshStats measureMeshStats(Path meshesRoot) {
        if (meshesRoot == null || !Files.isDirectory(meshesRoot)) {
            return new MeshStats(0L, 0);
        }
        AtomicLong bytes = new AtomicLong();
        AtomicInteger chunks = new AtomicInteger();
        try {
            Files.walkFileTree(meshesRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".json") && !"manifest.json".equals(name)) {
                        bytes.addAndGet(attrs.size());
                        chunks.incrementAndGet();
                    } else if (name.endsWith(".ymesh")) {
                        bytes.addAndGet(attrs.size());
                    } else if ("manifest.json".equals(name)) {
                        bytes.addAndGet(attrs.size());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return new MeshStats(bytes.get(), chunks.get());
        }
        return new MeshStats(bytes.get(), chunks.get());
    }

    private record MeshStats(long bytes, int chunks) {
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
