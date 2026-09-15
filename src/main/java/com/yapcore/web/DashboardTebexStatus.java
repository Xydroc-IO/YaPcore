package com.yapcore.web;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured Tebex status: parse {@code tebex info}, cache last check, Hub-only fleet placement.
 */
public final class DashboardTebexStatus {

    private static final Pattern SERVER_LINE = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+for\\s+webstore\\s+(.+?)\\s*$");
    private static final Pattern CURRENCY_LINE = Pattern.compile(
            "(?i)Server prices are in\\s+(.+?)\\s*$");
    private static final Pattern URL_LINE = Pattern.compile(
            "(?i)Webstore URL:\\s*(.+?)\\s*$");
    private static final Pattern CONNECTED_LINE = Pattern.compile(
            "(?i)Successfully connected to your store:\\s*(.+?)\\s+as\\s+(.+?)\\s*$");

    private static final AtomicReference<Map<String, Object>> LAST = new AtomicReference<>(Map.of());

    private DashboardTebexStatus() {
    }

    public static Map<String, Object> cached() {
        return new LinkedHashMap<>(LAST.get());
    }

    public static Map<String, Object> rememberInfo(String raw) {
        Map<String, Object> parsed = parseInfo(raw);
        parsed.put("lastInfoAt", Instant.now().toString());
        parsed.put("rawInfo", raw == null ? "" : raw);
        Map<String, Object> merged = new LinkedHashMap<>(LAST.get());
        merged.putAll(parsed);
        LAST.set(Map.copyOf(merged));
        return parsed;
    }

    public static Map<String, Object> rememberForceCheck(String raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lastCheckAt", Instant.now().toString());
        row.put("lastCheckResult", raw == null ? "" : raw.trim());
        row.put("lastCheckOk", raw != null && !raw.toLowerCase(Locale.ROOT).contains("invalid"));
        Map<String, Object> merged = new LinkedHashMap<>(LAST.get());
        merged.putAll(row);
        LAST.set(Map.copyOf(merged));
        return row;
    }

    public static Map<String, Object> parseInfo(String raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storeName", "");
        out.put("serverName", "");
        out.put("currency", "");
        out.put("webstoreUrl", "");
        out.put("connected", false);
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String line : raw.replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher connected = CONNECTED_LINE.matcher(trimmed);
            if (connected.find()) {
                out.put("storeName", connected.group(1).trim());
                out.put("serverName", connected.group(2).trim());
                out.put("connected", true);
                continue;
            }
            Matcher server = SERVER_LINE.matcher(trimmed);
            if (server.find()) {
                out.put("serverName", server.group(1).trim());
                out.put("storeName", server.group(2).trim());
                out.put("connected", true);
                continue;
            }
            Matcher currency = CURRENCY_LINE.matcher(trimmed);
            if (currency.find()) {
                out.put("currency", currency.group(1).trim());
                out.put("connected", true);
                continue;
            }
            Matcher url = URL_LINE.matcher(trimmed);
            if (url.find()) {
                out.put("webstoreUrl", url.group(1).trim());
                out.put("connected", true);
            }
        }
        if (raw.toLowerCase(Locale.ROOT).contains("information for this server")) {
            out.put("connected", true);
        }
        return out;
    }

    /**
     * Hub-only placement: Tebex belongs on the fleet primary (lobby) or single-node root,
     * not on survival / other gameplay backends.
     */
    public static Map<String, Object> hubPlacement(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path plugins = root.resolve("plugins");
        boolean rootInstalled = DashboardNetworkSnapshots.jarPresent(plugins, "tebex");
        out.put("rootInstalled", rootInstalled);

        Path fleetJson = root.resolve("fleet/fleet.json");
        Path instances = root.resolve("fleet/instances");
        boolean fleetLayout = Files.isDirectory(instances);
        out.put("fleetEnabled", fleetLayout && Files.isRegularFile(fleetJson));

        String primaryId = readPrimaryId(fleetJson);
        out.put("primaryId", primaryId);

        List<String> withTebex = new ArrayList<>();
        List<String> withoutOnPrimary = new ArrayList<>();
        if (fleetLayout) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(instances)) {
                for (Path inst : stream) {
                    if (!Files.isDirectory(inst)) {
                        continue;
                    }
                    String id = inst.getFileName().toString();
                    Path jar = inst.resolve("plugins").resolve("tebex.jar");
                    Path disabled = inst.resolve("plugins").resolve("tebex.jar.disabled");
                    boolean present = Files.isRegularFile(jar);
                    boolean hardDisabled = Files.isRegularFile(disabled);
                    if (present) {
                        withTebex.add(id);
                    }
                    if (id.equalsIgnoreCase(primaryId) && !present && !hardDisabled) {
                        withoutOnPrimary.add(id);
                    }
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }

        out.put("instancesWithTebex", withTebex);
        List<String> extras = new ArrayList<>();
        for (String id : withTebex) {
            if (!id.equalsIgnoreCase(primaryId)) {
                extras.add(id);
            }
        }
        out.put("nonHubInstalls", extras);

        boolean hubOk;
        String detail;
        if (!fleetLayout) {
            hubOk = rootInstalled;
            detail = rootInstalled ? "single-node installed" : "missing";
        } else if (!withTebex.isEmpty() && extras.isEmpty()
                && withTebex.stream().anyMatch(id -> id.equalsIgnoreCase(primaryId))) {
            hubOk = true;
            detail = "hub only (" + primaryId + ")";
        } else if (!extras.isEmpty()) {
            hubOk = false;
            detail = "also on " + String.join(", ", extras) + " — move to " + primaryId + " only";
        } else if (rootInstalled && withTebex.isEmpty()) {
            hubOk = false;
            detail = "in catalog; copy to " + primaryId + " (Hub)";
        } else if (!withoutOnPrimary.isEmpty() || withTebex.isEmpty()) {
            hubOk = false;
            detail = "missing on Hub (" + primaryId + ")";
        } else {
            hubOk = true;
            detail = "hub only";
        }
        out.put("hubOnlyOk", hubOk);
        out.put("hubPlacementDetail", detail);
        return out;
    }

    private static String readPrimaryId(Path fleetJson) {
        if (!Files.isRegularFile(fleetJson)) {
            return "lobby";
        }
        try {
            String raw = Files.readString(fleetJson);
            Matcher m = Pattern.compile("\"primaryId\"\\s*:\\s*\"([^\"]+)\"").matcher(raw);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "lobby";
    }
}
