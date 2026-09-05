package com.yapcore.map;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mesh retention: delete chunk JSON older than max-age-days and/or trim oldest until under max-disk-mb.
 * Skips {@code manifest.json}; callers should rebuild manifests after prune.
 */
public final class MeshPruner {

    private MeshPruner() {
    }

    public record Result(long deletedFiles, long freedBytes, long remainingBytes,
                         Set<String> affectedWorldLayers) {
        public Result(long deletedFiles, long freedBytes, long remainingBytes) {
            this(deletedFiles, freedBytes, remainingBytes, Set.of());
        }
    }

    /**
     * @return prune stats; {@code affectedWorldLayers} entries are {@code "world|layer"} for
     *         directories that lost files (so manifests can be rebuilt).
     */
    public static Result prune(Path meshesRoot, int maxAgeDays, int maxDiskMb) throws IOException {
        if (!Files.isDirectory(meshesRoot)) {
            return new Result(0, 0, 0, Set.of());
        }
        List<MeshFile> files = new ArrayList<>();
        Files.walkFileTree(meshesRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json") || "manifest.json".equals(name)) {
                    return FileVisitResult.CONTINUE;
                }
                files.add(new MeshFile(file, attrs.size(), attrs.lastModifiedTime()));
                return FileVisitResult.CONTINUE;
            }
        });

        long deleted = 0;
        long freed = 0;
        long total = files.stream().mapToLong(MeshFile::size).sum();
        long now = System.currentTimeMillis();
        long ageCutoff = maxAgeDays > 0 ? now - maxAgeDays * 86_400_000L : Long.MIN_VALUE;
        Set<String> affected = new HashSet<>();

        if (maxAgeDays > 0) {
            for (MeshFile mf : files) {
                if (mf.mtime().toMillis() < ageCutoff && Files.deleteIfExists(mf.path())) {
                    deleted++;
                    freed += mf.size();
                    total -= mf.size();
                    mf.deleted = true;
                    trackAffected(meshesRoot, mf.path(), affected);
                }
            }
        }

        if (maxDiskMb > 0) {
            long budget = maxDiskMb * 1024L * 1024L;
            if (total > budget) {
                List<MeshFile> remaining = files.stream()
                        .filter(f -> !f.deleted)
                        .sorted(Comparator.comparingLong(f -> f.mtime().toMillis()))
                        .toList();
                for (MeshFile f : remaining) {
                    if (total <= budget) {
                        break;
                    }
                    if (Files.deleteIfExists(f.path())) {
                        deleted++;
                        freed += f.size();
                        total -= f.size();
                        f.deleted = true;
                        trackAffected(meshesRoot, f.path(), affected);
                    }
                }
            }
        }

        return new Result(deleted, freed, Math.max(0, total), Set.copyOf(affected));
    }

    /**
     * Path shape: {@code meshesRoot/{world}/{layer}/{cx}_{cz}.json} or legacy
     * {@code meshesRoot/{world}/{cx}_{cz}.json}.
     */
    private static void trackAffected(Path meshesRoot, Path file, Set<String> affected) {
        try {
            Path rel = meshesRoot.relativize(file.toAbsolutePath().normalize());
            int names = rel.getNameCount();
            if (names >= 3) {
                String world = rel.getName(0).toString();
                String layer = rel.getName(1).toString();
                affected.add(world + "|" + layer);
            } else if (names >= 2) {
                String world = rel.getName(0).toString();
                affected.add(world + "|" + MapLayerSampler.LAYER_FULL);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static final class MeshFile {
        final Path path;
        final long size;
        final FileTime mtime;
        boolean deleted;

        MeshFile(Path path, long size, FileTime mtime) {
            this.path = path;
            this.size = size;
            this.mtime = mtime;
        }

        Path path() {
            return path;
        }

        long size() {
            return size;
        }

        FileTime mtime() {
            return mtime;
        }
    }
}
