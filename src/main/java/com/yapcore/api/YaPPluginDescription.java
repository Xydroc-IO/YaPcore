package com.yapcore.api;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Descriptor from {@code yap.yml} inside a next-gen plugin jar.
 */
public record YaPPluginDescription(
        String name,
        String main,
        String version,
        String description,
        List<String> authors,
        String api
) {
    @SuppressWarnings("unchecked")
    public static YaPPluginDescription fromYaml(InputStream in) {
        Object loaded = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid yap.yml");
        }
        Map<String, Object> raw = (Map<String, Object>) map;
        String name = String.valueOf(raw.get("name"));
        String main = String.valueOf(raw.get("main"));
        if (name == null || "null".equals(name) || main == null || "null".equals(main)) {
            throw new IllegalArgumentException("yap.yml requires name and main");
        }
        List<String> authors = Collections.emptyList();
        if (raw.get("authors") instanceof List<?> list) {
            authors = list.stream().map(String::valueOf).toList();
        } else if (raw.get("author") != null) {
            authors = List.of(String.valueOf(raw.get("author")));
        }
        return new YaPPluginDescription(
                name,
                main,
                String.valueOf(raw.getOrDefault("version", "0.0.0")),
                String.valueOf(raw.getOrDefault("description", "")),
                authors,
                String.valueOf(raw.getOrDefault("api", "yap-1"))
        );
    }
}
