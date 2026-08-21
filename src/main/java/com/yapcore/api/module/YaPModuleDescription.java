package com.yapcore.api.module;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Descriptor from {@code module.yml} inside a module jar.
 */
public record YaPModuleDescription(
        String name,
        String main,
        String version,
        String description,
        List<String> authors,
        String api,
        List<String> provides,
        List<String> requires
) {
    @SuppressWarnings("unchecked")
    public static YaPModuleDescription fromYaml(InputStream in) {
        Object loaded = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid module.yml");
        }
        Map<String, Object> raw = (Map<String, Object>) map;
        String name = String.valueOf(raw.get("name"));
        String main = String.valueOf(raw.get("main"));
        if (name == null || "null".equals(name) || main == null || "null".equals(main)) {
            throw new IllegalArgumentException("module.yml requires name and main");
        }
        List<String> authors = Collections.emptyList();
        if (raw.get("authors") instanceof List<?> list) {
            authors = list.stream().map(String::valueOf).toList();
        } else if (raw.get("author") != null) {
            authors = List.of(String.valueOf(raw.get("author")));
        }
        return new YaPModuleDescription(
                name,
                main,
                String.valueOf(raw.getOrDefault("version", "0.0.0")),
                String.valueOf(raw.getOrDefault("description", "")),
                authors,
                String.valueOf(raw.getOrDefault("api", "yap-module-1")),
                stringList(raw.get("provides")),
                stringList(raw.get("requires"))
        );
    }

    private static List<String> stringList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
