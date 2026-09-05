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
import java.util.List;

/**
 * Tile retention: delete PNGs older than max-age-days and/or trim oldest until under max-disk-mb.
 */
public final class TilePruner {

    private TilePruner() {
    }

    public record Result(long deletedFiles, long freedBytes, long remainingBytes) {
    }

    public static Result prune(Path tilesRoot, int maxAgeDays, int maxDiskMb) throws IOException {
        if (!Files.isDirectory(tilesRoot)) {
            return new Result(0, 0, 0);
        }
        List<TileFile> files = new ArrayList<>();
        Files.walkFileTree(tilesRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().endsWith(".png")) {
                    return FileVisitResult.CONTINUE;
                }
                files.add(new TileFile(file, attrs.size(), attrs.lastModifiedTime()));
                return FileVisitResult.CONTINUE;
            }
        });

        long deleted = 0;
        long freed = 0;
        long total = files.stream().mapToLong(TileFile::size).sum();
        long now = System.currentTimeMillis();
        long ageCutoff = maxAgeDays > 0 ? now - maxAgeDays * 86_400_000L : Long.MIN_VALUE;

        if (maxAgeDays > 0) {
            for (TileFile tf : files) {
                if (tf.mtime().toMillis() < ageCutoff && Files.deleteIfExists(tf.path())) {
                    deleted++;
                    freed += tf.size();
                    total -= tf.size();
                    tf.deleted = true;
                }
            }
        }

        if (maxDiskMb > 0) {
            long budget = maxDiskMb * 1024L * 1024L;
            if (total > budget) {
                List<TileFile> remaining = files.stream()
                        .filter(f -> !f.deleted)
                        .sorted(Comparator.comparingLong(f -> f.mtime().toMillis()))
                        .toList();
                for (TileFile f : remaining) {
                    if (total <= budget) {
                        break;
                    }
                    if (Files.deleteIfExists(f.path())) {
                        deleted++;
                        freed += f.size();
                        total -= f.size();
                        f.deleted = true;
                    }
                }
            }
        }

        return new Result(deleted, freed, Math.max(0, total));
    }

    private static final class TileFile {
        final Path path;
        final long size;
        final FileTime mtime;
        boolean deleted;

        TileFile(Path path, long size, FileTime mtime) {
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
