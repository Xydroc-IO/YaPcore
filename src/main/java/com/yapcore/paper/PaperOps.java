package com.yapcore.paper;

import com.yapcore.config.ServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeds Folia/Paper {@code ops.json} from YaP {@code ops=} names.
 * <p>
 * Merges into existing ops — never drops operators who were {@code /op}'d in-game
 * but are not listed in chassis {@code ops=}. Also keeps Mojang / proxy UUIDs already
 * present in {@code ops.json} or {@code usercache.json}.
 */
public final class PaperOps {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperOps");
    private static final Pattern ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private record OpRow(UUID uuid, String name, int level, boolean bypass) {
    }

    private PaperOps() {
    }

    public static void ensure(Path paperDir, ServerConfig config) throws IOException {
        List<String> names = config.getOps();
        System.setProperty("yapcore.auto-op", Boolean.toString(config.isAutoOp()));
        Path opsFile = paperDir.resolve("ops.json");
        Path usercache = paperDir.resolve("usercache.json");

        // uuid → row (preserve every existing op first)
        Map<UUID, OpRow> byUuid = new LinkedHashMap<>();
        loadExisting(opsFile, byUuid);

        // Merge network-wide ops (chassis config/network-ops.json) when resolvable.
        NetworkOps.findRoot(paperDir).ifPresent(root -> {
            try {
                NetworkOps.syncFromChassisOps(root, names);
                for (NetworkOps.Entry e : NetworkOps.load(root).values()) {
                    OpRow prev = byUuid.get(e.uuid());
                    int level = prev != null ? prev.level() : 4;
                    boolean bypass = prev != null && prev.bypass();
                    byUuid.put(e.uuid(), new OpRow(e.uuid(), e.name(), level, bypass));
                }
            } catch (IOException ex) {
                LOG.warning("network-ops merge skipped: " + ex.getMessage());
            }
        });

        if (names.isEmpty() && byUuid.isEmpty()) {
            LOG.info("auto-op=" + config.isAutoOp()
                    + " (joiners " + (config.isAutoOp() ? "will be OP'd" : "need /op")
                    + "; left ops.json untouched)");
            return;
        }
        if (names.isEmpty()) {
            // Still rewrite if network-ops added UUIDs above.
            if (!byUuid.isEmpty()) {
                writeOps(opsFile, byUuid);
                LOG.info("Merged network-ops into ops.json (" + byUuid.size() + " uuid(s)) → " + opsFile);
            }
            return;
        }

        Map<UUID, OpRow> before = new LinkedHashMap<>(byUuid);
        Map<String, Set<UUID>> managed = new LinkedHashMap<>();
        for (String name : names) {
            managed.put(name.toLowerCase(Locale.ROOT), new LinkedHashSet<>());
        }
        // Collect UUIDs already associated with managed names (ops + usercache)
        for (OpRow row : byUuid.values()) {
            Set<UUID> set = managed.get(row.name().toLowerCase(Locale.ROOT));
            if (set != null) {
                set.add(row.uuid());
            }
        }
        collectFromFile(usercache, managed);

        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT);
            Set<UUID> uuids = managed.get(key);
            if (uuids == null) {
                uuids = new LinkedHashSet<>();
            }
            uuids.add(offlineUuid(name));
            for (UUID uuid : uuids) {
                OpRow prev = byUuid.get(uuid);
                int level = prev != null ? prev.level() : 4;
                boolean bypass = prev != null && prev.bypass();
                // Prefer chassis display name for managed ops=
                byUuid.put(uuid, new OpRow(uuid, name, level, bypass));
            }
        }

        if (byUuid.equals(before) && Files.isRegularFile(opsFile)) {
            LOG.fine("ops.json unchanged (" + byUuid.size() + " uuid(s)) → " + opsFile);
            return;
        }
        writeOps(opsFile, byUuid);
        LOG.info("Merged ops.json for " + names.size() + " managed name(s), "
                + byUuid.size() + " uuid(s) total → " + opsFile);
    }

    private static void loadExisting(Path opsFile, Map<UUID, OpRow> byUuid) {
        if (!Files.isRegularFile(opsFile)) {
            return;
        }
        try {
            String raw = Files.readString(opsFile);
            Matcher m = ENTRY.matcher(raw);
            while (m.find()) {
                try {
                    UUID uuid = UUID.fromString(m.group(1));
                    String name = m.group(2);
                    int level = 4;
                    boolean bypass = false;
                    // Best-effort level/bypass near this entry
                    int from = Math.max(0, m.start() - 80);
                    int to = Math.min(raw.length(), m.end() + 120);
                    String window = raw.substring(from, to);
                    Matcher lv = Pattern.compile("\"level\"\\s*:\\s*(\\d+)").matcher(window);
                    if (lv.find()) {
                        level = Integer.parseInt(lv.group(1));
                    }
                    Matcher bp = Pattern.compile("\"bypassesPlayerLimit\"\\s*:\\s*(true|false)")
                            .matcher(window);
                    if (bp.find()) {
                        bypass = Boolean.parseBoolean(bp.group(1));
                    }
                    byUuid.putIfAbsent(uuid, new OpRow(uuid, name, level, bypass));
                } catch (IllegalArgumentException ignored) {
                    // skip bad uuid
                }
            }
        } catch (IOException e) {
            LOG.warning("Could not read " + opsFile + ": " + e.getMessage());
        }
    }

    private static void collectFromFile(Path file, Map<String, Set<UUID>> byName) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String raw = Files.readString(file);
            Matcher m = ENTRY.matcher(raw);
            while (m.find()) {
                String uuidStr = m.group(1);
                String name = m.group(2);
                Set<UUID> set = byName.get(name.toLowerCase(Locale.ROOT));
                if (set == null) {
                    continue;
                }
                try {
                    set.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        } catch (IOException e) {
            LOG.warning("Could not read " + file + ": " + e.getMessage());
        }
    }

    private static void writeOps(Path opsFile, Map<UUID, OpRow> byUuid) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (OpRow row : byUuid.values()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  {\n")
                    .append("    \"uuid\": \"").append(row.uuid()).append("\",\n")
                    .append("    \"name\": ").append(jsonString(row.name())).append(",\n")
                    .append("    \"level\": ").append(row.level()).append(",\n")
                    .append("    \"bypassesPlayerLimit\": ").append(row.bypass()).append("\n")
                    .append("  }");
        }
        json.append("\n]\n");
        Files.writeString(opsFile, json.toString(), StandardCharsets.UTF_8);
    }

    /** Mojang offline-mode player UUID. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
