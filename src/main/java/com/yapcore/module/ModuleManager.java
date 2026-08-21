package com.yapcore.module;

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

/** Disk manager for {@code modules/} jars (add / remove / list). */
public final class ModuleManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.Modules");

    private final Path modulesDir;
    private final CopyOnWriteArrayList<Consumer<List<ModuleInfo>>> listeners = new CopyOnWriteArrayList<>();

    public ModuleManager(Path modulesDir) {
        this.modulesDir = Objects.requireNonNull(modulesDir);
    }

    public Path getModulesDir() {
        return modulesDir;
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(modulesDir);
    }

    public void addListener(Consumer<List<ModuleInfo>> listener) {
        listeners.add(listener);
    }

    public List<ModuleInfo> listModules() {
        List<ModuleInfo> list = new ArrayList<>();
        if (!Files.isDirectory(modulesDir)) {
            return list;
        }
        try (Stream<Path> stream = Files.list(modulesDir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") || n.endsWith(".yapmod");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        try {
                            list.add(ModuleInfo.fromPath(p));
                        } catch (IOException e) {
                            LOG.warning("Could not read module " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.warning("Could not list modules: " + e.getMessage());
        }
        return list;
    }

    public ModuleInfo addModule(Path sourceJar) throws IOException {
        ensureDirectory();
        if (!Files.isRegularFile(sourceJar)) {
            throw new IOException("Not a file: " + sourceJar);
        }
        String name = sourceJar.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar") && !lower.endsWith(".yapmod")) {
            throw new IOException("Module must be .jar or .yapmod");
        }
        Path dest = modulesDir.resolve(name);
        Files.copy(sourceJar, dest, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Installed module: " + name);
        ModuleInfo info = ModuleInfo.fromPath(dest);
        fireChanged();
        return info;
    }

    public boolean removeModule(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid module name");
        }
        Path root = modulesDir.toAbsolutePath().normalize();
        Path absolute = root.resolve(fileName).normalize();
        if (!absolute.startsWith(root)) {
            throw new IOException("Invalid module path");
        }
        if (!Files.exists(absolute)) {
            return false;
        }
        Files.delete(absolute);
        LOG.info("Removed module: " + fileName);
        fireChanged();
        return true;
    }

    public void refresh() {
        fireChanged();
    }

    private void fireChanged() {
        List<ModuleInfo> snapshot = listModules();
        for (Consumer<List<ModuleInfo>> l : listeners) {
            l.accept(snapshot);
        }
    }

    public record ModuleInfo(String fileName, Path path, long sizeBytes, long lastModifiedEpochMs) {
        public static ModuleInfo fromPath(Path path) throws IOException {
            return new ModuleInfo(
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
