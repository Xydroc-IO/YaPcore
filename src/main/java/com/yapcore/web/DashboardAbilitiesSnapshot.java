package com.yapcore.web;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only snapshot of {@code plugins/YaPAbilities} for the web dashboard. */
public final class DashboardAbilitiesSnapshot {

    private static final String DATA_DIR = "YaPAbilities";

    private DashboardAbilitiesSnapshot() {
    }

    public static boolean installed(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-abilities");
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = rootDir.resolve("plugins");
        out.put("abilitiesInstalled", installed(pluginsDir));
        out.put("abilityCount", 0);
        out.put("statusEffectCount", 0);
        out.put("dualHotbar", false);
        out.put("abilityBarEnabled", false);
        out.put("abilityBookEnabled", false);
        out.put("shiftFBook", false);
        out.put("hotbarKeys", "4-9");
        out.put("barSlotCount", 6);
        out.put("barBindingPlayers", 0);
        out.put("barBindingPreview", List.of());
        out.put("combatInstalled", jarPresent(pluginsDir, "yap-combat"));
        out.put("bedrockMmoInstalled", jarPresent(pluginsDir, "yap-mmo-bedrock"));

        if (!installed(pluginsDir)) {
            return out;
        }

        Path dataDir = pluginsDir.resolve(DATA_DIR);
        Path configFile = dataDir.resolve("config.yml");
        out.put("configPresent", Files.isRegularFile(configFile));
        if (!Files.isRegularFile(configFile)) {
            return out;
        }

        try {
            Map<String, Object> yaml = loadYaml(configFile);
            out.put("enabled", bool(yaml.get("enabled"), true));
            Map<String, Object> bar = nestedMap(yaml, "ability-bar");
            out.put("dualHotbar", bool(bar.get("dual-hotbar"), true));
            out.put("abilityBarEnabled", bool(bar.get("enabled"), true));
            int firstKey = intVal(bar.get("first-key"), 4);
            int slotCount = intVal(bar.get("slot-count"), 6);
            out.put("barSlotCount", slotCount);
            out.put("hotbarKeys", firstKey + "-" + Math.min(9, firstKey + slotCount - 1));

            Map<String, Object> book = nestedMap(yaml, "ability-book");
            out.put("abilityBookEnabled", bool(book.get("enabled"), true));
            out.put("shiftFBook", openTriggerEnabled(book, "SNEAK_SWAP"));

            String abilitiesDir = str(yaml.get("abilities-directory"), "abilities");
            String effectsDir = str(yaml.get("effects-directory"), "effects");
            out.put("abilityCount", countCatalogEntries(dataDir.resolve(abilitiesDir), "abilities"));
            out.put("statusEffectCount", countCatalogEntries(dataDir.resolve(effectsDir), "effects"));
            out.put("barBindingPlayers", countBarsPlayers(dataDir.resolve("bars.yml")));
            out.put("barBindingPreview", List.of());
        } catch (IOException e) {
            out.put("error", e.getMessage() == null ? "config read failed" : e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static boolean openTriggerEnabled(Map<String, Object> book, String trigger) {
        Object raw = book.get("open-triggers");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (trigger.equalsIgnoreCase(String.valueOf(item).trim())) {
                return true;
            }
        }
        return false;
    }

    private static int countBarsPlayers(Path barsFile) throws IOException {
        if (!Files.isRegularFile(barsFile)) {
            return 0;
        }
        Map<String, Object> yaml = loadYaml(barsFile);
        Object players = yaml.get("players");
        if (players instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    private static int countCatalogEntries(Path dir, String sectionKey) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int total = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.yml")) {
            for (Path file : stream) {
                Map<String, Object> yaml = loadYaml(file);
                Object section = yaml.get(sectionKey);
                if (section instanceof Map<?, ?> map) {
                    total += map.size();
                }
            }
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        Object val = root.get(key);
        if (val instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return new LinkedHashMap<>();
    }

    private static String str(Object val, String fallback) {
        if (val == null) {
            return fallback;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static boolean bool(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return fallback;
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(token)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
