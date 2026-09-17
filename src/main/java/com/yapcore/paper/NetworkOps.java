package com.yapcore.paper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Network-wide operator list shared by chassis, Link, and Folia backends.
 * <p>
 * File: {@code config/network-ops.json}. Names in chassis {@code ops=} are also treated as
 * network ops (UUID filled in when the player joins).
 */
public final class NetworkOps {

    private static final Logger LOG = Logger.getLogger("YaPcore.NetworkOps");
    private static final Pattern ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    public record Entry(UUID uuid, String name) {
    }

    private NetworkOps() {
    }

    public static Path file(Path rootDir) {
        return rootDir.resolve("config").resolve("network-ops.json");
    }

    public static Map<UUID, Entry> load(Path rootDir) {
        Map<UUID, Entry> out = new LinkedHashMap<>();
        Path file = file(rootDir);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = ENTRY.matcher(raw);
            while (m.find()) {
                try {
                    UUID uuid = UUID.fromString(m.group(1));
                    out.put(uuid, new Entry(uuid, m.group(2)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        } catch (IOException e) {
            LOG.warning("Could not read " + file + ": " + e.getMessage());
        }
        return out;
    }

    public static void save(Path rootDir, Map<UUID, Entry> byUuid) throws IOException {
        Path file = file(rootDir);
        Files.createDirectories(file.getParent());
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Entry e : byUuid.values()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  {\n")
                    .append("    \"uuid\": \"").append(e.uuid()).append("\",\n")
                    .append("    \"name\": ").append(jsonString(e.name())).append("\n")
                    .append("  }");
        }
        json.append("\n]\n");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    }

    /** Add or refresh an operator (keeps one row per UUID; updates name). */
    public static void add(Path rootDir, UUID uuid, String name) throws IOException {
        Map<UUID, Entry> map = load(rootDir);
        String display = name == null || name.isBlank() ? "Player" : name.trim();
        // Drop other UUIDs with the same name so renames don't leave stale rows.
        map.entrySet().removeIf(e -> e.getValue().name().equalsIgnoreCase(display)
                && !e.getKey().equals(uuid));
        map.put(uuid, new Entry(uuid, display));
        save(rootDir, map);
    }

    /** Add by name only (offline UUID placeholder until they join with real UUID). */
    public static void addName(Path rootDir, String name) throws IOException {
        if (name == null || name.isBlank()) {
            return;
        }
        String display = name.trim();
        Map<UUID, Entry> map = load(rootDir);
        for (Entry e : map.values()) {
            if (e.name().equalsIgnoreCase(display)) {
                return;
            }
        }
        UUID offline = PaperOps.offlineUuid(display);
        map.put(offline, new Entry(offline, display));
        save(rootDir, map);
    }

    public static void removeName(Path rootDir, String name) throws IOException {
        if (name == null || name.isBlank()) {
            return;
        }
        Map<UUID, Entry> map = load(rootDir);
        map.entrySet().removeIf(e -> e.getValue().name().equalsIgnoreCase(name.trim()));
        save(rootDir, map);
    }

    public static boolean containsName(Path rootDir, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (Entry e : load(rootDir).values()) {
            if (e.name().toLowerCase(Locale.ROOT).equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsUuid(Path rootDir, UUID uuid) {
        return uuid != null && load(rootDir).containsKey(uuid);
    }

    public static boolean isNetworkOp(Path rootDir, UUID uuid, String name) {
        if (containsUuid(rootDir, uuid)) {
            return true;
        }
        return containsName(rootDir, name);
    }

    public static List<String> names(Path rootDir) {
        List<String> out = new ArrayList<>();
        for (Entry e : load(rootDir).values()) {
            out.add(e.name());
        }
        return out;
    }

    /**
     * Ensure every chassis {@code ops=} name exists in the network store (offline UUID ok).
     */
    public static void syncFromChassisOps(Path rootDir, List<String> chassisOps) throws IOException {
        if (chassisOps == null || chassisOps.isEmpty()) {
            return;
        }
        Map<UUID, Entry> map = load(rootDir);
        boolean changed = false;
        for (String name : chassisOps) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String display = name.trim();
            boolean found = false;
            for (Entry e : map.values()) {
                if (e.name().equalsIgnoreCase(display)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                UUID offline = PaperOps.offlineUuid(display);
                map.put(offline, new Entry(offline, display));
                changed = true;
            }
        }
        if (changed) {
            save(rootDir, map);
            LOG.info("Synced " + chassisOps.size() + " chassis ops= name(s) into network-ops.json");
        }
    }

    /** Resolve YaPcore root by walking parents for {@code config/server.properties}. */
    public static Optional<Path> findRoot(Path start) {
        Path p = start == null ? Path.of("").toAbsolutePath() : start.toAbsolutePath().normalize();
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("config").resolve("server.properties"))) {
                return Optional.of(p);
            }
            p = p.getParent();
        }
        return Optional.empty();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
