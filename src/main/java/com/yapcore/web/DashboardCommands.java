package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read/write {@code plugins/YaPCommands/commands.yml} for the dashboard. */
public final class DashboardCommands {

    public static final Path RELATIVE = Path.of("plugins", "YaPCommands", "commands.yml");

    private DashboardCommands() {
    }

    public static Path file(Path root) {
        return root.resolve(RELATIVE);
    }

    public static Map<String, Object> snapshot(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path file = file(root);
        out.put("configPresent", Files.isRegularFile(file));
        out.put("jarPresent", Files.isRegularFile(root.resolve("plugins").resolve("yap-commands.jar")));
        List<Map<String, Object>> commands = listCommands(root);
        out.put("commands", commands);
        out.put("count", commands.size());
        boolean requireUse = true;
        try {
            if (Files.isRegularFile(file)) {
                Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
                Object v = yaml.get("require-use-perm");
                if (v instanceof Boolean b) {
                    requireUse = b;
                }
            }
        } catch (IOException ignored) {
        }
        out.put("requireUsePerm", requireUse);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listCommands(Path root) {
        List<Map<String, Object>> out = new ArrayList<>();
        Path file = file(root);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
            Object cmdsObj = yaml.get("commands");
            if (!(cmdsObj instanceof Map<?, ?> map)) {
                return out;
            }
            map.entrySet().stream()
                    .sorted((a, b) -> String.valueOf(a.getKey()).compareToIgnoreCase(String.valueOf(b.getKey())))
                    .forEach(e -> {
                        if (e.getValue() instanceof Map<?, ?> raw) {
                            out.add(toCommand(String.valueOf(e.getKey()), (Map<String, Object>) raw));
                        }
                    });
        } catch (IOException ignored) {
            return out;
        }
        return out;
    }

    public static void saveCommand(Path root, Map<String, Object> cmd) throws IOException {
        String id = normalizeId(String.valueOf(cmd.getOrDefault("name", cmd.getOrDefault("id", ""))));
        if (id.isEmpty()) {
            throw new IllegalArgumentException("command name required");
        }
        if (isReserved(id)) {
            throw new IllegalArgumentException("reserved command name: " + id);
        }
        Path file = file(root);
        Files.createDirectories(file.getParent());
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file) : new LinkedHashMap<>();
        if (!yaml.containsKey("require-use-perm")) {
            yaml.put("require-use-perm", true);
        }
        Map<String, Object> commands = DashboardNetworkSnapshots.mapOrCreate(yaml, "commands");
        commands.put(id, fromCommand(cmd));
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    @SuppressWarnings("unchecked")
    public static void deleteCommand(Path root, String name) throws IOException {
        String key = normalizeId(name);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("command name required");
        }
        Path file = file(root);
        if (!Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file);
        Object cmdsObj = yaml.get("commands");
        if (cmdsObj instanceof Map<?, ?> cmds) {
            ((Map<String, Object>) cmds).remove(key);
            DashboardNetworkSnapshots.dumpYaml(file, yaml);
        }
    }

    public static void cloneCommand(Path root, String fromId, String toId) throws IOException {
        String from = normalizeId(fromId);
        String to = normalizeId(toId);
        if (from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("from and to names required");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("clone target must differ");
        }
        if (isReserved(to)) {
            throw new IllegalArgumentException("reserved command name: " + to);
        }
        Map<String, Object> source = null;
        for (Map<String, Object> cmd : listCommands(root)) {
            if (from.equals(cmd.get("name"))) {
                source = new LinkedHashMap<>(cmd);
                break;
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("unknown command: " + from);
        }
        source.put("name", to);
        saveCommand(root, source);
    }

    public static void setRequireUsePerm(Path root, boolean value) throws IOException {
        Path file = file(root);
        Files.createDirectories(file.getParent());
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file) : new LinkedHashMap<>();
        yaml.put("require-use-perm", value);
        if (!yaml.containsKey("commands")) {
            yaml.put("commands", new LinkedHashMap<>());
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    public static boolean isReserved(String name) {
        String n = normalizeId(name);
        return n.isEmpty() || n.equals("yapcommands") || n.equals("ycmd") || n.equals("customcmd");
    }

    private static Map<String, Object> toCommand(String name, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", normalizeId(name));
        out.put("enabled", bool(raw.get("enabled"), true));
        out.put("aliases", stringList(raw.get("aliases")));
        out.put("permission", str(raw.get("permission")));
        out.put("description", str(raw.get("description")));
        out.put("cooldownSeconds", intVal(raw.get("cooldown-seconds"), 0));
        out.put("hideNoPermission", bool(raw.get("hide-no-permission"), true));
        out.put("messages", stringList(raw.get("messages")));
        out.put("playerCommands", stringList(raw.get("player-commands")));
        out.put("consoleCommands", stringList(raw.get("console-commands")));
        out.put("broadcast", str(raw.get("broadcast")));
        return out;
    }

    private static Map<String, Object> fromCommand(Map<String, Object> cmd) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", bool(cmd.get("enabled"), true));
        out.put("aliases", stringList(cmd.get("aliases")));
        out.put("permission", str(cmd.get("permission")));
        out.put("description", str(cmd.get("description")));
        out.put("cooldown-seconds", intVal(cmd.get("cooldownSeconds"), intVal(cmd.get("cooldown-seconds"), 0)));
        out.put("hide-no-permission", bool(cmd.get("hideNoPermission"), bool(cmd.get("hide-no-permission"), true)));
        out.put("messages", stringList(cmd.get("messages")));
        out.put("player-commands", stringList(cmd.get("playerCommands") != null ? cmd.get("playerCommands") : cmd.get("player-commands")));
        out.put("console-commands", stringList(cmd.get("consoleCommands") != null ? cmd.get("consoleCommands") : cmd.get("console-commands")));
        out.put("broadcast", str(cmd.get("broadcast")));
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o != null) {
            return Boolean.parseBoolean(String.valueOf(o));
        }
        return def;
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
        } else if (o instanceof String s) {
            for (String line : s.split("\\R")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }
}
