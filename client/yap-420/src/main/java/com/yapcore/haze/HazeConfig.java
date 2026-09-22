package com.yapcore.haze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-side haze toggles. */
public final class HazeConfig {

    public boolean enabled = true;
    public float intensityScale = 1.0f;

    public static HazeConfig load() {
        HazeConfig cfg = new HazeConfig();
        Path path = Path.of("config", "yap-420.properties");
        if (!Files.isRegularFile(path)) {
            return cfg;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) {
                    continue;
                }
                int eq = t.indexOf('=');
                String key = t.substring(0, eq).trim();
                String val = t.substring(eq + 1).trim();
                if ("enabled".equals(key)) {
                    cfg.enabled = Boolean.parseBoolean(val);
                } else if ("intensity-scale".equals(key)) {
                    try {
                        cfg.intensityScale = Float.parseFloat(val);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            Yap420Client.LOGGER.warn("Failed reading {}", path, e);
        }
        return cfg;
    }
}
