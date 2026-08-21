package com.yapcore.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Manages jar plugins in the plugins directory (add / remove / list).
 */
public final class PluginManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.Plugins");

    private final Path pluginsDir;
    private final CopyOnWriteArrayList<Consumer<List<PluginInfo>>> listeners = new CopyOnWriteArrayList<>();

    public PluginManager(Path pluginsDir) {
        this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir");
    }

    public Path getPluginsDir() {
        return pluginsDir;
    }

    public void addListener(Consumer<List<PluginInfo>> listener) {
        listeners.add(listener);
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(pluginsDir);
    }

    public List<PluginInfo> listPlugins() {
        List<PluginInfo> list = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir)) {
            return list;
        }
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            stream.filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") || name.endsWith(".yap");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        try {
                            list.add(PluginInfo.fromPath(p));
                        } catch (IOException e) {
                            LOG.warning("Could not read plugin " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.warning("Could not list plugins: " + e.getMessage());
        }
        return list;
    }

    /**
     * Copies a plugin jar into the plugins folder.
     *
     * @return installed plugin info
     */
    public PluginInfo addPlugin(Path sourceJar) throws IOException {
        ensureDirectory();
        if (!Files.isRegularFile(sourceJar)) {
            throw new IOException("Not a file: " + sourceJar);
        }
        String name = sourceJar.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")
                && !name.toLowerCase(Locale.ROOT).endsWith(".yap")) {
            throw new IOException("Plugin must be a .jar or .yap file");
        }
        Path dest = pluginsDir.resolve(name);
        Files.copy(sourceJar, dest, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Installed plugin: " + name);
        PluginInfo info = PluginInfo.fromPath(dest);
        fireChanged();
        return info;
    }

    public boolean removePlugin(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid plugin name");
        }
        Path root = pluginsDir.toAbsolutePath().normalize();
        Path absolute = root.resolve(fileName).normalize();
        if (!absolute.startsWith(root)) {
            throw new IOException("Invalid plugin path");
        }
        if (!Files.exists(absolute)) {
            return false;
        }
        Files.delete(absolute);
        LOG.info("Removed plugin: " + fileName);
        fireChanged();
        return true;
    }

    public void refresh() {
        fireChanged();
    }

    private void fireChanged() {
        List<PluginInfo> snapshot = listPlugins();
        for (Consumer<List<PluginInfo>> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    public record PluginInfo(String fileName, Path path, long sizeBytes, long lastModifiedEpochMs) {
        public static PluginInfo fromPath(Path path) throws IOException {
            return new PluginInfo(
                    path.getFileName().toString(),
                    path,
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()
            );
        }

        public String sizeLabel() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            }
            if (sizeBytes < 1024 * 1024) {
                return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0);
            }
            return String.format(Locale.ROOT, "%.2f MB", sizeBytes / (1024.0 * 1024.0));
        }
    }
}
