package com.yapcore.fleet.local;

import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.plugin.PluginManager;
import com.yapcore.plugin.YapPluginControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-instance plugins/ directory ops (list / hard enable / uninstall). */
public final class FleetInstancePlugins {

    private FleetInstancePlugins() {
    }

    public static Path pluginsDir(Path rootDir, FleetInstance instance) {
        return InstanceLayout.dir(rootDir, instance).resolve("plugins");
    }

    public static List<Map<String, Object>> list(Path rootDir, FleetInstance instance)
            throws IOException {
        Path dir = pluginsDir(rootDir, instance);
        Files.createDirectories(dir);
        YapPluginControl ctrl = new YapPluginControl(rootDir, new PluginManager(dir));
        return ctrl.listDetailed();
    }

    public static Map<String, Object> setHardEnabled(
            Path rootDir, FleetInstance instance, String fileName, boolean enable)
            throws IOException {
        Path dir = pluginsDir(rootDir, instance);
        YapPluginControl ctrl = new YapPluginControl(rootDir, new PluginManager(dir));
        return ctrl.setEnabled(fileName, enable, YapPluginControl.Mode.HARD, false);
    }

    public static Map<String, Object> uninstall(
            Path rootDir, FleetInstance instance, String fileName) throws IOException {
        Path dir = pluginsDir(rootDir, instance);
        Path target = resolveJar(dir, fileName);
        Files.deleteIfExists(target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "uninstall");
        out.put("instanceId", instance.id());
        out.put("fileName", target.getFileName().toString());
        return out;
    }

    public static Map<String, Object> installFromCatalog(
            Path rootDir, FleetInstance instance, String jarName) throws IOException {
        String name = requireJarName(jarName);
        Path src = rootDir.resolve("plugins").resolve(name);
        if (!Files.isRegularFile(src)) {
            throw new IOException("Catalog jar missing: " + src);
        }
        Path destDir = pluginsDir(rootDir, instance);
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(name);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "install");
        out.put("instanceId", instance.id());
        out.put("fileName", name);
        out.put("path", dest.toString());
        out.put("sizeBytes", Files.size(dest));
        return out;
    }

    static Path resolveJar(Path pluginsDir, String fileName) throws IOException {
        String name = requireJarName(fileName);
        Path jar = pluginsDir.resolve(name);
        Path disabled = pluginsDir.resolve(name.endsWith(".disabled") ? name : name + ".disabled");
        if (Files.isRegularFile(jar)) {
            return jar;
        }
        if (Files.isRegularFile(disabled)) {
            return disabled;
        }
        // allow activeName without .jar.disabled suffix when disabled on disk
        if (!name.toLowerCase(Locale.ROOT).endsWith(".disabled")) {
            Path alt = pluginsDir.resolve(name + ".disabled");
            if (Files.isRegularFile(alt)) {
                return alt;
            }
        }
        throw new IOException("Plugin not found on instance: " + name);
    }

    static String requireJarName(String jarName) throws IOException {
        if (jarName == null || jarName.isBlank()) {
            throw new IOException("jar name required");
        }
        String name = Path.of(jarName.trim()).getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jar") || lower.endsWith(".jar.disabled"))) {
            throw new IOException("expected .jar file name: " + name);
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IOException("invalid jar name: " + name);
        }
        return name;
    }
}
