package org.bukkit.configuration.file;

import org.bukkit.configuration.ConfigurationSection;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * YAML config used by virtually every Bukkit/Paper plugin.
 */
public class YamlConfiguration extends FileConfiguration {

    public YamlConfiguration() {
        super();
    }

    public static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            if (file != null && file.exists()) {
                cfg.load(file);
            }
        } catch (IOException e) {
            // leave empty
        }
        return cfg;
    }

    public static YamlConfiguration loadConfiguration(InputStreamReader reader) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    cfg.root.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } catch (Exception ignored) {
        }
        return cfg;
    }

    public static YamlConfiguration loadConfiguration(InputStream stream) {
        return loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
