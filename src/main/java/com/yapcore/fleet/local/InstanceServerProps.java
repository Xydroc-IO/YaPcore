package com.yapcore.fleet.local;

import com.yapcore.fleet.model.FleetInstance;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Read / patch Folia {@code server.properties} for a fleet instance tree. */
public final class InstanceServerProps {

    private static final String[] KEYS = {
            "server-port", "server-ip", "max-players", "motd", "gamemode", "difficulty",
            "view-distance", "simulation-distance", "online-mode", "pvp",
            "spawn-protection", "spawn-monsters", "spawn-animals", "force-gamemode",
            "white-list", "enforce-whitelist", "enable-command-block",
            "level-name", "level-seed", "level-type", "generator-settings"
    };

    private InstanceServerProps() {
    }

    public static Path propsFile(Path rootDir, FleetInstance instance) {
        return InstanceLayout.dir(rootDir, instance).resolve("server.properties");
    }

    public static Map<String, String> read(Path rootDir, FleetInstance instance) throws IOException {
        Path file = propsFile(rootDir, instance);
        Properties p = load(file);
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            String v = p.getProperty(key);
            if (v != null) {
                out.put(key, v);
            }
        }
        out.putIfAbsent("server-port", Integer.toString(instance.port()));
        out.putIfAbsent("server-ip", instance.bind());
        return out;
    }

    /**
     * Patch known keys into {@code server.properties}. Returns the effective property map after write.
     * Does not update {@link FleetInstance} registry — caller must sync port/bind into fleet.json.
     */
    public static Map<String, String> patch(
            Path rootDir, FleetInstance instance, Map<String, String> updates) throws IOException {
        Path file = propsFile(rootDir, instance);
        Files.createDirectories(file.getParent());
        Properties p = load(file);
        if (updates != null) {
            for (Map.Entry<String, String> e : updates.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().trim();
                if (key.isEmpty() || !isAllowed(key)) {
                    continue;
                }
                String val = e.getValue() == null ? "" : e.getValue().trim();
                p.setProperty(key, val);
            }
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "YaP fleet instance " + instance.id());
        }
        return read(rootDir, instance);
    }

    static boolean isAllowed(String key) {
        for (String k : KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static Properties load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        return p;
    }
}
