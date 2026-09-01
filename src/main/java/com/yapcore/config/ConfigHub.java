package com.yapcore.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Operator-facing config hub under {@code <root>/config}.
 * <ul>
 *   <li>{@code server.properties} — YaPcore / YapEngine product</li>
 *   <li>{@code paper/} — symlink to {@code <paper-dir>/config} (Paper globals)</li>
 *   <li>{@code spigot.yml}, {@code bukkit.yml} — symlinks into paper-dir when present</li>
 * </ul>
 */
public final class ConfigHub {

    private static final Logger LOG = Logger.getLogger("YaPcore.ConfigHub");

    private ConfigHub() {
    }

    public static Path ensure(Path rootDir, Path paperDir) throws IOException {
        Path hub = rootDir.resolve("config").toAbsolutePath().normalize();
        Files.createDirectories(hub);
        Path paper = paperDir.toAbsolutePath().normalize();
        Files.createDirectories(paper.resolve("config"));

        linkDir(hub.resolve("paper"), paper.resolve("config"));
        linkFile(hub.resolve("spigot.yml"), paper.resolve("spigot.yml"));
        linkFile(hub.resolve("bukkit.yml"), paper.resolve("bukkit.yml"));
        linkFile(hub.resolve("commands.yml"), paper.resolve("commands.yml"));
        linkFile(hub.resolve("paper-server.properties"), paper.resolve("server.properties"));

        Path readme = hub.resolve("README.md");
        if (!Files.isRegularFile(readme)) {
            Files.writeString(readme, DEFAULT_README);
        }
        LOG.info("Config hub ready: " + hub + " (paper/ → " + paper.resolve("config") + ")");
        return hub;
    }

    private static void linkDir(Path link, Path target) throws IOException {
        Files.createDirectories(target);
        if (Files.isSymbolicLink(link)) {
            Path resolved = link.toRealPath().normalize();
            if (!resolved.equals(target.normalize())) {
                Files.delete(link);
                Files.createSymbolicLink(link, link.getParent().relativize(target));
            }
            return;
        }
        if (Files.isDirectory(link) && !Files.isSymbolicLink(link)) {
            try (Stream<Path> s = Files.list(link)) {
                if (s.findAny().isEmpty()) {
                    Files.delete(link);
                } else {
                    LOG.warning("config/paper exists as a real directory — leave as-is; prefer symlink to "
                            + target);
                    return;
                }
            }
        }
        if (!Files.exists(link)) {
            Files.createSymbolicLink(link, link.getParent().relativize(target));
        }
    }

    private static void linkFile(Path link, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return;
        }
        if (Files.isSymbolicLink(link) || Files.exists(link)) {
            return;
        }
        Files.createSymbolicLink(link, link.getParent().relativize(target));
    }

    private static final String DEFAULT_README = """
            # YaPcore config hub

            Edit **here** — do not hunt under `paper-kernel/` for day-to-day tuning.

            | Path | What |
            |------|------|
            | `server.properties` | YaP product (ports, dual-stack, Phase 3 flags, packs) |
            | `paper/` | Paper `paper-global.yml`, `paper-world-defaults.yml`, … |
            | `spigot.yml` / `bukkit.yml` | Classic Spigot/Bukkit (symlinks into paper-dir) |
            | `paper-server.properties` | Vanilla-style props Paper reads (symlink) |

            **Purpur-class mob / gameplay encyclopedia:** drop
            `yap-gameplay-knobs.jar` into `plugins/` and edit
            `plugins/YaPGameplayKnobs/knobs.yml` (or GUI → Tune).

            See `docs/ops/TUNE.md`.
            """;
}
