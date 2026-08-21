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
 * Minimal YAML-backed configuration compatible with typical plugin getConfig() usage.
 */
public class FileConfiguration implements ConfigurationSection {

    protected final Map<String, Object> root = new LinkedHashMap<>();
    private ConfigurationSection parent;
    private String pathName = "";

    public FileConfiguration() {
    }

    protected FileConfiguration(Map<String, Object> root, ConfigurationSection parent, String pathName) {
        this.root.putAll(root);
        this.parent = parent;
        this.pathName = pathName;
    }

    public void load(File file) throws IOException {
        root.clear();
        if (!file.exists()) {
            return;
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            Object loaded = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (loaded instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    root.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
    }

    public void save(File file) throws IOException {
        if (file.getParentFile() != null) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        Files.writeString(file.toPath(), new Yaml().dump(root), StandardCharsets.UTF_8);
    }

    public void save(String file) throws IOException {
        save(new File(file));
    }

    @Override
    public Object get(String path) {
        return get(path, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(String path, Object def) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof Map<?, ?> map)) {
                return def;
            }
            cur = map.get(part);
            if (cur == null) {
                return def;
            }
        }
        return cur;
    }

    @Override
    public String getString(String path) {
        Object v = get(path);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    public String getString(String path, String def) {
        Object v = get(path, def);
        return v == null ? def : String.valueOf(v);
    }

    @Override
    public int getInt(String path) {
        return getInt(path, 0);
    }

    @Override
    public int getInt(String path, int def) {
        Object v = get(path, def);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object v = get(path, def);
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @Override
    public double getDouble(String path) {
        return getDouble(path, 0d);
    }

    @Override
    public double getDouble(String path, double def) {
        Object v = get(path, def);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public long getLong(String path) {
        return getLong(path, 0L);
    }

    @Override
    public long getLong(String path, long def) {
        Object v = get(path, def);
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = get(path);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(parts[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        if (value == null) {
            cur.remove(parts[parts.length - 1]);
        } else {
            cur.put(parts[parts.length - 1], value);
        }
    }

    @Override
    public boolean contains(String path) {
        return get(path) != null;
    }

    @Override
    public boolean isSet(String path) {
        return contains(path);
    }

    @Override
    public void addDefault(String path, Object value) {
        if (!contains(path)) {
            set(path, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConfigurationSection getConfigurationSection(String path) {
        Object v = get(path);
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> cast = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                cast.put(String.valueOf(e.getKey()), e.getValue());
            }
            return new FileConfiguration(cast, this, path);
        }
        return null;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        if (!deep) {
            return new LinkedHashSet<>(root.keySet());
        }
        Set<String> keys = new LinkedHashSet<>();
        collectKeys("", root, keys);
        return keys;
    }

    @SuppressWarnings("unchecked")
    private void collectKeys(String prefix, Map<String, Object> map, Set<String> keys) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String p = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            keys.add(p);
            if (e.getValue() instanceof Map<?, ?> child) {
                Map<String, Object> cast = new LinkedHashMap<>();
                for (Map.Entry<?, ?> ce : child.entrySet()) {
                    cast.put(String.valueOf(ce.getKey()), ce.getValue());
                }
                collectKeys(p, cast, keys);
            }
        }
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        if (!deep) {
            return Collections.unmodifiableMap(root);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : getKeys(true)) {
            out.put(k, get(k));
        }
        return out;
    }

    @Override
    public String getName() {
        return pathName.contains(".") ? pathName.substring(pathName.lastIndexOf('.') + 1) : pathName;
    }

    @Override
    public String getCurrentPath() {
        return pathName;
    }

    @Override
    public ConfigurationSection getParent() {
        return parent;
    }
}
