package com.yapcore.yap420.persist;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Level;

/** Atomic YAML write helper (call from HEAVY / async). */
public final class StoreIo {

    private StoreIo() {
    }

    public static void saveAtomic(JavaPlugin plugin, File target, YamlConfiguration yaml) {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            plugin.getLogger().warning("Cannot create " + parent);
            return;
        }
        Path tmp = target.toPath().resolveSibling(target.getName() + "." + UUID.randomUUID() + ".tmp");
        try {
            yaml.save(tmp.toFile());
            try {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed saving " + target.getName(), e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    public static YamlConfiguration load(File file) {
        if (!file.isFile()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
