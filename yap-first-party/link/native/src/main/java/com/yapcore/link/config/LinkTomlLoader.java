package com.yapcore.link.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Minimal TOML subset → {@link Properties} for YaP Link.
 * Supports string keys, {@code [servers]}, {@code [forced-host]}, {@code try = ["a","b"]}.
 */
public final class LinkTomlLoader {

    private LinkTomlLoader() {
    }

    public static Properties load(Path toml) throws IOException {
        Properties props = new Properties();
        String section = "";
        for (String raw : Files.readAllLines(toml, StandardCharsets.UTF_8)) {
            String line = raw.split("#", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase();
                continue;
            }
            if (line.startsWith("try") && line.contains("[")) {
                String arr = line.substring(line.indexOf('[') + 1, line.lastIndexOf(']'));
                props.setProperty("try", arr.replace("\"", "").replace("'", "").trim());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = unquote(line.substring(0, eq).trim());
            String value = unquote(line.substring(eq + 1).trim());
            switch (section) {
                case "servers" -> props.setProperty("servers." + key, value);
                case "forced-host" -> props.setProperty("forced-host." + key, value);
                default -> props.setProperty(key.replace('-', '.'), value.replace('-', '.'));
            }
        }
        return props;
    }

    private static String unquote(String s) {
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
