package com.yapcore.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline directory of everyone who has joined (snapshot + usercache). */
public final class DashboardSeenPlayers {

    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}");

    private DashboardSeenPlayers() {
    }

    public static List<Map<String, Object>> load(Path root, List<Map<String, Object>> online) {
        Map<String, Map<String, Object>> byUuid = new LinkedHashMap<>();
        mergeFile(byUuid, root.resolve("plugins").resolve("YaPModeration").resolve("seen-players.json"));
        mergeUserCache(byUuid, root.resolve("usercache.json"));
        mergeUserCache(byUuid, root.resolve("folia-kernel").resolve("usercache.json"));
        for (Map<String, Object> row : online == null ? List.<Map<String, Object>>of() : online) {
            String uuid = str(row.get("uuid"));
            if (uuid.isBlank()) {
                continue;
            }
            Map<String, Object> dest = byUuid.computeIfAbsent(uuid.toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>());
            dest.put("uuid", uuid);
            dest.put("username", first(row.get("name"), dest.get("username")));
            dest.put("nickname", first(row.get("displayName"), dest.get("nickname")));
            dest.put("ip", first(row.get("ip"), dest.get("ip")));
            dest.put("ips", first(dest.get("ips"), row.get("ip")));
            dest.put("online", true);
            dest.put("lastSeen", System.currentTimeMillis());
        }
        List<Map<String, Object>> out = new ArrayList<>(byUuid.values());
        out.sort((a, b) -> Long.compare(num(b.get("lastSeen")), num(a.get("lastSeen"))));
        return out;
    }

    private static void mergeFile(Map<String, Map<String, Object>> byUuid, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            parseObjects(Files.readString(file, StandardCharsets.UTF_8), byUuid, false);
        } catch (IOException ignored) {
        }
    }

    private static void mergeUserCache(Map<String, Map<String, Object>> byUuid, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            parseObjects(Files.readString(file, StandardCharsets.UTF_8), byUuid, true);
        } catch (IOException ignored) {
        }
    }

    private static void parseObjects(String json, Map<String, Map<String, Object>> byUuid, boolean usercache) {
        Matcher om = OBJECT.matcher(json);
        while (om.find()) {
            Map<String, Object> raw = new LinkedHashMap<>();
            Matcher em = TinyJson.ENTRY.matcher(om.group(1));
            while (em.find()) {
                raw.put(em.group(1), TinyJson.parseValue(em.group(2)));
            }
            String uuid = str(first(raw.get("uuid"), raw.get("id")));
            if (uuid.isBlank()) {
                continue;
            }
            Map<String, Object> dest = byUuid.computeIfAbsent(uuid.toLowerCase(Locale.ROOT), k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", uuid);
                row.put("username", "");
                row.put("nickname", "");
                row.put("ip", "");
                row.put("ips", "");
                row.put("firstSeen", 0L);
                row.put("lastSeen", 0L);
                row.put("online", false);
                return row;
            });
            dest.put("uuid", uuid);
            if (usercache) {
                dest.put("username", first(raw.get("name"), dest.get("username")));
            } else {
                dest.put("username", first(raw.get("username"), dest.get("username")));
                dest.put("nickname", first(raw.get("nickname"), dest.get("nickname")));
                dest.put("ip", first(raw.get("ip"), dest.get("ip")));
                dest.put("ips", first(raw.get("ips"), dest.get("ips")));
                dest.put("firstSeen", num(first(raw.get("firstSeen"), dest.get("firstSeen"))));
                dest.put("lastSeen", num(first(raw.get("lastSeen"), dest.get("lastSeen"))));
            }
            dest.putIfAbsent("online", false);
        }
    }

    private static Object first(Object a, Object b) {
        String sa = str(a);
        if (!sa.isBlank()) {
            return a;
        }
        return b;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static long num(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
