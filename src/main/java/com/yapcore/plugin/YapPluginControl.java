package com.yapcore.plugin;

import com.yapcore.web.PluginConfigCatalog;
import com.yapcore.web.PluginConfigIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Soft (YAML {@code enabled}) and hard ({@code .jar.disabled}) plugin control for ops.
 * Folia does not hot-unload; hard changes need a Folia restart to take effect on the classpath.
 */
public final class YapPluginControl {

    public enum Tier {
        CORE,
        NETWORK,
        GAMEPLAY,
        THIRD_PARTY
    }

    public enum Mode {
        SOFT,
        HARD
    }

    private static final Set<String> CORE_TOKENS = Set.of(
            "yap-db", "yap-folia-bridge", "yap-perms", "yap-playerdata", "yap-essentials",
            "yap-chat", "yap-moderation", "yap-protect", "yap-admin");

    private static final Set<String> GAMEPLAY_TOKENS = Set.of(
            "yap-stacker", "yap-gameplay-knobs", "yap-skills", "yap-disasters", "yap-factions");

    private final Path root;
    private final Path pluginsDir;
    private final PluginManager jars;

    public YapPluginControl(Path root, PluginManager jars) {
        this.root = root;
        this.pluginsDir = jars.getPluginsDir();
        this.jars = jars;
    }

