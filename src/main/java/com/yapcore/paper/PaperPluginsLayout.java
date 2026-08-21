package com.yapcore.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Single operator-facing plugins folder: {@code <root>/plugins}.
 * When {@code paper-dir} is a direct child of the project root (e.g. {@code paper-kernel}),
 * {@code <paper-dir>/plugins} is a symlink to that folder so Paper and YaP share one directory.
 * Nested/isolated paper dirs (bench workdirs) keep a local {@code plugins/} and are not linked.
 */
public final class PaperPluginsLayout {

    private static final Logger LOG = Logger.getLogger("YaPcore.Plugins");

    private PaperPluginsLayout() {
    }

    /**
     * Ensures the operator plugins dir and, for the default paper layout, links
     * {@code paperDir/plugins} → root {@code plugins/}.
     *
     * @return the directory Paper should load plugins from (unified or isolated)
     */
    public static Path ensureUnified(Path rootDir, Path paperDir) throws IOException {
        Path root = rootDir.toAbsolutePath().normalize();
        Path paper = paperDir.toAbsolutePath().normalize();
        Path unified = root.resolve("plugins").normalize();
        Files.createDirectories(unified);
        Files.createDirectories(paper);

        // Only unify when paper-dir sits next to plugins/ (paper-kernel under root).
        // Bench/smoke workdirs stay isolated under their own plugins/.
        if (paper.getParent() == null || !paper.getParent().equals(root)) {
            Path local = paper.resolve("plugins");
            Files.createDirectories(local);
            LOG.fine("Isolated paper-dir plugins (no root link): " + local);
            return local;
        }

        Path paperPlugins = paper.resolve("plugins");
        if (Files.isSymbolicLink(paperPlugins)) {
            Path target = paperPlugins.toRealPath().normalize();
            if (!target.equals(unified)) {
                LOG.warning("paper-dir/plugins symlink points to " + target
                        + " — expected " + unified + "; recreating");
                Files.delete(paperPlugins);
                createLink(paper, paperPlugins, unified);
            }
            return unified;
        }

        if (Files.isDirectory(paperPlugins)) {
            migrateJars(paperPlugins, unified);
            deleteDirectoryContents(paperPlugins);
            Files.deleteIfExists(paperPlugins);
        } else {
            Files.deleteIfExists(paperPlugins);
        }

        createLink(paper, paperPlugins, unified);
        LOG.info("Unified plugins folder: " + unified
                + " (Paper sees " + paperPlugins + " → " + paper.relativize(unified) + ")");
        return unified;
    }

    private static void createLink(Path paperDir, Path paperPlugins, Path unified)
            throws IOException {
        Path relative = paperDir.relativize(unified);
        Files.createSymbolicLink(paperPlugins, relative);
    }

    private static void migrateJars(Path from, Path to) throws IOException {
        try (Stream<Path> stream = Files.list(from)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".jar") || n.endsWith(".yap");
            }).forEach(p -> {
                try {
                    Path dest = to.resolve(p.getFileName());
                    Files.move(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    LOG.info("Migrated plugin " + p.getFileName() + " → " + dest);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Could not migrate " + p, e);
                }
            });
        }
    }

    private static void deleteDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOG.fine("cleanup " + p + ": " + e.getMessage());
                        }
                    });
        }
    }
}
