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
 * Preserves Mojang / proxy UUIDs already in {@code ops.json} or {@code usercache.json}.
 * Older builds wrote offline-only UUIDs and wiped real join UUIDs every restart,
 * so staff had to {@code /op} themselves again after each boot.
 */
public final class PaperOps {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperOps");
    private static final Pattern ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);
    private static final Pattern USERCACHE_ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private PaperOps() {
    }

    public static void ensure(Path paperDir, ServerConfig config) throws IOException {
        List<String> names = config.getOps();
        System.setProperty("yapcore.auto-op", Boolean.toString(config.isAutoOp()));
        if (names.isEmpty()) {
            LOG.info("auto-op=" + config.isAutoOp()
                    + " (joiners " + (config.isAutoOp() ? "will be OP'd" : "need /op") + ")");
            return;
        }
        Path opsFile = paperDir.resolve("ops.json");
        Path usercache = paperDir.resolve("usercache.json");

        Map<String, Set<UUID>> byName = new LinkedHashMap<>();
        for (String name : names) {
            byName.put(name.toLowerCase(Locale.ROOT), new LinkedHashSet<>());
        }

        collectUuids(opsFile, byName, true);
        collectUuids(usercache, byName, false);

        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        int entries = 0;
        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT);
            Set<UUID> uuids = byName.get(key);
            if (uuids == null) {
                uuids = new LinkedHashSet<>();
            }
            // Always keep offline UUID so pure offline joins still match.
            uuids.add(offlineUuid(name));
            for (UUID uuid : uuids) {
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                entries++;
                json.append("  {\n")
                        .append("    \"uuid\": \"").append(uuid).append("\",\n")
                        .append("    \"name\": ").append(jsonString(name)).append(",\n")
                        .append("    \"level\": 4,\n")
                        .append("    \"bypassesPlayerLimit\": false\n")
                        .append("  }");
            }
        }
        json.append("\n]\n");
        Files.writeString(opsFile, json.toString(), StandardCharsets.UTF_8);
        LOG.info("Wrote ops.json for " + names.size() + " name(s), " + entries + " uuid(s) → " + opsFile);
    }

    private static void collectUuids(Path file, Map<String, Set<UUID>> byName, boolean opsFormat) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String raw = Files.readString(file);
            Pattern pattern = opsFormat ? ENTRY : USERCACHE_ENTRY;
            Matcher m = pattern.matcher(raw);
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
                    // skip bad uuid
                }
            }
        } catch (IOException e) {
            LOG.warning("Could not read " + file + ": " + e.getMessage());
        }
    }

    /** Mojang offline-mode player UUID. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