    public List<Map<String, Object>> listDetailed() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir)) {
            return out;
        }
        try (var stream = Files.list(pluginsDir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") || n.endsWith(".jar.disabled");
                    })
                    .sorted((a, b) -> a.getFileName().toString()
                            .compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(p -> out.add(describe(p)));
        }
        return out;
    }

    public Map<String, Object> describe(Path jarPath) {
        Map<String, Object> row = new LinkedHashMap<>();
        String fileName = jarPath.getFileName().toString();
        boolean hardEnabled = !fileName.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
        String activeName = hardEnabled ? fileName : fileName.substring(0, fileName.length() - ".disabled".length());
        PluginConfigCatalog.Entry entry = findEntry(activeName);
        Tier tier = tierFor(activeName, entry);
        row.put("fileName", fileName);
        row.put("activeName", activeName);
        row.put("hardEnabled", hardEnabled);
        row.put("tier", tier.name());
        row.put("protected", tier == Tier.CORE);
        row.put("catalogId", entry == null ? "" : entry.id());
        row.put("title", entry == null ? activeName : entry.title());
        row.put("reload", entry == null ? "" : entry.reload());
        boolean softSupported = entry != null;
        row.put("softSupported", softSupported);
        Boolean soft = softSupported ? readSoftEnabled(entry) : null;
        row.put("softEnabled", soft == null ? "" : soft);
        row.put("needsRestartForHard", true);
        try {
            row.put("sizeBytes", Files.size(jarPath));
            row.put("sizeLabel", PluginManager.PluginInfo.fromPath(jarPath).sizeLabel());
        } catch (IOException e) {
            row.put("sizeBytes", 0);
            row.put("sizeLabel", "?");
        }
        return row;
    }

    public Map<String, Object> setEnabled(String fileName, boolean enable, Mode mode, boolean force)
            throws IOException {
        Path target = resolveJar(fileName);
        String name = target.getFileName().toString();
        boolean currentlyHard = !name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
        String activeName = currentlyHard ? name : name.substring(0, name.length() - ".disabled".length());
        PluginConfigCatalog.Entry entry = findEntry(activeName);
        Tier tier = tierFor(activeName, entry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", name);
        result.put("activeName", activeName);
        result.put("tier", tier.name());
        result.put("mode", mode.name());

        if (mode == Mode.HARD) {
            if (tier == Tier.CORE && !enable && !force) {
                throw new IOException("CORE plugin hard-disable requires force=true");
            }
            Path next = hardToggle(target, enable);
            result.put("ok", true);
            result.put("hardEnabled", enable);
            result.put("fileName", next.getFileName().toString());
            result.put("needsRestart", true);
            result.put("reload", "");
            jars.refresh();
            return result;
        }

        if (entry == null) {
            throw new IOException("Soft enable/disable not supported for " + activeName
                    + " (no known config). Use mode=hard.");
        }
        if (tier == Tier.CORE && !enable && !force) {
            throw new IOException("CORE plugin soft-disable requires force=true");
        }
        writeSoftEnabled(entry, enable);
        result.put("ok", true);
        result.put("softEnabled", enable);
        result.put("needsRestart", entry.reload() == null || entry.reload().isBlank());
        result.put("reload", entry.reload() == null ? "" : entry.reload());
        return result;
    }

    public Map<String, Object> uninstall(String fileName, boolean force) throws IOException {
        Path target = resolveJar(fileName);
        String name = target.getFileName().toString();
        String activeName = name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled")
                ? name.substring(0, name.length() - ".disabled".length())
                : name;
        Tier tier = tierFor(activeName, findEntry(activeName));
        if (tier == Tier.CORE && !force) {
            throw new IOException("CORE uninstall requires force=true");
        }
        boolean ok = jars.removePlugin(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("fileName", name);
        result.put("needsRestart", true);
        return result;
    }

    public PluginManager.PluginInfo install(Path sourceJar) throws IOException {
        Path abs = sourceJar.toAbsolutePath().normalize();
        Path home = root.toAbsolutePath().normalize();
        if (!abs.startsWith(home)) {
            throw new IOException("Install path must be under YAPCORE_HOME (" + home + ")");
        }
        return jars.addPlugin(abs);
    }

    private Path hardToggle(Path jarPath, boolean enable) throws IOException {
        String name = jarPath.getFileName().toString();
        boolean disabled = name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
        if (enable) {
            if (!disabled) {
                return jarPath;
            }
            String live = name.substring(0, name.length() - ".disabled".length());
            Path dest = pluginsDir.resolve(live);
            if (Files.exists(dest)) {
                throw new IOException("Cannot enable: " + live + " already exists");
            }
            moveJar(jarPath, dest);
            return dest;
        }
        if (disabled) {
            return jarPath;
        }
        Path dest = pluginsDir.resolve(name + ".disabled");
        if (Files.exists(dest)) {
            throw new IOException("Cannot disable: " + dest.getFileName() + " already exists");
        }
        moveJar(jarPath, dest);
        return dest;
    }

    private static void moveJar(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveJar(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid plugin name");
        }
        Path rootAbs = pluginsDir.toAbsolutePath().normalize();
        Path p = rootAbs.resolve(fileName).normalize();
        if (!p.startsWith(rootAbs)) {
            throw new IOException("Invalid plugin path");
        }
        if (Files.isRegularFile(p)) {
            return p;
        }
        Path disabled = rootAbs.resolve(fileName + ".disabled");
        if (Files.isRegularFile(disabled)) {
            return disabled;
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            Path alt = rootAbs.resolve(fileName + ".disabled");
            if (Files.isRegularFile(alt)) {
                return alt;
            }
        }
        throw new IOException("Plugin not found: " + fileName);
    }

    private Boolean readSoftEnabled(PluginConfigCatalog.Entry entry) {
        try {
            Map<String, Object> yaml = PluginConfigIo.load(root, entry);
            if (yaml.isEmpty()) {
                return null;
            }
            String key = softKey(entry);
            Object v = resolveDotted(yaml, key);
            if (v instanceof Boolean b) {
                return b;
            }
            if (v != null) {
                return Boolean.parseBoolean(String.valueOf(v));
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeSoftEnabled(PluginConfigCatalog.Entry entry, boolean enable) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(softKey(entry), Boolean.toString(enable));
        PluginConfigIo.save(root, entry, fields);
    }

    private static String softKey(PluginConfigCatalog.Entry entry) {
        if ("knobs.yml".equals(entry.file())) {
            return "settings.enabled";
        }
        return "enabled";
    }

    @SuppressWarnings("unchecked")
    private static Object resolveDotted(Map<String, Object> yaml, String dotted) {
        String[] parts = dotted.split("\\.");
        Object cur = yaml;
        for (String part : parts) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = ((Map<String, Object>) map).get(part);
        }
        return cur;
    }

    public static PluginConfigCatalog.Entry findEntry(String jarFileName) {
        String token = jarToken(jarFileName);
        String lower = jarFileName.toLowerCase(Locale.ROOT);
        PluginConfigCatalog.Entry best = null;
        int bestLen = -1;
        for (PluginConfigCatalog.Entry e : PluginConfigCatalog.all()) {
            String jt = e.jarToken().toLowerCase(Locale.ROOT);
            boolean match = token.equals(jt)
                    || lower.equals(jt + ".jar")
                    || lower.equals(jt + ".jar.disabled")
                    || lower.startsWith(jt + "-")
                    || lower.startsWith(jt + ".");
            if (match && jt.length() > bestLen) {
                best = e;
                bestLen = jt.length();
            }
        }
        return best;
    }

    public static String jarToken(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jar.disabled")) {
            n = n.substring(0, n.length() - ".disabled".length());
        }
        if (n.endsWith(".jar")) {
            n = n.substring(0, n.length() - 4);
        }
        while (true) {
            int dash = n.lastIndexOf('-');
            if (dash <= 0) {
                break;
            }
            String suffix = n.substring(dash + 1);
            if (suffix.matches("\\d+(?:\\.\\d+)*(?:[a-z].*)?")) {
                n = n.substring(0, dash);
            } else {
                break;
            }
        }
        return n;
    }

    public static Tier tierFor(String jarFileName, PluginConfigCatalog.Entry entry) {
        String token = entry != null ? entry.jarToken().toLowerCase(Locale.ROOT) : jarToken(jarFileName);
        if (CORE_TOKENS.contains(token)) {
            return Tier.CORE;
        }
        if (GAMEPLAY_TOKENS.contains(token) || "yap-factions".equals(token)) {
            return Tier.GAMEPLAY;
        }
        if (entry != null && token.startsWith("yap-")) {
            return Tier.NETWORK;
        }
        if (token.startsWith("yap-")) {
            return Tier.NETWORK;
        }
        return Tier.THIRD_PARTY;
    }
}
