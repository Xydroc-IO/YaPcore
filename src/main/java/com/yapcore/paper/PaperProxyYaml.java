package com.yapcore.paper;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Paper/Folia YAML helpers for Velocity / BungeeCord proxy settings. */
final class PaperProxyYaml {

    private PaperProxyYaml() {
    }

    @SuppressWarnings("unchecked")
    static void writeVelocityGlobal(Path paperDir, boolean enabled,
                                    boolean velocityOnlineMode, String secret)
            throws IOException {
        Path cfgDir = paperDir.resolve("config");
        Files.createDirectories(cfgDir);
        Path global = cfgDir.resolve("paper-global.yml");
        Yaml yaml = paperYaml();
        Map<String, Object> root;
        if (Files.isRegularFile(global)) {
            try (InputStream in = Files.newInputStream(global)) {
                Object loaded = yaml.load(in);
                root = loaded instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m)
                        : new LinkedHashMap<>();
            }
        } else {
            root = new LinkedHashMap<>();
        }
        Map<String, Object> proxies = mapChild(root, "proxies");
        Map<String, Object> velocity = mapChild(proxies, "velocity");
        velocity.put("enabled", enabled);
        velocity.put("online-mode", velocityOnlineMode);
        velocity.put("secret", secret == null ? "" : secret);
        // Ensure Bungee forwarding stays off when Velocity modern is used
        Map<String, Object> bungee = mapChild(proxies, "bungee-cord");
        if (!bungee.containsKey("online-mode")) {
            bungee.put("online-mode", true);
        }
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(global), StandardCharsets.UTF_8)) {
            yaml.dump(root, w);
        }
    }

    @SuppressWarnings("unchecked")
    static void ensureSpigotBungeeOff(Path paperDir) throws IOException {
        Path spigot = paperDir.resolve("spigot.yml");
        if (!Files.isRegularFile(spigot)) {
            // Seed minimal settings so first boot doesn't enable BungeeCord by mistake
            String seed = """
                    settings:
                      bungeecord: false
                    """;
            Files.writeString(spigot, seed, StandardCharsets.UTF_8);
            return;
        }
        Yaml yaml = paperYaml();
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(spigot)) {
            Object loaded = yaml.load(in);
            root = loaded instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
        }
        Map<String, Object> settings = mapChild(root, "settings");
        settings.put("bungeecord", false);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(spigot), StandardCharsets.UTF_8)) {
            yaml.dump(root, w);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapChild(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (child instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
            parent.put(key, copy);
            return copy;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private static Yaml paperYaml() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        return new Yaml(opts);
    }
}
