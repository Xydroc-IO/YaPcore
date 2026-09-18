package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists YaPItems catalog ids from on-disk YAML (works when Folia is down).
 * Scans {@code plugins/YaPItems/items} recursively ({@code .yml} files), then defaults seed.
 */
public final class DashboardYapItemsCatalog {

    private DashboardYapItemsCatalog() {
    }

    public static List<String> listIds(Path root) {
        Set<String> ids = new LinkedHashSet<>();
        collectFrom(root.resolve("plugins").resolve("YaPItems").resolve("items"), ids);
        if (ids.isEmpty()) {
            collectFrom(root.resolve("config").resolve("defaults").resolve("plugins")
                    .resolve("YaPItems").resolve("items"), ids);
        }
        List<String> out = new ArrayList<>(ids);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static void collectFrom(Path itemsDir, Set<String> ids) {
        if (itemsDir == null || !Files.isDirectory(itemsDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(itemsDir)) {
            walk.filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".yml") || name.endsWith(".yaml");
            }).forEach(p -> readIds(p, ids));
        } catch (IOException ignored) {
            // catalog optional
        }
    }

    @SuppressWarnings("unchecked")
    private static void readIds(Path file, Set<String> ids) {
        try {
            Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
            for (var e : yaml.entrySet()) {
                String key = String.valueOf(e.getKey()).trim();
                if (key.isEmpty() || key.startsWith("#")) {
                    continue;
                }
                // Skip non-item top-level maps (e.g. future meta blocks)
                if (!(e.getValue() instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> body = (Map<String, Object>) e.getValue();
                if (!body.containsKey("base") && !body.containsKey("material") && !body.containsKey("type")) {
                    continue;
                }
                ids.add(key.toLowerCase(Locale.ROOT));
            }
        } catch (IOException ignored) {
            // skip unreadable file
        }
    }
}
