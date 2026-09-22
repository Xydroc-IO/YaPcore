package com.yapcore.mobs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Sidecar YAML so one plugin jar can keep two independent configs. */
public final class YamlConfigFile {

    private final Logger logger;
    private final File file;
    private FileConfiguration config = new YamlConfiguration();

    private YamlConfigFile(Logger logger, File file) {
        this.logger = logger;
        this.file = file;
    }

    /**
     * Load {@code fileName} under the host data folder. If it is missing, copy
     * {@code plugins/<legacyDir>/config.yml} when that still exists, otherwise the jar resource.
     */
    public static YamlConfigFile open(JavaPlugin host, String resource, String fileName, String legacyDir) {
        if (!host.getDataFolder().isDirectory() && !host.getDataFolder().mkdirs()) {
            host.getLogger().warning("Could not create " + host.getDataFolder());
        }
        File dest = new File(host.getDataFolder(), fileName);
        if (!dest.isFile()) {
            File legacy = new File(host.getDataFolder().getParentFile(), legacyDir + "/config.yml");
            if (legacy.isFile()) {
                try {
                    Files.copy(legacy.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    host.getLogger().info("Migrated " + legacy + " → " + dest);
                } catch (IOException e) {
                    host.getLogger().warning("Could not migrate " + legacy + ": " + e.getMessage());
                }
            }
            if (!dest.isFile() && host.getResource(resource) != null) {
                host.saveResource(resource, false);
            }
        }
        YamlConfigFile yaml = new YamlConfigFile(host.getLogger(), dest);
        yaml.reloadConfig();
        return yaml;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveConfig() {
        try {
            config.save(file);
        } catch (IOException e) {
            logger.warning("Could not save " + file + ": " + e.getMessage());
        }
    }

    public Logger getLogger() {
        return logger;
    }
}
