package org.bukkit.plugin;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PluginDescriptionFile {
    private final String name;
    private final String main;
    private final String version;
    private final String apiVersion;
    private final String description;
    private final List<String> authors;
    private final List<String> depend;
    private final List<String> softdepend;
    private final Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public PluginDescriptionFile(InputStream stream) {
        Object loaded = new Yaml().load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid plugin.yml");
        }
        this.raw = (Map<String, Object>) map;
        this.name = String.valueOf(raw.get("name"));
        this.main = String.valueOf(raw.get("main"));
        this.version = String.valueOf(raw.getOrDefault("version", "0.0.0"));
        this.apiVersion = String.valueOf(raw.getOrDefault("api-version", "1.20"));
        this.description = String.valueOf(raw.getOrDefault("description", ""));
        List<String> authorList = asStringList(raw.get("authors"));
        if (authorList.isEmpty() && raw.get("author") != null) {
            authorList = List.of(String.valueOf(raw.get("author")));
        }
        this.authors = authorList;
        this.depend = asStringList(raw.get("depend"));
        this.softdepend = asStringList(raw.get("softdepend"));
        if (name == null || name.isBlank() || "null".equals(name)) {
            throw new IllegalArgumentException("plugin.yml missing name");
        }
        if (main == null || main.isBlank() || "null".equals(main)) {
            throw new IllegalArgumentException("plugin.yml missing main");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    public String getName() { return name; }
    public String getMain() { return main; }
    public String getVersion() { return version; }
    public String getAPIVersion() { return apiVersion; }
    public String getDescription() { return description; }
    public List<String> getAuthors() { return authors; }
    public List<String> getDepend() { return depend; }
    public List<String> getSoftDepend() { return softdepend; }
    public String getFullName() { return name + " v" + version; }
    public Map<String, Object> getRaw() { return raw; }
}
