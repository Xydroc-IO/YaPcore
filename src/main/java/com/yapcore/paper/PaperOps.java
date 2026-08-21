package com.yapcore.paper;

import com.yapcore.config.ServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Seeds Paper {@code ops.json} from YaP {@code ops=} / offline UUID rules. */
public final class PaperOps {

    private static final Logger LOG = Logger.getLogger("YaPcore.PaperOps");

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
        Set<String> existing = new LinkedHashSet<>();
        if (Files.isRegularFile(opsFile)) {
            String prev = Files.readString(opsFile);
            // keep file if already has entries — still merge names below via rewrite
            if (prev.contains("\"uuid\"")) {
                // merge by rewriting full list from config names (authoritative)
            }
        }
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (String name : names) {
            if (!existing.add(name.toLowerCase())) {
                continue;
            }
            UUID uuid = offlineUuid(name);
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  {\n")
                    .append("    \"uuid\": \"").append(uuid).append("\",\n")
                    .append("    \"name\": ").append(jsonString(name)).append(",\n")
                    .append("    \"level\": 4,\n")
                    .append("    \"bypassesPlayerLimit\": false\n")
                    .append("  }");
        }
        json.append("\n]\n");
        Files.writeString(opsFile, json.toString(), StandardCharsets.UTF_8);
        LOG.info("Wrote ops.json for " + names.size() + " name(s) → " + opsFile);
    }

    /** Mojang offline-mode player UUID. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
